package com.example.calculadoracrochet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView

class ProyectosAdapter(
    private val listaProyectos: List<ProyectoCrochet>,

    private val onItemClick: (ProyectoCrochet) -> Unit
) : RecyclerView.Adapter<ProyectosAdapter.ProyectoViewHolder>() {

    class ProyectoViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val tvNombre: TextView = view.findViewById(R.id.tvNombreProyecto)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecioProyecto)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivImagen: ShapeableImageView = view.findViewById(R.id.ivImagenProyecto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProyectoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proyecto, parent, false)
        return ProyectoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProyectoViewHolder, position: Int) {
        val proyecto = listaProyectos[position]

        holder.tvNombre.text = proyecto.nombre
        holder.tvPrecio.text = String.format("$%.2f", proyecto.precioFinal)
        holder.tvFecha.text = "Guardado"

        if (!proyecto.fotoUri.isNullOrEmpty()) {
            holder.ivImagen.load(proyecto.fotoUri) {
                crossfade(true)
            }
            holder.ivImagen.imageTintList = null
        } else {
            holder.ivImagen.load(null)
            holder.ivImagen.setImageResource(android.R.drawable.ic_menu_camera)
            holder.ivImagen.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.gray_hint)
            )
        }

        holder.itemView.setOnClickListener {
            onItemClick(proyecto)
        }
    }

    override fun getItemCount() = listaProyectos.size
}