package com.example.calculadoracrochet.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.Normalizer
import java.util.UUID

class SubirFotoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val proyectoId = inputData.getString(KEY_PROYECTO_ID) ?: return Result.failure()
        val nombreProyecto = inputData.getString(KEY_NOMBRE_PROYECTO) ?: "proyecto"
        val rutaPrincipal = inputData.getString(KEY_RUTA_PRINCIPAL) ?: return Result.failure()
        val rutaMiniatura = inputData.getString(KEY_RUTA_MINIATURA)
        val urlsAnteriores = inputData.getStringArray(KEY_URLS_ANTERIORES)?.toList() ?: emptyList()

        val archivoPrincipal = File(rutaPrincipal)
        if (!archivoPrincipal.exists()) {
            Log.e("SubirFotoWorker", "El archivo principal local no existe: $rutaPrincipal")
            return Result.failure()
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    Log.w("SubirFotoWorker", "Auth anónimo falló o fue innecesario", e)
                }
            }

            val storage = FirebaseStorage.getInstance()
            val db = FirebaseFirestore.getInstance()

            val nombreLimpio = limpiarNombreParaStorage(nombreProyecto)
            val idCorto = proyectoId.replace("-", "").take(8)
            val carpetaLegible = "${nombreLimpio}_$idCorto"
            val hashFoto = UUID.randomUUID().toString().take(6)

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCacheControl("public,max-age=31536000,immutable")
                .build()

            val refPrincipal = storage.reference.child("fotos_proyectos/$carpetaLegible/${nombreLimpio}_${hashFoto}_foto.jpg")
            refPrincipal.putFile(Uri.fromFile(archivoPrincipal), metadata).await()
            val urlPrincipal = refPrincipal.downloadUrl.await().toString()

            val urlMiniatura = if (!rutaMiniatura.isNullOrBlank()) {
                val archivoMiniatura = File(rutaMiniatura)
                if (archivoMiniatura.exists()) {
                    val refMiniatura = storage.reference.child("fotos_proyectos/$carpetaLegible/${nombreLimpio}_${hashFoto}_miniatura.jpg")
                    refMiniatura.putFile(Uri.fromFile(archivoMiniatura), metadata).await()
                    refMiniatura.downloadUrl.await().toString()
                } else {
                    urlPrincipal
                }
            } else {
                urlPrincipal
            }

            db.collection("proyectos_mina")
                .document(proyectoId)
                .set(
                    mapOf(
                        "fotoUri" to urlPrincipal,
                        "fotoMiniaturaUri" to urlMiniatura
                    ),
                    SetOptions.merge()
                )
                .await()

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

            for (url in urlsAnteriores) {
                if (url.startsWith("http") && url != urlPrincipal && url != urlMiniatura) {
                    try {
                        val refVieja = EliminarFotoWorker.obtenerReferenciaStorage(storage, url)
                            ?: storage.getReferenceFromUrl(url)
                        refVieja.delete().await()
                    } catch (_: Exception) {}
                }
            }

            try {
                archivoPrincipal.delete()
                if (!rutaMiniatura.isNullOrBlank()) {
                    File(rutaMiniatura).delete()
                }
            } catch (_: Exception) {}

            Log.d("SubirFotoWorker", "Fotos sincronizadas y cacheadas en carpeta: $carpetaLegible")
            Result.success()
        } catch (e: Exception) {
            Log.e("SubirFotoWorker", "Error subiendo fotos a Storage, reintentando…", e)
            Result.retry()
        }
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
        const val KEY_PROYECTO_ID = "key_proyecto_id"
        const val KEY_NOMBRE_PROYECTO = "key_nombre_proyecto"
        const val KEY_RUTA_PRINCIPAL = "key_ruta_principal"
        const val KEY_RUTA_MINIATURA = "key_ruta_miniatura"
        const val KEY_URLS_ANTERIORES = "key_urls_anteriores"
    }
}