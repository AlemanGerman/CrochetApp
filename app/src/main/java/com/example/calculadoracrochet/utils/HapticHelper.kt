package com.example.calculadoracrochet.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.fragment.app.Fragment

object HapticHelper {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun vibrarConEfecto(context: Context, effect: VibrationEffect, fallbackDurationMs: Long = 30) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(fallbackDurationMs)
            }
        } catch (_: Exception) {}
    }

    private fun vibrarOnetime(context: Context, durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmplitude = amplitude.coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, safeAmplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    /**
     * Feedback para botones principales (CTA: "Nuevo Proyecto", "Guardar", "Siguiente").
     */
    fun clickPrincipal(view: View? = null, context: Context? = view?.context) {
        view?.isHapticFeedbackEnabled = true
        val handled = view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        ) ?: false

        if (!handled && context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrarConEfecto(context, VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), 25)
            } else {
                vibrarOnetime(context, 25, 180)
            }
        }
    }

    /**
     * Micro-feedback muy fino para selecciones:
     * - Tabs/chips de carpetas
     * - Toggles Sí/No
     * - Slider de margen de ganancia
     * - Salario mínimo
     * - Criterios de ordenación
     */
    fun seleccion(view: View? = null, context: Context? = view?.context) {
        view?.isHapticFeedbackEnabled = true
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }

        val handled = view?.performHapticFeedback(
            flag,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        ) ?: false

        if (!handled && context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrarConEfecto(context, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK), 12)
            } else {
                vibrarOnetime(context, 12, 100)
            }
        }
    }

    /**
     * Feedback de celebración / éxito al completar el guardado del proyecto o restaurar.
     * Ritmo doble pulso placentero: corto suave + corto marcado.
     */
    fun exito(view: View? = null, context: Context? = view?.context) {
        view?.isHapticFeedbackEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CONFIRM,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }

        context?.let { ctx ->
            try {
                val vibrator = getVibrator(ctx) ?: return
                if (!vibrator.hasVibrator()) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 35, 60, 60)
                    val amplitudes = intArrayOf(0, 160, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 35, 60, 60), -1)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Feedback para acciones destructivas o de advertencia:
     * - Eliminar fila de material o accesorio
     * - Mover a papelera
     * - Eliminar definitivamente
     * - Vaciar papelera
     */
    fun advertenciaOEliminar(view: View? = null, context: Context? = view?.context) {
        view?.isHapticFeedbackEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(
                HapticFeedbackConstants.REJECT,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }

        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrarConEfecto(ctx, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), 55)
            } else {
                vibrarOnetime(ctx, 55, 255)
            }
        }
    }

    /**
     * Feedback de error o validación bloqueada:
     * - Tope al intentar avanzar sin datos obligatorios
     * - Intentar guardar sin nombre
     */
    fun error(view: View? = null, context: Context? = view?.context) {
        view?.isHapticFeedbackEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(
                HapticFeedbackConstants.REJECT,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }

        context?.let { ctx ->
            try {
                val vibrator = getVibrator(ctx) ?: return
                if (!vibrator.hasVibrator()) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 40, 50, 45)
                    val amplitudes = intArrayOf(0, 220, 0, 220)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 40, 50, 45), -1)
                }
            } catch (_: Exception) {}
        }
    }
}

// Extensions for Views
fun View.hapticClickPrincipal() = HapticHelper.clickPrincipal(this)
fun View.hapticSeleccion() = HapticHelper.seleccion(this)
fun View.hapticExito() = HapticHelper.exito(this)
fun View.hapticEliminar() = HapticHelper.advertenciaOEliminar(this)
fun View.hapticError() = HapticHelper.error(this)

// Extensions for Fragments
fun Fragment.hapticClickPrincipal() = HapticHelper.clickPrincipal(view, context)
fun Fragment.hapticSeleccion() = HapticHelper.seleccion(view, context)
fun Fragment.hapticExito() = HapticHelper.exito(view, context)
fun Fragment.hapticEliminar() = HapticHelper.advertenciaOEliminar(view, context)
fun Fragment.hapticError() = HapticHelper.error(view, context)
