package com.example.calculadoracrochet.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class EstadisticasFragment : Fragment(R.layout.fragment_estadisticas) {

    private val viewModel: MinaViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnVolverEstadisticas)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        calcularYMostrarMetricas(view)
    }

    private fun calcularYMostrarMetricas(rootView: View) {
        val proyectos = viewModel.proyectosActivos

        val totalProyectos = proyectos.size
        val totalVentas = proyectos.sumOf { it.precioFinal }

        rootView.findViewById<TextView>(R.id.tvNumeroProyectos)?.text =
            if (totalProyectos == 1) "1 creación" else "$totalProyectos creaciones"
        rootView.findViewById<TextView>(R.id.tvTotalVentas)?.text =
            String.format(Locale("es", "MX"), "$%,.2f", totalVentas)

        val masCostoso = proyectos.maxByOrNull { it.precioFinal }
        val masHoras = proyectos.maxByOrNull { it.horasTrabajadas }

        configurarDestacado(
            proyecto = masCostoso,
            iv = rootView.findViewById(R.id.ivImgMasCostoso),
            tvNombre = rootView.findViewById(R.id.tvNombreMasCostoso),
            tvValor = rootView.findViewById(R.id.tvValorMasCostoso),
            textoValor = masCostoso?.let { String.format(Locale("es", "MX"), "$%,.2f", it.precioFinal) } ?: "$0.00"
        )

        configurarDestacado(
            proyecto = masHoras,
            iv = rootView.findViewById(R.id.ivImgMasHoras),
            tvNombre = rootView.findViewById(R.id.tvNombreMasHoras),
            tvValor = rootView.findViewById(R.id.tvValorMasHoras),
            textoValor = masHoras?.let { String.format(Locale("es", "MX"), "%.1f hrs", it.horasTrabajadas) } ?: "0.0 hrs"
        )

        val llCarpetas = rootView.findViewById<LinearLayout>(R.id.llContenedorCarpetasStats)
        llCarpetas?.removeAllViews()

        val carpetasSinTodos = CarpetasConfig.listaCarpetas.filter { it.nombre != "Todos" }

        val conteosPorCarpeta = carpetasSinTodos.map { carpeta ->
            val conteo = proyectos.count { it.carpetas.contains(carpeta.nombre) }
            Pair(carpeta, conteo)
        }

        val totalPiezasGrafica = conteosPorCarpeta.sumOf { it.second }

        val mapaPorcentajes = mutableMapOf<String, Int>()
        if (totalPiezasGrafica > 0) {
            data class ItemResto(val nombre: String, val base: Int, val resto: Double)

            val listaItems = conteosPorCarpeta.filter { it.second > 0 }.map { (carpeta, conteo) ->
                val exacto = (conteo.toDouble() / totalPiezasGrafica) * 100.0
                val base = exacto.toInt()
                ItemResto(carpeta.nombre, base, exacto - base)
            }

            val sumaBases = listaItems.sumOf { it.base }
            val faltante = 100 - sumaBases

            val ordenados = listaItems.sortedByDescending { it.resto }
            ordenados.forEachIndexed { i, item ->
                val extra = if (i < faltante) 1 else 0
                mapaPorcentajes[item.nombre] = item.base + extra
            }
        }

        val listaSlices = mutableListOf<PieChartView.Slice>()

        val coloresGrafica = listOf(
            0xFF8A4FB8.toInt(),
            0xFF10B981.toInt(),
            0xFFF59E0B.toInt(),
            0xFFEC4899.toInt(),
            0xFF3B82F6.toInt(),
            0xFF8B5CF6.toInt(),
            0xFF64748B.toInt()
        )

        conteosPorCarpeta.forEachIndexed { index, (carpeta, conteo) ->
            val colorAsignado = coloresGrafica[index % coloresGrafica.size]
            val porcentaje = mapaPorcentajes[carpeta.nombre] ?: 0

            if (conteo > 0) {
                listaSlices.add(PieChartView.Slice(carpeta.nombre, conteo, colorAsignado, porcentaje))
            }

            if (llCarpetas != null) {
                val rowView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_estadistica_carpeta, llCarpetas, false)

                val cardColor = rowView.findViewById<MaterialCardView>(R.id.cardColorIndicador)
                val ivIcono = rowView.findViewById<ImageView>(R.id.ivIconoCarpetaStat)
                val tvNombre = rowView.findViewById<TextView>(R.id.tvNombreCarpetaStat)
                val tvConteo = rowView.findViewById<TextView>(R.id.tvConteoCarpetaStat)

                val colorFinal = if (conteo > 0) colorAsignado else 0xFFE2E8F0.toInt()
                val colorStateList = ColorStateList.valueOf(colorFinal)
                cardColor?.setCardBackgroundColor(colorStateList)
                cardColor?.backgroundTintList = colorStateList

                ivIcono?.setImageResource(carpeta.iconoRes)
                ivIcono?.scaleType = ImageView.ScaleType.CENTER_CROP
                ivIcono?.setPadding(0, 0, 0, 0)
                ivIcono?.imageTintList = null

                val textoProyectos = if (conteo == 1) "1 proyecto" else "$conteo proyectos"
                tvNombre?.text = carpeta.nombre
                tvConteo?.text = if (conteo > 0) "$textoProyectos ($porcentaje%)" else "0 proyectos"

                llCarpetas.addView(rowView)
            }
        }

        val pieChart = rootView.findViewById<PieChartView>(R.id.pieChartCarpetas)
        pieChart?.setData(listaSlices)
    }

    private fun configurarDestacado(
        proyecto: ProyectoCrochet?,
        iv: ImageView?,
        tvNombre: TextView?,
        tvValor: TextView?,
        textoValor: String
    ) {
        if (iv == null || tvNombre == null || tvValor == null) return

        if (proyecto != null) {
            tvNombre.text = proyecto.nombre
            tvValor.text = textoValor

            val fotoParaMostrar = proyecto.fotoMiniaturaUri ?: proyecto.fotoUri
            if (!fotoParaMostrar.isNullOrEmpty()) {
                iv.load(fotoParaMostrar) {
                    crossfade(true)
                    error(R.drawable.ic_photo)
                }
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setPadding(0, 0, 0, 0)
                iv.imageTintList = null
            } else {
                iv.load(null)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setPadding(0, 0, 0, 0)

                val iconoRes = if (proyecto.carpetas.size == 1) {
                    CarpetasConfig.listaCarpetas.find { it.nombre == proyecto.carpetas.first() }?.iconoRes
                        ?: R.drawable.img_todos
                } else {
                    R.drawable.img_todos
                }
                iv.setImageResource(iconoRes)
                iv.imageTintList = null
            }
        } else {
            tvNombre.text = "Sin proyectos"
            tvValor.text = "-"
            iv.load(null)
            iv.setImageResource(R.drawable.img_todos)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setPadding(0, 0, 0, 0)
            iv.imageTintList = null
        }
    }
}