package com.example.calculadoracrochet

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultadoFragment : Fragment(R.layout.fragment_resultado) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

    private var uriFotoSeleccionada: String? = null
    private var ivPreviewModal: ImageView? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uriFotoSeleccionada = uri.toString()
            try {
                ivPreviewModal?.setImageURI(uri)
                ivPreviewModal?.imageTintList = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.calcularTotal()

        val subtotal = viewModel.costoMateriales + viewModel.costoDiseno + viewModel.costoManoObra + viewModel.costoEmpaque
        val ganancia = subtotal * (viewModel.margenGanancia / 100)

        val dateFormato = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale("es", "MX"))
        view.findViewById<TextView>(R.id.tvFechaTicket).text = dateFormato.format(Date()).uppercase()

        view.findViewById<TextView>(R.id.tvPrecioFinal).text = String.format("$%.2f", viewModel.precioFinalSugerdio)
        view.findViewById<TextView>(R.id.tvDesgloseMaterial).text = String.format("$%.2f", viewModel.costoMateriales)
        view.findViewById<TextView>(R.id.tvDesglosePatron).text = String.format("$%.2f", viewModel.costoDiseno)
        view.findViewById<TextView>(R.id.tvDesgloseManoObra).text = String.format("$%.2f", viewModel.costoManoObra)
        view.findViewById<TextView>(R.id.tvDesgloseEmpaque).text = String.format("$%.2f", viewModel.costoEmpaque)
        view.findViewById<TextView>(R.id.tvDesgloseSubtotal).text = String.format("$%.2f", subtotal)
        view.findViewById<TextView>(R.id.tvDesgloseGanancia).text = String.format("$%.2f", ganancia)

        view.findViewById<View>(R.id.btnGuardar).setOnClickListener {
            mostrarModalGuardar()
        }
    }

    private fun mostrarModalGuardar() {
        uriFotoSeleccionada = null
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_guardar_proyecto, null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        ivPreviewModal = dialogView.findViewById(R.id.ivFotoPreview)
        val btnElegirFoto = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarFoto)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNombreGuardar)
        val tilNombre = dialogView.findViewById<TextInputLayout>(R.id.tilNombreGuardar)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarGuardar)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarGuardar)

        if (viewModel.nombreTemp.isNotEmpty()) {
            etNombre?.setText(viewModel.nombreTemp)
        }

        if (!viewModel.fotoUriTemp.isNullOrEmpty()) {
            uriFotoSeleccionada = viewModel.fotoUriTemp
            try {
                ivPreviewModal?.setImageURI(android.net.Uri.parse(viewModel.fotoUriTemp))
                ivPreviewModal?.imageTintList = null
            } catch (e: Exception) {
                uriFotoSeleccionada = null
            }
        }

        btnElegirFoto?.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
        }

        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnConfirmar?.setOnClickListener {
            val nombre = etNombre?.text?.toString()?.trim() ?: ""
            if (nombre.isEmpty()) {
                tilNombre?.error = "Ingresa un nombre"
                return@setOnClickListener
            }

            viewModel.guardarProyecto(nombre, uriFotoSeleccionada)
            dialog.dismiss()

            parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }

        dialog.show()
    }
}