package com.example.calculadoracrochet.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class FotosOptimizadas(
    val principal: File,
    val miniatura: File?
) {
    fun eliminarArchivosTemporales() {
        principal.delete()
        miniatura?.delete()
    }
}

object FotosOptimizador {
    private const val MAX_LADO_FOTO = 1_600
    private const val MAX_LADO_MINIATURA = 480
    private const val CALIDAD_INICIAL_FOTO = 82
    private const val CALIDAD_INICIAL_MINIATURA = 75
    private const val CALIDAD_MINIMA = 60
    private const val TAMANO_MAX_FOTO_BYTES = 1_200_000L
    private const val TAMANO_MAX_MINIATURA_BYTES = 150_000L

    fun optimizar(context: Context, uri: Uri): FotosOptimizadas {
        val resolver = context.contentResolver
        val dimensiones = leerDimensiones(resolver, uri)
        val bitmapDecodificado = resolver.abrir(uri) { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = calcularMuestreo(dimensiones.first, dimensiones.second, MAX_LADO_FOTO)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: error("No se pudo leer la foto seleccionada")

        var foto = bitmapDecodificado
        val orientada = aplicarOrientacion(foto, leerOrientacion(resolver, uri))
        if (orientada !== foto) foto.recycle()
        foto = orientada

        val reducida = escalarSiEsNecesario(foto, MAX_LADO_FOTO)
        if (reducida !== foto) foto.recycle()
        foto = reducida

        val directorio = File(context.cacheDir, "fotos_para_subir").apply { mkdirs() }
        val idArchivo = UUID.randomUUID().toString()
        val archivoPrincipal = File(directorio, "$idArchivo.jpg")

        try {
            guardarJpeg(foto, archivoPrincipal, CALIDAD_INICIAL_FOTO, TAMANO_MAX_FOTO_BYTES)

            val miniaturaBitmap = escalarSiEsNecesario(foto, MAX_LADO_MINIATURA)
            val archivoMiniatura = if (miniaturaBitmap === foto) {
                null
            } else {
                File(directorio, "${idArchivo}_miniatura.jpg").also { archivo ->
                    guardarJpeg(
                        miniaturaBitmap,
                        archivo,
                        CALIDAD_INICIAL_MINIATURA,
                        TAMANO_MAX_MINIATURA_BYTES
                    )
                }
            }
            if (miniaturaBitmap !== foto) miniaturaBitmap.recycle()

            return FotosOptimizadas(archivoPrincipal, archivoMiniatura)
        } catch (error: Exception) {
            archivoPrincipal.delete()
            throw error
        } finally {
            foto.recycle()
        }
    }

    private fun leerDimensiones(resolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.abrir(uri) { input -> BitmapFactory.decodeStream(input, null, opciones) }
        check(opciones.outWidth > 0 && opciones.outHeight > 0) { "El archivo seleccionado no es una imagen válida" }
        return opciones.outWidth to opciones.outHeight
    }

    private fun calcularMuestreo(ancho: Int, alto: Int, maxLado: Int): Int {
        var muestra = 1
        while (max(ancho, alto) / muestra > maxLado * 2) muestra *= 2
        return muestra
    }

    private fun escalarSiEsNecesario(bitmap: Bitmap, maxLado: Int): Bitmap {
        val ladoActual = max(bitmap.width, bitmap.height)
        if (ladoActual <= maxLado) return bitmap

        val escala = maxLado.toFloat() / ladoActual
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * escala).roundToInt().coerceAtLeast(1),
            (bitmap.height * escala).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun leerOrientacion(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.abrir(uri) { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun aplicarOrientacion(bitmap: Bitmap, orientacion: Int): Bitmap {
        val matriz = Matrix().apply {
            when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                else -> return bitmap
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }

    private fun guardarJpeg(bitmap: Bitmap, archivo: File, calidadInicial: Int, tamanoMaximo: Long) {
        var calidad = calidadInicial
        do {
            FileOutputStream(archivo).use { salida ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)) {
                    "No se pudo comprimir la foto"
                }
            }
            calidad -= 5
        } while (archivo.length() > tamanoMaximo && calidad >= CALIDAD_MINIMA)
    }

    private inline fun <T> ContentResolver.abrir(uri: Uri, bloque: (InputStream) -> T): T {
        return openInputStream(uri)?.use(bloque) ?: error("No se pudo abrir la foto seleccionada")
    }
}
