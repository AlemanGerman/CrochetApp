package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.R

object CarpetasConfig {
    const val CARPETA_TODOS = "Todos"

    /**
     * [colorRes] and [textColorRes] are the background and text of the folder tag on the project cards;
     * [colorGraficaRes] is its slice in the statistics chart, the color of its icon.
     */
    data class CarpetaInfo(
        val nombre: String,
        val iconoRes: Int,
        val colorRes: Int,
        val textColorRes: Int,
        val colorGraficaRes: Int = R.color.morado
    )

    val listaCarpetas = listOf(
        CarpetaInfo(CARPETA_TODOS, R.drawable.ic_carpeta_todos, R.color.etiqueta_morado_fondo, R.color.etiqueta_morado_texto, R.color.morado),
        CarpetaInfo("Amigurumi", R.drawable.ic_carpeta_amigurumi, R.color.etiqueta_verde_fondo, R.color.etiqueta_verde_texto, R.color.verde),
        CarpetaInfo("Llaveros", R.drawable.ic_carpeta_llaveros, R.color.etiqueta_crema_fondo, R.color.etiqueta_crema_texto, R.color.grafica_arena),
        CarpetaInfo("Peluches", R.drawable.ic_carpeta_peluches, R.color.etiqueta_morado_fondo, R.color.etiqueta_morado_texto, R.color.morado),
        CarpetaInfo("Ramos", R.drawable.ic_carpeta_ramos, R.color.etiqueta_verde_fondo, R.color.etiqueta_verde_texto, R.color.grafica_verde_claro),
        CarpetaInfo("Accesorios", R.drawable.ic_carpeta_accesorios, R.color.etiqueta_morado_fondo, R.color.etiqueta_morado_texto, R.color.morado_fuerte),
        CarpetaInfo("Extras", R.drawable.ic_carpeta_extras, R.color.etiqueta_amarillo_fondo, R.color.etiqueta_amarillo_texto, R.color.amarillo)
    )

    fun obtenerNombresSinTodos(): List<String> = listaCarpetas.filter { it.nombre != CARPETA_TODOS }.map { it.nombre }
}