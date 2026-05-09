package com.glassbox.hello.status

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TypeSpecimen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.network.HelloApiClient
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.components.HelloSectionHeader
import com.glassbox.hello.ui.components.HelloStatusAvatarRing
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.launch

private const val STATUS_TTL_MS = 24L * 60L * 60L * 1000L

@Composable
fun StatusScreen(
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val api = remember { HelloApiClient() }
    val scope = rememberCoroutineScope()
    var statusesState by remember { mutableStateOf<ResultState<List<ChatModels.StatusItem>>>(ResultState.Loading) }
    var selectedGroup by remember { mutableStateOf<StatusGroup?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var createOpen by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentUserId, reloadToken) {
        val result = api.fetchStatuses(currentUserId)
        statusesState = if (result.isSuccess) {
            val cutoff = System.currentTimeMillis() - STATUS_TTL_MS
            ResultState.Success(result.getOrNull().orEmpty().filter { it.timestamp >= cutoff }.sortedBy { it.timestamp })
        } else {
            ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load statuses")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            item {
                HelloTopBar(
                    eyebrow = "HELLO STORIES",
                    title = "Status",
                    modifier = Modifier.padding(top = HelloSpacing.Sm)
                ) {
                    HelloPill("24 hours", active = true)
                }
            }
            item {
                Text("Status updates disappear after 24 hours.", color = HelloColors.DarkTextMuted)
            }
            when (val state = statusesState) {
                is ResultState.Loading -> item { LoadingView(modifier = Modifier.height(320.dp)) }
                is ResultState.Error -> item {
                    ErrorView(message = state.message, onRetry = {
                        statusesState = ResultState.Loading
                        reloadToken += 1
                    })
                }
                is ResultState.Success -> {
                    val groups = state.data.groupBy { it.userId }.map { (userId, statuses) ->
                        val latest = statuses.maxByOrNull { it.timestamp }
                        StatusGroup(
                            userId = userId,
                            userName = latest?.userName ?: if (userId == currentUserId) "My status" else "Hello user",
                            userAvatar = latest?.userAvatar,
                            statuses = statuses.sortedBy { it.timestamp }
                        )
                    }
                    val myGroup = groups.firstOrNull { it.userId == currentUserId }
                    val others = groups.filter { it.userId != currentUserId }

                    item {
                        MyStatusCard(
                            group = myGroup,
                            onOpen = {
                                if (myGroup != null) {
                                    selectedGroup = myGroup
                                    selectedIndex = 0
                                } else {
                                    createOpen = true
                                }
                            },
                            onCreate = { createOpen = true }
                        )
                    }

                    item { HelloSectionHeader("Recent updates") }

                    if (others.isEmpty()) {
                        item {
                            HelloEmptyState(
                                title = "No status updates",
                                message = "Family stories will appear here for 24 hours."
                            )
                        }
                    } else {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)) {
                                items(others, key = { it.userId }) { group ->
                                    StatusRingCard(
                                        group = group,
                                        viewed = group.statuses.all { s -> s.views?.any { it["userId"] == currentUserId } == true },
                                        onClick = {
                                            selectedGroup = group
                                            selectedIndex = 0
                                            group.statuses.firstOrNull()?.let { status ->
                                                scope.launch { api.markStatusViewed(status.id, currentUserId) }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(HelloSpacing.Xxl)) }
        }

        selectedGroup?.let { group ->
            StatusViewerScreen(
                group = group,
                index = selectedIndex,
                currentUserId = currentUserId,
                onIndex = { next ->
                    selectedIndex = next
                    group.statuses.getOrNull(next)?.let { status ->
                        scope.launch { api.markStatusViewed(status.id, currentUserId) }
                    }
                },
                onClose = {
                    selectedGroup = null
                    reloadToken += 1
                }
            )
        }

        if (createOpen) {
            CreateStatusDialog(
                currentUserId = currentUserId,
                api = api,
                onClose = { createOpen = false },
                onPosted = {
                    createOpen = false
                    reloadToken += 1
                }
            )
        }
    }
}

private data class StatusGroup(
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val statuses: List<ChatModels.StatusItem>
)

@Composable
private fun MyStatusCard(group: StatusGroup?, onOpen: () -> Unit, onCreate: () -> Unit) {
    HelloPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen), strong = true, shape = HelloShapes.Lg) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloStatusAvatarRing(name = "Me", seen = false, online = true)
            Column(modifier = Modifier.weight(1f)) {
                Text("My status", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(group?.let { "${it.statuses.size} updates - ${relativeTime(it.statuses.last().timestamp)}" } ?: "Tap to add status update", color = HelloColors.DarkTextMuted)
            }
            HelloPrimaryButton(text = "Add status", onClick = onCreate, modifier = Modifier.width(128.dp))
        }
    }
}

