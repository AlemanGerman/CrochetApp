package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
import java.util.Locale
import kotlin.math.roundToInt

val LOCALE_MX: Locale = Locale.forLanguageTag("es-MX")

/** Whole minutes in [horas]. Rounds instead of truncating: 1 h 05 min is 1.08333…, which truncates to 1 h 04 min. */
fun horasAMinutos(horas: Double): Int = (horas * 60).roundToInt()

/** "5 h 30 min", "45 min" or "2 h" for a duration in hours. */
fun duracionLegible(horas: Double): String {
    val totalMinutos = horasAMinutos(horas).coerceAtLeast(0)
    val h = totalMinutos / 60
    val min = totalMinutos % 60
    return when {
        h == 0 -> "$min min"
        min == 0 -> "$h h"
        else -> "$h h $min min"
    }
}

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
