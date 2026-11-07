package com.example.calculadoracrochet.data

import com.example.calculadoracrochet.data.model.ProyectoCrochet
import org.junit.Assert.assertEquals
import org.junit.Test

class FotoProyectoTest {

    private val fotoRemota = FotoProyecto("https://storage/a.jpg", "https://storage/a_min.jpg")

    private fun proyectoCon(id: String, foto: FotoProyecto) =
        ProyectoCrochet(id = id, fotoUri = foto.principal, fotoMiniaturaUri = foto.miniatura)

    @Test
    fun `remote photo used by no other project can be deleted`() {
        val proyectos = listOf(proyectoCon("a", fotoRemota), proyectoCon("b", FotoProyecto.VACIA))

        val urls = FotoProyecto.urlsSinUso(listOf(fotoRemota), proyectos, idsExcluidos = setOf("a"))

        assertEquals(fotoRemota.rutas, urls)
    }

    @Test
    fun `photo shared with a duplicate is kept`() {
        val proyectos = listOf(proyectoCon("a", fotoRemota), proyectoCon("copia", fotoRemota))

        val urls = FotoProyecto.urlsSinUso(listOf(fotoRemota), proyectos, idsExcluidos = setOf("a"))

        assertEquals(emptyList<String>(), urls)
    }

    @Test
    fun `photo shared only among projects being deleted is deleted`() {
        val proyectos = listOf(proyectoCon("a", fotoRemota), proyectoCon("copia", fotoRemota))

        val urls = FotoProyecto.urlsSinUso(
            listOf(fotoRemota, fotoRemota),
            proyectos,
            idsExcluidos = setOf("a", "copia")
        )

        assertEquals(fotoRemota.rutas, urls)
    }

    @Test
    fun `local files are never sent to remote deletion`() {
        val fotoLocal = FotoProyecto("file:///data/fotos/a.jpg", "file:///data/fotos/a_min.jpg")

        val urls = FotoProyecto.urlsSinUso(listOf(fotoLocal), emptyList(), idsExcluidos = emptySet())

        assertEquals(emptyList<String>(), urls)
    }
}
