package com.glassbox.hello.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class HelloAppPalette(
    val id: String,
    val label: String,
    val subtitle: String,
    val isDark: Boolean,
    val bgDeep: Color,
    val bgBase: Color,
    val surface: Color,
    val elevated: Color,
    val panel: Color,
    val panelStrong: Color,
    val panelMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val accentDeep: Color,
    val outgoing: Color,
    val outgoingBorder: Color,
    val outgoingText: Color,
    val incoming: Color,
    val incomingBorder: Color,
    val incomingText: Color,
    val warm: Color,
    val danger: Color,
    val orbA: Color,
    val orbB: Color,
    val orbC: Color,
    val previewPalette: List<Color>
)

object HelloAppThemes {
    const val DefaultId = "cute"

    val Cute = HelloAppPalette(
        id = "cute",
        label = "Cute theme",
        subtitle = "Candy pink - stitched kawaii glass",
        isDark = false,
        bgDeep = Color(0xFFFFC3DD),
        bgBase = Color(0xFFFFEAF4),
        surface = Color(0xFFFFF7FB),
        elevated = Color(0xFFFFD8EA),
        panel = Color(0xDFFFF6FA),
        panelStrong = Color(0xF9FFF8FC),
        panelMuted = Color(0xCFFFF0F7),
        border = Color(0x66FF9BC2),
        borderStrong = Color(0x99F05A9B),
        text = Color(0xFF8B1E50),
        textSecondary = Color(0xFFD14C86),
        textMuted = Color(0xFFC46A93),
        accent = Color(0xFFFF6FAE),
        accentStrong = Color(0xFFE83F86),
        accentSoft = Color(0x33FF6FAE),
        accentDeep = Color(0xFFC51E62),
        outgoing = Color(0xFFFF9FCA),
        outgoingBorder = Color(0xD9FF6FAE),
        outgoingText = Color(0xFF7D1746),
        incoming = Color(0xF9FFF8FC),
        incomingBorder = Color(0x88FF9BC2),
        incomingText = Color(0xFF84234B),
        warm = Color(0xFFFFB23F),
        danger = Color(0xFFFF4D72),
        orbA = Color(0x66FFFFFF),
        orbB = Color(0x44FF80B5),
        orbC = Color(0x33FFE08A),
        previewPalette = listOf(Color(0xFFFFC3DD), Color(0xFFFFF8FC), Color(0xFFFF6FAE), Color(0xFFFF9FCA), Color(0xFFFFB23F))
    )

    val Midnight = HelloAppPalette(
        id = "midnight",
        label = "Midnight",
        subtitle = "Deep navy - teal glow",
        isDark = true,
        bgDeep = Color(0xFF050A0F),
        bgBase = Color(0xFF0B1219),
        surface = Color(0xFF121B24),
        elevated = Color(0xFF1A2733),
        panel = Color(0x261E2A35),
        panelStrong = Color(0xF2131C24),
        panelMuted = Color(0x331A2733),
        border = Color(0x1CE2E8F0),
        borderStrong = Color(0x36E2E8F0),
        text = Color(0xFFF0F6F4),
        textSecondary = Color(0xFFA8B7C3),
        textMuted = Color(0xFF617080),
        accent = Color(0xFF00D4AA),
        accentStrong = Color(0xFF33FFCE),
        accentSoft = Color(0x2600D4AA),
        accentDeep = Color(0xFF009B82),
        outgoing = Color(0xFF0E4439),
        outgoingBorder = Color(0x7700D4AA),
        outgoingText = Color(0xFFF0F9F6),
        incoming = Color(0xFF1B2733),
        incomingBorder = Color(0x28FFFFFF),
        incomingText = Color(0xFFE9F0F6),
        warm = Color(0xFFFFB347),
        danger = Color(0xFFFF6B7A),
        orbA = Color(0x1400D4AA),
        orbB = Color(0x0D38BDF8),
        orbC = Color(0x0AFFB347),
        previewPalette = listOf(Color(0xFF03070B), Color(0xFF0B1219), Color(0xFF00D4AA), Color(0xFF134E44), Color(0xFF9B7CFF))
    )

