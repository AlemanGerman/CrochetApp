package com.example.calculadoracrochet.ui.dashboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.PantallaConGanchillo
import com.example.calculadoracrochet.ui.PestanaPrincipal
import com.example.calculadoracrochet.ui.calculadora.CalculadoraViewModel
import com.example.calculadoracrochet.ui.calculadora.MaterialesFragment
import com.example.calculadoracrochet.ui.comun.FondoPatronView
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.CarpetasUi
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.example.calculadoracrochet.utils.animarEntradaEnCascada
import com.example.calculadoracrochet.utils.fundirVisibilidad
import com.example.calculadoracrochet.utils.rebotar
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.margenSobreBarraDeNavegacion
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado
import com.example.calculadoracrochet.utils.rellenarSobreBarraDeNavegacion
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.lang.ref.WeakReference

class DashboardFragment : Fragment(R.layout.fragment_dashboard), PestanaPrincipal, PantallaConGanchillo {

    override val idPestana = R.id.nav_proyectos

    private val viewModel: ProyectosViewModel by activityViewModels()
    private val calculadoraViewModel: CalculadoraViewModel by activityViewModels()
    private lateinit var adapter: ProyectosAdapter
    private lateinit var detalleDialog: DetalleProyectoDialog
    private var dockCarpetas: DockCarpetasController? = null

    private var animadorLista: RecyclerView.ItemAnimator? = null
    private val busquedaPendiente = Runnable { filtrarYOrdenarLista() }

    private var proyectoEditando: ProyectoCrochet? = null
    private var vistaFotoEnEdicion: WeakReference<ImageView>? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val contexto = context
        val proyectoActual = proyectoEditando
        val scopeVista = view?.let { viewLifecycleOwner.lifecycleScope }
        if (uri != null && proyectoActual != null && contexto != null && scopeVista != null) {
            val referenciaVista = vistaFotoEnEdicion
            val fotoAnterior = proyectoActual.fotoUri

            DialogUtils.mostrarModalEncuadre(contexto, scopeVista, uri) { uriEncuadrada ->
                try {
                    referenciaVista?.get()?.takeIf { it.isAttachedToWindow }?.apply {
                        setImageURI(uriEncuadrada)
                        imageTintList = null
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                viewModel.actualizarFotoProyecto(proyectoActual, uriEncuadrada.toString()) { actualizada ->
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
            // Only repaints when the setting was changed in Ajustes meanwhile
            adapter.cambiarColorDinamico(PreferenciasApp.isColorDinamicoTarjetasActivo(requireContext()))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarBotonAtras()
        configurarInsets(view)

        detalleDialog = DetalleProyectoDialog(
            fragment = this,
            viewModel = viewModel,
            calculadoraViewModel = calculadoraViewModel,
            onProyectoModificado = { filtrarYOrdenarLista() },
            onSolicitarFoto = { proyecto, vistaFoto -> solicitarFoto(proyecto, vistaFoto) }
        )

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvProyectos)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProyectosAdapter(
            onItemClick = { proyecto, foto -> detalleDialog.mostrar(proyecto, foto) },
            onEditCarpetaClick = { proyecto -> mostrarModalCarpetas(proyecto) }
        )
        adapter.cambiarColorDinamico(PreferenciasApp.isColorDinamicoTarjetasActivo(requireContext()))
        recyclerView.adapter = adapter
        // A card whose content changed repaints in place (its color glides) instead of cross-fading with a copy
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        animadorLista = recyclerView.itemAnimator

        // The floating button tucks its label away while the list scrolls down
        val btnNuevo = view.findViewById<ExtendedFloatingActionButton>(R.id.btnNuevoProyecto)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 6 && btnNuevo.isExtended) btnNuevo.shrink()
                else if ((dy < -6 || !recyclerView.canScrollVertically(-1)) && !btnNuevo.isExtended) btnNuevo.extend()
            }
        })

        // The first list (opening the app, coming back from another tab) cascades in; later changes animate row by row
        var primeraLista = true
        viewModel.proyectosEnVivo.observe(viewLifecycleOwner) {
            filtrarYOrdenarLista(cascada = primeraLista)
            primeraLista = false
        }

        val etBuscar = view.findViewById<EditText>(R.id.etBuscarProyecto)
        etBuscar.setText(viewModel.filtroDashboard.textoBusqueda)
        etBuscar.doAfterTextChanged {
            viewModel.filtroDashboard = viewModel.filtroDashboard.copy(textoBusqueda = it?.toString().orEmpty())
            // Waits for a pause in typing instead of filtering on every letter
            etBuscar.removeCallbacks(busquedaPendiente)
            etBuscar.postDelayed(busquedaPendiente, ESPERA_BUSQUEDA_MS)
        }

