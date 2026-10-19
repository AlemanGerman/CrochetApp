package com.example.calculadoracrochet.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calculadoracrochet.data.BorradorCalculo
import com.example.calculadoracrochet.data.BorradorRepository
import com.example.calculadoracrochet.data.CalculadoraPrecio
import com.example.calculadoracrochet.data.FotoProyecto
import com.example.calculadoracrochet.data.FotosLocalesPersistentes
import com.example.calculadoracrochet.data.FotosProyectoRepository
import com.example.calculadoracrochet.data.ProyectosRepository
import com.example.calculadoracrochet.data.esRemota
import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.data.model.estaEnPapelera
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MinaViewModel(application: Application) : AndroidViewModel(application) {

    private val proyectosRepo = ProyectosRepository()
    private val fotosRepo = FotosProyectoRepository(application)
    private val borradorRepo = BorradorRepository(application)

    private var registroProyectos: ListenerRegistration? = null

    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
    var ajusteRedondeo: Double = 0.0
    var precioFinalSugerdio: Double = 0.0

    var horasTrabajadasTemp: Double = 0.0
    var pagoPorHoraTemp: Double = 0.0
    var carpetasTemp = mutableListOf<String>()

    var nombreTemp: String = ""
    var fotoUriTemp: String? = null
    var fotoMiniaturaUriTemp: String? = null

    var idProyectoEnEdicion: String? = null
    var fechaEnEdicion: Long? = null

    val listaEstambresGuardados = mutableListOf<EstambreGuardado>()
    val listaAccesoriosGuardados = mutableListOf<AccesorioGuardado>()

    val proyectosEnVivo = MutableLiveData<List<ProyectoCrochet>>()
    val historialProyectos = mutableListOf<ProyectoCrochet>()

    val proyectosActivos: List<ProyectoCrochet>
        get() = historialProyectos.filterNot { it.estaEnPapelera }

    val papeleraProyectos: List<ProyectoCrochet>
        get() = historialProyectos.filter { it.estaEnPapelera }

    init {
        restaurarBorrador()
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
            precargarFotosFaltantes(getApplication(), listaProcesada)
        }
    }

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
                borrarFotosSiNoSeUsan(fotosABorrar, idProyectoExcluido = idProyecto)
            }
            onComplete?.invoke(exito)
        }
    }

    fun eliminarProyectosPermanentes(proyectos: List<ProyectoCrochet>, onComplete: ((Boolean) -> Unit)? = null) {
        if (proyectos.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        proyectos.forEach { p ->
            fotosRepo.cancelarSubida(p.id)
            val fotosABorrar = FotoProyecto.de(p)
            fotosRepo.borrarArchivosLocales(fotosABorrar)
            borrarFotosSiNoSeUsan(fotosABorrar, idProyectoExcluido = p.id)
        }

        proyectosRepo.eliminarEnLote(proyectos.map { it.id }) { exito ->
            if (exito) {
                historialProyectos.removeAll(proyectos.toSet())
                publicarProyectos()
            }
            onComplete?.invoke(exito)
        }
    }

    fun vaciarPapelera(onComplete: ((Boolean) -> Unit)? = null) {
        eliminarProyectosPermanentes(papeleraProyectos, onComplete)
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

    // region Cálculo actual

    fun calcularTotal() {
        val desglose = CalculadoraPrecio.desglosar(
            costoMateriales, costoDiseno, costoManoObra, costoEmpaque, margenGanancia
        )

        val context = getApplication<Application>()
        val activo = PreferenciasApp.isRedondeoActivo(context)
        val multiplo = PreferenciasApp.getMultiploRedondeo(context)

        val (precio, ajuste) = PreferenciasApp.calcularRedondeo(desglose.totalExacto, activo, multiplo)
        precioFinalSugerdio = precio
        ajusteRedondeo = ajuste
    }

    /** Loads a saved project into the calculation. A duplicate gets a new id and date when saved. */
    fun cargarProyectoParaEdicion(proyecto: ProyectoCrochet, esDuplicado: Boolean) {
        idProyectoEnEdicion = if (esDuplicado) null else proyecto.id
        fechaEnEdicion = if (esDuplicado) null else proyecto.fechaGuardado
        costoMateriales = proyecto.costoMateriales
        costoDiseno = proyecto.costoDiseno
        costoManoObra = proyecto.costoManoObra
        costoEmpaque = proyecto.costoEmpaque
        margenGanancia = proyecto.margenGanancia
        ajusteRedondeo = proyecto.ajusteRedondeo
        horasTrabajadasTemp = proyecto.horasTrabajadas
        pagoPorHoraTemp = proyecto.pagoPorHora
        nombreTemp = proyecto.nombre
        fotoUriTemp = proyecto.fotoUri
        fotoMiniaturaUriTemp = proyecto.fotoMiniaturaUri
        carpetasTemp.reemplazarCon(proyecto.carpetas ?: emptyList())
        listaEstambresGuardados.reemplazarCon(proyecto.listaEstambres ?: emptyList())
        listaAccesoriosGuardados.reemplazarCon(proyecto.listaAccesorios ?: emptyList())
        guardarBorrador()
    }

    fun guardarProyecto(context: Context, nombre: String, fotoUri: String?, onComplete: (Boolean) -> Unit) {
        val idNuevo = idProyectoEnEdicion ?: UUID.randomUUID().toString()
        val fotoAnterior = FotoProyecto(fotoUriTemp, fotoMiniaturaUriTemp)

        if (fotoUri.esUriLocal()) {
            viewModelScope.launch(Dispatchers.IO) {
                val fotosPersistentes = fotosRepo.procesarYGuardarFotoLocal(idNuevo, Uri.parse(fotoUri))
                withContext(Dispatchers.Main) {
                    guardarEnFirestoreYEncolar(
                        idNuevo,
                        nombre,
                        fotoParaGuardar = fotosPersistentes?.comoFotoProyecto() ?: FotoProyecto.VACIA,
                        rutasFisicasParaSubir = fotosPersistentes,
                        fotoAnterior = fotoAnterior,
                        onComplete = onComplete
                    )
                }
            }
        } else {
            val miniatura = if (fotoUri.isNullOrBlank()) null else fotoMiniaturaUriTemp
            guardarEnFirestoreYEncolar(
                idNuevo,
                nombre,
                fotoParaGuardar = FotoProyecto(fotoUri, miniatura),
                rutasFisicasParaSubir = null,
                fotoAnterior = fotoAnterior,
                onComplete = onComplete
            )
        }
    }

    private fun guardarEnFirestoreYEncolar(
        idProyecto: String,
        nombre: String,
        fotoParaGuardar: FotoProyecto,
        rutasFisicasParaSubir: FotosLocalesPersistentes?,
        fotoAnterior: FotoProyecto,
        onComplete: (Boolean) -> Unit
    ) {
        val nuevoProyecto = construirProyecto(idProyecto, nombre, fotoParaGuardar)

        proyectosRepo.guardar(nuevoProyecto) { exito ->
            if (!exito) {
                onComplete(false)
                return@guardar
            }

            if (rutasFisicasParaSubir != null) {
                val urlsAnteriores = fotoAnterior.rutas
                    .filter { it.esRemota() && it !in fotoParaGuardar.rutas }
                fotosRepo.encolarSubida(idProyecto, nombre, rutasFisicasParaSubir, urlsAnteriores)
            } else if (fotoParaGuardar != fotoAnterior) {
                borrarFotosSiNoSeUsan(fotoAnterior, idProyectoExcluido = idProyecto)
            }

            fotosRepo.precargarLocal(fotoParaGuardar)
            limpiarCalculoActual()
            onComplete(true)
        }
    }

    private fun construirProyecto(idProyecto: String, nombre: String, foto: FotoProyecto) = ProyectoCrochet(
        id = idProyecto,
        nombre = nombre,
        fotoUri = foto.principal,
        fotoMiniaturaUri = foto.miniatura,
        fechaGuardado = fechaEnEdicion ?: System.currentTimeMillis(),
        fechaEliminado = null,
        eliminado = false,
        costoMateriales = costoMateriales,
        costoDiseno = costoDiseno,
        costoManoObra = costoManoObra,
        costoEmpaque = costoEmpaque,
        margenGanancia = margenGanancia,
        ajusteRedondeo = ajusteRedondeo,
        precioFinal = precioFinalSugerdio,
        horasTrabajadas = horasTrabajadasTemp,
        pagoPorHora = pagoPorHoraTemp,
        listaEstambres = listaEstambresGuardados.toList(),
        listaAccesorios = listaAccesoriosGuardados.toList(),
        carpetas = carpetasTemp.toList()
    )

    fun limpiarCalculoActual() {
        aplicarBorrador(BorradorCalculo())
        ajusteRedondeo = 0.0
        precioFinalSugerdio = 0.0
        limpiarBorrador()
    }

    // endregion

    // region Fotos

    fun actualizarFotoProyecto(
        context: Context,
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
        borrarFotosSiNoSeUsan(fotoAnterior, idProyectoExcluido = idProyecto)

        onComplete?.invoke(true)
    }

    @Suppress("UNUSED_PARAMETER")
    fun precargarFotosFaltantes(context: Context, proyectos: List<ProyectoCrochet>) {
        viewModelScope.launch(Dispatchers.IO) {
            fotosRepo.precargarFaltantes(proyectos)
        }
    }

    /** Remote photos may be shared by duplicated projects, so only delete URLs no other project uses. */
    private fun borrarFotosSiNoSeUsan(fotos: FotoProyecto, idProyectoExcluido: String? = null) {
        val urlsCandidatas = fotos.rutas.toSet().filter { it.esRemota() }
        if (urlsCandidatas.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val urlsEnUso = historialProyectos
                .filter { it.id != idProyectoExcluido }
                .flatMap { FotoProyecto.de(it).rutas }
                .toSet()

            fotosRepo.encolarEliminacionRemota(urlsCandidatas.filter { it !in urlsEnUso })
        }
    }

    private fun String?.esUriLocal(): Boolean = !isNullOrBlank() && !esRemota()

    // endregion

    // region Borrador

    fun guardarBorrador() {
        borradorRepo.guardar(crearBorrador())
    }

    fun restaurarBorrador() {
        val borrador = borradorRepo.cargar() ?: return
        aplicarBorrador(borrador)
        calcularTotal()
    }

    fun limpiarBorrador() {
        borradorRepo.limpiar()
    }

    private fun crearBorrador() = BorradorCalculo(
        costoMateriales = costoMateriales,
        costoDiseno = costoDiseno,
        costoManoObra = costoManoObra,
        costoEmpaque = costoEmpaque,
        margenGanancia = margenGanancia,
        horasTrabajadas = horasTrabajadasTemp,
        pagoPorHora = pagoPorHoraTemp,
        nombre = nombreTemp,
        fotoUri = fotoUriTemp,
        fotoMiniaturaUri = fotoMiniaturaUriTemp,
        idProyectoEnEdicion = idProyectoEnEdicion,
        fechaEnEdicion = fechaEnEdicion,
        carpetas = carpetasTemp.toSet(),
        estambres = listaEstambresGuardados.toList(),
        accesorios = listaAccesoriosGuardados.toList()
    )

    private fun aplicarBorrador(borrador: BorradorCalculo) {
        costoMateriales = borrador.costoMateriales
        costoDiseno = borrador.costoDiseno
        costoManoObra = borrador.costoManoObra
        costoEmpaque = borrador.costoEmpaque
        margenGanancia = borrador.margenGanancia
        horasTrabajadasTemp = borrador.horasTrabajadas
        pagoPorHoraTemp = borrador.pagoPorHora
        nombreTemp = borrador.nombre
        fotoUriTemp = borrador.fotoUri
        fotoMiniaturaUriTemp = borrador.fotoMiniaturaUri
        idProyectoEnEdicion = borrador.idProyectoEnEdicion
        fechaEnEdicion = borrador.fechaEnEdicion
        carpetasTemp.reemplazarCon(borrador.carpetas)
        listaEstambresGuardados.reemplazarCon(borrador.estambres)
        listaAccesoriosGuardados.reemplazarCon(borrador.accesorios)
    }

    private fun <T> MutableList<T>.reemplazarCon(elementos: Collection<T>) {
        clear()
        addAll(elementos)
    }

    // endregion
}
