package com.example.calculadoracrochet.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class EstambreGuardado(
    var nombre: String = "",
    var costo: String = "",
    var totales: String = "",
    var usados: String = ""
)

data class AccesorioGuardado(
    var nombre: String = "",
    var costo: String = "",
    var esCalculado: Boolean = false,
    var costoPaquete: String = "",
    var cantidadTotal: String = "",
    var cantidadUsada: String = ""
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

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class MinaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()

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
        restaurarBorrador()
        cargarProyectosDeLaNube()
    }

    fun cargarProyectosDeLaNube() {
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
                        }
                    } catch (e: Exception) {
                        Log.e("Firebase", "Error al procesar proyecto: ${documento.id}", e)
                    }
                }

                listaProcesada.sortByDescending { it.fechaGuardado }

                historialProyectos.clear()
                historialProyectos.addAll(listaProcesada)
                proyectosEnVivo.postValue(historialProyectos.toList())
                precargarFotosFaltantes(getApplication(), listaProcesada)
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
            viewModelScope.launch(Dispatchers.IO) {
                val fotosPersistentes = procesarYGuardarFotoLocal(contextoApp, idNuevo, Uri.parse(fotoUri))
                withContext(Dispatchers.Main) {
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
                            fotoAnterior = fotoAnterior,
                            onComplete = onComplete
                        )
                    } else {
                        guardarEnFirestoreYEncolar(
                            contextoApp,
                            idNuevo,
                            nombre,
                            fotoParaGuardar = FotoRemota(null, null),
                            rutasFisicasParaSubir = null,
                            fotoAnterior = fotoAnterior,
                            onComplete = onComplete
                        )
                    }
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
                fotoAnterior = fotoAnterior,
                onComplete = onComplete
            )
        }
    }

    private fun guardarEnFirestoreYEncolar(
        context: Context,
        idProyecto: String,
        nombre: String,
        fotoParaGuardar: FotoRemota,
        rutasFisicasParaSubir: FotosLocalesPersistentes?,
        fotoAnterior: FotoRemota,
        onComplete: (Boolean) -> Unit
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
            .addOnSuccessListener {
                if (rutasFisicasParaSubir != null) {
                    val urlsAnteriores = listOfNotNull(fotoAnterior.principal, fotoAnterior.miniatura)
                        .filter { it.startsWith("http") && it !in listOfNotNull(fotoParaGuardar.principal, fotoParaGuardar.miniatura) }

                    encolarSubidaFoto(context, idProyecto, nombre, rutasFisicasParaSubir, urlsAnteriores)
                } else if (fotoParaGuardar != fotoAnterior) {
                    borrarFotosSiNoSeUsan(fotoAnterior, idProyectoExcluido = idProyecto)
                }

                precargarLocalEnCoil(context, fotoParaGuardar)
                limpiarCalculoActual()
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error al guardar proyecto", e)
                onComplete(false)
            }
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

        viewModelScope.launch(Dispatchers.IO) {
            val fotosPersistentes = procesarYGuardarFotoLocal(contextoApp, idProyecto, Uri.parse(fotoLocal))
            withContext(Dispatchers.Main) {
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
                    precargarLocalEnCoil(contextoApp, nuevaFotoLocal)

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

        viewModelScope.launch(Dispatchers.IO) {
            val urlsEnUso = historialProyectos
                .filter { it.id != idProyectoExcluido }
                .flatMap { listOfNotNull(it.fotoUri, it.fotoMiniaturaUri) }
                .toSet()

            val urlsABorrar = urlsCandidatas.filter { it !in urlsEnUso }
            if (urlsABorrar.isEmpty()) return@launch

            val diskCache = getApplication<Application>().imageLoader.diskCache
            urlsABorrar.forEach { url ->
                try {
                    diskCache?.remove(url)
                } catch (_: Exception) {}
            }

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

    private fun precargarLocalEnCoil(context: Context, fotoLocal: FotoRemota) {
        try {
            val loader = context.imageLoader
            listOfNotNull(fotoLocal.principal, fotoLocal.miniatura)
                .filter { it.startsWith("file://") }
                .forEach { uriFile ->
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(uriFile)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
        } catch (e: Exception) {
            Log.w("Firebase", "No se pudo precargar la foto local en Coil", e)
        }
    }

    fun precargarFotosFaltantes(context: Context, proyectos: List<ProyectoCrochet>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val diskCache = loader.diskCache
                val urlsParaDescargar = mutableSetOf<String>()

                for (proyecto in proyectos) {
                    if (proyecto.eliminado || proyecto.fechaEliminado != null) continue
                    val urls = listOfNotNull(proyecto.fotoMiniaturaUri, proyecto.fotoUri)
                    for (url in urls) {
                        if (url.startsWith("http")) {
                            val enDisco = diskCache?.openSnapshot(url)?.use { true } ?: false
                            if (!enDisco) {
                                urlsParaDescargar.add(url)
                            }
                        }
                    }
                }

                for (url in urlsParaDescargar) {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .diskCacheKey(url)
                        .memoryCacheKey(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build()
                    loader.enqueue(request)
                }
            } catch (e: Exception) {
                Log.w("Firebase", "Error al precargar fotos faltantes", e)
            }
        }
    }

    private fun String?.esUriLocal(): Boolean = !isNullOrBlank() && !startsWith("http")

    fun actualizarCarpetasProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina")
            .document(proyecto.id)
            .update("carpetas", proyecto.carpetas)
            .addOnSuccessListener { Log.d("Firebase", "Carpetas actualizadas") }
    }

    private fun obtenerPrefs(): SharedPreferences =
        getApplication<Application>().getSharedPreferences("crochet_borrador", Context.MODE_PRIVATE)

    fun guardarBorrador() {
        val prefs = obtenerPrefs().edit()
        prefs.putFloat("costoMateriales", costoMateriales.toFloat())
        prefs.putFloat("costoDiseno", costoDiseno.toFloat())
        prefs.putFloat("costoManoObra", costoManoObra.toFloat())
        prefs.putFloat("costoEmpaque", costoEmpaque.toFloat())
        prefs.putFloat("margenGanancia", margenGanancia.toFloat())
        prefs.putFloat("horasTrabajadasTemp", horasTrabajadasTemp.toFloat())
        prefs.putFloat("pagoPorHoraTemp", pagoPorHoraTemp.toFloat())
        prefs.putString("nombreTemp", nombreTemp)
        prefs.putString("fotoUriTemp", fotoUriTemp)
        prefs.putString("fotoMiniaturaUriTemp", fotoMiniaturaUriTemp)
        prefs.putString("idProyectoEnEdicion", idProyectoEnEdicion)
        prefs.putLong("fechaEnEdicion", fechaEnEdicion ?: -1L)
        prefs.putStringSet("carpetasTemp", carpetasTemp.toSet())

        val arrEstambres = JSONArray()
        for (est in listaEstambresGuardados) {
            val obj = JSONObject()
            obj.put("nombre", est.nombre)
            obj.put("costo", est.costo)
            obj.put("totales", est.totales)
            obj.put("usados", est.usados)
            arrEstambres.put(obj)
        }
        prefs.putString("estambres", arrEstambres.toString())

        val arrAccesorios = JSONArray()
        for (acc in listaAccesoriosGuardados) {
            val obj = JSONObject()
            obj.put("nombre", acc.nombre)
            obj.put("costo", acc.costo)
            obj.put("esCalculado", acc.esCalculado)
            obj.put("costoPaquete", acc.costoPaquete)
            obj.put("cantidadTotal", acc.cantidadTotal)
            obj.put("cantidadUsada", acc.cantidadUsada)
            arrAccesorios.put(obj)
        }
        prefs.putString("accesorios", arrAccesorios.toString())
        prefs.apply()
    }

    fun restaurarBorrador() {
        val prefs = obtenerPrefs()
        if (!prefs.contains("costoMateriales") && !prefs.contains("horasTrabajadasTemp")) return

        costoMateriales = prefs.getFloat("costoMateriales", 0f).toDouble()
        costoDiseno = prefs.getFloat("costoDiseno", 0f).toDouble()
        costoManoObra = prefs.getFloat("costoManoObra", 0f).toDouble()
        costoEmpaque = prefs.getFloat("costoEmpaque", 0f).toDouble()
        margenGanancia = prefs.getFloat("margenGanancia", 0f).toDouble()
        horasTrabajadasTemp = prefs.getFloat("horasTrabajadasTemp", 0f).toDouble()
        pagoPorHoraTemp = prefs.getFloat("pagoPorHoraTemp", 0f).toDouble()
        nombreTemp = prefs.getString("nombreTemp", "") ?: ""
        fotoUriTemp = prefs.getString("fotoUriTemp", null)
        fotoMiniaturaUriTemp = prefs.getString("fotoMiniaturaUriTemp", null)
        idProyectoEnEdicion = prefs.getString("idProyectoEnEdicion", null)
        val fEd = prefs.getLong("fechaEnEdicion", -1L)
        fechaEnEdicion = if (fEd != -1L) fEd else null
        carpetasTemp.clear()
        carpetasTemp.addAll(prefs.getStringSet("carpetasTemp", emptySet()) ?: emptySet())

        val strEstambres = prefs.getString("estambres", null)
        if (!strEstambres.isNullOrEmpty()) {
            listaEstambresGuardados.clear()
            try {
                val arr = JSONArray(strEstambres)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    listaEstambresGuardados.add(
                        EstambreGuardado(
                            nombre = obj.optString("nombre"),
                            costo = obj.optString("costo"),
                            totales = obj.optString("totales"),
                            usados = obj.optString("usados")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        val strAccesorios = prefs.getString("accesorios", null)
        if (!strAccesorios.isNullOrEmpty()) {
            listaAccesoriosGuardados.clear()
            try {
                val arr = JSONArray(strAccesorios)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    listaAccesoriosGuardados.add(
                        AccesorioGuardado(
                            nombre = obj.optString("nombre"),
                            costo = obj.optString("costo"),
                            esCalculado = obj.optBoolean("esCalculado", false),
                            costoPaquete = obj.optString("costoPaquete"),
                            cantidadTotal = obj.optString("cantidadTotal"),
                            cantidadUsada = obj.optString("cantidadUsada")
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        calcularTotal()
    }

    fun limpiarBorrador() {
        obtenerPrefs().edit().clear().apply()
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
        limpiarBorrador()
    }
}