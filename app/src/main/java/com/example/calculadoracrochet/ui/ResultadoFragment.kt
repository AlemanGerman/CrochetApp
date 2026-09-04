package com.example.calculadoracrochet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.viewmodel.CalculadoraViewModel
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ResultadoFragment : Fragment(R.layout.fragment_resultado) {
    private val viewModel: CalculadoraViewModel by activityViewModels()

    private var uriFotoSeleccionada: String? = null
    private var ivPreviewModal: ImageView? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uriFotoSeleccionada = uri.toString()
            try {
                ivPreviewModal?.setImageURI(uri)
                ivPreviewModal?.imageTintList = null
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.calcularTotal()

        val subtotal = viewModel.costoMateriales + viewModel.costoDiseno + viewModel.costoManoObra + viewModel.costoEmpaque
        val ganancia = subtotal * (viewModel.margenGanancia / 100)

        val tvFechaTicket = view.findViewById<TextView>(R.id.tvFechaTicket)
        val dateFormato = SimpleDateFormat("EEEE, dd MMM, yyyy", Locale("es", "MX"))

        fun actualizarTextoFecha() {
            val fechaAUsar = viewModel.fechaEnEdicion ?: System.currentTimeMillis()
            tvFechaTicket.text = dateFormato.format(Date(fechaAUsar)).uppercase()
        }

        actualizarTextoFecha()
        val btnCambiarFecha = view.findViewById<MaterialButton>(R.id.btnCambiarFecha)

        if (viewModel.idProyectoEnEdicion != null) {
            btnCambiarFecha.visibility = View.VISIBLE
            btnCambiarFecha.setOnClickListener { mostrarModalFecha { actualizarTextoFecha() } }
        } else {
            btnCambiarFecha.visibility = View.GONE
        }

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

        view.findViewById<View>(R.id.btnGuardar).setOnClickListener { mostrarModalGuardar() }
    }

    private fun mostrarModalFecha(onFechaActualizada: () -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seleccionar_fecha, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePickerSpinner)
        val calendario = Calendar.getInstance()
        viewModel.fechaEnEdicion?.let { calendario.timeInMillis = it }
        datePicker.updateDate(calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH))

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarFecha).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<MaterialButton>(R.id.btnAceptarFecha).setOnClickListener {
            val nuevaFecha = Calendar.getInstance()
            nuevaFecha.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            viewModel.fechaEnEdicion = nuevaFecha.timeInMillis
            onFechaActualizada()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun mostrarModalGuardar() {
        uriFotoSeleccionada = null
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_guardar_proyecto, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).setCancelable(false).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        ivPreviewModal = dialogView.findViewById(R.id.ivFotoPreview)
        val btnElegirFoto = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarFoto)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNombreGuardar)
        val tilNombre = dialogView.findViewById<TextInputLayout>(R.id.tilNombreGuardar)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarGuardar)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarGuardar)
        val cgCarpetas = dialogView.findViewById<ChipGroup>(R.id.cgCarpetas)
        cgCarpetas.removeAllViews()

        val mapChips = mutableMapOf<String, Chip>()

        CarpetasConfig.obtenerNombresSinTodos().forEach { nombreCarpeta ->
            val info = CarpetasConfig.listaCarpetas.find { it.nombre == nombreCarpeta }
            val chip = Chip(requireContext()).apply {
                text = nombreCarpeta
                isCheckable = true
                info?.let {
                    setChipBackgroundColorResource(it.colorRes)
                    setTextColor(ContextCompat.getColor(context, it.textColorRes))
                    setChipStrokeColorResource(it.colorRes)
                    chipStrokeWidth = 1f * resources.displayMetrics.density
                }
            }
            if (viewModel.carpetasTemp.contains(nombreCarpeta)) chip.isChecked = true
            mapChips[nombreCarpeta] = chip
            cgCarpetas.addView(chip)
        }

        if (viewModel.nombreTemp.isNotEmpty()) etNombre?.setText(viewModel.nombreTemp)

        if (!viewModel.fotoUriTemp.isNullOrEmpty()) {
            uriFotoSeleccionada = viewModel.fotoUriTemp
            try {
                ivPreviewModal?.setImageURI(Uri.parse(viewModel.fotoUriTemp))
                ivPreviewModal?.imageTintList = null
            } catch (e: Exception) { uriFotoSeleccionada = null }
        }

        btnElegirFoto?.setOnClickListener { pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build()) }
        ivPreviewModal?.setOnClickListener { pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build()) }
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

            val carpetasSeleccionadas = mapChips.filter { it.value.isChecked }.keys.toList()
            viewModel.carpetasTemp.clear()
            viewModel.carpetasTemp.addAll(carpetasSeleccionadas)

            var fotoBytes: ByteArray? = null
            if (uriFotoSeleccionada != null && !uriFotoSeleccionada!!.startsWith("http")) {
                try {
                    val uri = Uri.parse(uriFotoSeleccionada)
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    fotoBytes = inputStream?.readBytes()
                    inputStream?.close()
                } catch (e: Exception) { e.printStackTrace() }
            }

            viewModel.guardarProyecto(nombre, uriFotoSeleccionada, fotoBytes) { exito ->
                if (exito) {
                    dialog.dismiss()
                    parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, DashboardFragment()).commit()
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