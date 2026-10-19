package com.example.calculadoracrochet.ui.calculadora

import com.example.calculadoracrochet.data.BorradorCalculo
import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoActualTest {

    private val proyectoGuardado = ProyectoCrochet(
        id = "p1",
        nombre = "Pulpito",
        fotoUri = "https://foto/p1.jpg",
        fotoMiniaturaUri = "https://foto/p1_min.jpg",
        fechaGuardado = 1_000L,
        costoMateriales = 50.0,
        costoDiseno = 10.0,
        costoManoObra = 100.0,
        costoEmpaque = 5.0,
        margenGanancia = 20.0,
        ajusteRedondeo = 2.0,
        precioFinal = 200.0,
        horasTrabajadas = 2.5,
        pagoPorHora = 40.0,
        listaEstambres = listOf(EstambreGuardado("Rojo", 100.0, 100.0, 50.0)),
        listaAccesorios = listOf(AccesorioGuardado("Ojos", 12.0)),
        carpetas = listOf("Amigurumis")
    )

    @Test
    fun `total adds every cost plus margin and rounds up to the multiple`() {
        val calculo = CalculoActual().apply {
            costoMateriales = 40.0
            costoDiseno = 10.0
            costoManoObra = 45.0
            costoEmpaque = 5.0
            margenGanancia = 10.0
        }

        calculo.calcularTotal(redondeoActivo = true, multiploRedondeo = 5.0)

        // (40 + 10 + 45 + 5) * 1.10 = 110 → already a multiple of 5
        assertEquals(110.0, calculo.precioFinalSugerido, 0.001)
        assertEquals(0.0, calculo.ajusteRedondeo, 0.0)

        calculo.margenGanancia = 12.0
        calculo.calcularTotal(redondeoActivo = true, multiploRedondeo = 5.0)

        // 112 → 115, the 3 pesos difference is the rounding adjustment
        assertEquals(115.0, calculo.precioFinalSugerido, 0.001)
        assertEquals(3.0, calculo.ajusteRedondeo, 0.001)
    }

    @Test
    fun `total without rounding keeps the exact price`() {
        val calculo = CalculoActual().apply {
            costoMateriales = 33.3
            margenGanancia = 0.0
        }

        calculo.calcularTotal(redondeoActivo = false, multiploRedondeo = 5.0)

        assertEquals(33.3, calculo.precioFinalSugerido, 0.0001)
        assertEquals(0.0, calculo.ajusteRedondeo, 0.0)
    }

    @Test
    fun `editing a project keeps its id and date`() {
        val calculo = CalculoActual()

        calculo.cargarDesde(proyectoGuardado, esDuplicado = false)

        assertEquals("p1", calculo.idProyectoEnEdicion)
        assertEquals(1_000L, calculo.fechaEnEdicion)
        assertEquals("Pulpito", calculo.nombre)
        assertEquals(2.5, calculo.horasTrabajadas, 0.0)
        assertEquals(listOf("Amigurumis"), calculo.carpetas)
        assertEquals(proyectoGuardado.listaEstambres, calculo.estambres)

        val guardado = calculo.aProyecto("Pulpito editado", ahora = 9_999L)

        assertEquals("p1", guardado.id)
        assertEquals(1_000L, guardado.fechaGuardado)
        assertEquals("Pulpito editado", guardado.nombre)
    }

    @Test
    fun `a duplicate gets a new id and the current date`() {
        val calculo = CalculoActual()

        calculo.cargarDesde(proyectoGuardado, esDuplicado = true)
        val duplicado = calculo.aProyecto("Pulpito 2", ahora = 9_999L)

        assertNull(calculo.idProyectoEnEdicion)
        assertNotEquals("p1", duplicado.id)
        assertEquals(9_999L, duplicado.fechaGuardado)
        // It starts with the original photo; ProyectosViewModel decides what to keep when saving
        assertEquals(proyectoGuardado.fotoUri, duplicado.fotoUri)
    }

    @Test
    fun `saved project copies every value and is not affected by later changes`() {
        val calculo = CalculoActual()
        calculo.cargarDesde(proyectoGuardado, esDuplicado = false)
        calculo.precioFinalSugerido = 200.0

        val guardado = calculo.aProyecto("Pulpito")
        calculo.estambres.clear()
        calculo.carpetas.add("Otra")

        assertEquals(proyectoGuardado.copy(nombre = "Pulpito"), guardado)
        assertEquals(1, guardado.listaEstambres.size)
        assertEquals(listOf("Amigurumis"), guardado.carpetas)
        assertTrue(!guardado.eliminado && guardado.fechaEliminado == null)
    }

    @Test
    fun `draft round trip restores the same calculation`() {
        val original = CalculoActual()
        original.cargarDesde(proyectoGuardado, esDuplicado = false)

        val restaurado = CalculoActual()
        restaurado.aplicar(original.aBorrador())

        assertEquals(original.aBorrador(), restaurado.aBorrador())
        assertEquals(original.aProyecto("x", ahora = 1L), restaurado.aProyecto("x", ahora = 1L).copy(
            // The draft does not store the result; it is recalculated after restoring
            ajusteRedondeo = original.ajusteRedondeo,
            precioFinal = original.precioFinalSugerido
        ))
    }

    @Test
    fun `clearing leaves an empty calculation`() {
        val calculo = CalculoActual()
        calculo.cargarDesde(proyectoGuardado, esDuplicado = false)
        calculo.precioFinalSugerido = 200.0

        calculo.limpiar()

        assertEquals(BorradorCalculo(), calculo.aBorrador())
        assertEquals(0.0, calculo.precioFinalSugerido, 0.0)
        assertEquals(0.0, calculo.ajusteRedondeo, 0.0)
    }
}
