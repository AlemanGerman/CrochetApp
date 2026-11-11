package com.example.calculadoracrochet.ui.comun

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import com.example.calculadoracrochet.R
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Header of a screen: its illustrated pattern redrawn tone on tone over the header purple, so
 * every screen keeps its own motif while the white titles stay readable. A soft shade at the top
 * keeps the status bar icons legible and one at the bottom sits behind the titles.
 *
 * The pattern is cut and tinted off the main thread, once per size, and fades in when ready.
 * The originals of the patterns, in color and full screen size, are kept in diseno/fondos.
 */
class FondoPatronView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** An encabezado_* strip: the top of a screen pattern in grays, 1080 px wide. */
    @DrawableRes
    var patron: Int = 0
        set(value) {
            if (field == value) return
            field = value
            mosaico = null
            pedirMosaico()
        }

    @ColorInt
    private var colorBase = ContextCompat.getColor(context, R.color.morado_encabezado)

    private var mosaico: Bitmap? = null
    private var aparicion = 1f
    private var claveEsperada: String? = null

    private val pinturaMosaico = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pinturaVeloArriba = Paint()
    private val pinturaVeloAbajo = Paint()
    private val destino = Rect()

    init {
        context.withStyledAttributes(attrs, R.styleable.FondoPatronView, defStyleAttr) {
            colorBase = getColor(R.styleable.FondoPatronView_colorBase, colorBase)
            patron = getResourceId(R.styleable.FondoPatronView_patron, 0)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Takes no room of its own: it fills the header that its siblings size (FrameLayout re-measures it exactly). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val oscuro = ContextCompat.getColor(context, R.color.morado_oscuro)
        pinturaVeloArriba.shader = LinearGradient(
            0f, 0f, 0f, h * 0.35f,
            ColorUtils.setAlphaComponent(oscuro, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        pinturaVeloAbajo.shader = LinearGradient(
            0f, h * 0.35f, 0f, h.toFloat(),
            Color.TRANSPARENT, ColorUtils.setAlphaComponent(oscuro, 110), Shader.TileMode.CLAMP
        )
        if (w != oldw || h != oldh) {
            mosaico = null
            pedirMosaico()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mosaico == null) pedirMosaico()
    }

    private fun pedirMosaico() {
        if (patron == 0 || width == 0 || height == 0) return
        val clave = clave(patron, width, height)
        claveEsperada = clave

        cache.get(clave)?.let {
            mosaico = it
            aparicion = 1f
            invalidate()
            return
        }

        val recursos = resources
        val patronPedido = patron
        val ancho = width
        val alto = height
        val base = colorBase
        hilo.execute {
            val listo = renderizar(recursos, patronPedido, ancho, alto, base)
            principal.post {
                if (listo != null) cache.put(clave, listo)
                if (claveEsperada == clave && listo != null) {
                    mosaico = listo
                    aparecer()
                }
            }
        }
    }

    private fun aparecer() {
        aparicion = 0f
        animate().cancel()
        val inicio = System.nanoTime()
        val paso = object : Runnable {
            override fun run() {
                aparicion = ((System.nanoTime() - inicio) / 1_000_000f / DURACION_APARICION_MS).coerceAtMost(1f)
                invalidate()
                if (aparicion < 1f && isAttachedToWindow) postOnAnimation(this)
            }
        }
        postOnAnimation(paso)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colorBase)
        mosaico?.let {
            pinturaMosaico.alpha = (aparicion * 255).roundToInt()
            destino.set(0, 0, width, height)
            canvas.drawBitmap(it, null, destino, pinturaMosaico)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinturaVeloArriba)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinturaVeloAbajo)
    }

    companion object {
        private const val DURACION_APARICION_MS = 180f

        /** Strength of the motif: how far a stitch lighter or darker than the background moves away from the base. */
        private const val INTENSIDAD = 0.85f

        private val hilo = Executors.newSingleThreadExecutor()
        private val principal = Handler(Looper.getMainLooper())

        /** Rendered headers, a few MB each: enough for the screens of one flow. */
        private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        private fun clave(patron: Int, ancho: Int, alto: Int) = "$patron:$ancho:$alto"

        /**
         * Prepares the header of a screen before it is shown (e.g. the next calculator step),
         * so it appears with its pattern instead of fading it in.
         */
        fun precargar(context: Context, @DrawableRes patron: Int, ancho: Int, alto: Int) {
            if (patron == 0 || ancho <= 0 || alto <= 0) return
            val clave = clave(patron, ancho, alto)
            if (cache.get(clave) != null) return
            val recursos = context.applicationContext.resources
            val base = ContextCompat.getColor(context, R.color.morado_encabezado)
            hilo.execute {
                if (cache.get(clave) != null) return@execute
                val listo = renderizar(recursos, patron, ancho, alto, base) ?: return@execute
                principal.post { cache.put(clave, listo) }
            }
        }

        /**
         * Scales the header strip of the pattern (encabezado_*: the top of the screen pattern, in
         * grays, 1080 px wide) to cover the header from its top edge, and tints it tone on tone with [base].
         */
        private fun renderizar(recursos: Resources, patron: Int, ancho: Int, alto: Int, base: Int): Bitmap? = try {
            val fuente = recursos.openRawResource(patron).use { flujo ->
                BitmapFactory.decodeStream(flujo, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            fuente?.let {
                // Like centerCrop, but anchored at the top: the pattern starts where the screen starts
                val escala = max(ancho / it.width.toFloat(), alto / it.height.toFloat())
                val anchoVisible = (ancho / escala).roundToInt().coerceAtMost(it.width)
                val altoVisible = (alto / escala).roundToInt().coerceAtMost(it.height)
                val izquierda = (it.width - anchoVisible) / 2

                val salida = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
                val pintura = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(matrizTonoSobreTono(base, luminanciaMedia(it)))
                }
                Canvas(salida).drawBitmap(
                    it,
                    Rect(izquierda, 0, izquierda + anchoVisible, altoVisible),
                    Rect(0, 0, ancho, alto),
                    pintura
                )
                it.recycle()
                salida
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            null
        }

        /** Average luminance (0..1) of the pattern: its background, which becomes exactly [base]. */
        private fun luminanciaMedia(bitmap: Bitmap): Float {
            val muestra = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            var suma = 0f
            for (x in 0 until muestra.width) {
                for (y in 0 until muestra.height) {
                    val c = muestra.getPixel(x, y)
                    suma += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
                }
            }
            if (muestra !== bitmap) muestra.recycle()
            return suma / (24 * 24)
        }

        /**
         * Pixels as bright as the background become [base]; brighter ones move towards white and
         * darker ones towards a deep purple, in proportion to how far they are from the background:
         * out = base + (255 - base) * k * (L - media), linear in the luminance L, so it fits a ColorMatrix.
         */
        private fun matrizTonoSobreTono(base: Int, media: Float): ColorMatrix {
            val pesos = floatArrayOf(0.299f, 0.587f, 0.114f)
            val canales = intArrayOf(Color.red(base), Color.green(base), Color.blue(base))
            val m = FloatArray(20)
            for (fila in 0..2) {
                val distancia = max(255 - canales[fila], 60)
                val escala = distancia / 255f * INTENSIDAD
                for (col in 0..2) m[fila * 5 + col] = escala * pesos[col]
                m[fila * 5 + 4] = canales[fila] - distancia * INTENSIDAD * media
            }
            m[18] = 1f
            return ColorMatrix(m)
        }
    }
}
