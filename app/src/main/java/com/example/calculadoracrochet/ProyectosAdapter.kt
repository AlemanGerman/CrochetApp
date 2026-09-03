package com.example.calculadoracrochet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProyectosAdapter(
    private var listaProyectos: List<ProyectoCrochet>,
    private val onItemClick: (ProyectoCrochet) -> Unit,
    private val onEditCarpetaClick: ((ProyectoCrochet) -> Unit)? = null
) : RecyclerView.Adapter<ProyectosAdapter.ProyectoViewHolder>() {

    class ProyectoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreProyecto)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecioProyecto)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivImagen: ShapeableImageView = view.findViewById(R.id.ivImagenProyecto)
        val llContenedorTags: LinearLayout = view.findViewById(R.id.llContenedorTags)
        val btnAgregarCarpetaVacio: ImageButton = view.findViewById(R.id.btnAgregarCarpetaVacio)
        val chipAmigurumi: Chip = view.findViewById(R.id.chipAmigurumi)
        val chipLlaveros: Chip = view.findViewById(R.id.chipLlaveros)
        val chipRamos: Chip = view.findViewById(R.id.chipRamos)
        val chipPeluches: Chip = view.findViewById(R.id.chipPeluches)
        val chipAccesorios: Chip = view.findViewById(R.id.chipAccesorios)
        val chipExtras: Chip = view.findViewById(R.id.chipExtras)
        val btnEditarCarpeta: ImageButton = view.findViewById(R.id.btnEditarCarpeta)
    }

    fun actualizarLista(nuevaLista: List<ProyectoCrochet>) {
        listaProyectos = nuevaLista
        notifyDataSetChanged()
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

        val formatoFecha = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        holder.tvFecha.text = formatoFecha.format(Date(proyecto.fechaGuardado))

        if (!proyecto.fotoUri.isNullOrEmpty()) {
            holder.ivImagen.load(proyecto.fotoUri) { crossfade(true) }
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

        if (proyecto.carpetas.isEmpty()) {
            holder.btnAgregarCarpetaVacio.visibility = View.VISIBLE
            holder.llContenedorTags.visibility = View.GONE
        } else {
            holder.btnAgregarCarpetaVacio.visibility = View.GONE
            holder.llContenedorTags.visibility = View.VISIBLE

            holder.chipAmigurumi.visibility = if (proyecto.carpetas.contains("Amigurumi")) View.VISIBLE else View.GONE
            holder.chipLlaveros.visibility = if (proyecto.carpetas.contains("Llaveros")) View.VISIBLE else View.GONE
            holder.chipRamos.visibility = if (proyecto.carpetas.contains("Ramos")) View.VISIBLE else View.GONE
            holder.chipPeluches.visibility = if (proyecto.carpetas.contains("Peluches")) View.VISIBLE else View.GONE
            holder.chipAccesorios.visibility = if (proyecto.carpetas.contains("Accesorios")) View.VISIBLE else View.GONE
            holder.chipExtras.visibility = if (proyecto.carpetas.contains("Extras")) View.VISIBLE else View.GONE
        }

        holder.btnAgregarCarpetaVacio.setOnClickListener {
            onEditCarpetaClick?.invoke(proyecto)
        }

        holder.btnEditarCarpeta.setOnClickListener {
            onEditCarpetaClick?.invoke(proyecto)
        }
    }

    override fun getItemCount() = listaProyectos.size
}