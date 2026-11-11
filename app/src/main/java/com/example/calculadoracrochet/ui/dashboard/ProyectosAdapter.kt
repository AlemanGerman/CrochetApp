package com.example.calculadoracrochet.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.CarpetasUi
import com.example.calculadoracrochet.utils.ColorExtractorHelper
import com.example.calculadoracrochet.utils.PreferenciasApp
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import com.example.calculadoracrochet.data.model.tieneFoto
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.LOCALE_MX

class ProyectosAdapter(
    private var listaProyectos: List<ProyectoCrochet>,
    private val onItemClick: (ProyectoCrochet) -> Unit,
    private val onEditCarpetaClick: ((ProyectoCrochet) -> Unit)? = null
) : RecyclerView.Adapter<ProyectosAdapter.ProyectoViewHolder>() {

    class ProyectoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val tvNombre: TextView = view.findViewById(R.id.tvNombreProyecto)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecioProyecto)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivImagen: ShapeableImageView = view.findViewById(R.id.ivImagenProyecto)
        val cgCarpetasProyecto: ChipGroup = view.findViewById(R.id.cgCarpetasProyecto)
        val tvSinCarpeta: TextView = view.findViewById(R.id.tvSinCarpeta)
        val btnEditarCarpeta: MaterialButton = view.findViewById(R.id.btnEditarCarpeta)

        var idProyectoActual: String? = null
    }

    private fun restablecerColorDefecto(holder: ProyectoViewHolder) {
        val context = holder.itemView.context
        val morado = ContextCompat.getColor(context, R.color.morado_fuerte)
        val suave = ContextCompat.getColor(context, R.color.tinta_suave)
        holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
        holder.cardView.strokeColor = ContextCompat.getColor(context, R.color.lavanda_borde)
        holder.tvNombre.setTextColor(ContextCompat.getColor(context, R.color.tinta))
        holder.tvPrecio.setTextColor(morado)
        holder.tvFecha.setTextColor(suave)
        holder.tvSinCarpeta.setTextColor(suave)
        holder.btnEditarCarpeta.iconTint = ColorStateList.valueOf(morado)
    }

    private fun aplicarColorDinamico(holder: ProyectoViewHolder, config: ColorExtractorHelper.ColorTarjetaConfig) {
        holder.cardView.setCardBackgroundColor(config.backgroundColor)
        holder.cardView.strokeColor = Color.TRANSPARENT
        holder.tvNombre.setTextColor(config.textColorPrimario)
        holder.tvPrecio.setTextColor(config.textColorPrimario)
        holder.tvFecha.setTextColor(config.textColorSecundario)
        holder.tvSinCarpeta.setTextColor(config.textColorSecundario)
        holder.btnEditarCarpeta.iconTint = ColorStateList.valueOf(config.iconTint)
    }

    private val formatoFecha = SimpleDateFormat("dd MMM yyyy", LOCALE_MX)

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
        holder.idProyectoActual = proyecto.id

        holder.tvNombre.text = proyecto.nombre
        holder.tvPrecio.text = proyecto.precioFinal.comoDinero()

        holder.tvFecha.text = formatoFecha.format(Date(proyecto.fechaGuardado))

        val context = holder.itemView.context
        val colorDinamicoActivo = PreferenciasApp.isColorDinamicoTarjetasActivo(context)

        val tieneFotoValida = proyecto.tieneFoto

        if (!colorDinamicoActivo || !tieneFotoValida) {
            restablecerColorDefecto(holder)
        } else {
            val fotoParaDashboard = proyecto.fotoMiniaturaUri ?: proyecto.fotoUri
            val claveCache = fotoParaDashboard ?: proyecto.id
            val colorEnCache = ColorExtractorHelper.obtenerColorEnCache(claveCache)
            if (colorEnCache != null) {
                aplicarColorDinamico(holder, colorEnCache)
            } else {
                restablecerColorDefecto(holder)
            }
        }

        if (tieneFotoValida) {
            holder.ivImagen.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.ivImagen.setPadding(0, 0, 0, 0)
            holder.ivImagen.imageTintList = null
            holder.ivImagen.setBackgroundColor(Color.TRANSPARENT)
            val fotoParaDashboard = proyecto.fotoMiniaturaUri ?: proyecto.fotoUri
            val claveCache = fotoParaDashboard ?: proyecto.id
            holder.ivImagen.load(fotoParaDashboard) {
                crossfade(false)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
                allowHardware(false)
                if (colorDinamicoActivo) {
                    listener(
                        onSuccess = { _, result ->
                            val drawable = result.drawable
                            val bitmap = when (drawable) {
                                is BitmapDrawable -> drawable.bitmap
                                else -> {
                                    try {
                                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bmp)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                        bmp
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                            if (bitmap != null) {
                                ColorExtractorHelper.extraerColor(bitmap, claveCache) { config ->
                                    if (holder.idProyectoActual == proyecto.id) {
                                        aplicarColorDinamico(holder, config)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        } else {
            holder.ivImagen.scaleType = ImageView.ScaleType.FIT_CENTER
            val paddingPx = (12 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.ivImagen.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            holder.ivImagen.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.lavanda))
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
            it.hapticClickPrincipal()
            onItemClick(proyecto)
        }

        holder.cgCarpetasProyecto.removeAllViews()
        CarpetasConfig.listaCarpetas.forEach { info ->
            if (info.nombre != CarpetasConfig.CARPETA_TODOS && proyecto.carpetas.contains(info.nombre)) {
                holder.cgCarpetasProyecto.addView(CarpetasUi.crearEtiqueta(holder.cgCarpetasProyecto, info))
            }
        }
        val sinCarpetas = holder.cgCarpetasProyecto.childCount == 0
        holder.tvSinCarpeta.visibility = if (sinCarpetas) View.VISIBLE else View.GONE
        (holder.cgCarpetasProyecto.parent as View).visibility = if (sinCarpetas) View.GONE else View.VISIBLE

        holder.btnEditarCarpeta.setOnClickListener {
            it.hapticSeleccion()
            onEditCarpetaClick?.invoke(proyecto)
        }
    }

    override fun getItemCount() = listaProyectos.size
}
