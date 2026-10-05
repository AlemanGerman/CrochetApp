package com.example.calculadoracrochet.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

object ColorExtractorHelper {

    data class ColorTarjetaConfig(
        val backgroundColor: Int,
        val textColorPrimario: Int,
        val textColorSecundario: Int,
        val iconTint: Int,
        val esOscuro: Boolean
    )

    private val cacheColores = LruCache<String, ColorTarjetaConfig>(100)

    fun obtenerColorEnCache(clave: String): ColorTarjetaConfig? {
        return cacheColores.get(clave)
    }

    fun extraerColor(
        bitmap: Bitmap,
        clave: String,
        onResultado: (ColorTarjetaConfig) -> Unit
    ) {
        val enCache = cacheColores.get(clave)
        if (enCache != null) {
            onResultado(enCache)
            return
        }

        if (bitmap.isRecycled) return

        try {
            Palette.from(bitmap)
                .maximumColorCount(16)
                .generate { palette ->
                    if (palette == null) return@generate

                    val swatch = palette.dominantSwatch
                        ?: palette.vibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.swatches.maxByOrNull { it.population }
                        ?: return@generate

                    val colorDominante = swatch.rgb
                    val contrasteBlanco = ColorUtils.calculateContrast(Color.WHITE, colorDominante)
                    val contrasteNegro = ColorUtils.calculateContrast(Color.BLACK, colorDominante)
                    val esFondoOscuro = contrasteBlanco >= contrasteNegro

                    val config = if (esFondoOscuro) {
                        ColorTarjetaConfig(
                            backgroundColor = colorDominante,
                            textColorPrimario = Color.WHITE,
                            textColorSecundario = Color.parseColor("#E0E0E0"),
                            iconTint = Color.WHITE,
                            esOscuro = true
                        )
                    } else {
                        ColorTarjetaConfig(
                            backgroundColor = colorDominante,
                            textColorPrimario = Color.BLACK,
                            textColorSecundario = Color.parseColor("#424242"),
                            iconTint = Color.BLACK,
                            esOscuro = false
                        )
                    }

                    cacheColores.put(clave, config)
                    onResultado(config)
                }
        } catch (_: Exception) {
            // Protección ante bitmaps con formatos especiales
        }
    }

    fun limpiarCache() {
        cacheColores.evictAll()
    }
}
