package com.example.calculadoracrochet.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.calculadoracrochet.R
import kotlin.math.roundToInt

/**
 * Purple pill behind the selected folder, drawn as the background of the folder row. Being a
 * drawable it takes no part in measuring the row, and moving or resizing it while it slides only
 * redraws: no layout pass of the dashboard on every frame, and a layout of the list meanwhile
 * cannot snap it back.
 */
class IndicadorPildora(context: Context) : Drawable() {

    private val pildora = ContextCompat.getDrawable(context, R.drawable.bg_selector_pildora)!!.mutate()

    var izquierda = 0f
        private set
    var ancho = 0f
        private set
    private var arriba = 0f
    private var alto = 0f

    var visible = false
        set(valor) {
            field = valor
            invalidateSelf()
        }

    val derecha: Float get() = izquierda + ancho

    /** Position in the coordinates of the row (the same as its children's x and y). */
    fun colocar(izquierda: Float, arriba: Float, ancho: Float, alto: Float) {
        this.izquierda = izquierda
        this.arriba = arriba
        this.ancho = ancho
        this.alto = alto
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (!visible || ancho <= 0f || alto <= 0f) return
        pildora.setBounds(
            bounds.left + izquierda.roundToInt(),
            bounds.top + arriba.roundToInt(),
            bounds.left + (izquierda + ancho).roundToInt(),
            bounds.top + (arriba + alto).roundToInt()
        )
        pildora.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        pildora.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pildora.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
