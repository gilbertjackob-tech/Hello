package com.glassbox.hello.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

object HelloThemeRuntime {
    val darkMode = mutableStateOf(true)
    val darkThemeActive: Boolean get() = darkMode.value
}

object HelloColors {
    private val lightBg = Color(0xFFF4F7F6)
    private val lightBgStrong = Color(0xFFFFFFFF)
    private val lightPanel = Color(0xD1FFFFFF)
    private val lightPanelStrong = Color(0xF5FFFFFF)
    private val lightPanelMuted = Color(0xB8F4F7F6)
    private val lightBorder = Color(0x1410172A)
    private val lightBorderStrong = Color(0x2410172A)
    private val lightText = Color(0xFF17202A)
    private val lightTextMuted = Color(0xFF5D6878)
    private val lightAccent = Color(0xFF0E7C86)
    private val lightAccentStrong = Color(0xFF075E66)
    private val lightAccentSoft = Color(0x240E7C86)
    private val lightDanger = Color(0xFFC44D58)
    private val lightWarning = Color(0xFFB7652C)

    private val darkBg = Color(0xFF101820)
    private val darkBgStrong = Color(0xFF16232C)
    private val darkPanel = Color(0xD117232C)
    private val darkPanelStrong = Color(0xF21C2A34)
    private val darkPanelMuted = Color(0xB822313B)
    private val darkBorder = Color(0x14E2E8F0)
    private val darkBorderStrong = Color(0x24E2E8F0)
    private val darkText = Color(0xFFEEF5F2)
    private val darkTextMuted = Color(0xFFA4B2BE)
    private val darkAccent = Color(0xFF58C7B7)
    private val darkAccentStrong = Color(0xFF83DCCF)
    private val darkAccentSoft = Color(0x2B58C7B7)
    private val darkDanger = Color(0xFFFF7A8A)

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

    val AuthBg: Color get() = darkBg
    val AuthPanel: Color get() = Color(0xFF1C2A34)
    val AuthInput: Color get() = Color(0xFF22313B)
    val AuthBorder: Color get() = Color(0xFF344551)
    val AuthText: Color get() = darkText
    val AuthMuted: Color get() = darkTextMuted
    val AuthAccent: Color get() = darkAccent
    val AuthAccentStrong: Color get() = darkAccentStrong
    val AuthErrorPanel: Color get() = Color(0x4D7F1D1D)
    val AuthErrorText: Color get() = Color(0xFFFF6B6B)

    val MessageMine: Color get() = Color(0xFFCFEFEB)
    val MessageMineDark: Color get() = if (HelloThemeRuntime.darkThemeActive) Color(0xFF0E5E64) else Color(0xFFCFEFEB)
    val MessageOther: Color get() = Color.White
    val MessageOtherDark: Color get() = if (HelloThemeRuntime.darkThemeActive) Color(0xFF22313B) else Color(0xFFFFFFFF)
    val ReadReceipt: Color get() = Color(0xFF6BB8FF)
}
