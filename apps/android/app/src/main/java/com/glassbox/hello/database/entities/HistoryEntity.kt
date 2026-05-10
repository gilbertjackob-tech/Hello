package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Persistent browser history entry scoped to a browser profile.
 */
@Entity(
    tableName = HistoryEntity.TABLE_NAME,
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
        Index(value = ["profileId", "url"], unique = true),
        Index(value = ["profileId", "timestamp"]),
        Index(value = ["profileId", "lastVisited"]),
        Index(value = ["profileId", "isBookmarked"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String? = null,
    val profileId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val faviconUrl: String? = null,
    val visitCount: Int = 1,
    val lastVisited: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
) {
    /**
     * Returns a canonical copy with normalized URL and valid timestamp ordering.
     */
    fun normalized(now: Long = System.currentTimeMillis()): HistoryEntity {
        val cleanTimestamp = timestamp.takeIf { it > 0L } ?: now
        val cleanLastVisited = lastVisited.takeIf { it >= cleanTimestamp } ?: cleanTimestamp

        return copy(
            url = normalizeUrl(url),
            title = title?.trim()?.takeIf { it.isNotBlank() },
            faviconUrl = faviconUrl?.trim()?.takeIf { it.isNotBlank() },
            timestamp = cleanTimestamp,
            visitCount = visitCount.coerceAtLeast(MIN_VISIT_COUNT),
            lastVisited = cleanLastVisited
        )
    }

    /**
     * Returns every validation error detected in this history entry.
     */
    fun validationErrors(): List<HistoryValidationError> {
        val candidate = normalized()
        val errors = mutableListOf<HistoryValidationError>()

        if (candidate.id < 0) {
            errors += HistoryValidationError.InvalidId
        }
        if (candidate.profileId <= 0) {
            errors += HistoryValidationError.InvalidProfileId
        }
        if (!isValidUrl(candidate.url)) {
            errors += HistoryValidationError.InvalidUrl(candidate.url)
        }
        if (candidate.title != null && candidate.title.length > MAX_TITLE_LENGTH) {
            errors += HistoryValidationError.TitleTooLong(MAX_TITLE_LENGTH)
        }
        if (candidate.faviconUrl != null && !isValidUrl(candidate.faviconUrl)) {
            errors += HistoryValidationError.InvalidFaviconUrl(candidate.faviconUrl)
        }
        if (candidate.timestamp <= 0L) {
            errors += HistoryValidationError.InvalidTimestamp
        }
        if (candidate.visitCount < MIN_VISIT_COUNT) {
            errors += HistoryValidationError.InvalidVisitCount
        }
        if (candidate.lastVisited < candidate.timestamp) {
            errors += HistoryValidationError.InvalidLastVisited
        }

        return errors
    }

    /**
     * Returns true when this entry can be safely persisted.
     */
    fun isValid(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized entry or throws when validation fails.
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): HistoryEntity {
        val candidate = normalized()
        val errors = candidate.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { error -> error.message }
        }
        return candidate
    }

    /**
     * Returns a copy representing another visit to the same URL.
     */
    fun incrementVisit(
        pageTitle: String? = title,
        pageFaviconUrl: String? = faviconUrl,
        now: Long = System.currentTimeMillis()
    ): HistoryEntity {
        return copy(
            title = pageTitle ?: title,
            faviconUrl = pageFaviconUrl ?: faviconUrl,
            visitCount = visitCount + 1,
            lastVisited = now
        ).normalized(now)
    }

    /**
     * Returns a copy with bookmark state changed.
     */
    fun withBookmarkState(bookmarked: Boolean, now: Long = System.currentTimeMillis()): HistoryEntity {
        return copy(isBookmarked = bookmarked, lastVisited = now).normalized(now)
    }

    companion object {
        const val TABLE_NAME: String = "browsing_history"
        const val MAX_TITLE_LENGTH: Int = 512
        const val MIN_VISIT_COUNT: Int = 1

        /**
         * Creates a normalized, validated history entry.
         */
        @Throws(IllegalArgumentException::class)
        fun create(
            url: String,
            title: String? = null,
            profileId: Int,
            faviconUrl: String? = null,
            isBookmarked: Boolean = false,
            now: Long = System.currentTimeMillis()
        ): HistoryEntity {
            return HistoryEntity(
                url = url,
                title = title,
                profileId = profileId,
                timestamp = now,
                faviconUrl = faviconUrl,
                visitCount = MIN_VISIT_COUNT,
                lastVisited = now,
                isBookmarked = isBookmarked
            ).requireValid()
        }

        /**
         * Normalizes a user-entered or browser-provided URL for storage.
         */
        fun normalizeUrl(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return trimmed
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"

            return try {
                val uri = URI(withScheme)
                val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
                val host = uri.host?.lowercase(Locale.US).orEmpty()
                if (scheme.isBlank() || host.isBlank()) return withScheme
                URI(
                    scheme,
                    uri.userInfo,
                    host,
                    uri.port,
                    uri.path?.ifBlank { "/" },
                    uri.query,
                    uri.fragment
                ).normalize().toString()
            } catch (_: URISyntaxException) {
                withScheme
            } catch (_: IllegalArgumentException) {
                withScheme
            }
        }

        /**
         * Returns true when the URL can be stored in browsing history.
         */
        fun isValidUrl(value: String): Boolean {
            return try {
                val uri = URI(normalizeUrl(value))
                val scheme = uri.scheme?.lowercase(Locale.US)
                val host = uri.host
                scheme in setOf("http", "https") && !host.isNullOrBlank()
            } catch (_: URISyntaxException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
}

/**
 * Validation failures for [HistoryEntity].
 */
sealed class HistoryValidationError {
    abstract val message: String

    data object InvalidId : HistoryValidationError() {
        override val message: String = "History id cannot be negative."
    }

    data object InvalidProfileId : HistoryValidationError() {
        override val message: String = "History profileId must be positive."
    }

    data class InvalidUrl(val url: String) : HistoryValidationError() {
        override val message: String = "Invalid history URL: $url."
    }

    data class TitleTooLong(val maxLength: Int) : HistoryValidationError() {
        override val message: String = "History title cannot exceed $maxLength characters."
    }

    data class InvalidFaviconUrl(val faviconUrl: String) : HistoryValidationError() {
        override val message: String = "Invalid favicon URL: $faviconUrl."
    }

    data object InvalidTimestamp : HistoryValidationError() {
        override val message: String = "History timestamp must be positive."
    }

    data object InvalidVisitCount : HistoryValidationError() {
        override val message: String = "History visitCount must be at least 1."
    }

    data object InvalidLastVisited : HistoryValidationError() {
        override val message: String = "History lastVisited cannot be earlier than timestamp."
    }
}
