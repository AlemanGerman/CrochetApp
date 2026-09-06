package com.example.calculadoracrochet.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.calculadoracrochet.R

class MainActivity : AppCompatActivity() {

    private var estaListo = false
    private lateinit var lottieGanchillo: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lottieGanchillo = findViewById(R.id.lottieGanchillo)

        splashScreen.setKeepOnScreenCondition { !estaListo }

        Handler(Looper.getMainLooper()).postDelayed({
            estaListo = true
        }, 750)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.ALPHA,
                1f,
                0f
            )
            fadeOut.duration = 300L
            fadeOut.doOnEnd {
                splashScreenView.remove()
            }
            fadeOut.start()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }
    }

    fun navegarConGanchillo(fragmentDestino: Fragment) {
        lottieGanchillo.removeAllUpdateListeners()
        lottieGanchillo.removeAllAnimatorListeners()

        lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
        lottieGanchillo.visibility = View.VISIBLE

        lottieGanchillo.addAnimatorUpdateListener { animation ->
            if (animation.animatedFraction >= 0.25f && lottieGanchillo.tag != "navegando") {
                lottieGanchillo.tag = "navegando"

                supportFragmentManager.beginTransaction()
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

        lottieGanchillo.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                lottieGanchillo.tag = ""
            }
            override fun onAnimationEnd(animation: Animator) {
                lottieGanchillo.post {
                    lottieGanchillo.pauseAnimation()
                    lottieGanchillo.progress = 0f
                    lottieGanchillo.visibility = View.VISIBLE
                }

                lottieGanchillo.removeAllUpdateListeners()
                lottieGanchillo.removeAllAnimatorListeners()
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        lottieGanchillo.playAnimation()
    }
    fun regresarConGanchillo() {
        val volviendoAlDashboard = supportFragmentManager.backStackEntryCount <= 1

        lottieGanchillo.removeAllUpdateListeners()
        lottieGanchillo.removeAllAnimatorListeners()

        lottieGanchillo.setAnimation(R.raw.animacion_ganchillo)
        lottieGanchillo.visibility = View.VISIBLE

        lottieGanchillo.addAnimatorUpdateListener { animation ->
            if (animation.animatedFraction >= 0.25f && lottieGanchillo.tag != "regresando") {
                lottieGanchillo.tag = "regresando"
                supportFragmentManager.popBackStack()
            }
        }

        lottieGanchillo.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                lottieGanchillo.tag = ""
            }
            override fun onAnimationEnd(animation: Animator) {
                lottieGanchillo.post {
                    if (volviendoAlDashboard) {
                        lottieGanchillo.visibility = View.GONE
                    } else {
                        lottieGanchillo.progress = 0f
                        lottieGanchillo.visibility = View.VISIBLE
                    }
                }
                lottieGanchillo.tag = ""
                lottieGanchillo.removeAllUpdateListeners()
                lottieGanchillo.removeAllAnimatorListeners()
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        lottieGanchillo.playAnimation()
    }
}