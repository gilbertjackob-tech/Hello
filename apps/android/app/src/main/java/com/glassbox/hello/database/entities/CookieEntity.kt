package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Persistent HTTP cookie scoped to a browser profile.
 */
@Entity(
    tableName = CookieEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["profileId", "domain"]),
        Index(value = ["profileId", "domain", "path"]),
        Index(value = ["expiresAt"]),
        Index(value = ["timestamp"])
    ]
)
data class CookieEntity(
    @PrimaryKey
    val cookieKey: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long? = null,
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false,
    val profileId: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns a canonical copy with normalized domain, path, key, and timestamp.
     */
    fun normalized(now: Long = System.currentTimeMillis()): CookieEntity {
        val cleanDomain = normalizeDomain(domain)
        val cleanPath = normalizePath(path)
        val cleanName = parseName(cookieKey).orEmpty().trim()
        val cleanTimestamp = timestamp.takeIf { it > 0L } ?: now

        return copy(
            cookieKey = buildCookieKey(profileId, cleanDomain, cleanPath, cleanName),
            domain = cleanDomain,
            path = cleanPath,
            expiresAt = expiresAt?.takeIf { it >= cleanTimestamp },
            timestamp = cleanTimestamp
        )
    }

    /**
     * Returns every validation error detected in this cookie.
     */
    fun validationErrors(): List<CookieValidationError> {
        val candidate = normalized()
        val errors = mutableListOf<CookieValidationError>()
        val cookieName = parseName(candidate.cookieKey)

        if (candidate.cookieKey.isBlank() || cookieName.isNullOrBlank()) {
            errors += CookieValidationError.InvalidCookieKey
        }
        if (cookieName != null && cookieName.length > MAX_NAME_LENGTH) {
            errors += CookieValidationError.NameTooLong(MAX_NAME_LENGTH)
        }
        if (candidate.value.length > MAX_VALUE_LENGTH) {
            errors += CookieValidationError.ValueTooLong(MAX_VALUE_LENGTH)
        }
        if (!isValidDomain(candidate.domain)) {
            errors += CookieValidationError.InvalidDomain(candidate.domain)
        }
        if (!candidate.path.startsWith("/")) {
            errors += CookieValidationError.InvalidPath(candidate.path)
        }
        if (candidate.profileId <= 0) {
            errors += CookieValidationError.InvalidProfileId
        }
        if (candidate.timestamp <= 0L) {
            errors += CookieValidationError.InvalidTimestamp
        }
        if (candidate.expiresAt != null && candidate.expiresAt < candidate.timestamp) {
            errors += CookieValidationError.InvalidExpiry
        }

        return errors
    }

    /**
     * Returns true when this cookie can be safely persisted.
     */
    fun isValidForStorage(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized cookie or throws when validation fails.
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): CookieEntity {
        val candidate = normalized()
        val errors = candidate.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { error -> error.message }
        }
        return candidate
    }

    /**
     * Returns true when this cookie has expired at [now].
     */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return expiresAt?.let { it <= now } == true
    }

    /**
     * Returns true when this cookie is allowed for [host].
     */
    fun matchesDomain(host: String): Boolean {
        val cleanHost = normalizeDomain(host)
        return cleanHost == domain || cleanHost.endsWith(".$domain")
    }

    /**
     * Returns true when this cookie should be sent for [url].
     */
    fun matchesUrl(url: String, now: Long = System.currentTimeMillis()): Boolean {
        if (isExpired(now)) return false

        return try {
            val uri = URI(HistoryEntity.normalizeUrl(url))
            val host = uri.host ?: return false
            val requestPath = normalizePath(uri.path)
            val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
            matchesDomain(host) &&
                requestPath.startsWith(path) &&
                (!isSecure || scheme == "https")
        } catch (_: URISyntaxException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /**
     * Returns the name=value fragment used in a Cookie request header.
     */
    fun asHeaderPair(): String {
        val name = parseName(cookieKey).orEmpty()
        return "$name=$value"
    }

    /**
     * Returns a copy with updated value and timestamp.
     */
    fun withValue(newValue: String, now: Long = System.currentTimeMillis()): CookieEntity {
        return copy(value = newValue, timestamp = now).requireValid()
    }

    /**
     * Returns a copy with updated cookie security flags.
     */
    fun withFlags(secure: Boolean, httpOnly: Boolean): CookieEntity {
        return copy(isSecure = secure, isHttpOnly = httpOnly).requireValid()
    }

    companion object {
        const val TABLE_NAME: String = "cookies"
        const val MAX_NAME_LENGTH: Int = 256
        const val MAX_VALUE_LENGTH: Int = 4096
        private const val KEY_SEPARATOR: String = "|"

        /**
         * Creates a normalized, validated cookie.
         */
        @Throws(IllegalArgumentException::class)
        fun create(
            name: String,
            value: String,
            domain: String,
            path: String = "/",
            profileId: Int,
            expiresAt: Long? = null,
            isSecure: Boolean = false,
            isHttpOnly: Boolean = false,
            now: Long = System.currentTimeMillis()
        ): CookieEntity {
            val cleanDomain = normalizeDomain(domain)
            val cleanPath = normalizePath(path)
            return CookieEntity(
                cookieKey = buildCookieKey(profileId, cleanDomain, cleanPath, name.trim()),
                value = value,
                domain = cleanDomain,
                path = cleanPath,
                expiresAt = expiresAt,
                isSecure = isSecure,
                isHttpOnly = isHttpOnly,
                profileId = profileId,
                timestamp = now
            ).requireValid()
        }

        /**
         * Builds a stable primary key for a cookie and profile.
         */
        fun buildCookieKey(
            profileId: Int,
            domain: String,
            path: String,
            name: String
        ): String {
            return listOf(
                profileId.toString(),
                normalizeDomain(domain),
                normalizePath(path),
                name.trim()
            ).joinToString(KEY_SEPARATOR)
        }

        /**
         * Extracts the cookie name from a persisted cookie key.
         */
        fun parseName(cookieKey: String): String? {
            val trimmed = cookieKey.trim()
            if (trimmed.isBlank()) return null
            return trimmed.substringAfterLast(KEY_SEPARATOR).takeIf { it.isNotBlank() }
        }

        /**
         * Extracts and normalizes the host from a URL.
         */
        fun domainFromUrl(url: String): String? {
            return try {
                val uri = URI(HistoryEntity.normalizeUrl(url))
                uri.host?.let { normalizeDomain(it) }
            } catch (_: URISyntaxException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /**
         * Normalizes cookie domains for consistent lookup.
         */
        fun normalizeDomain(domain: String): String {
            val trimmed = domain.trim().trimStart('.').lowercase(Locale.US)
            return try {
                IDN.toASCII(trimmed).lowercase(Locale.US)
            } catch (_: IllegalArgumentException) {
                trimmed
            }
        }

        /**
         * Normalizes cookie paths for prefix matching.
         */
        fun normalizePath(path: String?): String {
            val cleanPath = path?.trim().takeUnless { it.isNullOrBlank() } ?: "/"
            return if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
        }

        /**
         * Returns true when [domain] is a valid cookie domain.
         */
        fun isValidDomain(domain: String): Boolean {
            val cleanDomain = normalizeDomain(domain)
            if (cleanDomain.isBlank() || cleanDomain.length > 253) return false
            if (cleanDomain == "localhost") return true

            val labels = cleanDomain.split(".")
            return labels.size > 1 && labels.all { label ->
                label.isNotBlank() &&
                    label.length <= 63 &&
                    label.first().isLetterOrDigit() &&
                    label.last().isLetterOrDigit() &&
                    label.all { character -> character.isLetterOrDigit() || character == '-' }
            }
        }
    }
}

/**
 * Validation failures for [CookieEntity].
 */
sealed class CookieValidationError {
    abstract val message: String

    data object InvalidCookieKey : CookieValidationError() {
        override val message: String = "Cookie key must include a non-blank name."
    }

    data class NameTooLong(val maxLength: Int) : CookieValidationError() {
        override val message: String = "Cookie name cannot exceed $maxLength characters."
    }

    data class ValueTooLong(val maxLength: Int) : CookieValidationError() {
        override val message: String = "Cookie value cannot exceed $maxLength characters."
    }

    data class InvalidDomain(val domain: String) : CookieValidationError() {
        override val message: String = "Invalid cookie domain: $domain."
    }

    data class InvalidPath(val path: String) : CookieValidationError() {
        override val message: String = "Invalid cookie path: $path."
    }

    data object InvalidProfileId : CookieValidationError() {
        override val message: String = "Cookie profileId must be positive."
    }

    data object InvalidTimestamp : CookieValidationError() {
        override val message: String = "Cookie timestamp must be positive."
    }

    data object InvalidExpiry : CookieValidationError() {
        override val message: String = "Cookie expiresAt cannot be earlier than timestamp."
    }
}
