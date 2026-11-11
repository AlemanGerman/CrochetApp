package com.example.calculadoracrochet.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * The app draws edge to edge: the pattern headers run under the status bar and the bottom bars
 * under the navigation bar. These helpers add the system bar sizes to the padding set in XML.
 */

private data class Relleno(val arriba: Int, val abajo: Int)

/** The view's content starts below the status bar. */
fun View.rellenarBajoBarraDeEstado() {
    val inicial = Relleno(paddingTop, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(top = inicial.arriba + barras.top)
        insets
    }
    pedirInsets()
}

/** The view's content ends above the navigation bar, plus [extra] px. */
fun View.rellenarSobreBarraDeNavegacion(extra: Int = 0) {
    val inicial = Relleno(paddingTop, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(bottom = inicial.abajo + barras.bottom + extra)
        insets
    }
    pedirInsets()
}

/** Bottom margin of a floating view (the "Nuevo proyecto" button) over the navigation bar, plus [extra] px. */
fun View.margenSobreBarraDeNavegacion(extra: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = barras.bottom + extra }
        insets
    }
    pedirInsets()
}

/**
 * Screen with a bottom action bar and text fields: the bar rests on the navigation bar and rises
 * above the keyboard while it is open, shrinking [raiz] so the focused field can scroll into view.
 */
fun configurarInsetsConTeclado(raiz: View, barraInferior: View) {
    val inicialBarra = barraInferior.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
        val tecladoAbierto = insets.isVisible(WindowInsetsCompat.Type.ime()) && teclado.bottom > barras.bottom
        if (tecladoAbierto) {
            v.updatePadding(bottom = teclado.bottom)
            barraInferior.updatePadding(bottom = inicialBarra)
        } else {
            v.updatePadding(bottom = 0)
            barraInferior.updatePadding(bottom = inicialBarra + barras.bottom)
        }
        insets
    }
    raiz.pedirInsets()
}

private fun View.pedirInsets() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
