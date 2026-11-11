package com.example.calculadoracrochet.ui.configuracion

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.PestanaPrincipal
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado
import com.example.calculadoracrochet.utils.rellenarSobreBarraDeNavegacion
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticExito
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.ui.calculadora.CalculadoraViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.animarCambiosDeLayout

class ConfiguracionFragment : Fragment(R.layout.fragment_configuracion), PestanaPrincipal {

    override val idPestana = R.id.nav_ajustes

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private val proyectosViewModel: ProyectosViewModel by activityViewModels()

    private var switchRedondeo: MaterialSwitch? = null
    private var tvEstadoRedondeo: TextView? = null
    private var llContenedorMultiplo: LinearLayout? = null
    private var etMultiploPersonalizado: TextInputEditText? = null
    private var tilMultiplo: com.google.android.material.textfield.TextInputLayout? = null
    private var btnAplicarMultiplo: MaterialButton? = null
    private var tvSimulacionBase: TextView? = null
    private var tvSimulacionResultado: TextView? = null
    private var tvSimulacionAjuste: TextView? = null
    private var cgMultiplos: ChipGroup? = null
    private var llMultiploOtro: View? = null

    private var switchVibracion: MaterialSwitch? = null
    private var tvEstadoVibracion: TextView? = null

    private var switchColorDinamico: MaterialSwitch? = null
    private var tvEstadoColorDinamico: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inicializarVistas(view)
        configurarBotonVolver(view)
        configurarSeccionRedondeo()
        configurarSeccionVibracion()
        configurarSeccionColorDinamico()
        actualizarSimulacion()
    }

    private fun inicializarVistas(view: View) {
        switchRedondeo = view.findViewById(R.id.switchRedondeo)
        tvEstadoRedondeo = view.findViewById(R.id.tvEstadoRedondeo)
        llContenedorMultiplo = view.findViewById(R.id.llContenedorMultiplo)
        etMultiploPersonalizado = view.findViewById(R.id.etMultiploPersonalizado)
        tilMultiplo = view.findViewById(R.id.tilMultiploPersonalizado)
        btnAplicarMultiplo = view.findViewById(R.id.btnAplicarMultiplo)
        tvSimulacionBase = view.findViewById(R.id.tvSimulacionBase)
        tvSimulacionResultado = view.findViewById(R.id.tvSimulacionResultado)
        tvSimulacionAjuste = view.findViewById(R.id.tvSimulacionAjuste)
        cgMultiplos = view.findViewById(R.id.cgMultiplos)
        llMultiploOtro = view.findViewById(R.id.llMultiploOtro)

        switchVibracion = view.findViewById(R.id.switchVibracion)
        tvEstadoVibracion = view.findViewById(R.id.tvEstadoVibracion)

        switchColorDinamico = view.findViewById(R.id.switchColorDinamico)
        tvEstadoColorDinamico = view.findViewById(R.id.tvEstadoColorDinamico)
    }

    private fun configurarBotonVolver(view: View) {
        view.findViewById<View>(R.id.contenidoEncabezado).rellenarBajoBarraDeEstado()
        view.findViewById<View>(R.id.hojaAjustes).apply {
            rellenarSobreBarraDeNavegacion()
            clipToOutline = true
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (requireActivity() as MainActivity).volverAProyectos()
            }
        })

        view.findViewById<View>(R.id.itemAbrirPapelera).setOnClickListener {
            it.hapticClickPrincipal()
            (requireActivity() as MainActivity).abrirPapelera()
        }
        val tvConteoPapelera = view.findViewById<TextView>(R.id.tvConteoPapelera)
        proyectosViewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            val enPapelera = proyectosViewModel.papeleraProyectos.size
            tvConteoPapelera.text = when (enPapelera) {
                0 -> "Está vacía"
                1 -> "1 proyecto eliminado"
                else -> "$enPapelera proyectos eliminados"
            }
        }
    }

    override fun alVolverAPulsar() {
        view?.findViewById<android.widget.ScrollView>(R.id.hojaAjustes)?.smoothScrollTo(0, 0)
    }

    private fun configurarSeccionRedondeo() {
        val context = requireContext()
        val redondeoActivo = PreferenciasApp.isRedondeoActivo(context)
        val multiploActual = PreferenciasApp.getMultiploRedondeo(context)

        switchRedondeo?.isChecked = redondeoActivo
        llContenedorMultiplo?.visibility = if (redondeoActivo) View.VISIBLE else View.GONE
        marcarMultiplo(multiploActual)
        actualizarSimulacion()

        switchRedondeo?.setOnCheckedChangeListener { _, isChecked ->
            switchRedondeo?.hapticSeleccion()
            PreferenciasApp.setRedondeoActivo(requireContext(), isChecked)
            (llContenedorMultiplo?.parent as? android.view.ViewGroup)?.animarCambiosDeLayout()
            llContenedorMultiplo?.visibility = if (isChecked) View.VISIBLE else View.GONE
            viewModel.calcularTotal()
            actualizarSimulacion()
        }

        // The usual amounts apply as soon as they are tapped; «Otro» opens the field for any amount
        cgMultiplos?.setOnCheckedStateChangeListener { grupo, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            grupo.hapticSeleccion()
            val esOtro = id == R.id.chipMultiploOtro
            (llContenedorMultiplo as? android.view.ViewGroup)?.animarCambiosDeLayout()
            llMultiploOtro?.visibility = if (esOtro) View.VISIBLE else View.GONE
            if (esOtro) {
                etMultiploPersonalizado?.requestFocus()
                return@setOnCheckedStateChangeListener
            }
            tilMultiplo?.error = null
            guardarMultiplo(MULTIPLOS_RAPIDOS.getValue(id))
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

    /** Checks the chip of [multiplo], or «Otro» with the amount in the field. */
    private fun marcarMultiplo(multiplo: Double) {
        val rapido = MULTIPLOS_RAPIDOS.entries.firstOrNull { it.value == multiplo }?.key
        cgMultiplos?.check(rapido ?: R.id.chipMultiploOtro)
        llMultiploOtro?.visibility = if (rapido == null) View.VISIBLE else View.GONE
        if (rapido == null) actualizarCampoMultiplo(multiplo)
    }

    private fun aplicarMultiploPersonalizado() {
        val texto = etMultiploPersonalizado?.text?.toString()?.trim()
        val nuevoMultiplo = texto?.toDoubleOrNull()

        if (nuevoMultiplo == null || nuevoMultiplo <= 0.0) {
            btnAplicarMultiplo?.hapticError()
            tilMultiplo?.error = "Escribe una cantidad mayor a 0"
            return
        }

        btnAplicarMultiplo?.hapticClickPrincipal()
        tilMultiplo?.error = null
        guardarMultiplo(nuevoMultiplo)
        // An amount that has its own chip moves the selection there
        if (MULTIPLOS_RAPIDOS.containsValue(nuevoMultiplo)) marcarMultiplo(nuevoMultiplo)
    }

    private fun guardarMultiplo(multiplo: Double) {
        PreferenciasApp.setMultiploRedondeo(requireContext(), multiplo)
        viewModel.calcularTotal()
        actualizarSimulacion()
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
            tvEstadoVibracion?.text = "Activada"
            tvEstadoVibracion?.setTextColor(ContextCompat.getColor(requireContext(), R.color.morado_fuerte))
        } else {
            tvEstadoVibracion?.text = "Desactivada"
            tvEstadoVibracion?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tinta_suave))
        }
    }

    private fun configurarSeccionColorDinamico() {
        val context = requireContext()
        val activo = PreferenciasApp.isColorDinamicoTarjetasActivo(context)

        switchColorDinamico?.isChecked = activo
        actualizarEstadoTextoColorDinamico(activo)

        switchColorDinamico?.setOnCheckedChangeListener { _, isChecked ->
            switchColorDinamico?.hapticSeleccion()
            PreferenciasApp.setColorDinamicoTarjetasActivo(requireContext(), isChecked)
            actualizarEstadoTextoColorDinamico(isChecked)
        }
    }

    private fun actualizarEstadoTextoColorDinamico(activo: Boolean) {
        if (activo) {
            tvEstadoColorDinamico?.text = "Activo"
            tvEstadoColorDinamico?.setTextColor(ContextCompat.getColor(requireContext(), R.color.morado_fuerte))
        } else {
            tvEstadoColorDinamico?.text = "Desactivado"
            tvEstadoColorDinamico?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tinta_suave))
        }
    }

    /** The subtitle says what rounding does right now; the example shows it with a sample price. */
    private fun actualizarSimulacion(multiploSimulado: Double? = null) {
        val context = context ?: return
        val activo = PreferenciasApp.isRedondeoActivo(context)
        val multiplo = multiploSimulado ?: PreferenciasApp.getMultiploRedondeo(context)

        tvEstadoRedondeo?.text = if (activo) {
            "Sube al siguiente múltiplo de ${PreferenciasApp.formatearMultiplo(multiplo)}"
        } else {
            "Se usa el precio exacto"
        }
        tvEstadoRedondeo?.setTextColor(
            ContextCompat.getColor(context, if (activo) R.color.morado_fuerte else R.color.tinta_suave)
        )

        val (precioRedondeado, ajuste) = PreferenciasApp.calcularRedondeo(PRECIO_EJEMPLO, activo, multiplo)
        tvSimulacionBase?.text = PRECIO_EJEMPLO.comoDinero()
        tvSimulacionResultado?.text = precioRedondeado.comoDinero()
        tvSimulacionAjuste?.text = if (ajuste > 0.0) "+${ajuste.comoDinero()}" else "sin cambio"
    }

    override fun onPause() {
        super.onPause()
        // Si el usuario escribió un múltiplo válido en «Otro» pero no presionó "Usar", se persiste para no perderlo
        if (cgMultiplos?.checkedChipId != R.id.chipMultiploOtro) return
        val texto = etMultiploPersonalizado?.text?.toString()?.trim()
        val nuevoMultiplo = texto?.toDoubleOrNull()
        if (nuevoMultiplo != null && nuevoMultiplo > 0.0) {
            context?.let { ctx ->
                PreferenciasApp.setMultiploRedondeo(ctx, nuevoMultiplo)
                viewModel.calcularTotal()
            }
        }
    }

    private companion object {
        const val PRECIO_EJEMPLO = 143.20

        val MULTIPLOS_RAPIDOS = mapOf(
            R.id.chipMultiplo1 to 1.0,
            R.id.chipMultiplo5 to 5.0,
            R.id.chipMultiplo10 to 10.0,
            R.id.chipMultiplo50 to 50.0
        )
    }
}
