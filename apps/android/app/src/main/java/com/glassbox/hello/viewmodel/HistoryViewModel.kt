package com.glassbox.hello.viewmodel

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.glassbox.hello.database.entities.HistoryEntity
import com.glassbox.hello.repository.HistoryRepository
import com.glassbox.hello.repository.HistorySearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for history pagination, search, and bookmark workflows.
 */
class HistoryViewModel(
    private val repository: HistoryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryViewState())
    val state: StateFlow<HistoryViewState> = _state.asStateFlow()

    private var historyJob: Job? = null
    private var bookmarksJob: Job? = null
    private var searchDebounceJob: Job? = null

    /**
     * Observes recent history and bookmarks for a profile.
     */
    fun getHistory(profileId: Int, pageSize: Int = DEFAULT_PAGE_SIZE) {
        require(profileId > 0) { "Profile id must be positive." }
        historyJob?.cancel()
        bookmarksJob?.cancel()
        _state.update { current ->
            current.copy(
                profileId = profileId,
                page = 0,
                pageSize = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE),
                historyItems = emptyList(),
                searchResults = emptyList(),
                isLoading = true,
                errorMessage = null
            )
        }

        historyJob = viewModelScope.launch {
            repository.getHistory(profileId, pageSize)
                .catch { error -> handleError("observe history", error) }
                .collect { items ->
                    _state.update { current ->
                        current.copy(historyItems = items, isLoading = false, canLoadMore = items.size >= current.pageSize)
                    }
                }
        }

        bookmarksJob = viewModelScope.launch {
            repository.getBookmarks(profileId, pageSize)
                .catch { error -> handleError("observe bookmarks", error) }
                .collect { items -> _state.update { current -> current.copy(bookmarks = items) } }
        }
    }

    /**
     * Loads a specific history page for the current profile.
     */
    fun loadPage(page: Int) {
        val profileId = requireObservedProfile()
        val pageSize = _state.value.pageSize
        viewModelScope.launch {
            execute("load history page") {
                val items = repository.getHistoryPage(profileId, page, pageSize)
                _state.update { current ->
                    current.copy(
                        page = page,
                        historyItems = items,
                        canLoadMore = items.size >= pageSize
                    )
                }
            }
        }
    }

    /**
     * Loads the next history page for the current profile.
     */
    fun loadNextPage() {
        val nextPage = _state.value.page + 1
        loadPage(nextPage)
    }

    /**
     * Debounces and runs history search for the current profile.
     */
    fun searchHistory(query: String) {
        val profileId = requireObservedProfile()
        val cleanQuery = query.trim()
        searchDebounceJob?.cancel()
        _state.update { current ->
            current.copy(searchQuery = cleanQuery, isSearching = cleanQuery.isNotBlank(), errorMessage = null)
        }

        searchDebounceJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (cleanQuery.isBlank()) {
                _state.update { current -> current.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }
            execute("search history") {
                val results = repository.searchFullText(
                    query = cleanQuery,
                    profileId = profileId,
                    page = 0,
                    pageSize = _state.value.pageSize
                )
                _state.update { current ->
                    current.copy(searchResults = results, page = 0, isSearching = false)
                }
            }
        }
    }

    /**
     * Loads the next full-text search results page.
     */
    fun loadNextSearchPage() {
        val profileId = requireObservedProfile()
        val query = _state.value.searchQuery
        if (query.isBlank()) return
        val nextPage = _state.value.page + 1
        viewModelScope.launch {
            execute("load next search page") {
                val results = repository.searchFullText(
                    query = query,
                    profileId = profileId,
                    page = nextPage,
                    pageSize = _state.value.pageSize
                )
                _state.update { current ->
                    current.copy(
                        searchResults = current.searchResults + results,
                        page = nextPage,
                        canLoadMore = results.size >= current.pageSize
                    )
                }
            }
        }
    }

    /**
     * Records a page visit for the current profile.
     */
    fun recordVisit(url: String, title: String? = null, faviconUrl: String? = null) {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("record visit") {
                repository.recordVisit(url, title, profileId, faviconUrl)
            }
        }
    }

    /**
     * Deletes one history row.
     */
    fun deleteHistory(historyId: Int) {
        viewModelScope.launch {
            execute("delete history") {
                val deleted = repository.deleteHistory(historyId)
                _state.update { current ->
                    current.copy(statusMessage = if (deleted) "History deleted" else "History not found")
                }
            }
        }
    }

    /**
     * Deletes multiple history rows.
     */
    fun deleteHistory(ids: List<Int>) {
        viewModelScope.launch {
            execute("delete history batch") {
                val deleted = repository.deleteHistory(ids)
                _state.update { current -> current.copy(statusMessage = "Deleted $deleted history items") }
            }
        }
    }

    /**
     * Clears all history for the current profile.
     */
    fun clearHistory() {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("clear history") {
                val deleted = repository.clearHistory(profileId)
                _state.update { current -> current.copy(statusMessage = "Cleared $deleted history items") }
            }
        }
    }

    /**
     * Clears current-profile history older than [beforeTime].
     */
    fun clearHistoryBefore(beforeTime: Long) {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("clear old history") {
                val deleted = repository.clearHistoryBefore(profileId, beforeTime)
                _state.update { current -> current.copy(statusMessage = "Cleared $deleted history items") }
            }
        }
    }

    /**
     * Bookmarks one history row.
     */
    fun bookmarkUrl(historyId: Int) {
        viewModelScope.launch {
            execute("bookmark history") {
                repository.bookmarkUrl(historyId)
                _state.update { current -> current.copy(statusMessage = "Bookmarked") }
            }
        }
    }

    /**
     * Updates bookmark state for one history row.
     */
    fun setBookmarkState(historyId: Int, bookmarked: Boolean) {
        viewModelScope.launch {
            execute("set bookmark state") {
                repository.setBookmarkState(historyId, bookmarked)
                _state.update { current -> current.copy(statusMessage = if (bookmarked) "Bookmarked" else "Bookmark removed") }
            }
        }
    }

    /**
     * Toggles one history row bookmark state.
     */
    fun toggleBookmark(historyId: Int) {
        viewModelScope.launch {
            execute("toggle bookmark") {
                val updated = repository.toggleBookmark(historyId)
                _state.update { current ->
                    current.copy(
                        selectedHistoryItem = updated,
                        statusMessage = if (updated.isBookmarked) "Bookmarked" else "Bookmark removed"
                    )
                }
            }
        }
    }

    /**
     * Selects a history item for details UI.
     */
    fun selectHistoryItem(history: HistoryEntity?) {
        _state.update { current -> current.copy(selectedHistoryItem = history) }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _state.update { current -> current.copy(errorMessage = null) }
    }

    /**
     * Clears the current transient status message.
     */
    fun clearStatusMessage() {
        _state.update { current -> current.copy(statusMessage = null) }
    }

    override fun onCleared() {
        historyJob?.cancel()
        bookmarksJob?.cancel()
        searchDebounceJob?.cancel()
        super.onCleared()
    }

    private fun requireObservedProfile(): Int {
        val profileId = _state.value.profileId
        require(profileId != null && profileId > 0) { "Call getHistory(profileId) before history operations." }
        return profileId
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T? {
        _state.update { current -> current.copy(isLoading = true, errorMessage = null) }
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError(operation, error)
            null
        } finally {
            _state.update { current -> current.copy(isLoading = false) }
        }
    }

    private fun handleError(operation: String, error: Throwable) {
        if (error is CancellationException) throw error
        Log.e(TAG, "History ViewModel failure during $operation.", error)
        _state.update { current ->
            current.copy(
                isLoading = false,
                isSearching = false,
                errorMessage = error.message ?: "History operation failed."
            )
        }
    }

    companion object {
        private const val TAG: String = "HistoryViewModel"
        private const val MIN_PAGE_SIZE: Int = 1
        private const val DEFAULT_PAGE_SIZE: Int = 100
        private const val MAX_PAGE_SIZE: Int = 500
        private const val SEARCH_DEBOUNCE_MS: Long = 300L

        /**
         * Creates a factory backed by the application database.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                        return HistoryViewModel(HistoryRepository.create(applicationContext)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

/**
 * Immutable history screen state.
 */
data class HistoryViewState(
    val profileId: Int? = null,
    val historyItems: List<HistoryEntity> = emptyList(),
    val bookmarks: List<HistoryEntity> = emptyList(),
    val searchResults: List<HistorySearchResult> = emptyList(),
    val selectedHistoryItem: HistoryEntity? = null,
    val searchQuery: String = "",
    val page: Int = 0,
    val pageSize: Int = 100,
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)
