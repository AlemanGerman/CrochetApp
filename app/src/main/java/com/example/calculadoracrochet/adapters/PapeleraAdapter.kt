package com.example.calculadoracrochet.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.android.material.checkbox.MaterialCheckBox

class PapeleraAdapter(
    private var lista: List<ProyectoCrochet>,
    private val onSeleccionCambiada: (Int) -> Unit,
    private val onRestaurarIndividual: (ProyectoCrochet) -> Unit,
    private val onEliminarIndividual: (ProyectoCrochet) -> Unit
) : RecyclerView.Adapter<PapeleraAdapter.PapeleraViewHolder>() {

    val seleccionados = mutableSetOf<ProyectoCrochet>()

    fun actualizarLista(nuevaLista: List<ProyectoCrochet>) {
        lista = nuevaLista
        seleccionados.clear()
        notifyDataSetChanged()
        onSeleccionCambiada(0)
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
        private val btnRestaurar: ImageButton = itemView.findViewById(R.id.btnRestaurarIndividual)
        private val btnEliminar: ImageButton = itemView.findViewById(R.id.btnEliminarIndividual)

        fun bind(item: ProyectoCrochet) {
            tvNombre.text = item.nombre

            if (!item.fotoUri.isNullOrEmpty()) {
                ivFoto.load(item.fotoUri) { crossfade(true) }
            } else {
                ivFoto.setImageResource(R.drawable.ic_sad)
            }

            val fechaEliminado = item.fechaEliminado ?: System.currentTimeMillis()
            val msPasados = System.currentTimeMillis() - fechaEliminado
            val diasPasados = (msPasados / (1000 * 60 * 60 * 24)).toInt()
            val diasRestantes = (30 - diasPasados).coerceAtLeast(0)

            tvDiasRestantes.text = "Se eliminará en $diasRestantes días"

            cbSeleccion.setOnCheckedChangeListener(null)
            cbSeleccion.isChecked = seleccionados.contains(item)

            cbSeleccion.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) seleccionados.add(item) else seleccionados.remove(item)
                onSeleccionCambiada(seleccionados.size)
            }

            btnRestaurar.setOnClickListener { onRestaurarIndividual(item) }
            btnEliminar.setOnClickListener { onEliminarIndividual(item) }
        }
    }
}