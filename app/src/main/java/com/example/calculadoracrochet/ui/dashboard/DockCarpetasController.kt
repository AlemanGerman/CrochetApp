package com.example.calculadoracrochet.ui.dashboard

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.rebotar
import kotlin.math.abs
import kotlin.math.sin

/** Folder filter row of the dashboard, with the purple pill that slides to the selected folder. */
class DockCarpetasController(
    private val raiz: View,
    carpetaInicial: String,
    private val estaActivo: () -> Boolean,
    private val onCarpetaSeleccionada: (String) -> Unit
) {

    var carpetaSeleccionada: String = carpetaInicial
        private set

    private val contexto = raiz.context
    private val llContenedor = raiz.findViewById<LinearLayout>(R.id.llCarpetasDashboard)
    private val indicador = IndicadorPildora(contexto).also { llContenedor.background = it }
    private val hsvCarpetas = raiz.findViewById<HorizontalScrollView>(R.id.hsvCarpetas)

    private val vistasCarpetas = mutableListOf<View>()
    private val colorNormal = ContextCompat.getColor(contexto, R.color.tinta)
    private val colorSobrePildora = ContextCompat.getColor(contexto, R.color.white)
    private var animadorSelector: ValueAnimator? = null

    fun configurar() {
        llContenedor.removeAllViews()
        vistasCarpetas.clear()

        val inflater = LayoutInflater.from(contexto)
        CarpetasConfig.listaCarpetas.forEach { carpetaInfo ->
            val itemView = inflater.inflate(R.layout.item_carpeta_dashboard, llContenedor, false)
            val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreCarpeta)

            itemView.findViewById<ImageView>(R.id.ivIconoCarpeta).setImageResource(carpetaInfo.iconoRes)
            tvNombre.text = carpetaInfo.nombre
            itemView.contentDescription = "Carpeta ${carpetaInfo.nombre}"
            actualizarColorTexto(tvNombre, carpetaInfo.nombre == carpetaSeleccionada)

            itemView.setOnClickListener {
                it.hapticSeleccion()
                if (carpetaSeleccionada == carpetaInfo.nombre) return@setOnClickListener

                carpetaSeleccionada = carpetaInfo.nombre
                vistasCarpetas.forEach { vista ->
                    val tv = vista.findViewById<TextView>(R.id.tvNombreCarpeta)
                    vista.isSelected = tv.text.toString() == carpetaSeleccionada
                }
                animarSelectorHacia(itemView)
                itemView.findViewById<View>(R.id.ivIconoCarpeta).rebotar()
                onCarpetaSeleccionada(carpetaSeleccionada)
            }

            itemView.isSelected = carpetaInfo.nombre == carpetaSeleccionada
            vistasCarpetas.add(itemView)
            llContenedor.addView(itemView)
        }

        llContenedor.post { posicionarIndicadorInicial() }
    }

    fun cancelarAnimaciones() {
        animadorSelector?.cancel()
        animadorSelector = null
    }

    private fun posicionarIndicadorInicial() {
        if (!estaActivo()) return
        val indiceInicial = CarpetasConfig.listaCarpetas
            .indexOfFirst { it.nombre == carpetaSeleccionada }
            .coerceAtLeast(0)
        val vistaInicial = llContenedor.getChildAt(indiceInicial) ?: return

        indicador.colocar(vistaInicial.x, vistaInicial.y, vistaInicial.width.toFloat(), vistaInicial.height.toFloat())
        indicador.visible = true
    }

    private fun animarSelectorHacia(vistaDestino: View) {
        animadorSelector?.cancel()

        val inicioX = indicador.izquierda
        val inicioAncho = indicador.ancho
        val finX = vistaDestino.x
        val finAncho = vistaDestino.width.toFloat()

        animadorSelector = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                // The pill stretches a little towards where it goes and catches up, like a drop of yarn
                val estirar = (if (finX > inicioX) 1 else -1) * sin(f * Math.PI).toFloat() * ESTIRAMIENTO * finAncho
                val x = inicioX + (finX - inicioX) * f
                val ancho = inicioAncho + (finAncho - inicioAncho) * f + abs(estirar)
                val izquierda = if (estirar < 0) x + estirar else x
                indicador.colocar(izquierda, vistaDestino.y, ancho, vistaDestino.height.toFloat())
                pintarTextosSegunIndicador(indicador.izquierda, indicador.derecha)
            }
            start()
        }

        hsvCarpetas?.let { scroll ->
            scroll.post {
                val centroX = (vistaDestino.left + vistaDestino.right) / 2 - scroll.width / 2
                scroll.smoothScrollTo(centroX.coerceAtLeast(0), 0)
            }
        }
    }

    private fun actualizarColorTexto(tvNombre: TextView, estaSeleccionada: Boolean) {
        tvNombre.setTextColor(if (estaSeleccionada) colorSobrePildora else colorNormal)
    }

    /** A folder name turns white as the pill slides under it, so it never sits white on the sheet. */
    private fun pintarTextosSegunIndicador(izquierda: Float, derecha: Float) {
        vistasCarpetas.forEach { vista ->
            val solape = (minOf(derecha, vista.x + vista.width) - maxOf(izquierda, vista.x)).coerceAtLeast(0f)
            val cobertura = if (vista.width > 0) (solape / vista.width).coerceIn(0f, 1f) else 0f
            vista.findViewById<TextView>(R.id.tvNombreCarpeta)
                .setTextColor(ColorUtils.blendARGB(colorNormal, colorSobrePildora, cobertura))
        }
    }

    private companion object {
        /** How much longer than the folder the pill gets halfway through the slide. */
        const val ESTIRAMIENTO = 0.18f
    }
}
