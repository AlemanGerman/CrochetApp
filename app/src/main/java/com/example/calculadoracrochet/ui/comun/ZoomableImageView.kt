package com.example.calculadoracrochet.ui.comun

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imgMatrix = Matrix()
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 5f

    private var animacionZoom: ValueAnimator? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        scaleType = ScaleType.MATRIX

        val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val targetScale = currentScale * factor
                val clampedScale = targetScale.coerceIn(minScale, maxScale)
                val actualFactor = clampedScale / currentScale
                currentScale = clampedScale

                imgMatrix.postScale(actualFactor, actualFactor, detector.focusX, detector.focusY)
                checkBounds()
                imageMatrix = imgMatrix
                return true
            }
        }

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (currentScale > 1f) {
                    imgMatrix.postTranslate(-distanceX, -distanceY)
                    checkBounds()
                    imageMatrix = imgMatrix
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.2f) {
                    animarHacia(matrizAjustada() ?: return true, 1f)
                } else {
                    val targetScale = 2.5f
                    val factor = targetScale / currentScale
                    val destino = Matrix(imgMatrix).apply { postScale(factor, factor, e.x, e.y) }
                    checkBounds(destino)
                    animarHacia(destino, targetScale)
                }
                return true
            }
        }

        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    fun resetZoom() {
        animacionZoom?.cancel()
        if (drawable != null && (width == 0 || height == 0)) {
            post { resetZoom() }
            return
        }
        val ajustada = matrizAjustada() ?: return
        imgMatrix.set(ajustada)
        imageMatrix = imgMatrix
        currentScale = 1f
    }

    /** The whole picture centered in the view: the zoomed-out state. */
    private fun matrizAjustada(): Matrix? {
        val d = drawable ?: return null
        if (width == 0 || height == 0) return null
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return null

        val scale = minOf(width.toFloat() / dw, height.toFloat() / dh)
        return Matrix().apply {
            postScale(scale, scale)
            postTranslate((width.toFloat() - dw * scale) / 2f, (height.toFloat() - dh * scale) / 2f)
        }
    }

    /**
     * Double tap glides to [destino] instead of jumping. Both matrices only scale and translate, so
     * blending their values frame by frame keeps the picture undistorted.
     */
    private fun animarHacia(destino: Matrix, escalaDestino: Float) {
        animacionZoom?.cancel()
        val desde = FloatArray(9).also { imgMatrix.getValues(it) }
        val hasta = FloatArray(9).also { destino.getValues(it) }
        val actual = FloatArray(9)
        val escalaInicial = currentScale
        animacionZoom = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURACION_ZOOM_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val p = it.animatedValue as Float
                for (i in 0 until 9) actual[i] = desde[i] + (hasta[i] - desde[i]) * p
                imgMatrix.setValues(actual)
                currentScale = escalaInicial + (escalaDestino - escalaInicial) * p
                imageMatrix = imgMatrix
            }
            start()
        }
    }

    private fun checkBounds(matriz: Matrix = imgMatrix) {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val rect = RectF(0f, 0f, dw, dh)
        matriz.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0f) deltaX = -rect.left
            if (rect.right < viewWidth) deltaX = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0f) deltaY = -rect.top
            if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom
        }

        matriz.postTranslate(deltaX, deltaY)
    }

    override fun onDetachedFromWindow() {
        animacionZoom?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A new touch (pinch or drag) takes over from a running double-tap glide where it is
        if (event.actionMasked == MotionEvent.ACTION_DOWN) animacionZoom?.cancel()
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)

        if (currentScale > 1f || scaleDetector.isInProgress) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    private companion object {
        const val DURACION_ZOOM_MS = 320L
    }
}
