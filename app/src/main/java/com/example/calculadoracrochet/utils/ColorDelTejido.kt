package com.example.calculadoracrochet.utils

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds the color of the crochet piece in a small photo, ignoring the surface it was photographed on.
 *
 * The most common color of a photo is usually the table, bed or wall behind the piece, so the
 * "dominant" color picked the background most of the time. Here the photo is grouped into a few
 * colors (k-means in CIELAB, where distances match how different colors look), and each group is
 * scored by how much of it is near the center (where the piece is framed) and how little of it
 * touches the edges (where the background is). Chromatic groups get a bonus over grays and whites.
 */
object ColorDelTejido {

    /** Side of the image analyzed, in pixels. Larger photos are scaled down to this before calling [elegir]. */
    const val LADO_MUESTRA = 48

    private const val GRUPOS = 6
    private const val ITERACIONES = 10
    private const val SIGMA_CENTRO = 0.28
    private const val MARGEN_BORDE = 0.14

    /** Below this chroma a color reads as gray, white, black or a very pale beige. */
    private const val CROMA_NEUTRO = 10.0

    /** Groups closer than this (ΔE) are the same yarn in light and shadow. */
    private const val DISTANCIA_MISMO_COLOR = 16.0

    /**
     * Color of the piece as opaque ARGB, or null when it is white or very pale (the card then
     * keeps its normal look instead of turning into a gray-ish white).
     */
    fun elegir(pixeles: IntArray, ancho: Int, alto: Int): Int? {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return null

        val muestras = ArrayList<Muestra>(ancho * alto)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                val argb = pixeles[y * ancho + x]
                if ((argb ushr 24) < 128) continue
                val nx = (x + 0.5) / ancho
                val ny = (y + 0.5) / alto
                val dx = nx - 0.5
                val dy = ny - 0.5
                val pesoCentro = exp(-(dx * dx + dy * dy) / (2 * SIGMA_CENTRO * SIGMA_CENTRO))
                val esBorde = nx < MARGEN_BORDE || nx > 1 - MARGEN_BORDE || ny < MARGEN_BORDE || ny > 1 - MARGEN_BORDE
                muestras += Muestra(Lab.desdeArgb(argb), pesoCentro, esBorde)
            }
        }
        if (muestras.isEmpty()) return null

        val grupos = agrupar(muestras)
        val totalCentro = muestras.sumOf { it.pesoCentro }
        val totalBorde = muestras.count { it.esBorde }.coerceAtLeast(1)

        val puntuados = grupos.filter { it.miembros.isNotEmpty() }.map { grupo ->
            val centro = grupo.miembros.sumOf { it.pesoCentro } / totalCentro
            val borde = grupo.miembros.count { it.esBorde }.toDouble() / totalBorde
            // Mostly on the edges: background. Never fully discarded, a tightly framed piece also touches them
            val protagonismo = maxOf(centro - 0.6 * borde, 0.2 * centro)
            val viveza = 0.35 + (grupo.centroide.croma / 40.0).coerceAtMost(1.0)
            GrupoPuntuado(grupo, centro, protagonismo * viveza)
        }
        val ganador = puntuados.maxByOrNull { it.puntaje } ?: return null

        // The same yarn in light and shadow may have been split: blend the groups that look alike
        val parecidos = puntuados.filter { it.grupo.centroide.distancia(ganador.grupo.centroide) < DISTANCIA_MISMO_COLOR }
        val pesoTotal = parecidos.sumOf { it.centro }.takeIf { it > 0 } ?: 1.0
        val color = Lab(
            l = parecidos.sumOf { it.grupo.centroide.l * it.centro } / pesoTotal,
            a = parecidos.sumOf { it.grupo.centroide.a * it.centro } / pesoTotal,
            b = parecidos.sumOf { it.grupo.centroide.b * it.centro } / pesoTotal
        )

        if (color.croma < CROMA_NEUTRO && color.l > 72) return null
        // Keeps the card readable: neither black nor washed out, same hue
        return color.copy(l = color.l.coerceIn(24.0, 86.0)).aArgb()
    }

    private class Muestra(val lab: Lab, val pesoCentro: Double, val esBorde: Boolean)

    private class Grupo(var centroide: Lab) {
        val miembros = ArrayList<Muestra>()
    }

    private class GrupoPuntuado(val grupo: Grupo, val centro: Double, val puntaje: Double)

    /** k-means with a deterministic start: the most central sample, then each time the one farthest from the chosen. */
    private fun agrupar(muestras: List<Muestra>): List<Grupo> {
        val iniciales = ArrayList<Lab>(GRUPOS)
        iniciales += muestras.maxBy { it.pesoCentro }.lab
        while (iniciales.size < GRUPOS) {
            val lejana = muestras.maxBy { m -> iniciales.minOf { it.distancia(m.lab) } }
            if (iniciales.minOf { it.distancia(lejana.lab) } < 1.0) break
            iniciales += lejana.lab
        }
        val grupos = iniciales.map { Grupo(it) }

        repeat(ITERACIONES) {
            grupos.forEach { it.miembros.clear() }
            muestras.forEach { m -> grupos.minBy { it.centroide.distancia(m.lab) }.miembros += m }
            var movido = false
            grupos.forEach { g ->
                if (g.miembros.isEmpty()) return@forEach
                val nuevo = Lab(
                    l = g.miembros.sumOf { it.lab.l } / g.miembros.size,
                    a = g.miembros.sumOf { it.lab.a } / g.miembros.size,
                    b = g.miembros.sumOf { it.lab.b } / g.miembros.size
                )
                if (nuevo.distancia(g.centroide) > 0.5) movido = true
                g.centroide = nuevo
            }
            if (!movido) return grupos
        }
        return grupos
    }

    /** CIELAB color (D65). */
    internal data class Lab(val l: Double, val a: Double, val b: Double) {
        val croma: Double get() = sqrt(a * a + b * b)

        fun distancia(otro: Lab): Double {
            val dl = l - otro.l
            val da = a - otro.a
            val db = b - otro.b
            return sqrt(dl * dl + da * da + db * db)
        }

        fun aArgb(): Int {
            val fy = (l + 16) / 116
            val fx = fy + a / 500
            val fz = fy - b / 200
            val x = 0.95047 * fInversa(fx)
            val y = 1.00000 * fInversa(fy)
            val z = 1.08883 * fInversa(fz)
            val r = aSrgb(3.2404542 * x - 1.5371385 * y - 0.4985314 * z)
            val g = aSrgb(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z)
            val bl = aSrgb(0.0556434 * x - 0.2040259 * y + 1.0572252 * z)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        companion object {
            fun desdeArgb(argb: Int): Lab {
                val r = lineal((argb shr 16) and 0xFF)
                val g = lineal((argb shr 8) and 0xFF)
                val b = lineal(argb and 0xFF)
                val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
                val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
                val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
                val fx = f(x)
                val fy = f(y)
                val fz = f(z)
                return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
            }

            private fun lineal(canal: Int): Double {
                val c = canal / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }

            private fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116

            private fun fInversa(t: Double): Double {
                val t3 = t * t * t
                return if (t3 > 216.0 / 24389) t3 else (116 * t - 16) / (24389.0 / 27)
            }

            private fun aSrgb(lineal: Double): Int {
                val c = lineal.coerceIn(0.0, 1.0)
                val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
                return (s * 255).roundToInt().coerceIn(0, 255)
            }
        }
    }

    /** Max difference between two colors' channels; handy for tests. */
    internal fun diferenciaCanales(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF))
    )
}
