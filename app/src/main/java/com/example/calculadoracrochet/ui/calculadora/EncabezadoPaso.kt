package com.example.calculadoracrochet.ui.calculadora

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.comun.FondoPatronView
import com.example.calculadoracrochet.utils.configurarInsetsConTeclado
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado

/** The four steps that ask for data, then the result. */
object PasosCalculadora {
    val NOMBRES = listOf("Materiales", "Patrón", "Mano de obra", "Empaque")
    val TOTAL = NOMBRES.size
    val RESULTADO = TOTAL + 1

    fun etiqueta(paso: Int): String = if (paso >= RESULTADO) "¡Listo!" else "Paso $paso de $TOTAL"

    fun descripcion(paso: Int): String =
        if (paso >= RESULTADO) "Cálculo terminado" else "Paso $paso de $TOTAL: ${NOMBRES[paso.coerceIn(1, TOTAL) - 1]}"
}

/**
 * Fills the shared header (encabezado_paso) and bottom bar (barra_accion_paso) of a calculator
 * screen: pattern, texts, progress, back button and the system bar and keyboard insets.
 */
object EncabezadoPaso {

    fun configurar(
        raiz: View,
        paso: Int,
        titulo: String,
        subtitulo: String,
        @DrawableRes patron: Int,
        @DrawableRes patronSiguiente: Int = 0,
        alVolver: () -> Unit
    ) {
        raiz.findViewById<FondoPatronView>(R.id.fondoEncabezadoPaso).patron = patron
        raiz.findViewById<TextView>(R.id.tvTituloPaso).text = titulo
        raiz.findViewById<TextView>(R.id.tvSubtituloPaso).text = subtitulo
        raiz.findViewById<TextView>(R.id.tvPasoActual).apply {
            text = PasosCalculadora.etiqueta(paso)
            contentDescription = PasosCalculadora.descripcion(paso)
        }

        val progreso = raiz.findViewById<ViewGroup>(R.id.llProgresoPasos)
        for (i in 0 until progreso.childCount) {
            progreso.getChildAt(i).setBackgroundResource(
                if (i < paso) R.drawable.bg_progreso_hecho else R.drawable.bg_progreso_pendiente
            )
        }

        raiz.findViewById<View>(R.id.btnVolverPaso).setOnClickListener {
            it.hapticClickPrincipal()
            alVolver()
        }

        raiz.findViewById<View>(R.id.btnCancelarProyecto).setOnClickListener {
            it.hapticClickPrincipal()
            (raiz.context as? MainActivity)?.cancelarProyecto()
        }

        raiz.findViewById<View>(R.id.contenidoEncabezadoPaso).rellenarBajoBarraDeEstado()
        configurarInsetsConTeclado(raiz, raiz.findViewById(R.id.barraAccionPaso))

        // The sheet clips what scrolls under its rounded corners
        raiz.findViewById<View>(R.id.hojaPaso)?.clipToOutline = true

        if (patronSiguiente != 0) {
            val encabezado = raiz.findViewById<View>(R.id.encabezadoPaso)
            encabezado.post {
                FondoPatronView.precargar(raiz.context, patronSiguiente, encabezado.width, encabezado.height)
            }
        }
    }

    /** Amount shown in the bottom bar while the step is filled in. */
    fun mostrarMonto(raiz: View, etiqueta: String, monto: String) {
        raiz.findViewById<TextView>(R.id.tvEtiquetaAcumulado).text = etiqueta
        raiz.findViewById<TextView>(R.id.tvMontoAcumulado).text = monto
        raiz.findViewById<View>(R.id.llMontoAcumulado).contentDescription = "$etiqueta: $monto"
    }
}
