package com.example.calculadoracrochet.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
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
    var listaAccesorios: List<AccesorioGuardado> = emptyList(),
    var carpetas: List<String> = emptyList()
)

class CalculadoraViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
    var precioFinalSugerdio: Double = 0.0

    var horasTrabajadasTemp: Double = 0.0
    var pagoPorHoraTemp: Double = 0.0
    var carpetasTemp = mutableListOf<String>()

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

    /**
     * Guarda el proyecto solamente después de que la foto elegida quede respaldada en
     * Firebase Storage. Firestore conserva la URL de descarga, nunca una URI local.
     */
    fun guardarProyecto(nombre: String, fotoUri: String?, onComplete: (Boolean) -> Unit) {
        val idNuevo = idProyectoEnEdicion ?: UUID.randomUUID().toString()
        val fotoAnterior = fotoUriTemp

        fun escribirEnFirestore(uriParaGuardar: String?, fotoRecienSubida: Boolean = false) {
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
                listaAccesorios = listaAccesoriosGuardados.toList(),
                carpetas = carpetasTemp.toList()
            )

            db.collection("proyectos_mina").document(nuevoProyecto.id).set(nuevoProyecto)
                .addOnSuccessListener {
                    Log.d("Firebase", "Proyecto guardado con éxito")
                    if (uriParaGuardar != fotoAnterior) {
                        borrarFotoDeStorage(fotoAnterior)
                    }
                    limpiarCalculoActual()
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error al guardar en Firestore", e)
                    // La ruta generada para esta carga no era usada antes, por lo que se
                    // puede limpiar sin afectar la foto anterior del proyecto.
                    if (fotoRecienSubida) borrarFotoDeStorage(uriParaGuardar)
                    onComplete(false)
                }
        }

        if (fotoUri.esUriLocal()) {
            subirFoto(idNuevo, Uri.parse(fotoUri), onSuccess = { urlDescarga ->
                escribirEnFirestore(urlDescarga, fotoRecienSubida = true)
            }, onFailure = { onComplete(false) })
        } else {
            escribirEnFirestore(fotoUri)
        }
    }

    fun eliminarProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina").document(proyecto.id).delete()
            .addOnSuccessListener { borrarFotoDeStorage(proyecto.fotoUri) }
            .addOnFailureListener { e -> Log.e("Firebase", "Error al eliminar proyecto", e) }
    }

    fun actualizarFotoProyecto(
        proyecto: ProyectoCrochet,
        fotoLocal: String,
        onComplete: (String?) -> Unit
    ) {
        subirFoto(proyecto.id, Uri.parse(fotoLocal), onSuccess = { urlDescarga ->
            db.collection("proyectos_mina").document(proyecto.id)
                .update("fotoUri", urlDescarga)
                .addOnSuccessListener {
                    val fotoAnterior = proyecto.fotoUri
                    proyecto.fotoUri = urlDescarga
                    if (fotoAnterior != urlDescarga) borrarFotoDeStorage(fotoAnterior)
                    onComplete(urlDescarga)
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error al guardar la URL de la foto", e)
                    // La nueva foto no quedó vinculada a ningún proyecto.
                    borrarFotoDeStorage(urlDescarga)
                    onComplete(null)
                }
        }, onFailure = {
            onComplete(null)
        })
    }

    fun eliminarFotoProyecto(proyecto: ProyectoCrochet, onComplete: (Boolean) -> Unit) {
        val fotoAnterior = proyecto.fotoUri
        db.collection("proyectos_mina").document(proyecto.id).update("fotoUri", null)
            .addOnSuccessListener {
                proyecto.fotoUri = null
                borrarFotoDeStorage(fotoAnterior)
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error al eliminar la foto del proyecto", e)
                onComplete(false)
            }
    }

    private fun subirFoto(
        idProyecto: String,
        uriLocal: Uri,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        // Cada carga usa una ruta nueva. Así, al reemplazar una foto, la versión anterior
        // sigue disponible hasta que Firestore apunte correctamente a la nueva URL.
        val storageRef = storage.reference.child("fotos_proyectos/$idProyecto/${UUID.randomUUID()}.jpg")

        asegurarSesionEnFirebase(onSuccess = {
            storageRef.putFile(uriLocal)
                .continueWithTask { tareaSubida ->
                    if (!tareaSubida.isSuccessful) {
                        throw tareaSubida.exception ?: IllegalStateException("No se pudo subir la foto")
                    }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { uriDescarga ->
                    Log.d("Firebase", "Foto respaldada en Storage: $uriDescarga")
                    onSuccess(uriDescarga.toString())
                }
                .addOnFailureListener { error ->
                    Log.e("Firebase", "No se pudo respaldar la foto en Storage", error)
                    storageRef.delete()
                    onFailure()
                }
        }, onFailure = onFailure)
    }

    private fun asegurarSesionEnFirebase(onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (auth.currentUser != null) {
            onSuccess()
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                Log.e("Firebase", "No se pudo iniciar sesión para respaldar la foto", error)
                onFailure()
            }
    }

    private fun borrarFotoDeStorage(fotoUri: String?) {
        if (fotoUri.isNullOrBlank() || !fotoUri.startsWith("http")) return

        try {
            storage.getReferenceFromUrl(fotoUri).delete()
                .addOnFailureListener { e -> Log.e("Firebase", "Error al borrar foto de Storage", e) }
        } catch (e: Exception) {
            // Las rutas locales antiguas y URLs de servicios externos no pertenecen a Storage.
            Log.w("Firebase", "La foto no pertenece a Firebase Storage", e)
        }
    }

    private fun String?.esUriLocal(): Boolean = !isNullOrBlank() && !startsWith("http")

    fun actualizarCarpetasProyecto(proyecto: ProyectoCrochet) {
        db.collection("proyectos_mina")
            .document(proyecto.id)
            .update("carpetas", proyecto.carpetas)
            .addOnSuccessListener { Log.d("Firebase", "Carpetas actualizadas") }
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
        carpetasTemp.clear()
    }
}
