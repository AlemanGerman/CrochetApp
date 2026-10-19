package com.example.calculadoracrochet.ui.calculadora

import com.example.calculadoracrochet.data.BorradorCalculo
import com.example.calculadoracrochet.data.CalculadoraPrecio
import com.example.calculadoracrochet.data.model.AccesorioGuardado
import com.example.calculadoracrochet.data.model.EstambreGuardado
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.utils.PreferenciasApp
import java.util.UUID

/**
 * The calculation in progress: what the calculator steps fill in and the result screen shows.
 * Plain Kotlin with no Android dependencies, so it can be unit tested.
 */
class CalculoActual {

    var costoMateriales: Double = 0.0
    var costoDiseno: Double = 0.0
    var costoManoObra: Double = 0.0
    var costoEmpaque: Double = 0.0
    var margenGanancia: Double = 0.0
    var ajusteRedondeo: Double = 0.0
    var precioFinalSugerido: Double = 0.0

    var horasTrabajadas: Double = 0.0
    var pagoPorHora: Double = 0.0

    var nombre: String = ""
    var fotoUri: String? = null
    var fotoMiniaturaUri: String? = null

    /** Set when editing a saved project; null for a new project or a duplicate. */
    var idProyectoEnEdicion: String? = null
    var fechaEnEdicion: Long? = null

    val carpetas = mutableListOf<String>()
    val estambres = mutableListOf<EstambreGuardado>()
    val accesorios = mutableListOf<AccesorioGuardado>()

    fun calcularTotal(redondeoActivo: Boolean, multiploRedondeo: Double) {
        val desglose = CalculadoraPrecio.desglosar(
            costoMateriales, costoDiseno, costoManoObra, costoEmpaque, margenGanancia
        )
        val (precio, ajuste) = PreferenciasApp.calcularRedondeo(desglose.totalExacto, redondeoActivo, multiploRedondeo)
        precioFinalSugerido = precio
        ajusteRedondeo = ajuste
    }

    /** Loads a saved project. A duplicate keeps no id or date, so it is saved as a new project. */
    fun cargarDesde(proyecto: ProyectoCrochet, esDuplicado: Boolean) {
        idProyectoEnEdicion = if (esDuplicado) null else proyecto.id
        fechaEnEdicion = if (esDuplicado) null else proyecto.fechaGuardado
        costoMateriales = proyecto.costoMateriales
        costoDiseno = proyecto.costoDiseno
        costoManoObra = proyecto.costoManoObra
        costoEmpaque = proyecto.costoEmpaque
        margenGanancia = proyecto.margenGanancia
        ajusteRedondeo = proyecto.ajusteRedondeo
        horasTrabajadas = proyecto.horasTrabajadas
        pagoPorHora = proyecto.pagoPorHora
        nombre = proyecto.nombre
        fotoUri = proyecto.fotoUri
        fotoMiniaturaUri = proyecto.fotoMiniaturaUri
        carpetas.reemplazarCon(proyecto.carpetas)
        estambres.reemplazarCon(proyecto.listaEstambres)
        accesorios.reemplazarCon(proyecto.listaAccesorios)
    }

    /**
     * Builds the project to save. It still carries the photo it was loaded with:
     * whoever saves it decides the final photo and cleans up the previous one.
     */
    fun aProyecto(nombre: String, ahora: Long = System.currentTimeMillis()) = ProyectoCrochet(
        id = idProyectoEnEdicion ?: UUID.randomUUID().toString(),
        nombre = nombre,
        fotoUri = fotoUri,
        fotoMiniaturaUri = fotoMiniaturaUri,
        fechaGuardado = fechaEnEdicion ?: ahora,
        fechaEliminado = null,
        eliminado = false,
        costoMateriales = costoMateriales,
        costoDiseno = costoDiseno,
        costoManoObra = costoManoObra,
        costoEmpaque = costoEmpaque,
        margenGanancia = margenGanancia,
        ajusteRedondeo = ajusteRedondeo,
        precioFinal = precioFinalSugerido,
        horasTrabajadas = horasTrabajadas,
        pagoPorHora = pagoPorHora,
        listaEstambres = estambres.toList(),
        listaAccesorios = accesorios.toList(),
        carpetas = carpetas.toList()
    )

    fun aBorrador() = BorradorCalculo(
        costoMateriales = costoMateriales,
        costoDiseno = costoDiseno,
        costoManoObra = costoManoObra,
        costoEmpaque = costoEmpaque,
        margenGanancia = margenGanancia,
        horasTrabajadas = horasTrabajadas,
        pagoPorHora = pagoPorHora,
        nombre = nombre,
        fotoUri = fotoUri,
        fotoMiniaturaUri = fotoMiniaturaUri,
        idProyectoEnEdicion = idProyectoEnEdicion,
        fechaEnEdicion = fechaEnEdicion,
        carpetas = carpetas.toSet(),
        estambres = estambres.toList(),
        accesorios = accesorios.toList()
    )

    /** The draft does not store the result; call [calcularTotal] afterwards to get it back. */
    fun aplicar(borrador: BorradorCalculo) {
        costoMateriales = borrador.costoMateriales
        costoDiseno = borrador.costoDiseno
        costoManoObra = borrador.costoManoObra
        costoEmpaque = borrador.costoEmpaque
        margenGanancia = borrador.margenGanancia
        horasTrabajadas = borrador.horasTrabajadas
        pagoPorHora = borrador.pagoPorHora
        nombre = borrador.nombre
        fotoUri = borrador.fotoUri
        fotoMiniaturaUri = borrador.fotoMiniaturaUri
        idProyectoEnEdicion = borrador.idProyectoEnEdicion
        fechaEnEdicion = borrador.fechaEnEdicion
        carpetas.reemplazarCon(borrador.carpetas)
        estambres.reemplazarCon(borrador.estambres)
        accesorios.reemplazarCon(borrador.accesorios)
    }

    fun limpiar() {
        aplicar(BorradorCalculo())
        ajusteRedondeo = 0.0
        precioFinalSugerido = 0.0
    }

    private fun <T> MutableList<T>.reemplazarCon(elementos: Collection<T>) {
        clear()
        addAll(elementos)
    }
}
