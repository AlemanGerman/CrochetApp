package com.example.calculadoracrochet.data

import android.content.Context
import com.example.calculadoracrochet.utils.comoTextoEditable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A yarn bought often: the materials step fills a row with it and only the grams used are left. */
data class EstambreFavorito(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val costoMadeja: Double,
    val gramosMadeja: Double
) {
    val etiqueta: String
        get() = "$nombre · $${costoMadeja.comoTextoEditable()} · ${gramosMadeja.comoTextoEditable()} g"
}

/**
 * An accessory used often (stuffing, safety eyes…). With [cantidadPaquete] it is charged by the
 * part used of a package; without it, [costo] is a fixed price per piece.
 */
data class AccesorioFavorito(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val costo: Double,
    val cantidadPaquete: Double? = null
) {
    val porCantidad: Boolean
        get() = cantidadPaquete != null && cantidadPaquete > 0.0

    val etiqueta: String
        get() = if (porCantidad) "$nombre · $${costo.comoTextoEditable()} por ${cantidadPaquete!!.comoTextoEditable()}"
        else "$nombre · $${costo.comoTextoEditable()}"
}

/** The user's own yarns and accessories, kept on the phone (and in its Android backup). */
object MaterialesFavoritosRepository {

    private const val PREFS = "crochet_materiales_favoritos"
    private const val KEY_ESTAMBRES = "estambres"
    private const val KEY_ACCESORIOS = "accesorios"

    /** First run only: the yarn that was offered before the list became editable. */
    private val ESTAMBRES_INICIALES = listOf(
        EstambreFavorito(nombre = "Sinfonía", costoMadeja = 100.0, gramosMadeja = 100.0)
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun estambres(context: Context): List<EstambreFavorito> {
        val json = prefs(context).getString(KEY_ESTAMBRES, null) ?: return ESTAMBRES_INICIALES
        return leerLista(json) { o ->
            EstambreFavorito(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                nombre = o.getString("nombre"),
                costoMadeja = o.getDouble("costoMadeja"),
                gramosMadeja = o.getDouble("gramosMadeja")
            )
        }
    }

    fun accesorios(context: Context): List<AccesorioFavorito> {
        val json = prefs(context).getString(KEY_ACCESORIOS, null) ?: return emptyList()
        return leerLista(json) { o ->
            AccesorioFavorito(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                nombre = o.getString("nombre"),
                costo = o.getDouble("costo"),
                cantidadPaquete = if (o.has("cantidadPaquete")) o.getDouble("cantidadPaquete") else null
            )
        }
    }

    fun guardarEstambres(context: Context, lista: List<EstambreFavorito>) {
        val json = JSONArray()
        lista.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("nombre", it.nombre)
                    .put("costoMadeja", it.costoMadeja)
                    .put("gramosMadeja", it.gramosMadeja)
            )
        }
        prefs(context).edit().putString(KEY_ESTAMBRES, json.toString()).apply()
    }

    fun guardarAccesorios(context: Context, lista: List<AccesorioFavorito>) {
        val json = JSONArray()
        lista.forEach {
            val o = JSONObject().put("id", it.id).put("nombre", it.nombre).put("costo", it.costo)
            if (it.porCantidad) o.put("cantidadPaquete", it.cantidadPaquete)
            json.put(o)
        }
        prefs(context).edit().putString(KEY_ACCESORIOS, json.toString()).apply()
    }

    /** A damaged entry is skipped instead of losing the whole list. */
    private fun <T> leerLista(json: String, leer: (JSONObject) -> T): List<T> {
        val arreglo = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arreglo.length()).mapNotNull { i ->
            runCatching { leer(arreglo.getJSONObject(i)) }.getOrNull()
        }
    }
}
