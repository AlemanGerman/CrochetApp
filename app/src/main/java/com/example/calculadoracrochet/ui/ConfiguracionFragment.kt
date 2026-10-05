package com.example.calculadoracrochet.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class ConfiguracionFragment : Fragment(R.layout.fragment_configuracion) {

    private val viewModel: MinaViewModel by activityViewModels()

    private var switchRedondeo: SwitchMaterial? = null
    private var tvEstadoRedondeo: TextView? = null
    private var llContenedorMultiplo: LinearLayout? = null
    private var cgMultiplos: ChipGroup? = null
    private var chipMultiplo1: Chip? = null
    private var chipMultiplo5: Chip? = null
    private var chipMultiplo10: Chip? = null
    private var chipMultiplo20: Chip? = null
    private var chipMultiplo50: Chip? = null
    private var etMultiploPersonalizado: TextInputEditText? = null
    private var btnAplicarMultiplo: MaterialButton? = null
    private var tvSimulacionBase: TextView? = null
    private var tvSimulacionResultado: TextView? = null

    private var switchVibracion: SwitchMaterial? = null
    private var tvEstadoVibracion: TextView? = null
    private var btnProbarVibracion: MaterialButton? = null

    private var actualizandoChipsProgramaticamente = false

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
        cgMultiplos = view.findViewById(R.id.cgMultiplos)
        chipMultiplo1 = view.findViewById(R.id.chipMultiplo1)
        chipMultiplo5 = view.findViewById(R.id.chipMultiplo5)
        chipMultiplo10 = view.findViewById(R.id.chipMultiplo10)
        chipMultiplo20 = view.findViewById(R.id.chipMultiplo20)
        chipMultiplo50 = view.findViewById(R.id.chipMultiplo50)
        etMultiploPersonalizado = view.findViewById(R.id.etMultiploPersonalizado)
        btnAplicarMultiplo = view.findViewById(R.id.btnAplicarMultiplo)
        tvSimulacionBase = view.findViewById(R.id.tvSimulacionBase)
        tvSimulacionResultado = view.findViewById(R.id.tvSimulacionResultado)

        switchVibracion = view.findViewById(R.id.switchVibracion)
        tvEstadoVibracion = view.findViewById(R.id.tvEstadoVibracion)
        btnProbarVibracion = view.findViewById(R.id.btnProbarVibracion)
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

        actualizarCampoMultiplo(multiploActual)
        sincronizarChipConMultiplo(multiploActual)

        switchRedondeo?.setOnCheckedChangeListener { _, isChecked ->
            switchRedondeo?.hapticSeleccion()
            PreferenciasApp.setRedondeoActivo(requireContext(), isChecked)
            actualizarEstadoTextoRedondeo(isChecked)
            llContenedorMultiplo?.visibility = if (isChecked) View.VISIBLE else View.GONE
            viewModel.calcularTotal()
            actualizarSimulacion()
        }

        chipMultiplo1?.setOnClickListener { seleccionarMultiploPreset(1.0) }
        chipMultiplo5?.setOnClickListener { seleccionarMultiploPreset(5.0) }
        chipMultiplo10?.setOnClickListener { seleccionarMultiploPreset(10.0) }
        chipMultiplo20?.setOnClickListener { seleccionarMultiploPreset(20.0) }
        chipMultiplo50?.setOnClickListener { seleccionarMultiploPreset(50.0) }

        btnAplicarMultiplo?.setOnClickListener {
            aplicarMultiploPersonalizado()
        }
    }

    private fun seleccionarMultiploPreset(valor: Double) {
        chipMultiplo1?.hapticSeleccion()
        PreferenciasApp.setMultiploRedondeo(requireContext(), valor)
        actualizarCampoMultiplo(valor)
        sincronizarChipConMultiplo(valor)
        viewModel.calcularTotal()
        actualizarSimulacion()
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
        sincronizarChipConMultiplo(nuevoMultiplo)
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

    private fun sincronizarChipConMultiplo(multiplo: Double) {
        actualizandoChipsProgramaticamente = true
        cgMultiplos?.clearCheck()
        when {
            multiplo == 1.0 -> chipMultiplo1?.isChecked = true
            multiplo == 5.0 -> chipMultiplo5?.isChecked = true
            multiplo == 10.0 -> chipMultiplo10?.isChecked = true
            multiplo == 20.0 -> chipMultiplo20?.isChecked = true
            multiplo == 50.0 -> chipMultiplo50?.isChecked = true
        }
        actualizandoChipsProgramaticamente = false
    }

    private fun actualizarEstadoTextoRedondeo(activo: Boolean) {
        if (activo) {
            tvEstadoRedondeo?.text = "Activo (redondeo hacia arriba)"
            tvEstadoRedondeo?.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
        } else {
            tvEstadoRedondeo?.text = "Desactivado (precio exacto al centavo)"
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

        btnProbarVibracion?.setOnClickListener {
            if (PreferenciasApp.isVibracionActiva(requireContext())) {
                it.hapticExito()
                Toast.makeText(requireContext(), "¡Vibración funcionando perfectamente! 🧶", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Las vibraciones están desactivadas. Actívalas arriba para sentirlas.",
                    Toast.LENGTH_SHORT
                ).show()
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

    private fun actualizarSimulacion() {
        val context = context ?: return
        val activo = PreferenciasApp.isRedondeoActivo(context)
        val multiplo = PreferenciasApp.getMultiploRedondeo(context)

        val localeMx = Locale.forLanguageTag("es-MX")
        val totalBaseSimulado = 143.20
        tvSimulacionBase?.text = String.format(localeMx, "Ejemplo de costo calculado: $%,.2f", totalBaseSimulado)

        if (activo) {
            val (precioRedondeado, ajuste) = PreferenciasApp.calcularRedondeo(totalBaseSimulado, activo, multiplo)
            val textoMultiplo = PreferenciasApp.formatearMultiplo(multiplo)
            tvSimulacionResultado?.text = String.format(
                localeMx,
                "Con redondeo (a %s): $%,.2f (+ $%,.2f a tu favor)",
                textoMultiplo,
                precioRedondeado,
                ajuste
            )
        } else {
            tvSimulacionResultado?.text = String.format(
                localeMx,
                "Sin redondeo: $%,.2f (precio exacto sin ajuste)",
                totalBaseSimulado
            )
        }
    }
}