        view.findViewById<MaterialButton>(R.id.btnTipoOrden).setOnClickListener { vistaBoton ->
            vistaBoton.hapticClickPrincipal()
            mostrarMenuCriterio(vistaBoton)
        }

        val btnDireccion = view.findViewById<MaterialButton>(R.id.btnDireccionOrden)
        actualizarRotacionFlecha(animar = false)
        btnDireccion.setOnClickListener {
            btnDireccion.hapticSeleccion()
            val filtro = viewModel.filtroDashboard
            viewModel.filtroDashboard = filtro.copy(descendente = !filtro.descendente)
            actualizarRotacionFlecha(animar = true)
            refrescarYSubir()
        }

        view.findViewById<View>(R.id.btnPapelera).setOnClickListener {
            it.hapticClickPrincipal()
            (requireActivity() as MainActivity).abrirPapelera()
        }

        btnNuevo.setOnClickListener {
            it.hapticClickPrincipal()
            calculadoraViewModel.limpiarCalculoActual()
            (requireActivity() as MainActivity).navegarConGanchillo(MaterialesFragment())
        }

        dockCarpetas = DockCarpetasController(
            raiz = view,
            carpetaInicial = viewModel.filtroDashboard.carpeta,
            estaActivo = { isAdded },
            onCarpetaSeleccionada = { carpeta ->
                viewModel.filtroDashboard = viewModel.filtroDashboard.copy(carpeta = carpeta)
                refrescarYSubir()
            }
        ).also { it.configurar() }

        // The first calculator step is usually next: have its header ready
        view.post {
            if (isAdded) {
                val encabezado = view.findViewById<View>(R.id.contenidoEncabezado).parent as View
                FondoPatronView.precargar(requireContext(), R.drawable.encabezado_materiales, view.width, encabezado.height)
            }
        }

        if (viewModel.proyectosEnVivo.value == null) filtrarYOrdenarLista()

