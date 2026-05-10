package com.glassbox.hello.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.glassbox.hello.database.entities.CookieEntity
import com.glassbox.hello.database.entities.DownloadEntity
import com.glassbox.hello.database.entities.HistoryEntity
import com.glassbox.hello.database.entities.ProfileEntity
import com.glassbox.hello.repository.BrowserRepository
import com.glassbox.hello.repository.ClearBrowsingDataResult
import com.glassbox.hello.repository.PruneResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Browser business ViewModel backed by the Room repository layer.
 */
class BrowserViewModel(
    private val repository: BrowserRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserViewState())
    val state: StateFlow<BrowserViewState> = _state.asStateFlow()

    private var activeProfileJob: Job? = null
    private var historyJob: Job? = null
    private var bookmarksJob: Job? = null
    private var downloadsJob: Job? = null
    private var cookiesJob: Job? = null

    init {
        loadActiveProfile()
    }

    /**
     * Starts observing the active profile and dependent browser data.
     */
    fun loadActiveProfile() {
        activeProfileJob?.cancel()
        activeProfileJob = viewModelScope.launch {
            repository.getActiveProfile()
                .catch { error -> handleError("load active profile", error) }
                .collect { profile ->
                    _state.update { current ->
                        current.copy(activeProfile = profile, isLoading = false)
                    }
                    observeProfileData(profile?.id)
                }
        }
    }

    /**
     * Navigates to a normalized URL and records a visit for HTTP(S) pages.
     */
    fun navigateToUrl(url: String, title: String? = null, faviconUrl: String? = null) {
        val normalizedUrl = normalizeBrowserUrl(url)
        _state.update { current ->
            current.copy(
                currentUrl = normalizedUrl,
                isLoading = true,
                errorMessage = null,
                statusMessage = "Navigating"
            )
        }

        if (HistoryEntity.isValidUrl(normalizedUrl)) {
            recordPageVisit(normalizedUrl, title, faviconUrl)
        }
    }

    /**
     * Marks page loading as complete and optionally records the final title.
     */
    fun finishNavigation(title: String? = null, faviconUrl: String? = null) {
        val url = _state.value.currentUrl
        _state.update { current ->
            current.copy(isLoading = false, statusMessage = null)
        }
        if (HistoryEntity.isValidUrl(url)) {
            recordPageVisit(url, title, faviconUrl)
        }
    }

    /**
     * Records a page visit for the active profile.
     */
    fun recordPageVisit(url: String, title: String? = null, faviconUrl: String? = null) {
        val profileId = _state.value.activeProfile?.id
        if (profileId == null) {
            _state.update { current -> current.copy(errorMessage = "No active browser profile.") }
            return
        }

        viewModelScope.launch {
            execute("record page visit") {
                repository.addToHistory(
                    url = url,
                    title = title,
                    profileId = profileId,
                    faviconUrl = faviconUrl
                )
            }
        }
    }

    /**
     * Searches active-profile history and keeps the result stream in state.
     */
    fun searchHistory(query: String) {
        val profileId = _state.value.activeProfile?.id
        if (profileId == null) {
            _state.update { current -> current.copy(errorMessage = "No active browser profile.") }
            return
        }

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.searchHistory(query, profileId)
                .catch { error -> handleError("search history", error) }
                .collect { items ->
                    _state.update { current ->
                        current.copy(historyItems = items, searchQuery = query.trim(), isLoading = false)
                    }
                }
        }
    }

    /**
     * Switches the active browser profile.
     */
    fun switchProfile(profileId: Int) {
        viewModelScope.launch {
            execute("switch profile") {
                val profile = repository.switchProfile(profileId)
                _state.update { current ->
                    current.copy(activeProfile = profile, statusMessage = "Profile switched")
                }
            }
        }
    }

    /**
     * Creates a browser profile and activates it when requested.
     */
    fun createProfile(
        name: String,
        type: String = ProfileEntity.TYPE_CUSTOM,
        email: String? = null,
        activate: Boolean = true
    ) {
        viewModelScope.launch {
            execute("create profile") {
                val id = repository.profiles.createProfile(
                    name = name,
                    type = type,
                    email = email,
                    isActive = activate
                )
                if (activate) {
                    repository.switchProfile(id.toInt())
                }
                _state.update { current -> current.copy(statusMessage = "Profile created") }
            }
        }
    }

    /**
     * Enqueues a download and starts progress tracking from persisted state.
     */
    fun downloadFile(
        url: String,
        fileName: String,
        filePath: String = fileName,
        fileSize: Long = DownloadEntity.UNKNOWN_SIZE,
        mimeType: String? = null
    ) {
        val profileId = _state.value.activeProfile?.id
        if (profileId == null) {
            _state.update { current -> current.copy(errorMessage = "No active browser profile.") }
            return
        }

        viewModelScope.launch {
            execute("queue download") {
                val id = repository.downloads.enqueueDownload(
                    url = url,
                    fileName = fileName,
                    filePath = filePath,
                    fileSize = fileSize,
                    profileId = profileId,
                    mimeType = mimeType
                )
                _state.update { current ->
                    current.copy(downloadProgress = current.downloadProgress + (id.toInt() to 0))
                }
            }
        }
    }

    /**
     * Saves a cookie for the active profile.
     */
    fun saveCookie(
        domain: String,
        name: String,
        value: String,
        path: String = "/",
        expiresAt: Long? = null,
        isSecure: Boolean = false,
        isHttpOnly: Boolean = false
    ) {
        val profileId = _state.value.activeProfile?.id
        if (profileId == null) {
            _state.update { current -> current.copy(errorMessage = "No active browser profile.") }
            return
        }

        viewModelScope.launch {
            execute("save cookie") {
                repository.saveCookie(
                    domain = domain,
                    name = name,
                    value = value,
                    profileId = profileId,
                    path = path,
                    expiresAt = expiresAt,
                    isSecure = isSecure,
                    isHttpOnly = isHttpOnly
                )
            }
        }
    }

    /**
     * Caches response bytes for the current browser session.
     */
    fun cacheResponse(url: String, data: ByteArray, expiresAt: Long? = null) {
        viewModelScope.launch {
            execute("cache response") {
                repository.cacheResponse(url, data, expiresAt)
            }
        }
    }

    /**
     * Clears selected browsing data for the active profile.
     */
    fun clearBrowsingData(
        clearHistory: Boolean = true,
        clearCookies: Boolean = true,
        clearCache: Boolean = false
    ) {
        val profileId = _state.value.activeProfile?.id
        if (profileId == null) {
            _state.update { current -> current.copy(errorMessage = "No active browser profile.") }
            return
        }

        viewModelScope.launch {
            execute("clear browsing data") {
                val result = repository.clearBrowsingData(
                    profileId = profileId,
                    clearHistory = clearHistory,
                    clearCookies = clearCookies,
                    clearCache = clearCache
                )
                _state.update { current ->
                    current.copy(statusMessage = result.toStatusMessage())
                }
            }
        }
    }

    /**
     * Deletes expired cookies and invalid cache entries.
     */
    fun pruneExpiredData() {
        viewModelScope.launch {
            execute("prune expired data") {
                val result = repository.pruneExpiredData()
                _state.update { current ->
                    current.copy(statusMessage = result.toStatusMessage())
                }
            }
        }
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
        activeProfileJob?.cancel()
        historyJob?.cancel()
        bookmarksJob?.cancel()
        downloadsJob?.cancel()
        cookiesJob?.cancel()
        super.onCleared()
    }

    private fun observeProfileData(profileId: Int?) {
        historyJob?.cancel()
        bookmarksJob?.cancel()
        downloadsJob?.cancel()
        cookiesJob?.cancel()

        if (profileId == null) {
            _state.update { current ->
                current.copy(
                    historyItems = emptyList(),
                    bookmarks = emptyList(),
                    downloads = emptyList(),
                    cookies = emptyList(),
                    downloadProgress = emptyMap()
                )
            }
            return
        }

        historyJob = viewModelScope.launch {
            repository.getHistory(profileId)
                .catch { error -> handleError("observe history", error) }
                .collect { items -> _state.update { current -> current.copy(historyItems = items) } }
        }
        bookmarksJob = viewModelScope.launch {
            repository.getBookmarks(profileId)
                .catch { error -> handleError("observe bookmarks", error) }
                .collect { items -> _state.update { current -> current.copy(bookmarks = items) } }
        }
        downloadsJob = viewModelScope.launch {
            repository.downloads.getDownloads(profileId)
                .catch { error -> handleError("observe downloads", error) }
                .collect { items ->
                    _state.update { current ->
                        current.copy(
                            downloads = items,
                            downloadProgress = items.associate { download -> download.id to download.progress }
                        )
                    }
                }
        }
        cookiesJob = viewModelScope.launch {
            repository.getAllCookies(profileId)
                .catch { error -> handleError("observe cookies", error) }
                .collect { items -> _state.update { current -> current.copy(cookies = items) } }
        }
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
        Log.e(TAG, "Browser ViewModel failure during $operation.", error)
        _state.update { current ->
            current.copy(
                isLoading = false,
                errorMessage = error.message ?: "Browser operation failed."
            )
        }
    }

    private fun normalizeBrowserUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return DEFAULT_URL
        if (trimmed == DEFAULT_URL) return DEFAULT_URL
        return HistoryEntity.normalizeUrl(trimmed)
    }

    private fun ClearBrowsingDataResult.toStatusMessage(): String {
        return "Cleared $historyRows history, $cookieRows cookies, $cacheRows cache entries"
    }

    private fun PruneResult.toStatusMessage(): String {
        return "Pruned $expiredCookies cookies and $expiredCacheEntries cache entries"
    }

    companion object {
        private const val TAG: String = "BrowserViewModel"
        private const val DEFAULT_URL: String = "about:blank"

        /**
         * Creates a factory backed by the application database.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
                        return BrowserViewModel(BrowserRepository.create(applicationContext)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

/**
 * Immutable browser state exposed to Compose and XML UI layers.
 */
data class BrowserViewState(
    val currentUrl: String = "about:blank",
    val activeProfile: ProfileEntity? = null,
    val historyItems: List<HistoryEntity> = emptyList(),
    val bookmarks: List<HistoryEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val cookies: List<CookieEntity> = emptyList(),
    val downloadProgress: Map<Int, Int> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)
