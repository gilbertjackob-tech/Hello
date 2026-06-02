package com.glassbox.hello.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HelloMint,
    onPrimary = Color(0xFF062F31),
    primaryContainer = Color(0xFF17484D),
    onPrimaryContainer = HelloDarkText,
    secondary = Color(0xFFF0B35A),
    onSecondary = Color.White,
    background = HelloDarkBackground,
    onBackground = HelloDarkText,
    surface = HelloDarkSurface,
    onSurface = HelloDarkText,
    surfaceVariant = HelloDarkSurfaceAlt,
    onSurfaceVariant = HelloMutedText,
    outline = Color(0xFF70818B),
    error = Color(0xFFFF7A8A)
)

private val LightColorScheme = lightColorScheme(
    primary = HelloGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEFEB),
    onPrimaryContainer = Color(0xFF07383D),
    secondary = Color(0xFFB7652C),
    onSecondary = Color.White,
    background = HelloLightBackground,
    onBackground = HelloLightText,
    surface = HelloLightSurface,
    onSurface = HelloLightText,
    surfaceVariant = Color(0xFFE7ECEB),
    onSurfaceVariant = Color(0xFF5D6878),
    outline = Color(0xFF7C898D),
    error = Color(0xFFC44D58)
)

@Composable
fun HelloTheme(
    themeMode: String = "system",
    content: @Composable (Boolean) -> Unit
) {
    val darkTheme = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect {
        HelloThemeRuntime.darkMode.value = darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = HelloTypography.MaterialTypography,
        content = { content(darkTheme) }
    )
}
