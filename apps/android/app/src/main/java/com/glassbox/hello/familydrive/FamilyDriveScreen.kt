package com.glassbox.hello.familydrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.glassbox.hello.chat.components.downloadAttachment
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DriveMode {
    Home,
    Circles,
    Events,
    AllPhotos,
    Trash
}

private const val DRIVE_CELL_RESTORE_SETTLE_MS = 220L

private enum class DriveUploadStep {
    SelectPhotos,
    ChooseEvent,
    ChooseAudiences,
    SortPhotos,
    ChoosePeople,
    UploadSummary,
    PendingUploads,
    EventView,
    Circles,
    EditCircle,
    Success
}

private enum class DriveActionType {
    Trash,
    Restore,
    PermanentDelete,
    RemovePending
}

private data class SelectedDriveMedia(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

private data class DriveAudienceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val privateOnly: Boolean = false,
    val custom: Boolean = false,
    val circle: DriveCircle? = null
)

private data class DrivePendingAction(
    val type: DriveActionType,
    val itemIds: Set<String>,
    val pendingItem: PendingDriveItem? = null
)

private sealed class DriveGridItem {
    abstract val id: String
    abstract val createdAt: Long
    abstract val monthLabel: String

    data class Synced(val item: DriveItem) : DriveGridItem() {
        override val id: String = item.id
        override val createdAt: Long = item.createdAt
        override val monthLabel: String = item.monthLabel ?: monthLabelFromTimestamp(item.createdAt)
    }

    data class Pending(val item: PendingDriveItem) : DriveGridItem() {
        override val id: String = item.id
        override val createdAt: Long = item.createdAt
        override val monthLabel: String = item.monthLabel
    }
}

