package com.glassbox.hello.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

object HelloWallpapers {
    const val Default = "default"
    const val None = "none"
    const val SoftGradient = "soft gradient"
    const val DeepNavy = "deep navy"
    const val GreenMist = "green mist"
    const val MidnightDots = "midnight dots"
    const val WarmPaper = "warm paper"
    const val BlueGlass = "blue glass"
    const val CustomImage = "custom image"

    val Options = listOf(
        Default,
        None,
        SoftGradient,
        DeepNavy,
        GreenMist,
        MidnightDots,
        WarmPaper,
        BlueGlass,
        CustomImage
    )
}

@Composable
fun ChatWallpaperBackground(
    wallpaper: String,
    opacity: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val alpha = opacity.coerceIn(0f, 1f)
    val normalized = wallpaper.lowercase()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
    ) {
        when (normalized) {
            HelloWallpapers.None -> Unit
            HelloWallpapers.SoftGradient -> GradientLayer(
                alpha,
                listOf(Color(0xFF172F2B), Color(0xFF08141B), Color(0xFF143D34))
            )
            HelloWallpapers.DeepNavy -> GradientLayer(
                alpha,
                listOf(Color(0xFF071A2B), Color(0xFF020A12), Color(0xFF10253A))
            )
            HelloWallpapers.GreenMist -> GradientLayer(
                alpha,
                listOf(Color(0xFF073B33), Color(0xFF0A181A), Color(0xFF155545))
            )
            HelloWallpapers.MidnightDots -> {
                GradientLayer(alpha, listOf(Color(0xFF081118), Color(0xFF05090D)))
                DotLayer(alpha)
            }
            HelloWallpapers.WarmPaper -> GradientLayer(
                alpha,
                listOf(Color(0xFF2C241D), Color(0xFF11100E), Color(0xFF3A3326))
            )
            HelloWallpapers.BlueGlass -> {
                GradientLayer(alpha, listOf(Color(0xFF082133), Color(0xFF071219), Color(0xFF0E3A4B)))
                GlassLineLayer(alpha)
            }
            HelloWallpapers.CustomImage -> CustomImagePlaceholder(alpha)
            else -> GradientLayer(
                alpha,
                listOf(HelloColors.DarkBgStrong, HelloColors.DarkBg, Color(0xFF0F2320))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HelloColors.DarkBg.copy(alpha = 0.18f))
        )
        content()
    }
}

@Composable
private fun GradientLayer(alpha: Float, colors: List<Color>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors.map { it.copy(alpha = alpha.coerceAtLeast(0.08f)) }))
    )
}

@Composable
private fun DotLayer(alpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 28.dp.toPx()
        val radius = 1.1.dp.toPx()
        var y = 10.dp.toPx()
        while (y < size.height) {
            var x = 12.dp.toPx()
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.12f * alpha), radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun GlassLineLayer(alpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 18.dp.toPx()))
        )
        var y = 36.dp.toPx()
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.08f * alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y + 80.dp.toPx()),
                strokeWidth = stroke.width,
                pathEffect = stroke.pathEffect
            )
            y += 92.dp.toPx()
        }
    }
}

@Composable
private fun CustomImagePlaceholder(alpha: Float) {
    GradientLayer(alpha, listOf(Color(0xFF101B21), Color(0xFF071219)))
}
