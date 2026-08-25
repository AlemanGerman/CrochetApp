package com.example.calculadoracrochet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

class MaterialesFragment : Fragment(R.layout.fragment_materiales) {

    private val viewModel: CalculadoraViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val llContenedorEstambres = view.findViewById<LinearLayout>(R.id.llContenedorEstambres)
        val llContenedorExtra = view.findViewById<LinearLayout>(R.id.llContenedorMaterialesExtra)

        val btnAnadirEstambre = view.findViewById<View>(R.id.btnAnadirEstambre)
        val btnAnadirMaterial = view.findViewById<View>(R.id.btnAnadirMaterial)

        if (viewModel.listaEstambresGuardados.isEmpty()) {
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
        } else {
            viewModel.listaEstambresGuardados.forEach {
                agregarFilaEstambre(llContenedorEstambres, it.nombre, it.costo, it.totales, it.usados)
            }
        }

        if (viewModel.listaAccesoriosGuardados.isEmpty()) {
            agregarFilaMaterial(llContenedorExtra, "", "")
        } else {
            viewModel.listaAccesoriosGuardados.forEach {
                agregarFilaMaterial(llContenedorExtra, it.nombre, it.costo)
            }
        }

        btnAnadirEstambre.setOnClickListener {
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
        }

        btnAnadirMaterial.setOnClickListener {
            agregarFilaMaterial(llContenedorExtra, "", "")
        }

        view.findViewById<View>(R.id.btnSiguienteMateriales).setOnClickListener {

            var todoCorrecto = true
            var costoTotalEstambres = 0.0
            var costoTotalAccesorios = 0.0

            val estambresTemp = mutableListOf<EstambreGuardado>()
            val accesoriosTemp = mutableListOf<AccesorioGuardado>()

            if (llContenedorEstambres.childCount == 0) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Faltan materiales")
                    .setMessage("Debes agregar por lo menos un estambre para poder hacer el cálculo.")
                    .setPositiveButton("Entendido") { dialog, _ -> dialog.dismiss() }
                    .show()
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
                    val costoFilaEstambre = (costoMadeja / gramosTotales) * gramosUsados
                    costoTotalEstambres += costoFilaEstambre
                }
            }

            for (i in 0 until llContenedorExtra.childCount) {
                val fila = llContenedorExtra.getChildAt(i)

                val tilNombreMaterial = fila.findViewById<TextInputLayout>(R.id.tilNombreMaterial)
                val tilCostoMaterial = fila.findViewById<TextInputLayout>(R.id.tilCostoMaterialExtra)

                val strNombreMat = fila.findViewById<EditText>(R.id.etNombreMaterial).text.toString().trim()
                val strCostoMat = fila.findViewById<EditText>(R.id.etCostoMaterialExtra).text.toString()

                accesoriosTemp.add(AccesorioGuardado(strNombreMat, strCostoMat))

                val costoMaterial = strCostoMat.toDoubleOrNull() ?: -1.0

                if (strNombreMat.isEmpty()) { tilNombreMaterial?.error = "Ingresa un nombre"; todoCorrecto = false } else { tilNombreMaterial?.error = null }
                if (costoMaterial <= 0.0) { tilCostoMaterial?.error = "Debe ser mayor a $0"; todoCorrecto = false } else { tilCostoMaterial?.error = null }

                if (costoMaterial > 0.0) {
                    costoTotalAccesorios += costoMaterial
                }
            }

            if (todoCorrecto) {
                viewModel.costoMateriales = costoTotalEstambres + costoTotalAccesorios

                viewModel.listaEstambresGuardados.clear()
                viewModel.listaEstambresGuardados.addAll(estambresTemp)

                viewModel.listaAccesoriosGuardados.clear()
                viewModel.listaAccesoriosGuardados.addAll(accesoriosTemp)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, PatronFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun agregarFilaEstambre(contenedor: LinearLayout, nombre: String, costo: String, totales: String, usados: String) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_estambre, contenedor, false)

        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreEstambre)
        val etCosto = vistaFila.findViewById<EditText>(R.id.etCostoMadeja)
        val etTotales = vistaFila.findViewById<EditText>(R.id.etGramosTotales)
        val etUsados = vistaFila.findViewById<EditText>(R.id.etGramosUsados)
        val btnEliminar = vistaFila.findViewById<ImageButton>(R.id.btnEliminarEstambre)

        etNombre.setText(nombre)
        etCosto.setText(costo)
        etTotales.setText(totales)
        etUsados.setText(usados)

        btnEliminar.setOnClickListener {
            val nombreActual = etNombre.text.toString().trim()
            val nombreAMostrar = if (nombreActual.isNotEmpty()) nombreActual else "sin nombre"

            DialogUtils.mostrarModal(
                context = requireContext(),
                titulo = "Eliminar estambre",
                mensaje = "¿Estás segura de que quieres eliminar el estambre '$nombreAMostrar'?",
                textoConfirmar = "Sí, eliminar",
                iconoResId = R.drawable.ic_eliminar
            ) {

                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }

    private fun agregarFilaMaterial(contenedor: LinearLayout, nombre: String, costo: String) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_material_extra, contenedor, false)

        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreMaterial)
        val etCosto = vistaFila.findViewById<EditText>(R.id.etCostoMaterialExtra)
        val btnEliminar = vistaFila.findViewById<ImageButton>(R.id.btnEliminarMaterial)

        etNombre.setText(nombre)
        etCosto.setText(costo)

        btnEliminar.setOnClickListener {
            val nombreActual = etNombre.text.toString().trim()
            val nombreAMostrar = if (nombreActual.isNotEmpty()) nombreActual else "sin nombre"

            DialogUtils.mostrarModal(
                context = requireContext(),
                titulo = "Eliminar estambre",
                mensaje = "¿Estás segura de que quieres eliminar el estambre '$nombreAMostrar'?",
                textoConfirmar = "Sí, eliminar",
                iconoResId = R.drawable.ic_eliminar

            ) {
                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }
}