package com.example.calculadoracrochet.ui

import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.example.calculadoracrochet.viewmodel.estaEnPapelera

enum class CriterioOrden {
    FECHA,
    PRECIO,
    FOTO,
    NOMBRE
}

/** Pure filtering/sorting of the dashboard list, kept apart from the Fragment so it can be unit tested. */
object FiltroProyectos {

    const val CARPETA_TODOS = "Todos"

    fun aplicar(
        proyectos: List<ProyectoCrochet>,
        textoBusqueda: String,
        carpeta: String,
        criterio: CriterioOrden,
        descendente: Boolean
    ): List<ProyectoCrochet> {
        val busqueda = textoBusqueda.lowercase().trim()

        val filtrados = proyectos.filter { proyecto ->
            !proyecto.estaEnPapelera &&
                proyecto.nombre.lowercase().contains(busqueda) &&
                (carpeta == CARPETA_TODOS || (proyecto.carpetas ?: emptyList()).contains(carpeta))
        }

        return filtrados.sortedWith(comparador(criterio, descendente))
    }

    private fun comparador(criterio: CriterioOrden, descendente: Boolean): Comparator<ProyectoCrochet> {
        val primario: Comparator<ProyectoCrochet> = when (criterio) {
            CriterioOrden.FECHA -> compareBy { it.fechaGuardado }
            CriterioOrden.PRECIO -> compareBy { it.precioFinal }
            CriterioOrden.FOTO -> compareBy { !it.fotoUri.isNullOrEmpty() }
            CriterioOrden.NOMBRE -> compareBy { it.nombre.lowercase().trim() }
        }

        // For names the "descending" arrow means A→Z, which is what users expect from the default state
        val invertir = if (criterio == CriterioOrden.NOMBRE) !descendente else descendente
        val ordenado = if (invertir) primario.reversed() else primario

        // Ties always show the most recent project first
        return ordenado.then(compareByDescending { it.fechaGuardado })
    }
}
