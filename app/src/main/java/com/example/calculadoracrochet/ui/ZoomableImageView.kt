package com.example.calculadoracrochet.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
                    resetZoom()
                } else {
                    val targetScale = 2.5f
                    val factor = targetScale / currentScale
                    currentScale = targetScale
                    imgMatrix.postScale(factor, factor, e.x, e.y)
                    checkBounds()
                    imageMatrix = imgMatrix
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
        val d = drawable ?: return
        if (width == 0 || height == 0) {
            post { resetZoom() }
            return
        }
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val scale = minOf(width.toFloat() / dw, height.toFloat() / dh)
        val dx = (width.toFloat() - dw * scale) / 2f
        val dy = (height.toFloat() - dh * scale) / 2f

        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        imgMatrix.postTranslate(dx, dy)
        imageMatrix = imgMatrix
        currentScale = 1f
    }

    private fun checkBounds() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val rect = RectF(0f, 0f, dw, dh)
        imgMatrix.mapRect(rect)

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

        imgMatrix.postTranslate(deltaX, deltaY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)

        if (currentScale > 1f || scaleDetector.isInProgress) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }
}