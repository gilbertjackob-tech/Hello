package com.glassbox.hello.database.dao

import android.database.sqlite.SQLiteException
import com.glassbox.hello.debug.AppLog as Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.glassbox.hello.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access layer for browser history and bookmarks.
 */
@Dao
abstract class HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertHistory(history: HistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertHistoryItems(histories: List<HistoryEntity>): List<Long>

    @Update
    protected abstract suspend fun updateHistory(history: HistoryEntity): Int

    @Delete
    protected abstract suspend fun deleteHistory(history: HistoryEntity): Int

    @Query("SELECT * FROM browsing_history WHERE id = :id LIMIT 1")
    protected abstract suspend fun getHistoryByIdInternal(id: Int): HistoryEntity?

    @Query("SELECT * FROM browsing_history WHERE profileId = :profileId AND url = :url LIMIT 1")
    protected abstract suspend fun getHistoryByUrlInternal(profileId: Int, url: String): HistoryEntity?

    @Query(
        "SELECT * FROM browsing_history " +
            "WHERE profileId = :profileId " +
            "ORDER BY lastVisited DESC LIMIT :limit"
    )
    protected abstract fun getHistoryByProfileInternal(
        profileId: Int,
        limit: Int
    ): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM browsing_history " +
            "WHERE profileId = :profileId " +
            "ORDER BY lastVisited DESC LIMIT :limit OFFSET :offset"
    )
    protected abstract suspend fun getHistoryPageInternal(
        profileId: Int,
        limit: Int,
        offset: Int
    ): List<HistoryEntity>

    @Query(
        "SELECT * FROM browsing_history " +
            "WHERE profileId = :profileId " +
            "AND (url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
            "ORDER BY lastVisited DESC LIMIT :limit"
    )
    protected abstract fun searchHistoryInternal(
        query: String,
        profileId: Int,
        limit: Int
    ): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM browsing_history " +
            "WHERE profileId = :profileId " +
            "AND (url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
            "ORDER BY lastVisited DESC LIMIT :limit OFFSET :offset"
    )
    protected abstract suspend fun searchHistoryPageInternal(
        query: String,
        profileId: Int,
        limit: Int,
        offset: Int
    ): List<HistoryEntity>

    @Query("DELETE FROM browsing_history WHERE timestamp < :beforeTime AND profileId = :profileId")
    protected abstract suspend fun clearHistoryBeforeInternal(beforeTime: Long, profileId: Int): Int

    @Query("DELETE FROM browsing_history WHERE profileId = :profileId")
    protected abstract suspend fun clearAllHistoryInternal(profileId: Int): Int

    @Query("DELETE FROM browsing_history WHERE id IN (:ids)")
    protected abstract suspend fun deleteByIdsInternal(ids: List<Int>): Int

    @Query("UPDATE browsing_history SET isBookmarked = :isBookmarked WHERE id = :id")
    protected abstract suspend fun setBookmarkStateInternal(id: Int, isBookmarked: Boolean): Int

    @Query(
        "SELECT * FROM browsing_history " +
            "WHERE isBookmarked = 1 AND profileId = :profileId " +
            "ORDER BY lastVisited DESC LIMIT :limit"
    )
    protected abstract fun getBookmarksInternal(profileId: Int, limit: Int): Flow<List<HistoryEntity>>

    /**
     * Inserts a validated history item and returns its row id.
     */
    suspend fun insert(history: HistoryEntity): Long {
        return execute("insert history") {
            insertHistory(history.requireValid())
        }
    }

    /**
     * Inserts validated history items in a single transaction.
     */
    @Transaction
    open suspend fun insertAll(histories: List<HistoryEntity>): List<Long> {
        require(histories.isNotEmpty()) { "History batch must not be empty." }
        return execute("insert history batch") {
            insertHistoryItems(histories.map { history -> history.requireValid() })
        }
    }

    /**
     * Deletes a history item and returns the number of affected rows.
     */
    suspend fun delete(history: HistoryEntity): Int {
        return execute("delete history") {
            deleteHistory(history)
        }
    }

    /**
     * Records a page visit, incrementing the existing URL row when present.
     */
    @Transaction
    open suspend fun recordVisit(history: HistoryEntity): Long {
        return execute("record history visit") {
            val normalized = history.requireValid()
            val existing = getHistoryByUrlInternal(normalized.profileId, normalized.url)
            if (existing == null) {
                insertHistory(normalized)
            } else {
                updateHistory(
                    existing.incrementVisit(
                        pageTitle = normalized.title,
                        pageFaviconUrl = normalized.faviconUrl,
                        now = normalized.lastVisited
                    )
                )
                existing.id.toLong()
            }
        }
    }

    /**
     * Returns the history row with [id], or null when no row exists.
     */
    suspend fun getHistoryById(id: Int): HistoryEntity? {
        require(id > 0) { "History id must be positive." }
        return execute("get history by id") {
            getHistoryByIdInternal(id)
        }
    }

    /**
     * Observes recent history for a profile.
     */
    fun getHistoryByProfile(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        return getHistoryByProfileInternal(profileId, coerceLimit(limit))
    }

    /**
     * Returns one history page for a profile.
     */
    suspend fun getHistoryPage(
        profileId: Int,
        offset: Int,
        limit: Int = DEFAULT_PAGE_SIZE
    ): List<HistoryEntity> {
        require(profileId > 0) { "Profile id must be positive." }
        require(offset >= 0) { "Offset cannot be negative." }
        return execute("get history page") {
            getHistoryPageInternal(profileId, coerceLimit(limit), offset)
        }
    }

    /**
     * Observes search results for a profile.
     */
    fun searchHistory(
        query: String,
        profileId: Int,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Flow<List<HistoryEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        return searchHistoryInternal(query.trim(), profileId, coerceLimit(limit))
    }

    /**
     * Returns one search results page for a profile.
     */
    suspend fun searchHistoryPage(
        query: String,
        profileId: Int,
        offset: Int,
        limit: Int = DEFAULT_PAGE_SIZE
    ): List<HistoryEntity> {
        require(profileId > 0) { "Profile id must be positive." }
        require(offset >= 0) { "Offset cannot be negative." }
        return execute("search history page") {
            searchHistoryPageInternal(query.trim(), profileId, coerceLimit(limit), offset)
        }
    }

    /**
     * Clears history older than [beforeTime] for a profile.
     */
    suspend fun clearHistoryBefore(beforeTime: Long, profileId: Int): Int {
        require(beforeTime > 0L) { "Before time must be positive." }
        require(profileId > 0) { "Profile id must be positive." }
        return execute("clear old history") {
            clearHistoryBeforeInternal(beforeTime, profileId)
        }
    }

    /**
     * Clears all history for a profile.
     */
    suspend fun clearAllHistory(profileId: Int): Int {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("clear profile history") {
            clearAllHistoryInternal(profileId)
        }
    }

    /**
     * Deletes a batch of history rows.
     */
    suspend fun deleteByIds(ids: List<Int>): Int {
        require(ids.isNotEmpty()) { "History ids must not be empty." }
        require(ids.all { id -> id > 0 }) { "History ids must be positive." }
        return execute("delete history batch") {
            deleteByIdsInternal(ids.distinct())
        }
    }

    /**
     * Marks a history row as bookmarked.
     */
    suspend fun bookmarkHistory(id: Int): Int {
        return setBookmarkState(id, isBookmarked = true)
    }

    /**
     * Updates a history row bookmark state.
     */
    suspend fun setBookmarkState(id: Int, isBookmarked: Boolean): Int {
        require(id > 0) { "History id must be positive." }
        return execute("set bookmark state") {
            setBookmarkStateInternal(id, isBookmarked)
        }
    }

    /**
     * Observes bookmarked history for a profile.
     */
    fun getBookmarks(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        return getBookmarksInternal(profileId, coerceLimit(limit))
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

    private fun coerceLimit(limit: Int): Int {
        return limit.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
    }

    private companion object {
        private const val TAG: String = "HistoryDao"
        private const val MIN_PAGE_SIZE: Int = 1
        private const val DEFAULT_PAGE_SIZE: Int = 100
        private const val MAX_PAGE_SIZE: Int = 500
    }
}
