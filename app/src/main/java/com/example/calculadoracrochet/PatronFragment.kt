package com.example.calculadoracrochet

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

class PatronFragment : Fragment(R.layout.fragment_patron) {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enlazamos el nuevo componente ToggleGroup
        val tgComproPatron = view.findViewById<MaterialButtonToggleGroup>(R.id.tgComproPatron)
        val llCamposPatron = view.findViewById<LinearLayout>(R.id.llCamposPatronComprado)

        val tilCostoPatron = view.findViewById<TextInputLayout>(R.id.tilCostoPatron)
        val tilNumeroMunecos = view.findViewById<TextInputLayout>(R.id.tilNumeroMunecos)

        val etCostoPatron = view.findViewById<EditText>(R.id.etCostoPatron)
        val etNumeroMunecos = view.findViewById<EditText>(R.id.etNumeroMunecos)

        // Escuchar el cambio en las opciones con el nuevo componente
        tgComproPatron.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnPatronSi) {
                    llCamposPatron.visibility = View.VISIBLE
                } else {
                    llCamposPatron.visibility = View.GONE
                    etCostoPatron.text?.clear()
                    etNumeroMunecos.text?.clear()
                    // Limpiamos los errores por si había alguno antes de cambiar de opción
                    tilCostoPatron.error = null
                    tilNumeroMunecos.error = null
                }
            }
        }

        // Navegación y cálculo
        view.findViewById<View>(R.id.btnSiguientePatron).setOnClickListener {
            // Verificamos si el botón seleccionado es el de "Comprado"
            val esComprado = tgComproPatron.checkedButtonId == R.id.btnPatronSi
            var todoCorrecto = true

            if (esComprado) {
                val strCosto = etCostoPatron.text.toString().trim()
                val strUnidades = etNumeroMunecos.text.toString().trim()

                // Conversión de datos[cite: 3].
                val costo = strCosto.toDoubleOrNull() ?: -1.0
                val unidades = strUnidades.toIntOrNull() ?: 0

                // Validación del Costo del Patrón (> 0 estricto)[cite: 3]
                if (costo <= 0.0) {
                    tilCostoPatron.error = "El costo debe ser mayor a $0"
                    todoCorrecto = false
                } else {
                    tilCostoPatron.error = null
                }

                // Validación del Número de Muñecos (Enteros positivos > 0)[cite: 3]
                if (unidades <= 0) {
                    tilNumeroMunecos.error = "Debe ser un número entero mayor a 0"
                    todoCorrecto = false
                } else {
                    tilNumeroMunecos.error = null
                }

                // Si todo está correcto, calculamos la amortización[cite: 3]
                if (todoCorrecto) {
                    viewModel.costoDiseno = costo / unidades.toDouble()
                }

            } else {
                // Si es propio o gratis, el costo financiero del PDF es $0[cite: 3]
                viewModel.costoDiseno = 0.0
            }

            // Pasar a Mano de Obra SOLAMENTE si todas las validaciones fueron correctas[cite: 3]
            if (todoCorrecto) {
                parentFragmentManager.beginTransaction()
                    // Le ponemos las mismas animaciones que en Materiales para mantener la fluidez
                    .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right,
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, ManoObraFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
}