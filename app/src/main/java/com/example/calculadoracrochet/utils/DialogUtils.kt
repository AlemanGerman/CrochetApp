package com.example.calculadoracrochet.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.comun.EncuadreImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

object DialogUtils {

    private fun esAccionDestructiva(texto: String): Boolean =
        listOf("Eliminar", "Vaciar", "Mover", "Quitar", "Borrar").any { texto.contains(it, ignoreCase = true) }

    fun crearDialogBase(context: Context, layoutResId: Int): Pair<AlertDialog, View> {
        val view = LayoutInflater.from(context).inflate(layoutResId, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return Pair(dialog, view)
    }

    fun mostrarSelectorFecha(
        context: Context,
        fechaInicialMs: Long?,
        onFechaSeleccionada: (Long) -> Unit
    ) {
        val (dialog, view) = crearDialogBase(context, R.layout.dialog_seleccionar_fecha)
        val datePicker = view.findViewById<DatePicker>(R.id.datePickerSpinner)
        val calendario = Calendar.getInstance()
        fechaInicialMs?.let { calendario.timeInMillis = it }

        datePicker.updateDate(
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        )

        view.findViewById<MaterialButton>(R.id.btnCancelarFecha).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnAceptarFecha).setOnClickListener {
            it.hapticClickPrincipal()
            val nuevaFecha = Calendar.getInstance().apply {
                set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            }
            onFechaSeleccionada(nuevaFecha.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    fun mostrarAlerta(
        context: Context,
        titulo: String,
        mensaje: String,
        textoConfirmar: String = "Confirmar",
        iconoResId: Int? = R.drawable.ic_eliminar,
        accionConfirmar: () -> Unit
    ) {
        val (dialog, view) = crearDialogBase(context, R.layout.dialog_alerta)

        val tvTitulo = view.findViewById<TextView>(R.id.tvTitulo)
        val tvMensaje = view.findViewById<TextView>(R.id.tvMensaje)
        val ivIcono = view.findViewById<ImageView>(R.id.ivIcono)
        val btnConfirmar = view.findViewById<MaterialButton>(R.id.btnConfirmar)

        tvTitulo.text = titulo
        tvMensaje.text = mensaje
        btnConfirmar.text = textoConfirmar

        // Deleting, emptying or trashing: the confirm button and the icon turn red
        val esDestructiva = esAccionDestructiva(textoConfirmar)
        if (esDestructiva) {
            btnConfirmar.backgroundTintList = ContextCompat.getColorStateList(context, R.color.error)
        }

        val contenedorIcono = view.findViewById<View>(R.id.contenedorIcono)
        if (iconoResId != null) {
            ivIcono.setImageResource(iconoResId)
            if (esDestructiva) {
                ivIcono.imageTintList = ContextCompat.getColorStateList(context, R.color.error)
                contenedorIcono.backgroundTintList = ContextCompat.getColorStateList(context, R.color.error_claro)
            }
            contenedorIcono.visibility = View.VISIBLE
        } else {
            contenedorIcono.visibility = View.GONE
        }

        view.findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }
        btnConfirmar.setOnClickListener {
            if (esDestructiva) {
                it.hapticEliminar()
            } else {
                it.hapticClickPrincipal()
            }
            accionConfirmar()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun mostrarInput(
        context: Context,
        titulo: String,
        hint: String,
        textoActual: String = "",
        accionGuardar: (String) -> Unit
    ) {
        val (dialog, view) = crearDialogBase(context, R.layout.dialog_input)

        val tvTitulo = view.findViewById<TextView>(R.id.tvTituloInput)
        val tilInput = view.findViewById<TextInputLayout>(R.id.tilInput)
        val etInput = view.findViewById<TextInputEditText>(R.id.etInput)

        tvTitulo.text = titulo
        tilInput.hint = hint
        etInput.setText(textoActual)

        view.findViewById<MaterialButton>(R.id.btnCancelarInput).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnGuardarInput).setOnClickListener {
            val nuevoTexto = etInput.text.toString().trim()
            if (nuevoTexto.isNotEmpty()) {
                it.hapticClickPrincipal()
                accionGuardar(nuevoTexto)
                dialog.dismiss()
            } else {
                it.hapticError()
                tilInput.error = "Este campo no puede estar vacío"
            }
        }

        dialog.show()
    }

    fun mostrarOpciones(
        context: Context,
        titulo: String,
        textoOpcion1: String,
        iconoOpcion1: Int = 0,
        textoOpcion2: String,
        iconoOpcion2: Int = 0,
        accionOpcion1: () -> Unit,
        accionOpcion2: () -> Unit
    ) {
        val (dialog, view) = crearDialogBase(context, R.layout.dialog_opciones)

        val tvTitulo = view.findViewById<TextView>(R.id.tvTituloOpciones)
        val btnOp1 = view.findViewById<MaterialButton>(R.id.btnOpcion1)
        val btnOp2 = view.findViewById<MaterialButton>(R.id.btnOpcion2)

        tvTitulo.text = titulo
        btnOp1.text = textoOpcion1
        btnOp2.text = textoOpcion2

        if (iconoOpcion1 != 0) btnOp1.setIconResource(iconoOpcion1)
        if (iconoOpcion2 != 0) btnOp2.setIconResource(iconoOpcion2)
        if (esAccionDestructiva(textoOpcion2)) {
            btnOp2.setTextColor(ContextCompat.getColor(context, R.color.error))
            btnOp2.setIconTintResource(R.color.error)
            btnOp2.backgroundTintList = ContextCompat.getColorStateList(context, R.color.error_claro)
        }

        view.findViewById<MaterialButton>(R.id.btnCancelarOpciones).setOnClickListener {
            it.hapticClickPrincipal()
            dialog.dismiss()
        }

        btnOp1.setOnClickListener {
            it.hapticClickPrincipal()
            accionOpcion1()
            dialog.dismiss()
        }
        btnOp2.setOnClickListener {
            if (esAccionDestructiva(textoOpcion2)) {
                it.hapticEliminar()
            } else {
                it.hapticClickPrincipal()
            }
            accionOpcion2()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Decoding and saving the photo run on [Dispatchers.IO]; [scope] should be the caller's view
     * lifecycle scope, so nothing is shown once the screen is gone.
     */
    fun mostrarModalEncuadre(
        context: Context,
        scope: CoroutineScope,
        uriOrigen: Uri,
        onFotoEncuadrada: (Uri) -> Unit
    ) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { cargarBitmapParaEncuadre(context, uriOrigen) }
            if (bitmap == null) {
                onFotoEncuadrada(uriOrigen)
                return@launch
            }

            val (dialog, view) = crearDialogBase(context, R.layout.dialog_ajustar_foto)
            val ivEncuadre = view.findViewById<EncuadreImageView>(R.id.ivEncuadre)
            val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarEncuadre)
            val btnAceptar = view.findViewById<MaterialButton>(R.id.btnAceptarEncuadre)

            ivEncuadre.setSourceBitmap(bitmap)

            btnCancelar.setOnClickListener {
                it.hapticClickPrincipal()
                dialog.dismiss()
            }

            btnAceptar.setOnClickListener {
                it.hapticClickPrincipal()
                btnAceptar.isEnabled = false
                btnCancelar.isEnabled = false
                val recortado = ivEncuadre.recortarBitmap() ?: bitmap
                scope.launch {
                    val uriGuardada = withContext(Dispatchers.IO) { guardarBitmapEncuadrado(context, recortado) }
                    dialog.dismiss()
                    onFotoEncuadrada(uriGuardada ?: uriOrigen)
                }
            }

            dialog.show()
        }
    }

    private fun cargarBitmapParaEncuadre(context: Context, uri: Uri): Bitmap? {
        val temp = File(context.cacheDir, "temp_crop_${UUID.randomUUID()}.tmp")
        try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").copyTo(temp, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, boundsOptions)
            val maxLado = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var muestra = 1
            while (maxLado / muestra > 2048) {
                muestra *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = muestra
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodificado = BitmapFactory.decodeFile(temp.absolutePath, decodeOptions) ?: return null

            val exif = ExifInterface(temp.absolutePath)
            val orientacion = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matriz = Matrix().apply {
                when (orientacion) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                }
            }

            return if (!matriz.isIdentity) {
                val rotado = Bitmap.createBitmap(decodificado, 0, 0, decodificado.width, decodificado.height, matriz, true)
                if (rotado !== decodificado) decodificado.recycle()
                rotado
            } else {
                decodificado
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun guardarBitmapEncuadrado(context: Context, bitmap: Bitmap): Uri? {
        val dir = File(context.cacheDir, "fotos_encuadradas").apply { mkdirs() }
        borrarRecortesViejos(dir)
        val archivo = File(dir, "crop_${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(archivo).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            return Uri.fromFile(archivo)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Crops are copied into filesDir when the project is saved, but the copy here is kept so a
     * failed save can be retried. Anything older than a day is no longer needed.
     */
    private fun borrarRecortesViejos(dir: File) {
        val limite = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        dir.listFiles()?.filter { it.lastModified() < limite }?.forEach { it.delete() }
    }
}