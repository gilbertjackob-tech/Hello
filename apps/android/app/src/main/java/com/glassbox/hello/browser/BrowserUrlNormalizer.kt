package com.glassbox.hello.browser

import android.net.Uri
import java.util.Locale

private val directAliases = mapOf(
    "google" to "https://www.google.com/",
    "youtube" to "https://www.youtube.com/",
    "yt" to "https://www.youtube.com/",
    "mail" to "https://mail.google.com/",
    "gmail" to "https://mail.google.com/",
    "facebook" to "https://www.facebook.com/",
    "fb" to "https://www.facebook.com/",
    "whatsapp" to "https://web.whatsapp.com/",
    "web.whatsapp" to "https://web.whatsapp.com/"
)

const val DEFAULT_BROWSER_HOME_URL: String = "https://www.google.com/"
const val DEFAULT_BROWSER_PROFILE_ID: String = "signin-google"
const val BROWSER_PROVIDER_LOCAL: String = "local"
const val BROWSER_PROVIDER_GOOGLE: String = "google"
const val BROWSER_PROVIDER_OUTLOOK: String = "outlook"
const val GOOGLE_SIGN_IN_URL: String = "https://accounts.google.com/signin/v2/identifier?continue=https%3A%2F%2Fwww.google.com%2F"
const val OUTLOOK_SIGN_IN_URL: String = "https://login.live.com/"

fun normalizeBrowserUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return DEFAULT_BROWSER_HOME_URL

    val alias = directAliases[value.lowercase(Locale.ROOT)]
    if (alias != null) return alias

    if (
        value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("file://", ignoreCase = true) ||
        value.startsWith("data:", ignoreCase = true)
    ) {
        return value
    }

    if (value == "about:blank") {
        return DEFAULT_BROWSER_HOME_URL
    }

    val hasSpaces = value.contains(' ')
    return if (value.contains('.') && !hasSpaces) {
        "https://${value.trimStart('/')}"
    } else {
        "https://www.google.com/search?q=${Uri.encode(value)}"
    }
}

fun deriveProfileLabelFromEmail(email: String): String {
    val localPart = email.substringBefore('@').trim()
    if (localPart.isBlank()) return email
    return localPart.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
    }
}

fun isEmailAddress(value: String): Boolean {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return EMAIL_PATTERN.matches(normalized)
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
