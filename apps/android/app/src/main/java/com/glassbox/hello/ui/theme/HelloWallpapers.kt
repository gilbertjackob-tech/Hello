package com.glassbox.hello.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.glassbox.hello.R

object HelloWallpapers {
    const val Default = "default"
    const val None = "none"
    const val ThemeCute = "theme cute"
    const val ThemeMidnight = "theme midnight"
    const val ThemeAmoled = "theme amoled"
    const val ThemeTwilight = "theme twilight"
    const val ThemeEmber = "theme ember"
    const val ThemeArctic = "theme arctic"
    const val ThemeDusk = "theme dusk"
    const val ThemeLight = "theme light"
    const val ThemeDark = "theme dark"
    const val SilkGlow = "silk glow"
    const val PaperGrain = "paper grain"
    const val Aurora = "aurora"
    const val MetroGrid = "metro grid"
    const val EmeraldFabric = "emerald fabric"
    const val SlateGlass = "slate glass"
    const val RoseMist = "rose mist"
    const val Linen = "linen"
    const val CustomImage = "custom image"

    val Options = listOf(
        Default,
        ThemeCute,
        ThemeMidnight,
        ThemeAmoled,
        ThemeTwilight,
        ThemeEmber,
        ThemeArctic,
        ThemeDusk,
        ThemeLight,
        ThemeDark,
        SilkGlow,
        PaperGrain,
        Aurora,
        MetroGrid,
        EmeraldFabric,
        SlateGlass,
        RoseMist,
        Linen,
        None,
        CustomImage
    )
}

@Composable
fun ChatWallpaperBackground(
    wallpaper: String,
    opacity: Float,
    modifier: Modifier = Modifier,
    darkOverride: Boolean? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val alpha = opacity.coerceIn(0f, 1f)
    val dark = darkOverride ?: HelloThemeRuntime.darkThemeActive
    val normalized = wallpaper.lowercase()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        when (normalized) {
            HelloWallpapers.None -> Unit
            HelloWallpapers.ThemeCute,
            HelloWallpapers.ThemeMidnight,
            HelloWallpapers.ThemeAmoled,
            HelloWallpapers.ThemeTwilight,
            HelloWallpapers.ThemeEmber,
            HelloWallpapers.ThemeArctic,
            HelloWallpapers.ThemeDusk,
            HelloWallpapers.ThemeLight,
            HelloWallpapers.ThemeDark -> ThemeImageLayer(normalized, alpha)
            HelloWallpapers.SilkGlow -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF10232B), Color(0xFF08131B), Color(0xFF16353B)) else listOf(Color(0xFFF6F0E8), Color(0xFFFDF8F1), Color(0xFFECEAE2)))
                GlowBlobLayer(alpha, if (dark) Color(0x3328C0A4) else Color(0x26C99A4A))
            }
            HelloWallpapers.PaperGrain -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF181818), Color(0xFF0F1112)) else listOf(Color(0xFFF7F1E6), Color(0xFFF2E9DA)))
                NoiseLayer(alpha, if (dark) Color.White.copy(alpha = 0.06f) else Color(0xFF5D564A).copy(alpha = 0.08f))
            }
            HelloWallpapers.Aurora -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF071219), Color(0xFF0B2020), Color(0xFF182C4D)) else listOf(Color(0xFFF3FBFE), Color(0xFFEFF7EA), Color(0xFFFFF2EA)))
                AuroraLayer(alpha, dark)
            }
            HelloWallpapers.MetroGrid -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF07131C), Color(0xFF0A1620)) else listOf(Color(0xFFF7FAFD), Color(0xFFEFF3F8)))
                GridLayer(alpha, dark)
            }
            HelloWallpapers.EmeraldFabric -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF06241F), Color(0xFF081519), Color(0xFF144038)) else listOf(Color(0xFFEFF8F4), Color(0xFFF9FCFA), Color(0xFFE5F1EA)))
                FabricLayer(alpha, dark)
            }
            HelloWallpapers.SlateGlass -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF071219), Color(0xFF122532), Color(0xFF0D1821)) else listOf(Color(0xFFF4F8FB), Color(0xFFE9F0F4), Color(0xFFF8FBFD)))
                GlassLineLayer(alpha, dark)
            }
            HelloWallpapers.RoseMist -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF24131D), Color(0xFF0E1119), Color(0xFF342132)) else listOf(Color(0xFFFFF1F4), Color(0xFFFFF8F8), Color(0xFFF7EAEE)))
                GlowBlobLayer(alpha, if (dark) Color(0x2AFF7B84) else Color(0x1FFF8EA3))
            }
            HelloWallpapers.Linen -> {
                GradientLayer(alpha, if (dark) listOf(Color(0xFF151A1F), Color(0xFF0D1217)) else listOf(Color(0xFFFAF6EE), Color(0xFFF2EBDF)))
                LinenLayer(alpha, dark)
            }
            HelloWallpapers.CustomImage -> CustomImagePlaceholder(alpha, dark)
            else -> {
                GradientLayer(alpha, if (dark) listOf(HelloColors.DarkBgStrong, HelloColors.DarkBg, Color(0xFF0F2320)) else listOf(Color(0xFFF7F2E9), Color(0xFFFBF8F2), Color(0xFFE6F2EE)))
                AuroraLayer(alpha * 0.55f, dark)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background((if (dark) HelloColors.DarkBg else HelloColors.Bg).copy(alpha = if (dark) 0.14f else 0.07f))
        )
        content()
    }
}

