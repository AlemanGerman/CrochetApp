package com.example.calculadoracrochet.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.android.gms.tasks.Task
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
                    ProyectoMapper.desdeMapa(documento.id, documento.data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar proyecto: ${documento.id}", e)
                    null
                }
            }.sortedByDescending { it.fechaGuardado }

            onCambio(listaProcesada)
        }

    fun guardar(proyecto: ProyectoCrochet, onResultado: (Boolean) -> Unit) {
        coleccion.document(proyecto.id).set(proyecto)
            .alConfirmarOEncolar("guardar proyecto ${proyecto.id}", onResultado)
    }

    fun actualizarCampos(
        idProyecto: String,
        campos: Map<String, Any?>,
        onResultado: ((Boolean) -> Unit)? = null
    ) {
        coleccion.document(idProyecto).update(campos)
            .alConfirmarOEncolar("actualizar $campos en $idProyecto") { onResultado?.invoke(it) }
    }

    fun actualizarCamposEnLote(
        idsProyectos: List<String>,
        campos: Map<String, Any?>,
        onResultado: (Boolean) -> Unit
    ) {
        val batch = db.batch()
        idsProyectos.forEach { batch.update(coleccion.document(it), campos) }
        batch.commit().alConfirmarOEncolar("actualizar $campos en lote", onResultado)
    }

    fun eliminar(idProyecto: String, onResultado: (Boolean) -> Unit) {
        coleccion.document(idProyecto).delete()
            .alConfirmarOEncolar("eliminar $idProyecto", onResultado)
    }

    fun eliminarEnLote(idsProyectos: List<String>, onResultado: (Boolean) -> Unit) {
        val batch = db.batch()
        idsProyectos.forEach { batch.delete(coleccion.document(it)) }
        batch.commit().alConfirmarOEncolar("eliminar en lote", onResultado)
    }

    /**
     * Firestore applies writes to the local cache right away, but the Task only completes once the
     * server acknowledges them, which may never happen on a slow or missing connection. If there is
     * no answer within [ESPERA_CONFIRMACION_MS] the write is already queued and will sync later,
     * so report success instead of leaving the UI waiting. Answers at most once.
     */
    private fun Task<Void>.alConfirmarOEncolar(descripcion: String, onResultado: (Boolean) -> Unit) {
        var respondido = false
        fun responder(exito: Boolean) {
            if (respondido) return
            respondido = true
            onResultado(exito)
        }

        val handler = Handler(Looper.getMainLooper())
        val porTiempo = Runnable {
            Log.w(TAG, "Sin confirmación del servidor al $descripcion; queda en cola para sincronizar")
            responder(true)
        }
        handler.postDelayed(porTiempo, ESPERA_CONFIRMACION_MS)

        addOnSuccessListener {
            handler.removeCallbacks(porTiempo)
            responder(true)
        }
        addOnFailureListener { e ->
            handler.removeCallbacks(porTiempo)
            Log.e(TAG, "Error al $descripcion", e)
            responder(false)
        }
    }

    companion object {
        const val COLECCION = "proyectos_mina"
        private const val TAG = "Firebase"
        private const val ESPERA_CONFIRMACION_MS = 3_000L
    }
}
