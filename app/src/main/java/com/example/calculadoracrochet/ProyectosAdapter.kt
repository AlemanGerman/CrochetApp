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
    // 1. AÑADIMOS ESTO: El adaptador ahora exige que le pases qué hacer al hacer clic
    private val onItemClick: (ProyectoCrochet) -> Unit
) : RecyclerView.Adapter<ProyectosAdapter.ProyectoViewHolder>() {

    class ProyectoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Conectamos con los IDs de tu archivo item_proyecto.xml
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

        // Llenamos los textos de la tarjeta
        holder.tvNombre.text = proyecto.nombre
        holder.tvPrecio.text = String.format("$%.2f", proyecto.precioFinal)
        holder.tvFecha.text = "Guardado" // Aquí puedes poner la fecha real más adelante si gustas

        // 2. Cargamos la foto con Coil si es que la usuaria eligió una
        if (!proyecto.fotoUri.isNullOrEmpty()) {
            holder.ivImagen.load(proyecto.fotoUri) {
                crossfade(true)
            }
            holder.ivImagen.imageTintList = null // Quitamos el tinte para que la foto se vea bien
        } else {
            // SOLUCIÓN: Limpiar la imagen reciclada y regresar al icono de cámara
            holder.ivImagen.load(null) // Esto borra la foto fantasma de la memoria de Coil
            holder.ivImagen.setImageResource(android.R.drawable.ic_menu_camera)
            // Le volvemos a poner el color gris al ícono
            holder.ivImagen.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.gray_hint)
            )
        }

        // 3. LA SOLUCIÓN AL ERROR: Le decimos a la tarjeta que ejecute la función cuando la toquen
        holder.itemView.setOnClickListener {
            onItemClick(proyecto)
        }
    }

    override fun getItemCount() = listaProyectos.size
}