@Composable
private fun CuteWallpaperLayer(alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF6FA).copy(alpha = alpha.coerceAtLeast(0.18f)),
                        Color(0xFFFFE1EC).copy(alpha = alpha.coerceAtLeast(0.18f)),
                        Color(0xFFFFF4F8).copy(alpha = alpha.coerceAtLeast(0.18f))
                    )
                )
            )
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cloud = Color.White.copy(alpha = 0.44f * alpha)
        val pink = Color(0xFFFF6FAE).copy(alpha = 0.18f * alpha)
        val linePink = Color(0xFFE83F86).copy(alpha = 0.18f * alpha)
        val star = Color.White.copy(alpha = 0.86f * alpha)
        drawCircle(cloud, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.18f, size.height * 0.18f))
        drawCircle(cloud.copy(alpha = cloud.alpha * 0.72f), radius = size.minDimension * 0.20f, center = Offset(size.width * 0.86f, size.height * 0.82f))
        drawCircle(pink, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.82f, size.height * 0.18f))
        drawCircle(pink.copy(alpha = pink.alpha * 0.72f), radius = size.minDimension * 0.14f, center = Offset(size.width * 0.12f, size.height * 0.78f))

        val diagonalGap = 64.dp.toPx()
        var startX = -size.height
        while (startX < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.18f * alpha),
                start = Offset(startX, 0f),
                end = Offset(startX + size.height, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 12.dp.toPx()))
            )
            startX += diagonalGap
        }

        val dotStep = 38.dp.toPx()
        var y = 14.dp.toPx()
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 18.dp.toPx() else 36.dp.toPx()
            while (x < size.width) {
                drawCircle(Color(0xFFEC5D97).copy(alpha = 0.12f * alpha), radius = 1.1.dp.toPx(), center = Offset(x, y))
                x += dotStep
            }
            y += dotStep
            row += 1
        }

        listOf(
            Offset(size.width * 0.12f, size.height * 0.34f),
            Offset(size.width * 0.72f, size.height * 0.28f),
            Offset(size.width * 0.34f, size.height * 0.72f),
            Offset(size.width * 0.88f, size.height * 0.58f)
        ).forEach { center ->
            val r = 7.dp.toPx()
            drawLine(star, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 1.3.dp.toPx())
            drawLine(star, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 1.3.dp.toPx())
        }

        listOf(
            Offset(size.width * 0.18f, size.height * 0.50f),
            Offset(size.width * 0.62f, size.height * 0.44f),
            Offset(size.width * 0.84f, size.height * 0.72f),
            Offset(size.width * 0.30f, size.height * 0.86f)
        ).forEach { center ->
            drawCuteHeart(center, 9.dp.toPx(), Color(0xFFFF6FAE).copy(alpha = 0.22f * alpha))
        }

        drawArc(
            color = linePink,
            startAngle = 95f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(size.width * 0.13f, size.height * 0.24f),
            size = Size(88.dp.toPx(), 88.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawArc(
            color = linePink.copy(alpha = linePink.alpha * 0.7f),
            startAngle = 95f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(size.width * 0.63f, size.height * 0.58f),
            size = Size(112.dp.toPx(), 112.dp.toPx()),
            style = Stroke(width = 1.1.dp.toPx())
        )
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

@Composable
private fun ThemeImageLayer(wallpaper: String, alpha: Float) {
    val asset = themeImageAsset(wallpaper)
    Image(
        painter = painterResource(asset.resId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.coerceIn(0.05f, 1f))
    )
}

private data class ThemeImageAsset(val fileName: String, val resId: Int)

private fun themeImageAsset(wallpaper: String): ThemeImageAsset {
    return when (wallpaper) {
        HelloWallpapers.ThemeCute -> ThemeImageAsset("theme_cute.png", R.drawable.theme_cute)
        HelloWallpapers.ThemeAmoled -> ThemeImageAsset("theme_amoled.png", R.drawable.theme_amoled)
        HelloWallpapers.ThemeTwilight -> ThemeImageAsset("theme_twilight.png", R.drawable.theme_twilight)
        HelloWallpapers.ThemeEmber -> ThemeImageAsset("theme_ember.png", R.drawable.theme_ember)
        HelloWallpapers.ThemeArctic -> ThemeImageAsset("theme_arctic.png", R.drawable.theme_arctic)
        HelloWallpapers.ThemeDusk -> ThemeImageAsset("theme_dusk.png", R.drawable.theme_dusk)
        HelloWallpapers.ThemeLight -> ThemeImageAsset("theme_light.png", R.drawable.theme_light)
        HelloWallpapers.ThemeDark -> ThemeImageAsset("theme_dark.png", R.drawable.theme_dark)
        else -> ThemeImageAsset("theme_midnight.png", R.drawable.theme_midnight)
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
private fun GlowBlobLayer(alpha: Float, glowColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(glowColor.copy(alpha = 0.9f * alpha), Color.Transparent)),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.18f, size.height * 0.16f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(glowColor.copy(alpha = 0.62f * alpha), Color.Transparent)),
            radius = size.minDimension * 0.36f,
            center = Offset(size.width * 0.84f, size.height * 0.74f)
        )
    }
}

