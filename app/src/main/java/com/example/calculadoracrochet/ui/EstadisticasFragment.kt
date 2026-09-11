package com.example.calculadoracrochet.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.viewmodel.CalculadoraViewModel
import java.util.Locale

class EstadisticasFragment : Fragment(R.layout.fragment_estadisticas) {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnVolverEstadisticas).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        calcularYMostrarMetricas(view)
    }

    private fun calcularYMostrarMetricas(rootView: View) {
        val proyectos = viewModel.proyectosActivos

        val totalProyectos = proyectos.size
        val totalVentas = proyectos.sumOf { it.precioFinal }
        val totalHoras = proyectos.sumOf { it.horasTrabajadas }

        val gananciaNeta = proyectos.sumOf { p ->
            val subtotal = p.costoMateriales + p.costoDiseno + p.costoManoObra + p.costoEmpaque
            val margenExtra = subtotal * (p.margenGanancia / 100.0)
            margenExtra + p.costoManoObra
        }

        rootView.findViewById<TextView>(R.id.tvNumeroProyectos).text = totalProyectos.toString()
        rootView.findViewById<TextView>(R.id.tvTotalVentas).text = String.format(Locale("es", "MX"), "$%,.2f", totalVentas)
        rootView.findViewById<TextView>(R.id.tvGananciasNetas).text = String.format(Locale("es", "MX"), "$%,.2f", gananciaNeta)
        rootView.findViewById<TextView>(R.id.tvHorasTotales).text = String.format(Locale("es", "MX"), "%.1f h", totalHoras)

        val llCarpetas = rootView.findViewById<LinearLayout>(R.id.llContenedorCarpetasStats)
        llCarpetas.removeAllViews()

        val carpetasSinTodos = CarpetasConfig.listaCarpetas.filter { it.nombre != "Todos" }

        carpetasSinTodos.forEach { carpeta ->
            val conteo = proyectos.count { it.carpetas.contains(carpeta.nombre) }

            val rowView = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.simple_list_item_2, llCarpetas, false)

            val text1 = rowView.findViewById<TextView>(android.R.id.text1)
            val text2 = rowView.findViewById<TextView>(android.R.id.text2)

            text1.text = carpeta.nombre
            text1.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
            text1.textSize = 15f

            text2.text = "$conteo ${if (conteo == 1) "proyecto" else "proyectos"}"
            text2.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
            text2.setTypeface(text2.typeface, Typeface.BOLD)
            text2.textSize = 14f

            llCarpetas.addView(rowView)
        }
    }
}