@Composable
private fun StatusRingCard(group: StatusGroup, viewed: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HelloStatusAvatarRing(name = group.userName, seen = viewed, online = true, imageUrl = group.userAvatar)
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Text(group.userName, color = HelloColors.DarkText, maxLines = 1)
        Text(relativeTime(group.statuses.last().timestamp), color = HelloColors.DarkTextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusViewerScreen(
    group: StatusGroup,
    index: Int,
    currentUserId: String,
    onIndex: (Int) -> Unit,
    onClose: () -> Unit
) {
    val status = group.statuses.getOrNull(index) ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .padding(HelloSpacing.Lg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs), modifier = Modifier.fillMaxWidth()) {
                group.statuses.forEachIndexed { i, _ ->
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(if (i <= index) HelloColors.DarkAccent else HelloColors.DarkBorderStrong, HelloShapes.Pill)
                    )
                }
            }
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HelloAvatar(group.userName, imageUrl = group.userAvatar, online = true)
                Spacer(modifier = Modifier.width(HelloSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.userName, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    Text(relativeTime(status.timestamp), color = HelloColors.DarkTextMuted)
                }
                HelloIconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close status", tint = HelloColors.DarkText)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable {
                        if (index < group.statuses.lastIndex) onIndex(index + 1) else onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                StatusContent(status)
                if (status.userId == currentUserId) {
                    HelloPill("${status.views?.size ?: 0} views", modifier = Modifier.align(Alignment.BottomCenter), active = true)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HelloPill("Previous", modifier = Modifier.clickable { if (index > 0) onIndex(index - 1) })
                HelloPill("Next", active = true, modifier = Modifier.clickable { if (index < group.statuses.lastIndex) onIndex(index + 1) else onClose() })
            }
        }
    }
}

@Composable
private fun StatusContent(status: ChatModels.StatusItem) {
    val resolved = UrlResolver.resolve(status.attachmentUrl)
    when {
        status.attachmentType?.startsWith("image") == true && resolved != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(resolved)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = "Status image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = { ImageStatusPlaceholder("Image unavailable") },
                    loading = { ImageStatusPlaceholder("Loading image") }
                )
                if (!status.text.isNullOrBlank()) {
                    StatusOverlayText(
                        text = status.text,
                        color = Color.White,
                        fontFamily = FontFamily.Default,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(HelloSpacing.Xxl)
                    )
                }
            }
        }
        else -> {
            val color = parseColor(status.backgroundColor)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, HelloShapes.Xl)
                    .padding(HelloSpacing.Xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(status.text.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun ImageStatusPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = HelloColors.DarkTextMuted)
    }
}

@Composable
private fun StatusOverlayText(
    text: String,
    color: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.34f), HelloShapes.Md)
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md)
    )
}

@Composable
private fun CreateStatusDialog(
    currentUserId: String,
    api: HelloApiClient,
    onClose: () -> Unit,
    onPosted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("#0f8f78") }
    var fontStyle by remember { mutableStateOf(StatusFont.Normal) }
    var textColor by remember { mutableStateOf(StatusTextColor.White) }
    var overlayAlign by remember { mutableStateOf(StatusOverlayAlign.Bottom) }
    var picked by remember { mutableStateOf<PickedStatusFile?>(null) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picked = uri?.let { readPickedStatusFile(context, it) }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Create status", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                Text("Share a thought or photo. Status disappears after 24 hours.", color = HelloColors.DarkTextMuted)
                StatusPreview(
                    text = text,
                    background = background,
                    picked = picked,
                    fontStyle = fontStyle,
                    textColor = textColor,
                    overlayAlign = overlayAlign
                )
                HelloSearchBar(value = text, onValueChange = { text = it }, placeholder = "Bangla or English overlay text")
                HelloSettingsMediaRow(
                    label = picked?.name ?: "Photo status",
                    helper = picked?.mimeType ?: "Pick a photo",
                    onClick = { picker.launch("image/*") }
                )
                StatusChoiceRow("Font", StatusFont.values().map { it.label }, fontStyle.label) { label ->
                    fontStyle = StatusFont.values().first { it.label == label }
                }
                StatusChoiceRow("Text color", StatusTextColor.values().map { it.label }, textColor.label) { label ->
                    textColor = StatusTextColor.values().first { it.label == label }
                }
                StatusChoiceRow("Text position", StatusOverlayAlign.values().map { it.label }, overlayAlign.label) { label ->
                    overlayAlign = StatusOverlayAlign.values().first { it.label == label }
                }
                if (picked == null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                        items(listOf("#0f8f78", "#8b5cf6", "#f43f5e", "#3b82f6", "#eab308", "#222222")) { c ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(parseColor(c), HelloShapes.Pill)
                                    .clickable { background = c }
                            )
                        }
                    }
                }
                if (error != null) Text(error.orEmpty(), color = HelloColors.DarkDanger)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !posting && (text.trim().isNotBlank() || picked != null),
                onClick = {
                    posting = true
                    scope.launch {
                        val upload = picked?.let { api.uploadFile(it.name, it.mimeType, it.bytes, currentUserId) }
                        if (upload?.isFailure == true) {
                            error = upload.exceptionOrNull()?.message ?: "Upload failed"
                            posting = false
                            return@launch
                        }
                        val uploaded = upload?.getOrNull()
                        val result = api.createStatus(
                            userId = currentUserId,
                            text = text.trim(),
                            attachmentUrl = uploaded?.url,
                            attachmentType = uploaded?.mimeType,
                            backgroundColor = if (uploaded == null) background else "",
                            duration = 5000
                        )
                        posting = false
                        if (result.isSuccess) onPosted() else error = result.exceptionOrNull()?.message ?: "Failed to post status"
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = HelloColors.DarkAccent)
                Text("Post", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = HelloColors.DarkTextMuted) }
        }
    )
}