    val Amoled = HelloAppPalette(
        id = "amoled",
        label = "Amoled",
        subtitle = "Pure black - electric blue",
        isDark = true,
        bgDeep = Color(0xFF000000),
        bgBase = Color(0xFF030507),
        surface = Color(0xFF070B10),
        elevated = Color(0xFF101826),
        panel = Color(0x241B2433),
        panelStrong = Color(0xF2070B10),
        panelMuted = Color(0x33111A27),
        border = Color(0x1F38BDF8),
        borderStrong = Color(0x6638BDF8),
        text = Color(0xFFF4F7FB),
        textSecondary = Color(0xFFA7B4C6),
        textMuted = Color(0xFF5B6677),
        accent = Color(0xFF14B8FF),
        accentStrong = Color(0xFF60D6FF),
        accentSoft = Color(0x2A14B8FF),
        accentDeep = Color(0xFF0273B8),
        outgoing = Color(0xFF062F4A),
        outgoingBorder = Color(0x7A14B8FF),
        outgoingText = Color(0xFFF0FAFF),
        incoming = Color(0xFF171827),
        incomingBorder = Color(0x2BFFFFFF),
        incomingText = Color(0xFFE8ECF7),
        warm = Color(0xFF6D6BFF),
        danger = Color(0xFFFF5F8A),
        orbA = Color(0x1014B8FF),
        orbB = Color(0x0A3B82F6),
        orbC = Color(0x0800D4AA),
        previewPalette = listOf(Color.Black, Color(0xFF02070D), Color(0xFF14B8FF), Color(0xFF06304D), Color(0xFF5B5DFF))
    )

    val Twilight = HelloAppPalette(
        id = "twilight",
        label = "Twilight",
        subtitle = "Deep purple - violet accent",
        isDark = true,
        bgDeep = Color(0xFF090315),
        bgBase = Color(0xFF120A20),
        surface = Color(0xFF1B102D),
        elevated = Color(0xFF28183F),
        panel = Color(0x2A2B1846),
        panelStrong = Color(0xF2160D25),
        panelMuted = Color(0x35281740),
        border = Color(0x2CA78BFA),
        borderStrong = Color(0x66A78BFA),
        text = Color(0xFFF4EFFD),
        textSecondary = Color(0xFFB9ACD2),
        textMuted = Color(0xFF74678B),
        accent = Color(0xFFA78BFA),
        accentStrong = Color(0xFFC4B5FD),
        accentSoft = Color(0x30A78BFA),
        accentDeep = Color(0xFF7C3AED),
        outgoing = Color(0xFF2B1754),
        outgoingBorder = Color(0x7AA78BFA),
        outgoingText = Color(0xFFF9F5FF),
        incoming = Color(0xFF211535),
        incomingBorder = Color(0x33FFFFFF),
        incomingText = Color(0xFFEDE7F8),
        warm = Color(0xFFF472B6),
        danger = Color(0xFFFB7185),
        orbA = Color(0x16A78BFA),
        orbB = Color(0x10F472B6),
        orbC = Color(0x0B38BDF8),
        previewPalette = listOf(Color(0xFF07020E), Color(0xFF160A27), Color(0xFFA78BFA), Color(0xFF27144B), Color(0xFFEC4899))
    )

    val Ember = HelloAppPalette(
        id = "ember",
        label = "Ember",
        subtitle = "Dark warm - amber fire",
        isDark = true,
        bgDeep = Color(0xFF0B0601),
        bgBase = Color(0xFF170D05),
        surface = Color(0xFF211407),
        elevated = Color(0xFF33200D),
        panel = Color(0x2F3A230B),
        panelStrong = Color(0xF21B1006),
        panelMuted = Color(0x3A3A230B),
        border = Color(0x33F59E0B),
        borderStrong = Color(0x77F59E0B),
        text = Color(0xFFFFF7ED),
        textSecondary = Color(0xFFD5C1A3),
        textMuted = Color(0xFF7F6D55),
        accent = Color(0xFFF59E0B),
        accentStrong = Color(0xFFFBBF24),
        accentSoft = Color(0x2EF59E0B),
        accentDeep = Color(0xFFB45309),
        outgoing = Color(0xFF4A2A05),
        outgoingBorder = Color(0x80F59E0B),
        outgoingText = Color(0xFFFFF7ED),
        incoming = Color(0xFF2B1B0B),
        incomingBorder = Color(0x33FFFFFF),
        incomingText = Color(0xFFF5EBD9),
        warm = Color(0xFFFFB347),
        danger = Color(0xFFFF4D4D),
        orbA = Color(0x18F59E0B),
        orbB = Color(0x0FFF4D4D),
        orbC = Color(0x0A8B5CF6),
        previewPalette = listOf(Color(0xFF050200), Color(0xFF1C1006), Color(0xFFF59E0B), Color(0xFF3A2108), Color(0xFFFF4D4D))
    )

