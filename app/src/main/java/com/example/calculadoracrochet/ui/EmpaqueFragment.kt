package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.viewmodel.CalculadoraViewModel
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout

class EmpaqueFragment : Fragment(R.layout.fragment_empaque) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

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

        sliderMargen.addOnChangeListener { _, value, _ ->
            tvMargenLabel.text = "Margen de ganancia extra: ${value.toInt()}%"
        }

        view.findViewById<View>(R.id.btnSiguienteEmpaque).setOnClickListener {
            val costoCaja = etCostoCaja.text.toString().toDoubleOrNull() ?: 0.0
            val margen = sliderMargen.value.toDouble()

            var todoCorrecto = true

            if (costoCaja < 0.0) {
                tilCostoCaja.error = "El costo no puede ser negativo"
                todoCorrecto = false
            } else {
                tilCostoCaja.error = null
            }

            if (todoCorrecto) {
                viewModel.costoEmpaque = costoCaja
                viewModel.margenGanancia = margen

                (requireActivity() as MainActivity).navegarConGanchillo(ResultadoFragment())
            }
        }
    }
}