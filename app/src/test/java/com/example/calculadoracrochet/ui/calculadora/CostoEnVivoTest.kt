package com.example.calculadoracrochet.ui.calculadora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostoEnVivoTest {

    @Test
    fun `the used part of a skein costs its share of the grams`() {
        assertEquals(25.0, CostoEnVivo.porcionUsada(100.0, 100.0, 25.0)!!, 1e-9)
        assertEquals(100.0, CostoEnVivo.porcionUsada(100.0, 100.0, 100.0)!!, 1e-9)
    }

    @Test
    fun `missing, non positive or excessive amounts give no cost yet`() {
        assertNull(CostoEnVivo.porcionUsada(null, 100.0, 25.0))
        assertNull(CostoEnVivo.porcionUsada(100.0, 0.0, 25.0))
        assertNull(CostoEnVivo.porcionUsada(100.0, 100.0, 0.0))
        assertNull(CostoEnVivo.porcionUsada(100.0, 100.0, 120.0))
    }

    @Test
    fun `a bought pattern is shared by the pieces`() {
        assertEquals(50.0, CostoEnVivo.patronPorPieza(150.0, 3)!!, 1e-9)
        assertNull(CostoEnVivo.patronPorPieza(150.0, 0))
        assertNull(CostoEnVivo.patronPorPieza(0.0, 3))
    }

    @Test
    fun `labor is the hourly rate times hours and minutes`() {
        assertEquals(105.0, CostoEnVivo.manoDeObra(60.0, 1, 45)!!, 1e-9)
        assertEquals(1200.0, CostoEnVivo.manoDeObra(40.0, 30, 0)!!, 1e-9)
        assertNull(CostoEnVivo.manoDeObra(null, 2, 0))
    }
}
