package com.example.calculadoracrochet.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder

class EliminarFotoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val urls = inputData.getStringArray(KEY_URLS)?.toList()
            ?: listOfNotNull(inputData.getString(KEY_URL_REMOTA))

        val urlsHttp = urls.filter { it.startsWith("http") }
        if (urlsHttp.isEmpty()) return Result.success()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                Log.w("EliminarFotoWorker", "Auth anónimo innecesario o falló", e)
            }
        }

        val storage = FirebaseStorage.getInstance()
        for (url in urlsHttp) {
            try {
                val ref = obtenerReferenciaStorage(storage, url)
                ref?.delete()?.await()
                Log.d("EliminarFotoWorker", "Foto eliminada en Storage: $url")
            } catch (e: StorageException) {
                if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND || e.httpResultCode == 404) {
                    Log.d("EliminarFotoWorker", "La foto ya no existía en Storage (404)")
                } else {
                    return Result.retry()
                }
            } catch (e: Exception) {
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_URLS = "key_urls"
        const val KEY_URL_REMOTA = "key_url_remota"

        fun obtenerReferenciaStorage(storage: FirebaseStorage, url: String): StorageReference? {
            try {
                return storage.getReferenceFromUrl(url)
            } catch (_: Exception) {}

            return try {
                val patron = Regex("/o/([^?]+)")
                val match = patron.find(url)
                if (match != null) {
                    val rutaCodificada = match.groupValues[1]
                    val rutaReal = URLDecoder.decode(rutaCodificada, "UTF-8")
                    storage.reference.child(rutaReal)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}