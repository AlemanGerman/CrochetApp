package com.example.calculadoracrochet.data

import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet

/**
 * Builds projects from raw Firestore data, accepting both formats:
 * - version 1: material amounts saved as text ("120", "15.50", "" ...)
 * - version 2: numbers
 * Old projects are not migrated in bulk; they switch to version 2 the next time they are saved.
 */
object ProyectoMapper {

    fun desdeMapa(id: String, datos: Map<String, Any?>): ProyectoCrochet = ProyectoCrochet(
        id = id,
        nombre = datos["nombre"] as? String ?: "Sin nombre",
        fotoUri = datos["fotoUri"] as? String,
        fotoMiniaturaUri = datos["fotoMiniaturaUri"] as? String,
        fechaGuardado = (datos["fechaGuardado"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        fechaEliminado = (datos["fechaEliminado"] as? Number)?.toLong(),
        eliminado = datos["eliminado"] as? Boolean ?: false,
        costoMateriales = decimal(datos["costoMateriales"]),
        costoDiseno = decimal(datos["costoDiseno"]),
        costoManoObra = decimal(datos["costoManoObra"]),
        costoEmpaque = decimal(datos["costoEmpaque"]),
        margenGanancia = decimal(datos["margenGanancia"]),
        ajusteRedondeo = decimal(datos["ajusteRedondeo"]),
        precioFinal = decimal(datos["precioFinal"]),
        horasTrabajadas = decimal(datos["horasTrabajadas"]),
        pagoPorHora = decimal(datos["pagoPorHora"]),
        listaEstambres = listaDeMapas(datos["listaEstambres"]).map(::estambreDesdeMapa),
        listaAccesorios = listaDeMapas(datos["listaAccesorios"]).map(::accesorioDesdeMapa),
        carpetas = (datos["carpetas"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        versionDatos = (datos["versionDatos"] as? Number)?.toInt() ?: 1
    )

    fun estambreDesdeMapa(datos: Map<String, Any?>): EstambreGuardado {
        val nombre = datos["nombre"] as? String ?: ""
        return if (datos.containsKey("costoMadeja")) {
            EstambreGuardado(
                nombre = nombre,
                costoMadeja = decimal(datos["costoMadeja"]),
                gramosTotales = decimal(datos["gramosTotales"]),
                gramosUsados = decimal(datos["gramosUsados"])
            )
        } else {
            // Version 1: "costo" held the price of the whole skein, not the cost of the grams used
            EstambreGuardado(
                nombre = nombre,
                costoMadeja = decimal(datos["costo"]),
                gramosTotales = decimal(datos["totales"]),
                gramosUsados = decimal(datos["usados"])
            )
        }
    }

    fun accesorioDesdeMapa(datos: Map<String, Any?>) = AccesorioGuardado(
        nombre = datos["nombre"] as? String ?: "",
        costo = decimal(datos["costo"]),
        esCalculado = datos["esCalculado"] as? Boolean ?: false,
        costoPaquete = decimal(datos["costoPaquete"]),
        cantidadTotal = decimal(datos["cantidadTotal"]),
        cantidadUsada = decimal(datos["cantidadUsada"])
    )

    /** Number, numeric text (with "." or ",") or anything else as 0. */
    fun decimal(valor: Any?): Double = when (valor) {
        is Number -> valor.toDouble()
        is String -> valor.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun listaDeMapas(valor: Any?): List<Map<String, Any?>> =
        (valor as? List<*>).orEmpty().mapNotNull { elemento ->
            (elemento as? Map<*, *>)?.entries?.associate { (clave, v) -> clave.toString() to v }
        }
}
