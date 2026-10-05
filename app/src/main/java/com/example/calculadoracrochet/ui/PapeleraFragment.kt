package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.adapters.PapeleraAdapter
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.HapticHelper
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PapeleraFragment : Fragment(R.layout.fragment_papelera) {

    private val viewModel: MinaViewModel by activityViewModels()
    private lateinit var adapter: PapeleraAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cardAcciones = view.findViewById<MaterialCardView>(R.id.cardAccionesSeleccion)
        val tvConteo = view.findViewById<TextView>(R.id.tvConteoSeleccionados)
        val rv = view.findViewById<RecyclerView>(R.id.rvProyectosPapelera)
        val llVacio = view.findViewById<View>(R.id.llPapeleraVacia)

        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = PapeleraAdapter(
            lista = viewModel.papeleraProyectos,
            onSeleccionCambiada = { totalSeleccionados ->
                if (totalSeleccionados > 0) {
                    cardAcciones.visibility = View.VISIBLE
                    tvConteo.text = "$totalSeleccionados seleccionados"
                } else {
                    cardAcciones.visibility = View.GONE
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

        view.findViewById<ImageButton>(R.id.btnVolverPapelera).setOnClickListener {
            it.hapticClickPrincipal()
            parentFragmentManager.popBackStack()
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
                    if (exito == true) {
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
                    if (exito == true) {
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
                    if (exito == true) {
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
        llVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }
}