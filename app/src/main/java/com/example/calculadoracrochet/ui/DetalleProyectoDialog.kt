package com.example.calculadoracrochet.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load
import coil.request.CachePolicy
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.CalculadoraPrecio
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.viewmodel.MinaViewModel
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.example.calculadoracrochet.viewmodel.tieneFoto
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Detail modal of a saved project: photo, rename, date change, cost breakdown,
 * move to trash and edit/duplicate.
 */
class DetalleProyectoDialog(
    private val fragment: Fragment,
    private val viewModel: MinaViewModel,
    private val onProyectoModificado: () -> Unit,
    private val onSolicitarFoto: (proyecto: ProyectoCrochet, vistaFoto: ImageView) -> Unit
) {

    private val formatoFecha = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "MX"))

    fun mostrar(proyecto: ProyectoCrochet) {
        val contexto = fragment.requireContext()
        val dialogView = LayoutInflater.from(contexto).inflate(R.layout.dialog_detalle_proyecto, null)
        val dialog = AlertDialog.Builder(contexto).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

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
                ivFoto.scaleType = ImageView.ScaleType.CENTER_CROP
                ivFoto.imageTintList = null
                ivFoto.cargarFotoProyecto(proyecto.fotoUri, proyecto.fotoMiniaturaUri)
            } else {
                ivFoto.setImageResource(R.drawable.ic_photo)
                ivFoto.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivFoto.imageTintList = ColorStateList.valueOf(Color.GRAY)
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
                textoOpcion2 = "Eliminar foto",
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
            val (dialogFecha, fechaDialogView) = DialogUtils.crearDialogBase(
                fragment.requireContext(),
                R.layout.dialog_seleccionar_fecha
            )
            val datePicker = fechaDialogView.findViewById<DatePicker>(R.id.datePickerSpinner)
            val calendario = Calendar.getInstance().apply { timeInMillis = proyecto.fechaGuardado }

            datePicker.updateDate(
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            )

            fechaDialogView.findViewById<MaterialButton>(R.id.btnCancelarFecha).setOnClickListener { boton ->
                boton.hapticClickPrincipal()
                dialogFecha.dismiss()
            }
            fechaDialogView.findViewById<MaterialButton>(R.id.btnAceptarFecha).setOnClickListener { boton ->
                boton.hapticClickPrincipal()
                val tiempoMs = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }.timeInMillis

                viewModel.actualizarFechaProyecto(proyecto, tiempoMs)
                tvFecha.text = formatoFecha.format(Date(tiempoMs))
                onProyectoModificado()
                dialogFecha.dismiss()
            }
            dialogFecha.show()
        }
    }

    private fun configurarDesglose(dialogView: View, proyecto: ProyectoCrochet) {
        dialogView.findViewById<TextView>(R.id.tvDetalleTotal).text = String.format("Total: $%.2f", proyecto.precioFinal)
        dialogView.findViewById<TextView>(R.id.tvDetallePatron).text = String.format("$%.2f", proyecto.costoDiseno)
        dialogView.findViewById<TextView>(R.id.tvDetalleEmpaque).text = String.format("$%.2f", proyecto.costoEmpaque)

        val desglose = CalculadoraPrecio.desglosar(
            proyecto.costoMateriales,
            proyecto.costoDiseno,
            proyecto.costoManoObra,
            proyecto.costoEmpaque,
            proyecto.margenGanancia
        )
        dialogView.findViewById<TextView>(R.id.tvDetalleGanancia).text =
            String.format("+ Ganancia Extra (%d%%): $%.2f", proyecto.margenGanancia.toInt(), desglose.ganancia)

        val tvDetalleRedondeo = dialogView.findViewById<TextView>(R.id.tvDetalleRedondeo)
        if (proyecto.ajusteRedondeo > 0.0) {
            tvDetalleRedondeo?.visibility = View.VISIBLE
            tvDetalleRedondeo?.text = String.format("+ Redondeo a favor: $%.2f", proyecto.ajusteRedondeo)
        } else {
            tvDetalleRedondeo?.visibility = View.GONE
        }

        dialogView.findViewById<TextView>(R.id.tvCostoMaterialesResumen).text =
            String.format("$%.2f", proyecto.costoMateriales)
        val tvDesgloseMat = dialogView.findViewById<TextView>(R.id.tvDesgloseMateriales)
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
        configurarSeccionDesplegable(dialogView.findViewById(R.id.llToggleMateriales), tvDesgloseMat)

        dialogView.findViewById<TextView>(R.id.tvCostoManoObraResumen).text =
            String.format("$%.2f", proyecto.costoManoObra)
        val tvDesgloseMano = dialogView.findViewById<TextView>(R.id.tvDesgloseManoObra)
        tvDesgloseMano.text = String.format(
            "• Horas invertidas: %.2f hrs\n• Cobro por hora: $%.2f",
            proyecto.horasTrabajadas,
            proyecto.pagoPorHora
        )
        configurarSeccionDesplegable(dialogView.findViewById(R.id.llToggleManoObra), tvDesgloseMano)
    }

    private fun configurarSeccionDesplegable(toggle: LinearLayout, contenido: View) {
        var expandido = false
        toggle.setOnClickListener {
            it.hapticSeleccion()
            expandido = !expandido
            contenido.visibility = if (expandido) View.VISIBLE else View.GONE
        }
    }

    private fun configurarAcciones(dialogView: View, dialog: AlertDialog, proyecto: ProyectoCrochet) {
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
            viewModel.cargarProyectoParaEdicion(proyecto, esDuplicado)
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
