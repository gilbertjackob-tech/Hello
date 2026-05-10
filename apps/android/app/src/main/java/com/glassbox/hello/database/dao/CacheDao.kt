package com.glassbox.hello.database.dao

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.glassbox.hello.database.entities.CacheEntity
import com.glassbox.hello.database.entities.HistoryEntity

/**
 * Room access layer for browser resource cache.
 */
@Dao
abstract class CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCache(cache: CacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCacheItems(cacheItems: List<CacheEntity>): List<Long>

    @Query(
        "SELECT * FROM cache " +
            "WHERE url = :url AND isValid = 1 AND (expiresAt IS NULL OR expiresAt > :now) " +
            "LIMIT 1"
    )
    protected abstract suspend fun getByUrlInternal(url: String, now: Long): CacheEntity?

    @Query("DELETE FROM cache WHERE expiresAt IS NOT NULL AND expiresAt < :now OR isValid = 0")
    protected abstract suspend fun deleteExpiredCacheInternal(now: Long): Int

    @Query("SELECT SUM(size) FROM cache WHERE isValid = 1")
    protected abstract suspend fun getTotalCacheSizeInternal(): Long?

    @Query("DELETE FROM cache WHERE url = :url")
    protected abstract suspend fun deleteByUrlInternal(url: String): Int

    @Query("DELETE FROM cache WHERE url IN (SELECT url FROM cache ORDER BY timestamp ASC LIMIT :limit)")
    protected abstract suspend fun deleteOldestInternal(limit: Int): Int

    @Query("DELETE FROM cache")
    protected abstract suspend fun clearAllCacheInternal(): Int

    /**
     * Inserts a validated cache entry and returns its row id.
     */
    suspend fun insert(cache: CacheEntity): Long {
        return execute("insert cache") {
            insertCache(cache.requireValid())
        }
    }

    /**
     * Inserts validated cache entries in a single transaction.
     */
    @Transaction
    open suspend fun insertAll(cacheItems: List<CacheEntity>): List<Long> {
        require(cacheItems.isNotEmpty()) { "Cache batch must not be empty." }
        return execute("insert cache batch") {
            insertCacheItems(cacheItems.map { cache -> cache.requireValid() })
        }
    }

    /**
     * Returns a valid, unexpired cache entry for [url], or null.
     */
    suspend fun getByUrl(url: String, now: Long = System.currentTimeMillis()): CacheEntity? {
        val cleanUrl = HistoryEntity.normalizeUrl(url)
        require(HistoryEntity.isValidUrl(cleanUrl)) { "Invalid cache URL." }
        return execute("get cache by url") {
            getByUrlInternal(cleanUrl, now)
        }
    }

    /**
     * Deletes expired or invalid cache rows.
     */
    suspend fun deleteExpiredCache(now: Long = System.currentTimeMillis()): Int {
        return execute("delete expired cache") {
            deleteExpiredCacheInternal(now)
        }
    }

    /**
     * Returns total valid cache bytes.
     */
    suspend fun getTotalCacheSize(): Long {
        return execute("get cache size") {
            getTotalCacheSizeInternal() ?: 0L
        }
    }

    /**
     * Deletes a cache row by URL.
     */
    suspend fun deleteByUrl(url: String): Int {
        val cleanUrl = HistoryEntity.normalizeUrl(url)
        require(HistoryEntity.isValidUrl(cleanUrl)) { "Invalid cache URL." }
        return execute("delete cache by url") {
            deleteByUrlInternal(cleanUrl)
        }
    }

    /**
     * Deletes the oldest [limit] cache rows.
     */
    suspend fun deleteOldest(limit: Int): Int {
        require(limit > 0) { "Delete limit must be positive." }
        return execute("delete oldest cache") {
            deleteOldestInternal(limit)
        }
    }

    /**
     * Clears every cache row.
     */
    suspend fun clearAllCache(): Int {
        return execute("clear all cache") {
            clearAllCacheInternal()
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
        private const val TAG: String = "CacheDao"
    }
}
