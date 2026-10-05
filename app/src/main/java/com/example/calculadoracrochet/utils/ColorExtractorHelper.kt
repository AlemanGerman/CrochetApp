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

    private fun tieneColorSignificativo(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val saturacion = hsv[1]
        val brillo = hsv[2]

        // Descarta fondos casi blancos puros (ej. mesa o sábana donde se fotografió el tejido)
        if (saturacion < 0.12f && brillo > 0.85f) return false
        // Descarta fondos casi negros puros
        if (brillo < 0.10f) return false
        // Descarta grises planos sin saturación cromática
        if (saturacion < 0.08f) return false

        return true
    }

    private fun seleccionarMejorSwatch(palette: Palette): Palette.Swatch? {
        // 1. Si el color dominante tiene tono cromático real (no es una mesa/fondo neutro blanco), se usa
        val dominante = palette.dominantSwatch
        if (dominante != null && tieneColorSignificativo(dominante.rgb)) {
            return dominante
        }

        // 2. Si el dominante es un fondo neutro (ej. mesa blanca), se extrae el color del estambre/creación
        val swatchConColor = palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.lightMutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.swatches
                .filter { tieneColorSignificativo(it.rgb) }
                .maxByOrNull { it.population }

        if (swatchConColor != null) {
            return swatchConColor
        }

        // 3. Fallback: Si la creación misma es blanca/negra/gris o no hay swatches cromáticos
        return dominante ?: palette.swatches.maxByOrNull { it.population }
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
                .maximumColorCount(24)
                .generate { palette ->
                    if (palette == null) return@generate

                    val swatch = seleccionarMejorSwatch(palette) ?: return@generate

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
                            textColorSecundario = Color.parseColor("#374151"),
                            iconTint = Color.BLACK,
                            esOscuro = false
                        )
                    }

                    cacheColores.put(clave, config)
                    onResultado(config)
                }
        } catch (_: Exception) {
            // Protección ante bitmaps reciclados o de formato especial
        }
    }

    fun limpiarCache() {
        cacheColores.evictAll()
    }
}
