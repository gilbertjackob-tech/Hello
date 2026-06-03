package com.glassbox.hello.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

@Composable
fun HelloTheme(
    themeMode: String = HelloAppThemes.DefaultId,
    content: @Composable (Boolean) -> Unit
) {
    val palette = HelloAppThemes.byId(themeMode)
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = if (palette.id == "ember") Color(0xFF1B1002) else Color(0xFF001A14),
            primaryContainer = palette.outgoing,
            onPrimaryContainer = palette.outgoingText,
            secondary = palette.warm,
            onSecondary = if (palette.id == "arctic") Color.White else Color(0xFF111827),
            background = palette.bgDeep,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderStrong,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accentStrong,
            secondary = palette.warm,
            onSecondary = Color.White,
            background = palette.bgDeep,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderStrong,
            error = palette.danger
        )
    }

    SideEffect {
        HelloThemeRuntime.activePalette.value = palette
        HelloThemeRuntime.darkMode.value = palette.isDark
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = HelloTypography.MaterialTypography,
        content = { content(palette.isDark) }
    )
}
