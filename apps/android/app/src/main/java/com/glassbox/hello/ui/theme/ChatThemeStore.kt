package com.glassbox.hello.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

data class ChatColorOption(
    val id: String,
    val label: String,
    val argb: Int
) {
    val color: Color get() = Color(argb)
}

data class ChatThemeOption(
    val id: String,
    val label: String,
    val colorId: String,
    val wallpaper: String
)

data class ChatThemeSelection(
    val colorId: String = ChatThemeStore.DefaultColorId,
    val wallpaper: String = HelloWallpapers.Default,
    val themeId: String = ChatThemeStore.DefaultThemeId
) {
    val color: Color get() = ChatThemeStore.colorById(colorId).color
}

object ChatThemeStore {
    const val DefaultThemeId = "classic-green"
    const val DefaultColorId = "green"

    private const val PREFS_NAME = "hello_chat_theme"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_COLOR_ID = "color_id"
    private const val KEY_WALLPAPER = "wallpaper"

    val Colors = listOf(
        ChatColorOption("green", "Green", 0xFF128C7E.toInt()),
        ChatColorOption("emerald", "Emerald", 0xFF0F9F6E.toInt()),
        ChatColorOption("blue", "Blue", 0xFF2D7FF9.toInt()),
        ChatColorOption("sky", "Sky", 0xFF0EA5E9.toInt()),
        ChatColorOption("purple", "Purple", 0xFF7C3AED.toInt()),
        ChatColorOption("rose", "Rose", 0xFFE11D48.toInt()),
        ChatColorOption("amber", "Amber", 0xFFD97706.toInt()),
        ChatColorOption("slate", "Slate", 0xFF475569.toInt()),
        ChatColorOption("teal", "Teal", 0xFF0D9488.toInt()),
        ChatColorOption("indigo", "Indigo", 0xFF4F46E5.toInt()),
        ChatColorOption("pink", "Pink", 0xFFDB2777.toInt()),
        ChatColorOption("lime", "Lime", 0xFF65A30D.toInt())
    )

    val Wallpapers = listOf(
        HelloWallpapers.Default,
        HelloWallpapers.SilkGlow,
        HelloWallpapers.PaperGrain,
        HelloWallpapers.Aurora,
        HelloWallpapers.MetroGrid,
        HelloWallpapers.EmeraldFabric,
        HelloWallpapers.SlateGlass,
        HelloWallpapers.RoseMist,
        HelloWallpapers.Linen,
        HelloWallpapers.None
    )

    val Themes = listOf(
        ChatThemeOption("classic-green", "Classic", "green", HelloWallpapers.Default),
        ChatThemeOption("emerald-fabric", "Emerald", "emerald", HelloWallpapers.EmeraldFabric),
        ChatThemeOption("aurora-blue", "Aurora", "blue", HelloWallpapers.Aurora),
        ChatThemeOption("purple-glass", "Purple", "purple", HelloWallpapers.SlateGlass),
        ChatThemeOption("rose-mist", "Rose", "rose", HelloWallpapers.RoseMist),
        ChatThemeOption("linen-amber", "Linen", "amber", HelloWallpapers.Linen)
    )

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun read(context: Context, userId: String): ChatThemeSelection {
        val prefs = prefs(context)
        val prefix = keyPrefix(userId)
        val themeId = prefs.getString("$prefix$KEY_THEME_ID", DefaultThemeId) ?: DefaultThemeId
        val colorId = prefs.getString("$prefix$KEY_COLOR_ID", null)
        val wallpaper = prefs.getString("$prefix$KEY_WALLPAPER", null)
        val theme = themeById(themeId)
        return ChatThemeSelection(
            colorId = colorById(colorId ?: theme.colorId).id,
            wallpaper = Wallpapers.firstOrNull { it == (wallpaper ?: theme.wallpaper) } ?: theme.wallpaper,
            themeId = theme.id
        )
    }

    fun save(context: Context, userId: String, selection: ChatThemeSelection) {
        val prefix = keyPrefix(userId)
        prefs(context).edit()
            .putString("$prefix$KEY_THEME_ID", selection.themeId)
            .putString("$prefix$KEY_COLOR_ID", colorById(selection.colorId).id)
            .putString("$prefix$KEY_WALLPAPER", Wallpapers.firstOrNull { it == selection.wallpaper } ?: HelloWallpapers.Default)
            .apply()
    }

    fun selectionForTheme(theme: ChatThemeOption): ChatThemeSelection {
        return ChatThemeSelection(colorId = theme.colorId, wallpaper = theme.wallpaper, themeId = theme.id)
    }

    fun colorById(id: String?): ChatColorOption {
        return Colors.firstOrNull { it.id == id } ?: Colors.first { it.id == DefaultColorId }
    }

    fun themeById(id: String?): ChatThemeOption {
        return Themes.firstOrNull { it.id == id } ?: Themes.first { it.id == DefaultThemeId }
    }

    private fun keyPrefix(userId: String): String {
        return "${userId.ifBlank { "local" }}."
    }
}

@Composable
fun rememberChatTheme(context: Context, userId: String): State<ChatThemeSelection> {
    val prefs = remember(context) { ChatThemeStore.prefs(context) }
    val state = remember(context, userId) { mutableStateOf(ChatThemeStore.read(context, userId)) }

    DisposableEffect(prefs, userId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state.value = ChatThemeStore.read(context, userId)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}
