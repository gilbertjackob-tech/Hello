package com.glassbox.hello.familydrive

import android.content.Intent
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

@Composable
fun FamilyDriveScreen(
    currentUserId: String,
    modifier: Modifier = Modifier,
    viewModel: FamilyDriveViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selectedItem by remember { mutableStateOf<DriveItem?>(null) }
    var mode by remember { mutableStateOf(DriveMode.Home) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(context, currentUserId, uris)
    }
    val openPicker = {
        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    selectedItem?.let { item ->
        DriveMediaViewer(
            item = item,
            onClose = { selectedItem = null },
            modifier = modifier.fillMaxSize()
        )
        return
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
                onLoadMore = { viewModel.loadMore() },
                onOpenItem = { selectedItem = it },
                onUploadClick = openPicker,
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
            total = state.total,
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
    onLoadMore: () -> Unit,
    onOpenItem: (DriveItem) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val groupedItems = remember(state.items) {
        state.items.groupBy { item -> item.monthLabel ?: monthLabelFromTimestamp(item.createdAt) }
    }

    LaunchedEffect(gridState, state.items.size, state.hasMore, state.isLoadingMore) {
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
                    text = "${state.total} items",
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = HelloColors.DarkText)
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Text(
            text = "Grouped by month",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(HelloSpacing.Md))

        if (state.isLoading && state.items.isEmpty()) {
            LoadingDrive(modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (state.items.isEmpty()) {
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
                    items(itemsInMonth, key = { it.id }) { item ->
                        DriveMediaCard(item = item, onClick = { onOpenItem(item) })
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
private fun DriveMediaCard(item: DriveItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(HelloShapes.Md)
            .background(HelloColors.DarkPanelStrong)
            .clickable(onClick = onClick)
    ) {
        if (item.isVideo) {
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
                    .data(UrlResolver.resolve(item.thumbnailUrl ?: item.url))
                    .crossfade(false)
                    .allowHardware(true)
                    .memoryCacheKey(item.id)
                    .diskCacheKey(item.id)
                    .build(),
                contentDescription = item.originalName,
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
        if (item.isVideo) {
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
private fun DriveMediaViewer(item: DriveItem, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
        }
    }
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
