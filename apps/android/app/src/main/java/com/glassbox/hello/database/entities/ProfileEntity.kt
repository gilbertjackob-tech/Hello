package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Room entity for an isolated browser profile.
 *
 * A profile owns browser-scoped data such as history, cookies, cache entries,
 * downloads, OAuth metadata, and the active WebView account context.
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
    val avatarUrl: String? = null,
    val oauthToken: String? = null,
    val refreshToken: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns this profile with canonical whitespace, type casing, email casing,
     * and timestamp ordering.
     */
    fun normalized(now: Long = System.currentTimeMillis()): ProfileEntity {
        val normalizedCreatedAt = createdAt.takeIf { it > 0L } ?: now
        val normalizedUpdatedAt = updatedAt
            .takeIf { it >= normalizedCreatedAt }
            ?: normalizedCreatedAt

        return copy(
            name = name.trim().replace(Regex("\\s+"), " "),
            type = normalizeType(type),
            email = email?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() },
            oauthToken = oauthToken?.trim()?.takeIf { it.isNotBlank() },
            refreshToken = refreshToken?.trim()?.takeIf { it.isNotBlank() },
            createdAt = normalizedCreatedAt,
            updatedAt = normalizedUpdatedAt
        )
    }

    /**
     * Validates the profile and returns all detected problems.
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
        if (candidate.avatarUrl != null && !isValidHttpUrl(candidate.avatarUrl)) {
            errors += ProfileValidationError.InvalidAvatarUrl(candidate.avatarUrl)
        }
        if (candidate.createdAt <= 0L) {
            errors += ProfileValidationError.InvalidCreatedAt
        }
        if (candidate.updatedAt < candidate.createdAt) {
            errors += ProfileValidationError.InvalidUpdatedAt
        }

        return errors
    }

    /**
     * Returns true when this profile is normalized and valid for persistence.
     */
    fun isValid(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized profile or throws when any validation rule fails.
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): ProfileEntity {
        val normalized = normalized()
        val errors = normalized.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { it.message }
        }
        return normalized
    }

    /**
     * Returns a copy with the active flag changed and `updatedAt` advanced.
     */
    fun withActiveState(active: Boolean, now: Long = System.currentTimeMillis()): ProfileEntity {
        return copy(isActive = active, updatedAt = now).normalized(now)
    }

    /**
     * Returns a copy with a new display name and `updatedAt` advanced.
     */
    fun renamed(newName: String, now: Long = System.currentTimeMillis()): ProfileEntity {
        return copy(name = newName, updatedAt = now).normalized(now)
    }

    /**
     * Returns a copy with refreshed OAuth tokens and `updatedAt` advanced.
     */
    fun withOAuthTokens(
        accessToken: String?,
        newRefreshToken: String?,
        now: Long = System.currentTimeMillis()
    ): ProfileEntity {
        return copy(
            oauthToken = accessToken,
            refreshToken = newRefreshToken ?: refreshToken,
            updatedAt = now
        ).normalized(now)
    }

    /**
     * Returns a copy with updated profile metadata and `updatedAt` advanced.
     */
    fun withMetadata(
        newEmail: String?,
        newAvatarUrl: String?,
        now: Long = System.currentTimeMillis()
    ): ProfileEntity {
        return copy(
            email = newEmail,
            avatarUrl = newAvatarUrl,
            updatedAt = now
        ).normalized(now)
    }

    companion object {
        const val TABLE_NAME: String = "profiles"
        const val TYPE_GMAIL: String = "gmail"
        const val TYPE_OUTLOOK: String = "outlook"
        const val TYPE_ICLOUD: String = "icloud"
        const val TYPE_CUSTOM: String = "custom"
        const val MAX_NAME_LENGTH: Int = 80

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
            avatarUrl: String? = null,
            oauthToken: String? = null,
            refreshToken: String? = null,
            isActive: Boolean = false,
            now: Long = System.currentTimeMillis()
        ): ProfileEntity {
            return ProfileEntity(
                name = name,
                type = type,
                email = email,
                avatarUrl = avatarUrl,
                oauthToken = oauthToken,
                refreshToken = refreshToken,
                isActive = isActive,
                createdAt = now,
                updatedAt = now
            ).requireValid()
        }

        /**
         * Normalizes provider type values used by profile APIs and UI.
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

        /**
         * Returns true when a value is an HTTP or HTTPS URL.
         */
        fun isValidHttpUrl(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
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

    data class InvalidAvatarUrl(val avatarUrl: String) : ProfileValidationError() {
        override val message: String = "Avatar URL must start with http:// or https://."
    }

    data object InvalidCreatedAt : ProfileValidationError() {
        override val message: String = "Profile createdAt must be positive."
    }

    data object InvalidUpdatedAt : ProfileValidationError() {
        override val message: String = "Profile updatedAt cannot be earlier than createdAt."
    }
}
