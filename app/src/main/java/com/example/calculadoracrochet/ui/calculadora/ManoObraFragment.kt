package com.example.calculadoracrochet.ui.calculadora

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.comoTextoEditable
import com.example.calculadoracrochet.utils.duracionLegible
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.horasAMinutos
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

class ManoObraFragment : Fragment(R.layout.fragment_mano_obra) {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private var ultimoRetrocesoTiempo: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EncabezadoPaso.configurar(
            raiz = view,
            paso = 3,
            titulo = "Mano de obra",
            subtitulo = "¿Cuánto tiempo duraste ignorándome?",
            patron = R.drawable.encabezado_mano_obra,
            patronSiguiente = R.drawable.encabezado_empaque,
            alVolver = { requireActivity().onBackPressedDispatcher.onBackPressed() }
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tiempoActual = SystemClock.elapsedRealtime()
                if (tiempoActual - ultimoRetrocesoTiempo < 400L) return
                ultimoRetrocesoTiempo = tiempoActual

                (requireActivity() as MainActivity).regresarDeslizando()
            }
        })

        val tilPagoHora = view.findViewById<TextInputLayout>(R.id.tilPagoHora)
        val etPagoHora = view.findViewById<EditText>(R.id.etPagoHora)
        val etHoras = view.findViewById<EditText>(R.id.etHoras)
        val etMinutos = view.findViewById<EditText>(R.id.etMinutos)
        val tvResumenTiempo = view.findViewById<TextView>(R.id.tvResumenTiempo)

        fun horas() = etHoras.text.toString().toIntOrNull()?.coerceIn(0, MAXIMO_HORAS) ?: 0
        fun minutos() = etMinutos.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0

        fun actualizarResumen() {
            val totalHoras = horas() + minutos() / 60.0
            tvResumenTiempo.text = if (totalHoras > 0.0) {
                "Le dedicaste ${duracionLegible(totalHoras)}"
            } else {
                "Aún no has puesto el tiempo"
            }
            val pago = etPagoHora.text.toString().trim().toDoubleOrNull()
            EncabezadoPaso.mostrarMonto(
                view,
                "Mano de obra",
                CostoEnVivo.manoDeObra(pago, horas(), minutos())?.comoDinero() ?: "—"
            )
        }

        if (viewModel.calculo.pagoPorHora > 0.0) {
            etPagoHora.setText(viewModel.calculo.pagoPorHora.comoTextoEditable())
        }
        if (viewModel.calculo.horasTrabajadas > 0.0) {
            val totalMinutos = horasAMinutos(viewModel.calculo.horasTrabajadas)
            etHoras.setText((totalMinutos / 60).coerceAtMost(MAXIMO_HORAS).toString())
            etMinutos.setText((totalMinutos % 60).toString())
        }

        etPagoHora.doAfterTextChanged {
            tilPagoHora.error = null
            actualizarResumen()
        }
        etHoras.doAfterTextChanged { actualizarResumen() }
        etMinutos.doAfterTextChanged {
            // Typing 75 minutes keeps the field honest: it becomes 59
            val valor = it?.toString()?.toIntOrNull()
            if (valor != null && valor > 59) {
                etMinutos.setText("59")
                etMinutos.setSelection(2)
            }
            actualizarResumen()
        }

        fun cambiar(campo: EditText, delta: Int, maximo: Int, vista: View) {
            val actual = campo.text.toString().toIntOrNull() ?: 0
            val nuevo = (actual + delta).coerceIn(0, maximo)
            if (nuevo != actual) vista.hapticSeleccion()
            campo.setText(nuevo.toString())
            campo.clearFocus()
        }
        view.findViewById<View>(R.id.btnMenosHoras).setOnClickListener { cambiar(etHoras, -1, MAXIMO_HORAS, it) }
        view.findViewById<View>(R.id.btnMasHoras).setOnClickListener { cambiar(etHoras, 1, MAXIMO_HORAS, it) }
        view.findViewById<View>(R.id.btnMenosMinutos).setOnClickListener {
            // From 7 minutes the next step down is 5, then 0: the counter moves in steps of 5
            val actual = minutos()
            val destino = if (actual % 5 == 0) actual - 5 else actual - actual % 5
            cambiar(etMinutos, destino - actual, 59, it)
        }
        view.findViewById<View>(R.id.btnMasMinutos).setOnClickListener {
            val actual = minutos()
            if (actual >= 55) {
                // 55 + 5 minutes rolls over into one more hour
                if (horas() < MAXIMO_HORAS) {
                    it.hapticSeleccion()
                    etHoras.setText((horas() + 1).toString())
                    etMinutos.setText((actual - 55).toString())
                }
            } else {
                cambiar(etMinutos, 5 - actual % 5, 59, it)
            }
        }

        view.findViewById<View>(R.id.btnSalarioMinimo).setOnClickListener {
            it.hapticSeleccion()
            etPagoHora.setText(SALARIO_MINIMO_HORA.comoTextoEditable())
            tilPagoHora.error = null
        }

        actualizarResumen()

        view.findViewById<View>(R.id.btnContinuarPaso).setOnClickListener {
            var todoCorrecto = true

            val pagoPorHora = etPagoHora.text.toString().trim().toDoubleOrNull() ?: -1.0
            val horasTotales = horas() + minutos() / 60.0

            if (pagoPorHora < SALARIO_MINIMO_HORA) {
                tilPagoHora.error = "Debe ser al menos el salario mínimo: ${SALARIO_MINIMO_HORA.comoDinero()} por hora"
                todoCorrecto = false
            } else {
                tilPagoHora.error = null
            }

            if (horasTotales <= 0.0) {
                Snackbar.make(view, "Pon al menos 1 minuto de trabajo", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.barraAccionPaso)
                    .show()
                todoCorrecto = false
            }

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.calculo.costoManoObra = pagoPorHora * horasTotales
                viewModel.calculo.pagoPorHora = pagoPorHora
                viewModel.calculo.horasTrabajadas = horasTotales
                viewModel.guardarBorrador()

                (requireActivity() as MainActivity).navegarDeslizando(EmpaqueFragment())
            } else {
                view.hapticError()
            }
        }
    }

    private companion object {
        /** Mexican minimum wage per hour; also the lowest rate the screen accepts. */
        const val SALARIO_MINIMO_HORA = 39.38

        const val MAXIMO_HORAS = 999
    }
}
