package com.example.calculadoracrochet.ui.dashboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.view.OneShotPreDrawListener
import coil.drawable.CrossfadeDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView

/**
 * Opens the project sheet with the photo of the tapped card growing into the photo of the sheet,
 * while the sheet rises and the background darkens. One animator drives the three so they stay in
 * step. The window's own slide/fade and dim are turned off because they run outside the views,
 * where the flying photo could not follow them.
 *
 * Closing (drag down, back, tap outside, [BottomSheetDialog.cancel]) slides the sheet down through
 * its behavior and the dark background fades with it.
 */
internal class EntradaHojaConFoto(
    private val dialog: BottomSheetDialog,
    /** Photo on the card that was tapped; null (or without a loaded picture) only slides the sheet. */
    private val origen: ImageView?,
    /** The photo slot of the sheet: hidden while the copy flies, shown when it lands. */
    private val destino: View,
    /** Shown after the landing (the "change photo" button over the photo). */
    private val apareceAlFinal: View?
) {
    private var animador: ValueAnimator? = null
    private var fantasma: ImageView? = null
    private var entrando = true

    /** Call after setContentView and before show(). */
    fun preparar() {
        val ventana = dialog.window ?: return
        val hoja = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val coordinador = dialog.findViewById<View>(com.google.android.material.R.id.coordinator) ?: return

        ventana.setWindowAnimations(0)
        ventana.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.dismissWithAnimation = true

        val velo = ColorDrawable(Color.BLACK).apply { alpha = 0 }
        coordinador.background = velo

        // While dragged or closing, the darkness follows how much of the sheet is still on screen
        dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) = Unit
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (entrando || bottomSheet.height == 0) return
                val visible = (coordinador.height - bottomSheet.top).toFloat() / bottomSheet.height
                velo.alpha = (ALFA_VELO * visible.coerceIn(0f, 1f)).toInt()
            }
        })

        // Off screen until the first frame, so it never flashes at its final place
        hoja.translationY = hoja.resources.displayMetrics.heightPixels.toFloat()
        val foto = bitmapDe(origen)
        if (foto != null) destino.alpha = 0f

        OneShotPreDrawListener.add(hoja) { iniciar(ventana.decorView as ViewGroup, hoja, velo, foto) }
        dialog.setOnDismissListener { terminar() }
    }

    private fun iniciar(decor: ViewGroup, hoja: View, velo: ColorDrawable, foto: Bitmap?) {
        val altoHoja = hoja.height.toFloat().coerceAtLeast(1f)
        hoja.translationY = altoHoja

        val vuelo = if (foto != null && origen != null) prepararVuelo(decor, foto, origen) else null
        apareceAlFinal?.alpha = if (vuelo != null) 0f else 1f

        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURACION_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val p = it.animatedValue as Float
                hoja.translationY = altoHoja * (1f - p)
                velo.alpha = (ALFA_VELO * p).toInt()
                vuelo?.colocar(p)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = terminar()
            })
            start()
        }
    }

    /** Puts the copy of the card photo in the window overlay, above the sheet. */
    private fun prepararVuelo(decor: ViewGroup, foto: Bitmap, origen: ImageView): Vuelo? {
        if (!origen.isAttachedToWindow || origen.width == 0) return null
        val base = IntArray(2).also { decor.getLocationOnScreen(it) }
        val pos = IntArray(2).also { origen.getLocationOnScreen(it) }
        val inicio = RectF(
            (pos[0] - base[0]).toFloat(),
            (pos[1] - base[1]).toFloat(),
            (pos[0] - base[0] + origen.width).toFloat(),
            (pos[1] - base[1] + origen.height).toFloat()
        )
        val radioInicio = (origen as? ShapeableImageView)?.shapeAppearanceModel
            ?.topLeftCornerSize?.getCornerSize(RectF(0f, 0f, origen.width.toFloat(), origen.height.toFloat())) ?: 0f
        val radioFin = (destino as? MaterialCardView)?.radius ?: 0f

        val copia = ImageView(decor.context).apply {
            setImageBitmap(foto)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        val vuelo = Vuelo(decor, copia, inicio, radioInicio, radioFin, base)
        copia.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, vuelo.radio)
        }
        decor.overlay.add(copia)
        fantasma = copia
        vuelo.colocar(0f)
        return vuelo
    }

    /** Each frame the copy goes from the card toward where the sheet's photo is right now. */
    private inner class Vuelo(
        private val decor: ViewGroup,
        private val copia: ImageView,
        private val inicio: RectF,
        private val radioInicio: Float,
        private val radioFin: Float,
        private val base: IntArray
    ) {
        var radio = radioInicio
            private set
        private val pos = IntArray(2)

        fun colocar(p: Float) {
            destino.getLocationOnScreen(pos)
            val finIzq = (pos[0] - base[0]).toFloat()
            val finArriba = (pos[1] - base[1]).toFloat()
            val izq = lerp(inicio.left, finIzq, p)
            val arriba = lerp(inicio.top, finArriba, p)
            val ancho = lerp(inicio.width(), destino.width.toFloat(), p).toInt().coerceAtLeast(1)
            val alto = lerp(inicio.height(), destino.height.toFloat(), p).toInt().coerceAtLeast(1)
            radio = lerp(radioInicio, radioFin, p)

            copia.measure(
                View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(alto, View.MeasureSpec.EXACTLY)
            )
            copia.layout(izq.toInt(), arriba.toInt(), izq.toInt() + ancho, arriba.toInt() + alto)
            copia.invalidateOutline()
            decor.invalidate()
        }
    }

    /** Lands everything at its final place; also runs if the sheet is closed mid-way. */
    private fun terminar() {
        if (!entrando) return
        entrando = false
        animador?.removeAllListeners()
        animador?.cancel()
        animador = null
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.translationY = 0f
        (dialog.findViewById<View>(com.google.android.material.R.id.coordinator)?.background as? ColorDrawable)?.alpha = ALFA_VELO
        destino.alpha = 1f
        apareceAlFinal?.animate()?.alpha(1f)?.setDuration(150L)?.start()
        fantasma?.let { copia -> (dialog.window?.decorView as? ViewGroup)?.overlay?.remove(copia) }
        fantasma = null
    }

    private companion object {
        const val DURACION_MS = 380L

        /** Same darkness as the Material dim it replaces (0.32). */
        const val ALFA_VELO = 82

        fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p

        /** The picture the card is showing, or null when it is not a loaded photo (placeholder, still loading). */
        fun bitmapDe(vista: ImageView?): Bitmap? {
            var dibujo: Drawable? = vista?.drawable
            while (dibujo is CrossfadeDrawable) dibujo = dibujo.end
            return (dibujo as? BitmapDrawable)?.bitmap
        }
    }
}
