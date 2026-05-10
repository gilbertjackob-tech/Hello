package com.glassbox.hello.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.glassbox.hello.database.AppDatabase
import com.glassbox.hello.database.dao.DownloadProgressUpdate
import com.glassbox.hello.database.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository focused on download queue management and status transitions.
 */
class DownloadRepository(
    private val database: AppDatabase
) {
    private val downloadDao = database.downloadDao()

    /**
     * Observes downloads for a profile.
     */
    fun getDownloads(profileId: Int): Flow<List<DownloadEntity>> {
        return downloadDao.getDownloadsByProfile(profileId)
    }

    /**
     * Observes one download by id.
     */
    fun getDownload(downloadId: Int): Flow<DownloadEntity?> {
        return downloadDao.getDownloadById(downloadId)
    }

    /**
     * Observes downloads matching a status.
     */
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>> {
        return downloadDao.getDownloadsByStatus(requireSupportedStatus(status))
    }

    /**
     * Observes queued downloads for a profile.
     */
    fun getQueue(profileId: Int): Flow<List<DownloadEntity>> {
        return downloadDao.getDownloadsByProfileAndStatus(profileId, DownloadEntity.STATUS_PENDING)
    }

    /**
     * Enqueues a new download and returns its row id.
     */
    suspend fun enqueueDownload(
        url: String,
        fileName: String,
        filePath: String,
        fileSize: Long,
        profileId: Int,
        mimeType: String? = null,
        isAutoDownload: Boolean = false
    ): Long {
        return execute("enqueue download") {
            downloadDao.insert(
                DownloadEntity.create(
                    url = url,
                    fileName = fileName,
                    filePath = filePath,
                    fileSize = fileSize,
                    profileId = profileId,
                    mimeType = mimeType,
                    isAutoDownload = isAutoDownload
                )
            )
        }
    }

    /**
     * Starts a pending or paused download.
     */
    suspend fun startDownload(downloadId: Int): DownloadEntity {
        return transition(downloadId, DownloadEntity.STATUS_DOWNLOADING)
    }

    /**
     * Pauses a pending or active download.
     */
    suspend fun pauseDownload(downloadId: Int): DownloadEntity {
        return transition(downloadId, DownloadEntity.STATUS_PAUSED)
    }

    /**
     * Resumes a paused download.
     */
    suspend fun resumeDownload(downloadId: Int): DownloadEntity {
        return transition(downloadId, DownloadEntity.STATUS_DOWNLOADING)
    }

    /**
     * Marks a download completed and writes final progress.
     */
    suspend fun completeDownload(downloadId: Int, downloadedSize: Long? = null): DownloadEntity {
        require(downloadId > 0) { "Download id must be positive." }
        return execute("complete download") {
            database.withTransaction {
                val current = requireDownload(downloadId)
                requireTransition(current.status, DownloadEntity.STATUS_COMPLETED)
                val finalSize = downloadedSize
                    ?: current.fileSize.takeIf { size -> size > DownloadEntity.UNKNOWN_SIZE }
                    ?: current.downloadedSize
                downloadDao.updateProgress(
                    id = downloadId,
                    progress = DownloadEntity.MAX_PROGRESS,
                    size = finalSize.coerceAtLeast(0L)
                )
                downloadDao.updateStatus(downloadId, DownloadEntity.STATUS_COMPLETED)
                requireDownload(downloadId)
            }
        }
    }

    /**
     * Marks a download failed.
     */
    suspend fun failDownload(downloadId: Int): DownloadEntity {
        return transition(downloadId, DownloadEntity.STATUS_FAILED)
    }

    /**
     * Retries a failed download by placing it back in the queue.
     */
    suspend fun retryDownload(downloadId: Int): DownloadEntity {
        return transition(downloadId, DownloadEntity.STATUS_PENDING)
    }

    /**
     * Deletes a download by id.
     */
    suspend fun cancelDownload(downloadId: Int): Boolean {
        require(downloadId > 0) { "Download id must be positive." }
        return execute("cancel download") {
            val current = downloadDao.getDownloadSnapshot(downloadId) ?: return@execute false
            downloadDao.delete(current) > 0
        }
    }

    /**
     * Updates download byte progress.
     */
    suspend fun updateProgress(downloadId: Int, downloadedSize: Long, fileSize: Long): Int {
        return execute("update download progress") {
            downloadDao.updateDownloadedSize(downloadId, downloadedSize, fileSize)
        }
    }

    /**
     * Applies multiple progress updates in one transaction.
     */
    suspend fun updateProgressBatch(updates: List<DownloadProgressUpdate>): Int {
        return execute("update download progress batch") {
            downloadDao.updateProgressBatch(updates)
        }
    }

    /**
     * Starts the oldest queued download for a profile.
     */
    suspend fun startNextQueued(profileId: Int): DownloadEntity? {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("start next queued download") {
            database.withTransaction {
                val next = downloadDao
                    .getDownloadsSnapshotByProfileAndStatus(profileId, DownloadEntity.STATUS_PENDING)
                    .firstOrNull()
                    ?: return@withTransaction null
                transitionInsideTransaction(next, DownloadEntity.STATUS_DOWNLOADING)
            }
        }
    }

    /**
     * Deletes terminal downloads for a profile.
     */
    suspend fun clearFinishedDownloads(profileId: Int): Int {
        return execute("clear finished downloads") {
            downloadDao.deleteTerminalDownloads(profileId)
        }
    }

    private suspend fun transition(downloadId: Int, nextStatus: String): DownloadEntity {
        require(downloadId > 0) { "Download id must be positive." }
        val cleanNextStatus = requireSupportedStatus(nextStatus)
        return execute("transition download") {
            database.withTransaction {
                val current = requireDownload(downloadId)
                transitionInsideTransaction(current, cleanNextStatus)
            }
        }
    }

    private suspend fun transitionInsideTransaction(
        current: DownloadEntity,
        nextStatus: String
    ): DownloadEntity {
        if (current.status == nextStatus) return current
        requireTransition(current.status, nextStatus)
        downloadDao.updateStatus(current.id, nextStatus)
        return requireDownload(current.id)
    }

    private suspend fun requireDownload(downloadId: Int): DownloadEntity {
        return downloadDao.getDownloadSnapshot(downloadId)
            ?: throw NoSuchElementException("Download $downloadId does not exist.")
    }

    private fun requireTransition(currentStatus: String, nextStatus: String) {
        val cleanCurrent = requireSupportedStatus(currentStatus)
        val cleanNext = requireSupportedStatus(nextStatus)
        val allowed = allowedTransitions[cleanCurrent].orEmpty()
        check(cleanNext in allowed) {
            "Cannot transition download from $cleanCurrent to $cleanNext."
        }
    }

    private fun requireSupportedStatus(status: String): String {
        val cleanStatus = status.trim().lowercase(Locale.US)
        require(cleanStatus in DownloadEntity.SUPPORTED_STATUSES) { "Unsupported download status: $status." }
        return cleanStatus
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Invalid input during $operation.", error)
            throw error
        } catch (error: BrowserRepositoryException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Repository failure during $operation.", error)
            throw BrowserRepositoryException(operation, error)
        }
    }

    companion object {
        private const val TAG: String = "DownloadRepository"

        private val allowedTransitions: Map<String, Set<String>> = mapOf(
            DownloadEntity.STATUS_PENDING to setOf(
                DownloadEntity.STATUS_DOWNLOADING,
                DownloadEntity.STATUS_PAUSED,
                DownloadEntity.STATUS_FAILED
            ),
            DownloadEntity.STATUS_DOWNLOADING to setOf(
                DownloadEntity.STATUS_PAUSED,
                DownloadEntity.STATUS_COMPLETED,
                DownloadEntity.STATUS_FAILED
            ),
            DownloadEntity.STATUS_PAUSED to setOf(
                DownloadEntity.STATUS_PENDING,
                DownloadEntity.STATUS_DOWNLOADING,
                DownloadEntity.STATUS_FAILED
            ),
            DownloadEntity.STATUS_FAILED to setOf(
                DownloadEntity.STATUS_PENDING,
                DownloadEntity.STATUS_DOWNLOADING
            ),
            DownloadEntity.STATUS_COMPLETED to emptySet()
        )

        /**
         * Creates a repository from an Android context without retaining an Activity reference.
         */
        fun create(context: Context): DownloadRepository {
            return DownloadRepository(AppDatabase.getInstance(context.applicationContext))
        }
    }
}
