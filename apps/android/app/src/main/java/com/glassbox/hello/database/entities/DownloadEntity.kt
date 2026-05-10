package com.glassbox.hello.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent browser download record scoped to a browser profile.
 */
@Entity(
    tableName = DownloadEntity.TABLE_NAME,
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
        Index(value = ["profileId", "status"]),
        Index(value = ["profileId", "startTime"]),
        Index(value = ["url"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedSize: Long = 0L,
    val status: String,
    val progress: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val mimeType: String? = null,
    val profileId: Int,
    val isAutoDownload: Boolean = false
) {
    /**
     * Returns a canonical copy with status, progress, and timestamps normalized.
     */
    fun normalized(now: Long = System.currentTimeMillis()): DownloadEntity {
        val cleanFileSize = fileSize.coerceAtLeast(UNKNOWN_SIZE)
        val cleanDownloadedSize = downloadedSize.coerceAtLeast(0L).let { size ->
            if (cleanFileSize > UNKNOWN_SIZE) size.coerceAtMost(cleanFileSize) else size
        }
        val cleanStatus = normalizeStatus(status)
        val cleanStartTime = startTime.takeIf { it > 0L } ?: now
        val cleanEndTime = endTime?.takeIf { it >= cleanStartTime }
        val calculatedProgress = calculateProgress(cleanDownloadedSize, cleanFileSize)

        return copy(
            url = HistoryEntity.normalizeUrl(url),
            fileName = fileName.trim(),
            filePath = filePath.trim(),
            fileSize = cleanFileSize,
            downloadedSize = cleanDownloadedSize,
            status = cleanStatus,
            progress = calculatedProgress,
            startTime = cleanStartTime,
            endTime = if (cleanStatus in TERMINAL_STATUSES) cleanEndTime ?: now else null,
            mimeType = mimeType?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Returns every validation error detected in this download entry.
     */
    fun validationErrors(): List<DownloadValidationError> {
        val candidate = normalized()
        val errors = mutableListOf<DownloadValidationError>()

        if (candidate.id < 0) {
            errors += DownloadValidationError.InvalidId
        }
        if (candidate.profileId <= 0) {
            errors += DownloadValidationError.InvalidProfileId
        }
        if (!HistoryEntity.isValidUrl(candidate.url)) {
            errors += DownloadValidationError.InvalidUrl(candidate.url)
        }
        if (candidate.fileName.isBlank()) {
            errors += DownloadValidationError.BlankFileName
        }
        if (candidate.filePath.isBlank()) {
            errors += DownloadValidationError.BlankFilePath
        }
        if (candidate.fileSize < UNKNOWN_SIZE) {
            errors += DownloadValidationError.InvalidFileSize
        }
        if (candidate.downloadedSize < 0L) {
            errors += DownloadValidationError.InvalidDownloadedSize
        }
        if (candidate.fileSize > UNKNOWN_SIZE && candidate.downloadedSize > candidate.fileSize) {
            errors += DownloadValidationError.DownloadedSizeExceedsFileSize
        }
        if (candidate.status !in SUPPORTED_STATUSES) {
            errors += DownloadValidationError.UnsupportedStatus(candidate.status)
        }
        if (candidate.progress !in MIN_PROGRESS..MAX_PROGRESS) {
            errors += DownloadValidationError.InvalidProgress
        }
        if (candidate.startTime <= 0L) {
            errors += DownloadValidationError.InvalidStartTime
        }
        if (candidate.endTime != null && candidate.endTime < candidate.startTime) {
            errors += DownloadValidationError.InvalidEndTime
        }

        return errors
    }

    /**
     * Returns true when this download can be safely persisted.
     */
    fun isValid(): Boolean = validationErrors().isEmpty()

    /**
     * Returns a normalized download or throws when validation fails.
     */
    @Throws(IllegalArgumentException::class)
    fun requireValid(): DownloadEntity {
        val candidate = normalized()
        val errors = candidate.validationErrors()
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { error -> error.message }
        }
        return candidate
    }

    /**
     * Returns true when the download is actively running or queued.
     */
    fun isActive(): Boolean = status in ACTIVE_STATUSES

    /**
     * Returns true when the download reached a terminal state.
     */
    fun isTerminal(): Boolean = status in TERMINAL_STATUSES

    /**
     * Returns a copy with updated transferred bytes and calculated progress.
     */
    fun withProgress(bytesDownloaded: Long, now: Long = System.currentTimeMillis()): DownloadEntity {
        return copy(
            downloadedSize = bytesDownloaded,
            status = if (status == STATUS_PENDING) STATUS_DOWNLOADING else status,
            startTime = startTime.takeIf { it > 0L } ?: now
        ).normalized(now)
    }

    /**
     * Returns a copy with updated download status.
     */
    fun withStatus(nextStatus: String, now: Long = System.currentTimeMillis()): DownloadEntity {
        val normalizedStatus = normalizeStatus(nextStatus)
        return copy(
            status = normalizedStatus,
            endTime = if (normalizedStatus in TERMINAL_STATUSES) now else null,
            downloadedSize = if (normalizedStatus == STATUS_COMPLETED && fileSize > UNKNOWN_SIZE) fileSize else downloadedSize
        ).normalized(now)
    }

    companion object {
        const val TABLE_NAME: String = "downloads"
        const val STATUS_PENDING: String = "pending"
        const val STATUS_DOWNLOADING: String = "downloading"
        const val STATUS_COMPLETED: String = "completed"
        const val STATUS_FAILED: String = "failed"
        const val STATUS_PAUSED: String = "paused"
        const val UNKNOWN_SIZE: Long = -1L
        const val MIN_PROGRESS: Int = 0
        const val MAX_PROGRESS: Int = 100

        val SUPPORTED_STATUSES: Set<String> = setOf(
            STATUS_PENDING,
            STATUS_DOWNLOADING,
            STATUS_COMPLETED,
            STATUS_FAILED,
            STATUS_PAUSED
        )

        val ACTIVE_STATUSES: Set<String> = setOf(
            STATUS_PENDING,
            STATUS_DOWNLOADING
        )

        val TERMINAL_STATUSES: Set<String> = setOf(
            STATUS_COMPLETED,
            STATUS_FAILED
        )

        /**
         * Creates a normalized, validated download record.
         */
        @Throws(IllegalArgumentException::class)
        fun create(
            url: String,
            fileName: String,
            filePath: String,
            fileSize: Long,
            profileId: Int,
            mimeType: String? = null,
            isAutoDownload: Boolean = false,
            now: Long = System.currentTimeMillis()
        ): DownloadEntity {
            return DownloadEntity(
                url = url,
                fileName = fileName,
                filePath = filePath,
                fileSize = fileSize,
                status = STATUS_PENDING,
                startTime = now,
                mimeType = mimeType,
                profileId = profileId,
                isAutoDownload = isAutoDownload
            ).requireValid()
        }

        /**
         * Calculates download progress from transferred bytes.
         */
        fun calculateProgress(downloadedSize: Long, fileSize: Long): Int {
            if (fileSize <= 0L) return MIN_PROGRESS
            return ((downloadedSize.coerceAtLeast(0L) * MAX_PROGRESS) / fileSize)
                .coerceIn(MIN_PROGRESS.toLong(), MAX_PROGRESS.toLong())
                .toInt()
        }

        /**
         * Normalizes persisted status strings.
         */
        fun normalizeStatus(status: String): String {
            val cleanStatus = status.trim().lowercase()
            return if (cleanStatus in SUPPORTED_STATUSES) cleanStatus else STATUS_PENDING
        }
    }
}

/**
 * Validation failures for [DownloadEntity].
 */
sealed class DownloadValidationError {
    abstract val message: String

    data object InvalidId : DownloadValidationError() {
        override val message: String = "Download id cannot be negative."
    }

    data object InvalidProfileId : DownloadValidationError() {
        override val message: String = "Download profileId must be positive."
    }

    data class InvalidUrl(val url: String) : DownloadValidationError() {
        override val message: String = "Invalid download URL: $url."
    }

    data object BlankFileName : DownloadValidationError() {
        override val message: String = "Download fileName cannot be blank."
    }

    data object BlankFilePath : DownloadValidationError() {
        override val message: String = "Download filePath cannot be blank."
    }

    data object InvalidFileSize : DownloadValidationError() {
        override val message: String = "Download fileSize cannot be less than -1."
    }

    data object InvalidDownloadedSize : DownloadValidationError() {
        override val message: String = "Download downloadedSize cannot be negative."
    }

    data object DownloadedSizeExceedsFileSize : DownloadValidationError() {
        override val message: String = "Download downloadedSize cannot exceed fileSize."
    }

    data class UnsupportedStatus(val status: String) : DownloadValidationError() {
        override val message: String = "Unsupported download status: $status."
    }

    data object InvalidProgress : DownloadValidationError() {
        override val message: String = "Download progress must be between 0 and 100."
    }

    data object InvalidStartTime : DownloadValidationError() {
        override val message: String = "Download startTime must be positive."
    }

    data object InvalidEndTime : DownloadValidationError() {
        override val message: String = "Download endTime cannot be earlier than startTime."
    }
}
