package com.glassbox.hello.familydrive

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyDriveUiState(
    val items: List<DriveItem> = emptyList(),
    val trashItems: List<DriveItem> = emptyList(),
    val pendingItems: List<PendingDriveItem> = emptyList(),
    val total: Int = 0,
    val trashTotal: Int = 0,
    val nextCursor: Long? = null,
    val trashNextCursor: Long? = null,
    val hasMore: Boolean = false,
    val trashHasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTrashLoading: Boolean = false,
    val isTrashLoadingMore: Boolean = false,
    val isUploading: Boolean = false,
    val isBusy: Boolean = false,
    val retryingPendingId: String? = null,
    val uploadDone: Int = 0,
    val uploadTotal: Int = 0,
    val error: String? = null,
    val infoMessage: String? = null,
    val lastDeleteLimit: DriveDeleteLimit? = null,
    val events: List<DriveEvent> = emptyList(),
    val circles: List<DriveCircle> = emptyList(),
    val chatContacts: List<DriveContact> = emptyList()
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
            val result = repository.fetchItems(limit = pageSize, before = null, sync = true)
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

    fun refreshTrash() {
        if (_state.value.isTrashLoading) return
        _state.update { it.copy(isTrashLoading = true, error = null, trashNextCursor = null, trashHasMore = false) }
        viewModelScope.launch {
            val result = repository.fetchTrash(limit = pageSize, before = null, sync = true)
            _state.update { current ->
                result.fold(
                    onSuccess = { response ->
                        current.copy(
                            trashItems = response.items,
                            trashTotal = response.total,
                            trashNextCursor = response.nextCursor,
                            trashHasMore = response.hasMore,
                            isTrashLoading = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        current.copy(isTrashLoading = false, error = error.message ?: "Trash could not load")
                    }
                )
            }
        }
    }

    fun refreshDeleteLimit(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            repository.fetchDeleteLimit(userId).fold(
                onSuccess = { limit -> _state.update { it.copy(lastDeleteLimit = limit) } },
                onFailure = { /* Keep the existing limit if the PC is offline. */ }
            )
        }
    }

    fun refreshDriveSetup(context: Context, userId: String) {
        viewModelScope.launch {
            repository.fetchEvents().fold(
                onSuccess = { events -> _state.update { it.copy(events = events) } },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Drive events could not load") } }
            )
            repository.fetchCircles().fold(
                onSuccess = { circles -> _state.update { it.copy(circles = circles) } },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Drive circles could not load") } }
            )
            repository.fetchChatContacts(context.applicationContext, userId).fold(
                onSuccess = { contacts -> _state.update { it.copy(chatContacts = contacts) } },
                onFailure = { /* Keep contact picker empty rather than showing unknown users. */ }
            )
        }
    }

    fun createEvent(name: String, userId: String, onCreated: (DriveEvent) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(error = "Enter an event name") }
            return
        }
        viewModelScope.launch {
            repository.createEvent(cleanName, userId).fold(
                onSuccess = { event ->
                    _state.update { current ->
                        current.copy(
                            events = (listOf(event) + current.events.filterNot { it.id == event.id }),
                            infoMessage = "Event created."
                        )
                    }
                    onCreated(event)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Event could not be created") } }
            )
        }
    }

    fun createCircle(name: String, ownerUserId: String, members: List<DriveCircleMember>, onCreated: (DriveCircle) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(error = "Enter a circle name") }
            return
        }
        viewModelScope.launch {
            repository.createCircle(cleanName, ownerUserId, members).fold(
                onSuccess = { circle ->
                    _state.update { current ->
                        current.copy(
                            circles = (listOf(circle) + current.circles.filterNot { it.id == circle.id }),
                            infoMessage = "Circle created."
                        )
                    }
                    onCreated(circle)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Circle could not be created") } }
            )
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
                        latest.copy(
                            items = (latest.items + response.items).distinctBy { it.id },
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

    fun loadMoreTrash() {
        val current = _state.value
        if (current.isTrashLoading || current.isTrashLoadingMore || !current.trashHasMore || current.trashNextCursor == null) return
        _state.update { it.copy(isTrashLoadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchTrash(limit = pageSize, before = current.trashNextCursor)
            _state.update { latest ->
                result.fold(
                    onSuccess = { response ->
                        latest.copy(
                            trashItems = (latest.trashItems + response.items).distinctBy { it.id },
                            trashTotal = response.total,
                            trashNextCursor = response.nextCursor,
                            trashHasMore = response.hasMore,
                            isTrashLoadingMore = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        latest.copy(isTrashLoadingMore = false, error = error.message ?: "More trash could not load")
                    }
                )
            }
        }
    }

    fun upload(context: Context, uploaderId: String, uris: List<Uri>, plan: DriveUploadPlan = DriveUploadPlan()) {
        if (uris.isEmpty() || _state.value.isUploading) return
        _state.update {
            it.copy(
                isUploading = true,
                uploadDone = 0,
                uploadTotal = uris.size,
                error = null,
                infoMessage = "Waiting to upload ${uris.size} item${if (uris.size == 1) "" else "s"}..."
            )
        }
        viewModelScope.launch {
            val result = repository.uploadUris(
                context = context.applicationContext,
                uris = uris,
                uploaderId = uploaderId,
                plan = plan,
                onProgress = { done, total ->
                    _state.update {
                        it.copy(
                            uploadDone = done,
                            uploadTotal = total,
                            infoMessage = "Uploading $done / $total..."
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { outcome ->
                    _state.update {
                        it.copy(
                            isUploading = false,
                            uploadDone = 0,
                            uploadTotal = 0,
                            infoMessage = if (outcome.pendingItems.isNotEmpty()) {
                                LOCAL_SAVE_MESSAGE
                            } else {
                                "Upload successful. Saved to Family Drive."
                            }
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

    fun moveItemsToTrash(userId: String, itemIds: Set<String>, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                var latestLimit: DriveDeleteLimit? = null
                itemIds.forEach { itemId ->
                    latestLimit = repository.deleteItem(itemId, userId).getOrThrow().deleteLimit
                }
                _state.update { current ->
                    current.copy(
                        items = current.items.filterNot { it.id in itemIds },
                        total = (current.total - itemIds.size).coerceAtLeast(0),
                        lastDeleteLimit = latestLimit,
                        isBusy = false,
                        infoMessage = "${itemIds.size} item${if (itemIds.size == 1) "" else "s"} moved to Trash."
                    )
                }
                refreshTrash()
                refreshDeleteLimit(userId)
                onDone()
            } catch (error: Exception) {
                _state.update { it.copy(isBusy = false, error = error.message ?: "Delete failed") }
            }
        }
    }

    fun restoreItems(itemIds: Set<String>, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                val restored = itemIds.map { repository.restoreItem(it).getOrThrow() }
                _state.update { current ->
                    current.copy(
                        trashItems = current.trashItems.filterNot { it.id in itemIds },
                        items = (restored + current.items).distinctBy { it.id },
                        trashTotal = (current.trashTotal - itemIds.size).coerceAtLeast(0),
                        total = current.total + restored.size,
                        isBusy = false,
                        infoMessage = "${restored.size} item${if (restored.size == 1) "" else "s"} restored."
                    )
                }
                onDone()
            } catch (error: Exception) {
                _state.update { it.copy(isBusy = false, error = error.message ?: "Restore failed") }
            }
        }
    }

    fun permanentlyDeleteItems(itemIds: Set<String>, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                itemIds.forEach { repository.permanentlyDeleteItem(it).getOrThrow() }
                _state.update { current ->
                    current.copy(
                        trashItems = current.trashItems.filterNot { it.id in itemIds },
                        trashTotal = (current.trashTotal - itemIds.size).coerceAtLeast(0),
                        isBusy = false,
                        infoMessage = "${itemIds.size} item${if (itemIds.size == 1) "" else "s"} permanently deleted."
                    )
                }
                onDone()
            } catch (error: Exception) {
                _state.update { it.copy(isBusy = false, error = error.message ?: "Permanent delete failed") }
            }
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

    fun removePending(context: Context, itemId: String) {
        viewModelScope.launch {
            repository.removePendingUpload(context.applicationContext, itemId).fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            pendingItems = current.pendingItems.filterNot { it.id == itemId },
                            infoMessage = "Pending local upload removed."
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "Pending upload could not be removed") }
                }
            )
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
            "Saved locally, waiting for PC. Please don't delete the original photos/videos until upload is complete."
    }
}