    val Arctic = HelloAppPalette(
        id = "arctic",
        label = "Arctic",
        subtitle = "Clean white - teal calm",
        isDark = false,
        bgDeep = Color(0xFFF8FAFC),
        bgBase = Color(0xFFEFF6F8),
        surface = Color(0xFFFFFFFF),
        elevated = Color(0xFFF1F5F9),
        panel = Color(0xEFFFFFFF),
        panelStrong = Color(0xFFFFFFFF),
        panelMuted = Color(0xCCEEF5F7),
        border = Color(0x1F0F172A),
        borderStrong = Color(0x3310172A),
        text = Color(0xFF17202A),
        textSecondary = Color(0xFF607080),
        textMuted = Color(0xFF8B97A6),
        accent = Color(0xFF0D9488),
        accentStrong = Color(0xFF0F766E),
        accentSoft = Color(0x220D9488),
        accentDeep = Color(0xFF0F766E),
        outgoing = Color(0xFF0D9488),
        outgoingBorder = Color(0x660D9488),
        outgoingText = Color(0xFFFFFFFF),
        incoming = Color(0xFFFFFFFF),
        incomingBorder = Color(0x2810172A),
        incomingText = Color(0xFF17202A),
        warm = Color(0xFF3B82F6),
        danger = Color(0xFFDC2626),
        orbA = Color(0x240D9488),
        orbB = Color(0x213B82F6),
        orbC = Color(0x15A78BFA),
        previewPalette = listOf(Color(0xFFF8FAFC), Color.White, Color(0xFF0D9488), Color(0xFFCDEDF2), Color(0xFF3B82F6))
    )

    val Dusk = HelloAppPalette(
        id = "dusk",
        label = "Dusk",
        subtitle = "Slate blue - royal accent",
        isDark = true,
        bgDeep = Color(0xFF07111B),
        bgBase = Color(0xFF0B1624),
        surface = Color(0xFF111D2C),
        elevated = Color(0xFF172A40),
        panel = Color(0x2A172A40),
        panelStrong = Color(0xF2111D2C),
        panelMuted = Color(0x38172A40),
        border = Color(0x243B82F6),
        borderStrong = Color(0x663B82F6),
        text = Color(0xFFEFF6FF),
        textSecondary = Color(0xFFA9B8CC),
        textMuted = Color(0xFF61738B),
        accent = Color(0xFF3B82F6),
        accentStrong = Color(0xFF60A5FA),
        accentSoft = Color(0x2A3B82F6),
        accentDeep = Color(0xFF1D4ED8),
        outgoing = Color(0xFF102F67),
        outgoingBorder = Color(0x773B82F6),
        outgoingText = Color(0xFFEFF6FF),
        incoming = Color(0xFF17263A),
        incomingBorder = Color(0x2FFFFFFF),
        incomingText = Color(0xFFE5EEF9),
        warm = Color(0xFF06B6D4),
        danger = Color(0xFFFF6B7A),
        orbA = Color(0x143B82F6),
        orbB = Color(0x1006B6D4),
        orbC = Color(0x0A00D4AA),
        previewPalette = listOf(Color(0xFF06101A), Color(0xFF111D2C), Color(0xFF3B82F6), Color(0xFF0B274A), Color(0xFF06B6D4))
    )

    val All = listOf(Cute, Midnight, Amoled, Twilight, Ember, Arctic, Dusk)

