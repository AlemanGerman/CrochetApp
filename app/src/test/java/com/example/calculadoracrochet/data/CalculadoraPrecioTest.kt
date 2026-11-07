package com.example.calculadoracrochet.data

import com.example.calculadoracrochet.utils.PreferenciasApp
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculadoraPrecioTest {

    @Test
    fun `subtotal adds the four costs and margin is a percentage of it`() {
        val desglose = CalculadoraPrecio.desglosar(
            costoMateriales = 50.0,
            costoDiseno = 20.0,
            costoManoObra = 120.0,
            costoEmpaque = 10.0,
            margenGanancia = 25.0
        )

        assertEquals(200.0, desglose.subtotal, 0.0)
        assertEquals(50.0, desglose.ganancia, 0.0)
        assertEquals(250.0, desglose.totalExacto, 0.0)
    }

    @Test
    fun `zero margin means no profit`() {
        val desglose = CalculadoraPrecio.desglosar(10.0, 0.0, 0.0, 0.0, 0.0)

        assertEquals(0.0, desglose.ganancia, 0.0)
        assertEquals(10.0, desglose.totalExacto, 0.0)
    }

    @Test
    fun `rounding goes up to the next multiple and reports the difference`() {
        val (precio, ajuste) = PreferenciasApp.calcularRedondeo(231.4, activo = true, multiplo = 5.0)

        assertEquals(235.0, precio, 0.0001)
        assertEquals(3.6, ajuste, 0.0001)
    }

    @Test
    fun `an exact multiple is not rounded`() {
        val (precio, ajuste) = PreferenciasApp.calcularRedondeo(230.0, activo = true, multiplo = 10.0)

        assertEquals(230.0, precio, 0.0)
        assertEquals(0.0, ajuste, 0.0)
    }

    @Test
    fun `floating point noise does not push the price to the next multiple`() {
        // 1 h 06 min at $50/h is 55.00000000000001 in doubles
        assertEquals(55.0 to 0.0, PreferenciasApp.calcularRedondeo(50.0 * (1 + 6 / 60.0), activo = true, multiplo = 5.0))
        // A $55 skein with 100 g used out of 100 g
        assertEquals(55.0 to 0.0, PreferenciasApp.calcularRedondeo(55.0 / 100 * 100, activo = true, multiplo = 5.0))
        // 1.1 / 0.1 is 11.000000000000002
        assertEquals(1.1 to 0.0, PreferenciasApp.calcularRedondeo(1.1, activo = true, multiplo = 0.1))
    }

    @Test
    fun `rounding off or with an invalid multiple keeps the exact price`() {
        assertEquals(231.4 to 0.0, PreferenciasApp.calcularRedondeo(231.4, activo = false, multiplo = 5.0))
        assertEquals(231.4 to 0.0, PreferenciasApp.calcularRedondeo(231.4, activo = true, multiplo = 0.0))
        assertEquals(231.4 to 0.0, PreferenciasApp.calcularRedondeo(231.4, activo = true, multiplo = -5.0))
    }

    @Test
    fun `multiple label hides cents only for whole amounts`() {
        assertEquals("$5", PreferenciasApp.formatearMultiplo(5.0))
        assertEquals("$0.50", PreferenciasApp.formatearMultiplo(0.5))
    }
}
