package com.example.calculadoracrochet.viewmodel

import java.util.UUID

/** Version 1 stored material amounts as text; version 2 stores numbers. */
const val VERSION_DATOS_ACTUAL = 2

data class EstambreGuardado(
    var nombre: String = "",
    var costoMadeja: Double = 0.0,
    var gramosTotales: Double = 0.0,
    var gramosUsados: Double = 0.0
) {
    /** Cost of the grams actually used. Also written to Firestore so the document reads on its own. */
    val costo: Double
        get() = if (gramosTotales > 0.0) costoMadeja / gramosTotales * gramosUsados else 0.0
}

data class AccesorioGuardado(
    var nombre: String = "",
    var costo: Double = 0.0,
    var esCalculado: Boolean = false,
    var costoPaquete: Double = 0.0,
    var cantidadTotal: Double = 0.0,
    var cantidadUsada: Double = 0.0
)

data class ProyectoCrochet(
    var id: String = UUID.randomUUID().toString(),
    var nombre: String = "Sin nombre",
    var fotoUri: String? = null,
    var fotoMiniaturaUri: String? = null,
    var fechaGuardado: Long = System.currentTimeMillis(),
    var fechaEliminado: Long? = null,
    var eliminado: Boolean = false,
    var costoMateriales: Double = 0.0,
    var costoDiseno: Double = 0.0,
    var costoManoObra: Double = 0.0,
    var costoEmpaque: Double = 0.0,
    var margenGanancia: Double = 0.0,
    var ajusteRedondeo: Double = 0.0,
    var precioFinal: Double = 0.0,
    var horasTrabajadas: Double = 0.0,
    var pagoPorHora: Double = 0.0,
    var listaEstambres: List<EstambreGuardado> = emptyList(),
    var listaAccesorios: List<AccesorioGuardado> = emptyList(),
    var carpetas: List<String> = emptyList(),
    var versionDatos: Int = VERSION_DATOS_ACTUAL
)

val ProyectoCrochet.estaEnPapelera: Boolean
    get() = fechaEliminado != null || eliminado

/** Old documents may contain the literal strings "null" or "undefined" instead of a real null. */
fun String?.esRutaFotoValida(): Boolean =
    !isNullOrBlank() && this != "null" && this != "undefined"

val ProyectoCrochet.tieneFoto: Boolean
    get() = fotoUri.esRutaFotoValida()
