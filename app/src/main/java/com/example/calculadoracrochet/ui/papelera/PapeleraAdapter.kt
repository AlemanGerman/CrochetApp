package com.example.calculadoracrochet.ui.papelera

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticEliminar
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.google.android.material.checkbox.MaterialCheckBox
import com.example.calculadoracrochet.data.model.esRutaFotoValida
import java.util.concurrent.TimeUnit

class PapeleraAdapter(
    private var lista: List<ProyectoCrochet>,
    private val onSeleccionCambiada: (Int) -> Unit,
    private val onRestaurarIndividual: (ProyectoCrochet) -> Unit,
    private val onEliminarIndividual: (ProyectoCrochet) -> Unit
) : RecyclerView.Adapter<PapeleraAdapter.PapeleraViewHolder>() {

    /** Selected by id: every update from Firestore brings new copies of the projects. */
    private val idsSeleccionados = mutableSetOf<String>()

    /** What each row showed, to tell which rows changed when the list is replaced. */
    private var vistos: List<Fila> = lista.map(::Fila)

    private data class Fila(val id: String, val nombre: String, val foto: String?, val fechaEliminado: Long?) {
        constructor(p: ProyectoCrochet) : this(p.id, p.nombre, p.fotoMiniaturaUri ?: p.fotoUri, p.fechaEliminado)
    }

    val seleccionados: List<ProyectoCrochet>
        get() = lista.filter { it.id in idsSeleccionados }

    /**
     * Rows restored or deleted slide away and the others close the gap, instead of the whole list
     * redrawing. The selection keeps the projects still in the trash.
     */
    fun actualizarLista(nuevaLista: List<ProyectoCrochet>) {
        val nuevos = nuevaLista.map(::Fila)
        val anteriores = vistos
        val diferencias = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = anteriores.size
            override fun getNewListSize() = nuevos.size
            override fun areItemsTheSame(viejo: Int, nuevo: Int) = anteriores[viejo].id == nuevos[nuevo].id
            override fun areContentsTheSame(viejo: Int, nuevo: Int) = anteriores[viejo] == nuevos[nuevo]
        })
        lista = nuevaLista
        vistos = nuevos
        idsSeleccionados.retainAll(nuevos.map { it.id }.toSet())
        diferencias.dispatchUpdatesTo(this)
        onSeleccionCambiada(idsSeleccionados.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PapeleraViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_proyecto_papelera, parent, false)
        return PapeleraViewHolder(v)
    }

    override fun onBindViewHolder(holder: PapeleraViewHolder, position: Int) {
        val item = lista[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = lista.size

    inner class PapeleraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbSeleccion: MaterialCheckBox = itemView.findViewById(R.id.cbSeleccionPapelera)
        private val ivFoto: ImageView = itemView.findViewById(R.id.ivFotoPapelera)
        private val tvNombre: TextView = itemView.findViewById(R.id.tvNombrePapelera)
        private val tvDiasRestantes: TextView = itemView.findViewById(R.id.tvDiasRestantes)
        private val btnRestaurar: View = itemView.findViewById(R.id.btnRestaurarIndividual)
        private val btnEliminar: View = itemView.findViewById(R.id.btnEliminarIndividual)

        fun bind(item: ProyectoCrochet) {
            tvNombre.text = item.nombre

            val fotoParaPapelera = item.fotoMiniaturaUri?.takeIf { it.esRutaFotoValida() }
                ?: item.fotoUri?.takeIf { it.esRutaFotoValida() }

            if (fotoParaPapelera != null) {
                ivFoto.scaleType = ImageView.ScaleType.CENTER_CROP
                ivFoto.setPadding(0, 0, 0, 0)
                ivFoto.imageTintList = null
                ivFoto.setBackgroundColor(Color.TRANSPARENT)
                ivFoto.load(fotoParaPapelera) {
                    crossfade(160)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    networkCachePolicy(CachePolicy.ENABLED)
                    listener(
                        onError = { _, _ ->
                            ivFoto.scaleType = ImageView.ScaleType.FIT_CENTER
                            val paddingPx = (10 * itemView.context.resources.displayMetrics.density).toInt()
                            ivFoto.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                            ivFoto.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.lavanda))
                            ivFoto.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.morado)
                            ivFoto.setImageResource(R.drawable.ic_sad)
                        }
                    )
                }
            } else {
                ivFoto.scaleType = ImageView.ScaleType.FIT_CENTER
                val paddingPx = (10 * itemView.context.resources.displayMetrics.density).toInt()
                ivFoto.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                ivFoto.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.lavanda))
                ivFoto.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.morado)
                ivFoto.setImageResource(R.drawable.ic_papelera)
            }

            val fechaEliminado = item.fechaEliminado ?: System.currentTimeMillis()
            val msPasados = (System.currentTimeMillis() - fechaEliminado).coerceAtLeast(0L)
            val diasPasados = TimeUnit.MILLISECONDS.toDays(msPasados)

            // Nothing deletes trashed projects automatically, so show how long it has been there
            tvDiasRestantes.text = when (diasPasados) {
                0L -> "En la papelera desde hoy"
                1L -> "En la papelera desde hace 1 día"
                else -> "En la papelera desde hace $diasPasados días"
            }

            cbSeleccion.setOnCheckedChangeListener(null)
            cbSeleccion.isChecked = item.id in idsSeleccionados

            cbSeleccion.setOnCheckedChangeListener { _, isChecked ->
                cbSeleccion.hapticSeleccion()
                if (isChecked) idsSeleccionados.add(item.id) else idsSeleccionados.remove(item.id)
                onSeleccionCambiada(idsSeleccionados.size)
            }

            itemView.setOnClickListener {
                cbSeleccion.isChecked = !cbSeleccion.isChecked
            }

            btnRestaurar.setOnClickListener {
                it.hapticClickPrincipal()
                onRestaurarIndividual(item)
            }
            btnEliminar.setOnClickListener {
                it.hapticEliminar()
                onEliminarIndividual(item)
            }
        }
    }
}