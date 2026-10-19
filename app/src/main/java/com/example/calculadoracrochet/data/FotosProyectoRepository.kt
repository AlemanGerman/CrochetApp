package com.example.calculadoracrochet.data

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.example.calculadoracrochet.viewmodel.estaEnPapelera
import com.example.calculadoracrochet.workers.EliminarFotoWorker
import com.example.calculadoracrochet.workers.SubirFotoWorker
import java.io.File
import java.util.UUID

/** Main photo + thumbnail of a project, as stored in Firestore (http URL or file:// URI). */
data class FotoProyecto(
    val principal: String?,
    val miniatura: String?
) {
    val rutas: List<String> get() = listOfNotNull(principal, miniatura)

    companion object {
        val VACIA = FotoProyecto(null, null)

        fun de(proyecto: ProyectoCrochet) = FotoProyecto(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
    }
}

data class FotosLocalesPersistentes(
    val principal: File,
    val miniatura: File?
) {
    fun comoFotoProyecto(): FotoProyecto {
        val uriPrincipal = "file://${principal.absolutePath}"
        return FotoProyecto(
            principal = uriPrincipal,
            miniatura = miniatura?.let { "file://${it.absolutePath}" } ?: uriPrincipal
        )
    }
}

/**
 * Local photo storage, background upload/deletion through WorkManager and Coil cache warm-up.
 * Has no knowledge of the project list: deciding whether a URL is still in use is up to the caller.
 */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class FotosProyectoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    private val conRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Copies, optimizes and persists the picked photo in filesDir. Blocking: call from Dispatchers.IO. */
    fun procesarYGuardarFotoLocal(idProyecto: String, uriLocal: Uri): FotosLocalesPersistentes? {
        val cacheDir = appContext.cacheDir
        val tempDir = File(cacheDir, "picker_temp").apply { mkdirs() }
        val archivoTemporal = File(tempDir, "${UUID.randomUUID()}.tmp")

        try {
            if (uriLocal.scheme == "file") {
                File(uriLocal.path ?: "").copyTo(archivoTemporal, overwrite = true)
            } else {
                appContext.contentResolver.openInputStream(uriLocal)?.use { input ->
                    archivoTemporal.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }

            val fotosOptimizadas = FotosOptimizador.optimizarArchivo(cacheDir, archivoTemporal)
            val dirDestino = File(appContext.filesDir, "fotos_locales").apply { if (!exists()) mkdirs() }

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
            Log.e(TAG, "Error al procesar foto local", e)
            return null
        } finally {
            if (archivoTemporal.exists()) archivoTemporal.delete()
        }
    }

    fun encolarSubida(
        idProyecto: String,
        nombreProyecto: String,
        fotos: FotosLocalesPersistentes,
        urlsAnteriores: List<String> = emptyList()
    ) {
        val datos = Data.Builder()
            .putString(SubirFotoWorker.KEY_PROYECTO_ID, idProyecto)
            .putString(SubirFotoWorker.KEY_NOMBRE_PROYECTO, nombreProyecto)
            .putString(SubirFotoWorker.KEY_RUTA_PRINCIPAL, fotos.principal.absolutePath)
            .putString(SubirFotoWorker.KEY_RUTA_MINIATURA, fotos.miniatura?.absolutePath)
            .putStringArray(SubirFotoWorker.KEY_URLS_ANTERIORES, urlsAnteriores.toTypedArray())
            .build()

        val request = OneTimeWorkRequestBuilder<SubirFotoWorker>()
            .setConstraints(conRed)
            .setInputData(datos)
            .addTag(nombreTrabajoSubida(idProyecto))
            .build()

        workManager.enqueueUniqueWork(nombreTrabajoSubida(idProyecto), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelarSubida(idProyecto: String) {
        workManager.cancelUniqueWork(nombreTrabajoSubida(idProyecto))
    }

    /** Evicts the URLs from Coil's disk cache and enqueues their deletion from Firebase Storage. */
    fun encolarEliminacionRemota(urls: List<String>) {
        if (urls.isEmpty()) return

        val diskCache = appContext.imageLoader.diskCache
        urls.forEach { url ->
            try {
                diskCache?.remove(url)
            } catch (_: Exception) {}
        }

        val request = OneTimeWorkRequestBuilder<EliminarFotoWorker>()
            .setConstraints(conRed)
            .setInputData(Data.Builder().putStringArray(EliminarFotoWorker.KEY_URLS, urls.toTypedArray()).build())
            .build()

        workManager.enqueue(request)
    }

    fun borrarArchivosLocales(fotos: FotoProyecto) {
        fotos.rutas.filter { it.esLocal() }.forEach { uriStr ->
            try {
                File(uriStr.removePrefix("file://")).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error al borrar archivo local: $uriStr", e)
            }
        }
    }

    fun precargarLocal(fotos: FotoProyecto) {
        try {
            val loader = appContext.imageLoader
            fotos.rutas.filter { it.esLocal() }.forEach { uriFile ->
                loader.enqueue(
                    ImageRequest.Builder(appContext)
                        .data(uriFile)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo precargar la foto local en Coil", e)
        }
    }

    /** Downloads into the disk cache the remote photos of active projects that are not cached yet. Blocking. */
    fun precargarFaltantes(proyectos: List<ProyectoCrochet>) {
        try {
            val loader = appContext.imageLoader
            val diskCache = loader.diskCache

            val urlsParaDescargar = proyectos
                .filterNot { it.estaEnPapelera }
                .flatMap { listOfNotNull(it.fotoMiniaturaUri, it.fotoUri) }
                .filter { it.esRemota() }
                .filter { url -> diskCache?.openSnapshot(url)?.use { true } != true }
                .toSet()

            for (url in urlsParaDescargar) {
                val request = ImageRequest.Builder(appContext)
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
            Log.w(TAG, "Error al precargar fotos faltantes", e)
        }
    }

    companion object {
        private const val TAG = "Firebase"

        fun nombreTrabajoSubida(idProyecto: String) = "subir_$idProyecto"
    }
}

fun String.esRemota(): Boolean = startsWith("http")

fun String.esLocal(): Boolean = startsWith("file://")
