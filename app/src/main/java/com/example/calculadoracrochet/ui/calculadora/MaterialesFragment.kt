package com.example.calculadoracrochet.ui.calculadora

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.calculadoracrochet.utils.rebotar
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.AccesorioFavorito
import com.example.calculadoracrochet.data.EstambreFavorito
import com.example.calculadoracrochet.data.MaterialesFavoritosRepository
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.PantallaConGanchillo
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
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
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.animarCambiosDeLayout

class MaterialesFragment : Fragment(R.layout.fragment_materiales), PantallaConGanchillo {

    private val viewModel: CalculadoraViewModel by activityViewModels()
    private var ultimoClicTiempo: Long = 0L

    private var raiz: View? = null
    private var llEstambres: LinearLayout? = null
    private var llExtras: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val llContenedorEstambres = view.findViewById<LinearLayout>(R.id.llContenedorEstambres)
        val llContenedorExtra = view.findViewById<LinearLayout>(R.id.llContenedorMaterialesExtra)

        llEstambres = llContenedorEstambres
        llExtras = llContenedorExtra
        raiz = view

        EncabezadoPaso.configurar(
            raiz = view,
            paso = 1,
            titulo = "Materiales",
            subtitulo = "¿Qué materiales vas a usar?",
            patron = R.drawable.encabezado_materiales,
            patronSiguiente = R.drawable.encabezado_patron,
            alVolver = { requireActivity().onBackPressedDispatcher.onBackPressed() }
        )

        // Built right away (not posted) so the rows are already there while the screen comes in
        if (viewModel.calculo.estambres.isEmpty()) {
            agregarFilaEstambre(llContenedorEstambres, "", "", "", "", animar = false)
        } else {
            viewModel.calculo.estambres.forEach {
                agregarFilaEstambre(
                    llContenedorEstambres,
                    it.nombre,
                    it.costoMadeja.comoTextoEditable(),
                    it.gramosTotales.comoTextoEditable(),
                    it.gramosUsados.comoTextoEditable(),
                    animar = false
                )
            }
        }

        viewModel.calculo.accesorios.forEach {
            if (it.esCalculado) {
                agregarFilaAccesorioCalculado(
                    llContenedorExtra,
                    it.nombre,
                    it.costoPaquete.comoTextoEditable(),
                    it.cantidadTotal.comoTextoEditable(),
                    it.cantidadUsada.comoTextoEditable(),
                    animar = false
                )
            } else {
                agregarFilaMaterial(llContenedorExtra, it.nombre, it.costo.comoTextoEditable(), animar = false)
            }
        }
        actualizarTotal()

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

        val estambresFavoritos = MaterialesFavoritosRepository.estambres(requireContext())
        ponerChipsRapidos(
            view, R.id.tvRapidoEstambres, R.id.hsvRapidoEstambres, R.id.cgRapidoEstambres,
            etiquetas = estambresFavoritos.map { it.etiqueta },
            pista = "Guarda tus estambres en Ajustes › Mis materiales para añadirlos con un toque."
        ) { i -> agregarEstambreRapido(llContenedorEstambres, estambresFavoritos[i]) }

        val accesoriosFavoritos = MaterialesFavoritosRepository.accesorios(requireContext())
        ponerChipsRapidos(
            view, R.id.tvRapidoAccesorios, R.id.hsvRapidoAccesorios, R.id.cgRapidoAccesorios,
            etiquetas = accesoriosFavoritos.map { it.etiqueta },
            pista = "Guarda relleno, ojos y más en Ajustes › Mis materiales para añadirlos con un toque."
        ) { i -> agregarAccesorioRapido(llContenedorExtra, accesoriosFavoritos[i]) }

        view.findViewById<View>(R.id.btnAnadirMaterial).setOnClickListener {
            it.hapticClickPrincipal()
            DialogUtils.mostrarOpciones(
                context = requireContext(),
                titulo = "¿Cómo lo calculamos?",
                textoOpcion1 = "Precio directo",
                iconoOpcion1 = R.drawable.ic_dinero,
                textoOpcion2 = "Por cantidad usada",
                iconoOpcion2 = R.drawable.ic_numbers,
                accionOpcion1 = { agregarFilaMaterial(llContenedorExtra, "", "") },
                accionOpcion2 = { agregarFilaAccesorioCalculado(llContenedorExtra, "") }
            )
        }

