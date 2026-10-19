package com.example.calculadoracrochet.ui.dashboard

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.hapticSeleccion

/** Folder dock at the bottom of the dashboard, with the animated pill that follows the selected folder. */
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
    private val indicador = raiz.findViewById<View>(R.id.indicadorSelector)
    private val hsvCarpetas = raiz.findViewById<HorizontalScrollView>(R.id.hsvCarpetas)

    private val vistasCarpetas = mutableListOf<View>()
    private var animadorSelector: ValueAnimator? = null

    fun configurar() {
        raiz.findViewById<View>(R.id.dockCarpetas).clipToOutline = true

        indicador.elevation = 0f
        llContenedor.elevation = 2f
        llContenedor.bringToFront()

        llContenedor.removeAllViews()
        vistasCarpetas.clear()

        val inflater = LayoutInflater.from(contexto)
        CarpetasConfig.listaCarpetas.forEach { carpetaInfo ->
            val itemView = inflater.inflate(R.layout.item_carpeta_dashboard, llContenedor, false)
            val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreCarpeta)

            itemView.findViewById<ImageView>(R.id.ivIconoCarpeta).setImageResource(carpetaInfo.iconoRes)
            tvNombre.text = carpetaInfo.nombre
            itemView.alpha = 1.0f
            actualizarColorTexto(tvNombre, carpetaInfo.nombre == carpetaSeleccionada)

            itemView.setOnClickListener {
                it.hapticSeleccion()
                if (carpetaSeleccionada == carpetaInfo.nombre) return@setOnClickListener

                carpetaSeleccionada = carpetaInfo.nombre
                vistasCarpetas.forEach { vista ->
                    val tv = vista.findViewById<TextView>(R.id.tvNombreCarpeta)
                    actualizarColorTexto(tv, tv.text.toString() == carpetaSeleccionada)
                }
                animarSelectorHacia(itemView)
                onCarpetaSeleccionada(carpetaSeleccionada)
            }

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

        indicador.layoutParams = indicador.layoutParams.apply {
            width = vistaInicial.width
            height = vistaInicial.height
        }
        indicador.translationX = vistaInicial.x
        indicador.translationY = vistaInicial.y
        indicador.visibility = View.VISIBLE
    }

    private fun animarSelectorHacia(vistaDestino: View) {
        animadorSelector?.cancel()

        val inicioX = indicador.translationX
        val finX = vistaDestino.x
        val inicioAncho = indicador.width
        val finAncho = vistaDestino.width
        val inicioAlto = indicador.height
        val finAlto = vistaDestino.height

        animadorSelector = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                indicador.translationX = inicioX + (finX - inicioX) * fraction

                val anchoActual = (inicioAncho + (finAncho - inicioAncho) * fraction).toInt()
                val altoActual = (inicioAlto + (finAlto - inicioAlto) * fraction).toInt()

                val params = indicador.layoutParams
                if (params.width != anchoActual || params.height != altoActual) {
                    params.width = anchoActual
                    params.height = altoActual
                    indicador.layoutParams = params
                }
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
        val color = if (estaSeleccionada) R.color.purple_primary else R.color.gray_light
        tvNombre.setTextColor(ContextCompat.getColor(contexto, color))
    }
}
