package com.example.calculadoracrochet.data

data class DesglosePrecio(
    val subtotal: Double,
    val ganancia: Double
) {
    val totalExacto: Double get() = subtotal + ganancia
}

object CalculadoraPrecio {

    fun desglosar(
        costoMateriales: Double,
        costoDiseno: Double,
        costoManoObra: Double,
        costoEmpaque: Double,
        margenGanancia: Double
    ): DesglosePrecio {
        val subtotal = costoMateriales + costoDiseno + costoManoObra + costoEmpaque
        return DesglosePrecio(subtotal, subtotal * (margenGanancia / 100))
    }
}
