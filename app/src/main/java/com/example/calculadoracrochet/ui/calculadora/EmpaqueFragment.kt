package com.example.calculadoracrochet.ui.calculadora

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.CalculadoraPrecio
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.PantallaConGanchillo
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.comoTextoEditable
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.validar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout

class EmpaqueFragment : Fragment(R.layout.fragment_empaque), PantallaConGanchillo {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EncabezadoPaso.configurar(
            raiz = view,
            paso = 4,
            titulo = "Empaque y ganancia",
            subtitulo = "Ahora sí, lo bueno jejeje",
            patron = R.drawable.encabezado_empaque,
            patronSiguiente = R.drawable.encabezado_resultado,
            alVolver = { requireActivity().onBackPressedDispatcher.onBackPressed() }
        )
        view.findViewById<MaterialButton>(R.id.btnContinuarPaso).text = "Calcular precio"

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (requireActivity() as MainActivity).regresarDeslizando()
            }
        })

        val tilCostoCaja = view.findViewById<TextInputLayout>(R.id.tilCostoCaja)
        val etCostoCaja = view.findViewById<EditText>(R.id.etCostoCaja)

        val tvMargenLabel = view.findViewById<TextView>(R.id.tvMargenLabel)
        val sliderMargen = view.findViewById<Slider>(R.id.sliderMargen)

        sliderMargen.setLabelFormatter { value -> "${value.toInt()}%" }

        if (viewModel.calculo.costoEmpaque > 0.0) {
            etCostoCaja.setText(viewModel.calculo.costoEmpaque.comoTextoEditable())
        }

        if (viewModel.calculo.margenGanancia >= 0.0) {
            sliderMargen.value = viewModel.calculo.margenGanancia.toFloat().coerceIn(0f, 100f)
        }

        // The suggested price as it would come out with what is on screen, rounding included
        fun actualizarPrecio() {
            val margen = sliderMargen.value.toInt()
            tvMargenLabel.text = "$margen%"
            val calculo = viewModel.calculo
            val empaque = etCostoCaja.text.toString().trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
            val desglose = CalculadoraPrecio.desglosar(
                calculo.costoMateriales, calculo.costoDiseno, calculo.costoManoObra, empaque, margen.toDouble()
            )
            val contexto = requireContext()
            val (precio, _) = PreferenciasApp.calcularRedondeo(
                desglose.totalExacto,
                PreferenciasApp.isRedondeoActivo(contexto),
                PreferenciasApp.getMultiploRedondeo(contexto)
            )
            EncabezadoPaso.mostrarMonto(view, "Precio sugerido", precio.comoDinero())
        }

        var ultimoMargen = sliderMargen.value.toInt()
        sliderMargen.addOnChangeListener { slider, value, fromUser ->
            val margenEntero = value.toInt()
            if (fromUser && margenEntero != ultimoMargen) {
                ultimoMargen = margenEntero
                slider.hapticSeleccion()
            }
            actualizarPrecio()
        }
        etCostoCaja.doAfterTextChanged {
            tilCostoCaja.error = null
            actualizarPrecio()
        }
        actualizarPrecio()

        view.findViewById<View>(R.id.btnContinuarPaso).setOnClickListener {
            val costoCaja = etCostoCaja.text.toString().toDoubleOrNull() ?: 0.0
            val margen = sliderMargen.value.toDouble()

            val todoCorrecto = tilCostoCaja.validar(
                if (costoCaja < 0.0) "El costo no puede ser negativo" else null
            )

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.calculo.costoEmpaque = costoCaja
                viewModel.calculo.margenGanancia = margen
                viewModel.guardarBorrador()

                (requireActivity() as MainActivity).navegarConGanchillo(ResultadoFragment())
            } else {
                view.hapticError()
            }
        }
    }
}