        view.findViewById<View>(R.id.btnContinuarPaso).setOnClickListener {
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
                viewModel.calculo.costoMateriales = costoTotalEstambres + costoTotalAccesorios
                viewModel.calculo.estambres.clear()
                viewModel.calculo.estambres.addAll(estambresTemp)
                viewModel.calculo.accesorios.clear()
                viewModel.calculo.accesorios.addAll(accesoriosTemp)
                viewModel.guardarBorrador()

                (requireActivity() as MainActivity).navegarDeslizando(PatronFragment())
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
        cantidadUsada: String = "",
        animar: Boolean = true
    ) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_accesorio_calculado, contenedor, false)
        vistaFila.isSaveFromParentEnabled = false
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

        vistaFila.findViewById<View>(R.id.btnEliminarAccCalc).setOnClickListener {
            it.hapticEliminar()
            val nombre = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar accesorio", "¿Estás segura de que quieres eliminar '$nombre'?", "Sí, eliminar") {
                quitarFila(contenedor, vistaFila)
            }
        }
        seguirCosto(vistaFila, etCosto, etTotales, etUsados)
        ponerFila(contenedor, vistaFila, animar)
    }

    private fun agregarFilaEstambre(
        contenedor: LinearLayout,
        nombre: String,
        costo: String,
        totales: String,
        usados: String,
        animar: Boolean = true
    ) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_estambre, contenedor, false)
        // The rows share view ids: they never save their own state, the draft restores them
        vistaFila.isSaveFromParentEnabled = false
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

        vistaFila.findViewById<View>(R.id.btnEliminarEstambre).setOnClickListener {
            it.hapticEliminar()
            val nombreActual = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar estambre", "¿Estás segura de que quieres eliminar el estambre '$nombreActual'?", "Sí, eliminar") {
                quitarFila(contenedor, vistaFila)
            }
        }
        seguirCosto(vistaFila, etCosto, etTotales, etUsados)
        ponerFila(contenedor, vistaFila, animar)
    }

    /**
     * One chip per saved yarn/accessory under its section; [alTocar] gets the chip's index. Without
     * saved ones the row is hidden and the label becomes a hint pointing to Ajustes.
     */
    private fun ponerChipsRapidos(
        vista: View,
        idEtiqueta: Int,
        idDesplazable: Int,
        idGrupo: Int,
        etiquetas: List<String>,
        pista: String,
        alTocar: (Int) -> Unit
    ) {
        val tvEtiqueta = vista.findViewById<TextView>(idEtiqueta)
        val desplazable = vista.findViewById<View>(idDesplazable)
        val grupo = vista.findViewById<ChipGroup>(idGrupo)
        if (etiquetas.isEmpty()) {
            tvEtiqueta.text = pista
            tvEtiqueta.textSize = 13f
            desplazable.visibility = View.GONE
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        etiquetas.forEachIndexed { i, etiqueta ->
            val chip = inflater.inflate(R.layout.chip_carpeta, grupo, false) as Chip
            chip.text = etiqueta
            chip.isCheckable = false
            chip.setChipIconResource(R.drawable.ic_agregar)
            chip.chipIconSize = 18 * resources.displayMetrics.density
            chip.chipIconTint = ContextCompat.getColorStateList(requireContext(), R.color.morado_fuerte)
            chip.contentDescription = "Añadir $etiqueta"
            chip.setOnClickListener {
                it.hapticClickPrincipal()
                it.rebotar(1.06f)
                alTocar(i)
            }
            grupo.addView(chip)
        }
    }

    /**
     * Fills the first empty yarn row with [estambre] (or adds a row when none is empty) and puts the
     * cursor in its grams used, the only field left.
     */
    private fun agregarEstambreRapido(contenedor: LinearLayout, estambre: EstambreFavorito) {
        val campos = listOf(R.id.etNombreEstambre, R.id.etCostoMadeja, R.id.etGramosTotales, R.id.etGramosUsados)
        val vacia = (0 until contenedor.childCount).map { contenedor.getChildAt(it) }
            .firstOrNull { fila -> campos.all { fila.findViewById<EditText>(it).text.isNullOrBlank() } }

        val fila = if (vacia != null) {
            vacia.findViewById<EditText>(R.id.etNombreEstambre).setText(estambre.nombre)
            vacia.findViewById<EditText>(R.id.etCostoMadeja).setText(estambre.costoMadeja.comoTextoEditable())
            vacia.findViewById<EditText>(R.id.etGramosTotales).setText(estambre.gramosMadeja.comoTextoEditable())
            vacia.rebotar(1.03f)
            vacia
        } else {
            agregarFilaEstambre(
                contenedor,
                estambre.nombre,
                estambre.costoMadeja.comoTextoEditable(),
                estambre.gramosMadeja.comoTextoEditable(),
                ""
            )
            contenedor.getChildAt(contenedor.childCount - 1)
        }
        enfocar(fila.findViewById(R.id.etGramosUsados))
    }

    /**
     * A per-piece accessory is added complete; one charged by package is added with the package
     * filled in and the cursor in the amount used.
     */
    private fun agregarAccesorioRapido(contenedor: LinearLayout, accesorio: AccesorioFavorito) {
        if (accesorio.porCantidad) {
            agregarFilaAccesorioCalculado(
                contenedor,
                accesorio.nombre,
                accesorio.costo.comoTextoEditable(),
                accesorio.cantidadPaquete!!.comoTextoEditable()
            )
            enfocar(contenedor.getChildAt(contenedor.childCount - 1).findViewById(R.id.etCantidadUsadaAcc))
        } else {
            agregarFilaMaterial(contenedor, accesorio.nombre, accesorio.costo.comoTextoEditable())
        }
    }

    private fun enfocar(campo: EditText) {
        campo.post {
            campo.requestFocus()
            ViewCompat.getWindowInsetsController(campo)?.show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun agregarFilaMaterial(contenedor: LinearLayout, nombre: String, costo: String, animar: Boolean = true) {
        val vistaFila = LayoutInflater.from(requireContext()).inflate(R.layout.item_material_extra, contenedor, false)
        vistaFila.isSaveFromParentEnabled = false
        val etNombre = vistaFila.findViewById<EditText>(R.id.etNombreMaterial)
        etNombre.setText(nombre)
        val etCosto = vistaFila.findViewById<EditText>(R.id.etCostoMaterialExtra)
        etCosto.setText(costo)
        etCosto.doAfterTextChanged { actualizarTotal() }

        vistaFila.findViewById<View>(R.id.btnEliminarMaterial).setOnClickListener {
            it.hapticEliminar()
            val nombreActual = etNombre.text.toString().trim().ifEmpty { "sin nombre" }
            DialogUtils.mostrarAlerta(requireContext(), "Eliminar accesorio", "¿Estás segura de que quieres eliminar '$nombreActual'?", "Sí, eliminar") {
                quitarFila(contenedor, vistaFila)
            }
        }
        ponerFila(contenedor, vistaFila, animar)
    }

    /** Rows added by the user push the rest down smoothly; the initial ones just appear. */
    private fun ponerFila(contenedor: LinearLayout, fila: View, animar: Boolean) {
        if (animar) contenedor.animarCambiosDeLayout()
        contenedor.addView(fila)
        actualizarTotal()
    }

    private fun quitarFila(contenedor: LinearLayout, fila: View) {
        if (fila.parent !== contenedor) return
        contenedor.animarCambiosDeLayout()
        contenedor.removeView(fila)
        actualizarTotal()
    }

    /** The row shows the cost of the used part as its three numbers are typed. */
    private fun seguirCosto(fila: View, etCosto: EditText, etTotal: EditText, etUsada: EditText) {
        val tvCosto = fila.findViewById<TextView>(R.id.tvCostoFila)
        fun refrescar() {
            val costo = costoDeFila(etCosto, etTotal, etUsada)
            tvCosto.text = costo?.comoDinero() ?: "—"
            actualizarTotal()
        }
        etCosto.doAfterTextChanged { refrescar() }
        etTotal.doAfterTextChanged { refrescar() }
        etUsada.doAfterTextChanged { refrescar() }
        tvCosto.text = costoDeFila(etCosto, etTotal, etUsada)?.comoDinero() ?: "—"
    }

    private fun costoDeFila(etCosto: EditText, etTotal: EditText, etUsada: EditText): Double? =
        CostoEnVivo.porcionUsada(
            etCosto.text.toString().trim().toDoubleOrNull(),
            etTotal.text.toString().trim().toDoubleOrNull(),
            etUsada.text.toString().trim().toDoubleOrNull()
        )

    /** Sum of every row that already has valid numbers, in the bottom bar. */
    private fun actualizarTotal() {
        val vista = raiz ?: return
        var total = 0.0
        llEstambres?.let { contenedor ->
            for (i in 0 until contenedor.childCount) {
                val fila = contenedor.getChildAt(i)
                total += costoDeFila(
                    fila.findViewById(R.id.etCostoMadeja),
                    fila.findViewById(R.id.etGramosTotales),
                    fila.findViewById(R.id.etGramosUsados)
                ) ?: 0.0
            }
        }
        llExtras?.let { contenedor ->
            for (i in 0 until contenedor.childCount) {
                val fila = contenedor.getChildAt(i)
                val directo = fila.findViewById<EditText>(R.id.etCostoMaterialExtra)
                total += if (directo != null) {
                    directo.text.toString().trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
                } else {
                    costoDeFila(
                        fila.findViewById(R.id.etCostoTotalAcc),
                        fila.findViewById(R.id.etCantidadTotalAcc),
                        fila.findViewById(R.id.etCantidadUsadaAcc)
                    ) ?: 0.0
                }
            }
        }
        EncabezadoPaso.mostrarMonto(vista, "Materiales", total.comoDinero())
    }

    override fun onDestroyView() {
        raiz = null
        llEstambres = null
        llExtras = null
        super.onDestroyView()
    }
}