package com.example.calculadoracrochet

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.textfield.TextInputLayout

class EmpaqueFragment : Fragment(R.layout.fragment_empaque) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enlazamos los contenedores de texto para poder mostrar errores
        val tilCostoCaja = view.findViewById<TextInputLayout>(R.id.tilCostoCaja)
        val tilMargen = view.findViewById<TextInputLayout>(R.id.tilMargen)

        val etCostoCaja = view.findViewById<EditText>(R.id.etCostoCaja)
        val etMargen = view.findViewById<EditText>(R.id.etMargen)

        view.findViewById<View>(R.id.btnSiguienteEmpaque).setOnClickListener {
            val costoCaja = etCostoCaja.text.toString().toDoubleOrNull() ?: 0.0
            val margen = etMargen.text.toString().toDoubleOrNull() ?: 0.0

            var todoCorrecto = true

            // Validamos que el costo no sea negativo
            if (costoCaja < 0.0) {
                tilCostoCaja.error = "El costo no puede ser negativo"
                todoCorrecto = false
            } else {
                tilCostoCaja.error = null
            }

            // Validamos que el margen no sea negativo
            if (margen < 0.0) {
                tilMargen.error = "El margen no puede ser negativo"
                todoCorrecto = false
            } else {
                tilMargen.error = null
            }

            // Si pasa todas las validaciones lógicas, avanzamos
            if (todoCorrecto) {
                viewModel.costoEmpaque = costoCaja
                viewModel.margenGanancia = margen

                // Navegamos a Resultados
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ResultadoFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
}