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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.validar
import com.example.calculadoracrochet.utils.errorSiNoPositivo
import com.example.calculadoracrochet.utils.aNumeroOInvalido
import com.example.calculadoracrochet.utils.animarCambiosDeLayout
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.ui.MainActivity
import androidx.core.widget.doAfterTextChanged
import android.view.ViewGroup

class PatronFragment : Fragment(R.layout.fragment_patron) {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    private var ultimoRetrocesoTiempo: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EncabezadoPaso.configurar(
            raiz = view,
            paso = 2,
            titulo = "El patrón",
            subtitulo = "¿Tú lo hiciste o lo compraste? 🤨",
            patron = R.drawable.encabezado_patron,
            patronSiguiente = R.drawable.encabezado_mano_obra,
            alVolver = { requireActivity().onBackPressedDispatcher.onBackPressed() }
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tiempoActual = SystemClock.elapsedRealtime()

                if (tiempoActual - ultimoRetrocesoTiempo < 400L) {
                    return
                }
                ultimoRetrocesoTiempo = tiempoActual

                (requireActivity() as MainActivity).regresarDeslizando()
            }
        })

        val tgComproPatron = view.findViewById<MaterialButtonToggleGroup>(R.id.tgComproPatron)
        val llCamposPatron = view.findViewById<LinearLayout>(R.id.llCamposPatronComprado)

        val tilCostoPatron = view.findViewById<TextInputLayout>(R.id.tilCostoPatron)
        val tilNumeroMunecos = view.findViewById<TextInputLayout>(R.id.tilNumeroMunecos)

        val etCostoPatron = view.findViewById<EditText>(R.id.etCostoPatron)
        val etNumeroMunecos = view.findViewById<EditText>(R.id.etNumeroMunecos)

        // Editing a project or restoring a draft: show the saved pattern cost instead of resetting it to 0.
        // Only the cost per piece is saved, so it comes back as that cost for 1 piece.
        val costoGuardado = viewModel.calculo.costoDiseno
        val textoCostoGuardado = if (costoGuardado > 0.0) {
            String.format(Locale.US, "%.2f", costoGuardado).removeSuffix(".00")
        } else {
            ""
        }
        if (costoGuardado > 0.0) {
            tgComproPatron.check(R.id.btnPatronSi)
            llCamposPatron.visibility = View.VISIBLE
            etCostoPatron.setText(textoCostoGuardado)
            etNumeroMunecos.setText("1")
        }

        fun actualizarMonto() {
            val porPieza = if (tgComproPatron.checkedButtonId == R.id.btnPatronSi) {
                CostoEnVivo.patronPorPieza(
                    etCostoPatron.text.toString().trim().toDoubleOrNull(),
                    etNumeroMunecos.text.toString().trim().toIntOrNull()
                )
            } else {
                0.0
            }
            EncabezadoPaso.mostrarMonto(view, "Patrón por pieza", porPieza?.comoDinero() ?: "—")
        }
        etCostoPatron.doAfterTextChanged { actualizarMonto() }
        etNumeroMunecos.doAfterTextChanged { actualizarMonto() }
        actualizarMonto()

        tgComproPatron.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                tgComproPatron.hapticSeleccion()
                (llCamposPatron.parent as ViewGroup).animarCambiosDeLayout()
                if (checkedId == R.id.btnPatronSi) {
                    llCamposPatron.visibility = View.VISIBLE
                } else {
                    llCamposPatron.visibility = View.GONE
                    etCostoPatron.text?.clear()
                    etNumeroMunecos.text?.clear()
                    tilCostoPatron.error = null
                    tilNumeroMunecos.error = null
                }
                actualizarMonto()
            }
        }

        view.findViewById<View>(R.id.btnContinuarPaso).setOnClickListener {
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
                    // The field shows 2 decimals of the saved cost; keep the exact value if it was not changed
                    viewModel.calculo.costoDiseno =
                        if (strCosto == textoCostoGuardado && unidades == 1) costoGuardado else costo / unidades.toDouble()
                }

            } else {
                viewModel.calculo.costoDiseno = 0.0
            }

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.guardarBorrador()
                (requireActivity() as MainActivity).navegarDeslizando(ManoObraFragment())
            } else {
                view.hapticError()
            }
        }
    }
}