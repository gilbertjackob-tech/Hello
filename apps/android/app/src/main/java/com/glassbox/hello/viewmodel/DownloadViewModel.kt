package com.glassbox.hello.viewmodel

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.glassbox.hello.database.dao.DownloadProgressUpdate
import com.glassbox.hello.database.entities.DownloadEntity
import com.glassbox.hello.repository.DownloadRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for persisted browser downloads and queue operations.
 */
class DownloadViewModel(
    private val repository: DownloadRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadViewState())
    val state: StateFlow<DownloadViewState> = _state.asStateFlow()

    private var downloadsJob: Job? = null
    private var queueJob: Job? = null
    private var activeDownloadJob: Job? = null

    /**
     * Observes downloads and queued items for a profile.
     */
    fun observeDownloads(profileId: Int) {
        require(profileId > 0) { "Profile id must be positive." }
        downloadsJob?.cancel()
        queueJob?.cancel()
        _state.update { current -> current.copy(profileId = profileId, isLoading = true, errorMessage = null) }

        downloadsJob = viewModelScope.launch {
            repository.getDownloads(profileId)
                .catch { error -> handleError("observe downloads", error) }
                .collect { downloads ->
                    _state.update { current ->
                        current.copy(
                            downloads = downloads,
                            progressById = downloads.associate { download -> download.id to download.progress },
                            isLoading = false
                        )
                    }
                }
        }

        queueJob = viewModelScope.launch {
            repository.getQueue(profileId)
                .catch { error -> handleError("observe download queue", error) }
                .collect { queue -> _state.update { current -> current.copy(queue = queue) } }
        }
    }

    /**
     * Tracks one active download by id.
     */
    fun trackDownload(downloadId: Int) {
        require(downloadId > 0) { "Download id must be positive." }
        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            repository.getDownload(downloadId)
                .catch { error -> handleError("track download", error) }
                .collect { download ->
                    _state.update { current ->
                        current.copy(
                            activeDownload = download,
                            progressById = if (download == null) {
                                current.progressById
                            } else {
                                current.progressById + (download.id to download.progress)
                            }
                        )
                    }
                }
        }
    }

    /**
     * Enqueues a new download for the observed profile.
     */
    fun enqueueDownload(
        url: String,
        fileName: String,
        filePath: String,
        fileSize: Long = DownloadEntity.UNKNOWN_SIZE,
        mimeType: String? = null,
        isAutoDownload: Boolean = false
    ) {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("enqueue download") {
                repository.enqueueDownload(
                    url = url,
                    fileName = fileName,
                    filePath = filePath,
                    fileSize = fileSize,
                    profileId = profileId,
                    mimeType = mimeType,
                    isAutoDownload = isAutoDownload
                )
                _state.update { current -> current.copy(statusMessage = "Download queued") }
            }
        }
    }

    /**
     * Starts a pending or paused download.
     */
    fun startDownload(downloadId: Int) {
        transition("start download") { repository.startDownload(downloadId) }
    }

    /**
     * Pauses an active download.
     */
    fun pauseDownload(downloadId: Int) {
        transition("pause download") { repository.pauseDownload(downloadId) }
    }

    /**
     * Resumes a paused download.
     */
    fun resumeDownload(downloadId: Int) {
        transition("resume download") { repository.resumeDownload(downloadId) }
    }

    /**
     * Marks a download complete.
     */
    fun completeDownload(downloadId: Int, downloadedSize: Long? = null) {
        transition("complete download") { repository.completeDownload(downloadId, downloadedSize) }
    }

    /**
     * Marks a download failed.
     */
    fun failDownload(downloadId: Int) {
        transition("fail download") { repository.failDownload(downloadId) }
    }

    /**
     * Retries a failed download.
     */
    fun retryDownload(downloadId: Int) {
        transition("retry download") { repository.retryDownload(downloadId) }
    }

    /**
     * Cancels and removes a download.
     */
    fun cancelDownload(downloadId: Int) {
        viewModelScope.launch {
            execute("cancel download") {
                val canceled = repository.cancelDownload(downloadId)
                _state.update { current ->
                    current.copy(statusMessage = if (canceled) "Download canceled" else "Download not found")
                }
            }
        }
    }

    /**
     * Updates progress for one download.
     */
    fun updateProgress(downloadId: Int, downloadedSize: Long, fileSize: Long) {
        viewModelScope.launch {
            execute("update download progress") {
                repository.updateProgress(downloadId, downloadedSize, fileSize)
            }
        }
    }

    /**
     * Applies multiple progress updates in one repository transaction.
     */
    fun updateProgressBatch(updates: List<DownloadProgressUpdate>) {
        viewModelScope.launch {
            execute("update progress batch") {
                val changed = repository.updateProgressBatch(updates)
                _state.update { current -> current.copy(statusMessage = "Updated $changed downloads") }
            }
        }
    }

    /**
     * Starts the oldest queued download for the observed profile.
     */
    fun startNextQueued() {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("start next queued") {
                val next = repository.startNextQueued(profileId)
                _state.update { current ->
                    current.copy(activeDownload = next, statusMessage = if (next == null) "Queue is empty" else "Download started")
                }
            }
        }
    }

    /**
     * Removes completed and failed downloads for the observed profile.
     */
    fun clearFinishedDownloads() {
        val profileId = requireObservedProfile()
        viewModelScope.launch {
            execute("clear finished downloads") {
                val deleted = repository.clearFinishedDownloads(profileId)
                _state.update { current -> current.copy(statusMessage = "Removed $deleted finished downloads") }
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
        downloadsJob?.cancel()
        queueJob?.cancel()
        activeDownloadJob?.cancel()
        super.onCleared()
    }

    private fun transition(operation: String, block: suspend () -> DownloadEntity) {
        viewModelScope.launch {
            execute(operation) {
                val download = block()
                _state.update { current -> current.copy(activeDownload = download, statusMessage = "Download updated") }
            }
        }
    }

    private fun requireObservedProfile(): Int {
        val profileId = _state.value.profileId
        require(profileId != null && profileId > 0) { "Call observeDownloads(profileId) before download operations." }
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
        Log.e(TAG, "Download ViewModel failure during $operation.", error)
        _state.update { current ->
            current.copy(
                isLoading = false,
                errorMessage = error.message ?: "Download operation failed."
            )
        }
    }

    companion object {
        private const val TAG: String = "DownloadViewModel"

        /**
         * Creates a factory backed by the application database.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    if (modelClass.isAssignableFrom(DownloadViewModel::class.java)) {
                        return DownloadViewModel(DownloadRepository.create(applicationContext)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

/**
 * Immutable download management state.
 */
data class DownloadViewState(
    val profileId: Int? = null,
    val downloads: List<DownloadEntity> = emptyList(),
    val queue: List<DownloadEntity> = emptyList(),
    val activeDownload: DownloadEntity? = null,
    val progressById: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)
