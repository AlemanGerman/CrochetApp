package com.example.calculadoracrochet.ui.calculadora

/**
 * Amounts the calculator shows while the user types, before the step is validated.
 * Incomplete or inconsistent numbers give null, so the screen can show a dash instead of a wrong cost.
 */
object CostoEnVivo {

    /** Cost of the used part of a skein or package: costoTotal / cantidadTotal × cantidadUsada. */
    fun porcionUsada(costoTotal: Double?, cantidadTotal: Double?, cantidadUsada: Double?): Double? {
        if (costoTotal == null || cantidadTotal == null || cantidadUsada == null) return null
        if (costoTotal <= 0.0 || cantidadTotal <= 0.0 || cantidadUsada <= 0.0) return null
        if (cantidadUsada > cantidadTotal) return null
        return costoTotal / cantidadTotal * cantidadUsada
    }

    /** A bought pattern is shared by every piece knitted with it. */
    fun patronPorPieza(costoPatron: Double?, piezas: Int?): Double? {
        if (costoPatron == null || piezas == null || costoPatron <= 0.0 || piezas <= 0) return null
        return costoPatron / piezas
    }

    fun manoDeObra(pagoPorHora: Double?, horas: Int, minutos: Int): Double? {
        if (pagoPorHora == null || pagoPorHora <= 0.0) return null
        return pagoPorHora * (horas + minutos / 60.0)
    }
}
