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
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    val proyectosEnVivo = MutableLiveData<List<ProyectoCrochet>>()
    val historialProyectos = mutableListOf<ProyectoCrochet>()

    val proyectosActivos: List<ProyectoCrochet>
        get() = historialProyectos.filterNot { it.estaEnPapelera }

    val papeleraProyectos: List<ProyectoCrochet>
        get() = historialProyectos.filter { it.estaEnPapelera }

    init {
        cargarProyectosDeLaNube()
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
        }
    }

    /**
     * Saves a project built by the calculator. [proyecto] still carries the photo it was loaded
     * with; [fotoElegida] is the one picked on the result screen (local URI, remote URL or null).
     */
    fun guardarProyecto(proyecto: ProyectoCrochet, fotoElegida: String?, onComplete: (Boolean) -> Unit) {
        val fotoAnterior = FotoProyecto.de(proyecto)

        if (fotoElegida.esUriLocal()) {
            viewModelScope.launch(Dispatchers.IO) {
                val fotosPersistentes = fotosRepo.procesarYGuardarFotoLocal(proyecto.id, Uri.parse(fotoElegida))
                withContext(Dispatchers.Main) {
                    guardarEnFirestoreYEncolar(
                        proyecto,
                        fotoParaGuardar = fotosPersistentes?.comoFotoProyecto() ?: FotoProyecto.VACIA,
                        rutasFisicasParaSubir = fotosPersistentes,
                        fotoAnterior = fotoAnterior,
                        onComplete = onComplete
                    )
                }
            }
        } else {
            val miniatura = if (fotoElegida.isNullOrBlank()) null else fotoAnterior.miniatura
            guardarEnFirestoreYEncolar(
                proyecto,
                fotoParaGuardar = FotoProyecto(fotoElegida, miniatura),
                rutasFisicasParaSubir = null,
                fotoAnterior = fotoAnterior,
                onComplete = onComplete
            )
        }
    }

    private fun guardarEnFirestoreYEncolar(
        proyecto: ProyectoCrochet,
        fotoParaGuardar: FotoProyecto,
        rutasFisicasParaSubir: FotosLocalesPersistentes?,
        fotoAnterior: FotoProyecto,
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
                val urlsAnteriores = fotoAnterior.rutas
                    .filter { it.esRemota() && it !in fotoParaGuardar.rutas }
                fotosRepo.encolarSubida(proyectoFinal.id, proyectoFinal.nombre, rutasFisicasParaSubir, urlsAnteriores)
            } else if (fotoParaGuardar != fotoAnterior) {
                borrarFotosSiNoSeUsan(listOf(fotoAnterior), idsExcluidos = setOf(proyectoFinal.id))
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
        fotosABorrar.forEach { fotosRepo.borrarArchivosLocales(it) }
        // Exclude every project being deleted, so a photo shared only among them is deleted too
        borrarFotosSiNoSeUsan(fotosABorrar, idsExcluidos = ids.toSet())

        proyectosRepo.eliminarEnLote(ids) { exito ->
            if (exito) {
                historialProyectos.removeAll { it.id in ids }
                publicarProyectos()
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
}
