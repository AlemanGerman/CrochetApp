package com.example.calculadoracrochet.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.calculadoracrochet.utils.FotosOptimizador
import com.example.calculadoracrochet.workers.EliminarFotoWorker
import com.example.calculadoracrochet.workers.SubirFotoWorker
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.Executors

data class EstambreGuardado(
    var nombre: String = "",
    var costo: String = "",
    var totales: String = "",
    var usados: String = ""
)

data class AccesorioGuardado(
    var nombre: String = "",
    var costo: String = ""
)

data class ProyectoCrochet(
    var id: String = UUID.randomUUID().toString(),
    var nombre: String = "Sin nombre",
    var fotoUri: String? = null,
    var fotoMiniaturaUri: String? = null,
    var fechaGuardado: Long = System.currentTimeMillis(),
    var fechaEliminado: Long? = null,
    var eliminado: Boolean = false,
    var costoMateriales: Double = 0.0,
    var costoDiseno: Double = 0.0,
    var costoManoObra: Double = 0.0,
    var costoEmpaque: Double = 0.0,
    var margenGanancia: Double = 0.0,
    var precioFinal: Double = 0.0,
    var horasTrabajadas: Double = 0.0,
    var pagoPorHora: Double = 0.0,
    var listaEstambres: List<EstambreGuardado> = emptyList(),
    var listaAccesorios: List<AccesorioGuardado> = emptyList(),
    var carpetas: List<String> = emptyList()
)

private data class FotoRemota(
    val principal: String?,
    val miniatura: String?
)

private data class FotosLocalesPersistentes(
    val principal: File,
    val miniatura: File?
)

class MinaViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val hiloFotos = Executors.newSingleThreadExecutor()
    private val handlerPrincipal = Handler(Looper.getMainLooper())

    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
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
        get() = historialProyectos.filter { it.fechaEliminado == null && !it.eliminado }

    val papeleraProyectos: List<ProyectoCrochet>
        get() = historialProyectos.filter { it.fechaEliminado != null || it.eliminado }

    init {
        cargarProyectosDeLaNube()
    }

    fun cargarProyectosDeLaNube() {
        val appContext = FirebaseApp.getInstance().applicationContext
        db.collection("proyectos_mina")
            .addSnapshotListener { documentos, error ->
                if (error != null) {
                    Log.e("Firebase", "Error al escuchar proyectos_mina", error)
                    return@addSnapshotListener
                }
                if (documentos == null) return@addSnapshotListener

                val listaProcesada = mutableListOf<ProyectoCrochet>()
                for (documento in documentos) {
                    try {
                        val proyecto = documento.toObject(ProyectoCrochet::class.java)?.apply {
                            id = documento.id
                            carpetas = carpetas ?: emptyList()
                            listaEstambres = listaEstambres ?: emptyList()
                            listaAccesorios = listaAccesorios ?: emptyList()
                        }
                        if (proyecto != null) {
                            listaProcesada.add(proyecto)
                            precargarEnCoil(appContext, FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri))
                        }
                    } catch (e: Exception) {
                        Log.e("Firebase", "Error al procesar proyecto con ID: ${documento.id}", e)
                    }
                }

                listaProcesada.sortByDescending { it.fechaGuardado }

                historialProyectos.clear()
                historialProyectos.addAll(listaProcesada)
                proyectosEnVivo.postValue(historialProyectos.toList())
            }
    }

    fun calcularTotal() {
        val subtotal = costoMateriales + costoDiseno + costoManoObra + costoEmpaque
        val gananciaExtra = subtotal * (margenGanancia / 100)
        precioFinalSugerdio = subtotal + gananciaExtra
    }

    fun guardarProyecto(context: Context, nombre: String, fotoUri: String?, onComplete: (Boolean) -> Unit) {
        val idNuevo = idProyectoEnEdicion ?: UUID.randomUUID().toString()
        val fotoAnterior = FotoRemota(fotoUriTemp, fotoMiniaturaUriTemp)
        val contextoApp = context.applicationContext

        if (fotoUri.esUriLocal()) {
            hiloFotos.execute {
                val fotosPersistentes = procesarYGuardarFotoLocal(contextoApp, idNuevo, Uri.parse(fotoUri))
                handlerPrincipal.post {
                    if (fotosPersistentes != null) {
                        val fotoLocal = FotoRemota(
                            principal = "file://${fotosPersistentes.principal.absolutePath}",
                            miniatura = fotosPersistentes.miniatura?.let { "file://${it.absolutePath}" }
                                ?: "file://${fotosPersistentes.principal.absolutePath}"
                        )
                        guardarEnFirestoreYEncolar(
                            contextoApp,
                            idNuevo,
                            nombre,
                            fotoParaGuardar = fotoLocal,
                            rutasFisicasParaSubir = fotosPersistentes,
                            fotoAnterior = fotoAnterior
                        )
                    } else {
                        guardarEnFirestoreYEncolar(
                            contextoApp,
                            idNuevo,
                            nombre,
                            fotoParaGuardar = FotoRemota(null, null),
                            rutasFisicasParaSubir = null,
                            fotoAnterior = fotoAnterior
                        )
                    }
                    limpiarCalculoActual()
                    onComplete(true)
                }
            }
        } else {
            val miniatura = if (fotoUri.isNullOrBlank()) null else fotoMiniaturaUriTemp
            val fotoRemota = FotoRemota(fotoUri, miniatura)
            guardarEnFirestoreYEncolar(
                contextoApp,
                idNuevo,
                nombre,
                fotoParaGuardar = fotoRemota,
                rutasFisicasParaSubir = null,
                fotoAnterior = fotoAnterior
            )
            limpiarCalculoActual()
            onComplete(true)
        }
    }

    private fun guardarEnFirestoreYEncolar(
        context: Context,
        idProyecto: String,
        nombre: String,
        fotoParaGuardar: FotoRemota,
        rutasFisicasParaSubir: FotosLocalesPersistentes?,
        fotoAnterior: FotoRemota
    ) {
        val nuevoProyecto = ProyectoCrochet(
            id = idProyecto,
            nombre = nombre,
            fotoUri = fotoParaGuardar.principal,
            fotoMiniaturaUri = fotoParaGuardar.miniatura,
            fechaGuardado = fechaEnEdicion ?: System.currentTimeMillis(),
            fechaEliminado = null,
            eliminado = false,
            costoMateriales = costoMateriales,
            costoDiseno = costoDiseno,
            costoManoObra = costoManoObra,
            costoEmpaque = costoEmpaque,
            margenGanancia = margenGanancia,
            precioFinal = precioFinalSugerdio,
            horasTrabajadas = horasTrabajadasTemp,
            pagoPorHora = pagoPorHoraTemp,
            listaEstambres = listaEstambresGuardados.toList(),
            listaAccesorios = listaAccesoriosGuardados.toList(),
            carpetas = carpetasTemp.toList()
        )

        db.collection("proyectos_mina").document(nuevoProyecto.id).set(nuevoProyecto)

        if (rutasFisicasParaSubir != null) {
            val urlsAnteriores = listOfNotNull(fotoAnterior.principal, fotoAnterior.miniatura)
                .filter { it.startsWith("http") && it !in listOfNotNull(fotoParaGuardar.principal, fotoParaGuardar.miniatura) }

            encolarSubidaFoto(context, idProyecto, nombre, rutasFisicasParaSubir, urlsAnteriores)
        } else if (fotoParaGuardar != fotoAnterior) {
            borrarFotosSiNoSeUsan(fotoAnterior, idProyectoExcluido = idProyecto)
        }

        precargarEnCoil(context, fotoParaGuardar)
    }

    fun moverAPapelera(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val ahora = System.currentTimeMillis()
        db.collection("proyectos_mina").document(proyecto.id)
            .update(mapOf("fechaEliminado" to ahora, "eliminado" to true))
            .addOnSuccessListener {
                proyecto.fechaEliminado = ahora
                proyecto.eliminado = true
                proyectosEnVivo.postValue(historialProyectos.toList())
                onComplete?.invoke(true)
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    fun restaurarProyectos(proyectos: List<ProyectoCrochet>, onComplete: ((Boolean) -> Unit)? = null) {
        if (proyectos.isEmpty()) {
            onComplete?.invoke(true)
            return
        }
        val batch = db.batch()
        proyectos.forEach { p ->
            val ref = db.collection("proyectos_mina").document(p.id)
            batch.update(ref, mapOf("fechaEliminado" to null, "eliminado" to false))
        }
        batch.commit()
            .addOnSuccessListener {
                proyectos.forEach {
                    it.fechaEliminado = null
                    it.eliminado = false
                }
                proyectosEnVivo.postValue(historialProyectos.toList())
                onComplete?.invoke(true)
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = FirebaseApp.getInstance().applicationContext
        val fotosABorrar = FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
        val idProyecto = proyecto.id

        WorkManager.getInstance(appContext).cancelUniqueWork("subir_$idProyecto")

        db.collection("proyectos_mina").document(idProyecto).delete()
            .addOnSuccessListener {
                historialProyectos.removeAll { it.id == idProyecto }
                proyectosEnVivo.postValue(historialProyectos.toList())
                borrarArchivosLocalesSiExisten(fotosABorrar)
                borrarFotosSiNoSeUsan(fotosABorrar, idProyectoExcluido = idProyecto)
                onComplete?.invoke(true)
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    fun eliminarProyectosPermanentes(proyectos: List<ProyectoCrochet>, onComplete: ((Boolean) -> Unit)? = null) {
        if (proyectos.isEmpty()) {
            onComplete?.invoke(true)
            return
        }
        val appContext = FirebaseApp.getInstance().applicationContext
        val batch = db.batch()

        proyectos.forEach { p ->
            val ref = db.collection("proyectos_mina").document(p.id)
            batch.delete(ref)
            WorkManager.getInstance(appContext).cancelUniqueWork("subir_${p.id}")
            val fotosABorrar = FotoRemota(p.fotoUri, p.fotoMiniaturaUri)
            borrarArchivosLocalesSiExisten(fotosABorrar)
            borrarFotosSiNoSeUsan(fotosABorrar, idProyectoExcluido = p.id)
        }

        batch.commit()
            .addOnSuccessListener {
                historialProyectos.removeAll(proyectos.toSet())
                proyectosEnVivo.postValue(historialProyectos.toList())
                onComplete?.invoke(true)
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    fun vaciarPapelera(onComplete: ((Boolean) -> Unit)? = null) {
        eliminarProyectosPermanentes(papeleraProyectos, onComplete)
    }

    fun actualizarFotoProyecto(
        context: Context,
        proyecto: ProyectoCrochet,
        fotoLocal: String,
        onComplete: (Boolean) -> Unit
    ) {
        val fotoAnterior = FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
        val idProyecto = proyecto.id
        val contextoApp = context.applicationContext

        hiloFotos.execute {
            val fotosPersistentes = procesarYGuardarFotoLocal(contextoApp, idProyecto, Uri.parse(fotoLocal))
            handlerPrincipal.post {
                if (fotosPersistentes != null) {
                    val nuevaFotoLocal = FotoRemota(
                        principal = "file://${fotosPersistentes.principal.absolutePath}",
                        miniatura = fotosPersistentes.miniatura?.let { "file://${it.absolutePath}" }
                            ?: "file://${fotosPersistentes.principal.absolutePath}"
                    )

                    db.collection("proyectos_mina").document(idProyecto)
                        .update(
                            mapOf(
                                "fotoUri" to nuevaFotoLocal.principal,
                                "fotoMiniaturaUri" to nuevaFotoLocal.miniatura
                            )
                        )

                    proyecto.fotoUri = nuevaFotoLocal.principal
                    proyecto.fotoMiniaturaUri = nuevaFotoLocal.miniatura

                    val urlsABorrar = listOfNotNull(fotoAnterior.principal, fotoAnterior.miniatura)
                        .filter { it.startsWith("http") }

                    encolarSubidaFoto(contextoApp, idProyecto, proyecto.nombre, fotosPersistentes, urlsABorrar)
                    borrarArchivosLocalesSiExisten(fotoAnterior)
                    precargarEnCoil(contextoApp, nuevaFotoLocal)

                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
        }
    }

    fun eliminarFotoProyecto(proyecto: ProyectoCrochet, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = FirebaseApp.getInstance().applicationContext
        val fotoAnterior = FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
        val idProyecto = proyecto.id

        WorkManager.getInstance(appContext).cancelUniqueWork("subir_$idProyecto")

        db.collection("proyectos_mina").document(idProyecto)
            .update(mapOf("fotoUri" to null, "fotoMiniaturaUri" to null))

        proyecto.fotoUri = null
        proyecto.fotoMiniaturaUri = null

        borrarArchivosLocalesSiExisten(fotoAnterior)
        borrarFotosSiNoSeUsan(fotoAnterior, idProyectoExcluido = idProyecto)

        onComplete?.invoke(true)
    }

    private fun procesarYGuardarFotoLocal(
        context: Context,
        idProyecto: String,
        uriLocal: Uri
    ): FotosLocalesPersistentes? {
        val cacheDir = context.cacheDir
        val tempDir = File(cacheDir, "picker_temp").apply { mkdirs() }
        val archivoTemporal = File(tempDir, "${UUID.randomUUID()}.tmp")

        try {
            if (uriLocal.scheme == "file") {
                File(uriLocal.path ?: "").copyTo(archivoTemporal, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uriLocal)?.use { input ->
                    archivoTemporal.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }

            val fotosOptimizadas = FotosOptimizador.optimizarArchivo(cacheDir, archivoTemporal)
            val dirDestino = File(context.filesDir, "fotos_locales").apply { if (!exists()) mkdirs() }

            val archivoPrincipal = File(dirDestino, "${idProyecto}_${UUID.randomUUID()}_foto.jpg")
            fotosOptimizadas.principal.copyTo(archivoPrincipal, overwrite = true)

            val archivoMiniatura = fotosOptimizadas.miniatura?.let { mini ->
                val destMini = File(dirDestino, "${idProyecto}_${UUID.randomUUID()}_mini.jpg")
                mini.copyTo(destMini, overwrite = true)
                destMini
            }

            fotosOptimizadas.eliminarArchivosTemporales()
            return FotosLocalesPersistentes(archivoPrincipal, archivoMiniatura)
        } catch (e: Exception) {
            Log.e("Firebase", "Error al procesar foto local", e)
            return null
        } finally {
            if (archivoTemporal.exists()) archivoTemporal.delete()
        }
    }

    private fun encolarSubidaFoto(
        context: Context,
        idProyecto: String,
        nombreProyecto: String,
        fotos: FotosLocalesPersistentes,
        urlsAnteriores: List<String> = emptyList()
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val datos = Data.Builder()
            .putString(SubirFotoWorker.KEY_PROYECTO_ID, idProyecto)
            .putString(SubirFotoWorker.KEY_NOMBRE_PROYECTO, nombreProyecto)
            .putString(SubirFotoWorker.KEY_RUTA_PRINCIPAL, fotos.principal.absolutePath)
            .putString(SubirFotoWorker.KEY_RUTA_MINIATURA, fotos.miniatura?.absolutePath)
            .putStringArray(SubirFotoWorker.KEY_URLS_ANTERIORES, urlsAnteriores.toTypedArray())
            .build()

        val request = OneTimeWorkRequestBuilder<SubirFotoWorker>()
            .setConstraints(constraints)
            .setInputData(datos)
            .addTag("subir_$idProyecto")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "subir_$idProyecto",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun borrarFotosSiNoSeUsan(
        fotos: FotoRemota,
        idProyectoExcluido: String? = null
    ) {
        val urlsCandidatas = setOfNotNull(fotos.principal, fotos.miniatura)
            .filter { it.startsWith("http") }
        if (urlsCandidatas.isEmpty()) return

        val urlsEnUso = historialProyectos
            .filter { it.id != idProyectoExcluido }
            .flatMap { listOfNotNull(it.fotoUri, it.fotoMiniaturaUri) }
            .toSet()

        val urlsABorrar = urlsCandidatas.filter { it !in urlsEnUso }
        if (urlsABorrar.isEmpty()) return

        val appContext = FirebaseApp.getInstance().applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val datos = Data.Builder()
            .putStringArray(EliminarFotoWorker.KEY_URLS, urlsABorrar.toTypedArray())
            .build()

        val request = OneTimeWorkRequestBuilder<EliminarFotoWorker>()
            .setConstraints(constraints)
            .setInputData(datos)
            .build()

        WorkManager.getInstance(appContext).enqueue(request)
    }

    private fun borrarArchivosLocalesSiExisten(fotos: FotoRemota) {
        listOfNotNull(fotos.principal, fotos.miniatura).forEach { uriStr ->
            if (uriStr.startsWith("file://")) {
                try {
                    val ruta = uriStr.removePrefix("file://")
                    File(ruta).delete()
                } catch (e: Exception) {
                    Log.e("Firebase", "Error al borrar archivo local: $uriStr", e)
                }
            }
        }
    }

    private fun precargarEnCoil(context: Context, fotoRemota: FotoRemota) {
        try {
            val loader = context.imageLoader
            fotoRemota.principal?.takeIf { it.isNotBlank() }?.let { url ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
            fotoRemota.miniatura?.takeIf { it.isNotBlank() && it != fotoRemota.principal }?.let { urlMin ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(urlMin)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
        } catch (e: Exception) {
            Log.w("Firebase", "No se pudo precargar la foto en Coil", e)
        }
    }

    private fun String?.esUriLocal(): Boolean = !isNullOrBlank() && !startsWith("http")

    fun actualizarCarpetasProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina")
            .document(proyecto.id)
            .update("carpetas", proyecto.carpetas)
            .addOnSuccessListener { Log.d("Firebase", "Carpetas actualizadas") }
    }

    fun limpiarCalculoActual() {
        costoMateriales = 0.0
        costoDiseno = 0.0
        costoManoObra = 0.0
        costoEmpaque = 0.0
        margenGanancia = 0.0
        precioFinalSugerdio = 0.0
        horasTrabajadasTemp = 0.0
        pagoPorHoraTemp = 0.0
        nombreTemp = ""
        fotoUriTemp = null
        fotoMiniaturaUriTemp = null
        idProyectoEnEdicion = null
        fechaEnEdicion = null
        listaEstambresGuardados.clear()
        listaAccesoriosGuardados.clear()
        carpetasTemp.clear()
    }

    override fun onCleared() {
        hiloFotos.shutdownNow()
        super.onCleared()
    }
}