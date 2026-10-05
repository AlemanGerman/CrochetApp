package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticExito
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class ConfiguracionFragment : Fragment(R.layout.fragment_configuracion) {

    private val viewModel: MinaViewModel by activityViewModels()

    private var switchRedondeo: SwitchMaterial? = null
    private var tvEstadoRedondeo: TextView? = null
    private var llContenedorMultiplo: LinearLayout? = null
    private var etMultiploPersonalizado: TextInputEditText? = null
    private var btnAplicarMultiplo: MaterialButton? = null
    private var tvSimulacionBase: TextView? = null
    private var tvSimulacionResultado: TextView? = null

    private var switchVibracion: SwitchMaterial? = null
    private var tvEstadoVibracion: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inicializarVistas(view)
        configurarBotonVolver(view)
        configurarSeccionRedondeo()
        configurarSeccionVibracion()
        actualizarSimulacion()
    }

    private fun inicializarVistas(view: View) {
        switchRedondeo = view.findViewById(R.id.switchRedondeo)
        tvEstadoRedondeo = view.findViewById(R.id.tvEstadoRedondeo)
        llContenedorMultiplo = view.findViewById(R.id.llContenedorMultiplo)
        etMultiploPersonalizado = view.findViewById(R.id.etMultiploPersonalizado)
        btnAplicarMultiplo = view.findViewById(R.id.btnAplicarMultiplo)
        tvSimulacionBase = view.findViewById(R.id.tvSimulacionBase)
        tvSimulacionResultado = view.findViewById(R.id.tvSimulacionResultado)

        switchVibracion = view.findViewById(R.id.switchVibracion)
        tvEstadoVibracion = view.findViewById(R.id.tvEstadoVibracion)
    }

    private fun configurarBotonVolver(view: View) {
        view.findViewById<ImageButton>(R.id.btnVolverConfiguracion)?.setOnClickListener {
            it.hapticClickPrincipal()
            parentFragmentManager.popBackStack()
        }
    }

    private fun configurarSeccionRedondeo() {
        val context = requireContext()
        val redondeoActivo = PreferenciasApp.isRedondeoActivo(context)
        val multiploActual = PreferenciasApp.getMultiploRedondeo(context)

        switchRedondeo?.isChecked = redondeoActivo
        actualizarEstadoTextoRedondeo(redondeoActivo)
        llContenedorMultiplo?.visibility = if (redondeoActivo) View.VISIBLE else View.GONE

        // Muestra el número actual configurado (por defecto 5)
        actualizarCampoMultiplo(multiploActual)

        switchRedondeo?.setOnCheckedChangeListener { _, isChecked ->
            switchRedondeo?.hapticSeleccion()
            PreferenciasApp.setRedondeoActivo(requireContext(), isChecked)
            actualizarEstadoTextoRedondeo(isChecked)
            llContenedorMultiplo?.visibility = if (isChecked) View.VISIBLE else View.GONE
            viewModel.calcularTotal()
            actualizarSimulacion()
        }

        btnAplicarMultiplo?.setOnClickListener {
            aplicarMultiploPersonalizado()
        }

        etMultiploPersonalizado?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                aplicarMultiploPersonalizado()
                true
            } else {
                false
            }
        }

        etMultiploPersonalizado?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val valor = s?.toString()?.trim()?.toDoubleOrNull()
                if (valor != null && valor > 0.0) {
                    actualizarSimulacion(multiploSimulado = valor)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun aplicarMultiploPersonalizado() {
        val texto = etMultiploPersonalizado?.text?.toString()?.trim()
        val nuevoMultiplo = texto?.toDoubleOrNull()

        if (nuevoMultiplo == null || nuevoMultiplo <= 0.0) {
            btnAplicarMultiplo?.hapticError()
            etMultiploPersonalizado?.error = "Ingresa un número mayor a 0"
            return
        }

        btnAplicarMultiplo?.hapticClickPrincipal()
        etMultiploPersonalizado?.error = null
        PreferenciasApp.setMultiploRedondeo(requireContext(), nuevoMultiplo)
        actualizarCampoMultiplo(nuevoMultiplo)
        viewModel.calcularTotal()
        actualizarSimulacion()

        Toast.makeText(
            requireContext(),
            "Múltiplo guardado: ${PreferenciasApp.formatearMultiplo(nuevoMultiplo)}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun actualizarCampoMultiplo(multiplo: Double) {
        val textoFormateado = if (multiplo % 1.0 == 0.0) {
            multiplo.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", multiplo)
        }
        etMultiploPersonalizado?.setText(textoFormateado)
        etMultiploPersonalizado?.setSelection(etMultiploPersonalizado?.text?.length ?: 0)
    }

    private fun actualizarEstadoTextoRedondeo(activo: Boolean) {
        if (activo) {
            tvEstadoRedondeo?.text = "Activo"
            tvEstadoRedondeo?.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
        } else {
            tvEstadoRedondeo?.text = "Desactivado"
            tvEstadoRedondeo?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_hint))
        }
    }

    private fun configurarSeccionVibracion() {
        val context = requireContext()
        val vibracionActiva = PreferenciasApp.isVibracionActiva(context)

        switchVibracion?.isChecked = vibracionActiva
        actualizarEstadoTextoVibracion(vibracionActiva)

        switchVibracion?.setOnCheckedChangeListener { _, isChecked ->
            PreferenciasApp.setVibracionActiva(requireContext(), isChecked)
            actualizarEstadoTextoVibracion(isChecked)
            if (isChecked) {
                switchVibracion?.hapticExito()
            } else {
                Toast.makeText(requireContext(), "Vibraciones desactivadas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarEstadoTextoVibracion(activa: Boolean) {
        if (activa) {
            tvEstadoVibracion?.text = "Activada en toda la app"
            tvEstadoVibracion?.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
        } else {
            tvEstadoVibracion?.text = "Desactivada"
            tvEstadoVibracion?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_hint))
        }
    }

    private fun actualizarSimulacion(multiploSimulado: Double? = null) {
        val context = context ?: return
        val activo = PreferenciasApp.isRedondeoActivo(context)
        val multiplo = multiploSimulado ?: PreferenciasApp.getMultiploRedondeo(context)

        val localeMx = Locale.forLanguageTag("es-MX")
        val totalBaseSimulado = 143.20
        tvSimulacionBase?.text = String.format(localeMx, "Ejemplo sin redondeo: $%,.2f", totalBaseSimulado)

        if (activo) {
            val (precioRedondeado, ajuste) = PreferenciasApp.calcularRedondeo(totalBaseSimulado, activo, multiplo)
            val textoMultiplo = PreferenciasApp.formatearMultiplo(multiplo)
            tvSimulacionResultado?.text = String.format(
                localeMx,
                "Con redondeo (%s): $%,.2f (+ $%,.2f a tu favor)",
                textoMultiplo,
                precioRedondeado,
                ajuste
            )
        } else {
            tvSimulacionResultado?.text = String.format(
                localeMx,
                "Sin redondeo: $%,.2f (precio exacto)",
                totalBaseSimulado
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Si el usuario escribió un múltiplo válido pero no presionó "Aplicar", se persiste para no perderlo
        val texto = etMultiploPersonalizado?.text?.toString()?.trim()
        val nuevoMultiplo = texto?.toDoubleOrNull()
        if (nuevoMultiplo != null && nuevoMultiplo > 0.0) {
            context?.let { ctx ->
                PreferenciasApp.setMultiploRedondeo(ctx, nuevoMultiplo)
                viewModel.calcularTotal()
            }
        }
    }
}
