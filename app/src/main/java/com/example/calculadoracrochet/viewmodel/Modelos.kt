package com.example.calculadoracrochet.viewmodel

import java.util.UUID

data class EstambreGuardado(
    var nombre: String = "",
    var costo: String = "",
    var totales: String = "",
    var usados: String = ""
)

data class AccesorioGuardado(
    var nombre: String = "",
    var costo: String = "",
    var esCalculado: Boolean = false,
    var costoPaquete: String = "",
    var cantidadTotal: String = "",
    var cantidadUsada: String = ""
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
    var carpetas: List<String> = emptyList()
)

val ProyectoCrochet.estaEnPapelera: Boolean
    get() = fechaEliminado != null || eliminado

/** Old documents may contain the literal strings "null" or "undefined" instead of a real null. */
fun String?.esRutaFotoValida(): Boolean =
    !isNullOrBlank() && this != "null" && this != "undefined"

val ProyectoCrochet.tieneFoto: Boolean
    get() = fotoUri.esRutaFotoValida()
