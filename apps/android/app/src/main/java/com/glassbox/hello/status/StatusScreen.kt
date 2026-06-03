package com.glassbox.hello.status

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

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
    var searchQuery by remember { mutableStateOf("") }

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
            item {
                HelloSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search stories",
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val others = groups
                        .filter { it.userId != currentUserId }
                        .filter { searchQuery.isBlank() || it.userName.contains(searchQuery, ignoreCase = true) }

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

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            HelloPill("Recent", active = true)
                            HelloPill("Muted")
                            HelloPill("Viewed")
                        }
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
                        items(others, key = { it.userId }) { group ->
                            StatusListRow(
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
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)) {
                                items(others.take(8), key = { "ring-${it.userId}" }) { group ->
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
private fun StatusListRow(group: StatusGroup, viewed: Boolean, onClick: () -> Unit) {
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        strong = true,
        shape = HelloShapes.Lg
    ) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloStatusAvatarRing(name = group.userName, seen = viewed, online = true, imageUrl = group.userAvatar)
            Column(modifier = Modifier.weight(1f)) {
                Text(group.userName, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(
                    "${group.statuses.size} update${if (group.statuses.size == 1) "" else "s"} - ${relativeTime(group.statuses.last().timestamp)}",
                    color = HelloColors.DarkTextMuted
                )
            }
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                tint = if (viewed) HelloColors.StoryRingSeen else HelloColors.StoryRingUnseen
            )
        }
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
                            .background(if (i <= index) HelloColors.StoryProgressActive else HelloColors.StoryProgressInactive, HelloShapes.Pill)
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
                    HelloPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = HelloSpacing.Lg),
                        strong = false,
                        shape = HelloShapes.Pill
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = HelloColors.StoryAccent, modifier = Modifier.size(18.dp))
                            Text("${status.views?.size ?: 0} views", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                        }
                    }
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
    boxStyle: StatusTextBoxStyle = StatusTextBoxStyle.Glass,
    modifier: Modifier = Modifier
) {
    val overlayBg = when (boxStyle) {
        StatusTextBoxStyle.Glass -> Color.Black.copy(alpha = 0.34f)
        StatusTextBoxStyle.Solid -> Color(0xE6101218)
        StatusTextBoxStyle.Clean -> Color.Transparent
    }
    Text(
        text = text,
        color = color,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        modifier = modifier
            .background(overlayBg, HelloShapes.Md)
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
    var background by remember { mutableStateOf(colorToHex(HelloColors.StoryCanvasBackground)) }
    var fontStyle by remember { mutableStateOf(StatusFont.Normal) }
    var textColor by remember { mutableStateOf(StatusTextColor.White) }
    var look by remember { mutableStateOf(StatusImageLook.Natural) }
    var boxStyle by remember { mutableStateOf(StatusTextBoxStyle.Glass) }
    var overlayOffsetX by remember { mutableStateOf(0f) }
    var overlayOffsetY by remember { mutableStateOf(0.22f) }
    var picked by remember { mutableStateOf<PickedStatusFile?>(null) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picked = uri?.let { readPickedStatusFile(context, it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg.copy(alpha = 0.98f))
            .safeDrawingPadding()
            .padding(HelloSpacing.Lg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                HelloIconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close editor", tint = HelloColors.DarkText)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Create status", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    Text("Edit directly on the story canvas, then post the final image.", color = HelloColors.DarkTextMuted)
                }
                TextButton(
                    enabled = !posting && (text.trim().isNotBlank() || picked != null),
                    onClick = {
                        posting = true
                        error = null
                        scope.launch {
                            val upload = picked?.let { photo ->
                                runCatching {
                                    renderEditedStatusImage(
                                        sourceBytes = photo.bytes,
                                        caption = text.trim(),
                                        look = look,
                                        textColor = textColor.color.toArgb(),
                                        font = fontStyle,
                                        boxStyle = boxStyle,
                                        offsetX = overlayOffsetX,
                                        offsetY = overlayOffsetY
                                    )
                                }.fold(
                                    onSuccess = { rendered ->
                                        api.uploadFile(
                                            "status-${System.currentTimeMillis()}.jpg",
                                            "image/jpeg",
                                            rendered,
                                            currentUserId
                                        )
                                    },
                                    onFailure = { Result.failure(it) }
                                )
                            }
                            if (upload?.isFailure == true) {
                                error = upload.exceptionOrNull()?.message ?: "Upload failed"
                                posting = false
                                return@launch
                            }
                            val uploaded = upload?.getOrNull()
                            val result = api.createStatus(
                                userId = currentUserId,
                                text = if (uploaded == null) text.trim() else "",
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
                    Icon(Icons.Default.Send, contentDescription = null, tint = HelloColors.StoryPrimaryButton)
                    Text(if (posting) "Posting..." else "Post", color = HelloColors.StoryPrimaryButton)
                }
            }

            DirectEditStatusPreview(
                text = text,
                background = background,
                picked = picked,
                fontStyle = fontStyle,
                textColor = textColor,
                boxStyle = boxStyle,
                look = look,
                overlayOffsetX = overlayOffsetX,
                overlayOffsetY = overlayOffsetY,
                onOverlayOffsetChange = { x, y ->
                    overlayOffsetX = x
                    overlayOffsetY = y
                }
            )

            HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
                Column(
                    modifier = Modifier.padding(HelloSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                ) {
                    HelloSearchBar(value = text, onValueChange = { text = it }, placeholder = "Type directly on the story canvas")
                    HelloSettingsMediaRow(
                        label = picked?.name ?: "Choose story photo",
                        helper = if (picked == null) "Pick a photo from device" else "Replace the current photo",
                        onClick = { picker.launch("image/*") }
                    )
                    if (picked != null) {
                        StatusChoiceRow("Look", StatusImageLook.entries.map { it.label }, look.label) { label ->
                            look = StatusImageLook.entries.first { it.label == label }
                        }
                    }
                    StatusChoiceRow("Font", StatusFont.entries.map { it.label }, fontStyle.label) { label ->
                        fontStyle = StatusFont.entries.first { it.label == label }
                    }
                    StatusChoiceRow("Text color", StatusTextColor.entries.map { it.label }, textColor.label) { label ->
                        textColor = StatusTextColor.entries.first { it.label == label }
                    }
                    StatusChoiceRow("Text box", StatusTextBoxStyle.entries.map { it.label }, boxStyle.label) { label ->
                        boxStyle = StatusTextBoxStyle.entries.first { it.label == label }
                    }
                    if (picked == null) {
                        val storyPalette = listOf(
                            colorToHex(HelloColors.StoryCanvasBackground),
                            colorToHex(HelloColors.StoryAccent),
                            colorToHex(HelloColors.Accent),
                            colorToHex(HelloColors.Warning),
                            colorToHex(HelloColors.Danger),
                            colorToHex(HelloColors.BgStrong)
                        ).distinct()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            items(storyPalette) { c ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(HelloShapes.Pill)
                                        .background(parseColor(c))
                                        .clickable { background = c }
                                )
                            }
                        }
                    }
                    Text(
                        "Drag the caption directly on the preview. Image stories are exported with the selected look and text baked in.",
                        color = HelloColors.DarkTextMuted
                    )
                    if (error != null) Text(error.orEmpty(), color = HelloColors.DarkDanger)
                }
            }
        }
    }
}

enum class StatusFont(val label: String, val family: FontFamily) {
    Normal("Normal", FontFamily.Default),
    Bold("Bold", FontFamily.Default),
    Serif("Serif", FontFamily.Serif),
    Monospace("Monospace", FontFamily.Monospace)
}

private enum class StatusTextColor(val label: String, val color: Color) {
    White("Ivory", Color(0xFFFFFBF4)),
    Black("Ink", Color(0xFF101418)),
    HelloGreen("Mint", HelloColors.DarkAccent),
    Red("Rose", Color(0xFFFF7388)),
    Yellow("Sun", Color(0xFFFFE15A))
}

@Composable
private fun DirectEditStatusPreview(
    text: String,
    background: String,
    picked: PickedStatusFile?,
    fontStyle: StatusFont,
    textColor: StatusTextColor,
    boxStyle: StatusTextBoxStyle,
    look: StatusImageLook,
    overlayOffsetX: Float,
    overlayOffsetY: Float,
    onOverlayOffsetChange: (Float, Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .clip(HelloShapes.Xl)
            .background(parseColor(background)),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        if (picked != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(picked.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Selected status photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = if (look == StatusImageLook.Natural) null else ColorFilter.colorMatrix(ColorMatrix(statusLookMatrix(look))),
                loading = { ImageStatusPlaceholder("Loading photo") },
                error = { ImageStatusPlaceholder("Photo unavailable") }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (picked == null) 0.08f else 0.12f))
        )
        if (text.isBlank()) {
            Text(
                if (picked == null) "Type a status" else "Add text or post the photo as-is",
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
        }
        if (text.isNotBlank()) {
            StatusOverlayText(
                text = text,
                color = textColor.color,
                fontFamily = fontStyle.family,
                boxStyle = boxStyle,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (overlayOffsetX * widthPx * 0.42f).roundToInt(),
                            y = (overlayOffsetY * heightPx * 0.42f).roundToInt()
                        )
                    }
                    .pointerInput(text, picked?.name, boxStyle.label) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val nextX = overlayOffsetX + (dragAmount.x / (widthPx * 0.42f))
                            val nextY = overlayOffsetY + (dragAmount.y / (heightPx * 0.42f))
                            onOverlayOffsetChange(nextX.coerceIn(-0.38f, 0.38f), nextY.coerceIn(-0.42f, 0.42f))
                        }
                    }
            )
        }
        HelloPill(
            text = if (picked != null) "Drag text on image" else "Text status",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HelloSpacing.Lg),
            active = true
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HelloSpacing.Md)
                .background(HelloColors.StoryToolRailBackground, HelloShapes.Pill)
                .padding(vertical = HelloSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            Icon(Icons.Default.TypeSpecimen, contentDescription = null, tint = HelloColors.StoryPopupText, modifier = Modifier.size(20.dp))
            Icon(Icons.Default.Palette, contentDescription = null, tint = HelloColors.StoryPopupText, modifier = Modifier.size(20.dp))
            Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.StoryPopupText, modifier = Modifier.size(20.dp))
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

private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return "#%06X".format(0xFFFFFF and argb)
}
