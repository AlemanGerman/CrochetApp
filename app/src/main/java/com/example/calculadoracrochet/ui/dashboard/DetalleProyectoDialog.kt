package com.example.calculadoracrochet.ui.dashboard

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.request.CachePolicy
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.CalculadoraPrecio
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.ui.calculadora.CalculadoraViewModel
import com.example.calculadoracrochet.ui.calculadora.MaterialesFragment
import com.example.calculadoracrochet.ui.comun.ZoomableImageView
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.LOCALE_MX
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.duracionLegible
import com.example.calculadoracrochet.utils.desgloseMateriales
import com.example.calculadoracrochet.utils.hacerDesplegable
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.data.model.tieneFoto
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Detail sheet of a saved project: photo, rename, date change, cost breakdown,
 * move to trash and edit/duplicate.
 */
class DetalleProyectoDialog(
    private val fragment: Fragment,
    private val viewModel: ProyectosViewModel,
    private val calculadoraViewModel: CalculadoraViewModel,
    private val onProyectoModificado: () -> Unit,
    private val onSolicitarFoto: (proyecto: ProyectoCrochet, vistaFoto: ImageView) -> Unit
) {

    private val formatoFecha = SimpleDateFormat("dd 'de' MMMM, yyyy", LOCALE_MX)

    fun mostrar(proyecto: ProyectoCrochet) {
        val contexto = fragment.requireContext()
        val dialog = BottomSheetDialog(contexto)
        val dialogView = LayoutInflater.from(contexto).inflate(R.layout.dialog_detalle_proyecto, null)
        dialog.setContentView(dialogView)
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            // Never the whole screen: the rounded top and a strip of the dashboard stay visible
            maxHeight = (contexto.resources.displayMetrics.heightPixels * 0.92f).toInt()
        }

        configurarFoto(dialogView, proyecto)
        configurarNombre(dialogView, proyecto)
        configurarFecha(dialogView, proyecto)
        configurarDesglose(dialogView, proyecto)
        configurarAcciones(dialogView, dialog, proyecto)

        dialog.show()
    }

    private fun configurarFoto(dialogView: View, proyecto: ProyectoCrochet) {
        val cvFotoContenedor = dialogView.findViewById<View>(R.id.cvDetalleFotoContenedor)
        val ivFoto = dialogView.findViewById<ImageView>(R.id.ivDetalleFoto)
        val btnEditarFoto = dialogView.findViewById<View>(R.id.btnEditarFotoDetalle)

        fun refrescarImagenLocal() {
            cvFotoContenedor.visibility = View.VISIBLE
            if (proyecto.tieneFoto) {
                ivFoto.setPadding(0, 0, 0, 0)
                ivFoto.scaleType = ImageView.ScaleType.CENTER_CROP
                ivFoto.imageTintList = null
                ivFoto.cargarFotoProyecto(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
                btnEditarFoto?.visibility = View.VISIBLE
                cvFotoContenedor.contentDescription = "Ver la foto en grande"
            } else {
                // The whole slot is the button to add one
                val relleno = (80 * ivFoto.resources.displayMetrics.density).toInt()
                ivFoto.setPadding(relleno, relleno, relleno, relleno)
                ivFoto.setImageResource(R.drawable.ic_agregar_foto)
                ivFoto.scaleType = ImageView.ScaleType.FIT_CENTER
                ivFoto.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ivFoto.context, R.color.morado))
                btnEditarFoto?.visibility = View.GONE
                cvFotoContenedor.contentDescription = "Añadir foto"
            }
        }
        refrescarImagenLocal()

        btnEditarFoto?.setOnClickListener {
            it.hapticClickPrincipal()
            if (!proyecto.tieneFoto) {
                onSolicitarFoto(proyecto, ivFoto)
                return@setOnClickListener
            }

            DialogUtils.mostrarOpciones(
                context = fragment.requireContext(),
                titulo = "Opciones de foto",
                textoOpcion1 = "Cambiar foto",
                iconoOpcion1 = R.drawable.ic_agregar_foto,
                textoOpcion2 = "Quitar foto",
                iconoOpcion2 = R.drawable.ic_eliminar,
                accionOpcion1 = { onSolicitarFoto(proyecto, ivFoto) },
                accionOpcion2 = {
                    viewModel.eliminarFotoProyecto(proyecto) { eliminada ->
                        if (!fragment.isAdded) return@eliminarFotoProyecto
                        if (eliminada) {
                            refrescarImagenLocal()
                            onProyectoModificado()
                        } else {
                            mostrarError("No se pudo eliminar la foto. Inténtalo de nuevo.")
                        }
                    }
                }
            )
        }

        cvFotoContenedor.setOnClickListener {
            it.hapticClickPrincipal()
            if (proyecto.tieneFoto) {
                mostrarFotoGrande(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
            } else {
                onSolicitarFoto(proyecto, ivFoto)
            }
        }
    }

    private fun configurarNombre(dialogView: View, proyecto: ProyectoCrochet) {
        val tvNombre = dialogView.findViewById<TextView>(R.id.tvDetalleNombre)
        tvNombre.text = proyecto.nombre

        tvNombre.setOnClickListener {
            it.hapticClickPrincipal()
            DialogUtils.mostrarInput(
                context = fragment.requireContext(),
                titulo = "Cambiar nombre",
                hint = "Nombre del proyecto",
                textoActual = proyecto.nombre
            ) { nuevoNombre ->
                viewModel.actualizarNombreProyecto(proyecto, nuevoNombre)
                tvNombre.text = nuevoNombre
                onProyectoModificado()
            }
        }
    }

    private fun configurarFecha(dialogView: View, proyecto: ProyectoCrochet) {
        val tvFecha = dialogView.findViewById<TextView>(R.id.tvDetalleFecha)
        tvFecha.text = formatoFecha.format(Date(proyecto.fechaGuardado))

        tvFecha.setOnClickListener {
            it.hapticClickPrincipal()
            DialogUtils.mostrarSelectorFecha(fragment.requireContext(), proyecto.fechaGuardado) { tiempoMs ->
                viewModel.actualizarFechaProyecto(proyecto, tiempoMs)
                tvFecha.text = formatoFecha.format(Date(tiempoMs))
                onProyectoModificado()
            }
        }
    }

    private fun configurarDesglose(dialogView: View, proyecto: ProyectoCrochet) {
        dialogView.findViewById<TextView>(R.id.tvDetalleTotal).text = proyecto.precioFinal.comoDinero()
        dialogView.findViewById<TextView>(R.id.tvDetallePatron).text = proyecto.costoDiseno.comoDinero()
        dialogView.findViewById<TextView>(R.id.tvDetalleEmpaque).text = proyecto.costoEmpaque.comoDinero()

        val desglose = CalculadoraPrecio.desglosar(
            proyecto.costoMateriales,
            proyecto.costoDiseno,
            proyecto.costoManoObra,
            proyecto.costoEmpaque,
            proyecto.margenGanancia
        )
        dialogView.findViewById<TextView>(R.id.tvEtiquetaGanancia).text =
            "Ganancia extra (${proyecto.margenGanancia.toInt()}%)"
        dialogView.findViewById<TextView>(R.id.tvDetalleGanancia).text = "+${desglose.ganancia.comoDinero()}"

        val filaRedondeo = dialogView.findViewById<View>(R.id.llFilaRedondeoDetalle)
        if (proyecto.ajusteRedondeo > 0.0) {
            filaRedondeo.visibility = View.VISIBLE
            dialogView.findViewById<TextView>(R.id.tvDetalleRedondeo).text = "+${proyecto.ajusteRedondeo.comoDinero()}"
        } else {
            filaRedondeo.visibility = View.GONE
        }

        dialogView.findViewById<TextView>(R.id.tvCostoMaterialesResumen).text = proyecto.costoMateriales.comoDinero()
        val tvDesgloseMat = dialogView.findViewById<TextView>(R.id.tvDesgloseMateriales)
        tvDesgloseMat.text = desgloseMateriales(
            estambres = proyecto.listaEstambres,
            accesorios = proyecto.listaAccesorios,
            vineta = "• ",
            textoVacio = "Sin detalles guardados"
        )
        dialogView.findViewById<View>(R.id.llToggleMateriales)
            .hacerDesplegable(tvDesgloseMat, dialogView.findViewById(R.id.ivFlechaMateriales))

        dialogView.findViewById<TextView>(R.id.tvCostoManoObraResumen).text = proyecto.costoManoObra.comoDinero()
        val tvDesgloseMano = dialogView.findViewById<TextView>(R.id.tvDesgloseManoObra)
        tvDesgloseMano.text = "• Tiempo invertido: ${duracionLegible(proyecto.horasTrabajadas)}" +
            "\n• Cobro por hora: ${proyecto.pagoPorHora.comoDinero()}"
        dialogView.findViewById<View>(R.id.llToggleManoObra)
            .hacerDesplegable(tvDesgloseMano, dialogView.findViewById(R.id.ivFlechaManoObra))
    }

    private fun configurarAcciones(dialogView: View, dialog: BottomSheetDialog, proyecto: ProyectoCrochet) {
        dialogView.findViewById<MaterialButton>(R.id.btnDetalleCerrar).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleEliminar).setOnClickListener {
            it.hapticEliminar()
            DialogUtils.mostrarAlerta(
                context = fragment.requireContext(),
                titulo = "Mover a la papelera",
                mensaje = "¿Segura que deseas mover '${proyecto.nombre}' a la papelera?",
                textoConfirmar = "Mover",
                iconoResId = R.drawable.ic_sad
            ) {
                viewModel.moverAPapelera(proyecto) { movido ->
                    if (!fragment.isAdded) return@moverAPapelera
                    if (movido) {
                        dialog.dismiss()
                        onProyectoModificado()
                        Toast.makeText(fragment.requireContext(), "Proyecto enviado a la papelera", Toast.LENGTH_SHORT).show()
                    } else {
                        mostrarError("No se pudo mover a la papelera. Inténtalo de nuevo.")
                    }
                }
            }
        }

        fun abrirEnCalculadora(esDuplicado: Boolean) {
            calculadoraViewModel.cargarProyectoParaEdicion(proyecto, esDuplicado)
            dialog.dismiss()
            (fragment.requireActivity() as MainActivity).navegarConGanchillo(MaterialesFragment())
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDetalleDuplicar).setOnClickListener {
            it.hapticClickPrincipal()
            abrirEnCalculadora(esDuplicado = true)
        }
        dialogView.findViewById<View>(R.id.btnOpcionesDetalle)?.setOnClickListener {
            it.hapticClickPrincipal()
            abrirEnCalculadora(esDuplicado = false)
        }
    }

    private fun mostrarFotoGrande(fotoUri: String?, miniaturaUri: String?) {
        if (fotoUri.isNullOrBlank()) return
        val (dialog, view) = DialogUtils.crearDialogBase(fragment.requireContext(), R.layout.dialog_foto_zoom)

        view.findViewById<ZoomableImageView>(R.id.ivFotoZoom).cargarFotoProyecto(fotoUri, miniaturaUri)
        view.findViewById<MaterialButton>(R.id.btnCerrarZoom).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun mostrarError(mensaje: String) {
        Toast.makeText(fragment.requireContext(), mensaje, Toast.LENGTH_LONG).show()
    }

    /** Loads the full photo using the thumbnail already in memory as placeholder. */
    private fun ImageView.cargarFotoProyecto(fotoUri: String?, miniaturaUri: String?) {
        load(fotoUri) {
            crossfade(true)
            if (!miniaturaUri.isNullOrBlank()) {
                placeholderMemoryCacheKey(miniaturaUri)
            }
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)
            error(R.drawable.ic_photo)
        }
    }
}
