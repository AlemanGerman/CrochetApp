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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter
    private var ordenMasReciente = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProyectosAdapter(emptyList()) { proyectoClickeado ->
            mostrarDetalleProyecto(proyectoClickeado)
        }
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
        }

        view.findViewById<MaterialButton>(R.id.btnNuevoProyecto).setOnClickListener {
            viewModel.limpiarCalculoActual()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MaterialesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun filtrarYOrdenarLista() {
        val textoBusqueda = view?.findViewById<EditText>(R.id.etBuscarProyecto)?.text.toString().lowercase()

        var listaResultante = viewModel.historialProyectos.filter { proyecto ->
            proyecto.nombre.lowercase().contains(textoBusqueda)
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

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar).setOnClickListener {
            viewModel.costoMateriales = proyecto.costoMateriales
            viewModel.costoDiseno = proyecto.costoDiseno
            viewModel.costoManoObra = proyecto.costoManoObra
            viewModel.costoEmpaque = proyecto.costoEmpaque
            viewModel.margenGanancia = proyecto.margenGanancia

            viewModel.horasTrabajadasTemp = proyecto.horasTrabajadas
            viewModel.pagoPorHoraTemp = proyecto.pagoPorHora

            viewModel.nombreTemp = proyecto.nombre
            viewModel.fotoUriTemp = proyecto.fotoUri

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

        dialog.show()
    }
}