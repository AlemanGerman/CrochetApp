package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import java.util.Locale

val LOCALE_MX: Locale = Locale.forLanguageTag("es-MX")

/** "$1,234.50" regardless of the device language, so every screen shows money the same way. */
fun Double.comoDinero(): String = String.format(LOCALE_MX, "$%,.2f", this)

/** One line per yarn/accessory with its saved cost, or [textoVacio] when there are none. */
fun desgloseMateriales(
    estambres: List<EstambreGuardado>,
    accesorios: List<AccesorioGuardado>,
    vineta: String,
    textoVacio: String
): String = buildString {
    if (estambres.isEmpty() && accesorios.isEmpty()) {
        append(textoVacio)
    } else {
        estambres.forEach { appendLine("$vineta${it.nombre}: $${it.costo}") }
        accesorios.forEach { appendLine("$vineta${it.nombre}: $${it.costo}") }
    }
}.trimEnd()
