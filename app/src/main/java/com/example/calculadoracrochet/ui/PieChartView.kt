package com.example.calculadoracrochet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val nombre: String,
        val cantidad: Int,
        val color: Int
    )

    private val slices = mutableListOf<Slice>()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val slicePercentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rectF = RectF()

    fun setData(nuevosDatos: List<Slice>) {
        slices.clear()
        slices.addAll(nuevosDatos.filter { it.cantidad > 0 })
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val size = minOf(w, h)
        val strokeWidth = size * 0.18f
        arcPaint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 16f
        rectF.set(
            (w - size) / 2f + padding,
            (h - size) / 2f + padding,
            (w + size) / 2f - padding,
            (h + size) / 2f - padding
        )

        val total = slices.sumOf { it.cantidad }
        val cx = w / 2f
        val cy = h / 2f

        if (total == 0) {
            arcPaint.color = Color.parseColor("#ECE6F0")
            canvas.drawArc(rectF, 0f, 360f, false, arcPaint)
            return
        }

        var startAngle = -90f
        slicePercentPaint.textSize = strokeWidth * 0.38f
        val arcRadius = (rectF.width() / 2f)

        slices.forEach { slice ->
            val sweepAngle = (slice.cantidad.toFloat() / total) * 360f
            arcPaint.color = slice.color
            canvas.drawArc(rectF, startAngle, sweepAngle, false, arcPaint)

            val porcentaje = (slice.cantidad.toFloat() / total * 100).toInt()
            if (porcentaje >= 8) {
                val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                val textX = cx + (arcRadius * cos(midAngleRad)).toFloat()
                val textY = cy + (arcRadius * sin(midAngleRad)).toFloat() + (slicePercentPaint.textSize / 3f)
                canvas.drawText("$porcentaje%", textX, textY, slicePercentPaint)
            }

            startAngle += sweepAngle
        }
    }
}