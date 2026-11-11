package com.example.calculadoracrochet.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.example.calculadoracrochet.R
import com.google.android.material.chip.Chip

/** Chips of the folders, the same in every screen that shows or picks them. */
object CarpetasUi {

    /** Folder pictures cut into circles, built once per icon. */
    private val iconosRedondos = HashMap<Int, Bitmap>()

    /** The folder picture as a circle; falls back to the plain drawable for non-bitmap icons. */
    fun iconoRedondo(context: Context, iconoRes: Int): Drawable? {
        val bitmap = iconosRedondos[iconoRes]
            ?: (ContextCompat.getDrawable(context, iconoRes) as? BitmapDrawable)?.bitmap
                ?.also { iconosRedondos[iconoRes] = it }
            ?: return ContextCompat.getDrawable(context, iconoRes)
        return RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
            isCircular = true
            setAntiAlias(true)
        }
    }

    /** Selectable folder with its icon: lavender when off, purple when picked. */
    fun crearChipSeleccionable(padre: ViewGroup, carpeta: CarpetasConfig.CarpetaInfo, seleccionada: Boolean): Chip {
        val chip = LayoutInflater.from(padre.context).inflate(R.layout.chip_carpeta, padre, false) as Chip
        chip.text = carpeta.nombre
        // Round like the folder icons of the dashboard, not the square picture
        chip.chipIcon = iconoRedondo(padre.context, carpeta.iconoRes)
        chip.chipIconTint = null
        chip.isChecked = seleccionada
        chip.setOnCheckedChangeListener { vista, _ -> vista.hapticSeleccion() }
        return chip
    }

    /** Small read-only tag with the folder colors (project cards). */
    fun crearEtiqueta(padre: ViewGroup, carpeta: CarpetasConfig.CarpetaInfo): Chip {
        val contexto = padre.context
        val chip = LayoutInflater.from(contexto).inflate(R.layout.chip_etiqueta, padre, false) as Chip
        chip.text = carpeta.nombre
        chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(contexto, carpeta.colorRes))
        chip.setTextColor(ContextCompat.getColor(contexto, carpeta.textColorRes))
        return chip
    }
}
