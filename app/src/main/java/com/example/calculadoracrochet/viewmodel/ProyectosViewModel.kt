package com.example.calculadoracrochet.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calculadoracrochet.data.FotoProyecto
import com.example.calculadoracrochet.data.FotosLocalesPersistentes
import com.example.calculadoracrochet.data.FotosProyectoRepository
import com.example.calculadoracrochet.data.ProyectosRepository
import com.example.calculadoracrochet.data.esRemota
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.data.model.estaEnPapelera
import com.example.calculadoracrochet.data.model.fotoParaLista
import com.example.calculadoracrochet.ui.dashboard.CriterioOrden
import com.example.calculadoracrochet.ui.dashboard.FiltroProyectos
import com.example.calculadoracrochet.utils.ColorExtractorHelper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Saved projects: the live list from Firestore and every change to it (save, edit, photo,
 * trash, delete). Shared at activity scope by the dashboard, trash and statistics screens,
 * so there is a single Firestore listener.
 */
class ProyectosViewModel(application: Application) : AndroidViewModel(application) {

    private val proyectosRepo = ProyectosRepository()
    private val fotosRepo = FotosProyectoRepository(application)

    private var registroProyectos: ListenerRegistration? = null
    private var coloresPodados = false

    val proyectosEnVivo = MutableLiveData<List<ProyectoCrochet>>()
    val historialProyectos = mutableListOf<ProyectoCrochet>()

    val proyectosActivos: List<ProyectoCrochet>
        get() = historialProyectos.filterNot { it.estaEnPapelera }

    val papeleraProyectos: List<ProyectoCrochet>
        get() = historialProyectos.filter { it.estaEnPapelera }

    /** Search, folder and order of the dashboard, kept while the user moves between the main tabs. */
    var filtroDashboard = FiltroDashboard()

    init {
        iniciarSesionYCargar()
    }

