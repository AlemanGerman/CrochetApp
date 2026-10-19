package com.example.calculadoracrochet.ui.estadisticas

import com.example.calculadoracrochet.data.model.ProyectoCrochet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadisticasProyectosTest {

    @Test
    fun `percentages always add up to 100`() {
        val porcentajes = EstadisticasProyectos.porcentajesQueSuman100(listOf(1, 1, 1))

        assertEquals(100, porcentajes.sum())
        assertEquals(listOf(34, 33, 33), porcentajes)
    }

    @Test
    fun `missing points go to the biggest decimal parts`() {
        // 2/7 = 28.57, 2/7 = 28.57, 3/7 = 42.86 → 28 + 28 + 42 = 98, the two largest remainders get +1
        val porcentajes = EstadisticasProyectos.porcentajesQueSuman100(listOf(2, 2, 3))

        assertEquals(listOf(29, 28, 43), porcentajes)
        assertEquals(100, porcentajes.sum())
    }

    @Test
    fun `empty folders get zero and never receive extra points`() {
        val porcentajes = EstadisticasProyectos.porcentajesQueSuman100(listOf(0, 1, 0, 2))

        assertEquals(listOf(0, 33, 0, 67), porcentajes)
    }

    @Test
    fun `nothing to count gives all zeros`() {
        assertEquals(listOf(0, 0), EstadisticasProyectos.porcentajesQueSuman100(listOf(0, 0)))
        assertEquals(emptyList<Int>(), EstadisticasProyectos.porcentajesQueSuman100(emptyList()))
    }

    @Test
    fun `summary totals, highlights and folder counts`() {
        val proyectos = listOf(
            ProyectoCrochet(nombre = "Gato", precioFinal = 300.0, horasTrabajadas = 2.0, carpetas = listOf("Amigurumis")),
            ProyectoCrochet(nombre = "Bolsa", precioFinal = 500.0, horasTrabajadas = 1.0, carpetas = listOf("Bolsas")),
            ProyectoCrochet(nombre = "Manta", precioFinal = 200.0, horasTrabajadas = 9.0, carpetas = listOf("Amigurumis", "Bolsas"))
        )

        val resumen = EstadisticasProyectos.calcular(proyectos, listOf("Amigurumis", "Bolsas", "Ropa"))

        assertEquals(3, resumen.totalProyectos)
        assertEquals(1000.0, resumen.totalVentas, 0.0)
        assertEquals("Bolsa", resumen.masCostoso?.nombre)
        assertEquals("Manta", resumen.masHoras?.nombre)
        // A project in two folders counts in both, so the chart splits 4 pieces
        assertEquals(
            listOf(ConteoCarpeta("Amigurumis", 2, 50), ConteoCarpeta("Bolsas", 2, 50), ConteoCarpeta("Ropa", 0, 0)),
            resumen.carpetas
        )
    }

    @Test
    fun `summary of no projects`() {
        val resumen = EstadisticasProyectos.calcular(emptyList(), listOf("Amigurumis"))

        assertEquals(0, resumen.totalProyectos)
        assertEquals(0.0, resumen.totalVentas, 0.0)
        assertNull(resumen.masCostoso)
        assertNull(resumen.masHoras)
        assertEquals(listOf(ConteoCarpeta("Amigurumis", 0, 0)), resumen.carpetas)
    }
}
