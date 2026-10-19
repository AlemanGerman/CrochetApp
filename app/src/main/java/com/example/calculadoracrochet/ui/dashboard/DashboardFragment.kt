package com.example.calculadoracrochet.ui.dashboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.calculadora.MaterialesFragment
import com.example.calculadoracrochet.ui.configuracion.ConfiguracionFragment
import com.example.calculadoracrochet.ui.estadisticas.EstadisticasFragment
import com.example.calculadoracrochet.ui.papelera.PapeleraFragment
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import java.lang.ref.WeakReference

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: MinaViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter
    private lateinit var detalleDialog: DetalleProyectoDialog
    private var dockCarpetas: DockCarpetasController? = null

    private var criterioActual: CriterioOrden = CriterioOrden.FECHA
    private var esOrdenDescendente: Boolean = true
    private var carpetaSeleccionada = FiltroProyectos.CARPETA_TODOS

    private var proyectoEditando: ProyectoCrochet? = null
    private var vistaFotoEnEdicion: WeakReference<ImageView>? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val contexto = context
        val proyectoActual = proyectoEditando
        if (uri != null && proyectoActual != null && contexto != null) {
            val referenciaVista = vistaFotoEnEdicion
            val fotoAnterior = proyectoActual.fotoUri

            DialogUtils.mostrarModalEncuadre(contexto, uri) { uriEncuadrada ->
                try {
                    referenciaVista?.get()?.takeIf { it.isAttachedToWindow }?.apply {
                        setImageURI(uriEncuadrada)
                        imageTintList = null
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                viewModel.actualizarFotoProyecto(contexto, proyectoActual, uriEncuadrada.toString()) { actualizada ->
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
        proyectoEditando = null
        vistaFotoEnEdicion = null
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepararAnimacionGanchillo()
        configurarBotonAtras()

        detalleDialog = DetalleProyectoDialog(
            fragment = this,
            viewModel = viewModel,
            onProyectoModificado = { filtrarYOrdenarLista() },
            onSolicitarFoto = { proyecto, vistaFoto -> solicitarFoto(proyecto, vistaFoto) }
        )

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProyectosAdapter(
            listaProyectos = emptyList(),
            onItemClick = { proyecto -> detalleDialog.mostrar(proyecto) },
            onEditCarpetaClick = { proyecto -> mostrarModalCarpetas(proyecto) }
        )
        recyclerView.adapter = adapter

        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            filtrarYOrdenarLista()
        }

        view.findViewById<EditText>(R.id.etBuscarProyecto).doAfterTextChanged { filtrarYOrdenarLista() }

        view.findViewById<MaterialButton>(R.id.btnTipoOrden).setOnClickListener { vistaBoton ->
            vistaBoton.hapticClickPrincipal()
            mostrarMenuCriterio(vistaBoton)
        }

        val btnDireccion = view.findViewById<MaterialButton>(R.id.btnDireccionOrden)
        btnDireccion.setIconResource(R.drawable.ic_flecha_abajo)
        actualizarRotacionFlecha(animar = false)
        btnDireccion.setOnClickListener {
            btnDireccion.hapticSeleccion()
            esOrdenDescendente = !esOrdenDescendente
            actualizarRotacionFlecha(animar = true)
            refrescarYSubir()
        }

        view.findViewById<ImageButton>(R.id.btnOpciones).setOnClickListener {
            it.hapticClickPrincipal()
            mostrarBottomSheetOpciones()
        }

        view.findViewById<MaterialButton>(R.id.btnNuevoProyecto).setOnClickListener {
            it.hapticClickPrincipal()
            viewModel.limpiarCalculoActual()
            (requireActivity() as MainActivity).navegarConGanchillo(MaterialesFragment())
        }

        dockCarpetas = DockCarpetasController(
            raiz = view,
            carpetaInicial = carpetaSeleccionada,
            estaActivo = { isAdded },
            onCarpetaSeleccionada = { carpeta ->
                carpetaSeleccionada = carpeta
                refrescarYSubir()
            }
        ).also { it.configurar() }

        filtrarYOrdenarLista()
    }

    override fun onDestroyView() {
        dockCarpetas?.cancelarAnimaciones()
        dockCarpetas = null
        super.onDestroyView()
    }

    private fun prepararAnimacionGanchillo() {
        val lottieGanchillo = requireActivity().findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieGanchillo)
        if (lottieGanchillo != null && !lottieGanchillo.isAnimating) {
            lottieGanchillo.post {
                lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
                lottieGanchillo.pauseAnimation()
                lottieGanchillo.progress = 0f
                lottieGanchillo.visibility = View.VISIBLE
            }
        }
    }

    private fun configurarBotonAtras() {
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
    }

    private fun solicitarFoto(proyecto: ProyectoCrochet, vistaFoto: ImageView) {
        proyectoEditando = proyecto
        vistaFotoEnEdicion = WeakReference(vistaFoto)
        pickMedia.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
    }

    private fun filtrarYOrdenarLista() {
        val textoBusqueda = view?.findViewById<EditText>(R.id.etBuscarProyecto)?.text?.toString() ?: ""
        adapter.actualizarLista(
            FiltroProyectos.aplicar(
                proyectos = viewModel.historialProyectos,
                textoBusqueda = textoBusqueda,
                carpeta = carpetaSeleccionada,
                criterio = criterioActual,
                descendente = esOrdenDescendente
            )
        )
    }

    private fun refrescarYSubir() {
        filtrarYOrdenarLista()
        view?.findViewById<RecyclerView>(R.id.rvProyectos)?.smoothScrollToPosition(0)
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
        val menuView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_ordenar, null)

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

        data class OpcionCriterio(val opcion: Int, val check: Int, val texto: Int)

        val opciones = mapOf(
            CriterioOrden.FECHA to OpcionCriterio(R.id.opcCriterioFecha, R.id.checkCriterioFecha, R.id.tvCriterioFecha),
            CriterioOrden.PRECIO to OpcionCriterio(R.id.opcCriterioPrecio, R.id.checkCriterioPrecio, R.id.tvCriterioPrecio),
            CriterioOrden.FOTO to OpcionCriterio(R.id.opcCriterioFoto, R.id.checkCriterioFoto, R.id.tvCriterioFoto),
            CriterioOrden.NOMBRE to OpcionCriterio(R.id.opcCriterioNombre, R.id.checkCriterioNombre, R.id.tvCriterioNombre)
        )

        opciones[criterioActual]?.let { actual ->
            menuView.findViewById<View>(actual.check)?.visibility = View.VISIBLE
            menuView.findViewById<TextView>(actual.texto)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
                setTypeface(typeface, Typeface.BOLD)
            }
        }

        opciones.forEach { (criterio, ids) ->
            menuView.findViewById<View>(ids.opcion).setOnClickListener {
                it.hapticSeleccion()
                if (criterioActual != criterio) {
                    criterioActual = criterio
                    refrescarYSubir()
                }
                popupWindow.dismiss()
            }
        }

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val offsetX = -(menuView.measuredWidth - anchorView.width)
        popupWindow.showAsDropDown(anchorView, offsetX, 12)
    }

    private fun mostrarBottomSheetOpciones() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_dashboard_opciones, null)
        bottomSheetDialog.setContentView(sheetView)

        mapOf(
            R.id.itemOpcionEstadisticas to { EstadisticasFragment() },
            R.id.itemOpcionConfiguracion to { ConfiguracionFragment() },
            R.id.itemOpcionPapelera to { PapeleraFragment() }
        ).forEach { (idOpcion, crearDestino) ->
            sheetView.findViewById<View>(idOpcion)?.setOnClickListener {
                it.hapticClickPrincipal()
                bottomSheetDialog.dismiss()
                navegarHaciaFragmento(crearDestino())
            }
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
        val (dialog, dialogView) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_editar_carpetas)

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
                setOnCheckedChangeListener { _, _ ->
                    hapticSeleccion()
                }
            }
            checkboxesMap[nombreCarpeta] = cb
            llContenedor.addView(cb)
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarCarpetas).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarCarpetas).setOnClickListener {
            it.hapticClickPrincipal()
            proyecto.carpetas = checkboxesMap.filter { it.value.isChecked }.keys.toList()
            viewModel.actualizarCarpetasProyecto(proyecto)
            filtrarYOrdenarLista()
            dialog.dismiss()
        }

        dialog.show()
    }
}
