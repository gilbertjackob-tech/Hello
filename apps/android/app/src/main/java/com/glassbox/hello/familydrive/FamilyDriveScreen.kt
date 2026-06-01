package com.glassbox.hello.familydrive

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    AllPhotos
}

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
    var selectedItem by remember { mutableStateOf<DriveItem?>(null) }
    var deleteCandidate by remember { mutableStateOf<DriveItem?>(null) }
    var mode by remember { mutableStateOf(DriveMode.Home) }
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
    fun removeFavorite(itemId: String) {
        if (!favoriteIds.contains(itemId)) return
        val next = favoriteIds - itemId
        favoriteIds = next
        favoritesPrefs.edit().putStringSet("ids", next).apply()
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
        viewModel.retryPending(context, currentUserId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            DriveMode.Home -> DriveHomeContent(
                state = state,
                onUploadClick = openPicker,
                onOpenAllPhotos = { mode = DriveMode.AllPhotos },
                modifier = Modifier.fillMaxSize()
            )
            DriveMode.AllPhotos -> AllPhotosContent(
                state = state,
                onBack = { mode = DriveMode.Home },
                onRefresh = { viewModel.refresh() },
                onRetryPending = { viewModel.retryPending(context, currentUserId) },
                onLoadMore = { viewModel.loadMore() },
                onOpenItem = { selectedItem = it },
                onOpenPending = { openLocalPendingMedia(context, it) },
                favoriteIds = favoriteIds,
                onToggleFavorite = { toggleFavorite(it) },
                onRetryPendingItem = { viewModel.retryPending(context, currentUserId, it) },
                onUploadClick = openPicker,
                modifier = Modifier.fillMaxSize()
            )
        }

        selectedItem?.let { item ->
            DriveMediaViewer(
                item = item,
                isFavorite = favoriteIds.contains(item.id),
                onClose = { selectedItem = null },
                onToggleFavorite = { toggleFavorite(item.id) },
                onDelete = { deleteCandidate = item },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.isUploading) {
            UploadingOverlay(
                done = state.uploadDone,
                total = state.uploadTotal,
                modifier = Modifier.fillMaxSize()
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

        state.infoMessage?.let { message ->
            InfoBanner(
                message = message,
                onDismiss = { viewModel.clearInfoMessage() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HelloSpacing.Lg)
            )
        }

        deleteCandidate?.let { item ->
            DeleteDriveItemDialog(
                item = item,
                isDeleting = state.deletingItemId == item.id,
                onCancel = { deleteCandidate = null },
                onConfirm = {
                    viewModel.deleteItem(item.id) {
                        removeFavorite(item.id)
                        if (selectedItem?.id == item.id) selectedItem = null
                        deleteCandidate = null
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
            text = "Drive",
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
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
            shape = HelloShapes.Lg,
            contentPadding = PaddingValues(vertical = 15.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = HelloColors.DarkBg)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Upload Photos",
                color = HelloColors.DarkBg,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
        AllPhotosCard(
            total = state.total + state.pendingItems.size,
            isLoading = state.isLoading,
            onClick = onOpenAllPhotos
        )

        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = HelloColors.DarkTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Centrally stored and safe.\nLatest to oldest.",
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AllPhotosCard(
    total: Int,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Lg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(HelloShapes.Md)
                    .background(HelloColors.DarkAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = HelloColors.DarkAccent
                )
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All Photos & Videos",
                    color = HelloColors.DarkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isLoading) "Loading..." else "$total items",
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HelloColors.DarkTextMuted
            )
        }
    }
}

@Composable
private fun AllPhotosContent(
    state: FamilyDriveUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetryPending: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItem: (DriveItem) -> Unit,
    onOpenPending: (PendingDriveItem) -> Unit,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onRetryPendingItem: (String) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val pendingCount = state.pendingItems.size
    val gridItems = remember(state.items, state.pendingItems) {
        (state.pendingItems.map { DriveGridItem.Pending(it) } + state.items.map { DriveGridItem.Synced(it) })
            .sortedByDescending { it.createdAt }
    }
    val groupedItems = remember(gridItems) {
        gridItems.groupBy { item -> item.monthLabel }
    }

    LaunchedEffect(gridState, gridItems.size, state.hasMore, state.isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                val total = gridState.layoutInfo.totalItemsCount
                if (total > 0 && lastVisible >= total - 9) onLoadMore()
            }
    }

    Column(
        modifier = modifier.padding(horizontal = HelloSpacing.ScreenPadding)
    ) {
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "All Photos & Videos",
                    color = HelloColors.DarkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${state.total + pendingCount} items",
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                TextButton(
                    onClick = {
                        if (pendingCount > 0) onRetryPending() else onRefresh()
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = if (pendingCount > 0) "Sync pending uploads" else "Refresh",
                        tint = if (pendingCount > 0) HelloColors.DarkAccent else HelloColors.DarkText
                    )
                }
                if (pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B5C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pendingCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Text(
            text = if (pendingCount > 0) "Grouped by month - $pendingCount waiting for PC" else "Grouped by month",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(HelloSpacing.Md))

        if (state.isLoading && gridItems.isEmpty()) {
            LoadingDrive(modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (gridItems.isEmpty()) {
            EmptyDrive(onUploadClick = onUploadClick, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedItems.forEach { (monthLabel, itemsInMonth) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MonthHeader(monthLabel = monthLabel, count = itemsInMonth.size)
                    }
                    items(itemsInMonth, key = { it.id }) { gridItem ->
                        DriveMediaCard(
                            item = gridItem,
                            isFavorite = gridItem is DriveGridItem.Synced && favoriteIds.contains(gridItem.item.id),
                            isRetrying = gridItem is DriveGridItem.Pending && state.retryingPendingId in setOf(gridItem.item.id, "all"),
                            onToggleFavorite = {
                                if (gridItem is DriveGridItem.Synced) onToggleFavorite(gridItem.item.id)
                            },
                            onRetryPending = {
                                if (gridItem is DriveGridItem.Pending) onRetryPendingItem(gridItem.item.id)
                            },
                            onClick = {
                                when (gridItem) {
                                    is DriveGridItem.Synced -> onOpenItem(gridItem.item)
                                    is DriveGridItem.Pending -> onOpenPending(gridItem.item)
                                }
                            }
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(HelloSpacing.Lg),
                            contentAlignment = Alignment.Center
                        ) {
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
private fun MonthHeader(monthLabel: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HelloSpacing.Md, bottom = HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthLabel,
            color = HelloColors.DarkText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DriveMediaCard(
    item: DriveGridItem,
    isFavorite: Boolean,
    isRetrying: Boolean,
    onToggleFavorite: () -> Unit,
    onRetryPending: () -> Unit,
    onClick: () -> Unit
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
        is DriveGridItem.Synced -> item.item.originalName
        is DriveGridItem.Pending -> item.item.displayName
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(HelloShapes.Md)
            .background(HelloColors.DarkPanelStrong)
            .clickable(onClick = onClick)
    ) {
        if (isVideo) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = HelloColors.DarkAccent,
                    modifier = Modifier.size(36.dp)
                )
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
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text("Video", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        when (item) {
            is DriveGridItem.Synced -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (isFavorite) 0.62f else 0.34f))
                        .clickable(onClick = onToggleFavorite)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = if (isFavorite) "Remove favorite" else "Favorite",
                        tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            is DriveGridItem.Pending -> {
                PendingUploadBadge(
                    item = item.item,
                    isRetrying = isRetrying,
                    onRetry = onRetryPending,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
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
    modifier: Modifier = Modifier
) {
    val tint = when (item.status) {
        PendingDriveStatus.FAILED_RETRYABLE -> Color(0xFFFFC107)
        PendingDriveStatus.UPLOADING -> HelloColors.DarkAccent
        else -> Color.White
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onRetry)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRetrying || item.status == PendingDriveStatus.UPLOADING) {
            CircularProgressIndicator(color = HelloColors.DarkAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = "Sync pending upload",
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    Box(
        modifier = modifier
            .padding(top = 30.dp)
            .clip(HelloShapes.Sm)
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = when (item.status) {
                PendingDriveStatus.FAILED_RETRYABLE -> "Retry"
                PendingDriveStatus.UPLOADING -> "Syncing"
                else -> "Pending"
            },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyDrive(onUploadClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = HelloColors.DarkAccent,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Text(
                text = "No photos yet",
                color = HelloColors.DarkText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Upload photos and videos to save them on this PC.",
                color = HelloColors.DarkTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = HelloSpacing.Xxl)
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))
            Button(
                onClick = onUploadClick,
                colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent),
                shape = HelloShapes.Lg
            ) {
                Text("Upload Now", color = HelloColors.DarkBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriveMediaViewer(
    item: DriveItem,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedUrl = UrlResolver.resolve(item.url)
    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(HelloSpacing.Lg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDateTime(item.createdAt),
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onToggleFavorite) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = if (isFavorite) "Remove favorite" else "Favorite",
                        tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = HelloColors.DarkAccent,
                            modifier = Modifier.size(78.dp)
                        )
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
                        model = ImageRequest.Builder(context)
                            .data(resolvedUrl)
                            .crossfade(false)
                            .allowHardware(true)
                            .build(),
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                        loading = { CircularProgressIndicator(color = HelloColors.DarkAccent) },
                        error = { Text("Photo load failed", color = Color.White) }
                    )
                }
            }
            Text(
                text = item.originalName ?: "Family Drive media",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Saved in All Photos & Videos - ${formatFileSize(item.size)}",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    shape = HelloShapes.Md,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Favorite", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = {
                        resolvedUrl?.let { downloadAttachment(context, it, item.originalName ?: "family-drive-media") }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    shape = HelloShapes.Md,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HelloColors.DarkDanger,
                        contentColor = Color.White
                    ),
                    shape = HelloShapes.Md,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DeleteDriveItemDialog(
    item: DriveItem,
    isDeleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onCancel() },
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkTextMuted,
        title = { Text("Delete from Drive?", fontWeight = FontWeight.Black) },
        text = {
            Text(
                text = "This removes ${item.originalName ?: "this item"} from the central family library.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkDanger),
                shape = HelloShapes.Md
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isDeleting) "Deleting..." else "Delete", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isDeleting) {
                Text("Cancel", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun UploadingOverlay(done: Int, total: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        HelloPanel(strong = true, shape = HelloShapes.Xl) {
            Column(
                modifier = Modifier.padding(HelloSpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = HelloColors.DarkAccent)
                Spacer(modifier = Modifier.height(HelloSpacing.Lg))
                Text("Uploading...", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text("$done / $total uploaded", color = HelloColors.DarkTextMuted)
                Spacer(modifier = Modifier.height(HelloSpacing.Sm))
                Text(
                    "Saving files to the PC central library.",
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                color = HelloColors.DarkDanger,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDismiss) {
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
            Text(
                text = message,
                color = HelloColors.DarkText,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
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
