package com.example.calculadoracrochet.ui.comun

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class EncuadreImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imgMatrix = Matrix()
    private var sourceBitmap: Bitmap? = null

    private var currentScale = 1f
    private var minScale = 1f
    private var maxScale = 5f

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
                imgMatrix.postTranslate(-distanceX, -distanceY)
                checkBounds()
                imageMatrix = imgMatrix
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > minScale * 1.3f) {
                    resetCrop()
                } else {
                    val targetScale = minScale * 2f
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

    fun setSourceBitmap(bitmap: Bitmap) {
        this.sourceBitmap = bitmap
        setImageBitmap(bitmap)
        resetCrop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetCrop()
    }

    fun resetCrop() {
        val bmp = sourceBitmap ?: return
        if (width == 0 || height == 0) {
            post { resetCrop() }
            return
        }

        val scaleX = width.toFloat() / bmp.width.toFloat()
        val scaleY = height.toFloat() / bmp.height.toFloat()
        minScale = maxOf(scaleX, scaleY)
        maxScale = minScale * 4f
        currentScale = minScale

        val scaledW = bmp.width * minScale
        val scaledH = bmp.height * minScale
        val dx = (width.toFloat() - scaledW) / 2f
        val dy = (height.toFloat() - scaledH) / 2f

        imgMatrix.reset()
        imgMatrix.postScale(minScale, minScale)
        imgMatrix.postTranslate(dx, dy)
        imageMatrix = imgMatrix
    }

    private fun checkBounds() {
        val bmp = sourceBitmap ?: return
        val rect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        imgMatrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        if (rect.left > 0f) deltaX = -rect.left
        if (rect.right < viewW) deltaX = viewW - rect.right

        if (rect.top > 0f) deltaY = -rect.top
        if (rect.bottom < viewH) deltaY = viewH - rect.bottom

        imgMatrix.postTranslate(deltaX, deltaY)
    }

    fun recortarBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (width == 0 || height == 0) return null

        val inverse = Matrix()
        if (!imgMatrix.invert(inverse)) return null

        val visibleRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        inverse.mapRect(visibleRect)

        val x = visibleRect.left.toInt().coerceIn(0, bmp.width - 1)
        val y = visibleRect.top.toInt().coerceIn(0, bmp.height - 1)
        val w = visibleRect.width().toInt().coerceIn(1, bmp.width - x)
        val h = visibleRect.height().toInt().coerceIn(1, bmp.height - y)

        val lado = minOf(w, h)
        return Bitmap.createBitmap(bmp, x, y, lado, lado)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }
}
