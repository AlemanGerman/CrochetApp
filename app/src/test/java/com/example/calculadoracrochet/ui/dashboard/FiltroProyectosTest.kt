package com.example.calculadoracrochet.ui.dashboard

import com.example.calculadoracrochet.data.model.ProyectoCrochet
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltroProyectosTest {

    private val gato = ProyectoCrochet(id = "gato", nombre = "Gato", precioFinal = 300.0, fechaGuardado = 3L, carpetas = listOf("Amigurumis"))
    private val bolsa = ProyectoCrochet(id = "bolsa", nombre = "bolsa playera", precioFinal = 500.0, fechaGuardado = 1L, fotoUri = "https://f", carpetas = listOf("Bolsas"))
    private val abeja = ProyectoCrochet(id = "abeja", nombre = "Abeja", precioFinal = 300.0, fechaGuardado = 2L, carpetas = listOf("Amigurumis"))
    private val borrado = ProyectoCrochet(id = "borrado", nombre = "Gato viejo", fechaEliminado = 5L)

    private val todos = listOf(gato, bolsa, abeja, borrado)

    private fun ids(
        texto: String = "",
        carpeta: String = FiltroProyectos.CARPETA_TODOS,
        criterio: CriterioOrden = CriterioOrden.FECHA,
        descendente: Boolean = true
    ) = FiltroProyectos.aplicar(todos, texto, carpeta, criterio, descendente).map { it.id }

    @Test
    fun `projects in the trash never show`() {
        assertEquals(listOf("gato", "abeja", "bolsa"), ids())
    }

    @Test
    fun `search ignores case and surrounding spaces`() {
        assertEquals(listOf("gato"), ids(texto = "  GAT "))
        assertEquals(listOf("bolsa"), ids(texto = "playera"))
    }

    @Test
    fun `folder filter`() {
        assertEquals(listOf("gato", "abeja"), ids(carpeta = "Amigurumis"))
        assertEquals(emptyList<String>(), ids(carpeta = "Ropa"))
    }

    @Test
    fun `price order breaks ties with the most recent project`() {
        assertEquals(listOf("bolsa", "gato", "abeja"), ids(criterio = CriterioOrden.PRECIO, descendente = true))
        assertEquals(listOf("gato", "abeja", "bolsa"), ids(criterio = CriterioOrden.PRECIO, descendente = false))
    }

    @Test
    fun `name order is A to Z in the default direction`() {
        assertEquals(listOf("abeja", "bolsa", "gato"), ids(criterio = CriterioOrden.NOMBRE, descendente = true))
        assertEquals(listOf("gato", "bolsa", "abeja"), ids(criterio = CriterioOrden.NOMBRE, descendente = false))
    }

    @Test
    fun `photo order puts projects with photo first`() {
        assertEquals("bolsa", ids(criterio = CriterioOrden.FOTO, descendente = true).first())
    }
}
