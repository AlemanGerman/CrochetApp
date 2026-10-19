package com.example.calculadoracrochet.utils

import android.view.View
import com.google.android.material.textfield.TextInputLayout

/** Tapping this view shows/hides [contenido]. Starts collapsed. */
fun View.hacerDesplegable(contenido: View) {
    var expandido = false
    setOnClickListener {
        it.hapticSeleccion()
        expandido = !expandido
        contenido.visibility = if (expandido) View.VISIBLE else View.GONE
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