@Composable
fun FamilyDriveScreen(
    currentUserId: String,
    modifier: Modifier = Modifier,
    viewModel: FamilyDriveViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(DriveMode.Home) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerTrashMode by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<DrivePendingAction?>(null) }
    var uploadStep by remember { mutableStateOf<DriveUploadStep?>(null) }
    var editingCircle by remember { mutableStateOf<DriveCircle?>(null) }
    var selectedUploadMedia by remember { mutableStateOf<List<SelectedDriveMedia>>(emptyList()) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var selectedEventName by remember { mutableStateOf("") }
    var selectedAudienceIds by remember { mutableStateOf(setOf<String>()) }
    var customPeopleIds by remember { mutableStateOf(setOf<String>()) }
    var sortAssignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var avatarCircleForUpload by remember { mutableStateOf<DriveCircle?>(null) }
    var activeSortAudienceId by remember { mutableStateOf("only_me") }
    var deleteSecurityAnswer by remember { mutableStateOf("") }
    val viewerItems = if (viewerTrashMode) state.trashItems else state.items
    val viewerItem = viewerIndex?.let { viewerItems.getOrNull(it) }
    val activeSyncedItems = if (mode == DriveMode.Trash) state.trashItems else state.items

    fun toggleSelection(itemId: String) {
        selectedIds = selectedIds.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }.toSet()
    }

    fun selectMonth(monthItems: List<DriveGridItem>) {
        val syncedIds = monthItems.filterIsInstance<DriveGridItem.Synced>().map { it.item.id }
        if (syncedIds.isEmpty()) return
        val next = selectedIds.toMutableSet()
        val allSelected = syncedIds.all { it in next }
        syncedIds.forEach { if (allSelected) next.remove(it) else next.add(it) }
        selectedIds = next
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val pickedMedia = readSelectedDriveMedia(context, uris)
            selectedUploadMedia = if (uploadStep == null) {
                selectedEventId = null
                selectedEventName = ""
                selectedAudienceIds = emptySet()
                customPeopleIds = emptySet()
                sortAssignments = emptyMap()
                activeSortAudienceId = "only_me"
                pickedMedia
            } else {
                (selectedUploadMedia + pickedMedia).distinctBy { it.uri.toString() }
            }
            uploadStep = DriveUploadStep.SelectPhotos
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val circle = avatarCircleForUpload
        if (uri != null && circle != null) {
            viewModel.uploadCircleAvatar(context, circle.id, currentUserId, uri)
        }
        avatarCircleForUpload = null
    }
    val openPicker = {
        mediaPicker.launch(arrayOf("image/*", "video/*"))
    }

    LaunchedEffect(currentUserId) {
        viewModel.startPendingObserver(context)
        viewModel.setScope(currentUserId)
        viewModel.refresh(currentUserId)
        viewModel.refreshDeleteLimit(currentUserId)
        viewModel.refreshDriveSetup(context, currentUserId)
        viewModel.retryPending(context, currentUserId)
    }

    LaunchedEffect(mode) {
        selectedIds = emptySet()
        viewerIndex = null
        when (mode) {
            DriveMode.Trash -> viewModel.refreshTrash(currentUserId, state.activeCircleId, null)
            DriveMode.Home, DriveMode.AllPhotos -> viewModel.refresh(currentUserId, state.activeCircleId, state.activeEventId)
            DriveMode.Circles -> viewModel.refreshDriveSetup(context, currentUserId, state.activeCircleId)
            DriveMode.Events -> viewModel.loadEvents(currentUserId, state.activeCircleId)
        }
    }

    LaunchedEffect(activeSyncedItems) {
        val activeIds = activeSyncedItems.map { it.id }.toSet()
        if (selectedIds.any { it !in activeIds }) {
            selectedIds = selectedIds.intersect(activeIds)
        }
    }

    fun closeUploadFlow() {
        uploadStep = null
        editingCircle = null
        selectedUploadMedia = emptyList()
        sortAssignments = emptyMap()
        customPeopleIds = emptySet()
    }

    fun buildUploadPlan(): DriveUploadPlan {
        val chosenCircleIds = selectedAudienceIds.toList()
        val breakdown = chosenCircleIds.associateWith { selectedUploadMedia.size }
        return DriveUploadPlan(
            eventName = selectedEventName,
            eventId = selectedEventId,
            circleIds = chosenCircleIds,
            allowedUserIds = emptyList(),
            audienceBreakdown = breakdown
        )
    }

    fun openCirclePhotos(circle: DriveCircle) {
        editingCircle = null
        selectedEventId = null
        selectedEventName = ""
        viewModel.setScope(currentUserId, circle.id, null)
        viewModel.loadEvents(currentUserId, circle.id)
        viewModel.refresh(currentUserId, circle.id, null)
        mode = DriveMode.AllPhotos
    }

    fun openCircleEvents(circle: DriveCircle) {
        editingCircle = null
        selectedEventId = null
        selectedEventName = ""
        viewModel.setScope(currentUserId, circle.id, null)
        viewModel.loadEvents(currentUserId, circle.id)
        mode = DriveMode.Events
    }

    BackHandler(enabled = uploadStep != null || pendingAction != null || viewerIndex != null || selectedIds.isNotEmpty() || mode != DriveMode.Home) {
        when {
            uploadStep != null -> closeUploadFlow()
            pendingAction != null -> pendingAction = null
            viewerIndex != null -> viewerIndex = null
            selectedIds.isNotEmpty() -> selectedIds = emptySet()
            mode == DriveMode.Events -> mode = DriveMode.Circles
            mode == DriveMode.Circles && editingCircle != null -> {
                editingCircle = null
            }
            mode != DriveMode.Home -> mode = DriveMode.Home
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            DriveMode.Home -> DriveHomeContent(
                state = state,
                onUploadClick = openPicker,
                onOpenAllPhotos = { mode = DriveMode.AllPhotos },
                onOpenCircles = {
                    editingCircle = null
                    mode = DriveMode.Circles
                },
                onOpenTrash = {
                    viewModel.setScope(currentUserId, state.activeCircleId, null)
                    selectedEventId = null
                    selectedEventName = ""
                    mode = DriveMode.Trash
                },
                modifier = Modifier.fillMaxSize()
            )
            DriveMode.Circles -> {
                if (editingCircle == null) {
                    CircleManagementStep(
                        activeUserId = currentUserId,
                        circles = state.circles,
                        onBack = { mode = DriveMode.Home },
                        onCreateCircle = {
                            editingCircle = null
                            mode = DriveMode.Circles
                            uploadStep = DriveUploadStep.EditCircle
                        },
                        onOpenCircle = { circle -> openCircleEvents(circle) },
                        onManageCircle = { circle ->
                            editingCircle = circle
                            mode = DriveMode.Circles
                            uploadStep = DriveUploadStep.EditCircle
                        },
                        onUploadCircleAvatar = { circle ->
                            avatarCircleForUpload = circle
                            avatarPicker.launch("image/*")
                        },
                        onDeleteOrLeaveCircle = { circle ->
                            val isOwner = circle.ownerUserId.isNullOrBlank() || circle.ownerUserId == currentUserId
                            if (isOwner) {
                                viewModel.deleteCircle(context, circle.id, currentUserId) {
                                    if (editingCircle?.id == circle.id) editingCircle = null
                                }
                            } else {
                                viewModel.startDeletePoll(currentUserId, "circle", circle.id, circle.id)
                            }
                        }
                    )
                }
            }
            DriveMode.Events -> DriveEventManagementScreen(
                state = state,
                onBack = { mode = DriveMode.Circles },
                onOpenCircleChooser = {
                    editingCircle = null
                    mode = DriveMode.Circles
                },
                onChooseEvent = { event ->
                    selectedEventId = event.id
                    selectedEventName = event.name
                    viewModel.setScope(currentUserId, state.activeCircleId, event.id)
                    viewModel.refresh(currentUserId, state.activeCircleId, event.id)
                    mode = DriveMode.AllPhotos
                },
                onCreateEvent = { name ->
                    val circleId = state.activeCircleId
                    if (!circleId.isNullOrBlank()) {
                        viewModel.createEvent(context, name, currentUserId, circleId) { event ->
                            selectedEventId = event.id
                            selectedEventName = event.name
                        }
                    }
                },
                onRenameEvent = { eventId, name ->
                    viewModel.renameEvent(context, eventId, currentUserId, name) { event ->
                        if (selectedEventId == event.id) selectedEventName = event.name
                    }
                },
                onDeleteEvent = { eventId ->
                    viewModel.deleteEvent(context, eventId, currentUserId) {
                        if (selectedEventId == eventId) {
                            selectedEventId = null
                            selectedEventName = ""
                        }
                    }
                },
                onOpenCircleMedia = {
                    val activeCircle = state.circles.firstOrNull { it.id == state.activeCircleId }
                    if (activeCircle != null) openCirclePhotos(activeCircle) else mode = DriveMode.AllPhotos
                },
                modifier = Modifier.fillMaxSize()
            )
            DriveMode.AllPhotos -> DriveLibraryContent(
                mode = mode,
                state = state,
                selectedIds = selectedIds,
                favoriteIds = state.favoriteIds,
                activeCircleId = state.activeCircleId,
                activeEventId = state.activeEventId,
                onBack = {
                    mode = if (!state.activeCircleId.isNullOrBlank()) {
                        DriveMode.Events
                    } else {
                        DriveMode.Home
                    }
                },
                onRefresh = { viewModel.refresh(currentUserId, state.activeCircleId, state.activeEventId) },
                onRetryPending = { viewModel.retryPending(context, currentUserId) },
                onLoadMore = { viewModel.loadMore(currentUserId, state.activeCircleId, state.activeEventId) },
                onOpenItem = { item ->
                    viewerTrashMode = false
                    viewerIndex = state.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                },
                onOpenPending = { openLocalPendingMedia(context, it) },
                onToggleFavorite = { itemId ->
                    viewModel.toggleFavorite(currentUserId, itemId, itemId !in state.favoriteIds)
                },
                onToggleSelection = { toggleSelection(it) },
                onSelectMonth = { selectMonth(it) },
                onRetryPendingItem = { viewModel.retryPending(context, currentUserId, it) },
                onRemovePendingItem = { pendingAction = DrivePendingAction(DriveActionType.RemovePending, emptySet(), it) },
                onUploadClick = openPicker,
                modifier = Modifier.fillMaxSize()
            )
            DriveMode.Trash -> DriveLibraryContent(
                mode = mode,
                state = state,
                selectedIds = selectedIds,
                favoriteIds = state.favoriteIds,
                activeCircleId = state.activeCircleId,
                activeEventId = null,
                onBack = { mode = DriveMode.Home },
                onRefresh = { viewModel.refreshTrash(currentUserId, state.activeCircleId, null) },
                onRetryPending = {},
                onLoadMore = { viewModel.loadMoreTrash(currentUserId, state.activeCircleId, null) },
                onOpenItem = { item ->
                    viewerTrashMode = true
                    viewerIndex = state.trashItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                },
                onOpenPending = {},
                onToggleFavorite = {},
                onToggleSelection = { toggleSelection(it) },
                onSelectMonth = { selectMonth(it) },
                onRetryPendingItem = {},
                onRemovePendingItem = {},
                onUploadClick = openPicker,
                modifier = Modifier.fillMaxSize()
            )
        }

        uploadStep?.let { step ->
            DriveUploadFlow(
                activeUserId = currentUserId,
                step = step,
                media = selectedUploadMedia,
                selectedEventName = selectedEventName,
                selectedAudienceIds = selectedAudienceIds,
                customPeopleIds = customPeopleIds,
                sortAssignments = sortAssignments,
                activeAudienceId = activeSortAudienceId,
                state = state,
                onBack = {
                    uploadStep = when (step) {
                        DriveUploadStep.SelectPhotos -> null
                        DriveUploadStep.ChooseAudiences -> DriveUploadStep.SelectPhotos
                        DriveUploadStep.ChooseEvent -> DriveUploadStep.ChooseAudiences
                        DriveUploadStep.SortPhotos -> DriveUploadStep.ChooseAudiences
                        DriveUploadStep.ChoosePeople -> DriveUploadStep.ChooseAudiences
                        DriveUploadStep.UploadSummary -> DriveUploadStep.ChooseEvent
                        DriveUploadStep.PendingUploads -> DriveUploadStep.UploadSummary
                        DriveUploadStep.EventView -> DriveUploadStep.UploadSummary
                        DriveUploadStep.Circles -> DriveUploadStep.SelectPhotos
                        DriveUploadStep.EditCircle -> DriveUploadStep.Circles
                        DriveUploadStep.Success -> DriveUploadStep.EventView
                    }
                },
                onClose = { closeUploadFlow() },
                onAddMorePhotos = openPicker,
                onNextFromSelection = { uploadStep = DriveUploadStep.ChooseAudiences },
                onChooseEvent = {
                    selectedEventId = it.id
                    selectedEventName = it.name
                    viewModel.setScope(currentUserId, selectedAudienceIds.firstOrNull(), it.id)
                    uploadStep = DriveUploadStep.UploadSummary
                },
                onCreateEvent = { name ->
                    val circleId = selectedAudienceIds.firstOrNull()
                    if (!circleId.isNullOrBlank()) {
                        viewModel.createEvent(context, name, currentUserId, circleId) { event ->
                            selectedEventId = event.id
                            selectedEventName = event.name
                            viewModel.setScope(currentUserId, circleId, event.id)
                            uploadStep = DriveUploadStep.UploadSummary
                        }
                    }
                },
                onRenameEvent = { eventId, name ->
                    viewModel.renameEvent(context, eventId, currentUserId, name) { event ->
                        if (selectedEventId == event.id) selectedEventName = event.name
                    }
                },
                onDeleteEvent = { eventId ->
                    viewModel.deleteEvent(context, eventId, currentUserId) {
                        if (selectedEventId == eventId) {
                            selectedEventId = null
                            selectedEventName = ""
                        }
                    }
                },
                onToggleAudience = { audience ->
                    val nextAudiences = if (selectedAudienceIds.contains(audience.id)) emptySet() else setOf(audience.id)
                    selectedAudienceIds = nextAudiences
                    activeSortAudienceId = nextAudiences.firstOrNull().orEmpty()
                    viewModel.loadEvents(currentUserId, nextAudiences.firstOrNull())
                },
                onDonePeople = { people ->
                    customPeopleIds = people
                    selectedAudienceIds = if (people.isEmpty()) selectedAudienceIds - "choose_people" else selectedAudienceIds + "choose_people"
                    uploadStep = DriveUploadStep.ChooseAudiences
                },
                onContinueAudiences = {
                    viewModel.loadEvents(currentUserId, selectedAudienceIds.firstOrNull())
                    uploadStep = DriveUploadStep.ChooseEvent
                },
                onSetActiveAudience = { activeSortAudienceId = it },
                onAssignSelected = { mediaIds ->
                    sortAssignments = sortAssignments + mediaIds.associateWith { activeSortAudienceId }
                    val nextAudience = selectedAudienceIds.firstOrNull { id -> id != activeSortAudienceId && sortAssignments.values.count { it == id } == 0 }
                    if (nextAudience != null) activeSortAudienceId = nextAudience
                },
                onSetRemaining = {
                    val remaining = selectedUploadMedia.map { it.id }.filterNot { sortAssignments.containsKey(it) }
                    sortAssignments = sortAssignments + remaining.associateWith { activeSortAudienceId }
                },
                onReviewSummary = { uploadStep = DriveUploadStep.UploadSummary },
                onSubmit = {
                    viewModel.upload(context, currentUserId, selectedUploadMedia.map { it.uri }, buildUploadPlan())
                    uploadStep = DriveUploadStep.PendingUploads
                },
                onOpenPending = { uploadStep = DriveUploadStep.PendingUploads },
                onOpenEvent = { uploadStep = DriveUploadStep.EventView },
                onOpenCircles = {
                    editingCircle = null
                    mode = DriveMode.Circles
                    uploadStep = null
                },
                onEditCircle = {
                    editingCircle = null
                    uploadStep = DriveUploadStep.EditCircle
                },
                onOpenCircle = { circle ->
                    openCircleEvents(circle)
                    uploadStep = null
                },
                onManageCircle = { circle ->
                    editingCircle = circle
                    uploadStep = DriveUploadStep.EditCircle
                },
                onUploadCircleAvatar = { circle ->
                    avatarCircleForUpload = circle
                    avatarPicker.launch("image/*")
                },
                editingCircle = editingCircle,
                onCreateCircle = { circleId, name, members ->
                    viewModel.createCircle(context, circleId, name, currentUserId, members) {
                        editingCircle = null
                        uploadStep = DriveUploadStep.Circles
                    }
                },
                onDeleteCircle = { circle ->
                    viewModel.deleteCircle(context, circle.id, currentUserId) {
                        if (editingCircle?.id == circle.id) editingCircle = null
                        uploadStep = DriveUploadStep.Circles
                    }
                },
                onLeaveCircle = { circle ->
                    viewModel.leaveCircle(circle.id, currentUserId) {
                        if (editingCircle?.id == circle.id) editingCircle = null
                        uploadStep = DriveUploadStep.Circles
                    }
                },
                onSuccess = { uploadStep = DriveUploadStep.Success },
                onRetryPending = { viewModel.retryPending(context, currentUserId) },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (selectedIds.isNotEmpty()) {
            SelectionBar(
                selectedCount = selectedIds.size,
                totalCount = activeSyncedItems.size,
                trashMode = mode == DriveMode.Trash,
                onSelectAll = { selectedIds = activeSyncedItems.map { it.id }.toSet() },
                onClear = { selectedIds = emptySet() },
                onTrash = { pendingAction = DrivePendingAction(DriveActionType.Trash, selectedIds) },
                onRestore = { pendingAction = DrivePendingAction(DriveActionType.Restore, selectedIds) },
                onPermanentDelete = { pendingAction = DrivePendingAction(DriveActionType.PermanentDelete, selectedIds) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HelloSpacing.Md)
            )
        }

        viewerItem?.let { item ->
            DriveMediaViewer(
                item = item,
                isFavorite = state.favoriteIds.contains(item.id),
                trashMode = viewerTrashMode,
                hasPrevious = (viewerIndex ?: 0) > 0,
                hasNext = (viewerIndex ?: 0) < viewerItems.lastIndex,
                onClose = { viewerIndex = null },
                onPrevious = { viewerIndex = ((viewerIndex ?: 0) - 1).coerceAtLeast(0) },
                onNext = { viewerIndex = ((viewerIndex ?: 0) + 1).coerceAtMost(viewerItems.lastIndex) },
                onToggleFavorite = { viewModel.toggleFavorite(currentUserId, item.id, item.id !in state.favoriteIds) },
                onDelete = { pendingAction = DrivePendingAction(DriveActionType.Trash, setOf(item.id)) },
                onRestore = { pendingAction = DrivePendingAction(DriveActionType.Restore, setOf(item.id)) },
                onPermanentDelete = { pendingAction = DrivePendingAction(DriveActionType.PermanentDelete, setOf(item.id)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        state.infoMessage?.let { message ->
            InfoBanner(
                message = message,
                onDismiss = { viewModel.clearInfoMessage() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HelloSpacing.Lg)
            )
        }

        state.error?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HelloSpacing.Lg)
            )
        }

        pendingAction?.let { action ->
            DriveActionDialog(
                action = action,
                deleteLimit = state.lastDeleteLimit,
                securityAnswer = deleteSecurityAnswer,
                isBusy = state.isBusy,
                onSecurityAnswerChange = { deleteSecurityAnswer = it },
                onCancel = {
                    pendingAction = null
                    deleteSecurityAnswer = ""
                },
                onConfirm = {
                    when (action.type) {
                        DriveActionType.Trash -> viewModel.moveItemsToTrash(currentUserId, action.itemIds, deleteSecurityAnswer) {
                            selectedIds = emptySet()
                            viewerIndex = null
                            pendingAction = null
                            deleteSecurityAnswer = ""
                        }
                        DriveActionType.Restore -> viewModel.restoreItems(currentUserId, action.itemIds) {
                            selectedIds = emptySet()
                            viewerIndex = null
                            pendingAction = null
                        }
                        DriveActionType.PermanentDelete -> viewModel.permanentlyDeleteItems(currentUserId, action.itemIds) {
                            selectedIds = emptySet()
                            viewerIndex = null
                            pendingAction = null
                        }
                        DriveActionType.RemovePending -> {
                            action.pendingItem?.let { viewModel.removePending(context, it.id) }
                            pendingAction = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DriveUploadFlow(
    activeUserId: String,
    step: DriveUploadStep,
    media: List<SelectedDriveMedia>,
    selectedEventName: String,
    selectedAudienceIds: Set<String>,
    customPeopleIds: Set<String>,
    sortAssignments: Map<String, String>,
    activeAudienceId: String,
    state: FamilyDriveUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAddMorePhotos: () -> Unit,
    onNextFromSelection: () -> Unit,
    onChooseEvent: (DriveEvent) -> Unit,
    onCreateEvent: (String) -> Unit,
    onRenameEvent: (String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onToggleAudience: (DriveAudienceOption) -> Unit,
    onDonePeople: (Set<String>) -> Unit,
    onContinueAudiences: () -> Unit,
    onSetActiveAudience: (String) -> Unit,
    onAssignSelected: (Set<String>) -> Unit,
    onSetRemaining: () -> Unit,
    onReviewSummary: () -> Unit,
    onSubmit: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenCircles: () -> Unit,
    onEditCircle: () -> Unit,
    onOpenCircle: (DriveCircle) -> Unit,
    onManageCircle: (DriveCircle) -> Unit,
    onUploadCircleAvatar: (DriveCircle) -> Unit,
    editingCircle: DriveCircle?,
    onCreateCircle: (String?, String, List<DriveCircleMember>) -> Unit,
    onDeleteCircle: (DriveCircle) -> Unit,
    onLeaveCircle: (DriveCircle) -> Unit,
    onSuccess: () -> Unit,
    onRetryPending: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(HelloColors.DarkBg)
            .padding(HelloSpacing.Md)
    ) {
        when (step) {
            DriveUploadStep.SelectPhotos -> SelectPhotosStep(media, onBack, onAddMorePhotos, onNextFromSelection)
            DriveUploadStep.ChooseEvent -> ChooseEventStep(state.events, selectedEventName, onBack, onChooseEvent, onCreateEvent, onRenameEvent, onDeleteEvent)
            DriveUploadStep.ChooseAudiences -> ChooseAudiencesStep(state.circles, selectedAudienceIds, customPeopleIds, onBack, onToggleAudience, onContinueAudiences, onEditCircle)
            DriveUploadStep.SortPhotos -> SortPhotosStep(
                media = media,
                circles = state.circles,
                selectedAudienceIds = selectedAudienceIds,
                customPeopleIds = customPeopleIds,
                sortAssignments = sortAssignments,
                activeAudienceId = activeAudienceId,
                onBack = onBack,
                onSetActiveAudience = onSetActiveAudience,
                onAssignSelected = onAssignSelected,
                onSetRemaining = onSetRemaining,
                onReviewSummary = onReviewSummary
            )
            DriveUploadStep.ChoosePeople -> ChoosePeopleStep(state.chatContacts, customPeopleIds, onBack, onDonePeople)
            DriveUploadStep.UploadSummary -> UploadSummaryStep(media, selectedEventName, state.circles, selectedAudienceIds, customPeopleIds, sortAssignments, state, onBack, onSubmit)
            DriveUploadStep.PendingUploads -> PendingUploadsStep(state, onBack, onRetryPending, onOpenEvent, onSuccess)
            DriveUploadStep.EventView -> UploadEventPreviewStep(media, selectedEventName, state.circles, sortAssignments, onBack, onOpenCircles)
            DriveUploadStep.Circles -> CircleManagementStep(
                activeUserId = activeUserId,
                circles = state.circles,
                onBack = onBack,
                onCreateCircle = onEditCircle,
                onOpenCircle = onOpenCircle,
                onManageCircle = onManageCircle,
                onUploadCircleAvatar = onUploadCircleAvatar,
                onDeleteOrLeaveCircle = onManageCircle
            )
            DriveUploadStep.EditCircle -> EditCircleStep(state.chatContacts, editingCircle, activeUserId = activeUserId, onBack, onCreateCircle, onDeleteCircle, onLeaveCircle, onUploadCircleAvatar)
            DriveUploadStep.Success -> UploadSuccessStep(onOpenEvent, onClose)
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).height(48.dp)) {
            Text("Close", color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Bold)
        }
        if (step != DriveUploadStep.PendingUploads && state.pendingItems.any { it.status != PendingDriveStatus.SYNCED }) {
            TextButton(onClick = onOpenPending, modifier = Modifier.align(Alignment.BottomEnd).height(48.dp)) {
                Text("Pending ${state.pendingItems.count { it.status != PendingDriveStatus.SYNCED }}", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FlowHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(end = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
            subtitle?.let {
                Text(it, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun EmptyInlineState(title: String, subtitle: String) {
    HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = HelloSpacing.Md), strong = false, shape = HelloShapes.Lg) {
        Column(modifier = Modifier.padding(HelloSpacing.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(HelloSpacing.Xs))
            Text(subtitle, color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectPhotosStep(media: List<SelectedDriveMedia>, onBack: () -> Unit, onAddMore: () -> Unit, onNext: () -> Unit) {
    val gridState = rememberLazyGridState()
    Column(modifier = Modifier.fillMaxSize()) {
        FlowHeader("Select Photos", "${media.size} selected", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(media, key = { it.id }) { item ->
                UploadMediaTile(
                    item = item,
                    selected = true,
                    lightweight = gridState.isScrollInProgress,
                    onClick = {}
                )
            }
        }
        FlowBottomButton(
            primary = "Next (${media.size})",
            secondary = "Add more",
            enabled = media.isNotEmpty(),
            onPrimary = onNext,
            onSecondary = onAddMore
        )
    }
}

@Composable
private fun ChooseEventStep(
    events: List<DriveEvent>,
    selectedEventName: String,
    onBack: () -> Unit,
    onChoose: (DriveEvent) -> Unit,
    onCreateEvent: (String) -> Unit,
    onRenameEvent: (String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    var eventName by remember { mutableStateOf("") }
    var editingEventId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (showHeader) {
            FlowHeader("Choose Event", "Where should these memories go?", onBack)
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
        }
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = HelloSpacing.Md), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Md)) {
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("New event name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(HelloSpacing.Sm))
                Button(
                    onClick = { onCreateEvent(eventName) },
                    enabled = eventName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                    shape = HelloShapes.Md
                ) {
                    Text("Create Event", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
                }
            }
        }
        if (events.isEmpty()) {
            EmptyInlineState("No events yet", "Create an event to keep this upload in its own PC folder.")
        } else {
            events.forEach { event ->
                EventManagementCard(
                    event = event,
                    selected = event.name == selectedEventName,
                    editing = editingEventId == event.id,
                    editingName = editingName,
                    onSelect = { onChoose(event) },
                    onStartRename = {
                        editingEventId = event.id
                        editingName = event.name
                    },
                    onEditingNameChange = { editingName = it },
                    onConfirmRename = {
                        onRenameEvent(event.id, editingName)
                        editingEventId = null
                        editingName = ""
                    },
                    onCancelRename = {
                        editingEventId = null
                        editingName = ""
                    },
                    onDelete = { onDeleteEvent(event.id) }
                )
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun EventManagementCard(
    event: DriveEvent,
    selected: Boolean,
    editing: Boolean,
    editingName: String,
    onSelect: () -> Unit,
    onStartRename: () -> Unit,
    onEditingNameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: () -> Unit
) {
    val isPending = event.syncStatus != PendingDriveEventStatus.SYNCED
    HelloPanel(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        strong = selected,
        shape = HelloShapes.Lg
    ) {
        Column(modifier = Modifier.padding(HelloSpacing.Md), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelect, role = Role.Button),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp).clip(HelloShapes.Md).background(HelloColors.DarkPanelStrong), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
                }
                Spacer(modifier = Modifier.width(HelloSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                        if (isPending) {
                            SmallStatusBadge(
                                text = if (event.syncStatus == PendingDriveEventStatus.FAILED_RETRYABLE) "Needs sync" else "Local",
                                tint = if (event.syncStatus == PendingDriveEventStatus.FAILED_RETRYABLE) HelloColors.DarkDanger else HelloColors.DarkAccent
                            )
                        }
                    }
                    Text(
                        if (isPending) "Saved locally - ${event.itemCount} items" else "${event.itemCount} items",
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HelloColors.DarkTextMuted)
            }
            if (editing) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditingNameChange,
                    label = { Text("Rename event") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirmRename,
                        enabled = editingName.isNotBlank(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                        shape = HelloShapes.Md
                    ) {
                        Text("Save", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
                    }
                    TextButton(onClick = onCancelRename, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Cancel", color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onStartRename, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Rename", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Delete", color = HelloColors.DarkDanger, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    HelloPanel(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick, role = Role.Button),
        strong = selected,
        shape = HelloShapes.Lg
    ) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(HelloShapes.Md).background(HelloColors.DarkPanelStrong), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(subtitle, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun ChooseAudiencesStep(
    circles: List<DriveCircle>,
    selectedIds: Set<String>,
    customPeopleIds: Set<String>,
    onBack: () -> Unit,
    onToggle: (DriveAudienceOption) -> Unit,
    onContinue: () -> Unit,
    onCreateCircle: () -> Unit
) {
    val audiences = circles.map { circle ->
        DriveAudienceOption(
            id = circle.id,
            title = circle.name,
            subtitle = buildCircleSubtitle(circle),
            circle = circle
        )
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FlowHeader("Choose Circle", "Upload into one circle, then choose an event.", onBack)
        Text("Select one circle.", color = HelloColors.DarkTextMuted, modifier = Modifier.padding(start = 48.dp, bottom = HelloSpacing.Md))
        if (circles.isEmpty()) {
            EmptyInlineState("No circles yet", "Create a circle from your Hello people before uploading.")
            Button(
                onClick = onCreateCircle,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = HelloSpacing.Md),
                colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                shape = HelloShapes.Md
            ) {
                Text("Create Circle", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
            }
        }
        audiences.forEach { audience ->
            val selected = audience.id in selectedIds
            AudienceChoiceCard(
                audience = audience,
                selected = selected,
                onClick = { onToggle(audience) }
            )
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Lg))
        Button(
            onClick = onContinue,
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
            shape = HelloShapes.Lg
        ) {
            Text("Continue", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
        TextButton(onClick = onCreateCircle, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Manage Circles", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun AudienceChoiceCard(audience: DriveAudienceOption, selected: Boolean, onClick: () -> Unit) {
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(1.dp, if (selected) HelloColors.DarkAccent else Color.Transparent, HelloShapes.Lg)
            .clickable(onClick = onClick, role = Role.Checkbox),
        strong = selected,
        shape = HelloShapes.Lg
    ) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(if (audience.privateOnly) HelloColors.DarkPanelStrong else HelloColors.DarkAccent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (audience.privateOnly) Icons.Default.Lock else Icons.Default.Favorite, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(audience.title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(audience.subtitle, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            SelectionMark(selected = selected)
        }
    }
}

@Composable
private fun SortPhotosStep(
    media: List<SelectedDriveMedia>,
    circles: List<DriveCircle>,
    selectedAudienceIds: Set<String>,
    customPeopleIds: Set<String>,
    sortAssignments: Map<String, String>,
    activeAudienceId: String,
    onBack: () -> Unit,
    onSetActiveAudience: (String) -> Unit,
    onAssignSelected: (Set<String>) -> Unit,
    onSetRemaining: () -> Unit,
    onReviewSummary: () -> Unit
) {
    val gridState = rememberLazyGridState()
    var pickedIds by remember(media, activeAudienceId) { mutableStateOf(setOf<String>()) }
    val audienceIds = (selectedAudienceIds + if (customPeopleIds.isNotEmpty()) setOf("choose_people") else emptySet()).toList()
    val unsorted = media.filterNot { sortAssignments.containsKey(it.id) }
    val activeName = audienceTitle(activeAudienceId, circles)
    Column(modifier = Modifier.fillMaxSize()) {
        FlowHeader("Sort Photos", "${unsorted.size} left to sort", onBack)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = HelloSpacing.Md), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip("Unsorted", unsorted.size, selected = false, onClick = {})
            audienceIds.forEach { id ->
                SortChip(audienceTitle(id, circles), sortAssignments.values.count { it == id }, selected = id == activeAudienceId) {
                    onSetActiveAudience(id)
                    pickedIds = emptySet()
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(unsorted, key = { it.id }) { item ->
                UploadMediaTile(
                    item = item,
                    selected = item.id in pickedIds,
                    lightweight = gridState.isScrollInProgress,
                    onClick = {
                        pickedIds = pickedIds.toMutableSet().apply {
                            if (contains(item.id)) remove(item.id) else add(item.id)
                        }
                    }
                )
            }
        }
        if (unsorted.size in 1..20) {
            HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), strong = true, shape = HelloShapes.Lg) {
                Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Text("Only ${unsorted.size} photos left", color = HelloColors.DarkText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSetRemaining) {
                        Text("Set Remaining", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        FlowBottomButton(
            primary = if (pickedIds.isEmpty()) "Review Upload" else "Set ${pickedIds.size} photos for $activeName",
            secondary = "Tip: tap or drag across photos",
            enabled = true,
            onPrimary = {
                if (pickedIds.isEmpty()) onReviewSummary() else {
                    onAssignSelected(pickedIds)
                    pickedIds = emptySet()
                }
            },
            onSecondary = {}
        )
    }
}

@Composable
private fun SortChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(HelloShapes.Md)
            .background(if (selected) HelloColors.DarkAccent else HelloColors.DarkPanelStrong)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$count", color = if (selected) HelloColors.DarkBg else HelloColors.DarkAccent, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = if (selected) HelloColors.DarkBg else HelloColors.DarkText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoosePeopleStep(contacts: List<DriveContact>, currentIds: Set<String>, onBack: () -> Unit, onDone: (Set<String>) -> Unit) {
    var selected by remember(currentIds) { mutableStateOf(currentIds) }
    var query by remember { mutableStateOf("") }
    val visibleContacts = contacts.filter { contact ->
        query.isBlank() ||
            contact.name.contains(query, ignoreCase = true) ||
            contact.username.orEmpty().contains(query, ignoreCase = true)
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FlowHeader("Choose People", "Search by name or username", onBack)
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(vertical = HelloSpacing.Md), strong = true, shape = HelloShapes.Lg) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search people") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(HelloSpacing.Md)
            )
        }
        if (contacts.isEmpty()) {
            EmptyInlineState("No people found", "Family Drive suggests people from your Hello account and existing chats.")
        }
        visibleContacts.forEach { contact ->
            val isSelected = contact.id in selected
            AudienceChoiceCard(
                audience = DriveAudienceOption(contact.id, contact.name, contactHandle(contact)),
                selected = isSelected,
                onClick = {
                    selected = selected.toMutableSet().apply { if (contains(contact.id)) remove(contact.id) else add(contact.id) }
                }
            )
        }
        Button(onClick = { onDone(selected) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
            Text("Done (${selected.size})", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun UploadSummaryStep(
    media: List<SelectedDriveMedia>,
    eventName: String,
    circles: List<DriveCircle>,
    selectedAudienceIds: Set<String>,
    customPeopleIds: Set<String>,
    sortAssignments: Map<String, String>,
    state: FamilyDriveUiState,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val breakdown = buildSummaryBreakdown(media, circles, selectedAudienceIds, customPeopleIds, sortAssignments)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelloSpacing.ScreenPadding)
    ) {
        FlowHeader("Upload Summary", "Review event and visibility", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        EventChoiceCard(eventName, "${media.size} photos/videos - ${formatFileSize(media.sumOf { it.size })}", selected = true, onClick = {})
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = HelloSpacing.Md), strong = false, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Md), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Text("Collage preview", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    SelectedMediaCollageGrid(
                        media = media.take(6),
                        circles = circles,
                        sortAssignments = sortAssignments,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        breakdown.forEach { (label, count) ->
            HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), strong = false, shape = HelloShapes.Lg) {
                Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(count.toString(), color = HelloColors.DarkAccent, fontWeight = FontWeight.Black)
                }
            }
        }
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(vertical = HelloSpacing.Md), strong = true, shape = HelloShapes.Lg) {
            Text(
                if (state.error == null) "PC Drive status: PC On or reachable recently" else "PC Drive status: PC Offline - save pending",
                color = if (state.error == null) HelloColors.DarkText else HelloColors.DarkAccent,
                modifier = Modifier.padding(HelloSpacing.Md),
                fontWeight = FontWeight.Bold
            )
        }
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = media.isNotEmpty() && eventName.isNotBlank() && !state.isUploading, colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
            Text(if (state.error == null) "Upload" else "Save Pending", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Review", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PendingUploadsStep(state: FamilyDriveUiState, onBack: () -> Unit, onRetry: () -> Unit, onOpenEvent: () -> Unit, onSuccess: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FlowHeader("Pending Uploads", "Saved locally until PC Drive is online", onBack)
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(vertical = HelloSpacing.Md), strong = true, shape = HelloShapes.Lg) {
            Text("Please don't delete the original photos/videos until upload is complete.", color = HelloColors.DarkAccent, modifier = Modifier.padding(HelloSpacing.Md), fontWeight = FontWeight.Bold)
        }
        if (state.isUploading) {
            LoadingDrive(modifier = Modifier.fillMaxWidth().height(180.dp))
        }
        state.pendingItems.filter { it.status != PendingDriveStatus.SYNCED }.ifEmpty {
            listOf(PendingDriveItem("demo_pending", "", "Waiting for selected upload", "image/jpeg", "image", 0L, System.currentTimeMillis(), "now", "Today"))
        }.forEach { item ->
            HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), strong = false, shape = HelloShapes.Lg) {
                Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(HelloShapes.Md).background(HelloColors.DarkPanelStrong), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = HelloColors.DarkAccent)
                    }
                    Spacer(modifier = Modifier.width(HelloSpacing.Md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.eventName ?: "Family Drive upload", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                        Text("${item.displayName} - Waiting for PC Drive", color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.lastError?.let { Text(if (it.contains("read", true)) "Original file missing. Upload cannot complete." else it, color = HelloColors.DarkDanger, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (state.retryingPendingId == item.id || state.retryingPendingId == "all") {
                        CircularProgressIndicator(color = HelloColors.DarkAccent, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Text("Make sure PC Drive is running and connected to the internet.", color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(HelloSpacing.Md))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
            Text("Retry Sync", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
        TextButton(onClick = if (state.pendingItems.any { it.status == PendingDriveStatus.SYNCED }) onSuccess else onOpenEvent, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("View Event", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun UploadEventPreviewStep(media: List<SelectedDriveMedia>, eventName: String, circles: List<DriveCircle>, sortAssignments: Map<String, String>, onBack: () -> Unit, onOpenCircles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = HelloSpacing.ScreenPadding)
    ) {
        FlowHeader(eventName, "Preview before Family Drive sync", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
                Text("${media.size} selected item${if (media.size == 1) "" else "s"}", color = HelloColors.DarkText, fontWeight = FontWeight.Black)
                Text("Grouped as a shared collage. Audience labels stay attached to each tile.", color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        SelectedMediaCollageGrid(
            media = media,
            circles = circles,
            sortAssignments = sortAssignments,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onOpenCircles, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
            Text("Manage Circles", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CircleManagementStep(
    activeUserId: String,
    circles: List<DriveCircle>,
    onBack: () -> Unit,
    onCreateCircle: () -> Unit,
    onOpenCircle: (DriveCircle) -> Unit,
    onManageCircle: (DriveCircle) -> Unit,
    onUploadCircleAvatar: (DriveCircle) -> Unit,
    onDeleteOrLeaveCircle: (DriveCircle) -> Unit
) {
    val pendingCount = circles.count { it.syncStatus != PendingDriveCircleStatus.SYNCED }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelloSpacing.ScreenPadding)
    ) {
        FlowHeader("My Circles", "Saved sharing groups", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HelloColors.DarkAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = HelloColors.DarkAccent)
                    }
                    Spacer(modifier = Modifier.width(HelloSpacing.Md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Family sharing circles", color = HelloColors.DarkText, fontWeight = FontWeight.Black)
                        Text(
                            if (pendingCount > 0) "$pendingCount circle${if (pendingCount == 1) "" else "s"} waiting for PC sync." else "Create and manage private sharing groups.",
                            color = HelloColors.DarkTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(
                    onClick = onCreateCircle,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                    shape = HelloShapes.Md
                ) {
                    Text("New Circle", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        if (circles.isEmpty()) {
            EmptyInlineState("No circles yet", "Create a circle from your Hello people list.")
        } else {
            circles.forEach { circle ->
                CircleManagementCard(
                    activeUserId = activeUserId,
                    circle = circle,
                    onOpenCircle = { onOpenCircle(circle) },
                    onManageCircle = { onManageCircle(circle) },
                    onUploadCircleAvatar = { onUploadCircleAvatar(circle) },
                    onDeleteOrLeaveCircle = { onDeleteOrLeaveCircle(circle) }
                )
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Xl))
    }
}

@Composable
private fun CircleManagementCard(
    activeUserId: String,
    circle: DriveCircle,
    onOpenCircle: () -> Unit,
    onManageCircle: () -> Unit,
    onUploadCircleAvatar: () -> Unit,
    onDeleteOrLeaveCircle: () -> Unit
) {
    var menuExpanded by remember(circle.id) { mutableStateOf(false) }
    val isOwner = circle.ownerUserId.isNullOrBlank() || circle.ownerUserId == activeUserId
    val isPending = circle.syncStatus != PendingDriveCircleStatus.SYNCED
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onOpenCircle, role = Role.Button),
        strong = false,
        shape = HelloShapes.Lg
    ) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(HelloColors.DarkAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (!circle.avatarUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(UrlResolver.resolve(circle.avatarUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
                }
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(circle.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isPending) {
                        SmallStatusBadge(
                            text = when (circle.syncStatus) {
                                PendingDriveCircleStatus.FAILED_RETRYABLE -> "Needs sync"
                                PendingDriveCircleStatus.SYNCING -> "Syncing"
                                else -> "Pending"
                            },
                            tint = if (circle.syncStatus == PendingDriveCircleStatus.FAILED_RETRYABLE) HelloColors.DarkDanger else HelloColors.DarkAccent
                        )
                    }
                }
                Text(
                    if (isPending) "Saved on this phone. Syncs when the PC is online." else buildCircleManagementSubtitle(circle),
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Circle options", tint = HelloColors.DarkTextMuted)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open events") },
                        onClick = {
                            menuExpanded = false
                            onOpenCircle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Manage circle") },
                        onClick = {
                            menuExpanded = false
                            onManageCircle()
                        }
                    )
                    if (isOwner && !isPending) {
                        DropdownMenuItem(
                            text = { Text("Profile picture") },
                            onClick = {
                                menuExpanded = false
                                onUploadCircleAvatar()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isOwner) "Delete circle" else "Request delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteOrLeaveCircle()
                        }
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HelloColors.DarkAccent)
        }
    }
}

@Composable
private fun EditCircleStep(
    contacts: List<DriveContact>,
    existingCircle: DriveCircle?,
    activeUserId: String,
    onBack: () -> Unit,
    onSave: (String?, String, List<DriveCircleMember>) -> Unit,
    onDeleteCircle: (DriveCircle) -> Unit,
    onLeaveCircle: (DriveCircle) -> Unit,
    onUploadCircleAvatar: (DriveCircle) -> Unit
) {
    var circleName by remember(existingCircle?.id) { mutableStateOf(existingCircle?.name.orEmpty()) }
    var selectedIds by remember(existingCircle?.id) {
        mutableStateOf(existingCircle?.members.orEmpty().map { it.userId }.filter(String::isNotBlank).toSet())
    }
    var selectedRoles by remember(existingCircle?.id) {
        mutableStateOf(
            existingCircle?.members.orEmpty()
                .filter { it.userId.isNotBlank() }
                .associate { it.userId to normalizeDriveRole(it.role) }
        )
    }
    var role by remember(existingCircle?.id) {
        mutableStateOf(existingCircle?.members.orEmpty().firstOrNull { it.userId != existingCircle?.ownerUserId }?.role?.let(::normalizeDriveRole) ?: "Viewer")
    }
    val isPending = existingCircle?.syncStatus != null && existingCircle.syncStatus != PendingDriveCircleStatus.SYNCED
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelloSpacing.ScreenPadding)
    ) {
        FlowHeader(if (existingCircle == null) "Create Circle" else "Manage Circle", "Simple permissions for family sharing", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (existingCircle == null) "Circle details" else "Circle settings", color = HelloColors.DarkText, fontWeight = FontWeight.Black)
                        Text(
                            if (isPending) "This circle is saved locally and will sync when the PC is online." else "Choose a clear name and who can access it.",
                            color = HelloColors.DarkTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isPending) {
                        SmallStatusBadge(text = "Pending", tint = HelloColors.DarkAccent)
                    }
                }
                if (existingCircle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(HelloColors.DarkAccent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!existingCircle.avatarUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(UrlResolver.resolve(existingCircle.avatarUrl))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
                            }
                        }
                        Spacer(modifier = Modifier.width(HelloSpacing.Md))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(existingCircle.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                            Text(buildCircleManagementSubtitle(existingCircle), color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isPending) {
                            TextButton(onClick = { onUploadCircleAvatar(existingCircle) }) {
                                Text("Photo", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = circleName,
                    onValueChange = { circleName = it },
                    label = { Text("Circle name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Text("Members", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedIds.isEmpty()) "Select people from Hello chats and contacts." else "${selectedIds.size} selected member${if (selectedIds.size == 1) "" else "s"}",
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (contacts.isEmpty()) {
                    EmptyInlineState("No people found", "People from your Hello account and existing chats can be added here.")
                }
                contacts.forEach { contact ->
                    val selected = contact.id in selectedIds
                    val contactRole = selectedRoles[contact.id] ?: role
                    AudienceChoiceCard(
                        DriveAudienceOption(
                            contact.id,
                            contact.name,
                            if (selected) "${contactHandle(contact)} - $contactRole" else contactHandle(contact)
                        ),
                        selected = selected
                    ) {
                        val nextSelected = selectedIds.toMutableSet().apply {
                            if (contains(contact.id)) remove(contact.id) else add(contact.id)
                        }.toSet()
                        selectedIds = nextSelected
                        selectedRoles = selectedRoles.toMutableMap().apply {
                            if (contact.id in nextSelected) {
                                put(contact.id, this[contact.id] ?: role)
                            } else {
                                remove(contact.id)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Text("Default permission", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                listOf("Admin", "Contributor", "Viewer").forEach { label ->
                    AudienceChoiceCard(
                        DriveAudienceOption(
                            label,
                            label,
                            if (selectedIds.isEmpty()) "Select people first" else "Apply to ${selectedIds.size} selected member${if (selectedIds.size == 1) "" else "s"}"
                        ),
                        selected = label == role,
                        onClick = {
                            role = label
                            if (selectedIds.isNotEmpty()) {
                                selectedRoles = selectedRoles.toMutableMap().apply {
                                    selectedIds.forEach { put(it, label) }
                                }
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        Button(
            onClick = {
                val members = contacts
                    .filter { it.id in selectedIds }
                    .map {
                        DriveCircleMember(
                            userId = it.id,
                            role = driveRoleStorageValue(selectedRoles[it.id] ?: role),
                            name = it.name,
                            username = it.username,
                            avatar = it.avatar
                        )
                    }
                onSave(existingCircle?.id, circleName, members)
            },
            enabled = circleName.isNotBlank() && selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
            shape = HelloShapes.Lg
        ) {
            Text(if (existingCircle == null) "Save Circle" else "Update Circle", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
        }
        existingCircle?.let { circle ->
            val isOwner = circle.ownerUserId.isNullOrBlank() || circle.ownerUserId == activeUserId
            TextButton(
                onClick = {
                    if (isOwner) onDeleteCircle(circle) else onLeaveCircle(circle)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (isPending || isOwner) "Remove Circle" else "Leave Circle", color = HelloColors.DarkDanger, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Xl))
    }
}

@Composable
private fun UploadSuccessStep(onOpenEvent: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(HelloSpacing.Xl)) {
            Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF1DB954)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))
            Text("Pending uploads completed", color = HelloColors.DarkText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("Your photos/videos are saved to PC.", color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
                Text("All files are safe and backed up.", color = HelloColors.DarkText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(HelloSpacing.Md))
            }
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))
            Button(onClick = onOpenEvent, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
                Text("View Event", color = HelloColors.DarkBg, fontWeight = FontWeight.Black)
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Back to Home", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriveEventManagementScreen(
    state: FamilyDriveUiState,
    onBack: () -> Unit,
    onOpenCircleChooser: () -> Unit,
    onChooseEvent: (DriveEvent) -> Unit,
    onCreateEvent: (String) -> Unit,
    onRenameEvent: (String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onOpenCircleMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCircleName = state.circles.firstOrNull { it.id == state.activeCircleId }?.name
        ?: state.circles.firstOrNull()?.name
        ?: "No circle selected"
    Column(
        modifier = modifier
            .padding(horizontal = HelloSpacing.ScreenPadding)
            .padding(top = HelloSpacing.Md, bottom = HelloSpacing.Lg)
    ) {
        FlowHeader("Events", "Manage folders inside one circle", onBack)
        Spacer(modifier = Modifier.height(HelloSpacing.Md))
        HelloPanel(modifier = Modifier.fillMaxWidth().padding(bottom = HelloSpacing.Md), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Md), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Text("Current circle", color = HelloColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(activeCircleName, color = HelloColors.AccentStrong, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenCircleChooser, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Change circle", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onOpenCircleMedia, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Open media", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (state.activeCircleId.isNullOrBlank()) {
            EmptyInlineState("No circle selected", "Open the Circles page first, then manage the events inside that circle.")
        } else {
            ChooseEventStep(
                events = state.events,
                selectedEventName = state.activeEventId?.let { activeId -> state.events.firstOrNull { it.id == activeId }?.name }.orEmpty(),
                onBack = onBack,
                onChoose = onChooseEvent,
                onCreateEvent = onCreateEvent,
                onRenameEvent = onRenameEvent,
                onDeleteEvent = onDeleteEvent,
                showHeader = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UploadMediaTile(
    item: SelectedDriveMedia,
    selected: Boolean,
    lightweight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(HelloShapes.Md)
            .background(HelloColors.DarkPanelStrong)
            .border(2.dp, if (selected) HelloColors.DarkAccent else Color.Transparent, HelloShapes.Md)
            .clickable(onClick = onClick, role = Role.Checkbox)
    ) {
        if (lightweight) {
            DriveMediaPlaceholder(isVideo = item.isVideo)
        } else if (item.isVideo) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(36.dp))
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(false)
                    .allowHardware(true)
                    .size(480, 480)
                    .memoryCacheKey(item.id)
                    .build(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HelloColors.DarkAccent, modifier = Modifier.size(18.dp)) } },
                error = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkTextMuted) } }
            )
        }
        if (!lightweight) {
            SelectionMark(selected = selected, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        if (item.isVideo && !lightweight) {
            Text("Video", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun SmallStatusBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectedMediaCollageTile(
    item: SelectedDriveMedia,
    collageStyle: DriveCollageStyle,
    audienceLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(collageStyle.aspectRatio)
            .clip(collageStyle.shape)
            .background(HelloColors.DarkPanelStrong)
            .border(1.dp, Color.White.copy(alpha = 0.58f), collageStyle.shape)
    ) {
        if (item.isVideo) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(false)
                    .allowHardware(true)
                    .memoryCacheKey(item.id)
                    .build(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HelloColors.DarkAccent, modifier = Modifier.size(18.dp))
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkTextMuted)
                    }
                }
            )
        }
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Video", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        audienceLabel?.let { label ->
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SelectedMediaCollageGrid(
    media: List<SelectedDriveMedia>,
    circles: List<DriveCircle>,
    sortAssignments: Map<String, String>,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = modifier
    ) {
        itemsIndexed(media, key = { _, item -> item.id }, span = { index, item ->
            GridItemSpan(driveCollageStyleFor(item.id, item.isVideo, index).span)
        }) { index, item ->
            val collageStyle = driveCollageStyleFor(item.id, item.isVideo, index)
            SelectedMediaCollageTile(
                item = item,
                collageStyle = collageStyle,
                audienceLabel = sortAssignments[item.id]?.let { audienceTitle(it, circles) }
            )
        }
    }
}

@Composable
private fun FlowBottomButton(
    primary: String,
    secondary: String,
    enabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Column(modifier = Modifier.padding(HelloSpacing.Md)) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(38.dp)) {
                Text(secondary, color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onPrimary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                shape = HelloShapes.Lg
            ) {
                Text(primary, color = HelloColors.DarkBg, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

private fun buildSummaryBreakdown(
    media: List<SelectedDriveMedia>,
    circles: List<DriveCircle>,
    selectedAudienceIds: Set<String>,
    customPeopleIds: Set<String>,
    sortAssignments: Map<String, String>
): Map<String, Int> {
    if (sortAssignments.isNotEmpty()) {
        return sortAssignments.values.groupingBy { audienceTitle(it, circles) }.eachCount()
    }
    val base = selectedAudienceIds.associate { audienceTitle(it, circles) to media.size }.toMutableMap()
    if (customPeopleIds.isNotEmpty()) base["Choose People"] = media.size
    return base.ifEmpty { mapOf("Only Me" to media.size) }
}

private fun audienceTitle(id: String, circles: List<DriveCircle> = emptyList()): String {
    return when (id) {
        "only_me" -> "Only Me"
        "choose_people" -> "Choose People"
        else -> circles.firstOrNull { it.id == id }?.name ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun readSelectedDriveMedia(context: Context, uris: List<Uri>): List<SelectedDriveMedia> {
    return uris.mapIndexed { index, uri ->
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        val metadata = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                name to size
            } else {
                null
            }
        }
        SelectedDriveMedia(
            id = "picked_${index}_${uri.hashCode()}",
            uri = uri,
            displayName = metadata?.first ?: "family-drive-media",
            mimeType = mimeType,
            size = metadata?.second ?: 0L
        )
    }
}

@Composable
private fun DriveHomeContent(
    state: FamilyDriveUiState,
    onUploadClick: () -> Unit,
    onOpenAllPhotos: () -> Unit,
    onOpenCircles: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelloSpacing.ScreenPadding)
            .padding(top = HelloSpacing.Xl, bottom = HelloSpacing.Lg)
    ) {
        Text(
            text = "HELLO DRIVE ✧",
            color = HelloColors.DarkAccent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Family Drive",
            color = HelloColors.AccentStrong,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "All our family memories in one place.",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
            shape = HelloShapes.Pill
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Upload Photos", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(30.dp))
        DriveHomeCard(
            icon = Icons.Default.Cloud,
            title = "Circles",
            subtitle = if (state.isLoading) {
                "Loading your shared groups..."
            } else {
                "${state.circles.size} shared groups. Open boxed circles, then enter a circle Drive to upload, mark, manage roles, and handle delete requests."
            },
            onClick = onOpenCircles
        )
        Spacer(modifier = Modifier.height(12.dp))
        DriveHomeCard(
            icon = Icons.Default.Delete,
            title = "Trash",
            subtitle = if (state.activeCircleId.isNullOrBlank()) {
                "Deleted circle items stay here until you permanently remove them from PC trash."
            } else {
                "Trash for ${audienceTitle(state.activeCircleId, state.circles)}. Delete here again to remove from PC trash permanently."
            },
            onClick = onOpenTrash
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = HelloColors.Accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Shared trash protects accidental deletes.\nDaily delete limit: 20 items.",
                color = HelloColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DriveHomeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Lg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(HelloShapes.Md)
                    .background(HelloColors.DarkAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HelloColors.AccentStrong, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(subtitle, color = HelloColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HelloColors.Accent)
        }
    }
}

@Composable
private fun DriveLibraryContent(
    mode: DriveMode,
    state: FamilyDriveUiState,
    selectedIds: Set<String>,
    favoriteIds: Set<String>,
    activeCircleId: String?,
    activeEventId: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetryPending: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItem: (DriveItem) -> Unit,
    onOpenPending: (PendingDriveItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMonth: (List<DriveGridItem>) -> Unit,
    onRetryPendingItem: (String) -> Unit,
    onRemovePendingItem: (PendingDriveItem) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trashMode = mode == DriveMode.Trash
    val gridState = rememberLazyGridState()
    val visiblePendingItems = remember(state.pendingItems, activeCircleId, activeEventId, trashMode) {
        if (trashMode) {
            emptyList()
        } else {
            state.pendingItems.filter { pending ->
                val matchesCircle = activeCircleId.isNullOrBlank() || pending.selectedCircleIds.isEmpty() || activeCircleId in pending.selectedCircleIds
                val matchesEvent = activeEventId.isNullOrBlank() || pending.eventId.isNullOrBlank() || pending.eventId == activeEventId
                matchesCircle && matchesEvent
            }
        }
    }
    val pendingCount = visiblePendingItems.size
    val gridItems = remember(mode, state.items, state.trashItems, visiblePendingItems) {
        val synced = if (trashMode) state.trashItems else state.items
        val pending = if (trashMode) emptyList() else visiblePendingItems.map { DriveGridItem.Pending(it) }
        (pending + synced.map { DriveGridItem.Synced(it) }).sortedByDescending { it.createdAt }
    }
    val groupedItems = remember(gridItems) { gridItems.groupBy { it.monthLabel } }
    val selectionMode = selectedIds.isNotEmpty()
    val loading = if (trashMode) state.isTrashLoading else state.isLoading
    val loadingMore = if (trashMode) state.isTrashLoadingMore else state.isLoadingMore
    val total = if (trashMode) state.trashTotal else state.total + pendingCount
    var lightweightCells by remember(gridState) { mutableStateOf(gridState.isScrollInProgress) }
    val activeEventName = state.events.firstOrNull { it.id == activeEventId }?.name
    val activeCircleName = state.circles.firstOrNull { it.id == activeCircleId }?.name

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (isScrolling) {
                    lightweightCells = true
                } else {
                    delay(DRIVE_CELL_RESTORE_SETTLE_MS)
                    lightweightCells = false
                }
            }
    }

    LaunchedEffect(gridState, gridItems.size, state.hasMore, state.trashHasMore, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                val count = gridState.layoutInfo.totalItemsCount
                if (count > 0 && lastVisible >= count - 9) onLoadMore()
            }
    }

    Column(modifier = modifier.padding(horizontal = HelloSpacing.ScreenPadding)) {
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        trashMode -> "Trash"
                        !activeEventName.isNullOrBlank() -> activeEventName
                        !activeCircleName.isNullOrBlank() -> activeCircleName
                        else -> "All Circles"
                    },
                    color = HelloColors.DarkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text("$total items", color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                TextButton(
                    onClick = { if (!trashMode && pendingCount > 0) onRetryPending() else onRefresh() },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = if (!trashMode && pendingCount > 0) "Sync pending uploads" else "Refresh",
                        tint = if (!trashMode && pendingCount > 0) HelloColors.DarkAccent else HelloColors.DarkText
                    )
                }
                if (!trashMode && pendingCount > 0) {
                    CountBadge(count = pendingCount, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (trashMode) "Shared trash - restore accidental deletes" else if (pendingCount > 0) "Circle Drive - $pendingCount waiting for PC" else "Circle Drive",
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (!trashMode) {
                TextButton(onClick = onUploadClick, modifier = Modifier.height(48.dp)) {
                    Text("Upload", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        if (loading && gridItems.isEmpty()) {
            LoadingDrive(modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (gridItems.isEmpty()) {
            EmptyDrive(trashMode = trashMode, onUploadClick = onUploadClick, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 6.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 116.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedItems.forEach { (monthLabel, itemsInMonth) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MonthHeader(
                            monthLabel = monthLabel,
                            count = itemsInMonth.size,
                            selectionMode = selectionMode,
                            selected = itemsInMonth.filterIsInstance<DriveGridItem.Synced>().all { it.item.id in selectedIds },
                            onSelectMonth = { onSelectMonth(itemsInMonth) }
                        )
                    }
                    itemsInMonth.forEachIndexed { index, gridItem ->
                        val collageStyle = driveCollageStyleFor(gridItem.id, gridItem.isVideoItem(), index)
                        item(key = gridItem.id, span = { GridItemSpan(collageStyle.span) }) {
                            DriveMediaCard(
                                item = gridItem,
                                collageStyle = collageStyle,
                                lightweight = lightweightCells,
                                trashMode = trashMode,
                                isSelected = gridItem is DriveGridItem.Synced && gridItem.item.id in selectedIds,
                                selectionMode = selectionMode,
                                isFavorite = gridItem is DriveGridItem.Synced && favoriteIds.contains(gridItem.item.id),
                                isRetrying = gridItem is DriveGridItem.Pending && state.retryingPendingId in setOf(gridItem.item.id, "all"),
                                onToggleFavorite = {
                                    if (gridItem is DriveGridItem.Synced) onToggleFavorite(gridItem.item.id)
                                },
                                onRetryPending = {
                                    if (gridItem is DriveGridItem.Pending) onRetryPendingItem(gridItem.item.id)
                                },
                                onRemovePending = {
                                    if (gridItem is DriveGridItem.Pending) onRemovePendingItem(gridItem.item)
                                },
                                onClick = {
                                    when (gridItem) {
                                        is DriveGridItem.Synced -> {
                                            if (selectionMode) onToggleSelection(gridItem.item.id) else onOpenItem(gridItem.item)
                                        }
                                        is DriveGridItem.Pending -> onOpenPending(gridItem.item)
                                    }
                                },
                                onLongClick = {
                                    if (gridItem is DriveGridItem.Synced) onToggleSelection(gridItem.item.id)
                                }
                            )
                        }
                    }
                }
                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(HelloSpacing.Lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = HelloColors.DarkAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingDrive(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = HelloColors.DarkAccent)
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Text("Loading...", color = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun EmptyDrive(trashMode: Boolean, onUploadClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (trashMode) Icons.Default.Delete else Icons.Default.Cloud,
                contentDescription = null,
                tint = HelloColors.DarkAccent,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Text(
                text = if (trashMode) "Trash is empty" else "No photos yet",
                color = HelloColors.DarkText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (trashMode) "Deleted Drive items appear here before permanent removal." else "Upload photos and videos to save them on this PC.",
                color = HelloColors.DarkTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = HelloSpacing.Xxl)
            )
            if (!trashMode) {
                Spacer(modifier = Modifier.height(HelloSpacing.Lg))
                Button(onClick = onUploadClick, colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Lg) {
                    Text("Upload Now", color = HelloColors.DarkBg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    monthLabel: String,
    count: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = selectionMode, onClick = onSelectMonth, role = Role.Button)
            .semantics {
                contentDescription = if (selectionMode) "Select $monthLabel month" else "$monthLabel, $count items"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionMark(selected = selected)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = monthLabel,
            color = HelloColors.DarkText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text("$count", color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private data class DriveCollageStyle(
    val span: Int,
    val aspectRatio: Float,
    val shape: RoundedCornerShape
)

private fun DriveGridItem.isVideoItem(): Boolean = when (this) {
    is DriveGridItem.Synced -> item.isVideo
    is DriveGridItem.Pending -> item.isVideo
}

private fun driveCollageStyleFor(itemId: String, isVideo: Boolean, index: Int): DriveCollageStyle {
    val patterns = listOf(
        DriveCollageStyle(span = 2, aspectRatio = 1.48f, shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 24.dp, bottomEnd = 24.dp)),
        DriveCollageStyle(span = 1, aspectRatio = 0.82f, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 22.dp, bottomStart = 28.dp, bottomEnd = 18.dp)),
        DriveCollageStyle(span = 1, aspectRatio = 1.16f, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 30.dp, bottomStart = 20.dp, bottomEnd = 30.dp)),
        DriveCollageStyle(span = 2, aspectRatio = 1.08f, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 20.dp, bottomStart = 34.dp, bottomEnd = 28.dp)),
        DriveCollageStyle(span = 1, aspectRatio = 1.28f, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 32.dp)),
        DriveCollageStyle(span = 1, aspectRatio = 0.94f, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 20.dp, bottomStart = 24.dp, bottomEnd = 32.dp))
    )
    val stableIndex = (itemId.sumOf { it.code } + index) % patterns.size
    val base = patterns[stableIndex]
    return if (isVideo) base.copy(aspectRatio = 1f) else base
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriveMediaCard(
    item: DriveGridItem,
    collageStyle: DriveCollageStyle,
    lightweight: Boolean,
    trashMode: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    isFavorite: Boolean,
    isRetrying: Boolean,
    onToggleFavorite: () -> Unit,
    onRetryPending: () -> Unit,
    onRemovePending: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = item.isVideoItem()
    val imageData = when (item) {
        is DriveGridItem.Synced -> UrlResolver.resolve(item.item.thumbnailUrl ?: item.item.url)
        is DriveGridItem.Pending -> Uri.parse(item.item.localUri)
    }
    val displayName = when (item) {
        is DriveGridItem.Synced -> item.item.originalName ?: "Drive media"
        is DriveGridItem.Pending -> item.item.displayName
    }
    val imageRequest = remember(context, imageData, item.id) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .allowHardware(true)
            .size(480, 480)
            .memoryCacheKey(item.id)
            .diskCacheKey(item.id)
            .build()
    }
    Box(
        modifier = Modifier
            .aspectRatio(collageStyle.aspectRatio)
            .clip(collageStyle.shape)
            .background(HelloColors.DarkPanelStrong)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.58f), shape = collageStyle.shape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = if (isSelected) "Selected $displayName" else displayName
                role = Role.Button
            }
    ) {
        DriveMediaPlaceholder(isVideo = isVideo)
        AsyncImage(
            model = imageRequest,
            contentDescription = displayName,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent { if (!lightweight) drawContent() },
            contentScale = ContentScale.Crop
        )
        if (!lightweight && selectionMode && item is DriveGridItem.Synced) {
            SelectionMark(selected = isSelected, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        if (isVideo && !lightweight) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Video",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (!lightweight) when (item) {
            is DriveGridItem.Synced -> {
                if (!trashMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = if (isFavorite) 0.62f else 0.34f))
                            .clickable(onClick = onToggleFavorite, role = Role.Button)
                            .semantics { contentDescription = if (isFavorite) "Remove favorite" else "Favorite" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            is DriveGridItem.Pending -> {
                PendingUploadBadge(
                    item = item.item,
                    isRetrying = isRetrying,
                    onRetry = onRetryPending,
                    onRemove = onRemovePending,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                )
            }
        }
    }
}

@Composable
private fun DriveMediaPlaceholder(isVideo: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HelloColors.DarkPanelStrong),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Default.PlayArrow else Icons.Default.Image,
            contentDescription = null,
            tint = HelloColors.DarkTextMuted.copy(alpha = 0.58f),
            modifier = Modifier.size(if (isVideo) 32.dp else 28.dp)
        )
    }
}

@Composable
private fun PendingUploadBadge(
    item: PendingDriveItem,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onRetry, role = Role.Button)
                .semantics { contentDescription = "Retry pending upload" },
            contentAlignment = Alignment.Center
        ) {
            if (isRetrying || item.status == PendingDriveStatus.UPLOADING) {
                CircularProgressIndicator(color = HelloColors.DarkAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .clip(HelloShapes.Sm)
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onRemove, role = Role.Button)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = when (item.status) {
                    PendingDriveStatus.FAILED_RETRYABLE -> "Stored local"
                    PendingDriveStatus.UPLOADING -> "Uploading"
                    PendingDriveStatus.SYNCED -> "Saved"
                    else -> "Stored local"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    totalCount: Int,
    trashMode: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Column(modifier = Modifier.padding(HelloSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$selectedCount selected", color = HelloColors.DarkText, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll) {
                    Text("Select all $totalCount", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClear) {
                    Text("Clear", color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (trashMode) {
                    Button(onClick = onRestore, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Md) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = HelloColors.DarkBg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", color = HelloColors.DarkBg, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onPermanentDelete, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkDanger), shape = HelloShapes.Md) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete forever", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    Button(onClick = onTrash, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkDanger), shape = HelloShapes.Md) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Move to Trash", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveMediaViewer(
    item: DriveItem,
    isFavorite: Boolean,
    trashMode: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedUrl = UrlResolver.resolve(item.url)
    var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(HelloSpacing.Lg)
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset > 72f && hasPrevious -> onPrevious()
                            dragOffset < -72f && hasNext -> onNext()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(formatDateTime(item.createdAt), color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.bodySmall)
                if (!trashMode) {
                    TextButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = if (isFavorite) "Remove favorite" else "Favorite",
                            tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onPrevious, enabled = hasPrevious, modifier = Modifier.align(Alignment.CenterStart).size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous media", tint = Color.White)
                }
                if (item.isVideo) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(78.dp))
                        Spacer(modifier = Modifier.height(HelloSpacing.Md))
                        Text("Video", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(HelloSpacing.Md))
                        Button(
                            onClick = {
                                resolvedUrl?.let { url ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), item.mimeType ?: "video/*")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                            shape = HelloShapes.Lg
                        ) {
                            Text("Open Video", color = HelloColors.DarkBg, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context).data(resolvedUrl).crossfade(false).allowHardware(true).build(),
                            contentDescription = item.originalName,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)),
                            contentScale = ContentScale.Fit,
                            loading = { CircularProgressIndicator(color = HelloColors.DarkAccent) },
                            error = { Text("Photo load failed", color = HelloColors.DarkDanger) }
                        )
                    }
                }
                TextButton(onClick = onNext, enabled = hasNext, modifier = Modifier.align(Alignment.CenterEnd).size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next media", tint = Color.White)
                }
            }
            Text(item.originalName ?: "Family Drive media", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(
                text = if (trashMode) "Deleted ${formatDateTime(item.deletedAt ?: 0L)} - ${formatFileSize(item.size)}" else "Saved in ${item.eventName ?: "circle media"} - ${formatFileSize(item.size)}",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trashMode) {
                    Button(onClick = onRestore, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent), shape = HelloShapes.Md, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(onClick = onPermanentDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkDanger), shape = HelloShapes.Md, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    Button(onClick = onToggleFavorite, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.12f), contentColor = Color.White), shape = HelloShapes.Md, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Favorite", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkDanger, contentColor = Color.White), shape = HelloShapes.Md, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trash", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Button(
                    onClick = { resolvedUrl?.let { downloadAttachment(context, it, item.originalName ?: "family-drive-media") } },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                    shape = HelloShapes.Md,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DriveActionDialog(
    action: DrivePendingAction,
    deleteLimit: DriveDeleteLimit?,
    securityAnswer: String,
    isBusy: Boolean,
    onSecurityAnswerChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val count = action.pendingItem?.let { 1 } ?: action.itemIds.size
    val title = when (action.type) {
        DriveActionType.Trash -> "Move to Trash?"
        DriveActionType.Restore -> "Restore from Trash?"
        DriveActionType.PermanentDelete -> "Delete forever?"
        DriveActionType.RemovePending -> "Remove pending upload?"
    }
    val text = when (action.type) {
        DriveActionType.Trash -> {
            val limitText = deleteLimit?.let { "${it.remaining} of ${it.limit} trash moves remain today." }
                ?: "Family Drive allows 20 trash moves per user each day."
            "This moves $count item${if (count == 1) "" else "s"} to shared Trash. $limitText"
        }
        DriveActionType.Restore -> "This restores $count item${if (count == 1) "" else "s"} to the family gallery."
        DriveActionType.PermanentDelete -> "This permanently removes $count item${if (count == 1) "" else "s"} from the PC. This cannot be undone."
        DriveActionType.RemovePending -> "This removes the local waiting upload from this device only."
    }
    val confirmText = when (action.type) {
        DriveActionType.Trash -> "Move to Trash"
        DriveActionType.Restore -> "Restore"
        DriveActionType.PermanentDelete -> "Delete forever"
        DriveActionType.RemovePending -> "Remove"
    }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onCancel() },
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkTextMuted,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (action.type == DriveActionType.Trash) {
                    Spacer(modifier = Modifier.height(HelloSpacing.Md))
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = onSecurityAnswerChange,
                        label = { Text("Security answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy && (action.type != DriveActionType.Trash || securityAnswer.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = if (action.type == DriveActionType.Restore) HelloColors.DarkAccent else HelloColors.DarkDanger),
                shape = HelloShapes.Md
            ) {
                Text(if (isBusy) "Working..." else confirmText, color = if (action.type == DriveActionType.Restore) HelloColors.DarkBg else Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isBusy) {
                Text("Cancel", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Text(text = message, color = HelloColors.DarkDanger, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
    }
}

@Composable
private fun InfoBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = HelloColors.DarkAccent)
            Spacer(modifier = Modifier.width(HelloSpacing.Sm))
            Text(text = message, color = HelloColors.DarkText, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (selected) HelloColors.DarkAccent else Color.Black.copy(alpha = 0.52f))
            .semantics {
                contentDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B5C)),
        contentAlignment = Alignment.Center
    ) {
        Text(count.coerceAtMost(99).toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun openLocalPendingMedia(context: Context, item: PendingDriveItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(item.localUri), item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun monthLabelFromTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown Month"
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(timestamp))
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown date"
    return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.US).format(Date(timestamp))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[index])
}

private fun contactHandle(contact: DriveContact): String {
    val username = contact.username?.trim().orEmpty()
    return if (username.isNotBlank()) "@$username" else contact.name
}

private fun buildCircleSubtitle(circle: DriveCircle): String {
    val visibleMembers = circle.members
        .mapNotNull { member ->
            member.username?.trim()?.takeIf(String::isNotBlank)?.let { "@$it" }
                ?: member.name?.trim()?.takeIf(String::isNotBlank)
        }
        .distinct()
        .take(2)
    val prefix = "${circle.memberCount} member${if (circle.memberCount == 1) "" else "s"}"
    return if (visibleMembers.isEmpty()) prefix else "$prefix - ${visibleMembers.joinToString(", ")}"
}

private fun buildCircleManagementSubtitle(circle: DriveCircle): String {
    val roleSummary = circle.members
        .filter { it.userId != circle.ownerUserId }
        .groupingBy { normalizeDriveRole(it.role) }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .joinToString(" - ") { "${it.value} ${it.key.lowercase(Locale.US)}" }
    return listOf(buildCircleSubtitle(circle), roleSummary)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun normalizeDriveRole(role: String?): String {
    val value = role?.trim().orEmpty().lowercase(Locale.US)
    return when {
        value == "owner" || value == "admin" || value.contains("manage") -> "Admin"
        value.contains("add") || value.contains("contribute") || value == "member" -> "Contributor"
        else -> "Viewer"
    }
}

private fun driveRoleStorageValue(label: String): String {
    return when (label.trim().lowercase(Locale.US)) {
        "admin", "can manage" -> "manage"
        "contributor", "can add" -> "add"
        else -> "view"
    }
}
