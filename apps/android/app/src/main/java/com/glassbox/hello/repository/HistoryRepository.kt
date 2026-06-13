package com.glassbox.hello.repository

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.database.AppDatabase
import com.glassbox.hello.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository focused on browser history, bookmarks, pagination, and text search.
 */
class HistoryRepository(
    private val database: AppDatabase
) {
    private val historyDao = database.historyDao()

    /**
     * Observes recent history for a profile.
     */
    fun getHistory(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        return historyDao.getHistoryByProfile(profileId, limit)
    }

    /**
     * Returns one page of recent history.
     */
    suspend fun getHistoryPage(
        profileId: Int,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HistoryEntity> {
        require(page >= 0) { "Page cannot be negative." }
        return execute("get history page") {
            historyDao.getHistoryPage(
                profileId = profileId,
                offset = page * coercePageSize(pageSize),
                limit = coercePageSize(pageSize)
            )
        }
    }

    /**
     * Observes simple title and URL search results.
     */
    fun searchHistory(
        query: String,
        profileId: Int,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Flow<List<HistoryEntity>> {
        return historyDao.searchHistory(query.trim(), profileId, limit)
    }

    /**
     * Returns one page of simple title and URL search results.
     */
    suspend fun searchHistoryPage(
        query: String,
        profileId: Int,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HistoryEntity> {
        require(page >= 0) { "Page cannot be negative." }
        return execute("search history page") {
            historyDao.searchHistoryPage(
                query = query.trim(),
                profileId = profileId,
                offset = page * coercePageSize(pageSize),
                limit = coercePageSize(pageSize)
            )
        }
    }

    /**
     * Performs repository-level full-text search across stored title and URL tokens.
     */
    suspend fun searchFullText(
        query: String,
        profileId: Int,
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HistorySearchResult> {
        require(page >= 0) { "Page cannot be negative." }
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()

        return execute("search full text history") {
            val candidateLimit = FULL_TEXT_CANDIDATE_LIMIT.coerceAtLeast((page + 1) * coercePageSize(pageSize))
            historyDao.getHistoryPage(
                profileId = profileId,
                offset = 0,
                limit = candidateLimit
            )
                .mapNotNull { history ->
                    val score = score(history, query.trim(), terms)
                    if (score > 0) {
                        HistorySearchResult(
                            history = history,
                            score = score,
                            matchedTerms = matchedTerms(history, terms)
                        )
                    } else {
                        null
                    }
                }
                .sortedWith(
                    compareByDescending<HistorySearchResult> { result -> result.score }
                        .thenByDescending { result -> result.history.lastVisited }
                )
                .drop(page * coercePageSize(pageSize))
                .take(coercePageSize(pageSize))
        }
    }

    /**
     * Records a visit and returns the history row id.
     */
    suspend fun addToHistory(
        url: String,
        title: String?,
        profileId: Int,
        faviconUrl: String? = null
    ): Long {
        return recordVisit(
            url = url,
            title = title,
            profileId = profileId,
            faviconUrl = faviconUrl
        )
    }

    /**
     * Records a visit and returns the history row id.
     */
    suspend fun recordVisit(
        url: String,
        title: String?,
        profileId: Int,
        faviconUrl: String? = null,
        isBookmarked: Boolean = false
    ): Long {
        return execute("record history visit") {
            historyDao.recordVisit(
                HistoryEntity.create(
                    url = url,
                    title = title,
                    profileId = profileId,
                    faviconUrl = faviconUrl,
                    isBookmarked = isBookmarked
                )
            )
        }
    }

    /**
     * Deletes one history row by id.
     */
    suspend fun deleteHistory(historyId: Int): Boolean {
        require(historyId > 0) { "History id must be positive." }
        return execute("delete history") {
            val history = historyDao.getHistoryById(historyId) ?: return@execute false
            historyDao.delete(history) > 0
        }
    }

    /**
     * Deletes multiple history rows by id.
     */
    suspend fun deleteHistory(ids: List<Int>): Int {
        return execute("delete history batch") {
            historyDao.deleteByIds(ids)
        }
    }

    /**
     * Clears all history for a profile.
     */
    suspend fun clearHistory(profileId: Int): Int {
        return execute("clear history") {
            historyDao.clearAllHistory(profileId)
        }
    }

    /**
     * Clears history older than [beforeTime] for a profile.
     */
    suspend fun clearHistoryBefore(profileId: Int, beforeTime: Long): Int {
        return execute("clear old history") {
            historyDao.clearHistoryBefore(beforeTime, profileId)
        }
    }

    /**
     * Marks a history row as bookmarked.
     */
    suspend fun bookmarkUrl(historyId: Int): Int {
        return historyDao.bookmarkHistory(historyId)
    }

    /**
     * Updates bookmark state for a history row.
     */
    suspend fun setBookmarkState(historyId: Int, bookmarked: Boolean): Int {
        return execute("set bookmark state") {
            historyDao.setBookmarkState(historyId, bookmarked)
        }
    }

    /**
     * Toggles bookmark state for a history row and returns the updated row.
     */
    suspend fun toggleBookmark(historyId: Int): HistoryEntity {
        require(historyId > 0) { "History id must be positive." }
        return execute("toggle bookmark") {
            val history = historyDao.getHistoryById(historyId)
                ?: throw NoSuchElementException("History row $historyId does not exist.")
            val updated = history.withBookmarkState(!history.isBookmarked)
            historyDao.setBookmarkState(historyId, updated.isBookmarked)
            updated
        }
    }

    /**
     * Observes bookmarked history for a profile.
     */
    fun getBookmarks(profileId: Int, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<HistoryEntity>> {
        return historyDao.getBookmarks(profileId, limit)
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

    private fun coercePageSize(pageSize: Int): Int {
        return pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
    }

    private fun tokenize(value: String): List<String> {
        return tokenRegex.findAll(value.lowercase())
            .map { match -> match.value }
            .filter { token -> token.length >= MIN_SEARCH_TOKEN_LENGTH }
            .distinct()
            .toList()
    }

    private fun score(history: HistoryEntity, phrase: String, terms: List<String>): Int {
        val cleanPhrase = phrase.lowercase()
        val title = history.title.orEmpty().lowercase()
        val url = history.url.lowercase()
        var score = 0

        if (cleanPhrase.isNotBlank() && title.contains(cleanPhrase)) score += TITLE_PHRASE_SCORE
        if (cleanPhrase.isNotBlank() && url.contains(cleanPhrase)) score += URL_PHRASE_SCORE

        terms.forEach { term ->
            if (title.contains(term)) score += TITLE_TOKEN_SCORE
            if (url.contains(term)) score += URL_TOKEN_SCORE
        }
        if (history.isBookmarked) score += BOOKMARK_SCORE
        return score
    }

    private fun matchedTerms(history: HistoryEntity, terms: List<String>): List<String> {
        val haystack = "${history.title.orEmpty()} ${history.url}".lowercase()
        return terms.filter { term -> haystack.contains(term) }
    }

    companion object {
        private const val TAG: String = "HistoryRepository"
        private const val MIN_PAGE_SIZE: Int = 1
        private const val DEFAULT_PAGE_SIZE: Int = 100
        private const val MAX_PAGE_SIZE: Int = 500
        private const val FULL_TEXT_CANDIDATE_LIMIT: Int = 1_000
        private const val MIN_SEARCH_TOKEN_LENGTH: Int = 2
        private const val TITLE_PHRASE_SCORE: Int = 60
        private const val URL_PHRASE_SCORE: Int = 30
        private const val TITLE_TOKEN_SCORE: Int = 12
        private const val URL_TOKEN_SCORE: Int = 6
        private const val BOOKMARK_SCORE: Int = 4
        private val tokenRegex: Regex = Regex("[\\p{L}\\p{Nd}]+")

        /**
         * Creates a repository from an Android context without retaining an Activity reference.
         */
        fun create(context: Context): HistoryRepository {
            return HistoryRepository(AppDatabase.getInstance(context.applicationContext))
        }
    }
}

/**
 * Ranked history search result.
 */
data class HistorySearchResult(
    val history: HistoryEntity,
    val score: Int,
    val matchedTerms: List<String>
)
