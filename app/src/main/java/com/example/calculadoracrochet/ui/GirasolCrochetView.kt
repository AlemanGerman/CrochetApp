package com.example.calculadoracrochet.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class GirasolCrochetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val petaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA216")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }

    private val semillaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B4513")
        style = Paint.Style.FILL
    }

    private val semillaStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    private val cFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA216")
        style = Paint.Style.FILL
    }

    private val cStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    private class MiniTurtle(val path: Path) {
        var x = 0.0
        var y = 0.0
        var heading = 0.0
        var isDown = true

        fun penup() { isDown = false }
        fun pendown() { isDown = true }

        fun rt(deg: Double) { heading -= deg }
        fun lt(deg: Double) { heading += deg }

        fun goto(newX: Double, newY: Double) {
            x = newX
            y = newY
            if (isDown) path.lineTo(x.toFloat(), y.toFloat())
            else path.moveTo(x.toFloat(), y.toFloat())
        }

        fun circle(radius: Double, extent: Double) {
            val radHead = Math.toRadians(heading)
            val cx = x - radius * sin(radHead)
            val cy = y + radius * cos(radHead)

            val startAng = atan2(y - cy, x - cx)
            val sweepAng = Math.toRadians(extent)

            val steps = max(3, (extent / 4).toInt())
            for (s in 1..steps) {
                val curAng = startAng + sweepAng * (s.toDouble() / steps)
                val px = cx + radius * cos(curAng)
                val py = cy + radius * sin(curAng)
                if (isDown) path.lineTo(px.toFloat(), py.toFloat())
                else path.moveTo(px.toFloat(), py.toFloat())
            }
            x = cx + radius * cos(startAng + sweepAng)
            y = cy + radius * sin(startAng + sweepAng)
            heading += extent
        }
    }

    private val petalosPathCompleto = Path()
    private val petalosPathAnimado = Path()
    private val pathMeasure = PathMeasure()
    private var longitudTotalPetalos = 0f

    private val semillasCoords = mutableListOf<Pair<Float, Float>>()
    private val posC = listOf(
        Pair(24f, 21f), Pair(21f, 24f), Pair(18f, 27f), Pair(15f, 30f), Pair(12f, 30f),
        Pair(9f, 30f), Pair(6f, 30f), Pair(3f, 30f), Pair(0f, 30f),
        Pair(-3f, 30f), Pair(-6f, 30f), Pair(-9f, 27f), Pair(-12f, 24f),
        Pair(-15f, 24f), Pair(-18f, 21f), Pair(-18f, 18f), Pair(-21f, 15f),
        Pair(-21f, 12f), Pair(-21f, 9f), Pair(-21f, 6f), Pair(-21f, 3f),
        Pair(-21f, 0f), Pair(-21f, -3f), Pair(-21f, -6f), Pair(-21f, -9f),
        Pair(-21f, -12f), Pair(-21f, -15f), Pair(-18f, -18f), Pair(-18f, -21f),
        Pair(-15f, -24f), Pair(-12f, -24f), Pair(-9f, -27f), Pair(-6f, -30f),
        Pair(-3f, -30f), Pair(0f, -30f), Pair(3f, -30f), Pair(6f, -30f),
        Pair(6f, -30f), Pair(12f, -30f), Pair(15f, -30f), Pair(18f, -27f),
        Pair(21f, -24f), Pair(24f, -21f)
    )

    private var progresoAnimacion = 0f
    private var animador: ValueAnimator? = null

    init {
        generarGeometria()
    }

    private fun generarGeometria() {
        petalosPathCompleto.reset()
        val t = MiniTurtle(petalosPathCompleto)
        t.penup()
        t.goto(0.0, -40.0)
        t.pendown()

        for (i in 0 until 16) {
            for (j in 0 until 24) {
                t.rt(90.0)
                t.circle((150 - j * 6).toDouble(), 90.0)
                t.lt(90.0)
                t.circle((150 - j * 6).toDouble(), 90.0)
                t.rt(180.0)
            }
            t.circle(40.0, 24.0)
        }

        pathMeasure.setPath(petalosPathCompleto, false)
        longitudTotalPetalos = pathMeasure.length

        semillasCoords.clear()
        val goldenAng = 137.508
        val phi = goldenAng * (PI / 180.0)

        for (i in 0 until 140) {
            val r = 4.0 * sqrt(i.toDouble())
            val theta = i * phi
            val x = (r * cos(theta)).toFloat()
            val y = (r * sin(theta)).toFloat()
            semillasCoords.add(Pair(x, y))
        }
    }

    fun reproducirAnimacion(alFinalizar: (() -> Unit)? = null) {
        animador?.cancel()
        progresoAnimacion = 0f
        invalidate()

        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 20000L

            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                progresoAnimacion = va.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progresoAnimacion = 1f
                    invalidate()
                    alFinalizar?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    alFinalizar?.invoke()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        canvas.drawColor(Color.BLACK)

        if (progresoAnimacion <= 0f) return

        canvas.save()
        val escala = minOf(w, h) / 520f
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(escala, -escala)

        val fraccionPetalos = (progresoAnimacion / 0.70f).coerceIn(0f, 1f)
        if (fraccionPetalos > 0f) {
            if (fraccionPetalos >= 1f) {
                canvas.drawPath(petalosPathCompleto, petaloPaint)
            } else {
                petalosPathAnimado.reset()
                pathMeasure.getSegment(0f, longitudTotalPetalos * fraccionPetalos, petalosPathAnimado, true)
                canvas.drawPath(petalosPathAnimado, petaloPaint)
            }
        }

        val fraccionSemillas = ((progresoAnimacion - 0.70f) / 0.18f).coerceIn(0f, 1f)
        if (fraccionSemillas > 0f) {
            val semillasVisibles = (semillasCoords.size * fraccionSemillas).toInt()
            for (i in 0 until semillasVisibles) {
                val (sx, sy) = semillasCoords[i]
                canvas.drawCircle(sx, sy, 5f, semillaFillPaint)
                canvas.drawCircle(sx, sy, 5f, semillaStrokePaint)
            }
        }

        val fraccionC = ((progresoAnimacion - 0.88f) / 0.12f).coerceIn(0f, 1f)
        if (fraccionC > 0f) {
            val cVisibles = (posC.size * fraccionC).toInt()
            for (i in 0 until cVisibles) {
                val (cx, cy) = posC[i]
                canvas.drawCircle(cx, cy, 3f, cFillPaint)
                canvas.drawCircle(cx, cy, 3f, cStrokePaint)
            }
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animador?.cancel()
    }
}