package com.glassbox.hello.status

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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

private enum class StoryDraftKind {
    Text,
    Media
}

private data class StatusGroup(
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val stories: List<FirestoreStatusStory>
)

private data class PickedStoryFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

private data class StoryTheme(
    val id: String,
    val label: String,
    val color: Color
)

private data class StoryFont(
    val id: String,
    val label: String,
    val family: FontFamily,
    val weight: FontWeight
)

private val storyThemes = listOf(
    StoryTheme("pink", "Pink", Color(0xFFF472B6)),
    StoryTheme("rose", "Rose", Color(0xFFFB7185)),
    StoryTheme("plum", "Plum", Color(0xFFA855F7)),
    StoryTheme("sky", "Sky", Color(0xFF38BDF8)),
    StoryTheme("mint", "Mint", Color(0xFF34D399)),
    StoryTheme("amber", "Amber", Color(0xFFFBBF24))
)

private val storyFonts = listOf(
    StoryFont("bold", "Bold", FontFamily.Default, FontWeight.Black),
    StoryFont("soft", "Soft", FontFamily.Serif, FontWeight.Bold),
    StoryFont("clean", "Clean", FontFamily.SansSerif, FontWeight.SemiBold),
    StoryFont("mono", "Mono", FontFamily.Monospace, FontWeight.Bold)
)

private val storyTextColors = listOf(
    "#fff7fb" to "Ivory",
    "#101418" to "Ink",
    "#fef08a" to "Sun",
    "#bbf7d0" to "Mint",
    "#dbeafe" to "Cloud"
)

@Composable
fun StatusScreen(
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val api = remember { HelloApiClient() }
    val repository = remember { StatusFirestoreRepository() }
    val scope = rememberCoroutineScope()
    var storiesState by remember { mutableStateOf<ResultState<List<FirestoreStatusStory>>>(ResultState.Loading) }
    var currentUserName by remember { mutableStateOf("Me") }
    var currentUserAvatar by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<StatusGroup?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var createModeOpen by remember { mutableStateOf(false) }
    var draftKind by remember { mutableStateOf<StoryDraftKind?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    fun refreshStories() {
        storiesState = ResultState.Loading
        reloadToken += 1
    }

    LaunchedEffect(currentUserId) {
        api.fetchUser(currentUserId).getOrNull()?.let { user ->
            currentUserName = user.name
            currentUserAvatar = user.avatar
        }
    }

    LaunchedEffect(currentUserId, reloadToken) {
        val result = repository.fetchActiveStories()
        storiesState = result.fold(
            onSuccess = { ResultState.Success(it) },
            onFailure = { ResultState.Error(it.message ?: "Failed to load stories") }
        )
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
                Text("Share a quick text, photo, or video story.", color = HelloColors.DarkTextMuted)
            }

            when (val state = storiesState) {
                is ResultState.Loading -> item { LoadingView(modifier = Modifier.height(320.dp)) }
                is ResultState.Error -> item { ErrorView(message = state.message, onRetry = { refreshStories() }) }
                is ResultState.Success -> {
                    val groups = state.data.groupBy { it.userId }.map { (userId, stories) ->
                        val latest = stories.maxByOrNull { it.createdAt }
                        StatusGroup(
                            userId = userId,
                            userName = latest?.userName ?: if (userId == currentUserId) "My status" else "Hello user",
                            userAvatar = latest?.userAvatar,
                            stories = stories.sortedBy { it.createdAt }
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
                                    createModeOpen = true
                                }
                            },
                            onCreate = { createModeOpen = true }
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
                        items(others, key = { it.userId }) { group ->
                            StatusListRow(
                                group = group,
                                viewed = group.stories.all { it.viewers.containsKey(currentUserId) },
                                onClick = {
                                    selectedGroup = group
                                    selectedIndex = 0
                                    group.stories.firstOrNull()?.let { story ->
                                        scope.launch { repository.markViewed(story.id, currentUserId) }
                                    }
                                }
                            )
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
                    group.stories.getOrNull(next)?.let { story ->
                        scope.launch { repository.markViewed(story.id, currentUserId) }
                    }
                },
                onClose = {
                    selectedGroup = null
                    refreshStories()
                }
            )
        }

        if (createModeOpen) {
            StoryTypeDialog(
                onDismiss = { createModeOpen = false },
                onText = {
                    createModeOpen = false
                    draftKind = StoryDraftKind.Text
                },
                onMedia = {
                    createModeOpen = false
                    draftKind = StoryDraftKind.Media
                }
            )
        }

        draftKind?.let { kind ->
            SimpleStoryComposer(
                kind = kind,
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserAvatar = currentUserAvatar,
                api = api,
                repository = repository,
                onClose = { draftKind = null },
                onPosted = {
                    draftKind = null
                    refreshStories()
                }
            )
        }
    }
}

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
                Text(group?.let { "${it.stories.size} updates - ${relativeTime(it.stories.last().createdAt)}" } ?: "Tap to add status update", color = HelloColors.DarkTextMuted)
            }
            HelloPrimaryButton(text = "Add status", onClick = onCreate, modifier = Modifier.width(128.dp))
        }
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
                    "${group.stories.size} update${if (group.stories.size == 1) "" else "s"} - ${relativeTime(group.stories.last().createdAt)}",
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
    val story = group.stories.getOrNull(index) ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .padding(HelloSpacing.Lg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs), modifier = Modifier.fillMaxWidth()) {
                group.stories.forEachIndexed { i, _ ->
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
                    Text(relativeTime(story.createdAt), color = HelloColors.DarkTextMuted)
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
                        if (index < group.stories.lastIndex) onIndex(index + 1) else onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                StoryCanvas(story = story, editable = false)
                if (story.userId == currentUserId) {
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
                            Text("${story.viewers.size} views", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HelloPill("Previous", modifier = Modifier.clickable { if (index > 0) onIndex(index - 1) })
                HelloPill("Next", active = true, modifier = Modifier.clickable { if (index < group.stories.lastIndex) onIndex(index + 1) else onClose() })
            }
        }
    }
}

