package com.glassbox.hello.familydrive

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class FamilyDriveUiState(
    val items: List<DriveItem> = emptyList(),
    val pendingItems: List<PendingDriveItem> = emptyList(),
    val total: Int = 0,
    val nextCursor: Long? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isUploading: Boolean = false,
    val deletingItemId: String? = null,
    val retryingPendingId: String? = null,
    val uploadDone: Int = 0,
    val uploadTotal: Int = 0,
    val error: String? = null,
    val infoMessage: String? = null
)

class FamilyDriveViewModel : ViewModel() {
    private val repository = FamilyDriveRepository()
    private val pageSize = 60
    private var pendingObserverJob: Job? = null

    private val _state = MutableStateFlow(FamilyDriveUiState())
    val state: StateFlow<FamilyDriveUiState> = _state.asStateFlow()

    fun startPendingObserver(context: Context) {
        if (pendingObserverJob != null) return
        pendingObserverJob = viewModelScope.launch {
            repository.observePendingUploads(context.applicationContext).collect { pending ->
                _state.update { it.copy(pendingItems = pending) }
            }
        }
    }

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, nextCursor = null, hasMore = false) }
        viewModelScope.launch {
            val result = repository.fetchItems(limit = pageSize, before = null)
            _state.update { current ->
                result.fold(
                    onSuccess = { response ->
                        current.copy(
                            items = response.items,
                            total = response.total,
                            nextCursor = response.nextCursor,
                            hasMore = response.hasMore,
                            isLoading = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        current.copy(isLoading = false, error = error.message ?: "Drive load failed")
                    }
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore || current.nextCursor == null) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchItems(limit = pageSize, before = current.nextCursor)
            _state.update { latest ->
                result.fold(
                    onSuccess = { response ->
                        val merged = (latest.items + response.items).distinctBy { it.id }
                        latest.copy(
                            items = merged,
                            total = response.total,
                            nextCursor = response.nextCursor,
                            hasMore = response.hasMore,
                            isLoadingMore = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        latest.copy(isLoadingMore = false, error = error.message ?: "More photos could not load")
                    }
                )
            }
        }
    }

    fun upload(context: Context, uploaderId: String, uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isUploading) return
        _state.update {
            it.copy(
                isUploading = true,
                uploadDone = 0,
                uploadTotal = uris.size,
                error = null,
                infoMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.uploadUris(
                context = context.applicationContext,
                uris = uris,
                uploaderId = uploaderId,
                onProgress = { done, total ->
                    _state.update { it.copy(uploadDone = done, uploadTotal = total) }
                }
            )
            result.fold(
                onSuccess = { outcome ->
                    _state.update {
                        it.copy(
                            isUploading = false,
                            uploadDone = 0,
                            uploadTotal = 0,
                            infoMessage = if (outcome.pendingItems.isNotEmpty()) LOCAL_SAVE_MESSAGE else null
                        )
                    }
                    if (outcome.pendingItems.isNotEmpty()) {
                        FamilyDriveUploadWorker.enqueue(context.applicationContext, uploaderId)
                    }
                    refresh()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isUploading = false,
                            uploadDone = 0,
                            uploadTotal = 0,
                            error = error.message ?: "Upload failed"
                        )
                    }
                }
            )
        }
    }

    fun deleteItem(itemId: String, onDeleted: () -> Unit = {}) {
        if (_state.value.deletingItemId != null) return
        _state.update { it.copy(deletingItemId = itemId, error = null) }
        viewModelScope.launch {
            val result = repository.deleteItem(itemId)
            result.fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            items = current.items.filterNot { it.id == itemId },
                            total = (current.total - 1).coerceAtLeast(0),
                            deletingItemId = null,
                            error = null
                        )
                    }
                    onDeleted()
                },
                onFailure = { error ->
                    _state.update { current ->
                        current.copy(
                            deletingItemId = null,
                            error = error.message ?: "Delete failed"
                        )
                    }
                }
            )
        }
    }

    fun retryPending(context: Context, uploaderId: String, itemId: String? = null) {
        if (uploaderId.isBlank() || _state.value.retryingPendingId != null) return
        _state.update { it.copy(retryingPendingId = itemId ?: "all", error = null, infoMessage = null) }
        viewModelScope.launch {
            val result = repository.retryPendingUploads(context.applicationContext, uploaderId, itemId)
            result.fold(
                onSuccess = { uploadedCount ->
                    _state.update {
                        it.copy(
                            retryingPendingId = null,
                            infoMessage = if (uploadedCount > 0) {
                                "Pending uploads completed. Your photos/videos are saved to PC."
                            } else {
                                null
                            }
                        )
                    }
                    if (uploadedCount > 0) refresh()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            retryingPendingId = null,
                            error = error.message ?: "Pending uploads could not sync"
                        )
                    }
                }
            )
            FamilyDriveUploadWorker.enqueue(context.applicationContext, uploaderId)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfoMessage() {
        _state.update { it.copy(infoMessage = null) }
    }

    companion object {
        const val LOCAL_SAVE_MESSAGE: String =
            "Saved locally. Waiting for PC connection. Please don't delete the original photos/videos until upload is complete."
    }
}
