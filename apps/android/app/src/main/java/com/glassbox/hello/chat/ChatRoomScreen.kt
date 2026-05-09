package com.glassbox.hello.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.calls.ActiveCallScreen
import com.glassbox.hello.calls.CallUiStatus
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.calls.IncomingCallScreen
import com.glassbox.hello.calls.OutgoingCallScreen
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.core.User
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.ChatWallpaperBackground
import com.glassbox.hello.ui.theme.HelloAnimations
import com.glassbox.hello.ui.utils.AnimationUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chat: Chat,
    currentUserId: String,
    currentUserName: String,
    currentUserAvatar: String?,
    callViewModel: CallViewModel,
    onBack: () -> Unit,
    onOpenContactInfo: () -> Unit,
    onOpenSharedContent: (ChatSharedContentMode) -> Unit,
    onChatDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel()
    val messagesState by viewModel.messagesState.collectAsState()
    val messagesRefreshing by viewModel.messagesRefreshing.collectAsState()
    val sendMessageState by viewModel.sendMessageState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val usersState by viewModel.usersState.collectAsState()
    val callState by callViewModel.state.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showChatActions by remember { mutableStateOf(false) }
    var showEmojiRow by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var manualLocationText by remember { mutableStateOf("") }
    var showContactShare by remember { mutableStateOf(false) }
    var contactQuery by remember { mutableStateOf("") }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showMessageMenu by remember { mutableStateOf(false) }
    var pendingCallIsVideo by remember { mutableStateOf(false) }
    var pendingIncomingAccept by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }
    var fileSizeError by remember { mutableStateOf<String?>(null) }
    var pendingAttachment by remember { mutableStateOf<PickedFile?>(null) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }
    var cameraCapture by remember { mutableStateOf<CameraCapture?>(null) }
    var voiceState by remember { mutableStateOf(VoiceRecordingState()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val settingsState = rememberHelloSettingsState(context).value
    val title = chat.displayName(currentUserId)
    val other = chat.otherParticipant(currentUserId)
    val wallpaperOpacity = (settingsState.wallpaperOpacity.coerceIn(0, 100) / 100f)
    val subtitle = when {
        chat.isGroup -> "${chat.members?.size ?: chat.participants?.size ?: 0} participants"
        other?.online == true -> "Online"
        other != null -> "Hello user"
        else -> "Private family chat"
    }

    LaunchedEffect(chat.id) {
        viewModel.loadMessages(chat.id)
    }

    LaunchedEffect(showContactShare, contactQuery) {
        if (showContactShare) {
            viewModel.loadUsers(currentUserId, contactQuery)
        }
    }

    LaunchedEffect(currentUserId, currentUserName, currentUserAvatar) {
        callViewModel.connect(User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar))
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            readPickedFile(context, uri)?.let { selected ->
                // Validate file size - max 100MB
                if (selected.bytes.size > 100 * 1024 * 1024) {
                    fileSizeError = "Maximum file size is 100 MB"
                    return@let
                }
                pendingAttachment = selected
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val capture = cameraCapture
        if (saved && capture != null && capture.file.exists()) {
            pendingAttachment = PickedFile(
                uri = capture.uri,
                name = capture.file.name,
                mimeType = "image/jpeg",
                bytes = capture.file.readBytes()
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val capture = createCameraCapture(context)
            cameraCapture = capture
            cameraLauncher.launch(capture.uri)
        } else {
            permissionDialog = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val location = getLastKnownHelloLocation(context)
        if (location != null) {
            viewModel.shareLocation(
                chatId = chat.id,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                lat = location.latitude,
                lng = location.longitude
            )
        } else {
            showLocationDialog = true
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                val file = voiceRecorder.start()
                voiceState = VoiceRecordingState(active = true, startedAt = System.currentTimeMillis(), filePath = file.absolutePath)
            }.onFailure {
                voiceState = VoiceRecordingState(error = it.message ?: "Could not start voice note")
            }
        } else {
            permissionDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val needsCamera = pendingCallIsVideo
        val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO)
        val hasCamera = !needsCamera || grants[Manifest.permission.CAMERA] == true ||
            context.checkSelfPermissionCompat(Manifest.permission.CAMERA)
        if (hasAudio && hasCamera) {
            if (pendingIncomingAccept) {
                pendingIncomingAccept = false
                callViewModel.acceptIncoming(context)
            } else {
                callViewModel.startCall(
                    context = context,
                    chat = chat,
                    user = User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar),
                    isVideo = pendingCallIsVideo
                )
            }
        } else {
            pendingIncomingAccept = false
            permissionDialog = true
        }
    }

    fun requestCall(isVideo: Boolean) {
        pendingCallIsVideo = isVideo
        val permissions = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val granted = permissions.all { context.checkSelfPermissionCompat(it) }
        if (granted) {
            callViewModel.startCall(
                context = context,
                chat = chat,
                user = User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar),
                isVideo = isVideo
            )
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    fun acceptIncomingCall() {
        pendingIncomingAccept = true
        pendingCallIsVideo = callState.signal?.isVideo == true
        val permissions = if (pendingCallIsVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val granted = permissions.all { context.checkSelfPermissionCompat(it) }
        if (granted) {
            pendingIncomingAccept = false
            callViewModel.acceptIncoming(context)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    fun pickAttachment(action: AttachmentAction) {
        showAttachmentMenu = false
        when (action) {
            AttachmentAction.Gallery -> filePicker.launch("image/*")
            AttachmentAction.File -> filePicker.launch("*/*")
            AttachmentAction.Audio -> filePicker.launch("audio/*")
            AttachmentAction.Camera -> {
                if (context.checkSelfPermissionCompat(Manifest.permission.CAMERA)) {
                    val capture = createCameraCapture(context)
                    cameraCapture = capture
                    cameraLauncher.launch(capture.uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            AttachmentAction.Location -> {
                val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (permissions.any { context.checkSelfPermissionCompat(it) }) {
                    val location = getLastKnownHelloLocation(context)
                    if (location != null) {
                        viewModel.shareLocation(chat.id, currentUserId, currentUserName, currentUserAvatar, location.latitude, location.longitude)
                    } else {
                        showLocationDialog = true
                    }
                } else {
                    locationPermissionLauncher.launch(permissions)
                }
            }
            AttachmentAction.Contact -> {
                showContactShare = true
            }
        }
    }

    fun startVoiceNote() {
        if (!context.checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO)) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        runCatching {
            val file = voiceRecorder.start()
            voiceState = VoiceRecordingState(active = true, startedAt = System.currentTimeMillis(), filePath = file.absolutePath)
            AnimationUtils.Haptics.tapMedium(context)
        }.onFailure {
            voiceState = VoiceRecordingState(error = it.message ?: "Could not start voice note")
        }
    }

    fun finishVoiceNote(send: Boolean) {
        val file = voiceRecorder.stop(delete = !send)
        voiceState = VoiceRecordingState()
        if (send && file != null) {
            viewModel.uploadAndSendAttachment(
                chatId = chat.id,
                fileName = file.name,
                mimeType = "audio/mp4",
                bytes = file.readBytes(),
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                caption = ""
            )
        }
    }

    DisposableEffect(chat.id, currentUserId) {
        val socketManager = SocketManager.getInstance()
        socketManager.onMessageReceived = { message ->
            if (message.chatId == chat.id) viewModel.appendFromSocket(message)
        }
        socketManager.onMessageUpdated = { message ->
            if (message.chatId == chat.id) viewModel.updateFromSocket(message)
        }
        socketManager.connect(User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar))
        socketManager.joinChat(chat.id)
        onDispose {
            socketManager.leaveChat(chat.id)
            // Don't disconnect - let other screens keep using it
        }
    }

    LaunchedEffect(messagesState) {
        if (messagesState is ResultState.Success) {
            val messages = (messagesState as ResultState.Success<List<Message>>).data
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(sendMessageState) {
        if (sendMessageState is ResultState.Success) {
            messageText = ""
            pendingAttachment = null
            replyTo = null
            viewModel.resetSendMessageState()
            viewModel.resetUploadState()
        }
    }

    fun sendCurrentMessage() {
        val trimmed = messageText.trim()
        val attachment = pendingAttachment
        val replySnapshot = replyTo?.let {
            ChatModels.ReplyTo(
                id = it.id,
                text = it.text,
                senderName = it.senderName,
                senderId = it.senderId
            )
        }
        
        if (attachment != null) {
            // Create optimistic message for attachment
            val optimisticMsg = OptimisticMessageManager.createOptimisticMessage(
                chatId = chat.id,
                text = trimmed,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                replyTo = replySnapshot
            )
            // Add optimistic immediately
            viewModel.addOptimisticMessage(optimisticMsg.message)
            
            // Clear UI immediately
            messageText = ""
            replyTo = null
            
            // Send async - will patch on server response
            AnimationUtils.Haptics.sendMessage(context)
            viewModel.uploadAndSendAttachment(
                chatId = chat.id,
                fileName = attachment.name,
                mimeType = attachment.mimeType,
                bytes = attachment.bytes,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                caption = trimmed,
                replyTo = replySnapshot
            )
        } else if (trimmed.isNotEmpty()) {
            // Create optimistic message
            val optimisticMsg = OptimisticMessageManager.createOptimisticMessage(
                chatId = chat.id,
                text = trimmed,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                replyTo = replySnapshot
            )
            
            // Add optimistic message immediately to UI
            viewModel.addOptimisticMessage(optimisticMsg.message)
            
            // Clear input immediately
            messageText = ""
            replyTo = null
            
            // Send haptic feedback
            AnimationUtils.Haptics.sendMessage(context)
            
            // Send message asynchronously - will patch on server response
            viewModel.sendMessage(
                chatId = chat.id,
                text = trimmed,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                replyTo = replySnapshot
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .background(HelloColors.DarkBg)
                .imePadding()
        ) {
            ChatRoomHeader(
                title = title,
                subtitle = subtitle,
                avatarUrl = other?.avatar ?: chat.avatar,
                onBack = onBack,
                onOpenContactInfo = onOpenContactInfo,
                onAudioCall = { requestCall(false) },
                onVideoCall = { requestCall(true) },
                onMore = { showChatActions = true }
            )

            ChatWallpaperBackground(
                wallpaper = settingsState.wallpaper,
                opacity = wallpaperOpacity,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (messagesRefreshing) {
                        Text(
                            text = "Syncing...",
                            color = HelloColors.DarkTextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = HelloSpacing.Xs)
                                .background(HelloColors.DarkPanelMuted, HelloShapes.Sm)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    when (messagesState) {
                        is ResultState.Loading -> LoadingView()
                        is ResultState.Error -> ErrorView(
                            message = (messagesState as ResultState.Error).message,
                            onRetry = { viewModel.loadMessages(chat.id) }
                        )
                        is ResultState.Success -> {
                            val messages = (messagesState as ResultState.Success<List<Message>>).data
                            if (messages.isEmpty()) {
                                EmptyRoom()
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = HelloSpacing.Lg),
                                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
                                ) {
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                            Text(
                                                text = "TODAY",
                                                color = HelloColors.DarkTextMuted,
                                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                                modifier = Modifier
                                                    .background(HelloColors.DarkPanelMuted, HelloShapes.Sm)
                                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                                        }
                                    }
                                    items(messages.size, key = { messages[it].id }) { idx ->
                                        val message = messages[idx]
                                        val isOwn = message.senderId == currentUserId
                                        val prevMessage = if (idx > 0) messages[idx - 1] else null
                                        
                                        // Show sender name for group chats (only other messages, only first in group)
                                        if (chat.isGroup && !isOwn && prevMessage?.senderId != message.senderId) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                                            ) {
                                                Text(
                                                    text = message.senderName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = HelloColors.DarkAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = HelloSpacing.Md)
                                                )
                                            }
                                        }
                                        
                                        // Add extra spacing if sender changed
                                        if (prevMessage != null && prevMessage.senderId != message.senderId) {
                                            Spacer(modifier = Modifier.height(HelloSpacing.Md))
                                        }
                                        
                                        AnimatedMessageBubble(
                                            message = message,
                                            isOwn = isOwn,
                                            index = idx,
                                            currentUserId = currentUserId,
                                            context = context,
                                            onReply = { replyMsg ->
                                                replyTo = replyMsg
                                            },
                                            onLongPress = {
                                                selectedMessageId = message.id
                                                showMessageMenu = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sendMessageState is ResultState.Error) {
                Text(
                    text = (sendMessageState as ResultState.Error).message,
                    color = HelloColors.DarkDanger,
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                )
            }

            if (uploadState is ResultState.Loading) {
                Text(
                    text = "Uploading attachment...",
                    color = HelloColors.DarkTextMuted,
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                )
            }

            if (uploadState is ResultState.Error) {
                Text(
                    text = (uploadState as ResultState.Error).message,
                    color = HelloColors.DarkDanger,
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                )
            }

            if (pendingAttachment != null) {
                AttachmentPreviewBar(
                    file = pendingAttachment!!,
                    onRemove = {
                        pendingAttachment = null
                        viewModel.resetUploadState()
                    },
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                )
            }

            // Reply preview
            if (replyTo != null) {
                HelloPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs),
                    strong = true,
                    shape = HelloShapes.Sm
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(HelloSpacing.Md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyTo!!.senderName}",
                                color = HelloColors.DarkAccent,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = replyTo!!.text.take(80) + if (replyTo!!.text.length > 80) "..." else "",
                                color = HelloColors.DarkText,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                        HelloIconButton(onClick = { replyTo = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear reply", tint = HelloColors.DarkTextMuted)
                        }
                    }
                }
            }

            HelloPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Xs),
                strong = true,
                shape = HelloShapes.Lg
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs)
                        .animateContentSize()
                ) {
                    AnimatedVisibility(visible = showEmojiRow) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
                            modifier = Modifier.padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs)
                        ) {
                            items(listOf("👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🙏")) { emoji ->
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .background(HelloColors.DarkPanelMuted, HelloShapes.Pill)
                                        .clickable {
                                            messageText += emoji
                                            showEmojiRow = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    if (voiceState.active) {
                        Text(
                            text = "Recording voice note... tap mic to send",
                            color = HelloColors.DarkAccent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Xs)
                        )
                    }
                    voiceState.error?.let {
                        Text(
                            text = it,
                            color = HelloColors.DarkDanger,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Xs)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
                    ) {
                        HelloIconButton(onClick = { showEmojiRow = !showEmojiRow }) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = HelloColors.DarkTextMuted)
                        }
                        HelloIconButton(onClick = { showAttachmentMenu = true }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = HelloColors.DarkTextMuted)
                        }
                        HelloSearchBar(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = if (pendingAttachment == null) "Write a message" else "Add a caption",
                            keyboardOptions = if (settingsState.enterSends) {
                                KeyboardOptions(imeAction = ImeAction.Send)
                            } else {
                                KeyboardOptions.Default
                            },
                            keyboardActions = if (settingsState.enterSends) {
                                KeyboardActions(onSend = { sendCurrentMessage() })
                            } else {
                                KeyboardActions.Default
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HelloIconButton(
                            onClick = {
                                if (messageText.trim().isNotEmpty() || pendingAttachment != null) {
                                    sendCurrentMessage()
                                } else if (voiceState.active) {
                                    finishVoiceNote(send = true)
                                } else {
                                    startVoiceNote()
                                }
                            },
                            active = messageText.trim().isNotEmpty() || pendingAttachment != null || voiceState.active
                        ) {
                            Icon(
                                if (messageText.trim().isNotEmpty() || pendingAttachment != null) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = if (messageText.trim().isNotEmpty() || pendingAttachment != null) "Send message" else "Voice note",
                                tint = if (messageText.trim().isNotEmpty() || pendingAttachment != null || voiceState.active) HelloColors.DarkAccent else HelloColors.DarkTextMuted
                            )
                        }
                    }
                }
            }
        }

        // Message context menu
        if (showMessageMenu && selectedMessageId != null) {
            val selectedMessage = messagesState.let { state ->
                if (state is ResultState.Success) state.data.firstOrNull { it.id == selectedMessageId } else null
            }
            if (selectedMessage != null) {
                ModalBottomSheet(
                    onDismissRequest = { showMessageMenu = false },
                    containerColor = HelloColors.DarkPanelStrong
                ) {
                    MessageActionSheet(
                        message = selectedMessage,
                        isOwn = selectedMessage.senderId == currentUserId,
                        onReply = {
                            replyTo = selectedMessage
                            showMessageMenu = false
                        },
                        onStar = {
                            viewModel.starMessage(chat.id, selectedMessage.id, currentUserId)
                            showMessageMenu = false
                        },
                        onReact = {
                            showReactionPicker = true
                            showMessageMenu = false
                        },
                        onPin = {
                            viewModel.pinMessage(chat.id, selectedMessage.id)
                            showMessageMenu = false
                        },
                        onOpen = {
                            openMessageAttachment(context, selectedMessage)
                            showMessageMenu = false
                        },
                        onDelete = {
                            showDeleteConfirm = true
                            showMessageMenu = false
                        }
                    )
                }
            }
        }

        if (showReactionPicker && selectedMessageId != null) {
            val selectedMessage = messagesState.let { state ->
                if (state is ResultState.Success) state.data.firstOrNull { it.id == selectedMessageId } else null
            }
            if (selectedMessage != null) {
                AlertDialog(
                    onDismissRequest = { showReactionPicker = false },
                    containerColor = HelloColors.DarkPanelStrong,
                    title = { Text("Choose reaction", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            listOf("👍", "❤️", "😂", "😮", "😢", "👏").forEach { emoji ->
                                TextButton(onClick = {
                                    viewModel.reactToMessage(chat.id, selectedMessage.id, emoji, currentUserId)
                                    showReactionPicker = false
                                }) {
                                    Text(emoji, color = HelloColors.DarkAccent)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showReactionPicker = false }) {
                            Text("Cancel", color = HelloColors.DarkTextMuted)
                        }
                    }
                )
            }
        }

        if (showDeleteConfirm && selectedMessageId != null) {
            val selectedMessage = messagesState.let { state ->
                if (state is ResultState.Success) state.data.firstOrNull { it.id == selectedMessageId } else null
            }
            if (selectedMessage != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    containerColor = HelloColors.DarkPanelStrong,
                    title = { Text("Delete message?", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
                    text = { Text("This will remove the message from the chat.", color = HelloColors.DarkTextMuted) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteMessage(chat.id, selectedMessage.id, currentUserId)
                            showDeleteConfirm = false
                        }) {
                            Text("Delete", color = HelloColors.DarkDanger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", color = HelloColors.DarkTextMuted)
                        }
                    }
                )
            }
        }

        if (permissionDialog) {
            PermissionRequiredDialog(
                onOpenSettings = {
                    permissionDialog = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                onCancel = { permissionDialog = false }
            )
        }

        if (fileSizeError != null) {
            AlertDialog(
                onDismissRequest = { fileSizeError = null },
                containerColor = HelloColors.DarkPanelStrong,
                title = { Text("File too large", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
                text = { Text(fileSizeError ?: "", color = HelloColors.DarkText) },
                confirmButton = {
                    TextButton(onClick = { fileSizeError = null }) {
                        Text("OK", color = HelloColors.DarkAccent)
                    }
                }
            )
        }

        if (showAttachmentMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentMenu = false },
                containerColor = HelloColors.DarkPanelStrong
            ) {
                AttachmentBottomSheet(onAction = ::pickAttachment)
            }
        }

        if (showChatActions) {
            ModalBottomSheet(
                onDismissRequest = { showChatActions = false },
                containerColor = HelloColors.DarkPanelStrong
            ) {
                ChatActionSheet(
                    onContactInfo = {
                        showChatActions = false
                        onOpenContactInfo()
                    },
                    onMedia = {
                        showChatActions = false
                        onOpenSharedContent(ChatSharedContentMode.Media)
                    },
                    onFiles = {
                        showChatActions = false
                        onOpenSharedContent(ChatSharedContentMode.Files)
                    },
                    onLinks = {
                        showChatActions = false
                        onOpenSharedContent(ChatSharedContentMode.Links)
                    },
                    onClearChat = {
                        showChatActions = false
                        showClearChatConfirm = true
                    },
                    onDeleteChat = {
                        showChatActions = false
                        showDeleteChatConfirm = true
                    }
                )
            }
        }

        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                containerColor = HelloColors.DarkPanelStrong,
                title = { Text("Share location", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
                text = {
                    HelloSearchBar(
                        value = manualLocationText,
                        onValueChange = { manualLocationText = it },
                        placeholder = "Type a place or address"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (manualLocationText.isNotBlank()) {
                                viewModel.shareLocation(chat.id, currentUserId, currentUserName, currentUserAvatar, 0.0, 0.0, manualLocationText.trim())
                                manualLocationText = ""
                                showLocationDialog = false
                            }
                        }
                    ) { Text("Send", color = HelloColors.DarkAccent) }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationDialog = false }) {
                        Text("Cancel", color = HelloColors.DarkTextMuted)
                    }
                }
            )
        }

        if (showContactShare) {
            ContactShareDialog(
                query = contactQuery,
                onQueryChange = { contactQuery = it },
                usersState = usersState,
                onDismiss = {
                    showContactShare = false
                    contactQuery = ""
                },
                onShare = { user ->
                    viewModel.shareContact(chat.id, currentUserId, currentUserName, currentUserAvatar, user)
                    showContactShare = false
                    contactQuery = ""
                }
            )
        }

        if (showClearChatConfirm) {
            ConfirmChatDialog(
                title = "Clear chat?",
                message = "This removes local messages for your account.",
                onDismiss = { showClearChatConfirm = false },
                onConfirm = {
                    viewModel.clearChat(chat.id, currentUserId)
                    showClearChatConfirm = false
                }
            )
        }

        if (showDeleteChatConfirm) {
            ConfirmChatDialog(
                title = "Delete chat?",
                message = "This removes the chat locally for your account.",
                onDismiss = { showDeleteChatConfirm = false },
                onConfirm = {
                    viewModel.deleteChat(chat.id, currentUserId)
                    showDeleteChatConfirm = false
                    onChatDeleted()
                }
            )
        }
    }
}

private fun Context.checkSelfPermissionCompat(permission: String): Boolean {
    return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionRequiredDialog(
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Camera/microphone permission", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text("Camera/microphone permission is needed for calls.", color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

@Composable
private fun CallResultDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Call status", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
    )
}

private data class PickedFile(val uri: Uri, val name: String, val mimeType: String, val bytes: ByteArray)

private fun readPickedFile(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: "hello-attachment"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedFile(uri = uri, name = name, mimeType = mimeType, bytes = bytes)
}

@Composable
private fun AttachmentPreviewBar(
    file: PickedFile,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Md) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            if (file.mimeType.startsWith("image/")) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file.uri)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    modifier = Modifier
                        .size(56.dp)
                        .background(HelloColors.DarkPanelMuted, HelloShapes.Md),
                    contentScale = ContentScale.Crop,
                    loading = { AttachmentIcon() },
                    error = { AttachmentIcon() }
                )
            } else {
                AttachmentIcon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = HelloColors.DarkText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.mimeType} - ${formatBytes(file.bytes.size.toLong())}",
                    color = HelloColors.DarkTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Caption will be sent with this attachment.",
                    color = HelloColors.DarkAccent,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
            HelloIconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
