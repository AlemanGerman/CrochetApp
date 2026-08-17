package com.example.calculadoracrochet

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter // Asume que tienes tu adapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Configuras tu adapter y le pasas la función de clic
        adapter = ProyectosAdapter(viewModel.historialProyectos) { proyectoClickeado ->
            mostrarDetalleProyecto(proyectoClickeado)
        }
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnNuevoProyecto).setOnClickListener {
            viewModel.limpiarCalculoActual()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MaterialesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun mostrarDetalleProyecto(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_detalle_proyecto, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Llenar datos COMPLETOS en el modal del Dashboard
        dialogView.findViewById<TextView>(R.id.tvDetalleNombre).text = proyecto.nombre
        dialogView.findViewById<TextView>(R.id.tvDetalleTotal).text = String.format("Total: $%.2f", proyecto.precioFinal)

        dialogView.findViewById<TextView>(R.id.tvDetalleMateriales).text = String.format("Materiales: $%.2f", proyecto.costoMateriales)
        dialogView.findViewById<TextView>(R.id.tvDetallePatron).text = String.format("Diseño/Patrón: $%.2f", proyecto.costoDiseno)
        dialogView.findViewById<TextView>(R.id.tvDetalleManoObra).text = String.format("Mano de Obra: $%.2f", proyecto.costoManoObra)
        dialogView.findViewById<TextView>(R.id.tvDetalleEmpaque).text = String.format("Empaque: $%.2f", proyecto.costoEmpaque)

        // Calcular la ganancia exacta que tuvo este proyecto guardado
        val subtotalProyecto = proyecto.costoMateriales + proyecto.costoDiseno + proyecto.costoManoObra + proyecto.costoEmpaque
        val gananciaProyecto = subtotalProyecto * (proyecto.margenGanancia / 100)
        dialogView.findViewById<TextView>(R.id.tvDetalleGanancia).text = String.format("Ganancia Extra (%d%%): $%.2f", proyecto.margenGanancia.toInt(), gananciaProyecto)

        val btnCerrar = dialogView.findViewById<MaterialButton>(R.id.btnDetalleCerrar)
        val btnEliminar = dialogView.findViewById<MaterialButton>(R.id.btnDetalleEliminar)
        val btnDuplicar = dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        btnEliminar.setOnClickListener {
            // Reutilizamos tu DialogUtils (o un AlertDialog simple) para confirmar la eliminación
            DialogUtils.mostrarModal(
                requireContext(),
                "Eliminar Proyecto",
                "¿Segura que deseas eliminar '${proyecto.nombre}'?",
                "Eliminar"
            ) {
                viewModel.eliminarProyecto(proyecto)
                adapter.notifyDataSetChanged() // Refresca la lista
                dialog.dismiss()
            }
        }

        btnDuplicar.setOnClickListener {
            // Magia de copiar: Cargamos los valores al ViewModel como si fuera un proyecto en curso
            viewModel.costoMateriales = proyecto.costoMateriales
            viewModel.costoDiseno = proyecto.costoDiseno
            viewModel.costoManoObra = proyecto.costoManoObra
            viewModel.costoEmpaque = proyecto.costoEmpaque
            viewModel.margenGanancia = proyecto.margenGanancia

            dialog.dismiss()

            // Mandamos a la usuaria al Paso 1 (o al paso que desees) para que modifique factores
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MaterialesFragment())
                .addToBackStack(null)
                .commit()
        }

        dialog.show()
    }
}