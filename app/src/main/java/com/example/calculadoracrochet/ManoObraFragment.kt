package com.example.calculadoracrochet

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

class ManoObraFragment : Fragment(R.layout.fragment_mano_obra) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tilPagoHora = view.findViewById<TextInputLayout>(R.id.tilPagoHora)
        val etPagoHora = view.findViewById<EditText>(R.id.etPagoHora)
        val timePickerHoras = view.findViewById<TimePicker>(R.id.timePickerHoras)
        timePickerHoras.setIs24HourView(true)

        if (viewModel.pagoPorHoraTemp > 0.0) {
            etPagoHora.setText(viewModel.pagoPorHoraTemp.toString())
        }

        if (viewModel.horasTrabajadasTemp > 0.0) {
            val horas = viewModel.horasTrabajadasTemp.toInt()
            val minutos = ((viewModel.horasTrabajadasTemp - horas) * 60).toInt()
            timePickerHoras.hour = horas
            timePickerHoras.minute = minutos
        }

        view.findViewById<View>(R.id.btnSiguienteManoObra).setOnClickListener {
            var todoCorrecto = true

            val strPago = etPagoHora.text.toString().trim()
            val pagoPorHora = strPago.toDoubleOrNull() ?: -1.0

            val horas = timePickerHoras.hour
            val minutos = timePickerHoras.minute
            val horasTotales = horas + (minutos / 60.0)

            if (pagoPorHora < 39.38) {
                tilPagoHora?.error = "El salario mínimo es $39.38"
                todoCorrecto = false
            } else {
                tilPagoHora?.error = null
            }

            if (horasTotales <= 0.0) {
                Snackbar.make(view, "Debes seleccionar al menos 1 minuto de trabajo en el reloj", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(resources.getColor(R.color.purple_primary, null))
                    .show()
                todoCorrecto = false
            }

            if (todoCorrecto) {
                viewModel.pagoPorHoraTemp = pagoPorHora
                viewModel.horasTrabajadasTemp = horasTotales
                viewModel.costoManoObra = pagoPorHora * horasTotales

                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right,
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, EmpaqueFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
}