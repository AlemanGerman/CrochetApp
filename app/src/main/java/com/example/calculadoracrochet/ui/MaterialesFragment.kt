package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import com.google.android.material.textfield.TextInputLayout

import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.validar
import com.example.calculadoracrochet.utils.errorSiVacio
import com.example.calculadoracrochet.utils.errorSiNoPositivo
import com.example.calculadoracrochet.utils.errorCantidadUsada
import com.example.calculadoracrochet.utils.aNumeroOInvalido
import com.example.calculadoracrochet.utils.comoTextoEditable

class MaterialesFragment : Fragment(R.layout.fragment_materiales) {

    private val viewModel: MinaViewModel by activityViewModels()
    private var ultimoClicTiempo: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val llContenedorEstambres = view.findViewById<LinearLayout>(R.id.llContenedorEstambres)
        val llContenedorExtra = view.findViewById<LinearLayout>(R.id.llContenedorMaterialesExtra)

        view.post {
            if (viewModel.listaEstambresGuardados.isEmpty()) {
                agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
            } else {
                viewModel.listaEstambresGuardados.forEach {
                    agregarFilaEstambre(
                        llContenedorEstambres,
                        it.nombre,
                        it.costoMadeja.comoTextoEditable(),
                        it.gramosTotales.comoTextoEditable(),
                        it.gramosUsados.comoTextoEditable()
                    )
                }
            }

            if (viewModel.listaAccesoriosGuardados.isNotEmpty()) {
                viewModel.listaAccesoriosGuardados.forEach {
                    if (it.esCalculado) {
                        agregarFilaAccesorioCalculado(
                            llContenedorExtra,
                            it.nombre,
                            it.costoPaquete.comoTextoEditable(),
                            it.cantidadTotal.comoTextoEditable(),
                            it.cantidadUsada.comoTextoEditable()
                        )
                    } else {
                        agregarFilaMaterial(llContenedorExtra, it.nombre, it.costo.comoTextoEditable())
                    }
                }
            }
        }

