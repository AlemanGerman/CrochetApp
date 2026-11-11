package com.example.calculadoracrochet.utils

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textfield.TextInputLayout

/** Tapping this view shows/hides [contenido], turning [flecha] upside down while open. Starts collapsed. */
fun View.hacerDesplegable(contenido: View, flecha: View? = null) {
    var expandido = false
    setOnClickListener {
        it.hapticSeleccion()
        expandido = !expandido
        (contenido.parent as? ViewGroup)?.animarCambiosDeLayout()
        contenido.visibility = if (expandido) View.VISIBLE else View.GONE
        flecha?.animate()?.rotation(if (expandido) 180f else 0f)?.setDuration(200L)?.start()
    }
}

/** The next change of children or visibility in this container animates (rows appearing, errors showing). */
fun ViewGroup.animarCambiosDeLayout() {
    if (layoutTransition == null) {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(200L)
        }
    }
}

/** Empty or non-numeric input becomes -1 so it fails "must be positive" checks. */
fun String.aNumeroOInvalido(): Double = trim().toDoubleOrNull() ?: -1.0

fun errorSiVacio(texto: String, mensaje: String): String? = if (texto.isEmpty()) mensaje else null

fun errorSiNoPositivo(valor: Double, mensaje: String): String? = if (valor > 0.0) null else mensaje

/** Used amount must be positive and, when the total is known, not exceed it. */
fun errorCantidadUsada(usada: Double, total: Double, mensajeNoPositivo: String): String? =
    errorSiNoPositivo(usada, mensajeNoPositivo)
        ?: if (total > 0 && usada > total) "Excede el total" else null

/** Shows [mensajeError] on the field (or clears it when null). Returns true when the field is valid. */
fun TextInputLayout?.validar(mensajeError: String?): Boolean {
    this?.error = mensajeError
    return mensajeError == null
}
