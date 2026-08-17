package com.example.calculadoracrochet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

object DialogUtils {

    /**
     * Función reutilizable para mostrar un modal estético.
     * Puedes cambiar el título, mensaje, texto del botón e icono dependiendo de la situación.
     */
    fun mostrarModal(
        context: Context,
        titulo: String,
        mensaje: String,
        textoConfirmar: String = "Confirmar",
        iconoResId: Int = android.R.drawable.ic_menu_delete,
        accionConfirmar: () -> Unit
    ) {
        // Inflamos nuestro diseño personalizado
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_personalizado, null)

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)
        val dialog = builder.create()

        // ¡CRUCIAL! Hacemos el fondo del AlertDialog transparente para que
        // se vean los bordes redondeados de nuestra MaterialCardView blanca.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Enlazamos los elementos de la vista
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDialogConfirm)

        // Asignamos los textos e icono que recibimos por parámetro
        tvTitle.text = titulo
        tvMessage.text = mensaje
        btnConfirm.text = textoConfirmar
        ivIcon.setImageResource(iconoResId)

        // Acciones de los botones
        btnCancel.setOnClickListener {
            dialog.dismiss() // Solo cierra el modal
        }

        btnConfirm.setOnClickListener {
            accionConfirmar() // Ejecuta la acción que le mandemos (ej. eliminar)
            dialog.dismiss()  // Y luego cierra el modal
        }

        dialog.show()
    }
}