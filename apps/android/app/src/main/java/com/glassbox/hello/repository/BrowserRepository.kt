package com.glassbox.hello.repository

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import androidx.room.withTransaction
import com.glassbox.hello.database.AppDatabase
import com.glassbox.hello.database.entities.CacheEntity
import com.glassbox.hello.database.entities.CookieEntity
import com.glassbox.hello.database.entities.HistoryEntity
import com.glassbox.hello.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exception type used when browser repository operations fail after validation.
 */
class BrowserRepositoryException(
    val operation: String,
    cause: Throwable
) : RuntimeException("Browser repository operation failed: $operation.", cause)

/**
 * Aggregate repository for profile, history, cookie, cache, and download browser data.
 */
class BrowserRepository(
    private val database: AppDatabase
) {
    private val profileDao = database.profileDao()
    private val historyDao = database.historyDao()
    private val cookieDao = database.cookieDao()
    private val cacheDao = database.cacheDao()

    val profiles: ProfileRepository = ProfileRepository(database)
    val history: HistoryRepository = HistoryRepository(database)
    val downloads: DownloadRepository = DownloadRepository(database)

    /**
     * Observes the currently active profile.
     */
    fun getActiveProfile(): Flow<ProfileEntity?> {
        return profiles.getActiveProfile()
    }

    /**
     * Observes all profiles ordered by most recently updated.
     */
    fun getAllProfiles(): Flow<List<ProfileEntity>> {
        return profiles.getAllProfiles()
    }

    /**
     * Creates a profile and returns its database id.
     */
    suspend fun createProfile(profile: ProfileEntity): Long {
        return profiles.createProfile(profile)
    }

    /**
     * Atomically switches the active profile.
     */
    suspend fun switchProfile(profileId: Int): ProfileEntity {
        return profiles.switchProfile(profileId)
    }

    /**
     * Deletes a profile and its profile-scoped data.
     */
    suspend fun deleteProfile(profile: ProfileEntity): Boolean {
        return deleteProfile(profile.id)
    }

    /**
     * Deletes a profile and its profile-scoped data.
     */
    suspend fun deleteProfile(profileId: Int): Boolean {
        return profiles.deleteProfile(profileId)
    }

    /**
     * Observes recent browsing history for a profile.
     */
    fun getHistory(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        return history.getHistory(profileId, limit)
    }

    /**
     * Observes browsing history search results for a profile.
     */
    fun searchHistory(
        query: String,
        profileId: Int,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Flow<List<HistoryEntity>> {
        return history.searchHistory(query, profileId, limit)
    }

    /**
     * Records a browser visit and returns the history row id.
     */
    suspend fun addToHistory(
        url: String,
        title: String?,
        profileId: Int,
        faviconUrl: String? = null
    ): Long {
        return history.addToHistory(
            url = url,
            title = title,
            profileId = profileId,
            faviconUrl = faviconUrl
        )
    }

    /**
     * Clears all history for a profile.
     */
    suspend fun clearHistory(profileId: Int): Int {
        return history.clearHistory(profileId)
    }

    /**
     * Marks a history row as bookmarked.
     */
    suspend fun bookmarkUrl(historyId: Int): Int {
        return history.bookmarkUrl(historyId)
    }

    /**
     * Observes bookmarked history for a profile.
     */
    fun getBookmarks(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        return history.getBookmarks(profileId, limit)
    }

    /**
     * Saves one cookie for a profile.
     */
    suspend fun saveCookie(
        domain: String,
        name: String,
        value: String,
        profileId: Int,
        path: String = "/",
        expiresAt: Long? = null,
        isSecure: Boolean = false,
        isHttpOnly: Boolean = false
    ): Long {
        return execute("save cookie") {
            database.withTransaction {
                cookieDao.insert(
                    CookieEntity.create(
                        name = name,
                        value = value,
                        domain = domain,
                        path = path,
                        profileId = profileId,
                        expiresAt = expiresAt,
                        isSecure = isSecure,
                        isHttpOnly = isHttpOnly
                    )
                )
            }
        }
    }

    /**
     * Saves cookies in a single transaction.
     */
    suspend fun saveCookies(cookies: List<CookieEntity>): List<Long> {
        return execute("save cookie batch") {
            database.withTransaction {
                cookieDao.insertAll(cookies)
            }
        }
    }

    /**
     * Observes all cookies for a profile.
     */
    fun getAllCookies(profileId: Int): Flow<List<CookieEntity>> {
        return cookieDao.getAllCookies(profileId)
    }

    /**
     * Returns unexpired cookies matching a domain.
     */
    suspend fun getCookies(profileId: Int, domain: String): List<CookieEntity> {
        return execute("get cookies") {
            cookieDao.getCookiesByDomain(profileId, domain)
        }
    }

    /**
     * Returns unexpired cookies matching a request URL.
     */
    suspend fun getCookiesForRequest(profileId: Int, url: String): List<CookieEntity> {
        return execute("get request cookies") {
            cookieDao.getCookiesForRequest(profileId, url)
        }
    }

    /**
     * Clears all cookies for a profile.
     */
    suspend fun clearCookies(profileId: Int): Int {
        return execute("clear cookies") {
            cookieDao.clearCookies(profileId)
        }
    }

    /**
     * Caches response bytes for a URL using an absolute expiry time.
     */
    suspend fun cacheResponse(
        url: String,
        data: ByteArray,
        expiresAt: Long? = null
    ): Long {
        return execute("cache response") {
            database.withTransaction {
                val cleanData = data.copyOf()
                cacheDao.insert(
                    CacheEntity(
                        url = url,
                        data = cleanData,
                        expiresAt = expiresAt,
                        size = cleanData.size.toLong()
                    ).requireValid()
                )
            }
        }
    }

    /**
     * Caches response bytes for a URL using a relative max-age.
     */
    suspend fun cacheResponseForMaxAge(
        url: String,
        data: ByteArray,
        maxAgeMillis: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        return execute("cache response max age") {
            database.withTransaction {
                cacheDao.insert(CacheEntity.create(url, data, maxAgeMillis, now))
            }
        }
    }

    /**
     * Returns a valid cached entry for a URL, or null when missing or expired.
     */
    suspend fun getCachedEntry(url: String): CacheEntity? {
        return execute("get cached entry") {
            cacheDao.getByUrl(url)
        }
    }

    /**
     * Returns cached response bytes for a URL, or null when missing or expired.
     */
    suspend fun getCachedResponse(url: String): ByteArray? {
        return getCachedEntry(url)?.data?.copyOf()
    }

    /**
     * Deletes expired cookies and invalid cache rows.
     */
    suspend fun pruneExpiredData(now: Long = System.currentTimeMillis()): PruneResult {
        return execute("prune expired data") {
            database.withTransaction {
                PruneResult(
                    expiredCookies = cookieDao.deleteExpiredCookies(now),
                    expiredCacheEntries = cacheDao.deleteExpiredCache(now)
                )
            }
        }
    }

    /**
     * Clears every cache row.
     */
    suspend fun clearCache(): Int {
        return execute("clear cache") {
            cacheDao.clearAllCache()
        }
    }

    /**
     * Trims cache to [maxBytes] by deleting oldest entries first.
     */
    suspend fun trimCacheToSize(maxBytes: Long): Long {
        require(maxBytes >= 0L) { "Max bytes cannot be negative." }
        return execute("trim cache") {
            var currentSize = cacheDao.getTotalCacheSize()
            while (currentSize > maxBytes && cacheDao.deleteOldest(CACHE_TRIM_BATCH_SIZE) > 0) {
                currentSize = cacheDao.getTotalCacheSize()
            }
            currentSize
        }
    }

    /**
     * Clears selected browsing data for a profile.
     */
    suspend fun clearBrowsingData(
        profileId: Int,
        clearHistory: Boolean = true,
        clearCookies: Boolean = true,
        clearCache: Boolean = false
    ): ClearBrowsingDataResult {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("clear browsing data") {
            database.withTransaction {
                ClearBrowsingDataResult(
                    historyRows = if (clearHistory) historyDao.clearAllHistory(profileId) else 0,
                    cookieRows = if (clearCookies) cookieDao.clearCookies(profileId) else 0,
                    cacheRows = if (clearCache) cacheDao.clearAllCache() else 0
                )
            }
        }
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
        private const val TAG: String = "BrowserRepository"
        private const val DEFAULT_PAGE_SIZE: Int = 100
        private const val CACHE_TRIM_BATCH_SIZE: Int = 25

        /**
         * Creates a repository from an Android context without retaining an Activity reference.
         */
        fun create(context: Context): BrowserRepository {
            return BrowserRepository(AppDatabase.getInstance(context.applicationContext))
        }
    }
}

/**
 * Result returned after pruning expired browser data.
 */
data class PruneResult(
    val expiredCookies: Int,
    val expiredCacheEntries: Int
)

/**
 * Result returned after clearing selected profile browsing data.
 */
data class ClearBrowsingDataResult(
    val historyRows: Int,
    val cookieRows: Int,
    val cacheRows: Int
)
