package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import java.util.Locale

val LOCALE_MX: Locale = Locale.forLanguageTag("es-MX")

/** "$1,234.50" regardless of the device language, so every screen shows money the same way. */
fun Double.comoDinero(): String = String.format(LOCALE_MX, "$%,.2f", this)

/** Text for pre-filling an input: "" for zero/invalid, "120" instead of "120.0", "15.5" otherwise. */
fun Double.comoTextoEditable(): String = when {
    this <= 0.0 -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}

/** One line per yarn/accessory with the cost of what was used, or [textoVacio] when there are none. */
fun desgloseMateriales(
    estambres: List<EstambreGuardado>,
    accesorios: List<AccesorioGuardado>,
    vineta: String,
    textoVacio: String
): String = buildString {
    if (estambres.isEmpty() && accesorios.isEmpty()) {
        append(textoVacio)
    } else {
        estambres.forEach { appendLine("$vineta${it.nombre}: ${it.costo.comoDinero()}") }
        accesorios.forEach { appendLine("$vineta${it.nombre}: ${it.costo.comoDinero()}") }
    }
}.trimEnd()
