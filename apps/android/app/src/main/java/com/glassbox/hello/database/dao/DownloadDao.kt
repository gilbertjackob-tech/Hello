package com.glassbox.hello.database.dao

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.glassbox.hello.database.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Immutable progress update used for batch download progress writes.
 */
data class DownloadProgressUpdate(
    val id: Int,
    val downloadedSize: Long,
    val fileSize: Long
)

/**
 * Room access layer for browser downloads.
 */
@Dao
abstract class DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertDownload(download: DownloadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertDownloads(downloads: List<DownloadEntity>): List<Long>

    @Update
    protected abstract suspend fun updateDownload(download: DownloadEntity): Int

    @Update
    protected abstract suspend fun updateDownloads(downloads: List<DownloadEntity>): Int

    @Delete
    protected abstract suspend fun deleteDownload(download: DownloadEntity): Int

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    protected abstract fun getDownloadByIdInternal(id: Int): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    protected abstract suspend fun getDownloadSnapshotInternal(id: Int): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY startTime DESC")
    protected abstract fun getDownloadsByProfileInternal(profileId: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY startTime DESC")
    protected abstract fun getDownloadsByStatusInternal(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND status = :status ORDER BY startTime DESC")
    protected abstract fun getDownloadsByProfileAndStatusInternal(
        profileId: Int,
        status: String
    ): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND status = :status ORDER BY startTime ASC")
    protected abstract suspend fun getDownloadsSnapshotByProfileAndStatusInternal(
        profileId: Int,
        status: String
    ): List<DownloadEntity>

    @Query("UPDATE downloads SET progress = :progress, downloadedSize = :size WHERE id = :id")
    protected abstract suspend fun updateProgressInternal(id: Int, progress: Int, size: Long): Int

    @Query("UPDATE downloads SET status = :status, endTime = :endTime WHERE id = :id")
    protected abstract suspend fun updateStatusInternal(id: Int, status: String, endTime: Long?): Int

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    protected abstract suspend fun deleteByIdsInternal(ids: List<Int>): Int

    @Query("DELETE FROM downloads WHERE profileId = :profileId AND status IN (:statuses)")
    protected abstract suspend fun deleteByStatusesInternal(profileId: Int, statuses: List<String>): Int

    /**
     * Inserts a validated download and returns its row id.
     */
    suspend fun insert(download: DownloadEntity): Long {
        return execute("insert download") {
            insertDownload(download.requireValid())
        }
    }

    /**
     * Inserts validated downloads in a single transaction.
     */
    @Transaction
    open suspend fun insertAll(downloads: List<DownloadEntity>): List<Long> {
        require(downloads.isNotEmpty()) { "Download batch must not be empty." }
        return execute("insert download batch") {
            insertDownloads(downloads.map { download -> download.requireValid() })
        }
    }

    /**
     * Updates a validated download and returns the number of affected rows.
     */
    suspend fun update(download: DownloadEntity): Int {
        return execute("update download") {
            updateDownload(download.requireValid())
        }
    }

    /**
     * Updates validated downloads in a single transaction.
     */
    @Transaction
    open suspend fun updateAll(downloads: List<DownloadEntity>): Int {
        require(downloads.isNotEmpty()) { "Download batch must not be empty." }
        return execute("update download batch") {
            updateDownloads(downloads.map { download -> download.requireValid() })
        }
    }

    /**
     * Deletes a download and returns the number of affected rows.
     */
    suspend fun delete(download: DownloadEntity): Int {
        return execute("delete download") {
            deleteDownload(download)
        }
    }

    /**
     * Observes a download by id.
     */
    fun getDownloadById(id: Int): Flow<DownloadEntity?> {
        require(id > 0) { "Download id must be positive." }
        return getDownloadByIdInternal(id)
    }

    /**
     * Returns the current download row by id, or null.
     */
    suspend fun getDownloadSnapshot(id: Int): DownloadEntity? {
        require(id > 0) { "Download id must be positive." }
        return execute("get download snapshot") {
            getDownloadSnapshotInternal(id)
        }
    }

    /**
     * Observes downloads for a profile.
     */
    fun getDownloadsByProfile(profileId: Int): Flow<List<DownloadEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        return getDownloadsByProfileInternal(profileId)
    }

    /**
     * Observes downloads matching a status.
     */
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>> {
        val cleanStatus = DownloadEntity.normalizeStatus(status)
        require(cleanStatus in DownloadEntity.SUPPORTED_STATUSES) { "Unsupported download status." }
        return getDownloadsByStatusInternal(cleanStatus)
    }

    /**
     * Observes downloads matching a profile and status.
     */
    fun getDownloadsByProfileAndStatus(profileId: Int, status: String): Flow<List<DownloadEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        val cleanStatus = DownloadEntity.normalizeStatus(status)
        require(cleanStatus in DownloadEntity.SUPPORTED_STATUSES) { "Unsupported download status." }
        return getDownloadsByProfileAndStatusInternal(profileId, cleanStatus)
    }

    /**
     * Returns downloads matching a profile and status ordered by queue position.
     */
    suspend fun getDownloadsSnapshotByProfileAndStatus(
        profileId: Int,
        status: String
    ): List<DownloadEntity> {
        require(profileId > 0) { "Profile id must be positive." }
        val cleanStatus = DownloadEntity.normalizeStatus(status)
        require(cleanStatus in DownloadEntity.SUPPORTED_STATUSES) { "Unsupported download status." }
        return execute("get queued download snapshot") {
            getDownloadsSnapshotByProfileAndStatusInternal(profileId, cleanStatus)
        }
    }

    /**
     * Updates progress and downloaded byte count for one download.
     */
    suspend fun updateProgress(id: Int, progress: Int, size: Long): Int {
        require(id > 0) { "Download id must be positive." }
        require(size >= 0L) { "Downloaded size cannot be negative." }
        return execute("update download progress") {
            updateProgressInternal(
                id = id,
                progress = progress.coerceIn(DownloadEntity.MIN_PROGRESS, DownloadEntity.MAX_PROGRESS),
                size = size
            )
        }
    }

    /**
     * Calculates and updates progress from downloaded bytes and total size.
     */
    suspend fun updateDownloadedSize(id: Int, downloadedSize: Long, fileSize: Long): Int {
        require(id > 0) { "Download id must be positive." }
        require(downloadedSize >= 0L) { "Downloaded size cannot be negative." }
        return updateProgress(
            id = id,
            progress = DownloadEntity.calculateProgress(downloadedSize, fileSize),
            size = downloadedSize
        )
    }

    /**
     * Applies multiple progress updates in one transaction.
     */
    @Transaction
    open suspend fun updateProgressBatch(updates: List<DownloadProgressUpdate>): Int {
        require(updates.isNotEmpty()) { "Progress updates must not be empty." }
        require(updates.all { update -> update.id > 0 }) { "Download ids must be positive." }
        require(updates.all { update -> update.downloadedSize >= 0L }) {
            "Downloaded sizes cannot be negative."
        }
        return execute("update download progress batch") {
            updates.sumOf { update ->
                updateProgressInternal(
                    id = update.id,
                    progress = DownloadEntity.calculateProgress(update.downloadedSize, update.fileSize),
                    size = update.downloadedSize
                )
            }
        }
    }

    /**
     * Updates a download status and terminal timestamp.
     */
    suspend fun updateStatus(
        id: Int,
        status: String,
        endTime: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Int {
        require(id > 0) { "Download id must be positive." }
        val cleanStatus = DownloadEntity.normalizeStatus(status)
        require(cleanStatus in DownloadEntity.SUPPORTED_STATUSES) { "Unsupported download status." }
        val terminalEndTime = if (cleanStatus in DownloadEntity.TERMINAL_STATUSES) {
            endTime ?: now
        } else {
            null
        }
        return execute("update download status") {
            updateStatusInternal(id, cleanStatus, terminalEndTime)
        }
    }

    /**
     * Deletes downloads by ids.
     */
    suspend fun deleteByIds(ids: List<Int>): Int {
        require(ids.isNotEmpty()) { "Download ids must not be empty." }
        require(ids.all { id -> id > 0 }) { "Download ids must be positive." }
        return execute("delete download batch") {
            deleteByIdsInternal(ids.distinct())
        }
    }

    /**
     * Deletes completed or failed downloads for a profile.
     */
    suspend fun deleteTerminalDownloads(profileId: Int): Int {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("delete terminal downloads") {
            deleteByStatusesInternal(profileId, DownloadEntity.TERMINAL_STATUSES.toList())
        }
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (error: SQLiteException) {
            Log.e(TAG, "Database failure during $operation.", error)
            throw IllegalStateException("Failed to $operation.", error)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Invalid data during $operation.", error)
            throw error
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unexpected failure during $operation.", error)
            throw error
        }
    }

    private companion object {
        private const val TAG: String = "DownloadDao"
    }
}
