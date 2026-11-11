package com.example.calculadoracrochet.ui.dashboard

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.data.model.fotoParaLista
import com.example.calculadoracrochet.utils.CarpetasConfig
import com.example.calculadoracrochet.utils.CarpetasUi
import com.example.calculadoracrochet.utils.ColorExtractorHelper
import com.example.calculadoracrochet.utils.LOCALE_MX
import com.example.calculadoracrochet.utils.comoDinero
import com.example.calculadoracrochet.utils.hapticClickPrincipal
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.hundirAlPresionar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date

/**
 * What a card shows, copied when the list is submitted. Projects are edited in place (name, date,
 * folders, photo), so comparing them directly would never see a change.
 */
class TarjetaProyecto(val proyecto: ProyectoCrochet) {
    val id: String = proyecto.id
    val nombre: String = proyecto.nombre
    val precioFinal: Double = proyecto.precioFinal
    val fechaGuardado: Long = proyecto.fechaGuardado
    val carpetas: List<String> = proyecto.carpetas.toList()

    val foto: String? = proyecto.fotoParaLista

    fun mismoContenido(otra: TarjetaProyecto): Boolean =
        nombre == otra.nombre &&
            precioFinal == otra.precioFinal &&
            fechaGuardado == otra.fechaGuardado &&
            foto == otra.foto &&
            carpetas == otra.carpetas
}

