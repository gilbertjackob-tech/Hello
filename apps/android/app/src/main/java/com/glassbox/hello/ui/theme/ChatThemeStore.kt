package com.glassbox.hello.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
    val incomingArgb: Int,
    val wallpaper: String,
    val wallpaperOpacity: Int = 100,
    val darkMode: Boolean = true
)

data class ChatThemeSelection(
    val colorId: String = ChatThemeStore.DefaultColorId,
    val incomingArgb: Int = ChatThemeStore.DefaultIncomingArgb,
    val wallpaper: String = HelloWallpapers.Default,
    val wallpaperOpacity: Int = 100,
    val darkMode: Boolean = true,
    val themeId: String = ChatThemeStore.DefaultThemeId
) {
    val color: Color get() = ChatThemeStore.colorById(colorId).color
    val incomingColor: Color get() = Color(incomingArgb)
    val outgoingTextColor: Color get() = ChatThemeStore.readableTextOn(color)
    val incomingTextColor: Color get() = ChatThemeStore.readableTextOn(incomingColor)
}

object ChatThemeStore {
    const val DefaultThemeId = "classic-green"
    const val DefaultColorId = "green"
    const val DefaultIncomingArgb: Int = 0xFF1F2C34.toInt()

    private const val PREFS_NAME = "hello_chat_theme"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_COLOR_ID = "color_id"
    private const val KEY_INCOMING_ARGB = "incoming_argb"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_WALLPAPER_OPACITY = "wallpaper_opacity"
    private const val KEY_DARK_MODE = "dark_mode"

    val Colors = listOf(
        ChatColorOption("green", "Green", 0xFF128C7E.toInt()),
        ChatColorOption("emerald", "Emerald", 0xFF0F9F6E.toInt()),
        ChatColorOption("jade", "Jade", 0xFF047857.toInt()),
        ChatColorOption("sapphire", "Sapphire", 0xFF1D4ED8.toInt()),
        ChatColorOption("blue", "Blue", 0xFF2D7FF9.toInt()),
        ChatColorOption("sky", "Sky", 0xFF0EA5E9.toInt()),
        ChatColorOption("royal", "Royal", 0xFF4338CA.toInt()),
        ChatColorOption("purple", "Purple", 0xFF7C3AED.toInt()),
        ChatColorOption("amethyst", "Amethyst", 0xFF9333EA.toInt()),
        ChatColorOption("rose", "Rose", 0xFFE11D48.toInt()),
        ChatColorOption("ruby", "Ruby", 0xFFBE123C.toInt()),
        ChatColorOption("wine", "Wine", 0xFF9F1239.toInt()),
        ChatColorOption("amber", "Amber", 0xFFD97706.toInt()),
        ChatColorOption("champagne", "Champagne", 0xFFB7791F.toInt()),
        ChatColorOption("gold", "Gold", 0xFFCA8A04.toInt()),
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
        ChatThemeOption("classic-green", "Classic", "green", DefaultIncomingArgb, HelloWallpapers.Default),
        ChatThemeOption("royal-emerald", "Royal Emerald", "emerald", 0xFF102821.toInt(), HelloWallpapers.EmeraldFabric, 92),
        ChatThemeOption("sapphire-pearl", "Sapphire Pearl", "sapphire", 0xFFEAF2FF.toInt(), HelloWallpapers.SlateGlass, 86, darkMode = false),
        ChatThemeOption("amethyst-noir", "Amethyst Noir", "amethyst", 0xFF21172D.toInt(), HelloWallpapers.SlateGlass, 96),
        ChatThemeOption("midnight-gold", "Midnight Gold", "gold", 0xFF1F2937.toInt(), HelloWallpapers.MetroGrid, 88),
        ChatThemeOption("ruby-rose", "Ruby Rose", "ruby", 0xFF33141D.toInt(), HelloWallpapers.RoseMist, 94),
        ChatThemeOption("champagne-linen", "Champagne", "champagne", 0xFFFFF6DE.toInt(), HelloWallpapers.Linen, 92, darkMode = false),
        ChatThemeOption("ocean-silk", "Ocean Silk", "teal", 0xFFE7FAF5.toInt(), HelloWallpapers.SilkGlow, 90, darkMode = false),
        ChatThemeOption("violet-aurora", "Violet Aurora", "royal", 0xFF181A35.toInt(), HelloWallpapers.Aurora, 100),
        ChatThemeOption("jade-paper", "Jade Paper", "jade", 0xFFEAF7EF.toInt(), HelloWallpapers.PaperGrain, 88, darkMode = false),
        ChatThemeOption("wine-glass", "Wine Glass", "wine", 0xFF2B1720.toInt(), HelloWallpapers.SlateGlass, 92),
        ChatThemeOption("silver-blue", "Silver Blue", "blue", 0xFFF0F6FF.toInt(), HelloWallpapers.MetroGrid, 82, darkMode = false)
    )

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun read(context: Context, userId: String): ChatThemeSelection {
        val prefs = prefs(context)
        val prefix = keyPrefix(userId)
        val themeId = prefs.getString("$prefix$KEY_THEME_ID", DefaultThemeId) ?: DefaultThemeId
        val colorId = prefs.getString("$prefix$KEY_COLOR_ID", null)
        val incomingArgb = prefs.getInt("$prefix$KEY_INCOMING_ARGB", Int.MIN_VALUE)
        val wallpaper = prefs.getString("$prefix$KEY_WALLPAPER", null)
        val opacity = prefs.getInt("$prefix$KEY_WALLPAPER_OPACITY", Int.MIN_VALUE)
        val darkMode = if (prefs.contains("$prefix$KEY_DARK_MODE")) {
            prefs.getBoolean("$prefix$KEY_DARK_MODE", true)
        } else {
            null
        }
        val theme = themeById(themeId)
        return ChatThemeSelection(
            colorId = colorById(colorId ?: theme.colorId).id,
            incomingArgb = if (incomingArgb == Int.MIN_VALUE) theme.incomingArgb else incomingArgb,
            wallpaper = Wallpapers.firstOrNull { it == (wallpaper ?: theme.wallpaper) } ?: theme.wallpaper,
            wallpaperOpacity = (if (opacity == Int.MIN_VALUE) theme.wallpaperOpacity else opacity).coerceIn(35, 100),
            darkMode = darkMode ?: theme.darkMode,
            themeId = theme.id
        )
    }

