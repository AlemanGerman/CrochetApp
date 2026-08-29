package com.example.calculadoracrochet

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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

    private val storage = com.google.firebase.storage.FirebaseStorage.getInstance()

    private var uriFotoSeleccionada: String? = null
    private var ivPreviewModal: ImageView? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

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

        val dateFormato = SimpleDateFormat("EEEE, dd MMM, yyyy", Locale("es", "MX"))
        view.findViewById<TextView>(R.id.tvFechaTicket).text = dateFormato.format(Date()).uppercase()

        view.findViewById<TextView>(R.id.tvPrecioFinal).text = String.format("$%.2f", viewModel.precioFinalSugerdio)
        view.findViewById<TextView>(R.id.tvDesgloseMaterial).text = String.format("$%.2f", viewModel.costoMateriales)
        view.findViewById<TextView>(R.id.tvDesglosePatron).text = String.format("$%.2f", viewModel.costoDiseno)
        view.findViewById<TextView>(R.id.tvDesgloseManoObra).text = String.format("$%.2f", viewModel.costoManoObra)
        view.findViewById<TextView>(R.id.tvDesgloseEmpaque).text = String.format("$%.2f", viewModel.costoEmpaque)
        view.findViewById<TextView>(R.id.tvDesgloseSubtotal).text = String.format("$%.2f", subtotal)
        view.findViewById<TextView>(R.id.tvDesgloseGanancia).text = String.format("$%.2f", ganancia)

        val tvLabelGanancia = view.findViewById<TextView>(R.id.tvLabelGanancia)
        tvLabelGanancia.text = String.format("GANANCIA EXTRA (%d%%):", viewModel.margenGanancia.toInt())

        val llFilaMateriales = view.findViewById<LinearLayout>(R.id.llFilaMateriales)
        val tvDetalleMaterialesDesc = view.findViewById<TextView>(R.id.tvDetalleMaterialesDesc)

        tvDetalleMaterialesDesc.text = buildString {
            if (viewModel.listaEstambresGuardados.isEmpty() && viewModel.listaAccesoriosGuardados.isEmpty()) {
                append("- Sin detalles agregados")
            } else {
                viewModel.listaEstambresGuardados.forEach { appendLine("- ${it.nombre}: $${it.costo}") }
                viewModel.listaAccesoriosGuardados.forEach { appendLine("- ${it.nombre}: $${it.costo}") }
            }
        }.trimEnd()

        var matExpandido = false
        llFilaMateriales.setOnClickListener {
            matExpandido = !matExpandido
            tvDetalleMaterialesDesc.visibility = if (matExpandido) View.VISIBLE else View.GONE
        }

        val llFilaManoObra = view.findViewById<LinearLayout>(R.id.llFilaManoObra)
        val tvDetalleManoObraDesc = view.findViewById<TextView>(R.id.tvDetalleManoObraDesc)

        tvDetalleManoObraDesc.text = String.format("- Tiempo: %.2f hrs\n- Pago: $%.2f/hr", viewModel.horasTrabajadasTemp, viewModel.pagoPorHoraTemp)

        var manoExpandida = false
        llFilaManoObra.setOnClickListener {
            manoExpandida = !manoExpandida
            tvDetalleManoObraDesc.visibility = if (manoExpandida) View.VISIBLE else View.GONE
        }

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

            btnConfirmar.isEnabled = false
            btnCancelar?.isEnabled = false
            btnConfirmar.text = "Subiendo a la nube..."

            viewModel.guardarProyecto(nombre, uriFotoSeleccionada) { exito ->
                if (exito) {
                    dialog.dismiss()
                    parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, DashboardFragment())
                        .commit()
                } else {
                    btnConfirmar.isEnabled = true
                    btnCancelar?.isEnabled = true
                    btnConfirmar.text = "Reintentar"
                }
            }
        }

        dialog.show()
    }
}