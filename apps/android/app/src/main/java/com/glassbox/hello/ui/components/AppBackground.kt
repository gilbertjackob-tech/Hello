package com.glassbox.hello.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.glassbox.hello.ui.theme.HelloColors

/**
 * Full-screen layered background — use this as the root of every screen.
 * Provides: deep gradient + 3 ambient orb shapes for depth.
 * Cost: single Canvas draw pass — very cheap.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.BgDeep)  // base fallback
    ) {
        // Layered gradient + orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Base vertical gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HelloColors.BgBase,
                        HelloColors.BgDeep
                    )
                )
            )

            // Top-right teal orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HelloColors.OrbTeal,
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.08f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.85f, h * 0.08f),
                radius = w * 0.55f
            )

            // Bottom-left purple orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HelloColors.OrbPurple,
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.75f),
                    radius = w * 0.5f
                ),
                center = Offset(w * 0.15f, h * 0.75f),
                radius = w * 0.5f
            )

            // Center-right warm orb (subtle)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HelloColors.OrbWarm,
                        Color.Transparent
                    ),
                    center = Offset(w * 0.7f, h * 0.45f),
                    radius = w * 0.4f
                ),
                center = Offset(w * 0.7f, h * 0.45f),
                radius = w * 0.4f
            )
        }

        content()
    }
}