private fun AttachmentIcon() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(HelloColors.DarkAccentSoft, HelloShapes.Md),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = HelloColors.DarkAccent)
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb.coerceAtLeast(1.0))
}

@Composable
private fun ChatRoomHeader(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    onBack: () -> Unit,
    onOpenContactInfo: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelloColors.DarkBgStrong)
            .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HelloIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenContactInfo),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelloAvatar(name = title, online = subtitle == "Online", size = 42.dp, imageUrl = avatarUrl)
            Spacer(modifier = Modifier.width(HelloSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = HelloColors.DarkText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = HelloColors.DarkTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            }
        }
        HelloIconButton(onClick = onVideoCall) {
            Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = HelloColors.DarkTextMuted)
        }
        HelloIconButton(onClick = onAudioCall) {
            Icon(Icons.Default.Call, contentDescription = "Audio call", tint = HelloColors.DarkTextMuted)
        }
        HelloIconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun AttachmentBottomSheet(onAction: (AttachmentAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Text("Attach", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val actions = listOf(
            Triple(AttachmentAction.Gallery, "Gallery", Icons.Default.Image),
            Triple(AttachmentAction.Camera, "Camera", Icons.Default.CameraAlt),
            Triple(AttachmentAction.File, "Document", Icons.Default.Description),
            Triple(AttachmentAction.Location, "Location", Icons.Default.LocationOn),
            Triple(AttachmentAction.Contact, "Contact", Icons.Default.Person),
            Triple(AttachmentAction.Audio, "Audio", Icons.Default.Mic)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            items(actions) { (action, label, icon) ->
                HelloPanel(
                    modifier = Modifier
                        .size(96.dp)
                        .clickable { onAction(action) },
                    strong = true,
                    shape = HelloShapes.Md
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(HelloSpacing.Sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = HelloColors.DarkAccent)
                        Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                        Text(label, color = HelloColors.DarkText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
    }
}

@Composable
private fun ChatActionSheet(
    onContactInfo: () -> Unit,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onLinks: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        Text("Chat actions", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SheetRow("Contact info", Icons.Default.Person, onContactInfo)
        SheetRow("Shared media", Icons.Default.Image, onMedia)
        SheetRow("Shared files", Icons.Default.Description, onFiles)
        SheetRow("Shared links", Icons.Default.Link, onLinks)
        SheetRow("Clear chat locally", Icons.Default.Delete, onClearChat, danger = true)
        SheetRow("Delete chat locally", Icons.Default.Delete, onDeleteChat, danger = true)
    }
}

@Composable
private fun MessageActionSheet(
    message: Message,
    isOwn: Boolean,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onReact: () -> Unit,
    onPin: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        Text("Message options", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SheetRow("Reply", Icons.AutoMirrored.Filled.ArrowBack, onReply)
        SheetRow(if (message.starredBy?.isNotEmpty() == true) "Unstar" else "Star", Icons.Default.Star, onStar)
        SheetRow("React", Icons.Default.EmojiEmotions, onReact)
        SheetRow("Pin", Icons.Default.PushPin, onPin)
        if (!message.attachmentUrl.isNullOrBlank()) {
            SheetRow("Open attachment", Icons.Default.Folder, onOpen)
        }
        if (isOwn) {
            SheetRow("Delete", Icons.Default.Delete, onDelete, danger = true)
        }
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HelloSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Icon(icon, contentDescription = label, tint = if (danger) HelloColors.DarkDanger else HelloColors.DarkTextMuted)
        Text(label, color = if (danger) HelloColors.DarkDanger else HelloColors.DarkText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContactShareDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    usersState: ResultState<List<ChatModels.User>>,
    onDismiss: () -> Unit,
    onShare: (ChatModels.User) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Share contact", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                HelloSearchBar(value = query, onValueChange = onQueryChange, placeholder = "Search contacts")
                when (usersState) {
                    is ResultState.Loading -> Text("Loading contacts...", color = HelloColors.DarkTextMuted)
                    is ResultState.Error -> Text(usersState.message, color = HelloColors.DarkDanger)
                    is ResultState.Success -> {
                        LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            items(usersState.data, key = { it.id }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onShare(user) }
                                        .padding(HelloSpacing.Sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                                ) {
                                    HelloAvatar(name = user.name, online = user.online == true, size = 40.dp, imageUrl = user.avatar)
                                    Text(user.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

@Composable
private fun ConfirmChatDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = HelloColors.DarkDanger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

private fun openMessageAttachment(context: Context, message: Message) {
    val url = UrlResolver.resolve(message.attachmentUrl) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun EmptyRoom() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No messages yet",
                color = HelloColors.DarkText,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(HelloSpacing.Xs))
            Text(
                text = "Send the first message.",
                color = HelloColors.DarkTextMuted
            )
        }
    }
}
