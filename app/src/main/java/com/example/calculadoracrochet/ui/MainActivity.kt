package com.example.calculadoracrochet.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.airbnb.lottie.LottieAnimationView
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.data.model.ProyectoCrochet
import com.example.calculadoracrochet.ui.configuracion.ConfiguracionFragment
import com.example.calculadoracrochet.ui.configuracion.MisMaterialesFragment
import com.example.calculadoracrochet.ui.dashboard.DashboardFragment
import com.example.calculadoracrochet.ui.estadisticas.EstadisticasFragment
import com.example.calculadoracrochet.ui.papelera.PapeleraFragment
import com.example.calculadoracrochet.utils.hapticSeleccion
import com.example.calculadoracrochet.utils.rebotar
import com.example.calculadoracrochet.viewmodel.ProyectosViewModel
import com.example.calculadoracrochet.ui.calculadora.CalculadoraViewModel
import com.example.calculadoracrochet.utils.DialogUtils
import com.google.android.material.bottomnavigation.BottomNavigationView

/** A main tab (Proyectos, Estadísticas, Ajustes): the bottom bar shows while it is on screen. */
interface PestanaPrincipal {
    val idPestana: Int

    /** The tab was tapped again while showing (e.g. scroll the list back to the top). */
    fun alVolverAPulsar() = Unit
}

/** A screen where the crochet hook rests at the top, ready for the next transition. */
interface PantallaConGanchillo

class MainActivity : AppCompatActivity() {

    private val proyectosViewModel: ProyectosViewModel by viewModels()
    private val calculadoraViewModel: CalculadoraViewModel by viewModels()

    private var estaListo = false
    private lateinit var lottieGanchillo: LottieAnimationView
    private lateinit var barraNavegacion: BottomNavigationView
    private var estaNavegando = false
    private var navegacionPendiente: (() -> Unit)? = null
    private val manejador = Handler(Looper.getMainLooper())

    /** Set while the bar is checked by code, so it does not navigate again. */
    private var sincronizandoBarra = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // White icons on the purple headers, dark ones on the white bottom bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lottieGanchillo = findViewById(R.id.lottieGanchillo)
        lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
        lottieGanchillo.speed = VELOCIDAD_GANCHILLO
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = View.VISIBLE

