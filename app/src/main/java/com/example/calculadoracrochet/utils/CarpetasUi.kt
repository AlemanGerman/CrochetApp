package com.example.calculadoracrochet.utils

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.calculadoracrochet.R
import com.google.android.material.chip.Chip

/** Chips of the folders, the same in every screen that shows or picks them. */
object CarpetasUi {

    /** Selectable folder with its icon: lavender when off, purple when picked. */
    fun crearChipSeleccionable(padre: ViewGroup, carpeta: CarpetasConfig.CarpetaInfo, seleccionada: Boolean): Chip {
        val chip = LayoutInflater.from(padre.context).inflate(R.layout.chip_carpeta, padre, false) as Chip
        chip.text = carpeta.nombre
        chip.setChipIconResource(carpeta.iconoRes)
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
