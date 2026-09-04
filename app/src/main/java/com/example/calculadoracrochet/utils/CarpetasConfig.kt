package com.example.calculadoracrochet.utils

import com.example.calculadoracrochet.R

object CarpetasConfig {
    data class CarpetaInfo(
        val nombre: String,
        val iconoRes: Int,
        val colorRes: Int,
        val textColorRes: Int
    )

    val listaCarpetas = listOf(
        CarpetaInfo("Todos", R.drawable.ic_carpeta_todos, R.color.purple_primary, R.color.white),
        CarpetaInfo("Amigurumi", R.drawable.ic_carpeta_amigurumi, R.color.green_mina, R.color.white),
        CarpetaInfo("Llaveros", R.drawable.ic_carpeta_llaveros, R.color.white_mina, R.color.black),
        CarpetaInfo("Peluches", R.drawable.ic_carpeta_peluches, R.color.purple_mina, R.color.white),
        CarpetaInfo("Ramos", R.drawable.ic_carpeta_ramos, R.color.green_mina, R.color.white),
        CarpetaInfo("Accesorios", R.drawable.ic_carpeta_accesorios, R.color.purple_mina, R.color.white),
        CarpetaInfo("Extras", R.drawable.ic_carpeta_extras, R.color.yellow_mina, R.color.black)
    )

    fun obtenerNombresSinTodos(): List<String> = listaCarpetas.filter { it.nombre != "Todos" }.map { it.nombre }
}