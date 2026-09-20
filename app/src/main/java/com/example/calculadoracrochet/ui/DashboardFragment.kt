package com.example.calculadoracrochet.ui

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.DatePicker
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.adapters.ProyectosAdapter
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.firebase.firestore.FirebaseFirestore
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CriterioOrden {
    FECHA,
    PRECIO,
    FOTO,
    NOMBRE
}

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: MinaViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter

    private var criterioActual: CriterioOrden = CriterioOrden.FECHA
    private var esOrdenDescendente: Boolean = true
    private var carpetaSeleccionada = "Todos"

    private var proyectoEditandoID: String? = null
    private var proyectoEditando: ProyectoCrochet? = null
    private var vistaFotoEnEdicion: WeakReference<ImageView>? = null

    private var animadorSelector: ValueAnimator? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val contexto = context
        if (uri != null && proyectoEditandoID != null && contexto != null) {
            val proyectoActual = proyectoEditando
            val referenciaVista = vistaFotoEnEdicion
            val fotoAnterior = proyectoActual?.fotoUri

            try {
                referenciaVista?.get()?.takeIf { it.isAttachedToWindow }?.apply {
                    setImageURI(uri)
                    imageTintList = null
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (proyectoActual != null) {
                viewModel.actualizarFotoProyecto(contexto, proyectoActual, uri.toString()) { actualizada ->
                    if (!isAdded) return@actualizarFotoProyecto

                    if (actualizada) {
                        filtrarYOrdenarLista()
                    } else {
                        referenciaVista?.get()?.takeIf { it.isAttachedToWindow }?.load(fotoAnterior) {
                            crossfade(true)
                        }
                        Toast.makeText(
                            requireContext(),
                            "No se pudo respaldar la foto. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        proyectoEditandoID = null
        proyectoEditando = null
        vistaFotoEnEdicion = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lottieGanchillo = requireActivity().findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieGanchillo)
        if (lottieGanchillo != null && !lottieGanchillo.isAnimating) {
            lottieGanchillo.post {
                lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
                lottieGanchillo.pauseAnimation()
                lottieGanchillo.progress = 0f
                lottieGanchillo.visibility = View.VISIBLE
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                DialogUtils.mostrarAlerta(
                    context = requireContext(),
                    titulo = "¿Salir de la aplicación?",
                    mensaje = "¿Acaso me odias 😿?",
                    textoConfirmar = "Sí, salir",
                    iconoResId = R.drawable.ic_sad
                ) {
                    requireActivity().finish()
                }
            }
        })

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProyectosAdapter(
            listaProyectos = emptyList<ProyectoCrochet>(),
            onItemClick = { proyectoClickeado -> mostrarDetalleProyecto(proyectoClickeado) },
            onEditCarpetaClick = { proyectoParaEditar -> mostrarModalCarpetas(proyectoParaEditar) }
        )
        recyclerView.adapter = adapter

        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            filtrarYOrdenarLista()
        }

        val etBuscar = view.findViewById<EditText>(R.id.etBuscarProyecto)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filtrarYOrdenarLista() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        view.findViewById<MaterialButton>(R.id.btnTipoOrden).setOnClickListener { vistaBoton ->
            mostrarMenuCriterio(vistaBoton)
        }

        val btnDireccion = view.findViewById<MaterialButton>(R.id.btnDireccionOrden)
        btnDireccion.setIconResource(R.drawable.ic_flecha_abajo)
        actualizarRotacionFlecha(animar = false)

        btnDireccion.setOnClickListener {
            esOrdenDescendente = !esOrdenDescendente
            actualizarRotacionFlecha(animar = true)
            filtrarYOrdenarLista()
            view.findViewById<RecyclerView>(R.id.rvProyectos)?.smoothScrollToPosition(0)
        }

        view.findViewById<ImageButton>(R.id.btnOpciones).setOnClickListener {
            mostrarBottomSheetOpciones()
        }

        view.findViewById<MaterialButton>(R.id.btnNuevoProyecto).setOnClickListener {
            viewModel.limpiarCalculoActual()
            (requireActivity() as MainActivity).navegarConGanchillo(MaterialesFragment())
        }

        configurarDockCarpetas(view)

        filtrarYOrdenarLista()
    }

    private fun actualizarRotacionFlecha(animar: Boolean = true) {
        val btnDireccion = view?.findViewById<MaterialButton>(R.id.btnDireccionOrden) ?: return
        val anguloDestino = if (esOrdenDescendente) 0f else 180f

        if (animar) {
            btnDireccion.animate()
                .rotation(anguloDestino)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        } else {
            btnDireccion.rotation = anguloDestino
        }
    }

    private fun mostrarMenuCriterio(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val menuView = inflater.inflate(R.layout.popup_ordenar, null)

        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        val mapaChecks = mapOf(
            CriterioOrden.FECHA to menuView.findViewById<View>(R.id.checkCriterioFecha),
            CriterioOrden.PRECIO to menuView.findViewById<View>(R.id.checkCriterioPrecio),
            CriterioOrden.FOTO to menuView.findViewById<View>(R.id.checkCriterioFoto),
            CriterioOrden.NOMBRE to menuView.findViewById<View>(R.id.checkCriterioNombre)
        )

        val mapaTextos = mapOf(
            CriterioOrden.FECHA to menuView.findViewById<TextView>(R.id.tvCriterioFecha),
            CriterioOrden.PRECIO to menuView.findViewById<TextView>(R.id.tvCriterioPrecio),
            CriterioOrden.FOTO to menuView.findViewById<TextView>(R.id.tvCriterioFoto),
            CriterioOrden.NOMBRE to menuView.findViewById<TextView>(R.id.tvCriterioNombre)
        )

        mapaChecks[criterioActual]?.visibility = View.VISIBLE
        mapaTextos[criterioActual]?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
            setTypeface(typeface, Typeface.BOLD)
        }

        fun seleccionarCriterio(nuevoCriterio: CriterioOrden) {
            if (criterioActual != nuevoCriterio) {
                criterioActual = nuevoCriterio
                filtrarYOrdenarLista()
                view?.findViewById<RecyclerView>(R.id.rvProyectos)?.smoothScrollToPosition(0)
            }
            popupWindow.dismiss()
        }

        menuView.findViewById<View>(R.id.opcCriterioFecha).setOnClickListener { seleccionarCriterio(CriterioOrden.FECHA) }
        menuView.findViewById<View>(R.id.opcCriterioPrecio).setOnClickListener { seleccionarCriterio(CriterioOrden.PRECIO) }
        menuView.findViewById<View>(R.id.opcCriterioFoto).setOnClickListener { seleccionarCriterio(CriterioOrden.FOTO) }
        menuView.findViewById<View>(R.id.opcCriterioNombre).setOnClickListener { seleccionarCriterio(CriterioOrden.NOMBRE) }

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val anchoMenu = menuView.measuredWidth
        val anchoBoton = anchorView.width
        val offsetX = -(anchoMenu - anchoBoton)

        popupWindow.showAsDropDown(anchorView, offsetX, 12)
    }

    private fun filtrarYOrdenarLista() {
        val etBuscar = view?.findViewById<EditText>(R.id.etBuscarProyecto)
        val textoBusqueda = etBuscar?.text?.toString()?.lowercase()?.trim() ?: ""

        var listaResultante = viewModel.historialProyectos.filter { proyecto ->
            val noEliminado = proyecto.fechaEliminado == null && !proyecto.eliminado
            val coincideNombre = proyecto.nombre.lowercase().contains(textoBusqueda)
            noEliminado && coincideNombre
        }

        if (carpetaSeleccionada != "Todos") {
            listaResultante = listaResultante.filter { proyecto ->
                (proyecto.carpetas ?: emptyList()).contains(carpetaSeleccionada)
            }
        }

        listaResultante = when (criterioActual) {
            CriterioOrden.FECHA -> {
                if (esOrdenDescendente) {
                    listaResultante.sortedByDescending { it.fechaGuardado }
                } else {
                    listaResultante.sortedBy { it.fechaGuardado }
                }
            }
            CriterioOrden.PRECIO -> {
                if (esOrdenDescendente) {
                    listaResultante.sortedWith(
                        compareByDescending<ProyectoCrochet> { it.precioFinal }
                            .thenByDescending { it.fechaGuardado }
                    )
                } else {
                    listaResultante.sortedWith(
                        compareBy<ProyectoCrochet> { it.precioFinal }
                            .thenByDescending { it.fechaGuardado }
                    )
                }
            }
            CriterioOrden.FOTO -> {
                if (esOrdenDescendente) {
                    listaResultante.sortedWith(
                        compareByDescending<ProyectoCrochet> { !it.fotoUri.isNullOrEmpty() }
                            .thenByDescending { it.fechaGuardado }
                    )
                } else {
                    listaResultante.sortedWith(
                        compareBy<ProyectoCrochet> { !it.fotoUri.isNullOrEmpty() }
                            .thenByDescending { it.fechaGuardado }
                    )
                }
            }
            CriterioOrden.NOMBRE -> {
                if (esOrdenDescendente) {
                    listaResultante.sortedWith(
                        compareBy<ProyectoCrochet> { it.nombre.lowercase().trim() }
                            .thenByDescending { it.fechaGuardado }
                    )
                } else {
                    listaResultante.sortedWith(
                        compareByDescending<ProyectoCrochet> { it.nombre.lowercase().trim() }
                            .thenByDescending { it.fechaGuardado }
                    )
                }
            }
        }

        adapter.actualizarLista(listaResultante)
    }

    private fun configurarDockCarpetas(view: View) {
        val dockCarpetas = view.findViewById<View>(R.id.dockCarpetas)
        val llContenedor = view.findViewById<LinearLayout>(R.id.llCarpetasDashboard)
        val indicador = view.findViewById<View>(R.id.indicadorSelector)
        val hsvCarpetas = view.findViewById<HorizontalScrollView>(R.id.hsvCarpetas)

        dockCarpetas.clipToOutline = true

        indicador.elevation = 0f
        llContenedor.elevation = 2f
        llContenedor.bringToFront()

        llContenedor.removeAllViews()
        val vistasCarpetas = mutableListOf<View>()

        CarpetasConfig.listaCarpetas.forEach { carpetaInfo ->
            val itemView = layoutInflater.inflate(R.layout.item_carpeta_dashboard, llContenedor, false)

            val ivIcono = itemView.findViewById<ImageView>(R.id.ivIconoCarpeta)
            val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreCarpeta)

            ivIcono.setImageResource(carpetaInfo.iconoRes)
            tvNombre.text = carpetaInfo.nombre
            itemView.alpha = 1.0f

            val esLaSeleccionada = carpetaInfo.nombre == carpetaSeleccionada
            actualizarColorTexto(tvNombre, esLaSeleccionada)

            itemView.setOnClickListener {
                if (carpetaSeleccionada == carpetaInfo.nombre) return@setOnClickListener

                carpetaSeleccionada = carpetaInfo.nombre

                vistasCarpetas.forEach { vista ->
                    val tv = vista.findViewById<TextView>(R.id.tvNombreCarpeta)
                    actualizarColorTexto(tv, tv.text.toString() == carpetaSeleccionada)
                }

                animarSelectorHacia(itemView, indicador, hsvCarpetas)

                filtrarYOrdenarLista()
                view.findViewById<RecyclerView>(R.id.rvProyectos)?.smoothScrollToPosition(0)
            }

            vistasCarpetas.add(itemView)
            llContenedor.addView(itemView)
        }

        llContenedor.post {
            if (!isAdded) return@post
            val indiceInicial = CarpetasConfig.listaCarpetas.indexOfFirst { it.nombre == carpetaSeleccionada }
            .coerceAtLeast(0)
            val vistaInicial = llContenedor.getChildAt(indiceInicial) ?: return@post

            val layoutParams = indicador.layoutParams
            layoutParams.width = vistaInicial.width
            layoutParams.height = vistaInicial.height
            indicador.layoutParams = layoutParams

            indicador.translationX = vistaInicial.x
            indicador.translationY = vistaInicial.y
            indicador.visibility = View.VISIBLE
        }
    }

    private fun animarSelectorHacia(
        vistaDestino: View,
        indicador: View,
        hsv: HorizontalScrollView?
    ) {
        animadorSelector?.cancel()

        val inicioX = indicador.translationX
        val finX = vistaDestino.x

        val inicioAncho = indicador.width
        val finAncho = vistaDestino.width

        val inicioAlto = indicador.height
        val finAlto = vistaDestino.height

        animadorSelector = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                indicador.translationX = inicioX + (finX - inicioX) * fraction

                val anchoActual = (inicioAncho + (finAncho - inicioAncho) * fraction).toInt()
                val altoActual = (inicioAlto + (finAlto - inicioAlto) * fraction).toInt()

                val params = indicador.layoutParams
                if (params.width != anchoActual || params.height != altoActual) {
                    params.width = anchoActual
                    params.height = altoActual
                    indicador.layoutParams = params
                }
            }
            start()
        }

        hsv?.let { scroll ->
            scroll.post {
                val centroX = (vistaDestino.left + vistaDestino.right) / 2 - scroll.width / 2
                scroll.smoothScrollTo(centroX.coerceAtLeast(0), 0)
            }
        }
    }

    private fun actualizarColorTexto(tvNombre: TextView, estaSeleccionada: Boolean) {
        if (estaSeleccionada) {
            tvNombre.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
        } else {
            tvNombre.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_light))
        }
    }

    private fun mostrarBottomSheetOpciones() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_dashboard_opciones, null)
        bottomSheetDialog.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.itemOpcionEstadisticas).setOnClickListener {
            bottomSheetDialog.dismiss()
            navegarHaciaFragmento(EstadisticasFragment())
        }

        sheetView.findViewById<View>(R.id.itemOpcionPapelera).setOnClickListener {
            bottomSheetDialog.dismiss()
            navegarHaciaFragmento(PapeleraFragment())
        }

        bottomSheetDialog.show()
    }

    private fun navegarHaciaFragmento(fragment: Fragment) {
        requireActivity().findViewById<View>(R.id.lottieGanchillo)?.visibility = View.GONE

        parentFragmentManager.beginTransaction()
        .setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        .replace(R.id.fragmentContainer, fragment)
        .addToBackStack(null)
        .commit()
    }

    private fun mostrarModalCarpetas(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_carpetas, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llCarpetasContenedor)
        llContenedor.removeAllViews()

        val checkboxesMap = mutableMapOf<String, MaterialCheckBox>()

        CarpetasConfig.obtenerNombresSinTodos().forEach { nombreCarpeta ->
            val carpetaInfo = CarpetasConfig.listaCarpetas.find { it.nombre == nombreCarpeta }
            val cb = MaterialCheckBox(requireContext()).apply {
                text = nombreCarpeta
                setTextColor(ContextCompat.getColor(context, R.color.black))
                carpetaInfo?.let {
                    buttonTintList = ContextCompat.getColorStateList(context, it.colorRes)
                }
                isChecked = (proyecto.carpetas ?: emptyList()).contains(nombreCarpeta)
            }
            checkboxesMap[nombreCarpeta] = cb
            llContenedor.addView(cb)
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarCarpetas).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarCarpetas).setOnClickListener {
            val nuevasCarpetas = checkboxesMap.filter { it.value.isChecked }.keys.toList()
            proyecto.carpetas = nuevasCarpetas
            viewModel.actualizarCarpetasProyecto(proyecto)
            filtrarYOrdenarLista()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarDetalleProyecto(proyecto: ProyectoCrochet) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_detalle_proyecto, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val cvFotoContenedor = dialogView.findViewById<View>(R.id.cvDetalleFotoContenedor)
        val ivFoto = dialogView.findViewById<ImageView>(R.id.ivDetalleFoto)

        val tieneFoto = !proyecto.fotoUri.isNullOrBlank() &&
                proyecto.fotoUri != "null" &&
                proyecto.fotoUri != "undefined"

        if (tieneFoto) {
            cvFotoContenedor.visibility = View.VISIBLE
            ivFoto.load(proyecto.fotoUri) {
                crossfade(true)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
                error(android.R.drawable.ic_menu_camera)
            }
        } else {
            cvFotoContenedor.visibility = View.GONE
        }

        fun refrescarImagenLocal() {
            cvFotoContenedor.visibility = View.VISIBLE
            if (!proyecto.fotoUri.isNullOrEmpty()) {
                ivFoto.load(proyecto.fotoUri) { crossfade(true) }
                ivFoto.scaleType = ImageView.ScaleType.CENTER_CROP
                ivFoto.imageTintList = null
            } else {
                ivFoto.setImageResource(R.drawable.ic_photo)
                ivFoto.imageTintList = ColorStateList.valueOf(Color.GRAY)
            }
        }
        refrescarImagenLocal()

        cvFotoContenedor.setOnClickListener {
            if (proyecto.fotoUri.isNullOrEmpty()) {
                proyectoEditando = proyecto
                proyectoEditandoID = proyecto.id
                vistaFotoEnEdicion = WeakReference(ivFoto)
                pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
            } else {
                DialogUtils.mostrarOpciones(
                    context = requireContext(),
                    titulo = "Opciones de foto",
                    textoOpcion1 = "Cambiar foto",
                    textoOpcion2 = "Eliminar foto",
                    accionOpcion1 = {
                        proyectoEditando = proyecto
                        proyectoEditandoID = proyecto.id
                        vistaFotoEnEdicion = WeakReference(ivFoto)
                        pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
                    },
                    accionOpcion2 = {
                        viewModel.eliminarFotoProyecto(proyecto) { eliminada ->
                            if (!isAdded) return@eliminarFotoProyecto
                            if (eliminada) {
                                refrescarImagenLocal()
                                filtrarYOrdenarLista()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "No se pudo eliminar la foto. Inténtalo de nuevo.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        val tvNombre = dialogView.findViewById<TextView>(R.id.tvDetalleNombre)
        tvNombre.text = proyecto.nombre

        tvNombre.setOnClickListener {
            DialogUtils.mostrarInput(
                context = requireContext(),
                titulo = "Cambiar nombre",
                hint = "Nombre del proyecto",
                textoActual = proyecto.nombre
            ) { nuevoNombre ->
                FirebaseFirestore.getInstance().collection("proyectos_mina").document(proyecto.id).update("nombre", nuevoNombre)
                proyecto.nombre = nuevoNombre
                tvNombre.text = nuevoNombre
                filtrarYOrdenarLista()
            }
        }

        val formatoFecha = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "MX"))
        val tvFecha = dialogView.findViewById<TextView>(R.id.tvDetalleFecha)
        tvFecha.text = formatoFecha.format(Date(proyecto.fechaGuardado))

        tvFecha.setOnClickListener {
            val (dialogFecha, fechaDialogView) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_seleccionar_fecha)
            val datePicker = fechaDialogView.findViewById<DatePicker>(R.id.datePickerSpinner)
            val calendario = Calendar.getInstance()
            calendario.timeInMillis = proyecto.fechaGuardado

            datePicker.updateDate(
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            )

            fechaDialogView.findViewById<MaterialButton>(R.id.btnCancelarFecha).setOnClickListener { dialogFecha.dismiss() }
            fechaDialogView.findViewById<MaterialButton>(R.id.btnAceptarFecha).setOnClickListener {
                val nuevaFecha = Calendar.getInstance()
                nuevaFecha.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                val tiempoMs = nuevaFecha.timeInMillis

                FirebaseFirestore.getInstance().collection("proyectos_mina").document(proyecto.id).update("fechaGuardado", tiempoMs)
                proyecto.fechaGuardado = tiempoMs
                tvFecha.text = formatoFecha.format(Date(tiempoMs))
                filtrarYOrdenarLista()
                dialogFecha.dismiss()
            }
            dialogFecha.show()
        }

        dialogView.findViewById<TextView>(R.id.tvDetalleTotal).text = String.format("Total: $%.2f", proyecto.precioFinal)
        dialogView.findViewById<TextView>(R.id.tvDetallePatron).text = String.format("$%.2f", proyecto.costoDiseno)
        dialogView.findViewById<TextView>(R.id.tvDetalleEmpaque).text = String.format("$%.2f", proyecto.costoEmpaque)

        val subtotal = proyecto.costoMateriales + proyecto.costoDiseno + proyecto.costoManoObra + proyecto.costoEmpaque
        val ganancia = subtotal * (proyecto.margenGanancia / 100)
        dialogView.findViewById<TextView>(R.id.tvDetalleGanancia).text = String.format("+ Ganancia Extra (%d%%): $%.2f", proyecto.margenGanancia.toInt(), ganancia)

        val llToggleMateriales = dialogView.findViewById<LinearLayout>(R.id.llToggleMateriales)
        val tvCostoMatResumen = dialogView.findViewById<TextView>(R.id.tvCostoMaterialesResumen)
        val tvDesgloseMat = dialogView.findViewById<TextView>(R.id.tvDesgloseMateriales)
        tvCostoMatResumen.text = String.format("$%.2f", proyecto.costoMateriales)

        val estambres = proyecto.listaEstambres ?: emptyList()
        val accesorios = proyecto.listaAccesorios ?: emptyList()

        tvDesgloseMat.text = buildString {
            if (estambres.isEmpty() && accesorios.isEmpty()) {
                append("Sin detalles guardados")
            } else {
                estambres.forEach { appendLine("• ${it.nombre}: $${it.costo}") }
                accesorios.forEach { appendLine("• ${it.nombre}: $${it.costo}") }
            }
        }.trimEnd()

        var matExpandido = false
        llToggleMateriales.setOnClickListener {
            matExpandido = !matExpandido
            tvDesgloseMat.visibility = if (matExpandido) View.VISIBLE else View.GONE
        }

        val llToggleMano = dialogView.findViewById<LinearLayout>(R.id.llToggleManoObra)
        val tvCostoManoResumen = dialogView.findViewById<TextView>(R.id.tvCostoManoObraResumen)
        val tvDesgloseMano = dialogView.findViewById<TextView>(R.id.tvDesgloseManoObra)
        tvCostoManoResumen.text = String.format("$%.2f", proyecto.costoManoObra)
        tvDesgloseMano.text = String.format("• Horas invertidas: %.2f hrs\n• Cobro por hora: $%.2f", proyecto.horasTrabajadas, proyecto.pagoPorHora)

        var manoExpandida = false
        llToggleMano.setOnClickListener {
            manoExpandida = !manoExpandida
            tvDesgloseMano.visibility = if (manoExpandida) View.VISIBLE else View.GONE
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleCerrar).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleEliminar).setOnClickListener {
            DialogUtils.mostrarAlerta(
                context = requireContext(),
                titulo = "Mover a la papelera",
                mensaje = "¿Segura que deseas mover '${proyecto.nombre}' a la papelera?",
                textoConfirmar = "Mover",
                iconoResId = R.drawable.ic_sad
            ) {
                viewModel.moverAPapelera(proyecto) { movido ->
                    if (!isAdded) return@moverAPapelera
                    if (movido) {
                        dialog.dismiss()
                        filtrarYOrdenarLista()
                        Toast.makeText(requireContext(), "Proyecto enviado a la papelera", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No se pudo mover a la papelera. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        fun prepararProyectoParaEdicion(esDuplicado: Boolean) {
            viewModel.idProyectoEnEdicion = if (esDuplicado) null else proyecto.id
            viewModel.fechaEnEdicion = if (esDuplicado) null else proyecto.fechaGuardado
            viewModel.costoMateriales = proyecto.costoMateriales
            viewModel.costoDiseno = proyecto.costoDiseno
            viewModel.costoManoObra = proyecto.costoManoObra
            viewModel.costoEmpaque = proyecto.costoEmpaque
            viewModel.margenGanancia = proyecto.margenGanancia
            viewModel.horasTrabajadasTemp = proyecto.horasTrabajadas
            viewModel.pagoPorHoraTemp = proyecto.pagoPorHora
            viewModel.nombreTemp = proyecto.nombre
            viewModel.fotoUriTemp = proyecto.fotoUri
            viewModel.fotoMiniaturaUriTemp = proyecto.fotoMiniaturaUri
            viewModel.carpetasTemp.clear()
            viewModel.carpetasTemp.addAll(proyecto.carpetas ?: emptyList())
            viewModel.listaEstambresGuardados.clear()
            viewModel.listaEstambresGuardados.addAll(proyecto.listaEstambres ?: emptyList())
            viewModel.listaAccesoriosGuardados.clear()
            viewModel.listaAccesoriosGuardados.addAll(proyecto.listaAccesorios ?: emptyList())

            dialog.dismiss()
            (requireActivity() as MainActivity).navegarConGanchillo(MaterialesFragment())
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar).setOnClickListener { prepararProyectoParaEdicion(true) }
        dialogView.findViewById<View>(R.id.btnOpcionesDetalle)?.setOnClickListener { prepararProyectoParaEdicion(false) }
        dialog.show()
    }
}