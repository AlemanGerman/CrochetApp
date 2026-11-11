package com.example.calculadoracrochet.utils

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorDelTejidoTest {

    private val lado = ColorDelTejido.LADO_MUESTRA

    /** A round piece of [radio] (fraction of the side) in the middle of [fondo]; [pieza] may vary with x/y. */
    private fun foto(fondo: Int, radio: Double, pieza: (Int, Int) -> Int): IntArray =
        IntArray(lado * lado) { i ->
            val x = i % lado
            val y = i / lado
            val dx = (x + 0.5) / lado - 0.5
            val dy = (y + 0.5) / lado - 0.5
            if (dx * dx + dy * dy <= radio * radio) pieza(x, y) else fondo
        }

    private fun parecido(esperado: Int, obtenido: Int?, tolerancia: Int = 40) {
        assertNotNull(obtenido)
        val diferencia = ColorDelTejido.diferenciaCanales(esperado, obtenido!!)
        assertTrue(
            "esperado #%06X, obtenido #%06X".format(esperado and 0xFFFFFF, obtenido and 0xFFFFFF),
            diferencia <= tolerancia
        )
    }

    @Test
    fun `a red piece on a white table is red`() {
        val pixeles = foto(fondo = 0xFFF4F2EE.toInt(), radio = 0.32) { _, _ -> 0xFFD0303A.toInt() }
        parecido(0xFFD0303A.toInt(), ColorDelTejido.elegir(pixeles, lado, lado))
    }

    @Test
    fun `a small pink piece on a blue blanket that fills most of the photo is pink`() {
        val pixeles = foto(fondo = 0xFF2F5FA8.toInt(), radio = 0.24) { _, _ -> 0xFFF08CB4.toInt() }
        parecido(0xFFF08CB4.toInt(), ColorDelTejido.elegir(pixeles, lado, lado))
    }

    @Test
    fun `a white piece on a wooden table keeps the normal card`() {
        val pixeles = foto(fondo = 0xFF9A6B43.toInt(), radio = 0.3) { _, _ -> 0xFFF7F5F0.toInt() }
        assertNull(ColorDelTejido.elegir(pixeles, lado, lado))
    }

    @Test
    fun `a piece framed edge to edge is its own color`() {
        val pixeles = IntArray(lado * lado) { 0xFF3E9A55.toInt() }
        parecido(0xFF3E9A55.toInt(), ColorDelTejido.elegir(pixeles, lado, lado))
    }

    @Test
    fun `light and shadow on the same yarn give one blue, not the beige table`() {
        val pixeles = foto(fondo = 0xFFE2CFAE.toInt(), radio = 0.34) { x, _ ->
            if (x < lado / 2) 0xFF4A78D0.toInt() else 0xFF2A4C90.toInt()
        }
        val color = ColorDelTejido.elegir(pixeles, lado, lado)
        assertNotNull(color)
        val r = (color!! shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        assertTrue("debería ser azul: #%06X".format(color and 0xFFFFFF), b > r + 40 && b > g + 20)
    }

    @Test
    fun `a dark gray piece on a white background stays readable`() {
        val pixeles = foto(fondo = 0xFFFFFFFF.toInt(), radio = 0.3) { _, _ -> 0xFF101010.toInt() }
        val color = ColorDelTejido.elegir(pixeles, lado, lado)
        assertNotNull(color)
        // Lightened from almost black to a charcoal, not left pitch black
        assertTrue(((color!! shr 16) and 0xFF) > 0x20)
    }
}
