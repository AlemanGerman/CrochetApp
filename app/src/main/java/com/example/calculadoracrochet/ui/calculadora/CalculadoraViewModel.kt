package com.example.calculadoracrochet.ui.calculadora

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.calculadoracrochet.data.BorradorRepository
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.utils.PreferenciasApp

/**
 * Calculator flow (materials → pattern → labor → packaging → result).
 * Shared by its steps at activity scope; the draft keeps the calculation if the app is killed.
 * Saving to Firestore is up to ProyectosViewModel, which receives the project from [prepararProyecto].
 */
class CalculadoraViewModel(application: Application) : AndroidViewModel(application) {

    private val borradorRepo = BorradorRepository(application)

    val calculo = CalculoActual()

    init {
        restaurarBorrador()
    }

    fun calcularTotal() {
        val context = getApplication<Application>()
        calculo.calcularTotal(
            redondeoActivo = PreferenciasApp.isRedondeoActivo(context),
            multiploRedondeo = PreferenciasApp.getMultiploRedondeo(context)
        )
    }

    /** Loads a saved project into the calculation. A duplicate gets a new id and date when saved. */
    fun cargarProyectoParaEdicion(proyecto: ProyectoCrochet, esDuplicado: Boolean) {
        calculo.cargarDesde(proyecto, esDuplicado)
        guardarBorrador()
    }

    fun prepararProyecto(nombre: String, carpetas: List<String>): ProyectoCrochet {
        calculo.carpetas.clear()
        calculo.carpetas.addAll(carpetas)
        return calculo.aProyecto(nombre)
    }

    fun limpiarCalculoActual() {
        calculo.limpiar()
        borradorRepo.limpiar()
    }

    fun guardarBorrador() {
        borradorRepo.guardar(calculo.aBorrador())
    }

    private fun restaurarBorrador() {
        val borrador = borradorRepo.cargar() ?: return
        calculo.aplicar(borrador)
        calcularTotal()
    }
}
