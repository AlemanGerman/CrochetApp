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
    fun mostrarModal(
        context: Context,
        titulo: String,
        mensaje: String,
        textoConfirmar: String = "Confirmar",
        iconoResId: Int = android.R.drawable.ic_menu_delete,
        accionConfirmar: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_personalizado, null)

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDialogConfirm)

        tvTitle.text = titulo
        tvMessage.text = mensaje
        btnConfirm.text = textoConfirmar
        ivIcon.setImageResource(iconoResId)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            accionConfirmar()
            dialog.dismiss()
        }

        dialog.show()
    }
}