    /**
     * Firestore rules only allow signed-in users, so sign in anonymously before listening.
     * The session is kept on the phone: only the very first launch needs a connection, and
     * without one it keeps retrying until there is.
     */
    private fun iniciarSesionYCargar() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            cargarProyectosDeLaNube()
            return
        }

        viewModelScope.launch {
            var espera = ESPERA_INICIAL_SESION_MS
            while (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo iniciar sesión anónima; se reintenta en ${espera / 1000} s", e)
                    delay(espera)
                    espera = (espera * 2).coerceAtMost(ESPERA_MAXIMA_SESION_MS)
                }
            }
            cargarProyectosDeLaNube()
        }
    }

    override fun onCleared() {
        registroProyectos?.remove()
        super.onCleared()
    }

    // region Proyectos (Firestore)

    fun cargarProyectosDeLaNube() {
        registroProyectos?.remove()
        registroProyectos = proyectosRepo.escucharProyectos { listaProcesada ->
            historialProyectos.clear()
            historialProyectos.addAll(listaProcesada)
            publicarProyectos()
            precargarFotosFaltantes(listaProcesada)
            podarColoresDeTarjetas(listaProcesada)
        }
    }

    /**
     * Saves a project built by the calculator. [proyecto] still carries the photo it was loaded
     * with; [fotoElegida] is the one picked on the result screen (local URI, remote URL or null).
     */
    fun guardarProyecto(proyecto: ProyectoCrochet, fotoElegida: String?, onComplete: (Boolean) -> Unit) {
        val fotoCargada = FotoProyecto.de(proyecto)
        // Null for a new project or a duplicate: the photo it was loaded with belongs to another project
        val fotoActual = historialProyectos.find { it.id == proyecto.id }?.let { FotoProyecto.de(it) }

        when {
            fotoActual != null && fotoElegida == fotoCargada.principal -> {
                // Photo not changed. Its upload may have finished since the project was opened, so keep the live one
                guardarEnFirestoreYEncolar(proyecto, fotoActual, null, fotoActual, onComplete)
            }
            fotoElegida.esUriLocal() -> viewModelScope.launch(Dispatchers.IO) {
                val fotosPersistentes = fotosRepo.procesarYGuardarFotoLocal(proyecto.id, Uri.parse(fotoElegida))
                withContext(Dispatchers.Main) {
                    if (fotosPersistentes == null) {
                        // Saving anyway would drop the new photo and delete the previous one
                        onComplete(false)
                    } else {
                        guardarEnFirestoreYEncolar(
                            proyecto,
                            fotoParaGuardar = fotosPersistentes.comoFotoProyecto(),
                            rutasFisicasParaSubir = fotosPersistentes,
                            fotoReemplazada = fotoActual,
                            onComplete = onComplete
                        )
                    }
                }
            }
            else -> {
                val miniatura = if (fotoElegida.isNullOrBlank()) null else fotoCargada.miniatura
                guardarEnFirestoreYEncolar(
                    proyecto,
                    fotoParaGuardar = FotoProyecto(fotoElegida, miniatura),
                    rutasFisicasParaSubir = null,
                    fotoReemplazada = fotoActual,
                    onComplete = onComplete
                )
            }
        }
    }

    /**
     * [fotoReemplazada] is the photo the project has right now, or null for a new project or a
     * duplicate, whose previous photo belongs to another project and must not be touched.
     */
    private fun guardarEnFirestoreYEncolar(
        proyecto: ProyectoCrochet,
        fotoParaGuardar: FotoProyecto,
        rutasFisicasParaSubir: FotosLocalesPersistentes?,
        fotoReemplazada: FotoProyecto?,
        onComplete: (Boolean) -> Unit
    ) {
        val proyectoFinal = proyecto.copy(
            fotoUri = fotoParaGuardar.principal,
            fotoMiniaturaUri = fotoParaGuardar.miniatura
        )

        proyectosRepo.guardar(proyectoFinal) { exito ->
            if (!exito) {
                onComplete(false)
                return@guardar
            }

            if (rutasFisicasParaSubir != null) {
                val urlsAnteriores = fotoReemplazada?.rutas.orEmpty()
                    .filter { it.esRemota() && it !in fotoParaGuardar.rutas }
                fotosRepo.encolarSubida(proyectoFinal.id, proyectoFinal.nombre, rutasFisicasParaSubir, urlsAnteriores)
                // The new upload replaces any pending one, so the previous local files are no longer needed
                fotoReemplazada?.let { fotosRepo.borrarArchivosLocales(it) }
            } else if (fotoReemplazada != null && fotoParaGuardar != fotoReemplazada) {
                // Photo removed: a pending upload must not write it back
                fotosRepo.cancelarSubida(proyectoFinal.id)
                fotosRepo.borrarArchivosLocales(fotoReemplazada)
                borrarFotosSiNoSeUsan(listOf(fotoReemplazada), idsExcluidos = setOf(proyectoFinal.id))
            }

            fotosRepo.precargarLocal(fotoParaGuardar)
            onComplete(true)
        }
    }

    fun actualizarCarpetasProyecto(proyecto: ProyectoCrochet) {
        proyectosRepo.actualizarCampos(proyecto.id, mapOf("carpetas" to proyecto.carpetas))
    }

    fun actualizarNombreProyecto(proyecto: ProyectoCrochet, nuevoNombre: String) {
        proyectosRepo.actualizarCampos(proyecto.id, mapOf("nombre" to nuevoNombre))
        proyecto.nombre = nuevoNombre
    }

    fun actualizarFechaProyecto(proyecto: ProyectoCrochet, fechaMs: Long) {
        proyectosRepo.actualizarCampos(proyecto.id, mapOf("fechaGuardado" to fechaMs))
        proyecto.fechaGuardado = fechaMs
    }

    private fun publicarProyectos() {
        proyectosEnVivo.postValue(historialProyectos.toList())
    }

    // endregion

    // region Papelera

    fun moverAPapelera(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val ahora = System.currentTimeMillis()
        proyectosRepo.actualizarCampos(proyecto.id, mapOf("fechaEliminado" to ahora, "eliminado" to true)) { exito ->
            if (exito) {
                proyecto.fechaEliminado = ahora
                proyecto.eliminado = true
                publicarProyectos()
            }
            onComplete?.invoke(exito)
        }
    }

    fun restaurarProyectos(proyectos: List<ProyectoCrochet>, onComplete: ((Boolean) -> Unit)? = null) {
        if (proyectos.isEmpty()) {
            onComplete?.invoke(true)
            return
        }
        proyectosRepo.actualizarCamposEnLote(
            proyectos.map { it.id },
            mapOf("fechaEliminado" to null, "eliminado" to false)
        ) { exito ->
            if (exito) {
                proyectos.forEach {
                    it.fechaEliminado = null
                    it.eliminado = false
                }
                publicarProyectos()
            }
            onComplete?.invoke(exito)
        }
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val fotosABorrar = FotoProyecto.de(proyecto)
        val idProyecto = proyecto.id

        fotosRepo.cancelarSubida(idProyecto)

        proyectosRepo.eliminar(idProyecto) { exito ->
            if (exito) {
                historialProyectos.removeAll { it.id == idProyecto }
                publicarProyectos()
                fotosRepo.borrarArchivosLocales(fotosABorrar)
                borrarFotosSiNoSeUsan(listOf(fotosABorrar), idsExcluidos = setOf(idProyecto))
            }
            onComplete?.invoke(exito)
        }
    }

    fun eliminarProyectosPermanentes(proyectos: List<ProyectoCrochet>, onComplete: ((Boolean) -> Unit)? = null) {
        if (proyectos.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        val ids = proyectos.map { it.id }
        val fotosABorrar = proyectos.map { FotoProyecto.de(it) }

        ids.forEach { fotosRepo.cancelarSubida(it) }

        proyectosRepo.eliminarEnLote(ids) { exito ->
            if (exito) {
                historialProyectos.removeAll { it.id in ids }
                publicarProyectos()
                fotosABorrar.forEach { fotosRepo.borrarArchivosLocales(it) }
                // Exclude every project being deleted, so a photo shared only among them is deleted too
                borrarFotosSiNoSeUsan(fotosABorrar, idsExcluidos = ids.toSet())
            }
            onComplete?.invoke(exito)
        }
    }

    fun vaciarPapelera(onComplete: ((Boolean) -> Unit)? = null) {
        eliminarProyectosPermanentes(papeleraProyectos, onComplete)
    }

    // endregion

    // region Fotos

    fun actualizarFotoProyecto(
        proyecto: ProyectoCrochet,
        fotoLocal: String,
        onComplete: (Boolean) -> Unit
    ) {
        val fotoAnterior = FotoProyecto.de(proyecto)
        val idProyecto = proyecto.id

        viewModelScope.launch(Dispatchers.IO) {
            val fotosPersistentes = fotosRepo.procesarYGuardarFotoLocal(idProyecto, Uri.parse(fotoLocal))
            withContext(Dispatchers.Main) {
                if (fotosPersistentes == null) {
                    onComplete(false)
                    return@withContext
                }

                val nuevaFotoLocal = fotosPersistentes.comoFotoProyecto()
                proyectosRepo.actualizarCampos(
                    idProyecto,
                    mapOf("fotoUri" to nuevaFotoLocal.principal, "fotoMiniaturaUri" to nuevaFotoLocal.miniatura)
                )

                proyecto.fotoUri = nuevaFotoLocal.principal
                proyecto.fotoMiniaturaUri = nuevaFotoLocal.miniatura

                val urlsABorrar = fotoAnterior.rutas.filter { it.esRemota() }

                fotosRepo.encolarSubida(idProyecto, proyecto.nombre, fotosPersistentes, urlsABorrar)
                fotosRepo.borrarArchivosLocales(fotoAnterior)
                fotosRepo.precargarLocal(nuevaFotoLocal)

                onComplete(true)
            }
        }
    }

    fun eliminarFotoProyecto(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val fotoAnterior = FotoProyecto.de(proyecto)
        val idProyecto = proyecto.id

        fotosRepo.cancelarSubida(idProyecto)
        proyectosRepo.actualizarCampos(idProyecto, mapOf("fotoUri" to null, "fotoMiniaturaUri" to null))

        proyecto.fotoUri = null
        proyecto.fotoMiniaturaUri = null

        fotosRepo.borrarArchivosLocales(fotoAnterior)
        borrarFotosSiNoSeUsan(listOf(fotoAnterior), idsExcluidos = setOf(idProyecto))

        onComplete?.invoke(true)
    }

    /** Once per launch: forgets the card colors of photos no project shows anymore. */
    private fun podarColoresDeTarjetas(proyectos: List<ProyectoCrochet>) {
        if (coloresPodados || proyectos.isEmpty()) return
        coloresPodados = true
        ColorExtractorHelper.podarGuardados(proyectos.mapNotNull { it.fotoParaLista }.toSet())
    }

    private fun precargarFotosFaltantes(proyectos: List<ProyectoCrochet>) {
        viewModelScope.launch(Dispatchers.IO) {
            fotosRepo.precargarFaltantes(proyectos)
        }
    }

    /** Reads the list on the main thread, where the Firestore listener writes it, then queues the deletion. */
    private fun borrarFotosSiNoSeUsan(fotos: List<FotoProyecto>, idsExcluidos: Set<String>) {
        val urlsSinUso = FotoProyecto.urlsSinUso(fotos, historialProyectos, idsExcluidos)
        if (urlsSinUso.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            fotosRepo.encolarEliminacionRemota(urlsSinUso)
        }
    }

    private fun String?.esUriLocal(): Boolean = !isNullOrBlank() && !esRemota()

    // endregion

    private companion object {
        const val TAG = "ProyectosViewModel"
        const val ESPERA_INICIAL_SESION_MS = 2_000L
        const val ESPERA_MAXIMA_SESION_MS = 60_000L
    }
}

data class FiltroDashboard(
    val textoBusqueda: String = "",
    val carpeta: String = FiltroProyectos.CARPETA_TODOS,
    val criterio: CriterioOrden = CriterioOrden.FECHA,
    val descendente: Boolean = true
)
