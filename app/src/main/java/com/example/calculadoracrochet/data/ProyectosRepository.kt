package com.example.calculadoracrochet.data

import android.util.Log
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Single access point to the Firestore collection of projects. */
class ProyectosRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(COLECCION)

    fun escucharProyectos(onCambio: (List<ProyectoCrochet>) -> Unit): ListenerRegistration =
        coleccion.addSnapshotListener { documentos, error ->
            if (error != null) {
                Log.e(TAG, "Error al escuchar $COLECCION", error)
                return@addSnapshotListener
            }
            if (documentos == null) return@addSnapshotListener

            val listaProcesada = documentos.mapNotNull { documento ->
                try {
                    documento.toObject(ProyectoCrochet::class.java).apply {
                        id = documento.id
                        // Firestore can leave these null on documents saved before the fields existed
                        carpetas = carpetas ?: emptyList()
                        listaEstambres = listaEstambres ?: emptyList()
                        listaAccesorios = listaAccesorios ?: emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar proyecto: ${documento.id}", e)
                    null
                }
            }.sortedByDescending { it.fechaGuardado }

            onCambio(listaProcesada)
        }

    fun guardar(proyecto: ProyectoCrochet, onResultado: (Boolean) -> Unit) {
        coleccion.document(proyecto.id).set(proyecto)
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar proyecto", e)
                onResultado(false)
            }
    }

    fun actualizarCampos(
        idProyecto: String,
        campos: Map<String, Any?>,
        onResultado: ((Boolean) -> Unit)? = null
    ) {
        coleccion.document(idProyecto).update(campos)
            .addOnSuccessListener { onResultado?.invoke(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar $campos en $idProyecto", e)
                onResultado?.invoke(false)
            }
    }

    fun actualizarCamposEnLote(
        idsProyectos: List<String>,
        campos: Map<String, Any?>,
        onResultado: (Boolean) -> Unit
    ) {
        val batch = db.batch()
        idsProyectos.forEach { batch.update(coleccion.document(it), campos) }
        batch.commit()
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    fun eliminar(idProyecto: String, onResultado: (Boolean) -> Unit) {
        coleccion.document(idProyecto).delete()
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    fun eliminarEnLote(idsProyectos: List<String>, onResultado: (Boolean) -> Unit) {
        val batch = db.batch()
        idsProyectos.forEach { batch.delete(coleccion.document(it)) }
        batch.commit()
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    companion object {
        const val COLECCION = "proyectos_mina"
        private const val TAG = "Firebase"
    }
}
