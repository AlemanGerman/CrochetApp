package com.example.calculadoracrochet

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter
    private var ordenMasReciente = true
    private var carpetaSeleccionada = "Todos"

    private var proyectoEditandoID: String? = null
    private var ivFotoActualizando: ImageView? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && proyectoEditandoID != null) {
            val idProyecto = proyectoEditandoID!!
            ivFotoActualizando?.load(uri) { crossfade(true) }
            ivFotoActualizando?.imageTintList = null
            ivFotoActualizando?.scaleType = ImageView.ScaleType.CENTER_CROP

            var fotoBytes: ByteArray? = null
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                fotoBytes = inputStream?.readBytes()
                inputStream?.close()
            } catch (e: Exception) { e.printStackTrace() }

            if (fotoBytes != null) {
                val storageRef = FirebaseStorage.getInstance().reference.child("fotos_proyectos/${idProyecto}.jpg")
                storageRef.putBytes(fotoBytes).addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uriDescarga ->
                        FirebaseFirestore.getInstance().collection("proyectos_mina")
                            .document(idProyecto).update("fotoUri", uriDescarga.toString())
                    }
                }
            }
        }
        proyectoEditandoID = null
        ivFotoActualizando = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProyectosAdapter(
            listaProyectos = emptyList<ProyectoCrochet>(),
            onItemClick = { proyectoClickeado -> mostrarDetalleProyecto(proyectoClickeado) },
            onEditCarpetaClick = { proyectoParaEditar -> mostrarModalCarpetas(proyectoParaEditar) }
        )
        recyclerView.adapter = adapter

        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            filtrarYOrdenarLista()
        }

        val etBuscar = view.findViewById<EditText>(R.id.etBuscarProyecto)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filtrarYOrdenarLista() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val btnOrdenar = view.findViewById<MaterialButton>(R.id.btnOrdenarFechas)
        btnOrdenar.setOnClickListener {
            ordenMasReciente = !ordenMasReciente
            val iconRes = if (ordenMasReciente) R.drawable.ic_flecha_abajo else R.drawable.ic_flecha_arriba
            btnOrdenar.setIconResource(iconRes)
            filtrarYOrdenarLista()
            recyclerView.scrollToPosition(0)
        }

        view.findViewById<MaterialButton>(R.id.btnNuevoProyecto).setOnClickListener {
            viewModel.limpiarCalculoActual()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MaterialesFragment())
                .addToBackStack(null)
                .commit()
        }

        configurarBotonesCarpetas(view)
    }

    private fun configurarBotonesCarpetas(view: View) {
        val llContenedor = view.findViewById<LinearLayout>(R.id.llCarpetasDashboard)
        llContenedor.removeAllViews()

        val vistasCarpetas = mutableListOf<View>()

        CarpetasConfig.listaCarpetas.forEach { carpetaInfo ->
            val itemView = layoutInflater.inflate(R.layout.item_carpeta_dashboard, llContenedor, false)

            val ivIcono = itemView.findViewById<ImageView>(R.id.ivIconoCarpeta)
            val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreCarpeta)

            ivIcono.setImageResource(carpetaInfo.iconoRes)
            tvNombre.text = carpetaInfo.nombre
            tvNombre.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

            itemView.alpha = if (carpetaInfo.nombre == carpetaSeleccionada) 1.0f else 0.5f

            itemView.setOnClickListener {
                carpetaSeleccionada = carpetaInfo.nombre
                vistasCarpetas.forEach { vista ->
                    val tv = vista.findViewById<TextView>(R.id.tvNombreCarpeta)
                    vista.alpha = if (tv.text.toString() == carpetaSeleccionada) 1.0f else 0.5f
                }
                filtrarYOrdenarLista()
            }

            vistasCarpetas.add(itemView)
            llContenedor.addView(itemView)
        }
    }

    private fun mostrarModalCarpetas(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_carpetas, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llCarpetasContenedor)
        llContenedor.removeAllViews()

        val checkboxesMap = mutableMapOf<String, MaterialCheckBox>()

        CarpetasConfig.obtenerNombresSinTodos().forEach { nombreCarpeta ->
            val carpetaInfo = CarpetasConfig.listaCarpetas.find { it.nombre == nombreCarpeta }
            val cb = MaterialCheckBox(requireContext()).apply {
                text = nombreCarpeta
                setTextColor(ContextCompat.getColor(context, R.color.black))
                carpetaInfo?.let {
                    buttonTintList = ContextCompat.getColorStateList(context, it.colorRes)
                }
                isChecked = proyecto.carpetas.contains(nombreCarpeta)
            }
            checkboxesMap[nombreCarpeta] = cb
            llContenedor.addView(cb)
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarCarpetas).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarCarpetas).setOnClickListener {
            val nuevasCarpetas = checkboxesMap.filter { it.value.isChecked }.keys.toList()
            proyecto.carpetas = nuevasCarpetas
            viewModel.actualizarCarpetasProyecto(proyecto)
            filtrarYOrdenarLista()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun filtrarYOrdenarLista() {
        val textoBusqueda = view?.findViewById<EditText>(R.id.etBuscarProyecto)?.text.toString().lowercase()
        var listaResultante = viewModel.historialProyectos.filter { proyecto ->
            proyecto.nombre.lowercase().contains(textoBusqueda)
        }

        if (carpetaSeleccionada != "Todos") {
            listaResultante = listaResultante.filter { proyecto ->
                proyecto.carpetas.contains(carpetaSeleccionada)
            }
        }

        listaResultante = if (ordenMasReciente) {
            listaResultante.sortedByDescending { it.fechaGuardado }
        } else {
            listaResultante.sortedBy { it.fechaGuardado }
        }
        adapter.actualizarLista(listaResultante)
    }

    private fun mostrarDetalleProyecto(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_detalle_proyecto, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val cvFotoContenedor = dialogView.findViewById<View>(R.id.cvDetalleFotoContenedor)
        val ivFoto = dialogView.findViewById<ImageView>(R.id.ivDetalleFoto)

        fun refrescarImagenLocal() {
            cvFotoContenedor.visibility = View.VISIBLE
            if (!proyecto.fotoUri.isNullOrEmpty()) {
                ivFoto.load(proyecto.fotoUri) { crossfade(true) }
                ivFoto.scaleType = ImageView.ScaleType.CENTER_CROP
                ivFoto.imageTintList = null
            } else {
                ivFoto.setImageResource(android.R.drawable.ic_menu_camera)
                ivFoto.scaleType = ImageView.ScaleType.CENTER
                ivFoto.imageTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
            }
        }
        refrescarImagenLocal()

        cvFotoContenedor.setOnClickListener {
            if (proyecto.fotoUri.isNullOrEmpty()) {
                proyectoEditandoID = proyecto.id
                ivFotoActualizando = ivFoto
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                DialogUtils.mostrarOpciones(
                    context = requireContext(),
                    titulo = "Opciones de foto",
                    textoOpcion1 = "Cambiar foto",
                    textoOpcion2 = "Eliminar foto",
                    accionOpcion1 = {
                        proyectoEditandoID = proyecto.id
                        ivFotoActualizando = ivFoto
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    accionOpcion2 = {
                        FirebaseFirestore.getInstance().collection("proyectos_mina").document(proyecto.id).update("fotoUri", null)
                        proyecto.fotoUri = null
                        refrescarImagenLocal()
                    }
                )
            }
        }

        val tvNombre = dialogView.findViewById<TextView>(R.id.tvDetalleNombre)
        tvNombre.text = proyecto.nombre

        tvNombre.setOnClickListener {
            DialogUtils.mostrarInput(
                context = requireContext(),
                titulo = "Cambiar nombre",
                hint = "Nombre del proyecto",
                textoActual = proyecto.nombre
            ) { nuevoNombre ->
                FirebaseFirestore.getInstance().collection("proyectos_mina").document(proyecto.id).update("nombre", nuevoNombre)
                proyecto.nombre = nuevoNombre
                tvNombre.text = nuevoNombre
            }
        }

        val formatoFecha = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "MX"))
        val tvFecha = dialogView.findViewById<TextView>(R.id.tvDetalleFecha)
        tvFecha.text = formatoFecha.format(Date(proyecto.fechaGuardado))

        tvFecha.setOnClickListener {
            val (dialogFecha, fechaDialogView) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_seleccionar_fecha)
            val datePicker = fechaDialogView.findViewById<DatePicker>(R.id.datePickerSpinner)
            val calendario = Calendar.getInstance()
            calendario.timeInMillis = proyecto.fechaGuardado

            datePicker.updateDate(
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            )

            fechaDialogView.findViewById<MaterialButton>(R.id.btnCancelarFecha).setOnClickListener { dialogFecha.dismiss() }
            fechaDialogView.findViewById<MaterialButton>(R.id.btnAceptarFecha).setOnClickListener {
                val nuevaFecha = Calendar.getInstance()
                nuevaFecha.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                val tiempoMs = nuevaFecha.timeInMillis

                FirebaseFirestore.getInstance().collection("proyectos_mina").document(proyecto.id).update("fechaGuardado", tiempoMs)
                proyecto.fechaGuardado = tiempoMs
                tvFecha.text = formatoFecha.format(Date(tiempoMs))
                dialogFecha.dismiss()
            }
            dialogFecha.show()
        }

        dialogView.findViewById<TextView>(R.id.tvDetalleTotal).text = String.format("Total: $%.2f", proyecto.precioFinal)
        dialogView.findViewById<TextView>(R.id.tvDetallePatron).text = String.format("$%.2f", proyecto.costoDiseno)
        dialogView.findViewById<TextView>(R.id.tvDetalleEmpaque).text = String.format("$%.2f", proyecto.costoEmpaque)

        val subtotal = proyecto.costoMateriales + proyecto.costoDiseno + proyecto.costoManoObra + proyecto.costoEmpaque
        val ganancia = subtotal * (proyecto.margenGanancia / 100)
        dialogView.findViewById<TextView>(R.id.tvDetalleGanancia).text = String.format("+ Ganancia Extra (%d%%): $%.2f", proyecto.margenGanancia.toInt(), ganancia)

        val llToggleMateriales = dialogView.findViewById<LinearLayout>(R.id.llToggleMateriales)
        val tvCostoMatResumen = dialogView.findViewById<TextView>(R.id.tvCostoMaterialesResumen)
        val tvDesgloseMat = dialogView.findViewById<TextView>(R.id.tvDesgloseMateriales)
        tvCostoMatResumen.text = String.format("$%.2f", proyecto.costoMateriales)
        tvDesgloseMat.text = buildString {
            if (proyecto.listaEstambres.isEmpty() && proyecto.listaAccesorios.isEmpty()) {
                append("Sin detalles guardados")
            } else {
                proyecto.listaEstambres.forEach { appendLine("• ${it.nombre}: $${it.costo}") }
                proyecto.listaAccesorios.forEach { appendLine("• ${it.nombre}: $${it.costo}") }
            }
        }.trimEnd()

        var matExpandido = false
        llToggleMateriales.setOnClickListener {
            matExpandido = !matExpandido
            tvDesgloseMat.visibility = if (matExpandido) View.VISIBLE else View.GONE
        }

        val llToggleMano = dialogView.findViewById<LinearLayout>(R.id.llToggleManoObra)
        val tvCostoManoResumen = dialogView.findViewById<TextView>(R.id.tvCostoManoObraResumen)
        val tvDesgloseMano = dialogView.findViewById<TextView>(R.id.tvDesgloseManoObra)
        tvCostoManoResumen.text = String.format("$%.2f", proyecto.costoManoObra)
        tvDesgloseMano.text = String.format("• Horas invertidas: %.2f hrs\n• Cobro por hora: $%.2f", proyecto.horasTrabajadas, proyecto.pagoPorHora)

        var manoExpandida = false
        llToggleMano.setOnClickListener {
            manoExpandida = !manoExpandida
            tvDesgloseMano.visibility = if (manoExpandida) View.VISIBLE else View.GONE
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleCerrar).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleEliminar).setOnClickListener {
            DialogUtils.mostrarAlerta(
                context = requireContext(),
                titulo = "Eliminar Proyecto",
                mensaje = "¿Segura que deseas eliminar '${proyecto.nombre}'?",
                textoConfirmar = "Eliminar"
            ) {
                viewModel.eliminarProyecto(proyecto)
                dialog.dismiss()
            }
        }

        fun prepararProyectoParaEdicion(esDuplicado: Boolean) {
            viewModel.idProyectoEnEdicion = if (esDuplicado) null else proyecto.id
            viewModel.costoMateriales = proyecto.costoMateriales
            viewModel.costoDiseno = proyecto.costoDiseno
            viewModel.costoManoObra = proyecto.costoManoObra
            viewModel.costoEmpaque = proyecto.costoEmpaque
            viewModel.margenGanancia = proyecto.margenGanancia
            viewModel.horasTrabajadasTemp = proyecto.horasTrabajadas
            viewModel.pagoPorHoraTemp = proyecto.pagoPorHora
            viewModel.nombreTemp = proyecto.nombre
            viewModel.fotoUriTemp = proyecto.fotoUri
            viewModel.carpetasTemp.clear()
            viewModel.carpetasTemp.addAll(proyecto.carpetas)
            viewModel.listaEstambresGuardados.clear()
            viewModel.listaEstambresGuardados.addAll(proyecto.listaEstambres)
            viewModel.listaAccesoriosGuardados.clear()
            viewModel.listaAccesoriosGuardados.addAll(proyecto.listaAccesorios)

            dialog.dismiss()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MaterialesFragment())
                .addToBackStack(null)
                .commit()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar).setOnClickListener { prepararProyectoParaEdicion(true) }
        dialogView.findViewById<View>(R.id.btnOpcionesDetalle)?.setOnClickListener { prepararProyectoParaEdicion(false) }
        dialog.show()
    }
}