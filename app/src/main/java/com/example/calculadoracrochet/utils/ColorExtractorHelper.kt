package com.example.calculadoracrochet.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Color of a project card taken from its photo (see [ColorDelTejido]). */
object ColorExtractorHelper {

    data class ColorTarjetaConfig(
        val backgroundColor: Int,
        val textColorPrimario: Int,
        val textColorSecundario: Int,
        val iconTint: Int,
        val esOscuro: Boolean
    )

    /** Result of analyzing a photo. [config] is null when the piece is white or pale and the card keeps its normal look. */
    class Analisis(val config: ColorTarjetaConfig?)

    /** Bumped when the way colors are picked changes, so colors saved by an older version are picked again. */
    private const val PREFS_COLORES = "colores_tarjetas_v2"
    private const val SIN_COLOR = 0

    private val cache = LruCache<String, Analisis>(200)
    private val hiloPrincipal = Handler(Looper.getMainLooper())
    private val ejecutor: Executor = Executors.newSingleThreadExecutor { tarea ->
        Thread(tarea, "color-tarjetas").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    /** Photos being analyzed, with who waits for each, so a card bound twice does not analyze it twice. */
    private val enCurso = HashMap<String, MutableList<(Analisis) -> Unit>>()

    /**
     * Colors already picked, kept across launches: without them every card would start white and
     * change once its photo is analyzed, each time the app opens.
     */
    @Volatile
    private var guardados: SharedPreferences? = null

    /** Starts loading the saved colors (SharedPreferences reads its file on its own thread). */
    fun inicializar(context: Context) {
        if (guardados == null) {
            guardados = context.applicationContext.getSharedPreferences(PREFS_COLORES, Context.MODE_PRIVATE)
        }
    }

    /** Null when this photo has not been analyzed yet. */
    fun obtenerEnCache(clave: String): Analisis? {
        cache.get(clave)?.let { return it }
        val prefs = guardados ?: return null
        if (!prefs.contains(clave)) return null
        val color = prefs.getInt(clave, SIN_COLOR)
        return Analisis(if (color == SIN_COLOR) null else configDesdeColor(color)).also { cache.put(clave, it) }
    }

    /**
     * Picks the card color of [bitmap] off the main thread; [onResultado] runs on the main thread.
     * The bitmap is only read here, on the calling thread, so it may be recycled afterwards.
     */
    fun extraerColor(bitmap: Bitmap, clave: String, onResultado: (Analisis) -> Unit) {
        obtenerEnCache(clave)?.let {
            onResultado(it)
            return
        }
        enCurso[clave]?.let {
            it += onResultado
            return
        }

        val pixeles = leerMuestra(bitmap) ?: return
        enCurso[clave] = mutableListOf(onResultado)

        ejecutor.execute {
            val color = runCatching {
                ColorDelTejido.elegir(pixeles, ColorDelTejido.LADO_MUESTRA, ColorDelTejido.LADO_MUESTRA)
            }.getOrNull()
            val analisis = Analisis(color?.let(::configDesdeColor))
            guardados?.edit()?.putInt(clave, color ?: SIN_COLOR)?.apply()

            hiloPrincipal.post {
                cache.put(clave, analisis)
                enCurso.remove(clave)?.forEach { it(analisis) }
            }
        }
    }

    /** Forgets the colors of photos no project uses anymore. */
    fun podarGuardados(clavesVigentes: Set<String>) {
        val prefs = guardados ?: return
        if (clavesVigentes.isEmpty()) return
        ejecutor.execute {
            val sobrantes = prefs.all.keys - clavesVigentes
            if (sobrantes.isNotEmpty()) prefs.edit().apply { sobrantes.forEach { remove(it) } }.apply()
        }
    }

    fun limpiarCache() {
        cache.evictAll()
        guardados?.edit()?.clear()?.apply()
    }

    /** The photo scaled to the analyzed size, as pixels. Null for bitmaps whose pixels cannot be read. */
    private fun leerMuestra(bitmap: Bitmap): IntArray? {
        if (bitmap.isRecycled) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) return null
        return try {
            val lado = ColorDelTejido.LADO_MUESTRA
            val muestra = Bitmap.createScaledBitmap(bitmap, lado, lado, true)
            IntArray(lado * lado).also { muestra.getPixels(it, 0, lado, 0, 0, lado, lado) }
                .also { if (muestra !== bitmap) muestra.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    private fun configDesdeColor(fondo: Int): ColorTarjetaConfig {
        val contrasteBlanco = ColorUtils.calculateContrast(Color.WHITE, fondo)
        val contrasteNegro = ColorUtils.calculateContrast(Color.BLACK, fondo)
        val oscuro = contrasteBlanco >= contrasteNegro
        // Text a little tinted by the card instead of pure black/white reads softer on the pastel cards
        val primario = if (oscuro) Color.WHITE else ColorUtils.blendARGB(Color.BLACK, fondo, 0.12f)
        val secundario = if (oscuro) ColorUtils.setAlphaComponent(Color.WHITE, 0xD9)
        else ColorUtils.setAlphaComponent(ColorUtils.blendARGB(Color.BLACK, fondo, 0.2f), 0xC0)
        return ColorTarjetaConfig(
            backgroundColor = fondo,
            textColorPrimario = primario,
            textColorSecundario = secundario,
            iconTint = primario,
            esOscuro = oscuro
        )
    }
}
