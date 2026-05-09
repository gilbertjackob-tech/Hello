package com.glassbox.hello.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HelloMint,
    onPrimary = Color(0xFF062F1E),
    primaryContainer = Color(0xFF1F4D35),
    onPrimaryContainer = HelloDarkText,
    secondary = HelloGreen,
    onSecondary = Color.White,
    background = HelloDarkBackground,
    onBackground = HelloDarkText,
    surface = HelloDarkSurface,
    onSurface = HelloDarkText,
    surfaceVariant = HelloDarkSurfaceAlt,
    onSurfaceVariant = HelloMutedText,
    outline = Color(0xFF6B7C83),
    error = Color(0xFFFF8A80)
)

private val LightColorScheme = lightColorScheme(
    primary = HelloGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F4DF),
    onPrimaryContainer = Color(0xFF063D27),
    secondary = HelloGreen,
    onSecondary = Color.White,
    background = HelloLightBackground,
    onBackground = HelloLightText,
    surface = HelloLightSurface,
    onSurface = HelloLightText,
    surfaceVariant = Color(0xFFE8EEF0),
    onSurfaceVariant = Color(0xFF53626A),
    outline = Color(0xFF7D8B91),
    error = Color(0xFFB3261E)
)

@Composable
fun HelloTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = HelloTypography.MaterialTypography,
        content = content
    )
}
