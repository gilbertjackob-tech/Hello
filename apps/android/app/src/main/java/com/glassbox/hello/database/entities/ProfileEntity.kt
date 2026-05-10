package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Persistent browser profile record.
 *
 * Each profile owns isolated browser data such as history, downloads, cache,
 * cookies, OAuth tokens, sync state, and WebView user-agent preferences.
 */
@Entity(
    tableName = ProfileEntity.TABLE_NAME,
    indices = [
        Index(value = ["email"]),
        Index(value = ["type"]),
        Index(value = ["isActive"]),
        Index(value = ["updatedAt"])
    ]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
    val userAgent: String? = null,
    val isActive: Boolean = false,
    val isSyncEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncTime: Long? = null
) {
    /**
     * Returns a canonical copy suitable for persistence.
     */
    fun normalized(now: Long = System.currentTimeMillis()): ProfileEntity {
        val cleanCreatedAt = createdAt.takeIf { it > 0L } ?: now
        val cleanUpdatedAt = updatedAt.takeIf { it >= cleanCreatedAt } ?: cleanCreatedAt

        return copy(
            name = name.trim().replace(Regex("\\s+"), " "),
            type = normalizeType(type),
            email = email?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() },
            accessToken = accessToken?.trim()?.takeIf { it.isNotBlank() },
            refreshToken = refreshToken?.trim()?.takeIf { it.isNotBlank() },
            tokenExpiry = tokenExpiry?.takeIf { it > 0L },
            userAgent = userAgent?.trim()?.takeIf { it.isNotBlank() },
            createdAt = cleanCreatedAt,
            updatedAt = cleanUpdatedAt,
            lastSyncTime = lastSyncTime?.takeIf { it > 0L }
        )
    }

    /**
     * Returns every validation error detected in this profile.
     */
    fun validationErrors(): List<ProfileValidationError> {
        val candidate = normalized()
        val errors = mutableListOf<ProfileValidationError>()

        if (candidate.id < 0) {
            errors += ProfileValidationError.InvalidId
        }
        if (candidate.name.isBlank()) {
            errors += ProfileValidationError.BlankName
        }
        if (candidate.name.length > MAX_NAME_LENGTH) {
            errors += ProfileValidationError.NameTooLong(MAX_NAME_LENGTH)
        }
        if (candidate.type !in SUPPORTED_TYPES) {
            errors += ProfileValidationError.UnsupportedType(candidate.type)
        }
        if (candidate.email != null && !EMAIL_PATTERN.matches(candidate.email)) {
            errors += ProfileValidationError.InvalidEmail(candidate.email)
        }
        if (candidate.userAgent != null && candidate.userAgent.length > MAX_USER_AGENT_LENGTH) {
            errors += ProfileValidationError.UserAgentTooLong(MAX_USER_AGENT_LENGTH)
        }
        if (candidate.tokenExpiry != null && candidate.tokenExpiry <= 0L) {
            errors += ProfileValidationError.InvalidTokenExpiry
        }
        if (candidate.createdAt <= 0L) {
            errors += ProfileValidationError.InvalidCreatedAt
        }
        if (candidate.updatedAt < candidate.createdAt) {
            errors += ProfileValidationError.InvalidUpdatedAt
        }
        if (candidate.lastSyncTime != null && candidate.lastSyncTime < candidate.createdAt) {
            errors += ProfileValidationError.InvalidLastSyncTime
        }

        return errors
    }

    /**
     * Returns true when this profile can be safely inserted or updated.
     */
    fun isValid(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized profile or throws an [IllegalArgumentException].
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): ProfileEntity {
        val candidate = normalized()
        val errors = candidate.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { error -> error.message }
        }
        return candidate
    }

    /**
     * Returns a copy with updated active state and timestamp.
     */
    fun withActiveState(active: Boolean, now: Long = System.currentTimeMillis()): ProfileEntity {
        return copy(isActive = active, updatedAt = now).normalized(now)
    }

    /**
     * Returns a copy with updated sync state and timestamp.
     */
    fun withSyncEnabled(enabled: Boolean, now: Long = System.currentTimeMillis()): ProfileEntity {
        return copy(isSyncEnabled = enabled, updatedAt = now).normalized(now)
    }

    /**
     * Returns a copy with updated OAuth tokens and expiry.
     */
    fun withOAuthTokens(
        newAccessToken: String?,
        newRefreshToken: String?,
        newTokenExpiry: Long?,
        now: Long = System.currentTimeMillis()
    ): ProfileEntity {
        return copy(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken ?: refreshToken,
            tokenExpiry = newTokenExpiry,
            updatedAt = now
        ).normalized(now)
    }

    /**
     * Returns true when the access token is missing or expired.
     */
    fun isTokenExpired(now: Long = System.currentTimeMillis(), bufferMillis: Long = TOKEN_EXPIRY_BUFFER_MS): Boolean {
        val expiry = tokenExpiry ?: return true
        return now + bufferMillis >= expiry
    }

    /**
     * Returns a copy with a refreshed sync timestamp.
     */
    fun markSynced(now: Long = System.currentTimeMillis()): ProfileEntity {
        return copy(lastSyncTime = now, updatedAt = now).normalized(now)
    }

    companion object {
        const val TABLE_NAME: String = "profiles"
        const val TYPE_GMAIL: String = "gmail"
        const val TYPE_OUTLOOK: String = "outlook"
        const val TYPE_ICLOUD: String = "icloud"
        const val TYPE_CUSTOM: String = "custom"
        const val MAX_NAME_LENGTH: Int = 80
        const val MAX_USER_AGENT_LENGTH: Int = 512
        const val TOKEN_EXPIRY_BUFFER_MS: Long = 5 * 60 * 1000L

        val SUPPORTED_TYPES: Set<String> = setOf(
            TYPE_GMAIL,
            TYPE_OUTLOOK,
            TYPE_ICLOUD,
            TYPE_CUSTOM
        )

        private val EMAIL_PATTERN: Regex = Regex(
            pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        /**
         * Creates a normalized, validated profile for insertion.
         */
        @Throws(IllegalArgumentException::class)
        fun create(
            name: String,
            type: String = TYPE_CUSTOM,
            email: String? = null,
            accessToken: String? = null,
            refreshToken: String? = null,
            tokenExpiry: Long? = null,
            userAgent: String? = null,
            isActive: Boolean = false,
            isSyncEnabled: Boolean = true,
            now: Long = System.currentTimeMillis()
        ): ProfileEntity {
            return ProfileEntity(
                name = name,
                type = type,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiry = tokenExpiry,
                userAgent = userAgent,
                isActive = isActive,
                isSyncEnabled = isSyncEnabled,
                createdAt = now,
                updatedAt = now
            ).requireValid()
        }

        /**
         * Normalizes provider aliases into supported profile types.
         */
        fun normalizeType(type: String): String {
            val normalized = type.trim().lowercase(Locale.US)
            return when (normalized) {
                TYPE_GMAIL, "google" -> TYPE_GMAIL
                TYPE_OUTLOOK, "microsoft", "office365", "office_365" -> TYPE_OUTLOOK
                TYPE_ICLOUD, "apple" -> TYPE_ICLOUD
                TYPE_CUSTOM, "" -> TYPE_CUSTOM
                else -> normalized
            }
        }
    }
}

