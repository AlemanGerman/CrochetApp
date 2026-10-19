package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout

import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.validar

class EmpaqueFragment : Fragment(R.layout.fragment_empaque) {
    private val viewModel: MinaViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lottieGanchillo = requireActivity().findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieGanchillo)
        if (lottieGanchillo != null && !lottieGanchillo.isAnimating) {
            lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
            lottieGanchillo.progress = 0f
            lottieGanchillo.visibility = View.VISIBLE
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().findViewById<View>(R.id.lottieGanchillo)?.visibility = View.GONE
                parentFragmentManager.popBackStack()
            }
        })

        val tilCostoCaja = view.findViewById<TextInputLayout>(R.id.tilCostoCaja)
        val etCostoCaja = view.findViewById<EditText>(R.id.etCostoCaja)

        val tvMargenLabel = view.findViewById<TextView>(R.id.tvMargenLabel)
        val sliderMargen = view.findViewById<Slider>(R.id.sliderMargen)

        sliderMargen.setLabelFormatter { value ->
            "${value.toInt()}%"
        }

        if (viewModel.costoEmpaque > 0.0) {
            etCostoCaja.setText(viewModel.costoEmpaque.toString())
        }

        if (viewModel.margenGanancia >= 0.0) {
            val margenSeguro = viewModel.margenGanancia.toFloat().coerceIn(0f, 100f)
            sliderMargen.value = margenSeguro
            tvMargenLabel.text = "Margen de ganancia extra: ${margenSeguro.toInt()}%"
        }

        var ultimoMargen = sliderMargen.value.toInt()
        sliderMargen.addOnChangeListener { slider, value, fromUser ->
            val margenEntero = value.toInt()
            tvMargenLabel.text = "Margen de ganancia extra: $margenEntero%"
            if (fromUser && margenEntero != ultimoMargen) {
                ultimoMargen = margenEntero
                slider.hapticSeleccion()
            }
        }

        view.findViewById<View>(R.id.btnSiguienteEmpaque).setOnClickListener {
            val costoCaja = etCostoCaja.text.toString().toDoubleOrNull() ?: 0.0
            val margen = sliderMargen.value.toDouble()

            val todoCorrecto = tilCostoCaja.validar(
                if (costoCaja < 0.0) "El costo no puede ser negativo" else null
            )

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.costoEmpaque = costoCaja
                viewModel.margenGanancia = margen
                viewModel.guardarBorrador()

                (requireActivity() as MainActivity).navegarConGanchillo(ResultadoFragment())
            } else {
                view.hapticError()
            }
        }
    }
}