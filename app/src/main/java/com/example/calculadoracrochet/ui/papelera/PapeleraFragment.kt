package com.example.calculadoracrochet.ui.papelera

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.utils.margenSobreBarraDeNavegacion
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado
import com.example.calculadoracrochet.utils.rellenarSobreBarraDeNavegacion
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.fundirVisibilidad
import com.example.calculadoracrochet.utils.HapticHelper
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PapeleraFragment : Fragment(R.layout.fragment_papelera) {

    private val viewModel: ProyectosViewModel by activityViewModels()
    private lateinit var adapter: PapeleraAdapter
    private var barraAccionesVisible = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        barraAccionesVisible = false

        val cardAcciones = view.findViewById<MaterialCardView>(R.id.cardAccionesSeleccion)
        val tvConteo = view.findViewById<TextView>(R.id.tvConteoSeleccionados)
        val rv = view.findViewById<RecyclerView>(R.id.rvProyectosPapelera)
        val llVacio = view.findViewById<View>(R.id.llPapeleraVacia)

        rv.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<View>(R.id.contenidoEncabezado).rellenarBajoBarraDeEstado()
        view.findViewById<View>(R.id.hojaPapelera).clipToOutline = true
        rv.rellenarSobreBarraDeNavegacion()
        cardAcciones.margenSobreBarraDeNavegacion((16 * resources.displayMetrics.density).toInt())

        adapter = PapeleraAdapter(
            lista = viewModel.papeleraProyectos,
            onSeleccionCambiada = { totalSeleccionados ->
                if (totalSeleccionados > 0) {
                    if (!barraAccionesVisible) {
                        barraAccionesVisible = true
                        cardAcciones.animate().cancel()
                        cardAcciones.visibility = View.VISIBLE
                        cardAcciones.translationY = 40f * resources.displayMetrics.density
                        cardAcciones.alpha = 0f
                        cardAcciones.animate().translationY(0f).alpha(1f).setDuration(240L)
                            .setInterpolator(OvershootInterpolator(1.2f)).start()
                    }
                    tvConteo.text = if (totalSeleccionados == 1) "1 seleccionado" else "$totalSeleccionados seleccionados"
                } else if (barraAccionesVisible) {
                    // Slides back down instead of vanishing
                    barraAccionesVisible = false
                    cardAcciones.animate().cancel()
                    cardAcciones.animate()
                        .translationY(40f * resources.displayMetrics.density)
                        .alpha(0f)
                        .setDuration(160L)
                        .withEndAction { cardAcciones.visibility = View.GONE }
                        .start()
                }
            },
            onRestaurarIndividual = { proyecto ->
                DialogUtils.mostrarAlerta(
                    context = requireContext(),
                    titulo = "Restaurar proyecto",
                    mensaje = "¿Deseas restaurar '${proyecto.nombre}' a tu lista de proyectos?",
                    textoConfirmar = "Restaurar",
                    iconoResId = null
                ) {
                    viewModel.restaurarProyectos(listOf(proyecto)) { restaurado ->
                        if (!isAdded) return@restaurarProyectos
                        if (restaurado) {
                            HapticHelper.exito(context = requireContext())
                            Toast.makeText(requireContext(), "Proyecto restaurado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onEliminarIndividual = { proyecto ->
                DialogUtils.mostrarAlerta(
                    context = requireContext(),
                    titulo = "Eliminar definitivamente",
                    mensaje = "¿Estás segura? No podrás recuperar '${proyecto.nombre}'.",
                    textoConfirmar = "Eliminar",
                    iconoResId = R.drawable.ic_sad
                ) {
                    viewModel.eliminarProyecto(proyecto) { eliminado ->
                        if (!isAdded) return@eliminarProyecto
                        if (eliminado) {
                            HapticHelper.advertenciaOEliminar(context = requireContext())
                        } else {
                            Toast.makeText(requireContext(), "No se pudo eliminar el proyecto", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        rv.adapter = adapter

        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            actualizarVista(llVacio)
        }

        view.findViewById<View>(R.id.btnVolverPapelera).setOnClickListener {
            it.hapticClickPrincipal()
            (requireActivity() as MainActivity).regresarDeslizando()
        }

        view.findViewById<MaterialButton>(R.id.btnVaciarPapelera).setOnClickListener {
            if (viewModel.papeleraProyectos.isEmpty()) return@setOnClickListener
            it.hapticEliminar()
            DialogUtils.mostrarAlerta(
                context = requireContext(),
                titulo = "Vaciar Papelera",
                mensaje = "¿Segura que deseas borrar todos los proyectos de la papelera? Esta acción no se puede deshacer.",
                textoConfirmar = "Vaciar todo",
                iconoResId = R.drawable.ic_sad
            ) {
                viewModel.vaciarPapelera { exito ->
                    if (!isAdded) return@vaciarPapelera
                    if (exito) {
                        HapticHelper.advertenciaOEliminar(context = requireContext())
                        Toast.makeText(requireContext(), "Papelera vaciada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnRestaurarSeleccionados).setOnClickListener {
            val seleccionados = adapter.seleccionados.toList()
            if (seleccionados.isEmpty()) return@setOnClickListener
            it.hapticClickPrincipal()

            val mensaje = if (seleccionados.size == 1) {
                "¿Deseas restaurar '${seleccionados.first().nombre}' a tu lista de proyectos?"
            } else {
                "¿Deseas restaurar estos ${seleccionados.size} proyectos a tu lista de proyectos?"
            }

            DialogUtils.mostrarAlerta(
                context = requireContext(),
                titulo = "Restaurar proyectos",
                mensaje = mensaje,
                textoConfirmar = "Restaurar",
                iconoResId = null
            ) {
                viewModel.restaurarProyectos(seleccionados) { exito ->
                    if (!isAdded) return@restaurarProyectos
                    if (exito) {
                        HapticHelper.exito(context = requireContext())
                        Toast.makeText(requireContext(), "${seleccionados.size} proyectos restaurados", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnEliminarSeleccionados).setOnClickListener {
            val seleccionados = adapter.seleccionados.toList()
            if (seleccionados.isEmpty()) return@setOnClickListener
            it.hapticEliminar()

            DialogUtils.mostrarAlerta(
                context = requireContext(),
                titulo = "Eliminar seleccionados",
                mensaje = "¿Deseas eliminar definitivamente estos ${seleccionados.size} proyectos?",
                textoConfirmar = "Eliminar",
                iconoResId = R.drawable.ic_sad
            ) {
                viewModel.eliminarProyectosPermanentes(seleccionados) { exito ->
                    if (!isAdded) return@eliminarProyectosPermanentes
                    if (exito) {
                        HapticHelper.advertenciaOEliminar(context = requireContext())
                        Toast.makeText(requireContext(), "Proyectos eliminados permanentemente", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        actualizarVista(llVacio)
    }

    private fun actualizarVista(llVacio: View) {
        val lista = viewModel.papeleraProyectos
        adapter.actualizarLista(lista)
        llVacio.fundirVisibilidad(lista.isEmpty())
        view?.findViewById<View>(R.id.btnVaciarPapelera)?.apply {
            isEnabled = lista.isNotEmpty()
            animate().alpha(if (lista.isEmpty()) 0f else 1f).setDuration(180L).start()
        }
    }
}