    fun normalizeId(id: String?): String {
        return when (id?.lowercase()) {
            "cute", "cute theme", "kawaii", "pink" -> Cute.id
            "light", "white", "arctic" -> Arctic.id
            "dark", "system", "midnight" -> Midnight.id
            null, "" -> DefaultId
            else -> All.firstOrNull { it.id == id.lowercase() }?.id ?: DefaultId
        }
    }

    fun byId(id: String?): HelloAppPalette = All.first { it.id == normalizeId(id) }
}

object HelloThemeRuntime {
    val activePalette = mutableStateOf(HelloAppThemes.Midnight)
    val darkMode = mutableStateOf(true)
    val darkThemeActive: Boolean get() = activePalette.value.isDark
}

object HelloColors {
    private val p: HelloAppPalette get() = HelloThemeRuntime.activePalette.value

    val Bg: Color get() = p.bgBase
    val BgStrong: Color get() = p.bgDeep
    val Panel: Color get() = p.panel
    val PanelStrong: Color get() = p.panelStrong
    val PanelMuted: Color get() = p.panelMuted
    val Border: Color get() = p.border
    val BorderStrong: Color get() = p.borderStrong
    val Text: Color get() = p.text
    val Accent: Color get() = p.accent
    val AccentStrong: Color get() = p.accentStrong
    val AccentSoft: Color get() = p.accentSoft
    val Danger: Color get() = p.danger
    val Warning: Color get() = p.warm

    val DarkBg: Color get() = p.bgDeep
    val DarkBgStrong: Color get() = p.bgBase
    val DarkPanel: Color get() = p.panel
    val DarkPanelStrong: Color get() = p.panelStrong
    val DarkPanelMuted: Color get() = p.panelMuted
    val DarkBorder: Color get() = p.border
    val DarkBorderStrong: Color get() = p.borderStrong
    val DarkText: Color get() = p.text
    val DarkTextMuted: Color get() = p.textSecondary
    val DarkAccent: Color get() = p.accent
    val DarkAccentStrong: Color get() = p.accentStrong
    val DarkAccentSoft: Color get() = p.accentSoft
    val DarkDanger: Color get() = p.danger

    val AuthBg: Color get() = p.bgDeep
    val AuthPanel: Color get() = p.panelStrong
    val AuthInput: Color get() = p.elevated
    val AuthBorder: Color get() = p.borderStrong
    val AuthText: Color get() = p.text
    val AuthMuted: Color get() = p.textSecondary
    val AuthAccent: Color get() = p.accent
    val AuthAccentStrong: Color get() = p.accentStrong
    val AuthErrorPanel: Color get() = p.danger.copy(alpha = 0.12f)
    val AuthErrorText: Color get() = p.danger

    val MessageMine: Color get() = p.outgoing
    val MessageMineDark: Color get() = p.outgoing
    val MessageOther: Color get() = p.incoming
    val MessageOtherDark: Color get() = p.incoming
    val ReadReceipt: Color get() = if (p.id == "arctic") Color(0xFF2563EB) else p.accentStrong

    val BgDeep: Color get() = p.bgDeep
    val BgBase: Color get() = p.bgBase
    val BgSurface: Color get() = p.surface
    val BgElevated: Color get() = p.elevated

    val GlassBg: Color get() = if (p.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)
    val GlassBgMedium: Color get() = if (p.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.86f)
    val GlassBgStrong: Color get() = if (p.isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.94f)
    val GlassBorder: Color get() = if (p.isDark) Color.White.copy(alpha = 0.14f) else Color(0xFF10172A).copy(alpha = 0.18f)
    val GlassBorderStrong: Color get() = if (p.isDark) Color.White.copy(alpha = 0.22f) else Color(0xFF10172A).copy(alpha = 0.26f)

    val TealPrimary: Color get() = p.accent
    val TealLight: Color get() = p.accentStrong
    val TealDeep: Color get() = p.accentDeep
    val TealGlow: Color get() = p.accentSoft
    val TealGlowWeak: Color get() = p.accentSoft.copy(alpha = 0.12f)