@Composable
private fun NoiseLayer(alpha: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 14.dp.toPx()
        val radius = 0.8.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < size.width) {
                drawCircle(color = color.copy(alpha = color.alpha * alpha), radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
            row += 1
        }
    }
}

@Composable
private fun AuroraLayer(alpha: Float, dark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val colors = if (dark) {
            listOf(Color(0x4019D3AE), Color(0x303979FF), Color(0x18FF7B84), Color.Transparent)
        } else {
            listOf(Color(0x26A0E9D9), Color(0x24A6B8FF), Color(0x18FFC3A3), Color.Transparent)
        }
        drawRect(brush = Brush.linearGradient(colors, start = Offset(size.width * 0.1f, 0f), end = Offset(size.width, size.height * 0.8f), tileMode = androidx.compose.ui.graphics.TileMode.Clamp), alpha = alpha)
    }
}

@Composable
private fun GridLayer(alpha: Float, dark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = if (dark) Color.White.copy(alpha = 0.06f * alpha) else Color(0xFF64748B).copy(alpha = 0.08f * alpha)
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(stroke, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(stroke, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
    }
}

@Composable
private fun FabricLayer(alpha: Float, dark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = if (dark) Color.White.copy(alpha = 0.05f * alpha) else Color(0xFF2D6B57).copy(alpha = 0.06f * alpha)
        val step = 22.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(stroke, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
        var x = 0f
        while (x < size.width) {
            drawLine(stroke, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}

@Composable
private fun GlassLineLayer(alpha: Float, dark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = if (dark) Color.White.copy(alpha = 0.08f * alpha) else Color(0xFF7A8CA4).copy(alpha = 0.08f * alpha)
        val stroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 18.dp.toPx()))
        )
        var y = 36.dp.toPx()
        while (y < size.height) {
            drawLine(
                color = color,
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
private fun LinenLayer(alpha: Float, dark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val vertical = if (dark) Color.White.copy(alpha = 0.03f * alpha) else Color(0xFF7D6E59).copy(alpha = 0.06f * alpha)
        val horizontal = if (dark) Color.White.copy(alpha = 0.02f * alpha) else Color(0xFF7D6E59).copy(alpha = 0.04f * alpha)
        val step = 18.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(vertical, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8.dp.toPx())
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(horizontal, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
            y += step
        }
    }
}

@Composable
private fun CustomImagePlaceholder(alpha: Float, dark: Boolean) {
    GradientLayer(alpha, if (dark) listOf(Color(0xFF101B21), Color(0xFF071219)) else listOf(Color(0xFFF4F0E9), Color(0xFFF9F5EE)))
}
