package com.example.calculadoracrochet

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    private val storage = FirebaseStorage.getInstance()

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

    var idProyectoEnEdicion: String? = null
    var fechaEnEdicion: Long? = null

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

    fun guardarProyecto(nombre: String, fotoUriLocal: String?, fotoBytes: ByteArray?, onComplete: (Boolean) -> Unit) {
        val idNuevo = idProyectoEnEdicion ?: UUID.randomUUID().toString()

        fun escribirEnFirestore(uriParaGuardar: String?) {
            val nuevoProyecto = ProyectoCrochet(
                id = idNuevo,
                nombre = nombre,
                fotoUri = uriParaGuardar,
                fechaGuardado = fechaEnEdicion ?: System.currentTimeMillis(),
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

            db.collection("proyectos_mina").document(nuevoProyecto.id).set(nuevoProyecto)
                .addOnSuccessListener {
                    Log.d("Firebase", "Proyecto guardado con éxito")
                    limpiarCalculoActual()
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error al guardar en Firestore", e)
                    onComplete(false)
                }
        }

        if (fotoBytes != null) {
            val storageRef = storage.reference.child("fotos_proyectos/${idNuevo}.jpg")

            storageRef.putBytes(fotoBytes)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uriDescarga ->
                        Log.d("Firebase", "Foto subida por bytes con éxito: $uriDescarga")
                        escribirEnFirestore(uriDescarga.toString())
                    }.addOnFailureListener {
                        Log.e("Firebase", "Error al obtener downloadUrl")
                        escribirEnFirestore(fotoUriLocal)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "La foto se guardará de forma local", e)
                    escribirEnFirestore(fotoUriLocal)
                }
        } else {
            escribirEnFirestore(fotoUriLocal)
        }
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina").document(proyecto.id).delete()

        if (proyecto.fotoUri != null && proyecto.fotoUri!!.startsWith("http")) {
            try {
                val fotoRef = storage.getReferenceFromUrl(proyecto.fotoUri!!)
                fotoRef.delete()
            } catch (e: Exception) {
                Log.e("Firebase", "Error al borrar foto de Storage", e)
            }
        }
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
        idProyectoEnEdicion = null
        fechaEnEdicion = null
        listaEstambresGuardados.clear()
        listaAccesoriosGuardados.clear()
    }
}