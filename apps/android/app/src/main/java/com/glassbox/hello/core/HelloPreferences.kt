package com.glassbox.hello.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

data class HelloSettingsState(
    val themeMode: String = "system",
    val enterSends: Boolean = true,
    val wallpaper: String = "default",
    val wallpaperOpacity: Int = 100
)

object HelloPreferences {
    private const val PREFS_NAME = "hello_settings"
    const val KEY_THEME = "theme"
    const val KEY_ENTER_SENDS = "enter_sends"
    const val KEY_WALLPAPER = "wallpaper"
    const val KEY_WALLPAPER_OPACITY = "wallpaper_opacity"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): HelloSettingsState {
        val prefs = prefs(context)
        return HelloSettingsState(
            themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
            enterSends = prefs.getBoolean(KEY_ENTER_SENDS, true),
            wallpaper = prefs.getString(KEY_WALLPAPER, "default") ?: "default",
            wallpaperOpacity = prefs.getInt(KEY_WALLPAPER_OPACITY, 100)
        )
    }

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME, mode).apply()
    }

    fun setEnterSends(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENTER_SENDS, enabled).apply()
    }

    fun setWallpaper(context: Context, wallpaper: String) {
        prefs(context).edit().putString(KEY_WALLPAPER, wallpaper).apply()
    }

    fun setWallpaperOpacity(context: Context, opacity: Int) {
        prefs(context).edit().putInt(KEY_WALLPAPER_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
}

@Composable
fun rememberHelloSettingsState(context: Context): State<HelloSettingsState> {
    val prefs = remember(context) { HelloPreferences.prefs(context) }
    val state = remember(context) { mutableStateOf(HelloPreferences.read(context)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state.value = HelloPreferences.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}