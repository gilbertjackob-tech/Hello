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
import com.glassbox.hello.database.entities.CookieEntity
import com.glassbox.hello.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access layer for profile-scoped HTTP cookies.
 */
@Dao
abstract class CookieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCookie(cookie: CookieEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCookies(cookies: List<CookieEntity>): List<Long>

    @Update
    protected abstract suspend fun updateCookie(cookie: CookieEntity): Int

    @Delete
    protected abstract suspend fun deleteCookieEntity(cookie: CookieEntity): Int

    @Query("SELECT * FROM cookies WHERE cookieKey = :cookieKey LIMIT 1")
    protected abstract suspend fun getCookieByKeyInternal(cookieKey: String): CookieEntity?

    @Query("SELECT * FROM cookies WHERE profileId = :profileId ORDER BY timestamp DESC")
    protected abstract fun getAllCookiesInternal(profileId: Int): Flow<List<CookieEntity>>

    @Query(
        "SELECT * FROM cookies " +
            "WHERE profileId = :profileId " +
            "AND (domain = :domain OR :domain LIKE '%.' || domain) " +
            "AND (expiresAt IS NULL OR expiresAt > :now) " +
            "ORDER BY LENGTH(path) DESC, timestamp DESC"
    )
    protected abstract suspend fun getCookiesByDomainInternal(
        profileId: Int,
        domain: String,
        now: Long
    ): List<CookieEntity>

    @Query(
        "SELECT * FROM cookies " +
            "WHERE profileId = :profileId " +
            "AND (domain = :domain OR :domain LIKE '%.' || domain) " +
            "AND (:path = path OR :path LIKE path || '%') " +
            "AND (expiresAt IS NULL OR expiresAt > :now) " +
            "ORDER BY LENGTH(path) DESC, timestamp DESC"
    )
    protected abstract suspend fun getCookiesForRequestInternal(
        profileId: Int,
        domain: String,
        path: String,
        now: Long
    ): List<CookieEntity>

    @Query("DELETE FROM cookies WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    protected abstract suspend fun deleteExpiredCookiesInternal(now: Long): Int

    @Query("DELETE FROM cookies WHERE profileId = :profileId")
    protected abstract suspend fun clearCookiesInternal(profileId: Int): Int

    @Query("DELETE FROM cookies WHERE profileId = :profileId AND domain = :domain")
    protected abstract suspend fun clearCookiesByDomainInternal(profileId: Int, domain: String): Int

    @Query("DELETE FROM cookies WHERE cookieKey IN (:cookieKeys)")
    protected abstract suspend fun deleteByKeysInternal(cookieKeys: List<String>): Int

    /**
     * Inserts a validated cookie and returns its row id.
     */
    suspend fun insert(cookie: CookieEntity): Long {
        return execute("insert cookie") {
            insertCookie(cookie.requireValid())
        }
    }

    /**
     * Inserts validated cookies in a single transaction.
     */
    @Transaction
    open suspend fun insertAll(cookies: List<CookieEntity>): List<Long> {
        require(cookies.isNotEmpty()) { "Cookie batch must not be empty." }
        return execute("insert cookie batch") {
            insertCookies(cookies.map { cookie -> cookie.requireValid() })
        }
    }

    /**
     * Updates a validated cookie and returns the number of affected rows.
     */
    suspend fun update(cookie: CookieEntity): Int {
        return execute("update cookie") {
            updateCookie(cookie.requireValid())
        }
    }

    /**
     * Deletes a cookie and returns the number of affected rows.
     */
    suspend fun delete(cookie: CookieEntity): Int {
        return execute("delete cookie") {
            deleteCookieEntity(cookie)
        }
    }

    /**
     * Returns a cookie by primary key, or null.
     */
    suspend fun getCookieByKey(cookieKey: String): CookieEntity? {
        val cleanKey = cookieKey.trim()
        require(cleanKey.isNotBlank()) { "Cookie key must not be blank." }
        return execute("get cookie by key") {
            getCookieByKeyInternal(cleanKey)
        }
    }

    /**
     * Observes all cookies for a profile.
     */
    fun getAllCookies(profileId: Int): Flow<List<CookieEntity>> {
        require(profileId > 0) { "Profile id must be positive." }
        return getAllCookiesInternal(profileId)
    }

    /**
     * Returns unexpired cookies matching a request domain.
     */
    suspend fun getCookiesByDomain(
        profileId: Int,
        domain: String,
        now: Long = System.currentTimeMillis()
    ): List<CookieEntity> {
        require(profileId > 0) { "Profile id must be positive." }
        val cleanDomain = CookieEntity.normalizeDomain(domain)
        require(CookieEntity.isValidDomain(cleanDomain)) { "Invalid cookie domain." }
        return execute("get cookies by domain") {
            getCookiesByDomainInternal(profileId, cleanDomain, now)
        }
    }

    /**
     * Returns unexpired cookies that match a request URL.
     */
    suspend fun getCookiesForRequest(
        profileId: Int,
        url: String,
        now: Long = System.currentTimeMillis()
    ): List<CookieEntity> {
        require(profileId > 0) { "Profile id must be positive." }
        val cleanUrl = HistoryEntity.normalizeUrl(url)
        val cleanDomain = CookieEntity.domainFromUrl(url)
        require(!cleanDomain.isNullOrBlank()) { "URL must include a valid host." }
        val cleanPath = CookieEntity.normalizePath(java.net.URI(cleanUrl).path)
        return execute("get cookies for request") {
            getCookiesForRequestInternal(profileId, cleanDomain, cleanPath, now)
                .filter { cookie -> cookie.matchesUrl(cleanUrl, now) }
        }
    }

    /**
     * Deletes expired cookies and returns the number of affected rows.
     */
    suspend fun deleteExpiredCookies(now: Long = System.currentTimeMillis()): Int {
        return execute("delete expired cookies") {
            deleteExpiredCookiesInternal(now)
        }
    }

    /**
     * Clears all cookies for a profile.
     */
    suspend fun clearCookies(profileId: Int): Int {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("clear profile cookies") {
            clearCookiesInternal(profileId)
        }
    }

    /**
     * Clears all cookies for a profile and domain.
     */
    suspend fun clearCookiesByDomain(profileId: Int, domain: String): Int {
        require(profileId > 0) { "Profile id must be positive." }
        val cleanDomain = CookieEntity.normalizeDomain(domain)
        require(CookieEntity.isValidDomain(cleanDomain)) { "Invalid cookie domain." }
        return execute("clear domain cookies") {
            clearCookiesByDomainInternal(profileId, cleanDomain)
        }
    }

    /**
     * Deletes cookies by primary keys.
     */
    suspend fun deleteByKeys(cookieKeys: List<String>): Int {
        val cleanKeys = cookieKeys.map { key -> key.trim() }.filter { key -> key.isNotBlank() }
        require(cleanKeys.isNotEmpty()) { "Cookie keys must not be empty." }
        return execute("delete cookies by keys") {
            deleteByKeysInternal(cleanKeys.distinct())
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
        private const val TAG: String = "CookieDao"
    }
}
