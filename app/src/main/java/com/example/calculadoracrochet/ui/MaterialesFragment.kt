package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.CalculadoraViewModel
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import com.google.android.material.textfield.TextInputLayout

class MaterialesFragment : Fragment(R.layout.fragment_materiales) {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lottieGanchillo = requireActivity().findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieGanchillo)
        if (!lottieGanchillo.isAnimating) {
            lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
            lottieGanchillo.progress = 0f
            lottieGanchillo.visibility = View.VISIBLE
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (requireActivity() as MainActivity).regresarConGanchillo()
            }
        })

        val llContenedorEstambres = view.findViewById<LinearLayout>(R.id.llContenedorEstambres)
        val llContenedorExtra = view.findViewById<LinearLayout>(R.id.llContenedorMaterialesExtra)

        if (viewModel.listaEstambresGuardados.isEmpty()) {
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
        } else {
            viewModel.listaEstambresGuardados.forEach {
                agregarFilaEstambre(llContenedorEstambres, it.nombre, it.costo, it.totales, it.usados)
            }
        }

        if (viewModel.listaAccesoriosGuardados.isNotEmpty()) {
            viewModel.listaAccesoriosGuardados.forEach {
                agregarFilaMaterial(llContenedorExtra, it.nombre, it.costo)
            }
        }

        view.findViewById<View>(R.id.btnAnadirEstambre).setOnClickListener {
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
        }

        view.findViewById<View>(R.id.btnAnadirMaterial).setOnClickListener {
            DialogUtils.mostrarOpciones(
                context = requireContext(),
                titulo = "Añadir accesorio",
                textoOpcion1 = "Precio Directo",
                textoOpcion2 = "Calcular por cantidad",
                accionOpcion1 = { agregarFilaMaterial(llContenedorExtra, "", "") },
                accionOpcion2 = { agregarFilaAccesorioCalculado(llContenedorExtra, "") }
            )
        }

        view.findViewById<View>(R.id.btnSiguienteMateriales).setOnClickListener {
            var todoCorrecto = true
            var costoTotalEstambres = 0.0
            var costoTotalAccesorios = 0.0

            val estambresTemp = mutableListOf<EstambreGuardado>()
            val accesoriosTemp = mutableListOf<AccesorioGuardado>()

            if (llContenedorEstambres.childCount == 0) {
                DialogUtils.mostrarAlerta(
                    context = requireContext(),
                    titulo = "Faltan materiales",
                    mensaje = "Debes agregar por lo menos un estambre para poder hacer el cálculo.",
                    textoConfirmar = "Entendido",
                    iconoResId = null
                ) {}
                return@setOnClickListener
            }

            for (i in 0 until llContenedorEstambres.childCount) {
                val fila = llContenedorEstambres.getChildAt(i)
                val tilNombre = fila.findViewById<TextInputLayout>(R.id.tilNombreEstambre)
                val tilCostoMadeja = fila.findViewById<TextInputLayout>(R.id.tilCostoMadeja)
                val tilGramosTotales = fila.findViewById<TextInputLayout>(R.id.tilGramosTotales)
                val tilGramosUsados = fila.findViewById<TextInputLayout>(R.id.tilGramosUsados)

                val strNombre = fila.findViewById<EditText>(R.id.etNombreEstambre).text.toString().trim()
                val strCosto = fila.findViewById<EditText>(R.id.etCostoMadeja).text.toString()
                val strTotales = fila.findViewById<EditText>(R.id.etGramosTotales).text.toString()
                val strUsados = fila.findViewById<EditText>(R.id.etGramosUsados).text.toString()

                estambresTemp.add(EstambreGuardado(strNombre, strCosto, strTotales, strUsados))

                val costoMadeja = strCosto.toDoubleOrNull() ?: -1.0
                val gramosTotales = strTotales.toDoubleOrNull() ?: -1.0
                val gramosUsados = strUsados.toDoubleOrNull() ?: -1.0

                if (strNombre.isEmpty()) { tilNombre?.error = "Ingresa el color/marca"; todoCorrecto = false } else { tilNombre?.error = null }
                if (costoMadeja <= 0.0) { tilCostoMadeja?.error = "Debe ser mayor a $0"; todoCorrecto = false } else { tilCostoMadeja?.error = null }
                if (gramosTotales <= 0.0) { tilGramosTotales?.error = "Debe ser mayor a 0g"; todoCorrecto = false } else { tilGramosTotales?.error = null }
                if (gramosUsados <= 0.0) { tilGramosUsados?.error = "Debe ser mayor a 0g"; todoCorrecto = false } else { tilGramosUsados?.error = null }
                if (gramosUsados > gramosTotales && gramosTotales > 0) { tilGramosUsados?.error = "Excede el total"; todoCorrecto = false }

                if (costoMadeja > 0 && gramosTotales > 0 && gramosUsados > 0) {
                    costoTotalEstambres += (costoMadeja / gramosTotales) * gramosUsados
                }
            }

            for (i in 0 until llContenedorExtra.childCount) {
                val fila = llContenedorExtra.getChildAt(i)
                val etCostoDirecto = fila.findViewById<EditText>(R.id.etCostoMaterialExtra)

                if (etCostoDirecto != null) {
                    val tilNombreMaterial = fila.findViewById<TextInputLayout>(R.id.tilNombreMaterial)
                    val tilCostoMaterial = fila.findViewById<TextInputLayout>(R.id.tilCostoMaterialExtra)
                    val strNombreMat = fila.findViewById<EditText>(R.id.etNombreMaterial).text.toString().trim()
                    val strCostoMat = etCostoDirecto.text.toString()

                    accesoriosTemp.add(AccesorioGuardado(strNombreMat, strCostoMat))
                    val costoMaterial = strCostoMat.toDoubleOrNull() ?: -1.0

                    if (strNombreMat.isEmpty()) { tilNombreMaterial?.error = "Ingresa un nombre"; todoCorrecto = false } else { tilNombreMaterial?.error = null }
                    if (costoMaterial <= 0.0) { tilCostoMaterial?.error = "Debe ser mayor a $0"; todoCorrecto = false } else { tilCostoMaterial?.error = null }
                    if (costoMaterial > 0.0) costoTotalAccesorios += costoMaterial

                } else {
                    val tilNombre = fila.findViewById<TextInputLayout>(R.id.tilNombreAccCalc)
                    val tilCostoTotal = fila.findViewById<TextInputLayout>(R.id.tilCostoTotalAcc)
                    val tilTotalCant = fila.findViewById<TextInputLayout>(R.id.tilCantidadTotalAcc)
                    val tilUsadaCant = fila.findViewById<TextInputLayout>(R.id.tilCantidadUsadaAcc)

                    val strNombre = fila.findViewById<EditText>(R.id.etNombreAccCalc).text.toString().trim()
                    val strCosto = fila.findViewById<EditText>(R.id.etCostoTotalAcc).text.toString()
                    val strTotales = fila.findViewById<EditText>(R.id.etCantidadTotalAcc).text.toString()
                    val strUsados = fila.findViewById<EditText>(R.id.etCantidadUsadaAcc).text.toString()

                    val costoTotal = strCosto.toDoubleOrNull() ?: -1.0
                    val cantTotal = strTotales.toDoubleOrNull() ?: -1.0
                    val cantUsada = strUsados.toDoubleOrNull() ?: -1.0

                    if (strNombre.isEmpty()) { tilNombre?.error = "Falta nombre"; todoCorrecto = false } else { tilNombre?.error = null }
                    if (costoTotal <= 0.0) { tilCostoTotal?.error = "Debe ser > $0"; todoCorrecto = false } else { tilCostoTotal?.error = null }
                    if (cantTotal <= 0.0) { tilTotalCant?.error = "Debe ser > 0"; todoCorrecto = false } else { tilTotalCant?.error = null }
                    if (cantUsada <= 0.0) { tilUsadaCant?.error = "Debe ser > 0"; todoCorrecto = false } else { tilUsadaCant?.error = null }
                    if (cantUsada > cantTotal && cantTotal > 0) { tilUsadaCant?.error = "Excede el total"; todoCorrecto = false }

                    if (costoTotal > 0 && cantTotal > 0 && cantUsada > 0) {
                        val costoFinal = (costoTotal / cantTotal) * cantUsada
                        costoTotalAccesorios += costoFinal
                        accesoriosTemp.add(AccesorioGuardado(strNombre, costoFinal.toString()))
                    }
                }
            }

            if (todoCorrecto) {
                viewModel.costoMateriales = costoTotalEstambres + costoTotalAccesorios
                viewModel.listaEstambresGuardados.clear()
                viewModel.listaEstambresGuardados.addAll(estambresTemp)
                viewModel.listaAccesoriosGuardados.clear()
                viewModel.listaAccesoriosGuardados.addAll(accesoriosTemp)

                (requireActivity() as MainActivity).navegarConGanchillo(PatronFragment())
            }
        }
    }

    private fun agregarFilaAccesorioCalculado(contenedor: LinearLayout, nombreSugerido: String) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_accesorio_calculado, contenedor, false)
        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreAccCalc)
        if (nombreSugerido.isNotEmpty()) etNombre.setText(nombreSugerido)

        vistaFila.findViewById<ImageButton>(R.id.btnEliminarAccCalc).setOnClickListener {
            val nombre = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar Accesorio", "¿Estás segura de que quieres eliminar '$nombre'?", "Sí, eliminar") {
                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }

    private fun agregarFilaEstambre(contenedor: LinearLayout, nombre: String, costo: String, totales: String, usados: String) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_estambre, contenedor, false)
        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreEstambre)
        etNombre.setText(nombre)
        vistaFila.findViewById<EditText>(R.id.etCostoMadeja).setText(costo)
        vistaFila.findViewById<EditText>(R.id.etGramosTotales).setText(totales)
        vistaFila.findViewById<EditText>(R.id.etGramosUsados).setText(usados)

        vistaFila.findViewById<ImageButton>(R.id.btnEliminarEstambre).setOnClickListener {
            val nombreActual = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar estambre", "¿Estás segura de que quieres eliminar el estambre '$nombreActual'?", "Sí, eliminar") {
                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }

    private fun agregarFilaMaterial(contenedor: LinearLayout, nombre: String, costo: String) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_material_extra, contenedor, false)
        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreMaterial)
        etNombre.setText(nombre)
        vistaFila.findViewById<EditText>(R.id.etCostoMaterialExtra).setText(costo)

        vistaFila.findViewById<ImageButton>(R.id.btnEliminarMaterial).setOnClickListener {
            val nombreActual = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar accesorio", "¿Estás segura de que quieres eliminar '$nombreActual'?", "Sí, eliminar") {
                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }
}