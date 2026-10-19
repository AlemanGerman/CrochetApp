package com.example.calculadoracrochet.ui.estadisticas

import com.example.calculadoracrochet.data.model.ProyectoCrochet

data class ConteoCarpeta(
    val nombre: String,
    val conteo: Int,
    val porcentaje: Int
)

data class ResumenEstadisticas(
    val totalProyectos: Int,
    val totalVentas: Double,
    val masCostoso: ProyectoCrochet?,
    val masHoras: ProyectoCrochet?,
    val carpetas: List<ConteoCarpeta>
)

/** Pure statistics of the active projects, kept apart from the Fragment so it can be unit tested. */
object EstadisticasProyectos {

    /** [carpetas] come back in the same order as [nombresCarpetas], including empty ones. */
    fun calcular(proyectos: List<ProyectoCrochet>, nombresCarpetas: List<String>): ResumenEstadisticas {
        val conteos = nombresCarpetas.map { nombre -> proyectos.count { nombre in it.carpetas } }
        val porcentajes = porcentajesQueSuman100(conteos)

        return ResumenEstadisticas(
            totalProyectos = proyectos.size,
            totalVentas = proyectos.sumOf { it.precioFinal },
            masCostoso = proyectos.maxByOrNull { it.precioFinal },
            masHoras = proyectos.maxByOrNull { it.horasTrabajadas },
            carpetas = nombresCarpetas.indices.map { i ->
                ConteoCarpeta(nombresCarpetas[i], conteos[i], porcentajes[i])
            }
        )
    }

    /**
     * Whole percentages that add up to exactly 100 (largest remainder method): every share is
     * rounded down and the missing points go to the shares with the biggest decimal part.
     * All zeros when there is nothing to count.
     */
    fun porcentajesQueSuman100(conteos: List<Int>): List<Int> {
        val total = conteos.sum()
        if (total <= 0) return conteos.map { 0 }

        val exactos = conteos.map { (it.toDouble() / total) * 100.0 }
        val resultado = exactos.map { it.toInt() }.toMutableList()
        val faltante = 100 - resultado.sum()

        conteos.indices
            .filter { conteos[it] > 0 }
            .sortedByDescending { exactos[it] - resultado[it] }
            .take(faltante)
            .forEach { resultado[it]++ }

        return resultado
    }
}
