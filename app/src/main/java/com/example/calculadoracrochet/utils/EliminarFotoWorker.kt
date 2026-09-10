package com.example.calculadoracrochet.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await

class EliminarFotoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val urls = inputData.getStringArray(KEY_URLS)?.toList() ?: return Result.success()
        val urlsHttp = urls.filter { it.startsWith("http") }
        if (urlsHttp.isEmpty()) return Result.success()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                Log.w("EliminarFotoWorker", "Auth anónimo falló", e)
            }
        }

        val storage = FirebaseStorage.getInstance()
        for (url in urlsHttp) {
            try {
                storage.getReferenceFromUrl(url).delete().await()
                Log.d("EliminarFotoWorker", "Foto eliminada de Storage: $url")
            } catch (e: StorageException) {
                if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND || e.httpResultCode == 404) {
                    Log.d("EliminarFotoWorker", "La foto ya no existía en Storage")
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
    }
}