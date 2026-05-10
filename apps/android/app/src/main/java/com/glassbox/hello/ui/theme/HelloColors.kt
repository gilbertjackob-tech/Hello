package com.glassbox.hello.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

object HelloThemeRuntime {
    val darkMode = mutableStateOf(true)
    val darkThemeActive: Boolean get() = darkMode.value
}

object HelloColors {
    private val lightBg = Color(0xFFF3EFE7)
    private val lightBgStrong = Color(0xFFFBF8F2)
    private val lightPanel = Color(0xC7FFFFFF)
    private val lightPanelStrong = Color(0xF0FFFFFF)
    private val lightPanelMuted = Color(0x9EFFFFFF)
    private val lightBorder = Color(0x1410172A)
    private val lightBorderStrong = Color(0x2410172A)
    private val lightText = Color(0xFF172033)
    private val lightTextMuted = Color(0xFF5D6A82)
    private val lightAccent = Color(0xFF0F8F78)
    private val lightAccentStrong = Color(0xFF0A6E5D)
    private val lightAccentSoft = Color(0x1F0F8F78)
    private val lightDanger = Color(0xFFCF4D4D)
    private val lightWarning = Color(0xFFB77800)

    private val darkBg = Color(0xFF071219)
    private val darkBgStrong = Color(0xFF0D1821)
    private val darkPanel = Color(0xCC0F1A21)
    private val darkPanelStrong = Color(0xF0111B21)
    private val darkPanelMuted = Color(0xB30D1821)
    private val darkBorder = Color(0x14E2E8F0)
    private val darkBorderStrong = Color(0x24E2E8F0)
    private val darkText = Color(0xFFEDF4FB)
    private val darkTextMuted = Color(0xFF9AA9BD)
    private val darkAccent = Color(0xFF28C0A4)
    private val darkAccentStrong = Color(0xFF49D3BC)
    private val darkAccentSoft = Color(0x2928C0A4)
    private val darkDanger = Color(0xFFFF7B84)

    val Bg: Color get() = lightBg
    val BgStrong: Color get() = lightBgStrong
    val Panel: Color get() = lightPanel
    val PanelStrong: Color get() = lightPanelStrong
    val PanelMuted: Color get() = lightPanelMuted
    val Border: Color get() = lightBorder
    val BorderStrong: Color get() = lightBorderStrong
    val Text: Color get() = lightText
    val TextMuted: Color get() = lightTextMuted
    val Accent: Color get() = lightAccent
    val AccentStrong: Color get() = lightAccentStrong
    val AccentSoft: Color get() = lightAccentSoft
    val Danger: Color get() = lightDanger
    val Warning: Color get() = lightWarning

    val DarkBg: Color get() = if (HelloThemeRuntime.darkThemeActive) darkBg else lightBg
    val DarkBgStrong: Color get() = if (HelloThemeRuntime.darkThemeActive) darkBgStrong else lightBgStrong
    val DarkPanel: Color get() = if (HelloThemeRuntime.darkThemeActive) darkPanel else lightPanel
    val DarkPanelStrong: Color get() = if (HelloThemeRuntime.darkThemeActive) darkPanelStrong else lightPanelStrong
    val DarkPanelMuted: Color get() = if (HelloThemeRuntime.darkThemeActive) darkPanelMuted else lightPanelMuted
    val DarkBorder: Color get() = if (HelloThemeRuntime.darkThemeActive) darkBorder else lightBorder
    val DarkBorderStrong: Color get() = if (HelloThemeRuntime.darkThemeActive) darkBorderStrong else lightBorderStrong
    val DarkText: Color get() = if (HelloThemeRuntime.darkThemeActive) darkText else lightText
    val DarkTextMuted: Color get() = if (HelloThemeRuntime.darkThemeActive) darkTextMuted else lightTextMuted
    val DarkAccent: Color get() = if (HelloThemeRuntime.darkThemeActive) darkAccent else lightAccent
    val DarkAccentStrong: Color get() = if (HelloThemeRuntime.darkThemeActive) darkAccentStrong else lightAccentStrong
    val DarkAccentSoft: Color get() = if (HelloThemeRuntime.darkThemeActive) darkAccentSoft else lightAccentSoft
    val DarkDanger: Color get() = if (HelloThemeRuntime.darkThemeActive) darkDanger else lightDanger

    val AuthBg: Color get() = Color(0xFF0B141A)
    val AuthPanel: Color get() = Color(0xFF111B21)
    val AuthInput: Color get() = Color(0xFF202C33)
    val AuthBorder: Color get() = Color(0xFF2F3B43)
    val AuthText: Color get() = Color(0xFFE9EDEF)
    val AuthMuted: Color get() = Color(0xFF8696A0)
    val AuthAccent: Color get() = Color(0xFF00A884)
    val AuthAccentStrong: Color get() = Color(0xFF008F6F)
    val AuthErrorPanel: Color get() = Color(0x4D7F1D1D)
    val AuthErrorText: Color get() = Color(0xFFFF6B6B)

    val MessageMine: Color get() = Color(0xFFD9FDD3)
    val MessageMineDark: Color get() = if (HelloThemeRuntime.darkThemeActive) Color(0xFF005C4B) else Color(0xFFD9FDD3)
    val MessageOther: Color get() = Color.White
    val MessageOtherDark: Color get() = if (HelloThemeRuntime.darkThemeActive) Color(0xFF202C33) else Color(0xFFFFFFFF)
    val ReadReceipt: Color get() = Color(0xFF53BDEB)
}
