package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoTest {

    @Test
    fun `money always uses pesos with thousands separator and two decimals`() {
        assertEquals("$1,234.50", 1234.5.comoDinero())
        assertEquals("$0.00", 0.0.comoDinero())
    }

    @Test
    fun `editable text hides zeros and useless decimals`() {
        assertEquals("", 0.0.comoTextoEditable())
        assertEquals("", (-3.0).comoTextoEditable())
        assertEquals("120", 120.0.comoTextoEditable())
        assertEquals("15.5", 15.5.comoTextoEditable())
    }

    @Test
    fun `materials breakdown lists the cost of what was used`() {
        val texto = desgloseMateriales(
            estambres = listOf(EstambreGuardado("Rojo", costoMadeja = 100.0, gramosTotales = 100.0, gramosUsados = 25.0)),
            accesorios = listOf(AccesorioGuardado("Ojos", costo = 12.0)),
            vineta = "- ",
            textoVacio = "Sin materiales"
        )

        assertEquals("- Rojo: $25.00\n- Ojos: $12.00", texto)
    }

    @Test
    fun `materials breakdown without materials shows the empty text`() {
        assertEquals("Sin materiales", desgloseMateriales(emptyList(), emptyList(), "- ", "Sin materiales"))
    }
}
