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

        val tgComproPatron = view.findViewById<MaterialButtonToggleGroup>(R.id.tgComproPatron)
        val llCamposPatron = view.findViewById<LinearLayout>(R.id.llCamposPatronComprado)

        val tilCostoPatron = view.findViewById<TextInputLayout>(R.id.tilCostoPatron)
        val tilNumeroMunecos = view.findViewById<TextInputLayout>(R.id.tilNumeroMunecos)

        val etCostoPatron = view.findViewById<EditText>(R.id.etCostoPatron)
        val etNumeroMunecos = view.findViewById<EditText>(R.id.etNumeroMunecos)

        tgComproPatron.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnPatronSi) {
                    llCamposPatron.visibility = View.VISIBLE
                } else {
                    llCamposPatron.visibility = View.GONE
                    etCostoPatron.text?.clear()
                    etNumeroMunecos.text?.clear()
                    tilCostoPatron.error = null
                    tilNumeroMunecos.error = null
                }
            }
        }

        view.findViewById<View>(R.id.btnSiguientePatron).setOnClickListener {
            val esComprado = tgComproPatron.checkedButtonId == R.id.btnPatronSi
            var todoCorrecto = true

            if (esComprado) {
                val strCosto = etCostoPatron.text.toString().trim()
                val strUnidades = etNumeroMunecos.text.toString().trim()

                val costo = strCosto.toDoubleOrNull() ?: -1.0
                val unidades = strUnidades.toIntOrNull() ?: 0

                if (costo <= 0.0) {
                    tilCostoPatron.error = "El costo debe ser mayor a $0"
                    todoCorrecto = false
                } else {
                    tilCostoPatron.error = null
                }

                if (unidades <= 0) {
                    tilNumeroMunecos.error = "Debe ser un número entero mayor a 0"
                    todoCorrecto = false
                } else {
                    tilNumeroMunecos.error = null
                }

                if (todoCorrecto) {
                    viewModel.costoDiseno = costo / unidades.toDouble()
                }

            } else {
                viewModel.costoDiseno = 0.0
            }

            if (todoCorrecto) {
                parentFragmentManager.beginTransaction()
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