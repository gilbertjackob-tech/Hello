package com.glassbox.hello.familydrive

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassbox.hello.debug.HelloDebugLog
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
    val chatContacts: List<DriveContact> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val deletePolls: List<DriveDeletePoll> = emptyList(),
    val activeCircleId: String? = null,
    val activeEventId: String? = null
)

class FamilyDriveViewModel : ViewModel() {
    private val repository = FamilyDriveRepository()
    private val pageSize = 60
    private var pendingObserverJob: Job? = null
    private var activeUserId: String? = null
    private var activeCircleId: String? = null
    private var activeEventId: String? = null

    private val _state = MutableStateFlow(FamilyDriveUiState())
    val state: StateFlow<FamilyDriveUiState> = _state.asStateFlow()

    fun setScope(userId: String, circleId: String? = null, eventId: String? = null) {
        activeUserId = userId.ifBlank { null }
        activeCircleId = circleId?.ifBlank { null }
        activeEventId = eventId?.ifBlank { null }
        _state.update { it.copy(activeCircleId = activeCircleId, activeEventId = activeEventId) }
    }

    fun startPendingObserver(context: Context) {
        if (pendingObserverJob != null) return
        HelloDebugLog.d("DriveVm", "startPendingObserver")
        pendingObserverJob = viewModelScope.launch {
            repository.observePendingUploads(context.applicationContext).collect { pending ->
                HelloDebugLog.d("DriveVm", "pendingObserver update count=${pending.size}")
                _state.update { it.copy(pendingItems = pending) }
            }
        }
    }

    fun refresh(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId, eventId: String? = activeEventId) {
        if (_state.value.isLoading || userId.isBlank()) return
        setScope(userId, circleId, eventId)
        HelloDebugLog.d("DriveVm", "refresh userId=$userId circleId=$circleId eventId=$eventId")
        _state.update { it.copy(isLoading = true, error = null, nextCursor = null, hasMore = false) }
        viewModelScope.launch {
            val result = repository.fetchItems(
                userId = userId,
                limit = pageSize,
                before = null,
                sync = true,
                circleId = circleId,
                eventId = eventId
            )
            _state.update { current ->
                result.fold(
                    onSuccess = { response ->
                        HelloDebugLog.d("DriveVm", "refresh success items=${response.items.size} total=${response.total} hasMore=${response.hasMore}")
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
                        HelloDebugLog.w("DriveVm", "refresh failure error=${error.message}", error)
                        current.copy(isLoading = false, error = error.message ?: "Drive load failed")
                    }
                )
            }
        }
    }

    fun refreshTrash(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId, eventId: String? = activeEventId) {
        if (_state.value.isTrashLoading || userId.isBlank()) return
        setScope(userId, circleId, eventId)
        HelloDebugLog.d("DriveVm", "refreshTrash userId=$userId circleId=$circleId eventId=$eventId")
        _state.update { it.copy(isTrashLoading = true, error = null, trashNextCursor = null, trashHasMore = false) }
        viewModelScope.launch {
            val result = repository.fetchTrash(
                userId = userId,
                limit = pageSize,
                before = null,
                sync = true,
                circleId = circleId,
                eventId = eventId
            )
            _state.update { current ->
                result.fold(
                    onSuccess = { response ->
                        HelloDebugLog.d("DriveVm", "refreshTrash success items=${response.items.size} total=${response.total} hasMore=${response.hasMore}")
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
                        HelloDebugLog.w("DriveVm", "refreshTrash failure error=${error.message}", error)
                        current.copy(isTrashLoading = false, error = error.message ?: "Trash could not load")
                    }
                )
            }
        }
    }

    fun refreshDeleteLimit(userId: String) {
        if (userId.isBlank()) return
        HelloDebugLog.d("DriveVm", "refreshDeleteLimit userId=$userId")
        viewModelScope.launch {
            repository.fetchDeleteLimit(userId).fold(
                onSuccess = { limit ->
                    HelloDebugLog.d("DriveVm", "refreshDeleteLimit success remaining=${limit.remaining} limit=${limit.limit} used=${limit.used} deleteDay=${limit.deleteDay}")
                    _state.update { it.copy(lastDeleteLimit = limit) }
                },
                onFailure = { /* Keep the existing limit if the PC is offline. */ }
            )
        }
    }

