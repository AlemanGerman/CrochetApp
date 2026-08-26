package com.example.calculadoracrochet

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

data class EstambreGuardado(var nombre: String = "", var costo: String = "", var totales: String = "", var usados: String = "")
data class AccesorioGuardado(var nombre: String = "", var costo: String = "")

data class ProyectoCrochet(
    var id: String = UUID.randomUUID().toString(),
    var nombre: String = "Sin nombre",
    var fotoUri: String? = null,
    var fechaGuardado: Long = System.currentTimeMillis(),

    var costoMateriales: Double = 0.0,
    var costoDiseno: Double = 0.0,
    var costoManoObra: Double = 0.0,
    var costoEmpaque: Double = 0.0,
    var margenGanancia: Double = 0.0,
    var precioFinal: Double = 0.0,

    var horasTrabajadas: Double = 0.0,
    var pagoPorHora: Double = 0.0,
    var listaEstambres: List<EstambreGuardado> = emptyList(),
    var listaAccesorios: List<AccesorioGuardado> = emptyList()
)

class CalculadoraViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
    var precioFinalSugerdio: Double = 0.0

    var horasTrabajadasTemp: Double = 0.0
    var pagoPorHoraTemp: Double = 0.0

    var nombreTemp: String = ""

    var fotoUriTemp: String? = null

    val listaEstambresGuardados = mutableListOf<EstambreGuardado>()
    val listaAccesoriosGuardados = mutableListOf<AccesorioGuardado>()

    val proyectosEnVivo = MutableLiveData<List<ProyectoCrochet>>()
    val historialProyectos = mutableListOf<ProyectoCrochet>()

    init {
        cargarProyectosDeLaNube()
    }

    fun cargarProyectosDeLaNube() {
        db.collection("proyectos_mina")
            .addSnapshotListener { documentos, error ->
                if (error != null || documentos == null) return@addSnapshotListener

                historialProyectos.clear()
                for (documento in documentos) {
                    val proyecto = documento.toObject(ProyectoCrochet::class.java)
                    historialProyectos.add(proyecto)
                }

                historialProyectos.sortByDescending { it.fechaGuardado }
                proyectosEnVivo.postValue(historialProyectos)
            }
    }

    fun calcularTotal() {
        val subtotal = costoMateriales + costoDiseno + costoManoObra + costoEmpaque
        val gananciaExtra = subtotal * (margenGanancia / 100)
        precioFinalSugerdio = subtotal + gananciaExtra
    }

    fun guardarProyecto(nombre: String, fotoUri: String?) {
        val nuevoProyecto = ProyectoCrochet(
            nombre = nombre,
            fotoUri = fotoUri,
            fechaGuardado = System.currentTimeMillis(),
            costoMateriales = costoMateriales,
            costoDiseno = costoDiseno,
            costoManoObra = costoManoObra,
            costoEmpaque = costoEmpaque,
            margenGanancia = margenGanancia,
            precioFinal = precioFinalSugerdio,
            horasTrabajadas = horasTrabajadasTemp,
            pagoPorHora = pagoPorHoraTemp,
            listaEstambres = listaEstambresGuardados.toList(),
            listaAccesorios = listaAccesoriosGuardados.toList()
        )

        db.collection("proyectos_mina")
            .document(nuevoProyecto.id)
            .set(nuevoProyecto)
            .addOnSuccessListener { Log.d("Firebase", "Proyecto guardado") }
            .addOnFailureListener { e -> Log.e("Firebase", "Error al guardar", e) }
        limpiarCalculoActual()
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina")
            .document(proyecto.id)
            .delete()
    }

    fun limpiarCalculoActual() {
        costoMateriales = 0.0
        costoDiseno = 0.0
        costoManoObra = 0.0
        costoEmpaque = 0.0
        margenGanancia = 0.0
        precioFinalSugerdio = 0.0
        horasTrabajadasTemp = 0.0
        pagoPorHoraTemp = 0.0
        nombreTemp = ""
        fotoUriTemp = null
        listaEstambresGuardados.clear()
        listaAccesoriosGuardados.clear()
    }
}