package com.example.calculadoracrochet.ui.estadisticas

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.calculadoracrochet.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ring of projects per folder. The first time it gets data it draws itself in a short sweep;
 * when the data changes later the slices grow or shrink to their new size instead of jumping.
 */
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

    /** A slice while it animates: its share of the ring goes from [desde] to [hasta] (0..1). */
    private class Tramo(val slice: Slice, val desde: Float, val hasta: Float) {
        fun fraccion(t: Float) = desde + (hasta - desde) * t
    }

    private var slices: List<Slice> = emptyList()
    private var tramos: List<Tramo> = emptyList()

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
    private val colorVacio = ContextCompat.getColor(context, R.color.lavanda)

    /** Number written in the hole of the ring: the projects counted. */
    private var totalCentro = 0
    private var totalCentroAnterior = 0

    /** How much of the ring is drawn (0..1): grows once, the first time there is data. */
    private var revelado = 0f

    /** Progress from the previous slices to the current ones (0..1). */
    private var mezcla = 1f

    private var animador: ValueAnimator? = null
    private var yaRevelado = false

    /**
     * False while the chart is still out of sight (below in a scroll): the first data waits
     * undrawn and the sweep starts when [revelar] is called, so it is seen drawing itself.
     */
    private var puedeRevelar = true
    private var revelarPendiente = false

    /** The sweep waits for [revelar] instead of starting as soon as there is data. */
    fun esperarParaRevelar() {
        if (!yaRevelado) puedeRevelar = false
    }

    fun revelar() {
        puedeRevelar = true
        if (revelarPendiente) {
            revelarPendiente = false
            animador?.start()
        }
    }

    fun setData(nuevosDatos: List<Slice>, totalProyectos: Int = nuevosDatos.sumOf { it.cantidad }) {
        val nuevas = nuevosDatos.filter { it.cantidad > 0 }
        val total = nuevas.sumOf { it.cantidad }
        contentDescription = if (total == 0) {
            "Sin proyectos en carpetas"
        } else {
            nuevas.joinToString(prefix = "Proyectos por carpeta: ") { "${it.nombre} ${it.porcentaje}%" }
        }
        if (nuevas == slices && totalProyectos == totalCentro && (yaRevelado || animador != null)) return

        // Where each slice is right now, so a change starts from what is on screen
        val actuales = tramos.associate { it.slice.nombre to it.fraccion(mezcla) }
        val colores = tramos.associate { it.slice.nombre to it.slice }
        val nuevasPorNombre = nuevas.associateBy { it.nombre }
        val nombres = (tramos.map { it.slice.nombre } + nuevas.map { it.nombre }).distinct()
        tramos = nombres.map { nombre ->
            val slice = nuevasPorNombre[nombre] ?: colores.getValue(nombre)
            val hasta = nuevasPorNombre[nombre]?.let { it.cantidad.toFloat() / total } ?: 0f
            Tramo(slice, actuales[nombre] ?: 0f, hasta)
        }.sortedBy { t -> nuevas.indexOfFirst { it.nombre == t.slice.nombre }.let { if (it < 0) Int.MAX_VALUE else it } }

        slices = nuevas
        totalCentroAnterior = if (yaRevelado) totalCentro else 0
        totalCentro = totalProyectos

        animador?.cancel()
        if (!yaRevelado) {
            if (total == 0) {
                // Nothing to draw in: show the empty ring at once and keep the sweep for the first data
                revelado = 1f
                mezcla = 1f
                invalidate()
                return
            }
            yaRevelado = true
            revelado = 0f
            mezcla = 1f
            tramos = tramos.map { Tramo(it.slice, it.hasta, it.hasta) }
            animador = ValueAnimator.ofFloat(0f, 1f).apply {
                // Lets the screen settle first, so the ring is seen drawing itself
                startDelay = RETRASO_REVELADO_MS
                duration = DURACION_REVELADO_MS
                interpolator = DecelerateInterpolator(1.7f)
                addUpdateListener {
                    revelado = it.animatedValue as Float
                    invalidate()
                }
            }
            if (puedeRevelar) animador?.start() else revelarPendiente = true
        } else {
            revelado = 1f
            mezcla = 0f
            animador = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURACION_CAMBIO_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener {
                    mezcla = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animador?.cancel()
        animador = null
        revelarPendiente = false
        revelado = 1f
        mezcla = 1f
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val size = minOf(w, h)
        // The ring thickens slightly as it draws in
        val strokeWidth = size * 0.18f * (0.75f + 0.25f * revelado)
        arcPaint.strokeWidth = strokeWidth

        val padding = size * 0.09f + 16f
        rectF.set(
            (w - size) / 2f + padding,
            (h - size) / 2f + padding,
            (w + size) / 2f - padding,
            (h + size) / 2f - padding
        )

        val cx = w / 2f
        val cy = h / 2f
        val conteo = totalCentroAnterior + (totalCentro - totalCentroAnterior) * minOf(revelado, mezcla)

        if (slices.isEmpty() && tramos.all { it.fraccion(mezcla) <= 0f }) {
            arcPaint.color = colorVacio
            canvas.drawArc(rectF, 0f, 360f, false, arcPaint)
            dibujarTotal(canvas, cx, cy, size, totalCentro)
            return
        }

        // A small swirl: the ring starts a bit behind 12 o'clock and settles there
        var startAngle = -90f - 50f * (1f - revelado)
        val finDibujado = startAngle + 360f * revelado
        slicePercentPaint.textSize = size * 0.18f * 0.38f
        slicePercentPaint.typeface = pinturaTotal.typeface
        // Percentages appear once most of the ring is drawn, and while slices resize they hide
        val visibilidadTexto = ((revelado - 0.65f) / 0.35f).coerceIn(0f, 1f) * if (mezcla < 1f) mezcla * mezcla else 1f
        slicePercentPaint.alpha = (visibilidadTexto * 255).roundToInt()
        dibujarTotal(canvas, cx, cy, size, conteo.roundToInt())
        val arcRadius = rectF.width() / 2f

        tramos.forEach { tramo ->
            val sweepAngle = tramo.fraccion(mezcla) * 360f
            if (sweepAngle <= 0f) return@forEach
            arcPaint.color = tramo.slice.color
            val sweepVisible = (finDibujado - startAngle).coerceIn(0f, sweepAngle)
            if (sweepVisible > 0f) canvas.drawArc(rectF, startAngle, sweepVisible, false, arcPaint)

            val porcentaje = if (tramo.hasta > 0f) {
                tramo.slice.porcentaje.takeIf { it > 0 } ?: (tramo.hasta * 100).roundToInt()
            } else {
                0
            }

            if (porcentaje >= 8 && slicePercentPaint.alpha > 0) {
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

    private companion object {
        const val RETRASO_REVELADO_MS = 140L
        const val DURACION_REVELADO_MS = 820L
        const val DURACION_CAMBIO_MS = 420L
    }
}