    val BubbleOut: Color get() = p.outgoing
    val BubbleOutBorder: Color get() = p.outgoingBorder
    val BubbleOutText: Color get() = p.outgoingText
    val BubbleIn: Color get() = p.incoming
    val BubbleInBorder: Color get() = p.incomingBorder
    val BubbleInText: Color get() = p.incomingText

    val WarmAccent: Color get() = p.warm
    val WarmGlow: Color get() = p.warm.copy(alpha = 0.14f)
    val OnlineGreen: Color get() = Color(0xFF4ADE80)
    val OfflineGray: Color get() = Color(0xFF4A5568)
    val Sending: Color get() = Color(0xFF718096)
    val Red: Color get() = Color(0xFFE53935)
    val RedGlow: Color get() = Color(0x22E53935)

    val TextPrimary: Color get() = p.text
    val TextSecondary: Color get() = p.textSecondary
    val TextMuted: Color get() = p.textMuted
    val TextOnTeal: Color get() = if (p.id == "ember") Color(0xFF1B1002) else if (p.isDark) Color(0xFF001A14) else Color.White

    val OrbTeal: Color get() = p.orbA
    val OrbPurple: Color get() = p.orbB
    val OrbWarm: Color get() = p.orbC

    val StoryAccent: Color get() = if (p.id == "cute") p.accent else if (p.isDark) p.accent else Color(0xFFFFD600)
    val StoryAccentStrong: Color get() = if (p.id == "cute") p.accentStrong else if (p.isDark) p.accentStrong else Color(0xFFFFC400)
    val StoryPrimaryButton: Color get() = StoryAccent
    val StoryPrimaryButtonText: Color get() = if (p.isDark) TextOnTeal else Color(0xFF101418)
    val StoryRingUnseen: Color get() = StoryAccent
    val StoryRingSeen: Color get() = if (p.isDark) Color.White.copy(alpha = 0.28f) else Color(0xFFCCD2DA)
    val StoryCanvasBackground: Color get() = if (p.id == "cute") Color(0xFFFFBCD7) else if (p.isDark) p.accentDeep else Color(0xFFFFD600)
    val StoryViewerOverlay: Color get() = Color.Black.copy(alpha = if (p.isDark) 0.64f else 0.48f)
    val StoryToolRailBackground: Color get() = if (p.isDark) Color.Black.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.86f)
    val StoryPopupBackground: Color get() = if (p.isDark) p.panelStrong else Color.White
    val StoryPopupText: Color get() = if (p.isDark) p.text else Color(0xFF111827)
    val StoryBottomSheetBackground: Color get() = if (p.isDark) p.bgBase else Color.White
    val StoryProgressActive: Color get() = if (p.isDark) Color.White else Color(0xFF111827)
    val StoryProgressInactive: Color get() = if (p.isDark) Color.White.copy(alpha = 0.28f) else Color(0xFF111827).copy(alpha = 0.18f)
    val StoryReplyBackground: Color get() = if (p.isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFF3F4F6)
    val StoryReplyText: Color get() = if (p.isDark) Color.White else Color(0xFF111827)
}

object HelloDimens {
    val CornerXS = 6.dp
    val CornerS = 10.dp
    val CornerM = 14.dp
    val CornerL = 18.dp
    val CornerXL = 24.dp
    val CornerFull = 100.dp

    val BubbleCorner = 18.dp
    val BubbleCornerSmall = 6.dp

    val SpaceXS = 4.dp
    val SpaceXs = SpaceXS
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXL = 20.dp
    val SpaceXXL = 28.dp

    val TopBarHeight = 60.dp
    val BottomNavHeight = 68.dp
    val ComposerMinHeight = 56.dp
    val AvatarS = 32.dp
    val AvatarM = 44.dp
    val AvatarL = 56.dp
    val BubbleMaxWidth = 0.75f
    val ChatCardHeight = 84.dp
}

object HelloMotion {
    const val DurationFast = 140
    const val DurationMedium = 240
    const val DurationPage = 320

    fun <T> fast() = tween<T>(DurationFast, easing = FastOutSlowInEasing)
    fun <T> medium() = tween<T>(DurationMedium, easing = FastOutSlowInEasing)
    fun <T> page() = tween<T>(DurationPage, easing = FastOutSlowInEasing)

    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val SpringGentle = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
