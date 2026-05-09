package com.glassbox.hello.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import com.glassbox.hello.ui.theme.HelloAnimations

object AnimationUtils {
    /**
     * Haptic feedback helper for different interaction types
     */
    object Haptics {
        fun tapLight(context: Context) = vibrate(context, 10)
        fun tapMedium(context: Context) = vibrate(context, 20)
        fun tapStrong(context: Context) = vibrate(context, 30)

        fun sendMessage(context: Context) = vibrate(context, 15)
        fun reactionSelect(context: Context) = vibrate(context, 20)
        fun threshold(context: Context) = vibrate(context, 10)

        @Suppress("MissingPermission")
        private fun vibrate(context: Context, duration: Long) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                        ?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                vibrator?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(duration)
                    }
                }
            } catch (e: Exception) {
                // Silently ignore vibration errors
            }
        }
    }

    /**
     * Status color animation for message states
     */
    @Composable
    fun animateStatusColor(
        targetColor: Color,
        durationMs: Int = HelloAnimations.Duration.NORMAL
    ): State<Color> {
        return animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(durationMillis = durationMs),
            label = "statusColor"
        )
    }

    /**
     * Opacity animation for message appearing/disappearing
     */
    @Composable
    fun animateOpacity(
        targetAlpha: Float,
        durationMs: Int = HelloAnimations.Duration.NORMAL
    ): State<Float> {
        return animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = durationMs),
            label = "opacity"
        )
    }

    /**
     * Scale animation for reactions/badges
     */
    @Composable
    fun animateScale(
        targetScale: Float,
        durationMs: Int = HelloAnimations.Duration.FAST
    ): State<Float> {
        return animateFloatAsState(
            targetValue = targetScale,
            animationSpec = HelloAnimations.Spring.bouncy,
            label = "scale"
        )
    }
}
