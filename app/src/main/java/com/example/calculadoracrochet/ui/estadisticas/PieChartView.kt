package com.example.calculadoracrochet.ui.estadisticas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.calculadoracrochet.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val nombre: String,
        val cantidad: Int,
        val color: Int,
        val porcentaje: Int = 0
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

    private val pinturaTotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tinta)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_extrabold)
    }
    private val pinturaLeyenda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tinta_suave)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_semibold)
    }

    /** Number written in the hole of the ring: the projects counted. */
    private var totalCentro = 0

    fun setData(nuevosDatos: List<Slice>, totalProyectos: Int = nuevosDatos.sumOf { it.cantidad }) {
        totalCentro = totalProyectos
        slices.clear()
        slices.addAll(nuevosDatos.filter { it.cantidad > 0 })
        val total = slices.sumOf { it.cantidad }
        contentDescription = if (total == 0) {
            "Sin proyectos en carpetas"
        } else {
            slices.joinToString(prefix = "Proyectos por carpeta: ") { "${it.nombre} ${it.porcentaje}%" }
        }
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
            arcPaint.color = ContextCompat.getColor(context, R.color.lavanda)
            canvas.drawArc(rectF, 0f, 360f, false, arcPaint)
            dibujarTotal(canvas, cx, cy, size, totalCentro)
            return
        }

        var startAngle = -90f
        slicePercentPaint.textSize = strokeWidth * 0.38f
        slicePercentPaint.typeface = pinturaTotal.typeface
        dibujarTotal(canvas, cx, cy, size, totalCentro)
        val arcRadius = (rectF.width() / 2f)

        slices.forEach { slice ->
            val sweepAngle = (slice.cantidad.toFloat() / total) * 360f
            arcPaint.color = slice.color
            canvas.drawArc(rectF, startAngle, sweepAngle, false, arcPaint)

            val porcentaje = if (slice.porcentaje > 0) {
                slice.porcentaje
            } else {
                (slice.cantidad.toFloat() / total * 100).roundToInt()
            }

            if (porcentaje >= 8) {
                val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                val textX = cx + (arcRadius * cos(midAngleRad)).toFloat()
                val textY = cy + (arcRadius * sin(midAngleRad)).toFloat() + (slicePercentPaint.textSize / 3f)
                canvas.drawText("$porcentaje%", textX, textY, slicePercentPaint)
            }

            startAngle += sweepAngle
        }
    }

    /** Projects counted, in the hole of the ring. */
    private fun dibujarTotal(canvas: Canvas, cx: Float, cy: Float, size: Float, total: Int) {
        pinturaTotal.textSize = size * 0.15f
        pinturaLeyenda.textSize = size * 0.065f
        canvas.drawText(total.toString(), cx, cy + pinturaTotal.textSize * 0.2f, pinturaTotal)
        canvas.drawText(if (total == 1) "proyecto" else "proyectos", cx, cy + pinturaTotal.textSize * 0.2f + pinturaLeyenda.textSize * 1.5f, pinturaLeyenda)
    }
}