    fun refreshDriveSetup(context: Context, userId: String, circleId: String? = activeCircleId) {
        if (userId.isBlank()) return
        HelloDebugLog.d("DriveVm", "refreshDriveSetup userId=$userId circleId=$circleId")
        setScope(userId, circleId, activeEventId)
        viewModelScope.launch {
            repository.fetchCircles(userId).fold(
                onSuccess = { circles ->
                    val uniqueCircles = circles.distinctBy { it.id }.sortedByDescending { it.updatedAt }
                    val nextCircleId = circleId ?: uniqueCircles.firstOrNull()?.id
                    activeCircleId = nextCircleId
                    _state.update { it.copy(circles = uniqueCircles, activeCircleId = nextCircleId) }
                    loadEvents(userId, nextCircleId)
                    if (!nextCircleId.isNullOrBlank()) loadDeletePolls(userId, nextCircleId)
                    else _state.update { it.copy(deletePolls = emptyList()) }
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Drive circles could not load") } }
            )
            repository.fetchChatContacts(context.applicationContext, userId).fold(
                onSuccess = { contacts -> _state.update { it.copy(chatContacts = contacts) } },
                onFailure = { /* Keep contact picker empty rather than showing unknown users. */ }
            )
            refreshFavorites(userId)
        }
    }

    fun loadEvents(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId) {
        if (userId.isBlank()) return
        activeCircleId = circleId?.ifBlank { null }
        _state.update { it.copy(activeCircleId = activeCircleId) }
        viewModelScope.launch {
            repository.fetchEvents(userId, activeCircleId).fold(
                onSuccess = { events ->
                    val nextEventId = activeEventId?.takeIf { id -> events.any { it.id == id } }
                    activeEventId = nextEventId
                    _state.update { it.copy(events = events, activeEventId = nextEventId) }
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Drive events could not load") } }
            )
        }
    }

    fun refreshFavorites(userId: String = activeUserId.orEmpty()) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            repository.fetchFavorites(userId).fold(
                onSuccess = { favorites -> _state.update { it.copy(favoriteIds = favorites.toSet()) } },
                onFailure = { error -> HelloDebugLog.w("DriveVm", "refreshFavorites failure error=${error.message}", error) }
            )
        }
    }

    fun toggleFavorite(userId: String, itemId: String, favorite: Boolean) {
        if (userId.isBlank() || itemId.isBlank()) return
        viewModelScope.launch {
            repository.setFavorite(userId, itemId, favorite).fold(
                onSuccess = {
                    _state.update {
                        val next = it.favoriteIds.toMutableSet()
                        if (favorite) next.add(itemId) else next.remove(itemId)
                        it.copy(favoriteIds = next)
                    }
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Favorite could not be updated") } }
            )
        }
    }

    fun createEvent(name: String, userId: String, circleId: String, onCreated: (DriveEvent) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(error = "Enter an event name") }
            return
        }
        viewModelScope.launch {
            repository.createEvent(cleanName, userId, circleId).fold(
                onSuccess = { event ->
                    activeCircleId = circleId
                    activeEventId = event.id
                    _state.update { current ->
                        current.copy(
                            events = (listOf(event) + current.events.filterNot { it.id == event.id }),
                            activeCircleId = circleId,
                            activeEventId = event.id,
                            infoMessage = "Event created."
                        )
                    }
                    onCreated(event)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Event could not be created") } }
            )
        }
    }