    fun save(context: Context, userId: String, selection: ChatThemeSelection) {
        val prefix = keyPrefix(userId)
        prefs(context).edit()
            .putString("$prefix$KEY_THEME_ID", selection.themeId)
            .putString("$prefix$KEY_COLOR_ID", colorById(selection.colorId).id)
            .putInt("$prefix$KEY_INCOMING_ARGB", selection.incomingArgb)
            .putString("$prefix$KEY_WALLPAPER", Wallpapers.firstOrNull { it == selection.wallpaper } ?: HelloWallpapers.Default)
            .putInt("$prefix$KEY_WALLPAPER_OPACITY", selection.wallpaperOpacity.coerceIn(35, 100))
            .putBoolean("$prefix$KEY_DARK_MODE", selection.darkMode)
            .apply()
    }

    fun selectionForTheme(theme: ChatThemeOption): ChatThemeSelection {
        return ChatThemeSelection(
            colorId = theme.colorId,
            incomingArgb = theme.incomingArgb,
            wallpaper = theme.wallpaper,
            wallpaperOpacity = theme.wallpaperOpacity,
            darkMode = theme.darkMode,
            themeId = theme.id
        )
    }

    fun colorById(id: String?): ChatColorOption {
        return Colors.firstOrNull { it.id == id } ?: Colors.first { it.id == DefaultColorId }
    }

    fun themeById(id: String?): ChatThemeOption {
        return Themes.firstOrNull { it.id == id } ?: Themes.first { it.id == DefaultThemeId }
    }

    fun companionIncomingArgb(colorId: String, darkMode: Boolean): Int {
        if (!darkMode) {
            return when (colorId) {
                "amber", "champagne", "gold" -> 0xFFFFF6DE.toInt()
                "rose", "ruby", "wine", "pink" -> 0xFFFFEEF3.toInt()
                "purple", "amethyst", "royal", "indigo" -> 0xFFF3EEFF.toInt()
                "blue", "sapphire", "sky" -> 0xFFEAF2FF.toInt()
                "green", "emerald", "jade", "teal", "lime" -> 0xFFEAF7EF.toInt()
                else -> 0xFFF2F4F7.toInt()
            }
        }
        return when (colorId) {
            "amber", "champagne", "gold" -> 0xFF2B2414.toInt()
            "rose", "ruby", "wine", "pink" -> 0xFF33141D.toInt()
            "purple", "amethyst", "royal", "indigo" -> 0xFF21172D.toInt()
            "blue", "sapphire", "sky" -> 0xFF13233A.toInt()
            "green", "emerald", "jade", "teal", "lime" -> 0xFF102821.toInt()
            else -> DefaultIncomingArgb
        }
    }

    fun readableTextOn(color: Color): Color {
        return if (color.luminance() > 0.52f) Color(0xFF071219) else Color.White
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
