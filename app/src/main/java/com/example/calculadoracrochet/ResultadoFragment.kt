package com.example.calculadoracrochet

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ResultadoFragment : Fragment(R.layout.fragment_resultado) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

    private var uriFotoSeleccionada: String? = null
    private var ivPreviewModal: ImageView? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uriFotoSeleccionada = uri.toString()
            ivPreviewModal?.setImageURI(uri)
            ivPreviewModal?.imageTintList = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.calcularTotal()

        val subtotal = viewModel.costoMateriales + viewModel.costoDiseno + viewModel.costoManoObra + viewModel.costoEmpaque
        val ganancia = subtotal * (viewModel.margenGanancia / 100)

        view.findViewById<TextView>(R.id.tvPrecioFinal).text = String.format("$%.2f MXN", viewModel.precioFinalSugerdio)
        view.findViewById<TextView>(R.id.tvDesgloseMaterial).text = String.format("Materiales: $%.2f", viewModel.costoMateriales)
        view.findViewById<TextView>(R.id.tvDesglosePatron).text = String.format("Diseño/Patrón: $%.2f", viewModel.costoDiseno)
        view.findViewById<TextView>(R.id.tvDesgloseManoObra).text = String.format("Mano de Obra: $%.2f", viewModel.costoManoObra)
        view.findViewById<TextView>(R.id.tvDesgloseEmpaque).text = String.format("Empaque: $%.2f", viewModel.costoEmpaque)

        view.findViewById<TextView>(R.id.tvDesgloseSubtotal).text = String.format("Subtotal (Costo): $%.2f", subtotal)
        view.findViewById<TextView>(R.id.tvDesgloseGanancia).text = String.format("Ganancia Extra (%d%%): $%.2f", viewModel.margenGanancia.toInt(), ganancia)

        view.findViewById<View>(R.id.btnGuardar).setOnClickListener {
            mostrarModalGuardar()
        }
    }

    private fun mostrarModalGuardar() {
        uriFotoSeleccionada = null
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_guardar_proyecto, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        ivPreviewModal = dialogView.findViewById(R.id.ivFotoPreview)
        val btnElegirFoto = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarFoto)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNombreGuardar)
        val tilNombre = dialogView.findViewById<TextInputLayout>(R.id.tilNombreGuardar)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarGuardar)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarGuardar)

        btnElegirFoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                tilNombre.error = "Ingresa un nombre"
                return@setOnClickListener
            }

            viewModel.guardarProyecto(nombre, uriFotoSeleccionada)
            dialog.dismiss()

            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }

        dialog.show()
    }
}