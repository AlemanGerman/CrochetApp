package com.example.calculadoracrochet.ui.configuracion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.AccesorioFavorito
import com.example.calculadoracrochet.data.EstambreFavorito
import com.example.calculadoracrochet.data.MaterialesFavoritosRepository
import com.example.calculadoracrochet.ui.MainActivity
import com.example.calculadoracrochet.utils.DialogUtils
import com.example.calculadoracrochet.utils.aNumeroOInvalido
import com.example.calculadoracrochet.utils.animarCambiosDeLayout
import com.example.calculadoracrochet.utils.comoTextoEditable
import com.example.calculadoracrochet.utils.errorSiNoPositivo
import com.example.calculadoracrochet.utils.errorSiVacio
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.utils.hapticError
import com.example.calculadoracrochet.utils.hundirAlPresionar
import com.example.calculadoracrochet.utils.rellenarBajoBarraDeEstado
import com.example.calculadoracrochet.utils.rellenarSobreBarraDeNavegacion
import com.example.calculadoracrochet.utils.validar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

/** The user's own yarns and accessories, offered as one-tap chips on the materials step. */
class MisMaterialesFragment : Fragment(R.layout.fragment_mis_materiales) {

    private var estambres: List<EstambreFavorito> = emptyList()
    private var accesorios: List<AccesorioFavorito> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val contexto = requireContext()

        view.findViewById<View>(R.id.contenidoEncabezado).rellenarBajoBarraDeEstado()
        view.findViewById<View>(R.id.hojaMisMateriales).apply {
            clipToOutline = true
            rellenarSobreBarraDeNavegacion()
        }
        view.findViewById<View>(R.id.btnVolverMisMateriales).setOnClickListener {
            it.hapticClickPrincipal()
            (requireActivity() as MainActivity).regresarDeslizando()
        }

        estambres = MaterialesFavoritosRepository.estambres(contexto)
        accesorios = MaterialesFavoritosRepository.accesorios(contexto)
        pintarEstambres(animar = false)
        pintarAccesorios(animar = false)