        configurarSplash(splashScreen, recreada = savedInstanceState != null)
        configurarBarraNavegacion()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }
    }

    /**
     * The splash stays until the first list of projects arrives, so the dashboard is not revealed
     * empty, but never less than [SPLASH_MINIMO_MS] (no flash) nor more than [SPLASH_MAXIMO_MS] (offline).
     * A recreated activity (rotation) shows no splash, and waiting would only freeze its first frame.
     */
    private fun configurarSplash(splashScreen: SplashScreen, recreada: Boolean) {
        splashScreen.setOnExitAnimationListener { proveedor -> animarSalidaSplash(proveedor) }
        splashScreen.setKeepOnScreenCondition { !estaListo }
        if (recreada) {
            estaListo = true
            return
        }

        val inicio = SystemClock.uptimeMillis()
        val liberar = Runnable { estaListo = true }
        manejador.postDelayed(liberar, SPLASH_MAXIMO_MS)

        proyectosViewModel.proyectosEnVivo.observe(this, object : Observer<List<ProyectoCrochet>> {
            override fun onChanged(value: List<ProyectoCrochet>) {
                proyectosViewModel.proyectosEnVivo.removeObserver(this)
                if (estaListo) return
                manejador.removeCallbacks(liberar)
                val transcurrido = SystemClock.uptimeMillis() - inicio
                manejador.postDelayed(liberar, (SPLASH_MINIMO_MS - transcurrido).coerceAtLeast(0L))
            }
        })
    }

    /** The splash fades while its icon grows a little, as if the app came forward. */
    private fun animarSalidaSplash(proveedor: SplashScreenViewProvider) {
        val animaciones = mutableListOf<Animator>(ObjectAnimator.ofFloat(proveedor.view, View.ALPHA, 1f, 0f))
        // Some launches (adb, some launchers) show the splash without an icon
        runCatching { proveedor.iconView }.getOrNull()?.let { icono ->
            animaciones += ObjectAnimator.ofFloat(icono, View.SCALE_X, 1f, 1.1f)
            animaciones += ObjectAnimator.ofFloat(icono, View.SCALE_Y, 1f, 1.1f)
        }
        AnimatorSet().apply {
            playTogether(animaciones)
            duration = 280L
            interpolator = DecelerateInterpolator(1.5f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = proveedor.remove()
            })
            start()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        navegacionPendiente?.let { navegar ->
            navegacionPendiente = null
            navegar()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // While the hook pulls the screen nothing underneath may react to touches
        if (estaNavegando) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        manejador.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // region Main tabs

    private fun configurarBarraNavegacion() {
        barraNavegacion = findViewById(R.id.barraNavegacion)

        barraNavegacion.setOnItemSelectedListener { item ->
            if (!sincronizandoBarra) {
                barraNavegacion.hapticSeleccion()
                // The icon of the tab just picked gives a small hop
                barraNavegacion.findViewById<View>(item.itemId)
                    ?.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
                    ?.rebotar(1.15f)
                mostrarPestana(item.itemId)
            }
            true
        }
        barraNavegacion.setOnItemReselectedListener {
            (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? PestanaPrincipal)?.alVolverAPulsar()
        }

        // Every screen that enters the container decides the bar and the resting hook.
        // This also covers going back, rotations and a screen restored after the app was killed.
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f.id != R.id.fragmentContainer) return
                if (f is PestanaPrincipal) {
                    marcarPestana(f.idPestana)
                    mostrarBarra(true)
                } else {
                    mostrarBarra(false)
                }
                if (!estaNavegando) ponerGanchilloEnReposo(f is PantallaConGanchillo)
            }
        }, false)
    }

    /** Opens a main tab. Tabs replace each other without piling up: back from any of them returns to Proyectos. */
    fun mostrarPestana(idPestana: Int) {
        val actual = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if ((actual as? PestanaPrincipal)?.idPestana == idPestana) return
        if (estaNavegando) return

        val destino: Fragment = when (idPestana) {
            R.id.nav_estadisticas -> EstadisticasFragment()
            R.id.nav_ajustes -> ConfiguracionFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.pestana_entrada, R.anim.pestana_salida)
            .replace(R.id.fragmentContainer, destino)
            .commit()
    }

    fun volverAProyectos() = mostrarPestana(R.id.nav_proyectos)

    private fun marcarPestana(idPestana: Int) {
        if (barraNavegacion.selectedItemId == idPestana) return
        sincronizandoBarra = true
        barraNavegacion.selectedItemId = idPestana
        sincronizandoBarra = false
    }

    private fun mostrarBarra(mostrar: Boolean) {
        barraNavegacion.animate().cancel()
        if (mostrar) {
            if (barraNavegacion.visibility == View.VISIBLE && barraNavegacion.translationY == 0f) return
            barraNavegacion.visibility = View.VISIBLE
            if (barraNavegacion.height == 0) {
                barraNavegacion.translationY = 0f
                return
            }
            barraNavegacion.animate()
                .translationY(0f)
                .setDuration(DURACION_BARRA_MS)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        } else {
            if (barraNavegacion.visibility != View.VISIBLE) return
            barraNavegacion.animate()
                .translationY(barraNavegacion.height.toFloat())
                .setDuration(DURACION_BARRA_MS)
                .withEndAction { barraNavegacion.visibility = View.GONE }
                .start()
        }
    }

    // endregion

    // region Navigation

    fun navegarConGanchillo(fragmentDestino: Fragment) {
        animarGanchillo {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                    R.anim.slide_in_top,
                    R.anim.slide_out_bottom,
                    R.anim.slide_in_top,
                    R.anim.slide_out_bottom
                )
                .replace(R.id.fragmentContainer, fragmentDestino)
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Leaves the calculator from any step, after confirming: the calculation and its draft are
     * dropped and the dashboard comes back. A project being edited stays as it was saved.
     */
    fun cancelarProyecto() {
        if (estaNavegando) return
        val editando = calculadoraViewModel.calculo.idProyectoEnEdicion != null
        DialogUtils.mostrarAlerta(
            context = this,
            titulo = if (editando) "¿Descartar los cambios?" else "¿Cancelar el proyecto?",
            mensaje = if (editando) "El proyecto se queda como estaba guardado."
            else "Se perderá lo que llevas capturado.",
            textoConfirmar = if (editando) "Descartar" else "Sí, cancelar",
            iconoResId = R.drawable.ic_sad
        ) {
            calculadoraViewModel.limpiarCalculoActual()
            irAlDashboardConGanchillo()
        }
    }

    fun regresarConGanchillo() {
        animarGanchillo { supportFragmentManager.popBackStack() }
    }

    /** After saving: back to the dashboard at the bottom of the stack, dropping the calculator steps. */
    fun irAlDashboardConGanchillo() {
        animarGanchillo {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    /** Next calculator step (or the trash): slides in from the right. */
    fun navegarDeslizando(fragmentDestino: Fragment) {
        if (estaNavegando) return
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragmentDestino)
            .addToBackStack(null)
            .commit()
    }

    fun regresarDeslizando() {
        if (estaNavegando) return
        supportFragmentManager.popBackStack()
    }

    fun abrirPapelera() = navegarDeslizando(PapeleraFragment())

    fun abrirMisMateriales() = navegarDeslizando(MisMaterialesFragment())

    // endregion

    // region Crochet hook

    /**
     * The new screen is built before the hook starts moving: building it takes a few frames, and
     * doing it mid-animation made the hook stutter. Both animations then start on the same frame
     * (anim/slide_in_top and slide_out_bottom wait for the hook to grab the yarn).
     */
    private fun animarGanchillo(cambiarPantalla: () -> Unit) {
        if (estaNavegando) return
        if (supportFragmentManager.isStateSaved) {
            // The app went to the background (e.g. during the "saved" animation): navigate when it comes back
            navegacionPendiente = { animarGanchillo(cambiarPantalla) }
            return
        }
        estaNavegando = true
        cancelarToquesEnCurso()

        lottieGanchillo.animate().cancel()
        lottieGanchillo.removeAllUpdateListeners()
        lottieGanchillo.removeAllAnimatorListeners()
        lottieGanchillo.cancelAnimation()
        lottieGanchillo.alpha = 1f
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = View.VISIBLE

        cambiarPantalla()
        supportFragmentManager.executePendingTransactions()

        lottieGanchillo.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = terminarAnimacion()
            override fun onAnimationCancel(animation: Animator) = terminarAnimacion()
        })
        lottieGanchillo.playAnimation()
    }

    private fun terminarAnimacion() {
        lottieGanchillo.removeAllAnimatorListeners()
        estaNavegando = false

        lottieGanchillo.post {
            if (estaNavegando) return@post
            val actual = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (actual is PantallaConGanchillo) {
                // The yarn comes back at the top of the new screen fading in instead of popping up
                lottieGanchillo.progress = 0f
                lottieGanchillo.alpha = 0f
                lottieGanchillo.visibility = View.VISIBLE
                lottieGanchillo.animate().alpha(1f).setDuration(220L).start()
            } else {
                ponerGanchilloEnReposo(false)
            }
        }
    }

    private fun cancelarToquesEnCurso() {
        val ahora = SystemClock.uptimeMillis()
        val cancelar = MotionEvent.obtain(ahora, ahora, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancelar)
        cancelar.recycle()
    }

    /** The hook waits at the first frame on the screens that leave with it; elsewhere it is hidden. */
    private fun ponerGanchilloEnReposo(visible: Boolean) {
        if (lottieGanchillo.isAnimating) return
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // endregion

    private companion object {
        const val DURACION_BARRA_MS = 220L

        /** The hook animation is 1 s long at normal speed; anim/slide_in_top and slide_out_bottom are timed to this speed. */
        const val VELOCIDAD_GANCHILLO = 1.4f

        const val SPLASH_MINIMO_MS = 350L
        const val SPLASH_MAXIMO_MS = 1_200L
    }
}
