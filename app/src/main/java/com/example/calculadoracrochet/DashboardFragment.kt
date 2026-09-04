package com.example.calculadoracrochet

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter
    private var ordenMasReciente = true

    private var carpetaSeleccionada = "Todos"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProyectosAdapter(
            listaProyectos = emptyList<ProyectoCrochet>(),
            onItemClick = { proyectoClickeado ->
                mostrarDetalleProyecto(proyectoClickeado)
            },
            onEditCarpetaClick = { proyectoParaEditar ->
                mostrarModalCarpetas(proyectoParaEditar)
            }
        )

        recyclerView.adapter = adapter

        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            filtrarYOrdenarLista()
        }

        val etBuscar = view.findViewById<EditText>(R.id.etBuscarProyecto)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filtrarYOrdenarLista()
            }
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

    private fun mostrarModalCarpetas(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_carpetas, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val cbAmigurumi = dialogView.findViewById<MaterialCheckBox>(R.id.cbAmigurumi)
        val cbLlaveros = dialogView.findViewById<MaterialCheckBox>(R.id.cbLlaveros)
        val cbRamos = dialogView.findViewById<MaterialCheckBox>(R.id.cbRamos)
        val cbPeluches = dialogView.findViewById<MaterialCheckBox>(R.id.cbPeluches)
        val cbAccesorios = dialogView.findViewById<MaterialCheckBox>(R.id.cbAccesorios)
        val cbExtras = dialogView.findViewById<MaterialCheckBox>(R.id.cbExtras)

        cbAmigurumi.isChecked = proyecto.carpetas.contains("Amigurumi")
        cbLlaveros.isChecked = proyecto.carpetas.contains("Llaveros")
        cbRamos.isChecked = proyecto.carpetas.contains("Ramos")
        cbPeluches.isChecked = proyecto.carpetas.contains("Peluches")
        cbAccesorios.isChecked = proyecto.carpetas.contains("Accesorios")
        cbExtras.isChecked = proyecto.carpetas.contains("Extras")

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarCarpetas).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarCarpetas).setOnClickListener {
            val nuevasCarpetas = mutableListOf<String>()
            if (cbAmigurumi.isChecked) nuevasCarpetas.add("Amigurumi")
            if (cbLlaveros.isChecked) nuevasCarpetas.add("Llaveros")
            if (cbRamos.isChecked) nuevasCarpetas.add("Ramos")
            if (cbPeluches.isChecked) nuevasCarpetas.add("Peluches")
            if (cbAccesorios.isChecked) nuevasCarpetas.add("Accesorios")
            if (cbExtras.isChecked) nuevasCarpetas.add("Extras")

            proyecto.carpetas = nuevasCarpetas

            viewModel.actualizarCarpetasProyecto(proyecto)

            filtrarYOrdenarLista()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun configurarBotonesCarpetas(view: View) {
        val carpetasIds = listOf(
            "Todos" to R.id.folderTodos,
            "Amigurumi" to R.id.folderAmigurumi,
            "Llaveros" to R.id.folderLlaveros,
            "Ramos" to R.id.folderRamos,
            "Peluches" to R.id.folderPeluches,
            "Accesorios" to R.id.folderAccesorios,
            "Extras" to R.id.folderExtras
        )

        carpetasIds.forEach { (nombreCarpeta, idLayout) ->
            view.findViewById<LinearLayout>(idLayout).setOnClickListener {
                carpetaSeleccionada = nombreCarpeta

                carpetasIds.forEach { (_, id) ->
                    val layout = view.findViewById<LinearLayout>(id)
                    layout.alpha = if (id == idLayout) 1.0f else 0.5f
                }

                filtrarYOrdenarLista()
            }
        }
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

        if (!proyecto.fotoUri.isNullOrEmpty()) {
            cvFotoContenedor.visibility = View.VISIBLE
            ivFoto.load(proyecto.fotoUri) {
                crossfade(true)
                error(android.R.drawable.ic_menu_camera)
            }
        } else {
            cvFotoContenedor.visibility = View.GONE
        }

        dialogView.findViewById<TextView>(R.id.tvDetalleNombre).text = proyecto.nombre

        val formatoFecha = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "MX"))
        dialogView.findViewById<TextView>(R.id.tvDetalleFecha).text = formatoFecha.format(Date(proyecto.fechaGuardado))

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
            DialogUtils.mostrarModal(requireContext(), "Eliminar Proyecto", "¿Segura que deseas eliminar '${proyecto.nombre}'?", "Eliminar") {
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

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar).setOnClickListener {
            prepararProyectoParaEdicion(esDuplicado = true)
        }

        val btnOpciones = dialogView.findViewById<View>(R.id.btnOpcionesDetalle)
        btnOpciones?.setOnClickListener {
            prepararProyectoParaEdicion(esDuplicado = false)
        }

        dialog.show()
    }
}