        view.findViewById<View>(R.id.btnNuevoEstambreFavorito).setOnClickListener {
            it.hapticClickPrincipal()
            editarEstambre(null)
        }
        view.findViewById<View>(R.id.btnNuevoAccesorioFavorito).setOnClickListener {
            it.hapticClickPrincipal()
            editarAccesorio(null)
        }
    }

    // region Lists

    private fun pintarEstambres(animar: Boolean) {
        val vista = view ?: return
        pintarLista(
            contenedor = vista.findViewById(R.id.llEstambresFavoritos),
            vacio = vista.findViewById(R.id.tvSinEstambres),
            animar = animar,
            elementos = estambres.map { estambre ->
                Fila(
                    nombre = estambre.nombre,
                    detalle = "$${estambre.costoMadeja.comoTextoEditable()} la madeja de ${estambre.gramosMadeja.comoTextoEditable()} g",
                    icono = R.drawable.ic_material_detalle,
                    alEditar = { editarEstambre(estambre) },
                    alEliminar = {
                        estambres = estambres.filterNot { it.id == estambre.id }
                        MaterialesFavoritosRepository.guardarEstambres(requireContext(), estambres)
                        pintarEstambres(animar = true)
                    }
                )
            }
        )
    }

    private fun pintarAccesorios(animar: Boolean) {
        val vista = view ?: return
        pintarLista(
            contenedor = vista.findViewById(R.id.llAccesoriosFavoritos),
            vacio = vista.findViewById(R.id.tvSinAccesorios),
            animar = animar,
            elementos = accesorios.map { accesorio ->
                Fila(
                    nombre = accesorio.nombre,
                    detalle = if (accesorio.porCantidad) {
                        "$${accesorio.costo.comoTextoEditable()} el paquete de ${accesorio.cantidadPaquete!!.comoTextoEditable()}"
                    } else {
                        "$${accesorio.costo.comoTextoEditable()} por pieza"
                    },
                    icono = R.drawable.ic_estrella,
                    alEditar = { editarAccesorio(accesorio) },
                    alEliminar = {
                        accesorios = accesorios.filterNot { it.id == accesorio.id }
                        MaterialesFavoritosRepository.guardarAccesorios(requireContext(), accesorios)
                        pintarAccesorios(animar = true)
                    }
                )
            }
        )
    }

    private class Fila(
        val nombre: String,
        val detalle: String,
        val icono: Int,
        val alEditar: () -> Unit,
        val alEliminar: () -> Unit
    )

    private fun pintarLista(contenedor: LinearLayout, vacio: View, animar: Boolean, elementos: List<Fila>) {
        if (animar) contenedor.animarCambiosDeLayout()
        contenedor.removeAllViews()
        vacio.visibility = if (elementos.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(contenedor.context)
        elementos.forEach { fila ->
            val vista = inflater.inflate(R.layout.item_material_favorito, contenedor, false)
            vista.findViewById<TextView>(R.id.tvNombreFavorito).text = fila.nombre
            vista.findViewById<TextView>(R.id.tvDetalleFavorito).text = fila.detalle
            vista.findViewById<ImageView>(R.id.ivIconoFavorito).setImageResource(fila.icono)
            vista.contentDescription = "${fila.nombre}, ${fila.detalle}. Toca para editar"
            vista.hundirAlPresionar()
            vista.setOnClickListener {
                it.hapticClickPrincipal()
                fila.alEditar()
            }
            vista.findViewById<View>(R.id.btnEliminarFavorito).apply {
                contentDescription = "Eliminar ${fila.nombre}"
                setOnClickListener {
                    it.hapticEliminar()
                    DialogUtils.mostrarAlerta(
                        context = requireContext(),
                        titulo = "Quitar de tus materiales",
                        mensaje = "¿Quitar '${fila.nombre}'? Los proyectos que ya lo usan no cambian.",
                        textoConfirmar = "Quitar"
                    ) { fila.alEliminar() }
                }
            }
            contenedor.addView(vista)
        }
    }

    // endregion

    // region Add / edit

    private fun editarEstambre(actual: EstambreFavorito?) {
        val (dialog, vista) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_material_favorito)
        val campos = CamposFavorito(vista)
        vista.findViewById<View>(R.id.tgTipoAccesorio).visibility = View.GONE
        vista.findViewById<TextView>(R.id.tvTituloFavorito).text = if (actual == null) "Nuevo estambre" else "Editar estambre"
        campos.tilNombre.hint = "Color o marca"
        campos.tilCosto.hint = "Costo de la madeja"
        campos.tilCantidad.hint = "Gramos de la madeja"
        campos.tilCantidad.suffixText = "g"
        vista.findViewById<TextView>(R.id.tvAyudaFavorito).text = "Al usarlo solo pondrás los gramos que gastaste."

        actual?.let {
            campos.etNombre.setText(it.nombre)
            campos.etCosto.setText(it.costoMadeja.comoTextoEditable())
            campos.etCantidad.setText(it.gramosMadeja.comoTextoEditable())
        }

        campos.alCancelar { dialog.dismiss() }
        campos.alGuardar(conCantidad = { true }) { nombre, costo, cantidad ->
            val nuevo = EstambreFavorito(
                id = actual?.id ?: java.util.UUID.randomUUID().toString(),
                nombre = nombre,
                costoMadeja = costo,
                gramosMadeja = cantidad!!
            )
            estambres = if (actual == null) estambres + nuevo else estambres.map { if (it.id == actual.id) nuevo else it }
            MaterialesFavoritosRepository.guardarEstambres(requireContext(), estambres)
            pintarEstambres(animar = true)
            dialog.dismiss()
        }
        dialog.show()
        if (actual == null) campos.enfocarNombre()
    }

    private fun editarAccesorio(actual: AccesorioFavorito?) {
        val (dialog, vista) = DialogUtils.crearDialogBase(requireContext(), R.layout.dialog_material_favorito)
        val campos = CamposFavorito(vista)
        val tgTipo = vista.findViewById<MaterialButtonToggleGroup>(R.id.tgTipoAccesorio)
        val tvAyuda = vista.findViewById<TextView>(R.id.tvAyudaFavorito)
        vista.findViewById<TextView>(R.id.tvTituloFavorito).text = if (actual == null) "Nuevo accesorio" else "Editar accesorio"
        campos.tilNombre.hint = "Nombre (relleno, ojos…)"

        fun mostrarTipo(porCantidad: Boolean) {
            campos.tilCantidad.visibility = if (porCantidad) View.VISIBLE else View.GONE
            campos.tilCosto.hint = if (porCantidad) "Costo del paquete" else "Costo por pieza"
            campos.tilCantidad.hint = "Cantidad del paquete (gramos, piezas…)"
            campos.etCosto.imeOptions = if (porCantidad) android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            else android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            tvAyuda.text = if (porCantidad) "Al usarlo solo pondrás cuánto gastaste del paquete."
            else "Se añade con su precio, listo."
        }

        val inicioPorCantidad = actual?.porCantidad == true
        tgTipo.check(if (inicioPorCantidad) R.id.btnTipoCantidad else R.id.btnTipoPieza)
        mostrarTipo(inicioPorCantidad)
        tgTipo.addOnButtonCheckedListener { grupo, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            grupo.hapticClickPrincipal()
            (grupo.parent as? android.view.ViewGroup)?.animarCambiosDeLayout()
            mostrarTipo(id == R.id.btnTipoCantidad)
        }

        actual?.let {
            campos.etNombre.setText(it.nombre)
            campos.etCosto.setText(it.costo.comoTextoEditable())
            it.cantidadPaquete?.let { cantidad -> campos.etCantidad.setText(cantidad.comoTextoEditable()) }
        }

        campos.alCancelar { dialog.dismiss() }
        campos.alGuardar(conCantidad = { tgTipo.checkedButtonId == R.id.btnTipoCantidad }) { nombre, costo, cantidad ->
            val nuevo = AccesorioFavorito(
                id = actual?.id ?: java.util.UUID.randomUUID().toString(),
                nombre = nombre,
                costo = costo,
                cantidadPaquete = cantidad
            )
            accesorios = if (actual == null) accesorios + nuevo else accesorios.map { if (it.id == actual.id) nuevo else it }
            MaterialesFavoritosRepository.guardarAccesorios(requireContext(), accesorios)
            pintarAccesorios(animar = true)
            dialog.dismiss()
        }
        dialog.show()
        if (actual == null) campos.enfocarNombre()
    }

    /** The name / cost / amount fields shared by both dialogs, with their checks. */
    private class CamposFavorito(private val vista: View) {
        val tilNombre: TextInputLayout = vista.findViewById(R.id.tilNombreFavorito)
        val tilCosto: TextInputLayout = vista.findViewById(R.id.tilCostoFavorito)
        val tilCantidad: TextInputLayout = vista.findViewById(R.id.tilCantidadFavorito)
        val etNombre: EditText = vista.findViewById(R.id.etNombreFavorito)
        val etCosto: EditText = vista.findViewById(R.id.etCostoFavorito)
        val etCantidad: EditText = vista.findViewById(R.id.etCantidadFavorito)

        fun alCancelar(accion: () -> Unit) {
            vista.findViewById<MaterialButton>(R.id.btnCancelarFavorito).setOnClickListener {
                it.hapticClickPrincipal()
                accion()
            }
        }

        /** [alValidar] gets the amount only when [conCantidad] says the amount field is in use. */
        fun alGuardar(conCantidad: () -> Boolean, alValidar: (nombre: String, costo: Double, cantidad: Double?) -> Unit) {
            vista.findViewById<MaterialButton>(R.id.btnGuardarFavorito).setOnClickListener {
                val nombre = etNombre.text.toString().trim()
                val costo = etCosto.text.toString().aNumeroOInvalido()
                val usaCantidad = conCantidad()
                val cantidad = etCantidad.text.toString().aNumeroOInvalido()

                var correcto = tilNombre.validar(errorSiVacio(nombre, "Ponle un nombre"))
                correcto = tilCosto.validar(errorSiNoPositivo(costo, "Debe ser mayor a $0")) && correcto
                correcto = (!usaCantidad || tilCantidad.validar(errorSiNoPositivo(cantidad, "Debe ser mayor a 0"))) && correcto
                if (!usaCantidad) tilCantidad.error = null

                if (correcto) {
                    it.hapticClickPrincipal()
                    alValidar(nombre, costo, if (usaCantidad) cantidad else null)
                } else {
                    it.hapticError()
                }
            }
        }

        fun enfocarNombre() {
            etNombre.post {
                etNombre.requestFocus()
                androidx.core.view.ViewCompat.getWindowInsetsController(etNombre)
                    ?.show(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
        }
    }

    // endregion
}
