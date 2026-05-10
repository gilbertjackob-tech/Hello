package com.glassbox.hello.security

import android.net.Uri
import com.glassbox.hello.BuildConfig
import com.glassbox.hello.client.CertificatePin
import com.glassbox.hello.client.CertificatePinningConfig
import java.net.IDN
import java.net.URI
import java.util.Locale
import okhttp3.CertificatePinner

/**
 * Security helpers for URL, SSL pinning, and HTTP header validation.
 */
object SecurityUtils {
    private val allowedHeaderName = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")
    private val blockedHeaders: Set<String> = setOf(
        "authorization",
        "cookie",
        "set-cookie",
        "proxy-authorization"
    )

    /**
     * Returns true when a URL is syntactically valid and safe to load.
     */
    fun validateUrl(url: String, allowLocalDebugHosts: Boolean = BuildConfig.DEBUG): Boolean {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.US)
            val host = uri.host
            if (scheme !in setOf("https", "http")) return false
            if (host.isNullOrBlank()) return false
            if (scheme == "http" && !allowLocalDebugHosts) return false
            if (scheme == "http" && !isLocalHost(host) && !allowLocalDebugHosts) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true when a URL should be considered secure for production API calls.
     */
    fun isHttpsUrl(url: String): Boolean {
        return runCatching { Uri.parse(url).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)
    }

    /**
     * Returns a normalized host name suitable for certificate pinning.
     */
    fun normalizeHost(host: String): String {
        return IDN.toASCII(host.trim().trimEnd('.').lowercase(Locale.US))
    }

    /**
     * Creates an OkHttp certificate pinner.
     */
    fun buildCertificatePinner(pinningConfig: CertificatePinningConfig): CertificatePinner {
        return pinningConfig.toCertificatePinner()
    }

    /**
     * Creates a certificate pinning config for a host.
     */
    fun certificatePinningConfig(hostname: String, sha256Pins: List<String>): CertificatePinningConfig {
        require(hostname.isNotBlank()) { "Hostname cannot be blank." }
        require(sha256Pins.all { pin -> pin.startsWith("sha256/") }) {
            "Certificate pins must use sha256/ prefixes."
        }
        return CertificatePinningConfig(
            pins = listOf(CertificatePin(normalizeHost(hostname), sha256Pins.distinct()))
        )
    }

    /**
     * Returns true when a header can be accepted from user-controlled input.
     */
    fun isAllowedHeader(name: String, value: String): Boolean {
        val cleanName = name.trim()
        if (!allowedHeaderName.matches(cleanName)) return false
        if (cleanName.lowercase(Locale.US) in blockedHeaders) return false
        return !value.contains('\r') && !value.contains('\n')
    }

    /**
     * Filters headers down to valid non-sensitive values.
     */
    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers
            .mapKeys { (name, _) -> name.trim() }
            .filter { (name, value) -> isAllowedHeader(name, value) }
            .mapValues { (_, value) -> value.trim() }
    }

    private fun isLocalHost(host: String): Boolean {
        val cleanHost = host.lowercase(Locale.US)
        return cleanHost == "localhost" ||
            cleanHost == "127.0.0.1" ||
            cleanHost == "::1" ||
            cleanHost.endsWith(".local")
    }
}
