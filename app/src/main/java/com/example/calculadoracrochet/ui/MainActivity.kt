package com.example.calculadoracrochet.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.airbnb.lottie.LottieAnimationView
import com.example.calculadoracrochet.R
import com.example.calculadoracrochet.ui.configuracion.ConfiguracionFragment
import com.example.calculadoracrochet.ui.dashboard.DashboardFragment
import com.example.calculadoracrochet.ui.estadisticas.EstadisticasFragment
import com.example.calculadoracrochet.ui.papelera.PapeleraFragment
import com.example.calculadoracrochet.utils.hapticSeleccion
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

    private var estaListo = false
    private lateinit var lottieGanchillo: LottieAnimationView
    private lateinit var barraNavegacion: BottomNavigationView
    private var estaNavegando = false

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
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = View.VISIBLE

        configurarSplash(splashScreen)
        configurarBarraNavegacion()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }
    }

    private fun configurarSplash(splashScreen: androidx.core.splashscreen.SplashScreen) {
        splashScreen.setKeepOnScreenCondition { !estaListo }
        Handler(Looper.getMainLooper()).postDelayed({ estaListo = true }, 750)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            fadeOut.duration = 300L
            fadeOut.doOnEnd { splashScreenView.remove() }
            fadeOut.start()
        }
    }

    // region Main tabs

    private fun configurarBarraNavegacion() {
        barraNavegacion = findViewById(R.id.barraNavegacion)

        barraNavegacion.setOnItemSelectedListener { item ->
            if (!sincronizandoBarra) {
                barraNavegacion.hapticSeleccion()
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

    // endregion

    // region Crochet hook

    /** Plays the crochet hook animation with touches blocked and runs [alLlegarAl25] once at 25 %. */
    private fun animarGanchillo(alLlegarAl25: () -> Unit) {
        if (estaNavegando) return
        estaNavegando = true
        bloquearToques(true)

        lottieGanchillo.removeAllUpdateListeners()
        lottieGanchillo.removeAllAnimatorListeners()

        lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = View.VISIBLE

        var accionEjecutada = false
        lottieGanchillo.addAnimatorUpdateListener { animation ->
            if (animation.animatedFraction >= 0.25f && !accionEjecutada) {
                accionEjecutada = true
                alLlegarAl25()
            }
        }

        lottieGanchillo.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) = terminarAnimacion()
            override fun onAnimationCancel(animation: Animator) = terminarAnimacion()
            override fun onAnimationRepeat(animation: Animator) {}
        })

        lottieGanchillo.playAnimation()
    }

    private fun terminarAnimacion() {
        lottieGanchillo.removeAllUpdateListeners()
        lottieGanchillo.removeAllAnimatorListeners()
        estaNavegando = false
        bloquearToques(false)

        lottieGanchillo.post {
            lottieGanchillo.pauseAnimation()
            val actual = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            ponerGanchilloEnReposo(actual is PantallaConGanchillo)
        }
    }

    /** The hook waits at the first frame on the screens that leave with it; elsewhere it is hidden. */
    private fun ponerGanchilloEnReposo(visible: Boolean) {
        if (lottieGanchillo.isAnimating) return
        lottieGanchillo.progress = 0f
        lottieGanchillo.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun bloquearToques(bloquear: Boolean) {
        if (bloquear) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    // endregion

    private companion object {
        const val DURACION_BARRA_MS = 220L
    }
}