/**
 * Validation failures for [ProfileEntity].
 */
sealed class ProfileValidationError {
    abstract val message: String

    data object InvalidId : ProfileValidationError() {
        override val message: String = "Profile id cannot be negative."
    }

    data object BlankName : ProfileValidationError() {
        override val message: String = "Profile name cannot be blank."
    }

    data class NameTooLong(val maxLength: Int) : ProfileValidationError() {
        override val message: String = "Profile name cannot exceed $maxLength characters."
    }

    data class UnsupportedType(val type: String) : ProfileValidationError() {
        override val message: String = "Unsupported profile type: $type."
    }

    data class InvalidEmail(val email: String) : ProfileValidationError() {
        override val message: String = "Invalid profile email: $email."
    }

    data class UserAgentTooLong(val maxLength: Int) : ProfileValidationError() {
        override val message: String = "Profile user agent cannot exceed $maxLength characters."
    }

    data object InvalidTokenExpiry : ProfileValidationError() {
        override val message: String = "Token expiry must be positive when present."
    }

    data object InvalidCreatedAt : ProfileValidationError() {
        override val message: String = "Profile createdAt must be positive."
    }

    data object InvalidUpdatedAt : ProfileValidationError() {
        override val message: String = "Profile updatedAt cannot be earlier than createdAt."
    }

    data object InvalidLastSyncTime : ProfileValidationError() {
        override val message: String = "Profile lastSyncTime cannot be earlier than createdAt."
    }
}
