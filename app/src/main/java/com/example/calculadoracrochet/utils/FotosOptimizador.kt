package com.example.calculadoracrochet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.scale

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

    fun optimizarArchivo(cacheDir: File, archivoOrigen: File): FotosOptimizadas {
        val dimensiones = leerDimensionesArchivo(archivoOrigen)
        val muestra = calcularMuestreo(dimensiones.first, dimensiones.second, MAX_LADO_FOTO)

        val opciones = BitmapFactory.Options().apply {
            inSampleSize = muestra
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmapDecodificado = BitmapFactory.decodeFile(archivoOrigen.absolutePath, opciones)
            ?: error("No se pudo decodificar la imagen seleccionada")

        var foto = bitmapDecodificado
        val orientada = aplicarOrientacion(foto, leerOrientacionArchivo(archivoOrigen))
        if (orientada !== foto) foto.recycle()
        foto = orientada

        val reducida = escalarSiEsNecesario(foto, MAX_LADO_FOTO)
        if (reducida !== foto) foto.recycle()
        foto = reducida

        val directorio = File(cacheDir, "fotos_para_subir").apply { mkdirs() }
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

    fun optimizar(context: Context, uri: Uri): FotosOptimizadas {
        val temp = File(context.cacheDir, "temp_opt_${UUID.randomUUID()}.tmp")
        try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").copyTo(temp, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("No se pudo abrir la foto seleccionada")
            }
            return optimizarArchivo(context.cacheDir, temp)
        } finally {
            temp.delete()
        }
    }

    private fun leerDimensionesArchivo(archivo: File): Pair<Int, Int> {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, opciones)
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
        return bitmap.scale(
            (bitmap.width * escala).roundToInt().coerceAtLeast(1),
            (bitmap.height * escala).roundToInt().coerceAtLeast(1)
        )
    }

    private fun leerOrientacionArchivo(archivo: File): Int = try {
        val exif = ExifInterface(archivo.absolutePath)
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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
}