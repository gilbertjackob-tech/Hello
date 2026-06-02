package com.glassbox.hello.familydrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.glassbox.hello.chat.components.downloadAttachment
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DriveMode {
    Home,
    AllPhotos,
    Trash
}

private enum class DriveActionType {
    Trash,
    Restore,
    PermanentDelete,
    RemovePending
}

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
    val viewerItems = if (viewerTrashMode) state.trashItems else state.items
    val viewerItem = viewerIndex?.let { viewerItems.getOrNull(it) }
    val activeSyncedItems = if (mode == DriveMode.Trash) state.trashItems else state.items
    val favoritesPrefs = remember(currentUserId) {
        context.getSharedPreferences("family_drive_favorites_$currentUserId", Context.MODE_PRIVATE)
    }
    var favoriteIds by remember(currentUserId) {
        mutableStateOf(favoritesPrefs.getStringSet("ids", emptySet())?.toSet().orEmpty())
    }

    fun toggleFavorite(itemId: String) {
        val next = favoriteIds.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }.toSet()
        favoriteIds = next
        favoritesPrefs.edit().putStringSet("ids", next).apply()
    }

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
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(context, currentUserId, uris)
    }
    val openPicker = {
        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    LaunchedEffect(currentUserId) {
        viewModel.startPendingObserver(context)
        viewModel.refresh()
        viewModel.refreshDeleteLimit(currentUserId)
        viewModel.retryPending(context, currentUserId)
    }

    LaunchedEffect(mode) {
        selectedIds = emptySet()
        viewerIndex = null
        if (mode == DriveMode.Trash) viewModel.refreshTrash()
    }

    LaunchedEffect(activeSyncedItems) {
        val activeIds = activeSyncedItems.map { it.id }.toSet()
        if (selectedIds.any { it !in activeIds }) {
            selectedIds = selectedIds.intersect(activeIds)
        }
    }

    BackHandler(enabled = pendingAction != null || viewerIndex != null || selectedIds.isNotEmpty() || mode != DriveMode.Home) {
        when {
            pendingAction != null -> pendingAction = null
            viewerIndex != null -> viewerIndex = null
            selectedIds.isNotEmpty() -> selectedIds = emptySet()
            mode != DriveMode.Home -> mode = DriveMode.Home
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            DriveMode.Home -> DriveHomeContent(
                state = state,
                onUploadClick = openPicker,
                onOpenAllPhotos = { mode = DriveMode.AllPhotos },
                onOpenTrash = { mode = DriveMode.Trash },
                modifier = Modifier.fillMaxSize()
            )
            DriveMode.AllPhotos -> DriveLibraryContent(
                mode = mode,
                state = state,
                selectedIds = selectedIds,
                favoriteIds = favoriteIds,
                onBack = { mode = DriveMode.Home },
                onRefresh = { viewModel.refresh() },
                onRetryPending = { viewModel.retryPending(context, currentUserId) },
                onLoadMore = { viewModel.loadMore() },
                onOpenItem = { item ->
                    viewerTrashMode = false
                    viewerIndex = state.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                },
                onOpenPending = { openLocalPendingMedia(context, it) },
                onToggleFavorite = { toggleFavorite(it) },
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
                favoriteIds = favoriteIds,
                onBack = { mode = DriveMode.Home },
                onRefresh = { viewModel.refreshTrash() },
                onRetryPending = {},
                onLoadMore = { viewModel.loadMoreTrash() },
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
                isFavorite = favoriteIds.contains(item.id),
                trashMode = viewerTrashMode,
                hasPrevious = (viewerIndex ?: 0) > 0,
                hasNext = (viewerIndex ?: 0) < viewerItems.lastIndex,
                onClose = { viewerIndex = null },
                onPrevious = { viewerIndex = ((viewerIndex ?: 0) - 1).coerceAtLeast(0) },
                onNext = { viewerIndex = ((viewerIndex ?: 0) + 1).coerceAtMost(viewerItems.lastIndex) },
                onToggleFavorite = { toggleFavorite(item.id) },
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
                isBusy = state.isBusy,
                onCancel = { pendingAction = null },
                onConfirm = {
                    when (action.type) {
                        DriveActionType.Trash -> viewModel.moveItemsToTrash(currentUserId, action.itemIds) {
                            favoriteIds = favoriteIds - action.itemIds
                            favoritesPrefs.edit().putStringSet("ids", favoriteIds).apply()
                            selectedIds = emptySet()
                            viewerIndex = null
                            pendingAction = null
                        }
                        DriveActionType.Restore -> viewModel.restoreItems(action.itemIds) {
                            selectedIds = emptySet()
                            viewerIndex = null
                            pendingAction = null
                        }
                        DriveActionType.PermanentDelete -> viewModel.permanentlyDeleteItems(action.itemIds) {
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
private fun DriveHomeContent(
    state: FamilyDriveUiState,
    onUploadClick: () -> Unit,
    onOpenAllPhotos: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = HelloSpacing.ScreenPadding)
            .padding(top = HelloSpacing.Xl, bottom = HelloSpacing.Lg)
    ) {
        Text(
            text = "HELLO DRIVE",
            color = HelloColors.DarkAccent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Family Drive",
            color = HelloColors.DarkText,
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
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
            shape = HelloShapes.Lg
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = HelloColors.DarkBg)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Upload Photos", color = HelloColors.DarkBg, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(30.dp))
        DriveHomeCard(
            icon = Icons.Default.Image,
            title = "All Photos & Videos",
            subtitle = if (state.isLoading) "Loading..." else "${state.total + state.pendingItems.size} items",
            onClick = onOpenAllPhotos
        )
        Spacer(modifier = Modifier.height(12.dp))
        DriveHomeCard(
            icon = Icons.Default.Delete,
            title = "Trash",
            subtitle = "${state.trashTotal} recoverable items",
            onClick = onOpenTrash
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = HelloColors.DarkTextMuted, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Shared trash protects accidental deletes.\nDaily delete limit: 20 items.",
                color = HelloColors.DarkTextMuted,
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
                .height(76.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(HelloShapes.Md)
                    .background(HelloColors.DarkAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HelloColors.DarkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun DriveLibraryContent(
    mode: DriveMode,
    state: FamilyDriveUiState,
    selectedIds: Set<String>,
    favoriteIds: Set<String>,
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
    val pendingCount = state.pendingItems.count { it.status != PendingDriveStatus.SYNCED }
    val gridItems = remember(mode, state.items, state.trashItems, state.pendingItems) {
        val synced = if (trashMode) state.trashItems else state.items
        val pending = if (trashMode) emptyList() else state.pendingItems.map { DriveGridItem.Pending(it) }
        (pending + synced.map { DriveGridItem.Synced(it) }).sortedByDescending { it.createdAt }
    }
    val groupedItems = remember(gridItems) { gridItems.groupBy { it.monthLabel } }
    val selectionMode = selectedIds.isNotEmpty()
    val loading = if (trashMode) state.isTrashLoading else state.isLoading
    val loadingMore = if (trashMode) state.isTrashLoadingMore else state.isLoadingMore
    val total = if (trashMode) state.trashTotal else state.total + pendingCount

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
                    text = if (trashMode) "Trash" else "All Photos & Videos",
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
                text = if (trashMode) "Shared trash - restore accidental deletes" else if (pendingCount > 0) "Grouped by month - $pendingCount waiting for PC" else "Grouped by month",
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 116.dp),
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
                    items(itemsInMonth, key = { it.id }) { gridItem ->
                        DriveMediaCard(
                            item = gridItem,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriveMediaCard(
    item: DriveGridItem,
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
    val isVideo = when (item) {
        is DriveGridItem.Synced -> item.item.isVideo
        is DriveGridItem.Pending -> item.item.isVideo
    }
    val imageData = when (item) {
        is DriveGridItem.Synced -> UrlResolver.resolve(item.item.thumbnailUrl ?: item.item.url)
        is DriveGridItem.Pending -> Uri.parse(item.item.localUri)
    }
    val displayName = when (item) {
        is DriveGridItem.Synced -> item.item.originalName ?: "Drive media"
        is DriveGridItem.Pending -> item.item.displayName
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(HelloShapes.Md)
            .background(HelloColors.DarkPanelStrong)
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
        if (isVideo) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = HelloColors.DarkAccent, modifier = Modifier.size(36.dp))
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(false)
                    .allowHardware(true)
                    .memoryCacheKey(item.id)
                    .diskCacheKey(item.id)
                    .build(),
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HelloColors.DarkAccent, modifier = Modifier.size(22.dp))
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkTextMuted)
                    }
                }
            )
        }
        if (selectionMode && item is DriveGridItem.Synced) {
            SelectionMark(selected = isSelected, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        if (isVideo) {
            Text(
                "Video",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        when (item) {
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
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                    PendingDriveStatus.FAILED_RETRYABLE -> "Retry"
                    PendingDriveStatus.UPLOADING -> "Syncing"
                    PendingDriveStatus.SYNCED -> "Saved"
                    else -> "Pending"
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
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(resolvedUrl).crossfade(false).allowHardware(true).build(),
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                        loading = { CircularProgressIndicator(color = HelloColors.DarkAccent) },
                        error = { Text("Photo load failed", color = Color.White) }
                    )
                }
                TextButton(onClick = onNext, enabled = hasNext, modifier = Modifier.align(Alignment.CenterEnd).size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next media", tint = Color.White)
                }
            }
            Text(item.originalName ?: "Family Drive media", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(
                text = if (trashMode) "Deleted ${formatDateTime(item.deletedAt ?: 0L)} - ${formatFileSize(item.size)}" else "Saved in All Photos & Videos - ${formatFileSize(item.size)}",
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
    isBusy: Boolean,
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
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
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
