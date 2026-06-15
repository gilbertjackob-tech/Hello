package com.glassbox.hello.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

data class HelloSettingsState(
    val themeMode: String = "cute",
    val enterSends: Boolean = true,
    val wallpaper: String = "theme cute",
    val wallpaperOpacity: Int = 100,
    val chatSounds: Boolean = true,
    val cloudChatEnabled: Boolean = true,
    val showCallLogsInChat: Boolean = false
)

object HelloPreferences {
    private const val PREFS_NAME = "hello_settings"
    const val KEY_THEME = "theme"
    const val KEY_ENTER_SENDS = "enter_sends"
    const val KEY_WALLPAPER = "wallpaper"
    const val KEY_WALLPAPER_OPACITY = "wallpaper_opacity"
    const val KEY_CHAT_SOUNDS = "chat_sounds"
    const val KEY_CLOUD_CHAT_ENABLED = "cloud_chat_enabled"
    const val KEY_SHOW_CALL_LOGS_IN_CHAT = "show_call_logs_in_chat"
    private const val KEY_CUTE_THEME_MIGRATED = "cute_theme_migrated"
    private const val KEY_CLOUD_CHAT_DEFAULT_MIGRATED = "cloud_chat_default_migrated"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): HelloSettingsState {
        val prefs = prefs(context)
        migrateCuteDefaults(prefs)
        return HelloSettingsState(
            themeMode = prefs.getString(KEY_THEME, "cute") ?: "cute",
            enterSends = prefs.getBoolean(KEY_ENTER_SENDS, true),
            wallpaper = prefs.getString(KEY_WALLPAPER, "theme cute") ?: "theme cute",
            wallpaperOpacity = prefs.getInt(KEY_WALLPAPER_OPACITY, 100),
            chatSounds = prefs.getBoolean(KEY_CHAT_SOUNDS, true),
            cloudChatEnabled = prefs.getBoolean(KEY_CLOUD_CHAT_ENABLED, true),
            showCallLogsInChat = prefs.getBoolean(KEY_SHOW_CALL_LOGS_IN_CHAT, false)
        )
    }

    private fun migrateCuteDefaults(prefs: SharedPreferences) {
        val migrateCuteTheme = !prefs.getBoolean(KEY_CUTE_THEME_MIGRATED, false)
        val migrateCloudChat = !prefs.getBoolean(KEY_CLOUD_CHAT_DEFAULT_MIGRATED, false)
        if (!migrateCuteTheme && !migrateCloudChat) return
        val savedTheme = prefs.getString(KEY_THEME, null)
        val savedWallpaper = prefs.getString(KEY_WALLPAPER, null)
        prefs.edit().apply {
            if (migrateCuteTheme && (savedTheme == null || savedTheme == "midnight" || savedTheme == "system")) {
                putString(KEY_THEME, "cute")
            }
            if (migrateCuteTheme && (savedWallpaper == null || savedWallpaper == "default")) {
                putString(KEY_WALLPAPER, "theme cute")
            }
            if (migrateCloudChat) {
                putBoolean(KEY_CLOUD_CHAT_ENABLED, true)
            }
            if (migrateCuteTheme) putBoolean(KEY_CUTE_THEME_MIGRATED, true)
            if (migrateCloudChat) putBoolean(KEY_CLOUD_CHAT_DEFAULT_MIGRATED, true)
        }.apply()
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

    fun setChatSounds(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHAT_SOUNDS, enabled).apply()
    }

    fun setCloudChatEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOUD_CHAT_ENABLED, enabled).apply()
    }

    fun setShowCallLogsInChat(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CALL_LOGS_IN_CHAT, enabled).apply()
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
