package com.example.calculadoracrochet.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.calculadoracrochet.data.ProyectosRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

class SubirFotoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val proyectoId = inputData.getString(KEY_PROYECTO_ID) ?: return Result.failure()
        val nombreProyecto = inputData.getString(KEY_NOMBRE_PROYECTO) ?: "proyecto"
        val rutaPrincipal = inputData.getString(KEY_RUTA_PRINCIPAL) ?: return Result.failure()
        val rutaMiniatura = inputData.getString(KEY_RUTA_MINIATURA)
        val urlsAnteriores = inputData.getNullableStringArray(KEY_URLS_ANTERIORES)?.filterNotNull() ?: emptyList()

        val archivoPrincipal = File(rutaPrincipal)
        if (!archivoPrincipal.exists()) {
            Log.e(TAG, "El archivo principal local no existe: $rutaPrincipal")
            return Result.failure()
        }

        val subidas = mutableListOf<StorageReference>()
        var escrituraIniciada = false

        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Auth anónimo falló o fue innecesario", e)
                }
            }

            val storage = FirebaseStorage.getInstance()
            val db = FirebaseFirestore.getInstance()

            val nombreLimpio = limpiarNombreParaStorage(nombreProyecto)
            val idCorto = proyectoId.replace("-", "").take(8)
            val carpetaLegible = "${nombreLimpio}_$idCorto"
            // The WorkRequest id survives retries, so a retry overwrites its own files instead of leaving copies
            val hashFoto = id.toString().replace("-", "").take(8)

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCacheControl("public,max-age=31536000,immutable")
                .build()

            val refPrincipal = storage.reference.child("fotos_proyectos/$carpetaLegible/${nombreLimpio}_${hashFoto}_foto.jpg")
            subidas += refPrincipal
            refPrincipal.putFile(Uri.fromFile(archivoPrincipal), metadata).await()
            val urlPrincipal = refPrincipal.downloadUrl.await().toString()

            val urlMiniatura = if (!rutaMiniatura.isNullOrBlank()) {
                val archivoMiniatura = File(rutaMiniatura)
                if (archivoMiniatura.exists()) {
                    val refMiniatura = storage.reference.child("fotos_proyectos/$carpetaLegible/${nombreLimpio}_${hashFoto}_miniatura.jpg")
                    subidas += refMiniatura
                    refMiniatura.putFile(Uri.fromFile(archivoMiniatura), metadata).await()
                    refMiniatura.downloadUrl.await().toString()
                } else {
                    urlPrincipal
                }
            } else {
                urlPrincipal
            }

            val documento = db.collection(ProyectosRepository.COLECCION).document(proyectoId)
            val actual = try {
                documento.get().await()
            } catch (_: Exception) {
                null
            }

            if (actual != null && (!actual.exists() || actual.getString("fotoUri").isNullOrBlank())) {
                // Project deleted or photo removed while uploading: nothing may point at these files
                borrarSubidas(subidas)
                borrarArchivosLocales(archivoPrincipal, rutaMiniatura)
                return Result.success()
            }

            // A replaced upload may have already written its URLs; they are not in urlsAnteriores
            val urlsEnFirestore = listOfNotNull(actual?.getString("fotoUri"), actual?.getString("fotoMiniaturaUri"))

            escrituraIniciada = true
            try {
                // update, not set: if the project was deleted meanwhile, set would bring it back as an empty project
                documento.update(mapOf("fotoUri" to urlPrincipal, "fotoMiniaturaUri" to urlMiniatura)).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
                borrarSubidas(subidas)
                borrarArchivosLocales(archivoPrincipal, rutaMiniatura)
                return Result.success()
            }

            val loader = applicationContext.imageLoader
            loader.execute(
                ImageRequest.Builder(applicationContext)
                    .data(urlMiniatura)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )

            if (urlPrincipal != urlMiniatura) {
                loader.execute(
                    ImageRequest.Builder(applicationContext)
                        .data(urlPrincipal)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }

            val urlsABorrar = (urlsAnteriores + urlsEnFirestore).distinct()
            for (url in urlsABorrar) {
                if (url.startsWith("http") && url != urlPrincipal && url != urlMiniatura) {
                    try {
                        if (usadaPorOtroProyecto(db, url, proyectoId)) continue
                        val refVieja = EliminarFotoWorker.obtenerReferenciaStorage(storage, url)
                            ?: storage.getReferenceFromUrl(url)
                        refVieja.delete().await()
                    } catch (_: Exception) {}
                }
            }

            borrarArchivosLocales(archivoPrincipal, rutaMiniatura)

            Log.d(TAG, "Fotos sincronizadas y cacheadas en carpeta: $carpetaLegible")
            Result.success()
        } catch (e: CancellationException) {
            // Replaced by a newer photo or stopped by the system: without the Firestore write nothing points here.
            // The local files stay, since a system stop is retried later.
            if (!escrituraIniciada) {
                withContext(NonCancellable) { borrarSubidas(subidas) }
            }
            throw e
        } catch (e: Exception) {
            if (runAttemptCount >= MAX_INTENTOS) {
                // Likely a permanent error (rules, quota). The photo stays local and keeps showing on this phone.
                Log.e(TAG, "Error subiendo fotos tras $runAttemptCount intentos; no se reintenta más", e)
                if (!escrituraIniciada) borrarSubidas(subidas)
                Result.failure()
            } else {
                Log.e(TAG, "Error subiendo fotos a Storage, reintentando…", e)
                Result.retry()
            }
        }
    }

    private suspend fun borrarSubidas(subidas: List<StorageReference>) {
        subidas.forEach { ref ->
            try {
                ref.delete().await()
            } catch (_: Exception) {}
        }
    }

    private fun borrarArchivosLocales(principal: File, rutaMiniatura: String?) {
        try {
            principal.delete()
            if (!rutaMiniatura.isNullOrBlank()) File(rutaMiniatura).delete()
        } catch (_: Exception) {}
    }

    /** Duplicated projects share the photo URLs, so those cannot be deleted from Storage. */
    private suspend fun usadaPorOtroProyecto(db: FirebaseFirestore, url: String, proyectoId: String): Boolean =
        listOf("fotoUri", "fotoMiniaturaUri").any { campo ->
            db.collection(ProyectosRepository.COLECCION)
                .whereEqualTo(campo, url)
                .get()
                .await()
                .documents
                .any { it.id != proyectoId }
        }

    private fun limpiarNombreParaStorage(nombre: String): String {
        val normalizado = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val limpio = normalizado.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (limpio.isBlank()) "proyecto" else limpio.take(30)
    }

    companion object {
        private const val TAG = "SubirFotoWorker"

        /** With WorkManager's exponential backoff (capped at 5 h) this is about two days of connected time. */
        private const val MAX_INTENTOS = 20

        const val KEY_PROYECTO_ID = "key_proyecto_id"
        const val KEY_NOMBRE_PROYECTO = "key_nombre_proyecto"
        const val KEY_RUTA_PRINCIPAL = "key_ruta_principal"
        const val KEY_RUTA_MINIATURA = "key_ruta_miniatura"
        const val KEY_URLS_ANTERIORES = "key_urls_anteriores"
    }
}