package com.example.calculadoracrochet.ui.estadisticas

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.OnBackPressedCallback
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.PestanaPrincipal
import com.example.calculadoracrochet.utils.duracionLegible
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado
import com.example.calculadoracrochet.utils.rellenarSobreBarraDeNavegacion
import androidx.core.content.ContextCompat
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.google.android.material.card.MaterialCardView
import com.example.calculadoracrochet.utils.comoDinero

class EstadisticasFragment : Fragment(R.layout.fragment_estadisticas), PestanaPrincipal {

    override val idPestana = R.id.nav_estadisticas

    private val viewModel: ProyectosViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.contenidoEncabezado).rellenarBajoBarraDeEstado()
        view.findViewById<View>(R.id.hojaEstadisticas).apply {
            rellenarSobreBarraDeNavegacion()
            clipToOutline = true
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (requireActivity() as MainActivity).volverAProyectos()
            }
        })

        // Live: the numbers follow any project saved, edited or trashed meanwhile
        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) { calcularYMostrarMetricas(view) }
        calcularYMostrarMetricas(view)
    }

    override fun alVolverAPulsar() {
        view?.findViewById<android.widget.ScrollView>(R.id.hojaEstadisticas)?.smoothScrollTo(0, 0)
    }

    private fun calcularYMostrarMetricas(rootView: View) {
        val carpetasSinTodos = CarpetasConfig.listaCarpetas.filter { it.nombre != "Todos" }
        val resumen = EstadisticasProyectos.calcular(
            proyectos = viewModel.proyectosActivos,
            nombresCarpetas = carpetasSinTodos.map { it.nombre }
        )

        val totalProyectos = resumen.totalProyectos
        rootView.findViewById<TextView>(R.id.tvNumeroProyectos)?.text =
            if (totalProyectos == 1) "1 creación" else "$totalProyectos creaciones"
        rootView.findViewById<TextView>(R.id.tvTotalVentas)?.text =
            resumen.totalVentas.comoDinero()
        rootView.findViewById<TextView>(R.id.tvPrecioPromedio)?.text =
            if (totalProyectos > 0) (resumen.totalVentas / totalProyectos).comoDinero() else "—"

        val masCostoso = resumen.masCostoso
        val masHoras = resumen.masHoras

        configurarDestacado(
            proyecto = masCostoso,
            iv = rootView.findViewById(R.id.ivImgMasCostoso),
            tvNombre = rootView.findViewById(R.id.tvNombreMasCostoso),
            tvValor = rootView.findViewById(R.id.tvValorMasCostoso),
            textoValor = (masCostoso?.precioFinal ?: 0.0).comoDinero()
        )

        configurarDestacado(
            proyecto = masHoras,
            iv = rootView.findViewById(R.id.ivImgMasHoras),
            tvNombre = rootView.findViewById(R.id.tvNombreMasHoras),
            tvValor = rootView.findViewById(R.id.tvValorMasHoras),
            textoValor = masHoras?.let { duracionLegible(it.horasTrabajadas) } ?: "—"
        )

        val llCarpetas = rootView.findViewById<LinearLayout>(R.id.llContenedorCarpetasStats)
        llCarpetas?.removeAllViews()

        val listaSlices = mutableListOf<PieChartView.Slice>()


        carpetasSinTodos.zip(resumen.carpetas).forEach { (carpeta, estadistica) ->
            // Each folder keeps the color of its icon, so the chart and the list read at a glance
            val colorAsignado = ContextCompat.getColor(requireContext(), carpeta.colorGraficaRes)
            val conteo = estadistica.conteo
            val porcentaje = estadistica.porcentaje

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

                val colorFinal = if (conteo > 0) colorAsignado else ContextCompat.getColor(requireContext(), R.color.lavanda_borde)
                val colorStateList = ColorStateList.valueOf(colorFinal)
                cardColor?.setCardBackgroundColor(colorStateList)
                cardColor?.backgroundTintList = colorStateList

                ivIcono?.setImageResource(carpeta.iconoRes)

                val textoProyectos = if (conteo == 1) "1 proyecto" else "$conteo proyectos"
                tvNombre?.text = carpeta.nombre
                tvConteo?.text = if (conteo > 0) "$textoProyectos · $porcentaje%" else "Sin proyectos"

                llCarpetas.addView(rowView)
            }
        }

        val pieChart = rootView.findViewById<PieChartView>(R.id.pieChartCarpetas)
        pieChart?.setData(listaSlices, totalProyectos)
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