package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent disk-cache entry for browser resources.
 */
@Entity(
    tableName = CacheEntity.TABLE_NAME,
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["expiresAt"]),
        Index(value = ["isValid"])
    ]
)
data class CacheEntity(
    @PrimaryKey
    val url: String,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val size: Long,
    val isValid: Boolean = true
) {
    /**
     * Returns a canonical copy with normalized URL, defensive data copy, and size fixed.
     */
    fun normalized(now: Long = System.currentTimeMillis()): CacheEntity {
        val cleanData = data.copyOf()
        val cleanTimestamp = timestamp.takeIf { it > 0L } ?: now
        val cleanSize = size.takeIf { it >= 0L } ?: cleanData.size.toLong()

        return copy(
            url = HistoryEntity.normalizeUrl(url),
            data = cleanData,
            timestamp = cleanTimestamp,
            expiresAt = expiresAt?.takeIf { it >= cleanTimestamp },
            size = cleanSize
        )
    }

    /**
     * Returns every validation error detected in this cache entry.
     */
    fun validationErrors(): List<CacheValidationError> {
        val candidate = normalized()
        val errors = mutableListOf<CacheValidationError>()

        if (!HistoryEntity.isValidUrl(candidate.url)) {
            errors += CacheValidationError.InvalidUrl(candidate.url)
        }
        if (candidate.data.isEmpty()) {
            errors += CacheValidationError.EmptyData
        }
        if (candidate.size < 0L) {
            errors += CacheValidationError.InvalidSize
        }
        if (candidate.size != candidate.data.size.toLong()) {
            errors += CacheValidationError.SizeMismatch(candidate.size, candidate.data.size.toLong())
        }
        if (candidate.timestamp <= 0L) {
            errors += CacheValidationError.InvalidTimestamp
        }
        if (candidate.expiresAt != null && candidate.expiresAt < candidate.timestamp) {
            errors += CacheValidationError.InvalidExpiry
        }

        return errors
    }

    /**
     * Returns true when this cache entry can be safely persisted.
     */
    fun isValidForStorage(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized cache entry or throws when validation fails.
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): CacheEntity {
        val candidate = normalized()
        val errors = candidate.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { error -> error.message }
        }
        return candidate
    }

    /**
     * Returns true when this entry is invalid or expired at [now].
     */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return !isValid || expiresAt?.let { it <= now } == true
    }

    /**
     * Returns true when the entry is usable and not expired.
     */
    fun canServe(now: Long = System.currentTimeMillis()): Boolean {
        return isValid && !isExpired(now) && data.isNotEmpty()
    }

    /**
     * Returns a copy marked invalid for cleanup.
     */
    fun invalidated(): CacheEntity = copy(isValid = false)

    /**
     * Returns a copy with replacement data and timestamps.
     */
    fun withData(
        newData: ByteArray,
        maxAgeMillis: Long? = null,
        now: Long = System.currentTimeMillis()
    ): CacheEntity {
        val cleanData = newData.copyOf()
        return copy(
            data = cleanData,
            timestamp = now,
            expiresAt = maxAgeMillis?.takeIf { it > 0L }?.let { now + it },
            size = cleanData.size.toLong(),
            isValid = true
        ).normalized(now)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheEntity) return false

        return url == other.url &&
            data.contentEquals(other.data) &&
            timestamp == other.timestamp &&
            expiresAt == other.expiresAt &&
            size == other.size &&
            isValid == other.isValid
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        result = 31 * result + size.hashCode()
        result = 31 * result + isValid.hashCode()
        return result
    }

    companion object {
        const val TABLE_NAME: String = "cache"

        /**
         * Creates a normalized, validated cache entry.
         */
        @Throws(IllegalArgumentException::class)
        fun create(
            url: String,
            data: ByteArray,
            maxAgeMillis: Long? = null,
            now: Long = System.currentTimeMillis()
        ): CacheEntity {
            val cleanData = data.copyOf()
            return CacheEntity(
                url = url,
                data = cleanData,
                timestamp = now,
                expiresAt = maxAgeMillis?.takeIf { it > 0L }?.let { now + it },
                size = cleanData.size.toLong(),
                isValid = true
            ).requireValid()
        }
    }
}

/**
 * Validation failures for [CacheEntity].
 */
sealed class CacheValidationError {
    abstract val message: String

    data class InvalidUrl(val url: String) : CacheValidationError() {
        override val message: String = "Invalid cache URL: $url."
    }

    data object EmptyData : CacheValidationError() {
        override val message: String = "Cache data cannot be empty."
    }

    data object InvalidSize : CacheValidationError() {
        override val message: String = "Cache size cannot be negative."
    }

    data class SizeMismatch(val declaredSize: Long, val actualSize: Long) : CacheValidationError() {
        override val message: String = "Cache size $declaredSize does not match actual size $actualSize."
    }

    data object InvalidTimestamp : CacheValidationError() {
        override val message: String = "Cache timestamp must be positive."
    }

    data object InvalidExpiry : CacheValidationError() {
        override val message: String = "Cache expiresAt cannot be earlier than timestamp."
    }
}
