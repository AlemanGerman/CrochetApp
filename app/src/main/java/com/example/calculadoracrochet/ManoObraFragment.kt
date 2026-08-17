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

        // 1. Vinculamos el reloj interactivo que está integrado en la pantalla[cite: 1]
        val timePickerHoras = view.findViewById<TimePicker>(R.id.timePickerHoras)
        timePickerHoras.setIs24HourView(true) // Formato de 24 horas

        view.findViewById<View>(R.id.btnSiguienteManoObra).setOnClickListener {
            var todoCorrecto = true

            val strPago = etPagoHora.text.toString().trim()
            val pagoPorHora = strPago.toDoubleOrNull() ?: -1.0

            // 2. Extraer las horas y minutos directamente de las manecillas del reloj[cite: 1]
            val horas = timePickerHoras.hour
            val minutos = timePickerHoras.minute

            // Convertir todo a horas decimales (Ej. 2 hrs 30 min = 2.5 horas)[cite: 1]
            val horasTotales = horas + (minutos / 60.0)

            // 3. Validación estricta del salario mínimo (Debe ser >= 39.38)[cite: 3]
            if (pagoPorHora < 39.38) {
                tilPagoHora?.error = "El salario mínimo es $39.38"
                todoCorrecto = false
            } else {
                tilPagoHora?.error = null
            }

            // 4. Validación para evitar proyectos de 0 horas[cite: 3]
            if (horasTotales <= 0.0) {
                Snackbar.make(view, "Debes seleccionar al menos 1 minuto de trabajo en el reloj", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(resources.getColor(R.color.purple_primary, null))
                    .show()
                todoCorrecto = false
            }

            // Guardar el cálculo y avanzar ÚNICAMENTE si no hay errores
            if (todoCorrecto) {
                viewModel.costoManoObra = pagoPorHora * horasTotales

                // Animación suave de transición y navegación al Empaque[cite: 6]
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