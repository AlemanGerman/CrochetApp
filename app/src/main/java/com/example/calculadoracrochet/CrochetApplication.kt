package com.example.calculadoracrochet

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class CrochetApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val cacheFolder = File(filesDir, "coil_fotos")
        val oldCacheFolder = File(cacheDir, "coil_fotos")
        if (oldCacheFolder.exists() && !cacheFolder.exists()) {
            oldCacheFolder.renameTo(cacheFolder)
        }

        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheFolder)
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