        if (savedInstanceState == null) {
            // The button pops in once the screen is there
            btnNuevo.scaleX = 0.6f
            btnNuevo.scaleY = 0.6f
            btnNuevo.alpha = 0f
            btnNuevo.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(160L).setDuration(320L)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }
    }

    override fun onDestroyView() {
        view?.findViewById<View>(R.id.etBuscarProyecto)?.removeCallbacks(busquedaPendiente)
        dockCarpetas?.cancelarAnimaciones()
        dockCarpetas = null
        super.onDestroyView()
    }

    override fun alVolverAPulsar() {
        view?.findViewById<RecyclerView>(R.id.rvProyectos)?.smoothScrollToPosition(0)
    }

    private fun configurarInsets(view: View) {
        val densidad = resources.displayMetrics.density
        view.findViewById<View>(R.id.contenidoEncabezado).rellenarBajoBarraDeEstado()
        view.findViewById<View>(R.id.rvProyectos).rellenarSobreBarraDeNavegacion()
        view.findViewById<View>(R.id.btnNuevoProyecto).margenSobreBarraDeNavegacion(
            resources.getDimensionPixelSize(R.dimen.alto_barra_navegacion) + (16 * densidad).toInt()
        )
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

    /** [cascada]: the whole list was replaced (folder, order), so the rows come in one after the other. */
    private fun filtrarYOrdenarLista(cascada: Boolean = false) {
        val vista = view ?: return
        val filtro = viewModel.filtroDashboard
        val lista = FiltroProyectos.aplicar(
            proyectos = viewModel.historialProyectos,
            textoBusqueda = filtro.textoBusqueda,
            carpeta = filtro.carpeta,
            criterio = filtro.criterio,
            descendente = filtro.descendente
        )
        val recyclerView = vista.findViewById<RecyclerView>(R.id.rvProyectos)
        if (cascada && lista.isNotEmpty()) {
            // Row by row animations would fight with the cascade
            recyclerView.itemAnimator = null
            adapter.enviarProyectos(lista) {
                if (view == null) return@enviarProyectos
                recyclerView.scrollToPosition(0)
                recyclerView.animarEntradaEnCascada {
                    if (view != null) recyclerView.itemAnimator = animadorLista
                }
            }
        } else {
            adapter.enviarProyectos(lista)
        }

        val cargado = viewModel.proyectosEnVivo.value != null
        val activos = viewModel.proyectosActivos
        vista.findViewById<TextView>(R.id.tvResumenProyectos).text = when {
            !cargado -> "Cargando proyectos…"
            activos.isEmpty() -> "Aquí aparecerán tus cotizaciones"
            else -> {
                val cuantos = if (activos.size == 1) "1 proyecto" else "${activos.size} proyectos"
                "$cuantos · ${activos.sumOf { it.precioFinal }.comoDinero()} cotizados"
            }
        }

        val llVacio = vista.findViewById<View>(R.id.llEstadoVacio)
        val mostrarVacio = cargado && lista.isEmpty()
        if (mostrarVacio && llVacio.visibility != View.VISIBLE) {
            vista.findViewById<View>(R.id.ivEstadoVacio).rebotar(1.18f)
        }
        llVacio.fundirVisibilidad(mostrarVacio)
        if (lista.isEmpty()) {
            val sinProyectos = activos.isEmpty()
            vista.findViewById<TextView>(R.id.tvTituloVacio).text =
                if (sinProyectos) "Aún no tienes proyectos" else "Sin resultados"
            vista.findViewById<TextView>(R.id.tvMensajeVacio).text =
                if (sinProyectos) "Toca «Nuevo proyecto» para hacer tu primera cotización."
                else "Ningún proyecto coincide con la búsqueda o la carpeta elegida."
        }
    }

    private fun refrescarYSubir() {
        filtrarYOrdenarLista(cascada = true)
    }

    private fun actualizarRotacionFlecha(animar: Boolean = true) {
        val btnDireccion = view?.findViewById<MaterialButton>(R.id.btnDireccionOrden) ?: return
        val anguloDestino = if (viewModel.filtroDashboard.descendente) 0f else 180f

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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = R.style.AnimacionMenuOrden
        }

        data class OpcionCriterio(val opcion: Int, val check: Int, val texto: Int)

        val opciones = mapOf(
            CriterioOrden.FECHA to OpcionCriterio(R.id.opcCriterioFecha, R.id.checkCriterioFecha, R.id.tvCriterioFecha),
            CriterioOrden.PRECIO to OpcionCriterio(R.id.opcCriterioPrecio, R.id.checkCriterioPrecio, R.id.tvCriterioPrecio),
            CriterioOrden.FOTO to OpcionCriterio(R.id.opcCriterioFoto, R.id.checkCriterioFoto, R.id.tvCriterioFoto),
            CriterioOrden.NOMBRE to OpcionCriterio(R.id.opcCriterioNombre, R.id.checkCriterioNombre, R.id.tvCriterioNombre)
        )

        opciones[viewModel.filtroDashboard.criterio]?.let { actual ->
            menuView.findViewById<View>(actual.check)?.visibility = View.VISIBLE
            menuView.findViewById<TextView>(actual.texto)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.morado_fuerte))
                typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.nunito_extrabold)
            }
        }

        opciones.forEach { (criterio, ids) ->
            menuView.findViewById<View>(ids.opcion).setOnClickListener {
                it.hapticSeleccion()
                if (viewModel.filtroDashboard.criterio != criterio) {
                    viewModel.filtroDashboard = viewModel.filtroDashboard.copy(criterio = criterio)
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
        popupWindow.showAsDropDown(anchorView, offsetX, 0)
    }

    private fun mostrarModalCarpetas(proyecto: ProyectoCrochet) {
        val (dialog, dialogView) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_editar_carpetas)

        val cgCarpetas = dialogView.findViewById<ChipGroup>(R.id.cgCarpetasEditar)
        val chips = mutableMapOf<String, Chip>()
        CarpetasConfig.listaCarpetas
            .filter { it.nombre != CarpetasConfig.CARPETA_TODOS }
            .forEach { carpeta ->
                val chip = CarpetasUi.crearChipSeleccionable(cgCarpetas, carpeta, proyecto.carpetas.contains(carpeta.nombre))
                chips[carpeta.nombre] = chip
                cgCarpetas.addView(chip)
            }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelarCarpetas).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnGuardarCarpetas).setOnClickListener {
            it.hapticClickPrincipal()
            proyecto.carpetas = chips.filter { it.value.isChecked }.keys.toList()
            viewModel.actualizarCarpetasProyecto(proyecto)
            filtrarYOrdenarLista()
            dialog.dismiss()
        }

        dialog.show()
    }

    private companion object {
        const val ESPERA_BUSQUEDA_MS = 220L
    }
}