@Composable
private fun StoryTypeDialog(onDismiss: () -> Unit, onText: () -> Unit, onMedia: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkTextMuted,
        title = { Text("Create story", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                StoryTypeOption(
                    icon = Icons.Default.TextFields,
                    title = "Text story",
                    subtitle = "Cute backgrounds, fonts, and movable text",
                    onClick = onText
                )
                StoryTypeOption(
                    icon = Icons.Default.Image,
                    title = "Image / Video story",
                    subtitle = "Pick media and add styled overlay text",
                    onClick = onMedia
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun StoryTypeOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    HelloPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), strong = true, shape = HelloShapes.Lg) {
        Row(modifier = Modifier.padding(HelloSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HelloColors.DarkAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Spacer(modifier = Modifier.width(HelloSpacing.Md))
            Column {
                Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Black)
                Text(subtitle, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SimpleStoryComposer(
    kind: StoryDraftKind,
    currentUserId: String,
    currentUserName: String,
    currentUserAvatar: String?,
    api: HelloApiClient,
    repository: StatusFirestoreRepository,
    onClose: () -> Unit,
    onPosted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(storyThemes.first()) }
    var selectedFont by remember { mutableStateOf(storyFonts.first()) }
    var selectedTextColor by remember { mutableStateOf(storyTextColors.first().first) }
    var picked by remember { mutableStateOf<PickedStoryFile?>(null) }
    var transform by remember { mutableStateOf(StoryTextTransform(y = 0.16f)) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picked = uri?.let { readPickedStoryFile(context, it) }
    }

    LaunchedEffect(kind) {
        if (kind == StoryDraftKind.Media && picked == null) {
            mediaPicker.launch(arrayOf("image/*", "video/*"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(HelloSpacing.Lg)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HelloIconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close composer", tint = HelloColors.DarkText)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (kind == StoryDraftKind.Text) "Text story" else "Image / Video story", color = HelloColors.DarkText, fontWeight = FontWeight.Black)
                    Text("Simple canvas, premium text controls.", color = HelloColors.DarkTextMuted)
                }
                TextButton(
                    enabled = !posting && (text.isNotBlank() || picked != null),
                    onClick = {
                        posting = true
                        error = null
                        scope.launch {
                            val uploaded = if (picked != null) {
                                api.uploadFile(
                                    fileName = picked!!.name,
                                    mimeType = picked!!.mimeType,
                                    bytes = picked!!.bytes,
                                    uploaderId = currentUserId
                                ).getOrElse {
                                    error = it.message ?: "Media upload failed"
                                    posting = false
                                    return@launch
                                }
                            } else {
                                null
                            }
                            val now = System.currentTimeMillis()
                            val story = FirestoreStatusStory(
                                id = "status_${now}_${currentUserId.hashCode()}",
                                userId = currentUserId,
                                userName = currentUserName,
                                userAvatar = currentUserAvatar,
                                kind = when {
                                    picked?.isVideo == true -> "video"
                                    picked != null -> "image"
                                    else -> "text"
                                },
                                text = text.trim(),
                                mediaUrl = uploaded?.url,
                                mediaType = uploaded?.mimeType,
                                backgroundThemeId = selectedTheme.id,
                                backgroundColor = colorToHex(selectedTheme.color),
                                fontId = selectedFont.id,
                                textColor = selectedTextColor,
                                textTransform = transform,
                                durationMs = 5000L,
                                createdAt = now,
                                expiresAt = now + STATUS_TTL_MS,
                                viewers = emptyMap()
                            )
                            repository.createStory(story).fold(
                                onSuccess = {
                                    posting = false
                                    onPosted()
                                },
                                onFailure = {
                                    posting = false
                                    error = it.message ?: "Story could not be posted"
                                }
                            )
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = HelloColors.StoryPrimaryButton)
                    Text(if (posting) "Posting..." else "Post", color = HelloColors.StoryPrimaryButton, fontWeight = FontWeight.Bold)
                }
            }

            StoryCanvas(
                story = FirestoreStatusStory(
                    id = "draft",
                    userId = currentUserId,
                    userName = currentUserName,
                    userAvatar = currentUserAvatar,
                    kind = if (picked?.isVideo == true) "video" else if (picked != null) "image" else "text",
                    text = text,
                    mediaUrl = picked?.uri?.toString(),
                    mediaType = picked?.mimeType,
                    backgroundThemeId = selectedTheme.id,
                    backgroundColor = colorToHex(selectedTheme.color),
                    fontId = selectedFont.id,
                    textColor = selectedTextColor,
                    textTransform = transform,
                    durationMs = 5000L,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + STATUS_TTL_MS,
                    viewers = emptyMap()
                ),
                editable = true,
                onTransform = { transform = it },
                modifier = Modifier.weight(1f)
            )

            HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
                Column(modifier = Modifier.padding(HelloSpacing.Md), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(if (kind == StoryDraftKind.Text) "Write your story" else "Add text over media") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (kind == StoryDraftKind.Media) {
                        Button(
                            onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkPanelMuted),
                            shape = HelloShapes.Md,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(HelloSpacing.Sm))
                            Text(picked?.name ?: "Choose image / video", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChoiceRow("Background", storyThemes.map { it.label }, selectedTheme.label) { label ->
                        selectedTheme = storyThemes.first { it.label == label }
                    }
                    ChoiceRow("Font", storyFonts.map { it.label }, selectedFont.label) { label ->
                        selectedFont = storyFonts.first { it.label == label }
                    }
                    ChoiceRow("Text", storyTextColors.map { it.second }, storyTextColors.first { it.first == selectedTextColor }.second) { label ->
                        selectedTextColor = storyTextColors.first { it.second == label }.first
                    }
                    if (error != null) Text(error.orEmpty(), color = HelloColors.DarkDanger)
                }
            }
        }
    }
}

@Composable
private fun StoryCanvas(
    story: FirestoreStatusStory,
    editable: Boolean,
    onTransform: (StoryTextTransform) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(HelloShapes.Xl)
            .background(parseColor(story.backgroundColor)),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        StoryMedia(story)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (story.kind == "text") 0.05f else 0.16f)))
        if (story.text.isBlank()) {
            Text(
                if (editable) "Add text" else "",
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
        } else {
            StoryOverlayText(
                story = story,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (story.textTransform.x * widthPx * 0.42f).roundToInt(),
                            y = (story.textTransform.y * heightPx * 0.42f).roundToInt()
                        )
                    }
                    .scale(story.textTransform.scale, story.textTransform.scale * story.textTransform.widthScale)
                    .rotate(story.textTransform.rotation)
                    .then(
                        if (editable) {
                            Modifier.pointerInput(story.textTransform) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    onTransform(
                                        story.textTransform.copy(
                                            x = (story.textTransform.x + pan.x / (widthPx * 0.42f)).coerceIn(-0.44f, 0.44f),
                                            y = (story.textTransform.y + pan.y / (heightPx * 0.42f)).coerceIn(-0.48f, 0.48f),
                                            scale = (story.textTransform.scale * zoom).coerceIn(0.7f, 2.2f),
                                            rotation = (story.textTransform.rotation + rotation).coerceIn(-28f, 28f)
                                        )
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (editable) {
                            Modifier.pointerInput(story.textTransform.widthScale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onTransform(
                                        story.textTransform.copy(
                                            widthScale = (story.textTransform.widthScale + dragAmount.x / widthPx).coerceIn(0.72f, 1.45f)
                                        )
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            )
        }
        if (editable) {
            HelloPill(
                text = "Drag, pinch, rotate text",
                active = true,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = HelloSpacing.Md)
            )
        }
    }
}

@Composable
private fun StoryMedia(story: FirestoreStatusStory) {
    val mediaUrl = story.mediaUrl ?: return
    val resolved = UrlResolver.resolve(mediaUrl) ?: mediaUrl
    when (story.kind) {
        "video" -> AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.parse(resolved))
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        start()
                    }
                }
            },
            update = { view ->
                if (!view.isPlaying) {
                    view.setVideoURI(Uri.parse(resolved))
                    view.start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        "image" -> SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(resolved)
                .crossfade(true)
                .build(),
            contentDescription = "Story image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { StoryPlaceholder("Loading image") },
            error = { StoryPlaceholder("Image unavailable") }
        )
    }
}

@Composable
private fun StoryOverlayText(story: FirestoreStatusStory, modifier: Modifier = Modifier) {
    val font = storyFonts.firstOrNull { it.id == story.fontId } ?: storyFonts.first()
    Text(
        text = story.text,
        color = parseColor(story.textColor),
        fontFamily = font.family,
        fontWeight = font.weight,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
            .background(Color.Black.copy(alpha = if (story.kind == "text") 0.08f else 0.34f), HelloShapes.Md)
            .border(1.dp, Color.White.copy(alpha = 0.12f), HelloShapes.Md)
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md)
    )
}

@Composable
private fun StoryPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(44.dp))
            Text(text, color = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
        Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            items(options) { option ->
                Surface(
                    onClick = { onSelected(option) },
                    shape = HelloShapes.Pill,
                    color = if (option == selected) HelloColors.DarkAccentSoft else HelloColors.DarkPanelMuted
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

private fun readPickedStoryFile(context: Context, uri: Uri): PickedStoryFile? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: return null
    if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) return null
    val metadata = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedStoryFile(uri, metadata ?: "story-media", mimeType, bytes)
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = (diff / 60000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

private fun parseColor(value: String?): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(value ?: "#f472b6"))
    }.getOrDefault(Color(0xFFF472B6))
}

private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return "#%06X".format(0xFFFFFF and argb)
}
