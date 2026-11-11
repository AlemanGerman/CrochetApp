package com.example.calculadoracrochet.utils

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoracrochet.R
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

/**
 * The rows on screen rise a little and fade in one after the other, for a list whose content was
 * replaced (another folder, another order). Runs once the new rows are laid out.
 */
fun RecyclerView.animarEntradaEnCascada(alTerminar: (() -> Unit)? = null) {
    val desplazamiento = 18 * resources.displayMetrics.density
    doOnPreDraw {
        var retraso = 0L
        for (i in 0 until childCount) {
            val fila = getChildAt(i)
            fila.animate().cancel()
            fila.alpha = 0f
            fila.translationY = desplazamiento
            fila.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(retraso)
                .setDuration(DURACION_CASCADA_MS)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
            retraso = (retraso + RETRASO_ENTRE_FILAS_MS).coerceAtMost(RETRASO_MAXIMO_CASCADA_MS)
        }
        postDelayed({ alTerminar?.invoke() }, retraso + DURACION_CASCADA_MS)
    }
    invalidate()
}

/** Cards and buttons sink a little under the finger and spring back on release, like a soft press. */
@SuppressLint("ClickableViewAccessibility")
fun View.hundirAlPresionar(escala: Float = 0.97f) {
    setOnTouchListener { vista, evento ->
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> vista.animate().scaleX(escala).scaleY(escala)
                .setStartDelay(0).setDuration(110L).setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vista.animate().scaleX(1f).scaleY(1f)
                .setStartDelay(0).setDuration(260L).setInterpolator(OvershootInterpolator(2.5f)).start()
        }
        // Only watches: the click, ripple and long press still happen as usual
        false
    }
}

/** A short bounce, to acknowledge a choice (the folder just picked, a saved value). */
fun View.rebotar(escala: Float = 1.12f) {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate().scaleX(escala).scaleY(escala).setDuration(110L).setInterpolator(DecelerateInterpolator())
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(240L).setInterpolator(OvershootInterpolator(3f)).start()
        }
        .start()
}

/** Fades in or out instead of popping; ends GONE when hidden. */
fun View.fundirVisibilidad(visible: Boolean, duracion: Long = 200L) {
    if (visible == (isVisible && alpha == 1f)) return
    animate().cancel()
    if (visible) {
        if (!isVisible) {
            alpha = 0f
            isVisible = true
        }
        animate().alpha(1f).setStartDelay(0).setDuration(duracion).setInterpolator(DecelerateInterpolator()).start()
    } else {
        if (!isVisible) return
        animate().alpha(0f).setStartDelay(0).setDuration(duracion * 3 / 4).withEndAction {
            isVisible = false
            alpha = 1f
        }.start()
    }
}

/**
 * Counts from the number shown before to [valor], writing each step with [formato].
 * The first time (nothing shown yet) it counts up from zero.
 */
fun TextView.contarHasta(valor: Double, duracion: Long = 650L, formato: (Double) -> String) {
    val clave = R.id.tag_valor_contador
    val anterior = getTag(R.id.tag_animador_contador) as? ValueAnimator
    val desde = (getTag(clave) as? Double) ?: 0.0
    if (desde == valor && getTag(clave) != null) {
        // Already counting (or counted) to this value
        if (anterior?.isRunning != true) text = formato(valor)
        return
    }
    anterior?.cancel()
    setTag(clave, valor)
    if (desde == valor) {
        text = formato(valor)
        return
    }
    val animador = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = duracion
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener { text = formato(desde + (valor - desde) * it.animatedFraction) }
    }
    setTag(R.id.tag_animador_contador, animador)
    animador.start()
}

/**
 * Views inside a scroll that wait hidden until they scroll into sight, then come in. Several that
 * come into sight together enter one after the other. Listens on the scroll view itself, so it
 * goes away with the screen.
 */
class RevelarAlDesplazar(private val scroll: ViewGroup) {

    private class Pendiente(val revelar: (View, Long) -> Unit)

    private val pendientes = LinkedHashMap<View, Pendiente>()
    private val visible = Rect()
    private val minimoVisible = 40 * scroll.resources.displayMetrics.density

    init {
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> revisar() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scroll.post { revisar() } }
    }

    /** [vista] is hidden with [ocultar] now, and shown with [revelar] (given a start delay) once in sight. */
    fun esperar(vista: View, ocultar: (View) -> Unit, revelar: (View, Long) -> Unit) {
        ocultar(vista)
        pendientes[vista] = Pendiente(revelar)
        vista.post { revisar() }
    }

    /** Hidden and rising a little, then fades up into place. */
    fun esperarSubiendo(vista: View, retrasoExtra: Long = 0L) {
        val desplazamiento = 28 * vista.resources.displayMetrics.density
        esperar(
            vista,
            ocultar = {
                it.alpha = 0f
                it.translationY = desplazamiento
            },
            revelar = { v, retraso ->
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(retraso + retrasoExtra)
                    .setDuration(360L)
                    .setInterpolator(DecelerateInterpolator(1.8f))
                    .start()
            }
        )
    }

    fun revisar() {
        if (pendientes.isEmpty()) return
        var retraso = 0L
        val iterador = pendientes.entries.iterator()
        while (iterador.hasNext()) {
            val (vista, pendiente) = iterador.next()
            if (!vista.isAttachedToWindow) {
                // Replaced (e.g. the folder rows rebuilt): nothing to wait for
                if (vista.parent == null) iterador.remove()
                continue
            }
            if (!enPantalla(vista)) continue
            iterador.remove()
            pendiente.revelar(vista, retraso)
            retraso += RETRASO_ENTRE_REVELADOS_MS
        }
    }

    private fun enPantalla(vista: View): Boolean {
        if (vista.height == 0 || !vista.getGlobalVisibleRect(visible)) return false
        return visible.height() >= minOf(vista.height * 0.25f, minimoVisible)
    }

    private companion object {
        const val RETRASO_ENTRE_REVELADOS_MS = 60L
    }
}

private const val DURACION_CASCADA_MS = 280L
private const val RETRASO_ENTRE_FILAS_MS = 35L
private const val RETRASO_MAXIMO_CASCADA_MS = 210L

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
