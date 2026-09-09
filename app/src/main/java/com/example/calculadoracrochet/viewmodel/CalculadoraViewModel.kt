package com.example.calculadoracrochet.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.calculadoracrochet.utils.FotosOptimizadas
import com.example.calculadoracrochet.utils.FotosOptimizador
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class EstambreGuardado(var nombre: String = "", var costo: String = "", var totales: String = "", var usados: String = "")
data class AccesorioGuardado(var nombre: String = "", var costo: String = "")

data class ProyectoCrochet(
    var id: String = UUID.randomUUID().toString(),
    var nombre: String = "Sin nombre",
    var fotoUri: String? = null,
    var fotoMiniaturaUri: String? = null,
    var fechaGuardado: Long = System.currentTimeMillis(),
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

class CalculadoraViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
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

    init {
        cargarProyectosDeLaNube()
    }

    fun cargarProyectosDeLaNube() {
        db.collection("proyectos_mina")
            .addSnapshotListener { documentos, error ->
                if (error != null || documentos == null) return@addSnapshotListener

                historialProyectos.clear()
                for (documento in documentos) {
                    val proyecto = documento.toObject(ProyectoCrochet::class.java)
                    historialProyectos.add(proyecto)
                }

                historialProyectos.sortByDescending { it.fechaGuardado }
                proyectosEnVivo.postValue(historialProyectos)
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

        fun escribirEnFirestore(fotoParaGuardar: FotoRemota, fotoRecienSubida: Boolean = false) {
            val nuevoProyecto = ProyectoCrochet(
                id = idNuevo,
                nombre = nombre,
                fotoUri = fotoParaGuardar.principal,
                fotoMiniaturaUri = fotoParaGuardar.miniatura,
                fechaGuardado = fechaEnEdicion ?: System.currentTimeMillis(),
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
                    Log.d("Firebase", "Proyecto guardado con éxito")
                    if (fotoParaGuardar != fotoAnterior) {
                        borrarFotosNoReferenciadas(fotoAnterior, idProyectoExcluido = idNuevo)
                    }
                    limpiarCalculoActual()
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error al guardar en Firestore", e)
                    if (fotoRecienSubida) borrarFotosDeStorage(fotoParaGuardar)
                    onComplete(false)
                }
        }

        if (fotoUri.esUriLocal()) {
            subirFoto(context, idNuevo, Uri.parse(fotoUri), onSuccess = { fotoRemota ->
                escribirEnFirestore(fotoRemota, fotoRecienSubida = true)
            }, onFailure = { onComplete(false) })
        } else {
            val miniatura = if (fotoUri.isNullOrBlank()) null else fotoMiniaturaUriTemp
            escribirEnFirestore(FotoRemota(fotoUri, miniatura))
        }
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet, onComplete: (Boolean) -> Unit) {
        // 1. Borramos el documento de Firestore primero
        db.collection("proyectos_mina").document(proyecto.id).delete()
            .addOnSuccessListener {
                // 2. Limpiamos en segundo plano las fotos que ya no se usan
                borrarFotosNoReferenciadas(
                    FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri),
                    idProyectoExcluido = proyecto.id
                )
                onComplete(true)
            }
            .addOnFailureListener { error ->
                Log.e("Firebase", "Error al eliminar proyecto de Firestore", error)
                onComplete(false)
            }
    }

    fun actualizarFotoProyecto(
        context: Context,
        proyecto: ProyectoCrochet,
        fotoLocal: String,
        onComplete: (Boolean) -> Unit
    ) {
        subirFoto(context, proyecto.id, Uri.parse(fotoLocal), onSuccess = { fotoNueva ->
            db.collection("proyectos_mina").document(proyecto.id)
                .update(
                    mapOf(
                        "fotoUri" to fotoNueva.principal,
                        "fotoMiniaturaUri" to fotoNueva.miniatura
                    )
                )
                .addOnSuccessListener {
                    val fotoAnterior = FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
                    proyecto.fotoUri = fotoNueva.principal
                    proyecto.fotoMiniaturaUri = fotoNueva.miniatura
                    borrarFotosNoReferenciadas(fotoAnterior, idProyectoExcluido = proyecto.id)
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error al guardar la URL de la foto", e)
                    borrarFotosDeStorage(fotoNueva)
                    onComplete(false)
                }
        }, onFailure = {
            onComplete(false)
        })
    }

    fun eliminarFotoProyecto(proyecto: ProyectoCrochet, onComplete: (Boolean) -> Unit) {
        val fotoAnterior = FotoRemota(proyecto.fotoUri, proyecto.fotoMiniaturaUri)

        // 1. Quitamos las referencias en Firestore primero
        db.collection("proyectos_mina").document(proyecto.id)
            .update(mapOf("fotoUri" to null, "fotoMiniaturaUri" to null))
            .addOnSuccessListener {
                proyecto.fotoUri = null
                proyecto.fotoMiniaturaUri = null
                // 2. Borramos en segundo plano de Storage
                borrarFotosNoReferenciadas(fotoAnterior, idProyectoExcluido = proyecto.id)
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error al eliminar la foto del proyecto", e)
                onComplete(false)
            }
    }

    private fun subirFoto(
        context: Context,
        idProyecto: String,
        uriLocal: Uri,
        onSuccess: (FotoRemota) -> Unit,
        onFailure: () -> Unit
    ) {
        val cacheDir = context.cacheDir
        val archivoTemporal = try {
            val dirTemp = File(cacheDir, "picker_temp").apply { mkdirs() }
            val temp = File(dirTemp, "${UUID.randomUUID()}.tmp")
            if (uriLocal.scheme == "file") {
                File(uriLocal.path ?: "").copyTo(temp, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uriLocal)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("No se pudo abrir el stream de la URI")
            }
            temp
        } catch (e: Exception) {
            Log.e("Firebase", "Error al extraer la imagen seleccionada", e)
            handlerPrincipal.post(onFailure)
            return
        }

        val contextoApp = context.applicationContext
        hiloFotos.execute {
            val fotosOptimizadas = try {
                FotosOptimizador.optimizarArchivo(cacheDir, archivoTemporal)
            } catch (error: Exception) {
                Log.e("Firebase", "No se pudo optimizar la foto seleccionada", error)
                handlerPrincipal.post(onFailure)
                return@execute
            } finally {
                archivoTemporal.delete()
            }

            handlerPrincipal.post {
                subirFotosOptimizadas(contextoApp, idProyecto, fotosOptimizadas, onSuccess, onFailure)
            }
        }
    }

    private fun subirFotosOptimizadas(
        context: Context,
        idProyecto: String,
        fotos: FotosOptimizadas,
        onSuccess: (FotoRemota) -> Unit,
        onFailure: () -> Unit
    ) {
        val rutaBase = "fotos_proyectos/$idProyecto/${UUID.randomUUID()}"
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCacheControl("public,max-age=31536000,immutable")
            .build()
        val referenciaPrincipal = storage.reference.child("${rutaBase}_foto.jpg")
        val referenciaMiniatura = fotos.miniatura?.let {
            storage.reference.child("${rutaBase}_miniatura.jpg")
        }

        fun fallo(error: Exception) {
            Log.e("Firebase", "No se pudo respaldar la foto en Storage", error)
            fotos.eliminarArchivosTemporales()
            onFailure()
        }

        fun precargarEnCoil(fotoRemota: FotoRemota) {
            try {
                val loader = context.imageLoader
                fotoRemota.principal?.let { url ->
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
                fotoRemota.miniatura?.let { urlMin ->
                    if (urlMin != fotoRemota.principal) {
                        loader.enqueue(
                            ImageRequest.Builder(context)
                                .data(urlMin)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("Firebase", "No se pudo precargar la foto en Coil", e)
            }
        }

        fun ejecutarSubida() {
            referenciaPrincipal.putFile(Uri.fromFile(fotos.principal), metadata)
                .continueWithTask { tareaSubida ->
                    if (!tareaSubida.isSuccessful) {
                        throw tareaSubida.exception ?: IllegalStateException("No se pudo subir la foto")
                    }
                    referenciaPrincipal.downloadUrl
                }
                .addOnSuccessListener { urlPrincipal ->
                    if (referenciaMiniatura == null) {
                        fotos.eliminarArchivosTemporales()
                        val fotoRemota = FotoRemota(urlPrincipal.toString(), urlPrincipal.toString())
                        precargarEnCoil(fotoRemota)
                        onSuccess(fotoRemota)
                        return@addOnSuccessListener
                    }

                    referenciaMiniatura.putFile(Uri.fromFile(fotos.miniatura), metadata)
                        .continueWithTask { tareaSubida ->
                            if (!tareaSubida.isSuccessful) {
                                throw tareaSubida.exception ?: IllegalStateException("No se pudo subir la miniatura")
                            }
                            referenciaMiniatura.downloadUrl
                        }
                        .addOnSuccessListener { urlMiniatura ->
                            fotos.eliminarArchivosTemporales()
                            val fotoRemota = FotoRemota(urlPrincipal.toString(), urlMiniatura.toString())
                            precargarEnCoil(fotoRemota)
                            onSuccess(fotoRemota)
                        }
                        .addOnFailureListener(::fallo)
                }
                .addOnFailureListener(::fallo)
        }

        asegurarSesionEnFirebase {
            ejecutarSubida()
        }
    }

    private fun asegurarSesionEnFirebase(onListo: () -> Unit) {
        if (auth.currentUser != null) {
            onListo()
            return
        }

        auth.signInAnonymously()
            .addOnCompleteListener {
                onListo()
            }
    }

    private fun borrarFotosNoReferenciadas(
        fotos: FotoRemota,
        idProyectoExcluido: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val urls = setOf(fotos.principal, fotos.miniatura).filterNotNull()
        if (urls.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        db.collection("proyectos_mina").get()
            .addOnSuccessListener { documentos ->
                val urlsEnUso = documentos
                    .filter { it.id != idProyectoExcluido }
                    .flatMap { documento ->
                        listOf(
                            documento.getString("fotoUri"),
                            documento.getString("fotoMiniaturaUri")
                        )
                    }
                    .toSet()
                val fotosSinReferencias = FotoRemota(
                    principal = fotos.principal?.takeUnless { it in urlsEnUso },
                    miniatura = fotos.miniatura?.takeUnless { it in urlsEnUso }
                )
                eliminarFotosDeStorage(fotosSinReferencias) { eliminadas ->
                    onComplete?.invoke(eliminadas)
                }
            }
            .addOnFailureListener { error ->
                Log.e("Firebase", "No se pudieron verificar las referencias de fotos", error)
                onComplete?.invoke(false)
            }
    }

    private fun borrarFotosDeStorage(fotos: FotoRemota) {
        eliminarFotosDeStorage(fotos)
    }

    private fun eliminarFotosDeStorage(fotos: FotoRemota, onComplete: ((Boolean) -> Unit)? = null) {
        val urls = setOf(fotos.principal, fotos.miniatura)
            .filterNotNull()
            .filter { it.startsWith("http") }
        if (urls.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        asegurarSesionEnFirebase {
            val tareas = urls.mapNotNull { url ->
                try {
                    storage.getReferenceFromUrl(url).delete()
                } catch (error: Exception) {
                    Log.w("Firebase", "URL no pertenece a Storage o ya fue descartada: $url", error)
                    null
                }
            }
            if (tareas.isEmpty()) {
                onComplete?.invoke(true)
                return@asegurarSesionEnFirebase
            }

            Tasks.whenAllComplete(tareas).addOnCompleteListener {
                val seEliminoTodo = tareas.all { tarea ->
                    val ex = tarea.exception as? StorageException
                    val msg = tarea.exception?.message?.lowercase() ?: ""
                    tarea.isSuccessful ||
                            ex?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                            ex?.httpResultCode == 404 ||
                            msg.contains("does not exist") ||
                            msg.contains("not found")
                }
                if (!seEliminoTodo) {
                    Log.w("Firebase", "Algunas fotos no se encontraron en Storage al limpiar (pudo haberse borrado antes)")
                }
                onComplete?.invoke(seEliminoTodo)
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