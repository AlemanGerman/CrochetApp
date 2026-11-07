package com.example.calculadoracrochet.ui.calculadora

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import com.example.calculadoracrochet.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

import com.example.calculadoracrochet.utils.comoTextoEditable
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.horasAMinutos

class ManoObraFragment : Fragment(R.layout.fragment_mano_obra) {
    private val viewModel: CalculadoraViewModel by activityViewModels()
    private var ultimoRetrocesoTiempo: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tiempoActual = SystemClock.elapsedRealtime()

                if (tiempoActual - ultimoRetrocesoTiempo < 400L) {
                    return
                }
                ultimoRetrocesoTiempo = tiempoActual

                val lottie = requireActivity().findViewById<View>(R.id.lottieGanchillo)
                lottie?.visibility = View.GONE

                parentFragmentManager.popBackStack()
            }
        })

        val cardPagoHora = view.findViewById<MaterialCardView>(R.id.cardPagoHora)
        val tvErrorPago = view.findViewById<TextView>(R.id.tvErrorPago)
        val etPagoHora = view.findViewById<EditText>(R.id.etPagoHora)
        val timePickerHoras = view.findViewById<TimePicker>(R.id.timePickerHoras)
        timePickerHoras.setIs24HourView(true)
        timePickerHoras.setOnTimeChangedListener { _, _, _ ->
            timePickerHoras.hapticSeleccion()
        }
        val colorMorado = ContextCompat.getColor(requireContext(), R.color.purple_primary)

        if (viewModel.calculo.pagoPorHora > 0.0) {
            etPagoHora.setText(viewModel.calculo.pagoPorHora.comoTextoEditable())
        }

        if (viewModel.calculo.horasTrabajadas > 0.0) {
            val totalMinutos = horasAMinutos(viewModel.calculo.horasTrabajadas)
            timePickerHoras.hour = (totalMinutos / 60).coerceAtMost(23)
            timePickerHoras.minute = totalMinutos % 60
        }

        view.findViewById<MaterialButton>(R.id.btnSalarioMinimo).setOnClickListener {
            it.hapticSeleccion()
            etPagoHora.setText(SALARIO_MINIMO_HORA.comoTextoEditable())
            cardPagoHora.strokeColor = colorMorado
            tvErrorPago.visibility = View.GONE
        }

        view.findViewById<View>(R.id.btnSiguienteManoObra).setOnClickListener {
            var todoCorrecto = true

            val strPago = etPagoHora.text.toString().trim()
            val pagoPorHora = strPago.toDoubleOrNull() ?: -1.0

            val horas = timePickerHoras.hour
            val minutos = timePickerHoras.minute
            val horasTotales = horas + (minutos / 60.0)

            if (pagoPorHora < SALARIO_MINIMO_HORA) {
                cardPagoHora.strokeColor = Color.parseColor("#E53935")
                tvErrorPago.visibility = View.VISIBLE
                todoCorrecto = false
            } else {
                cardPagoHora.strokeColor = colorMorado
                tvErrorPago.visibility = View.GONE
            }

            if (horasTotales <= 0.0) {
                Snackbar.make(view, "Debes seleccionar al menos 1 minuto de trabajo", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(colorMorado)
                    .show()
                todoCorrecto = false
            }

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.calculo.costoManoObra = pagoPorHora * horasTotales
                viewModel.calculo.pagoPorHora = pagoPorHora
                viewModel.calculo.horasTrabajadas = horasTotales
                viewModel.guardarBorrador()

                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, EmpaqueFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                view.hapticError()
            }
        }
    }

    private companion object {
        /** Mexican minimum wage per hour; also the lowest rate the screen accepts. */
        const val SALARIO_MINIMO_HORA = 39.38
    }
}