package com.glassbox.hello.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween

object HelloAnimations {
    // Spring presets for smooth, natural motion
    object Spring {
        val smooth = SpringSpec<Float>(
            dampingRatio = 0.85f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )

        val bouncy = SpringSpec<Float>(
            dampingRatio = 0.6f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )

        val stiff = SpringSpec<Float>(
            dampingRatio = 0.95f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        )
    }

    // Standard tween durations (ms)
    object Duration {
        const val INSTANT = 0
        const val FAST = 120
        const val NORMAL = 160
        const val MEDIUM = 240
        const val SLOW = 320
        const val VERY_SLOW = 480
    }

    // Easing presets
    object Easing {
        val standard = androidx.compose.animation.core.LinearEasing
        val emphasized = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        val decelerate = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)
        val accelerate = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 1f, 1f)
    }

    // Message-specific animations
    object Message {
        fun entrySpring() = Spring.smooth
        fun reactionSpring() = Spring.bouncy
        fun statusChangeSpring() = Spring.stiff
    }
}
