package com.example.calculadoracrochet.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.viewmodel.ProyectoCrochet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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

        val cgCarpetasProyecto: ChipGroup = view.findViewById(R.id.cgCarpetasProyecto)
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

        val tieneFotoValida = !proyecto.fotoUri.isNullOrBlank() &&
                proyecto.fotoUri != "null" &&
                proyecto.fotoUri != "undefined"

        if (tieneFotoValida) {
            holder.ivImagen.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.ivImagen.setPadding(0, 0, 0, 0)
            holder.ivImagen.imageTintList = null
            holder.ivImagen.setBackgroundColor(Color.TRANSPARENT)
            holder.ivImagen.load(proyecto.fotoUri) {
                crossfade(true)
            }
        } else {
            holder.ivImagen.scaleType = ImageView.ScaleType.FIT_CENTER
            val paddingPx = (6 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.ivImagen.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            holder.ivImagen.setBackgroundColor(Color.parseColor("#F3E8FF"))
            holder.ivImagen.imageTintList = null

            val iconoRes = if (proyecto.carpetas.size == 1) {
                val nombre = proyecto.carpetas.first().trim()
                when (nombre.lowercase()) {
                    "accesorio", "accesorios" -> R.drawable.img_accesorio
                    "amigurumi", "amigurumis" -> R.drawable.img_amigurumi
                    "llavero", "llaveros" -> R.drawable.img_llavero
                    "peluche", "peluches" -> R.drawable.img_peluche
                    "ramo", "ramos" -> R.drawable.img_ramo
                    "extra", "extras" -> R.drawable.ic_mas
                    else -> CarpetasConfig.listaCarpetas.find { it.nombre.equals(nombre, ignoreCase = true) }?.iconoRes
                        ?: R.drawable.img_todos
                }
            } else {
                R.drawable.img_todos
            }

            holder.ivImagen.load(iconoRes)
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

            holder.cgCarpetasProyecto.removeAllViews()

            CarpetasConfig.listaCarpetas.forEach { info ->
                if (info.nombre != "Todos" && proyecto.carpetas.contains(info.nombre)) {
                    val chip = Chip(holder.itemView.context).apply {
                        text = info.nombre
                        isCheckable = false
                        isClickable = false
                        setChipBackgroundColorResource(info.colorRes)
                        setTextColor(ContextCompat.getColor(context, info.textColorRes))
                        setChipStrokeColorResource(info.colorRes)
                        chipStrokeWidth = 1f * resources.displayMetrics.density
                    }
                    holder.cgCarpetasProyecto.addView(chip)
                }
            }
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