class ProyectosAdapter(
    private val onItemClick: (ProyectoCrochet) -> Unit,
    private val onEditCarpetaClick: (ProyectoCrochet) -> Unit
) : ListAdapter<TarjetaProyecto, ProyectosAdapter.ProyectoViewHolder>(DIFERENCIAS) {

    private val formatoFecha = SimpleDateFormat("dd MMM yyyy", LOCALE_MX)

    var colorDinamicoActivo: Boolean = true
        private set

    fun enviarProyectos(proyectos: List<ProyectoCrochet>, alAplicar: (() -> Unit)? = null) {
        submitList(proyectos.map(::TarjetaProyecto), alAplicar)
    }

    /** Repaints the cards only when the setting actually changed. */
    fun cambiarColorDinamico(activo: Boolean) {
        if (colorDinamicoActivo == activo) return
        colorDinamicoActivo = activo
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProyectoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_proyecto, parent, false)
        return ProyectoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProyectoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ProyectoViewHolder) {
        holder.detenerAnimaciones()
    }

    private data class ColoresTarjeta(
        val fondo: Int,
        val borde: Int,
        val nombre: Int,
        val precio: Int,
        val secundario: Int,
        val iconos: Int
    ) {
        fun hacia(destino: ColoresTarjeta, fraccion: Float) = ColoresTarjeta(
            fondo = ColorUtils.blendARGB(fondo, destino.fondo, fraccion),
            borde = ColorUtils.blendARGB(borde, destino.borde, fraccion),
            nombre = ColorUtils.blendARGB(nombre, destino.nombre, fraccion),
            precio = ColorUtils.blendARGB(precio, destino.precio, fraccion),
            secundario = ColorUtils.blendARGB(secundario, destino.secundario, fraccion),
            iconos = ColorUtils.blendARGB(iconos, destino.iconos, fraccion)
        )

        companion object {
            fun de(config: ColorExtractorHelper.ColorTarjetaConfig) = ColoresTarjeta(
                fondo = config.backgroundColor,
                // Same hue as the card, so the outline fades away instead of turning gray
                borde = ColorUtils.setAlphaComponent(config.backgroundColor, 0),
                nombre = config.textColorPrimario,
                precio = config.textColorPrimario,
                secundario = config.textColorSecundario,
                iconos = config.iconTint
            )
        }
    }

    inner class ProyectoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView: MaterialCardView = view as MaterialCardView
        private val tvNombre: TextView = view.findViewById(R.id.tvNombreProyecto)
        private val tvPrecio: TextView = view.findViewById(R.id.tvPrecioProyecto)
        private val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        private val ivImagen: ShapeableImageView = view.findViewById(R.id.ivImagenProyecto)
        private val cgCarpetasProyecto: ChipGroup = view.findViewById(R.id.cgCarpetasProyecto)
        private val tvSinCarpeta: TextView = view.findViewById(R.id.tvSinCarpeta)
        private val btnEditarCarpeta: MaterialButton = view.findViewById(R.id.btnEditarCarpeta)

        private val contexto = view.context
        private val coloresPorDefecto = ColoresTarjeta(
            fondo = ContextCompat.getColor(contexto, R.color.white),
            borde = ContextCompat.getColor(contexto, R.color.lavanda_borde),
            nombre = ContextCompat.getColor(contexto, R.color.tinta),
            precio = ContextCompat.getColor(contexto, R.color.morado_fuerte),
            secundario = ContextCompat.getColor(contexto, R.color.tinta_suave),
            iconos = ContextCompat.getColor(contexto, R.color.morado_fuerte)
        )
        private val paddingIcono = (12 * contexto.resources.displayMetrics.density).toInt()

        private var tarjetaActual: TarjetaProyecto? = null
        private var coloresActuales: ColoresTarjeta? = null
        private var animadorColores: ValueAnimator? = null
        private var carpetasMostradas: List<String>? = null

        init {
            view.hundirAlPresionar()
            view.setOnClickListener {
                val tarjeta = tarjetaActual ?: return@setOnClickListener
                it.hapticClickPrincipal()
                onItemClick(tarjeta.proyecto)
            }
            btnEditarCarpeta.setOnClickListener {
                val tarjeta = tarjetaActual ?: return@setOnClickListener
                it.hapticSeleccion()
                onEditCarpetaClick(tarjeta.proyecto)
            }
        }

        fun bind(tarjeta: TarjetaProyecto) {
            val mismaTarjeta = tarjetaActual?.id == tarjeta.id
            tarjetaActual = tarjeta

            tvNombre.text = tarjeta.nombre
            tvPrecio.text = tarjeta.precioFinal.comoDinero()
            tvFecha.text = formatoFecha.format(Date(tarjeta.fechaGuardado))

            val foto = tarjeta.foto
            val usarColorDeFoto = colorDinamicoActivo && foto != null
            val analisis = if (usarColorDeFoto) ColorExtractorHelper.obtenerEnCache(foto!!) else null
            val colores = analisis?.config?.let { ColoresTarjeta.de(it) } ?: coloresPorDefecto
            // The same project repainted (setting toggled, photo changed) glides to its new color
            pintar(colores, animar = mismaTarjeta)

            if (foto != null) {
                mostrarFoto(tarjeta, foto, extraerColor = usarColorDeFoto && analisis == null)
            } else {
                mostrarIconoDeCarpeta(tarjeta.carpetas)
            }

            mostrarCarpetas(tarjeta.carpetas)
        }

        fun detenerAnimaciones() {
            animadorColores?.cancel()
            animadorColores = null
            itemView.animate().cancel()
            itemView.alpha = 1f
            itemView.translationY = 0f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
        }

        private fun mostrarFoto(tarjeta: TarjetaProyecto, foto: String, extraerColor: Boolean) {
            ivImagen.scaleType = ImageView.ScaleType.CENTER_CROP
            ivImagen.setPadding(0, 0, 0, 0)
            ivImagen.imageTintList = null
            ivImagen.setBackgroundColor(Color.TRANSPARENT)

            ivImagen.load(foto) {
                // Only photos that were not in memory fade in; cached ones appear at once
                crossfade(DURACION_FUNDIDO_FOTO_MS)
                // The color is read from the pixels, which a hardware bitmap does not allow; otherwise it draws faster
                allowHardware(!extraerColor)
                if (extraerColor) {
                    listener(onSuccess = { _, resultado ->
                        val bitmap = comoBitmap(resultado.drawable) ?: return@listener
                        ColorExtractorHelper.extraerColor(bitmap, foto) { analisis ->
                            // The card may show another project by now
                            val actual = tarjetaActual
                            if (actual?.id == tarjeta.id && actual.foto == foto && colorDinamicoActivo) {
                                pintar(analisis.config?.let { ColoresTarjeta.de(it) } ?: coloresPorDefecto, animar = true)
                            }
                        }
                    })
                }
            }
        }

        private fun mostrarIconoDeCarpeta(carpetas: List<String>) {
            ivImagen.scaleType = ImageView.ScaleType.FIT_CENTER
            ivImagen.setPadding(paddingIcono, paddingIcono, paddingIcono, paddingIcono)
            ivImagen.setBackgroundColor(ContextCompat.getColor(contexto, R.color.lavanda))
            ivImagen.imageTintList = null

            val iconoRes = if (carpetas.size == 1) {
                val nombre = carpetas.first().trim()
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

            ivImagen.load(iconoRes)
        }

        /** Rebuilds the folder tags only when they changed, not on every bind while scrolling. */
        private fun mostrarCarpetas(carpetas: List<String>) {
            if (carpetas != carpetasMostradas) {
                carpetasMostradas = carpetas
                cgCarpetasProyecto.removeAllViews()
                CarpetasConfig.listaCarpetas.forEach { info ->
                    if (info.nombre != CarpetasConfig.CARPETA_TODOS && info.nombre in carpetas) {
                        cgCarpetasProyecto.addView(CarpetasUi.crearEtiqueta(cgCarpetasProyecto, info))
                    }
                }
            }
            val sinCarpetas = cgCarpetasProyecto.childCount == 0
            tvSinCarpeta.visibility = if (sinCarpetas) View.VISIBLE else View.GONE
            val scroll = cgCarpetasProyecto.parent as HorizontalScrollView
            scroll.visibility = if (sinCarpetas) View.GONE else View.VISIBLE
            // A recycled card keeps the horizontal scroll of the project it showed before
            scroll.scrollTo(0, 0)
        }

        private fun pintar(colores: ColoresTarjeta, animar: Boolean) {
            animadorColores?.cancel()
            animadorColores = null

            val desde = coloresActuales
            if (!animar || desde == null || desde == colores) {
                aplicarColores(colores)
                return
            }

            animadorColores = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURACION_CAMBIO_COLOR_MS
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { aplicarColores(desde.hacia(colores, it.animatedFraction)) }
                start()
            }
        }

        private fun aplicarColores(colores: ColoresTarjeta) {
            coloresActuales = colores
            cardView.setCardBackgroundColor(colores.fondo)
            cardView.strokeColor = colores.borde
            tvNombre.setTextColor(colores.nombre)
            tvPrecio.setTextColor(colores.precio)
            tvFecha.setTextColor(colores.secundario)
            tvSinCarpeta.setTextColor(colores.secundario)
            btnEditarCarpeta.iconTint = ColorStateList.valueOf(colores.iconos)
        }
    }

    private companion object {
        const val DURACION_FUNDIDO_FOTO_MS = 180
        const val DURACION_CAMBIO_COLOR_MS = 320L

        val DIFERENCIAS = object : DiffUtil.ItemCallback<TarjetaProyecto>() {
            override fun areItemsTheSame(anterior: TarjetaProyecto, nueva: TarjetaProyecto) = anterior.id == nueva.id
            override fun areContentsTheSame(anterior: TarjetaProyecto, nueva: TarjetaProyecto) = anterior.mismoContenido(nueva)
        }

        fun comoBitmap(drawable: Drawable): Bitmap? {
            if (drawable is BitmapDrawable) return drawable.bitmap
            return try {
                val ancho = drawable.intrinsicWidth.coerceAtLeast(1)
                val alto = drawable.intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
