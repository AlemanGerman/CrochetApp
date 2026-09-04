package com.example.calculadoracrochet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object DialogUtils {

    fun crearDialogBase(context: Context, layoutResId: Int): Pair<AlertDialog, View> {
        val view = LayoutInflater.from(context).inflate(layoutResId, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return Pair(dialog, view)
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

        if (iconoResId != null) {
            ivIcono.setImageResource(iconoResId)
            ivIcono.visibility = View.VISIBLE
        } else {
            ivIcono.visibility = View.GONE
        }

        view.findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { dialog.dismiss() }
        btnConfirmar.setOnClickListener {
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

        view.findViewById<MaterialButton>(R.id.btnCancelarInput).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnGuardarInput).setOnClickListener {
            val nuevoTexto = etInput.text.toString().trim()
            if (nuevoTexto.isNotEmpty()) {
                accionGuardar(nuevoTexto)
                dialog.dismiss()
            } else {
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
        if (iconoOpcion2 != 0) {
            btnOp2.setIconResource(iconoOpcion2)
            if (textoOpcion2.contains("Eliminar", ignoreCase = true)) {
                btnOp2.setTextColor(Color.parseColor("#E53935"))
                btnOp2.setStrokeColorResource(android.R.color.transparent)
                btnOp2.setIconTintResource(android.R.color.holo_red_dark)
            }
        }

        view.findViewById<MaterialButton>(R.id.btnCancelarOpciones).setOnClickListener { dialog.dismiss() }

        btnOp1.setOnClickListener {
            accionOpcion1()
            dialog.dismiss()
        }
        btnOp2.setOnClickListener {
            accionOpcion2()
            dialog.dismiss()
        }

        dialog.show()
    }
}