package com.example.calculadoracrochet

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class CrochetApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "coil_fotos"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .build()
}
