package com.example.calculadoracrochet.ui.calculadora

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.validar
import com.example.calculadoracrochet.utils.errorSiNoPositivo
import com.example.calculadoracrochet.utils.aNumeroOInvalido

class PatronFragment : Fragment(R.layout.fragment_patron) {

    private val viewModel: MinaViewModel by activityViewModels()

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

        val tgComproPatron = view.findViewById<MaterialButtonToggleGroup>(R.id.tgComproPatron)
        val llCamposPatron = view.findViewById<LinearLayout>(R.id.llCamposPatronComprado)

        val tilCostoPatron = view.findViewById<TextInputLayout>(R.id.tilCostoPatron)
        val tilNumeroMunecos = view.findViewById<TextInputLayout>(R.id.tilNumeroMunecos)

        val etCostoPatron = view.findViewById<EditText>(R.id.etCostoPatron)
        val etNumeroMunecos = view.findViewById<EditText>(R.id.etNumeroMunecos)

        tgComproPatron.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                tgComproPatron.hapticSeleccion()
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

                val costo = strCosto.aNumeroOInvalido()
                val unidades = strUnidades.toIntOrNull() ?: 0

                todoCorrecto = tilCostoPatron.validar(errorSiNoPositivo(costo, "El costo debe ser mayor a $0")) && todoCorrecto
                todoCorrecto = tilNumeroMunecos.validar(
                    errorSiNoPositivo(unidades.toDouble(), "Debe ser un número entero mayor a 0")
                ) && todoCorrecto

                if (todoCorrecto) {
                    viewModel.costoDiseno = costo / unidades.toDouble()
                }

            } else {
                viewModel.costoDiseno = 0.0
            }

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.guardarBorrador()
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, ManoObraFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                view.hapticError()
            }
        }
    }
}