package com.example.calculadoracrochet.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.calculadoracrochet.R

class MesFragment : Fragment(R.layout.fragment_mes) {

    private var saliendo = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                regresarAlDashboardConAnimacion()
            }
        })
    }

    private fun regresarAlDashboardConAnimacion() {
        if (saliendo) return
        saliendo = true

        val lottie = requireActivity().findViewById<LottieAnimationView>(R.id.lottieGanchillo)
        if (lottie == null) {
            parentFragmentManager.popBackStack()
            return
        }

        lottie.tag = "animando_mes"
        lottie.visibility = View.VISIBLE
        lottie.setAnimation(R.raw.animacion_mes)
        lottie.repeatCount = 0
        lottie.speed = 1f
        lottie.progress = 0f
        lottie.removeAllAnimatorListeners()

        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lottie.removeAllAnimatorListeners()

                parentFragmentManager.popBackStack()

                val duracion = if (lottie.duration > 0) lottie.duration else 750L
                val animadorReversa = ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = duracion
                    addUpdateListener { va ->
                        lottie.progress = va.animatedValue as Float
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(anim: Animator) {
                            lottie.tag = ""
                            lottie.visibility = View.GONE

                            lottie.setAnimation(R.raw.animacion_ganchillo)
                            lottie.pauseAnimation()
                            lottie.progress = 0f
                            lottie.visibility = View.VISIBLE
                        }
                    })
                }

                lottie.post {
                    animadorReversa.start()
                }
            }
        })
        lottie.playAnimation()
    }
}