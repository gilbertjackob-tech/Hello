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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.glassbox.hello.ui.theme.HelloAppThemes
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloThemeRuntime

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
            val isCute = HelloThemeRuntime.activePalette.value.id == HelloAppThemes.Cute.id

            // Base vertical gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HelloColors.BgBase,
                        HelloColors.BgDeep
                    )
                )
            )

            if (isCute) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFD6E8),
                            Color(0xFFFFF5FA),
                            Color(0xFFFFC7DE)
                        )
                    )
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.52f),
                    center = Offset(w * 0.12f, h * 0.18f),
                    radius = w * 0.22f
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.36f),
                    center = Offset(w * 0.86f, h * 0.82f),
                    radius = w * 0.30f
                )
                drawCircle(
                    color = HelloColors.OrbTeal,
                    center = Offset(w * 0.78f, h * 0.12f),
                    radius = w * 0.36f
                )
                val dotStep = 44.dp.toPx()
                var y = 18.dp.toPx()
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) 18.dp.toPx() else 40.dp.toPx()
                    while (x < w) {
                        drawCircle(
                            color = HelloColors.Accent.copy(alpha = 0.12f),
                            center = Offset(x, y),
                            radius = 1.1.dp.toPx()
                        )
                        x += dotStep
                    }
                    y += dotStep
                    row += 1
                }
                listOf(
                    Offset(w * 0.18f, h * 0.36f),
                    Offset(w * 0.72f, h * 0.30f),
                    Offset(w * 0.34f, h * 0.76f),
                    Offset(w * 0.88f, h * 0.56f),
                    Offset(w * 0.58f, h * 0.66f)
                ).forEach { center ->
                    val radius = 7.dp.toPx()
                    val color = Color.White.copy(alpha = 0.78f)
                    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 1.4.dp.toPx())
                    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 1.4.dp.toPx())
                }
                listOf(
                    Offset(w * 0.10f, h * 0.52f),
                    Offset(w * 0.88f, h * 0.36f),
                    Offset(w * 0.22f, h * 0.86f),
                    Offset(w * 0.72f, h * 0.78f)
                ).forEach { center ->
                    drawCuteHeart(center, 10.dp.toPx(), HelloColors.Accent.copy(alpha = 0.22f))
                }
                drawCuteCloud(Offset(w * 0.14f, h * 0.78f), w * 0.34f, Color.White.copy(alpha = 0.34f))
                drawCuteCloud(Offset(w * 0.82f, h * 0.22f), w * 0.25f, Color.White.copy(alpha = 0.26f))
                return@Canvas
            }

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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCuteHeart(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.72f)
        cubicTo(center.x - radius * 1.5f, center.y - radius * 0.1f, center.x - radius * 0.9f, center.y - radius * 1.25f, center.x, center.y - radius * 0.42f)
        cubicTo(center.x + radius * 0.9f, center.y - radius * 1.25f, center.x + radius * 1.5f, center.y - radius * 0.1f, center.x, center.y + radius * 0.72f)
        close()
    }
    drawPath(path = path, color = color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCuteCloud(
    center: Offset,
    width: Float,
    color: Color
) {
    drawCircle(color, width * 0.18f, Offset(center.x - width * 0.22f, center.y + width * 0.02f))
    drawCircle(color, width * 0.24f, Offset(center.x, center.y - width * 0.04f))
    drawCircle(color, width * 0.16f, Offset(center.x + width * 0.22f, center.y + width * 0.03f))
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - width * 0.34f, center.y),
        size = androidx.compose.ui.geometry.Size(width * 0.68f, width * 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.09f, width * 0.09f)
    )
}