    fun renameEvent(eventId: String, userId: String, name: String, onRenamed: (DriveEvent) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(error = "Enter an event name") }
            return
        }
        viewModelScope.launch {
            repository.renameEvent(eventId, userId, cleanName).fold(
                onSuccess = { event ->
                    _state.update { current ->
                        current.copy(
                            events = current.events.map { if (it.id == event.id) event else it },
                            items = current.items.map { if (it.eventId == event.id) it.copy(eventName = event.name) else it },
                            trashItems = current.trashItems.map { if (it.eventId == event.id) it.copy(eventName = event.name) else it },
                            infoMessage = "Event renamed."
                        )
                    }
                    onRenamed(event)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Event could not be renamed") } }
            )
        }
    }

    fun deleteEvent(eventId: String, userId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteEvent(eventId, userId).fold(
                onSuccess = {
                    if (activeEventId == eventId) activeEventId = null
                    _state.update {
                        it.copy(
                            events = it.events.filterNot { event -> event.id == eventId },
                            activeEventId = if (it.activeEventId == eventId) null else it.activeEventId,
                            infoMessage = "Event deleted."
                        )
                    }
                    onDeleted()
                },
                onFailure = { error ->
                    val pollRequired = (error as? DrivePcApiClient.DriveApiException)?.let {
                        it.code == 409 && it.responseBody.contains("\"requiresPoll\":true")
                    } == true
                    if (pollRequired) {
                        val circleId = activeCircleId
                        repository.createDeletePoll(userId, "event", eventId, circleId).fold(
                            onSuccess = { poll ->
                                _state.update { current ->
                                    current.copy(
                                        deletePolls = listOf(poll) + current.deletePolls.filterNot { it.id == poll.id },
                                        infoMessage = "Delete poll started."
                                    )
                                }
                            },
                            onFailure = { pollError ->
                                _state.update { it.copy(error = pollError.message ?: "Delete poll could not be created") }
                            }
                        )
                    } else {
                        _state.update { it.copy(error = error.message ?: "Event could not be deleted") }
                    }
                }
            )
        }
    }

    fun createCircle(id: String? = null, name: String, ownerUserId: String, members: List<DriveCircleMember>, onCreated: (DriveCircle) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(error = "Enter a circle name") }
            return
        }
        viewModelScope.launch {
            repository.createCircle(id, cleanName, ownerUserId, members).fold(
                onSuccess = { circle ->
                    activeCircleId = circle.id
                    _state.update { current ->
                        current.copy(
                            circles = (listOf(circle) + current.circles.filterNot { it.id == circle.id }).sortedByDescending { it.updatedAt },
                            activeCircleId = circle.id,
                            infoMessage = if (id.isNullOrBlank()) "Circle created." else "Circle updated."
                        )
                    }
                    loadEvents(ownerUserId, circle.id)
                    loadDeletePolls(ownerUserId, circle.id)
                    onCreated(circle)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Circle could not be created") } }
            )
        }
    }

    fun leaveCircle(circleId: String, userId: String, onDone: () -> Unit = {}) {
        if (circleId.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            repository.leaveCircle(circleId, userId).fold(
                onSuccess = {
                    val remainingCircles = _state.value.circles.filterNot { it.id == circleId }
                    val nextCircleId = remainingCircles.firstOrNull()?.id
                    activeCircleId = nextCircleId
                    activeEventId = null
                    _state.update {
                        it.copy(
                            circles = remainingCircles,
                            events = emptyList(),
                            items = emptyList(),
                            trashItems = emptyList(),
                            deletePolls = emptyList(),
                            activeCircleId = nextCircleId,
                            activeEventId = null,
                            infoMessage = "Left circle."
                        )
                    }
                    if (!nextCircleId.isNullOrBlank()) {
                        loadEvents(userId, nextCircleId)
                        loadDeletePolls(userId, nextCircleId)
                        refresh(userId, nextCircleId, null)
                    }
                    onDone()
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Circle could not be left") } }
            )
        }
    }

    fun deleteCircle(circleId: String, userId: String, onDone: () -> Unit = {}) {
        if (circleId.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            repository.deleteCircle(circleId, userId).fold(
                onSuccess = {
                    val remainingCircles = _state.value.circles.filterNot { it.id == circleId }
                    val nextCircleId = remainingCircles.firstOrNull()?.id
                    activeCircleId = nextCircleId
                    activeEventId = null
                    _state.update {
                        it.copy(
                            circles = remainingCircles,
                            events = emptyList(),
                            items = emptyList(),
                            trashItems = emptyList(),
                            deletePolls = emptyList(),
                            activeCircleId = nextCircleId,
                            activeEventId = null,
                            infoMessage = "Circle deleted."
                        )
                    }
                    if (!nextCircleId.isNullOrBlank()) {
                        loadEvents(userId, nextCircleId)
                        loadDeletePolls(userId, nextCircleId)
                        refresh(userId, nextCircleId, null)
                    }
                    onDone()
                },
                onFailure = { error ->
                    val pollRequired = (error as? DrivePcApiClient.DriveApiException)?.let {
                        it.code == 409 && it.responseBody.contains("\"requiresPoll\":true")
                    } == true
                    if (pollRequired) {
                        repository.createDeletePoll(userId, "circle", circleId, circleId).fold(
                            onSuccess = { poll ->
                                _state.update { current ->
                                    current.copy(
                                        deletePolls = listOf(poll) + current.deletePolls.filterNot { it.id == poll.id },
                                        infoMessage = "Delete poll started."
                                    )
                                }
                            },
                            onFailure = { pollError ->
                                _state.update { it.copy(error = pollError.message ?: "Delete poll could not be created") }
                            }
                        )
                    } else {
                        _state.update { it.copy(error = error.message ?: "Circle could not be deleted") }
                    }
                }
            )
        }
    }

    fun loadDeletePolls(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId) {
        if (userId.isBlank() || circleId.isNullOrBlank()) {
            _state.update { it.copy(deletePolls = emptyList()) }
            return
        }
        viewModelScope.launch {
            repository.fetchDeletePolls(userId, circleId).fold(
                onSuccess = { polls -> _state.update { it.copy(deletePolls = polls) } },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Delete polls could not load") } }
            )
        }
    }

    fun startDeletePoll(userId: String, targetType: String, targetId: String, circleId: String? = activeCircleId, onDone: (DriveDeletePoll) -> Unit = {}) {
        if (userId.isBlank() || targetType.isBlank() || targetId.isBlank()) return
        viewModelScope.launch {
            repository.createDeletePoll(userId, targetType, targetId, circleId).fold(
                onSuccess = { poll ->
                    _state.update { current ->
                        current.copy(
                            deletePolls = listOf(poll) + current.deletePolls.filterNot { it.id == poll.id },
                            infoMessage = "Delete poll started."
                        )
                    }
                    onDone(poll)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Delete poll could not be created") } }
            )
        }
    }

    fun voteDeletePoll(pollId: String, userId: String, vote: String, onDone: (DriveDeletePoll) -> Unit = {}) {
        if (pollId.isBlank() || userId.isBlank() || vote.isBlank()) return
        viewModelScope.launch {
            repository.voteDeletePoll(pollId, userId, vote).fold(
                onSuccess = { poll ->
                    _state.update { current ->
                        current.copy(
                            deletePolls = current.deletePolls.map { if (it.id == poll.id) poll else it },
                            infoMessage = "Vote recorded."
                        )
                    }
                    onDone(poll)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message ?: "Vote could not be recorded") } }
            )
        }
    }

    fun loadMore(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId, eventId: String? = activeEventId) {
        val current = _state.value
        if (userId.isBlank() || current.isLoading || current.isLoadingMore || !current.hasMore || current.nextCursor == null) return
        HelloDebugLog.d("DriveVm", "loadMore before=${current.nextCursor}")
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchItems(
                userId = userId,
                limit = pageSize,
                before = current.nextCursor,
                circleId = circleId,
                eventId = eventId
            )
            _state.update { latest ->
                result.fold(
                    onSuccess = { response ->
                        HelloDebugLog.d("DriveVm", "loadMore success items=${response.items.size} next=${response.nextCursor} hasMore=${response.hasMore}")
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
                        HelloDebugLog.w("DriveVm", "loadMore failure error=${error.message}", error)
                        latest.copy(isLoadingMore = false, error = error.message ?: "More photos could not load")
                    }
                )
            }
        }
    }

    fun loadMoreTrash(userId: String = activeUserId.orEmpty(), circleId: String? = activeCircleId, eventId: String? = activeEventId) {
        val current = _state.value
        if (userId.isBlank() || current.isTrashLoading || current.isTrashLoadingMore || !current.trashHasMore || current.trashNextCursor == null) return
        HelloDebugLog.d("DriveVm", "loadMoreTrash before=${current.trashNextCursor}")
        _state.update { it.copy(isTrashLoadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchTrash(
                userId = userId,
                limit = pageSize,
                before = current.trashNextCursor,
                circleId = circleId,
                eventId = eventId
            )
            _state.update { latest ->
                result.fold(
                    onSuccess = { response ->
                        HelloDebugLog.d("DriveVm", "loadMoreTrash success items=${response.items.size} next=${response.nextCursor} hasMore=${response.hasMore}")
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
                        HelloDebugLog.w("DriveVm", "loadMoreTrash failure error=${error.message}", error)
                        latest.copy(isTrashLoadingMore = false, error = error.message ?: "More trash could not load")
                    }
                )
            }
        }
    }

    fun upload(context: Context, uploaderId: String, uris: List<Uri>, plan: DriveUploadPlan = DriveUploadPlan()) {
        if (uris.isEmpty() || _state.value.isUploading) return
        HelloDebugLog.d("DriveVm", "upload uploaderId=$uploaderId count=${uris.size} eventId=${plan.eventId}")
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
                    HelloDebugLog.d("DriveVm", "upload success synced=${outcome.syncedItems.size} pending=${outcome.pendingItems.size}")
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
                    refresh(uploaderId)
                },
                onFailure = { error ->
                    HelloDebugLog.w("DriveVm", "upload failure error=${error.message}", error)
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

    fun moveItemsToTrash(userId: String, itemIds: Set<String>, securityAnswer: String, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        HelloDebugLog.d("DriveVm", "moveItemsToTrash userId=$userId count=${itemIds.size}")
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                var latestLimit: DriveDeleteLimit? = null
                itemIds.forEach { itemId ->
                    latestLimit = repository.deleteItem(itemId, userId, securityAnswer).getOrThrow().deleteLimit
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
                HelloDebugLog.w("DriveVm", "moveItemsToTrash failure error=${error.message}", error)
                _state.update { it.copy(isBusy = false, error = error.message ?: "Delete failed") }
            }
        }
    }

    fun restoreItems(userId: String, itemIds: Set<String>, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        HelloDebugLog.d("DriveVm", "restoreItems count=${itemIds.size}")
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                val restored = itemIds.map { repository.restoreItem(it, userId).getOrThrow() }
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
                HelloDebugLog.w("DriveVm", "restoreItems failure error=${error.message}", error)
                _state.update { it.copy(isBusy = false, error = error.message ?: "Restore failed") }
            }
        }
    }

    fun permanentlyDeleteItems(userId: String, itemIds: Set<String>, onDone: () -> Unit = {}) {
        if (itemIds.isEmpty() || _state.value.isBusy) return
        HelloDebugLog.d("DriveVm", "permanentlyDeleteItems count=${itemIds.size}")
        _state.update { it.copy(isBusy = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                itemIds.forEach { repository.permanentlyDeleteItem(it, userId).getOrThrow() }
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
                HelloDebugLog.w("DriveVm", "permanentlyDeleteItems failure error=${error.message}", error)
                _state.update { it.copy(isBusy = false, error = error.message ?: "Permanent delete failed") }
            }
        }
    }

    fun retryPending(context: Context, uploaderId: String, itemId: String? = null) {
        if (uploaderId.isBlank() || _state.value.retryingPendingId != null) return
        HelloDebugLog.d("DriveVm", "retryPending uploaderId=$uploaderId itemId=$itemId")
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
                    HelloDebugLog.w("DriveVm", "retryPending failure error=${error.message}", error)
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
        HelloDebugLog.d("DriveVm", "removePending itemId=$itemId")
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
                    HelloDebugLog.w("DriveVm", "removePending failure error=${error.message}", error)
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
