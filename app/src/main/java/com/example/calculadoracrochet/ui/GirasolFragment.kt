package com.example.calculadoracrochet.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.calculadoracrochet.R
import com.google.android.material.button.MaterialButton

class GirasolFragment : Fragment(R.layout.fragment_girasol) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val girasolView = view.findViewById<GirasolCrochetView>(R.id.girasolView)
        val btnReplay = view.findViewById<MaterialButton>(R.id.btnReplayAnimacion)

        btnReplay.setOnClickListener {
            btnReplay.isEnabled = false
            btnReplay.alpha = 0.75f
            btnReplay.text = "omg q chido se ve"

            girasolView.reproducirAnimacion {
                btnReplay.isEnabled = true
                btnReplay.alpha = 1.0f
                btnReplay.text = "picale si quieres verlo otra vez"
            }
        }
    }
}