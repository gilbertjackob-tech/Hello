package com.glassbox.hello.utils

import android.net.Uri
import android.webkit.WebSettings
import com.glassbox.hello.BuildConfig
import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Utility functions for browser URL handling and user-agent management.
 */
object BrowserUtils {
    private val searchableSchemes: Set<String> = setOf("http", "https", "file", "data", "about")

    /**
     * Normalizes user-entered text into a URL or search URL.
     */
    fun normalizeUrl(input: String, searchBaseUrl: String = Constants.DEFAULT_SEARCH_URL): String {
        val value = input.trim()
        if (value.isBlank()) return Constants.DEFAULT_HOME_URL
        if (value.equals("about:blank", ignoreCase = true)) return "about:blank"
        if (hasSupportedScheme(value)) return value

        val looksLikeHost = value.contains(".") && !value.any(Char::isWhitespace)
        return if (looksLikeHost) {
            "https://${value.trimStart('/')}"
        } else {
            searchBaseUrl + Uri.encode(value)
        }
    }

    /**
     * Returns true when a URL is valid for browser navigation.
     */
    fun isValidUrl(value: String): Boolean {
        return try {
            val normalized = normalizeUrl(value)
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase(Locale.US)
            scheme in searchableSchemes && (scheme in setOf("file", "data", "about") || !uri.host.isNullOrBlank())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts a normalized host name from a URL.
     */
    fun extractDomain(value: String): String? {
        return try {
            val uri = URI(normalizeUrl(value))
            uri.host
                ?.trim()
                ?.trimEnd('.')
                ?.lowercase(Locale.US)
                ?.let { host -> IDN.toUnicode(host) }
                ?.takeIf { host -> host.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the origin for a URL, or null for opaque schemes.
     */
    fun extractOrigin(value: String): String? {
        return try {
            val uri = URI(normalizeUrl(value))
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            val host = uri.host?.lowercase(Locale.US) ?: return null
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a stable app-specific user agent suffix.
     */
    fun appUserAgentSuffix(): String {
        return "GlassBoxHello/${BuildConfig.VERSION_NAME}"
    }

    /**
     * Builds a WebView user agent with the GlassBox suffix.
     */
    fun buildUserAgent(defaultUserAgent: String): String {
        val suffix = appUserAgentSuffix()
        return if (defaultUserAgent.contains(suffix)) defaultUserAgent else "$defaultUserAgent $suffix"
    }

    /**
     * Applies safe default browser settings.
     */
    fun configureWebSettings(settings: WebSettings, defaultUserAgent: String) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = buildUserAgent(defaultUserAgent)
    }

    private fun hasSupportedScheme(value: String): Boolean {
        val scheme = value.substringBefore(":", missingDelimiterValue = "")
            .lowercase(Locale.US)
        return scheme.isNotBlank() && scheme in searchableSchemes
    }
}
