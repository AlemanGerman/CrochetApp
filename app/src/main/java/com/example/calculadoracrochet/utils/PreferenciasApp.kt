package com.example.calculadoracrochet.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenciasApp {

    private const val PREFS_NAME = "crochet_configuracion"

    private const val KEY_REDONDEO_ACTIVO = "cfg_redondeo_activo"
    private const val KEY_MULTIPLO_REDONDEO = "cfg_multiplo_redondeo"
    private const val KEY_VIBRACION_ACTIVA = "cfg_vibracion_activa"

    const val DEFAULT_REDONDEO_ACTIVO = true
    const val DEFAULT_MULTIPLO_REDONDEO = 5.0
    const val DEFAULT_VIBRACION_ACTIVA = true

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isRedondeoActivo(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REDONDEO_ACTIVO, DEFAULT_REDONDEO_ACTIVO)
    }

    fun setRedondeoActivo(context: Context, activo: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REDONDEO_ACTIVO, activo).apply()
    }

    fun getMultiploRedondeo(context: Context): Double {
        return getPrefs(context).getFloat(KEY_MULTIPLO_REDONDEO, DEFAULT_MULTIPLO_REDONDEO.toFloat()).toDouble()
    }

    fun setMultiploRedondeo(context: Context, multiplo: Double) {
        val safeMultiplo = multiplo.coerceAtLeast(0.01).toFloat()
        getPrefs(context).edit().putFloat(KEY_MULTIPLO_REDONDEO, safeMultiplo).apply()
    }

    fun isVibracionActiva(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRACION_ACTIVA, DEFAULT_VIBRACION_ACTIVA)
    }

    fun setVibracionActiva(context: Context, activa: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRACION_ACTIVA, activa).apply()
    }

    /**
     * Calcula el precio redondeado y el ajuste resultante según las preferencias.
     * @return Pair(precioFinal, ajusteRedondeo)
     */
    fun calcularRedondeo(totalExacto: Double, activo: Boolean, multiplo: Double): Pair<Double, Double> {
        if (!activo || multiplo <= 0.0) {
            return Pair(totalExacto, 0.0)
        }
        val safeMultiplo = multiplo.coerceAtLeast(0.01)
        val redondeado = kotlin.math.ceil(totalExacto / safeMultiplo) * safeMultiplo
        val ajuste = (redondeado - totalExacto).let { if (it < 0.001) 0.0 else it }
        return Pair(redondeado, ajuste)
    }

    fun formatearMultiplo(multiplo: Double): String {
        return if (multiplo % 1.0 == 0.0) {
            String.format(java.util.Locale.US, "$%.0f", multiplo)
        } else {
            String.format(java.util.Locale.US, "$%.2f", multiplo)
        }
    }
}
