package com.example.calculadoracrochet.data

import com.example.calculadoracrochet.data.model.VERSION_DATOS_ACTUAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProyectoMapperTest {

    @Test
    fun `legacy project with only final price keeps the price and empty breakdown`() {
        val proyecto = ProyectoMapper.desdeMapa(
            "abc",
            mapOf("nombre" to "Pulpito", "precioFinal" to 350L, "fechaGuardado" to 1_700_000_000_000L)
        )

        assertEquals("abc", proyecto.id)
        assertEquals(350.0, proyecto.precioFinal, 0.0)
        assertEquals(0.0, proyecto.costoMateriales, 0.0)
        assertTrue(proyecto.listaEstambres.isEmpty())
        assertTrue(proyecto.carpetas.isEmpty())
        assertEquals(1, proyecto.versionDatos)
    }

    @Test
    fun `legacy yarn reads text amounts and treats costo as skein price`() {
        val estambre = ProyectoMapper.estambreDesdeMapa(
            mapOf("nombre" to "Rojo", "costo" to "120", "totales" to "100", "usados" to "25")
        )

        assertEquals(120.0, estambre.costoMadeja, 0.0)
        assertEquals(100.0, estambre.gramosTotales, 0.0)
        assertEquals(25.0, estambre.gramosUsados, 0.0)
        assertEquals(30.0, estambre.costo, 0.001)
    }

    @Test
    fun `current yarn format reads numbers`() {
        val estambre = ProyectoMapper.estambreDesdeMapa(
            mapOf("nombre" to "Azul", "costoMadeja" to 80.5, "gramosTotales" to 50L, "gramosUsados" to 50L, "costo" to 80.5)
        )

        assertEquals(80.5, estambre.costoMadeja, 0.0)
        assertEquals(80.5, estambre.costo, 0.001)
    }

    @Test
    fun `legacy and current accessories give the same result`() {
        val legado = ProyectoMapper.accesorioDesdeMapa(
            mapOf("nombre" to "Ojos", "costo" to "12.50", "esCalculado" to true,
                "costoPaquete" to "50", "cantidadTotal" to "20", "cantidadUsada" to "5")
        )
        val actual = ProyectoMapper.accesorioDesdeMapa(
            mapOf("nombre" to "Ojos", "costo" to 12.5, "esCalculado" to true,
                "costoPaquete" to 50.0, "cantidadTotal" to 20.0, "cantidadUsada" to 5.0)
        )

        assertEquals(actual, legado)
    }

    @Test
    fun `decimal accepts comma, blanks and junk`() {
        assertEquals(15.5, ProyectoMapper.decimal("15,5"), 0.0)
        assertEquals(15.5, ProyectoMapper.decimal(" 15.5 "), 0.0)
        assertEquals(0.0, ProyectoMapper.decimal(""), 0.0)
        assertEquals(0.0, ProyectoMapper.decimal("abc"), 0.0)
        assertEquals(0.0, ProyectoMapper.decimal(null), 0.0)
        assertEquals(7.0, ProyectoMapper.decimal(7), 0.0)
    }

    @Test
    fun `nested lists from Firestore are mapped`() {
        val proyecto = ProyectoMapper.desdeMapa(
            "x",
            mapOf(
                "listaEstambres" to listOf(mapOf("nombre" to "Rojo", "costo" to "100", "totales" to "100", "usados" to "10")),
                "listaAccesorios" to listOf(mapOf("nombre" to "Relleno", "costo" to "20")),
                "carpetas" to listOf("Amigurumis"),
                "versionDatos" to VERSION_DATOS_ACTUAL.toLong()
            )
        )

        assertEquals(10.0, proyecto.listaEstambres.single().costo, 0.001)
        assertEquals(20.0, proyecto.listaAccesorios.single().costo, 0.0)
        assertEquals(listOf("Amigurumis"), proyecto.carpetas)
        assertEquals(VERSION_DATOS_ACTUAL, proyecto.versionDatos)
    }
}
