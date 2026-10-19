package com.example.calculadoracrochet.data

import android.content.Context
import android.content.SharedPreferences
import com.example.calculadoracrochet.viewmodel.AccesorioGuardado
import com.example.calculadoracrochet.viewmodel.EstambreGuardado
import org.json.JSONArray
import org.json.JSONObject

/** Snapshot of the in-progress calculation so it survives the app being killed. */
data class BorradorCalculo(
    val costoMateriales: Double = 0.0,
    val costoDiseno: Double = 0.0,
    val costoManoObra: Double = 0.0,
    val costoEmpaque: Double = 0.0,
    val margenGanancia: Double = 0.0,
    val horasTrabajadas: Double = 0.0,
    val pagoPorHora: Double = 0.0,
    val nombre: String = "",
    val fotoUri: String? = null,
    val fotoMiniaturaUri: String? = null,
    val idProyectoEnEdicion: String? = null,
    val fechaEnEdicion: Long? = null,
    val carpetas: Set<String> = emptySet(),
    val estambres: List<EstambreGuardado> = emptyList(),
    val accesorios: List<AccesorioGuardado> = emptyList()
)

/**
 * Persists [BorradorCalculo] in SharedPreferences.
 * Keys and value types match the original format so existing drafts keep loading.
 */
class BorradorRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)

    fun guardar(borrador: BorradorCalculo) {
        prefs.edit()
            .putFloat("costoMateriales", borrador.costoMateriales.toFloat())
            .putFloat("costoDiseno", borrador.costoDiseno.toFloat())
            .putFloat("costoManoObra", borrador.costoManoObra.toFloat())
            .putFloat("costoEmpaque", borrador.costoEmpaque.toFloat())
            .putFloat("margenGanancia", borrador.margenGanancia.toFloat())
            .putFloat("horasTrabajadasTemp", borrador.horasTrabajadas.toFloat())
            .putFloat("pagoPorHoraTemp", borrador.pagoPorHora.toFloat())
            .putString("nombreTemp", borrador.nombre)
            .putString("fotoUriTemp", borrador.fotoUri)
            .putString("fotoMiniaturaUriTemp", borrador.fotoMiniaturaUri)
            .putString("idProyectoEnEdicion", borrador.idProyectoEnEdicion)
            .putLong("fechaEnEdicion", borrador.fechaEnEdicion ?: SIN_FECHA)
            .putStringSet("carpetasTemp", borrador.carpetas)
            .putString("estambres", estambresAJson(borrador.estambres))
            .putString("accesorios", accesoriosAJson(borrador.accesorios))
            .apply()
    }

    /** Returns null when no draft has been saved. */
    fun cargar(): BorradorCalculo? {
        if (!prefs.contains("costoMateriales") && !prefs.contains("horasTrabajadasTemp")) return null

        return BorradorCalculo(
            costoMateriales = leerDouble("costoMateriales"),
            costoDiseno = leerDouble("costoDiseno"),
            costoManoObra = leerDouble("costoManoObra"),
            costoEmpaque = leerDouble("costoEmpaque"),
            margenGanancia = leerDouble("margenGanancia"),
            horasTrabajadas = leerDouble("horasTrabajadasTemp"),
            pagoPorHora = leerDouble("pagoPorHoraTemp"),
            nombre = prefs.getString("nombreTemp", "") ?: "",
            fotoUri = prefs.getString("fotoUriTemp", null),
            fotoMiniaturaUri = prefs.getString("fotoMiniaturaUriTemp", null),
            idProyectoEnEdicion = prefs.getString("idProyectoEnEdicion", null),
            fechaEnEdicion = prefs.getLong("fechaEnEdicion", SIN_FECHA).takeIf { it != SIN_FECHA },
            carpetas = prefs.getStringSet("carpetasTemp", emptySet()) ?: emptySet(),
            estambres = leerLista("estambres") { obj ->
                EstambreGuardado(
                    nombre = obj.optString("nombre"),
                    costo = obj.optString("costo"),
                    totales = obj.optString("totales"),
                    usados = obj.optString("usados")
                )
            },
            accesorios = leerLista("accesorios") { obj ->
                AccesorioGuardado(
                    nombre = obj.optString("nombre"),
                    costo = obj.optString("costo"),
                    esCalculado = obj.optBoolean("esCalculado", false),
                    costoPaquete = obj.optString("costoPaquete"),
                    cantidadTotal = obj.optString("cantidadTotal"),
                    cantidadUsada = obj.optString("cantidadUsada")
                )
            }
        )
    }

    fun limpiar() {
        prefs.edit().clear().apply()
    }

    private fun leerDouble(clave: String): Double = prefs.getFloat(clave, 0f).toDouble()

    private fun <T> leerLista(clave: String, mapear: (JSONObject) -> T): List<T> {
        val json = prefs.getString(clave, null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { mapear(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun estambresAJson(estambres: List<EstambreGuardado>): String =
        JSONArray().apply {
            estambres.forEach { est ->
                put(
                    JSONObject()
                        .put("nombre", est.nombre)
                        .put("costo", est.costo)
                        .put("totales", est.totales)
                        .put("usados", est.usados)
                )
            }
        }.toString()

    private fun accesoriosAJson(accesorios: List<AccesorioGuardado>): String =
        JSONArray().apply {
            accesorios.forEach { acc ->
                put(
                    JSONObject()
                        .put("nombre", acc.nombre)
                        .put("costo", acc.costo)
                        .put("esCalculado", acc.esCalculado)
                        .put("costoPaquete", acc.costoPaquete)
                        .put("cantidadTotal", acc.cantidadTotal)
                        .put("cantidadUsada", acc.cantidadUsada)
                )
            }
        }.toString()

    private companion object {
        const val NOMBRE_PREFS = "crochet_borrador"
        const val SIN_FECHA = -1L
    }
}