private enum class StatusFont(val label: String, val family: FontFamily) {
    Normal("Normal", FontFamily.Default),
    Bold("Bold", FontFamily.Default),
    Serif("Serif", FontFamily.Serif),
    Monospace("Monospace", FontFamily.Monospace)
}

private enum class StatusTextColor(val label: String, val color: Color) {
    White("White", Color.White),
    Black("Black", Color.Black),
    HelloGreen("Hello green", HelloColors.DarkAccent),
    Red("Red", Color(0xFFFF5555)),
    Yellow("Yellow", Color(0xFFFFE15A))
}

private enum class StatusOverlayAlign(val label: String) {
    Center("Center"),
    Bottom("Bottom")
}

@Composable
private fun StatusPreview(
    text: String,
    background: String,
    picked: PickedStatusFile?,
    fontStyle: StatusFont,
    textColor: StatusTextColor,
    overlayAlign: StatusOverlayAlign
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(parseColor(background), HelloShapes.Xl),
        contentAlignment = Alignment.Center
    ) {
        if (picked != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(picked.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Selected status photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { ImageStatusPlaceholder("Loading photo") },
                error = { ImageStatusPlaceholder("Photo unavailable") }
            )
        }
        if (text.isBlank() && picked == null) {
            Text(
                "Type a status",
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
        }
        if (text.isNotBlank()) {
            val align = if (overlayAlign == StatusOverlayAlign.Bottom) Alignment.BottomCenter else Alignment.Center
            StatusOverlayText(
                text = text,
                color = textColor.color,
                fontFamily = fontStyle.family,
                modifier = Modifier
                    .align(align)
                    .padding(HelloSpacing.Xxl)
            )
        }
    }
}

@Composable
private fun StatusChoiceRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
        Text(label, color = HelloColors.DarkTextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            items(options) { option ->
                Surface(
                    onClick = { onSelected(option) },
                    shape = HelloShapes.Pill,
                    color = if (option == selected) HelloColors.DarkAccentSoft else HelloColors.DarkPanelMuted,
                    border = BorderStroke(1.dp, if (option == selected) HelloColors.DarkAccent else HelloColors.DarkBorder)
                ) {
                    Text(
                        option,
                        color = if (option == selected) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HelloSettingsMediaRow(label: String, helper: String, onClick: () -> Unit) {
    HelloPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), strong = false, shape = HelloShapes.Md) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column {
                Text(label, color = HelloColors.DarkText)
                Text(helper, color = HelloColors.DarkTextMuted)
            }
        }
    }
}

private data class PickedStatusFile(val uri: Uri, val name: String, val mimeType: String, val bytes: ByteArray)

private fun readPickedStatusFile(context: Context, uri: Uri): PickedStatusFile? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: "status-media"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedStatusFile(uri, name, mimeType, bytes)
}

private fun relativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val minutes = diff / 60000
    val hours = minutes / 60
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes} min ago"
        hours < 24 -> "${hours}h ago"
        else -> "Expired"
    }
}

private fun parseColor(value: String?): Color {
    return try {
        Color(android.graphics.Color.parseColor(value ?: "#8b5cf6"))
    } catch (_: Exception) {
        HelloColors.DarkPanelStrong
    }
}
