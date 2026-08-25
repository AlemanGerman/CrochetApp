package com.example.calculadoracrochet

import androidx.lifecycle.ViewModel
import java.util.UUID

data class ProyectoCrochet(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val fotoUri: String?,
    val costoMateriales: Double,
    val costoDiseno: Double,
    val costoManoObra: Double,
    val costoEmpaque: Double,
    val margenGanancia: Double,
    val precioFinal: Double
)

data class EstambreGuardado(val nombre: String, val costo: String, val totales: String, val usados: String)
data class AccesorioGuardado(val nombre: String, val costo: String)

class CalculadoraViewModel : ViewModel() {
    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
    var precioFinalSugerdio: Double = 0.0

    val listaEstambresGuardados = mutableListOf<EstambreGuardado>()

    val listaAccesoriosGuardados = mutableListOf<AccesorioGuardado>()

    val historialProyectos = mutableListOf<ProyectoCrochet>()

    fun calcularTotal() {
        val subtotal = costoMateriales + costoDiseno + costoManoObra + costoEmpaque
        val gananciaExtra = subtotal * (margenGanancia / 100)
        precioFinalSugerdio = subtotal + gananciaExtra
    }


    fun guardarProyecto(nombre: String, fotoUri: String?) {
        val nuevoProyecto = ProyectoCrochet(
            nombre = nombre,
            fotoUri = fotoUri,
            costoMateriales = costoMateriales,
            costoDiseno = costoDiseno,
            costoManoObra = costoManoObra,
            costoEmpaque = costoEmpaque,
            margenGanancia = margenGanancia,
            precioFinal = precioFinalSugerdio
        )
        historialProyectos.add(0, nuevoProyecto)
        limpiarCalculoActual()
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet) {
        historialProyectos.remove(proyecto)
    }

    fun limpiarCalculoActual() {
        costoMateriales = 0.0
        costoDiseno = 0.0
        costoManoObra = 0.0
        costoEmpaque = 0.0
        margenGanancia = 0.0
        precioFinalSugerdio = 0.0
        listaEstambresGuardados.clear()
        listaAccesoriosGuardados.clear()
    }
}