        val lottieGanchillo = requireActivity().findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieGanchillo)
        if (lottieGanchillo != null && !lottieGanchillo.isAnimating) {
            lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
            lottieGanchillo.progress = 0f
            lottieGanchillo.visibility = View.VISIBLE
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tiempoActual = SystemClock.elapsedRealtime()
                if (tiempoActual - ultimoClicTiempo < 400L) return
                ultimoClicTiempo = tiempoActual

                (requireActivity() as? MainActivity)?.regresarConGanchillo()
            }
        })

        view.findViewById<View>(R.id.btnAnadirEstambre).setOnClickListener {
            it.hapticClickPrincipal()
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "")
        }

        view.findViewById<View>(R.id.btnAnadirMaterial).setOnClickListener {
            it.hapticClickPrincipal()
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
            val tiempoActual = SystemClock.elapsedRealtime()
            if (tiempoActual - ultimoClicTiempo < 500L) return@setOnClickListener
            ultimoClicTiempo = tiempoActual

            var todoCorrecto = true
            var costoTotalEstambres = 0.0
            var costoTotalAccesorios = 0.0

            val estambresTemp = mutableListOf<EstambreGuardado>()
            val accesoriosTemp = mutableListOf<AccesorioGuardado>()

            if (llContenedorEstambres.childCount == 0) {
                view.hapticError()
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

                val costoMadeja = strCosto.aNumeroOInvalido()
                val gramosTotales = strTotales.aNumeroOInvalido()
                val gramosUsados = strUsados.aNumeroOInvalido()
                val estambre = EstambreGuardado(strNombre, costoMadeja, gramosTotales, gramosUsados)
                estambresTemp.add(estambre)

                todoCorrecto = tilNombre.validar(errorSiVacio(strNombre, "Ingresa el color/marca")) && todoCorrecto
                todoCorrecto = tilCostoMadeja.validar(errorSiNoPositivo(costoMadeja, "Debe ser mayor a $0")) && todoCorrecto
                todoCorrecto = tilGramosTotales.validar(errorSiNoPositivo(gramosTotales, "Debe ser mayor a 0g")) && todoCorrecto
                todoCorrecto = tilGramosUsados.validar(errorCantidadUsada(gramosUsados, gramosTotales, "Debe ser mayor a 0g")) && todoCorrecto

                if (costoMadeja > 0 && gramosTotales > 0 && gramosUsados > 0) {
                    costoTotalEstambres += estambre.costo
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

                    val costoMaterial = strCostoMat.aNumeroOInvalido()
                    accesoriosTemp.add(AccesorioGuardado(strNombreMat, costoMaterial, esCalculado = false))

                    todoCorrecto = tilNombreMaterial.validar(errorSiVacio(strNombreMat, "Ingresa un nombre")) && todoCorrecto
                    todoCorrecto = tilCostoMaterial.validar(errorSiNoPositivo(costoMaterial, "Debe ser mayor a $0")) && todoCorrecto
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

                    val costoTotal = strCosto.aNumeroOInvalido()
                    val cantTotal = strTotales.aNumeroOInvalido()
                    val cantUsada = strUsados.aNumeroOInvalido()

                    todoCorrecto = tilNombre.validar(errorSiVacio(strNombre, "Falta nombre")) && todoCorrecto
                    todoCorrecto = tilCostoTotal.validar(errorSiNoPositivo(costoTotal, "Debe ser > $0")) && todoCorrecto
                    todoCorrecto = tilTotalCant.validar(errorSiNoPositivo(cantTotal, "Debe ser > 0")) && todoCorrecto
                    todoCorrecto = tilUsadaCant.validar(errorCantidadUsada(cantUsada, cantTotal, "Debe ser > 0")) && todoCorrecto

                    if (costoTotal > 0 && cantTotal > 0 && cantUsada > 0) {
                        val costoFinal = (costoTotal / cantTotal) * cantUsada
                        costoTotalAccesorios += costoFinal
                        accesoriosTemp.add(
                            AccesorioGuardado(
                                nombre = strNombre,
                                costo = costoFinal,
                                esCalculado = true,
                                costoPaquete = costoTotal,
                                cantidadTotal = cantTotal,
                                cantidadUsada = cantUsada
                            )
                        )
                    }
                }
            }

            if (todoCorrecto) {
                view.hapticClickPrincipal()
                viewModel.costoMateriales = costoTotalEstambres + costoTotalAccesorios
                viewModel.listaEstambresGuardados.clear()
                viewModel.listaEstambresGuardados.addAll(estambresTemp)
                viewModel.listaAccesoriosGuardados.clear()
                viewModel.listaAccesoriosGuardados.addAll(accesoriosTemp)
                viewModel.guardarBorrador()

                requireActivity().findViewById<View>(R.id.lottieGanchillo)?.visibility = View.GONE

                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, PatronFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                view.hapticError()
            }
        }
    }

    private fun agregarFilaAccesorioCalculado(
        contenedor: LinearLayout,
        nombreSugerido: String = "",
        costoPaquete: String = "",
        cantidadTotal: String = "",
        cantidadUsada: String = ""
    ) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_accesorio_calculado, contenedor, false)
        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreAccCalc)
        val etCosto = vistaFila.findViewById<EditText>(R.id.etCostoTotalAcc)
        val etTotales = vistaFila.findViewById<EditText>(R.id.etCantidadTotalAcc)
        val etUsados = vistaFila.findViewById<EditText>(R.id.etCantidadUsadaAcc)

        if (nombreSugerido.isNotEmpty()) etNombre.setText(nombreSugerido)
        if (costoPaquete.isNotEmpty()) etCosto.setText(costoPaquete)
        if (cantidadTotal.isNotEmpty()) etTotales.setText(cantidadTotal)
        if (cantidadUsada.isNotEmpty()) etUsados.setText(cantidadUsada)

        etTotales.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etUsados.requestFocus()
                true
            } else false
        }

        vistaFila.findViewById<ImageButton>(R.id.btnEliminarAccCalc).setOnClickListener {
            it.hapticEliminar()
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
        val etCosto = vistaFila.findViewById<EditText>(R.id.etCostoMadeja)
        val etTotales = vistaFila.findViewById<EditText>(R.id.etGramosTotales)
        val etUsados = vistaFila.findViewById<EditText>(R.id.etGramosUsados)

        etNombre.setText(nombre)
        etCosto.setText(costo)
        etTotales.setText(totales)
        etUsados.setText(usados)

        etTotales.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etUsados.requestFocus()
                true
            } else false
        }

        vistaFila.findViewById<ImageButton>(R.id.btnEliminarEstambre).setOnClickListener {
            it.hapticEliminar()
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
            it.hapticEliminar()
            val nombreActual = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar accesorio", "¿Estás segura de que quieres eliminar '$nombreActual'?", "Sí, eliminar") {
                contenedor.removeView(vistaFila)
            }
        }
        contenedor.addView(vistaFila)
    }
}