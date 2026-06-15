package com.glassbox.hello.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.chat.components.ActionMessageState
import com.glassbox.hello.chat.components.AttachmentBottomSheet
import com.glassbox.hello.chat.components.AttachmentDraft
import com.glassbox.hello.chat.components.AttachmentPreviewStrip
import com.glassbox.hello.chat.components.ChatActionSheet
import com.glassbox.hello.chat.components.ChatComposer
import com.glassbox.hello.chat.components.ChatHeader
import com.glassbox.hello.chat.components.ChatMessageList
import com.glassbox.hello.chat.components.ContactShareDialog
import com.glassbox.hello.chat.components.EmojiStickerPickerSheet
import com.glassbox.hello.chat.components.MediaViewer
import com.glassbox.hello.chat.components.MediaViewerState
import com.glassbox.hello.chat.components.MessageActionSheet
import com.glassbox.hello.chat.components.ReplyComposerBar
import com.glassbox.hello.chat.components.createStickerMessageText
import com.glassbox.hello.chat.components.downloadAttachment
import com.glassbox.hello.chat.components.normalizeAttachmentUrl
import com.glassbox.hello.chat.components.openExternalTarget
import com.glassbox.hello.chat.components.rememberNearBottom
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User
import com.glassbox.hello.debug.HelloDebugLog
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.notifications.HelloNotificationCenter
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.rememberChatTheme
import com.glassbox.hello.ui.utils.AnimationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import androidx.activity.result.PickVisualMediaRequest

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
    val preparedMessagesState by viewModel.preparedMessagesState.collectAsState()
    val messagesRefreshing by viewModel.messagesRefreshing.collectAsState()
    val sendMessageState by viewModel.sendMessageState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val usersState by viewModel.usersState.collectAsState()
    val isLoadingOlderMessages by viewModel.isLoadingOlderMessages.collectAsState()
    val hasMoreOlderMessages by viewModel.hasMoreOlderMessages.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val socketManager = remember { SocketManager.getInstance() }
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val settingsState = rememberHelloSettingsState(context).value
    val chatTheme by rememberChatTheme(context, currentUserId)
    val listState = rememberLazyListState()

    var messageText by remember(chat.id) { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showChatActions by remember { mutableStateOf(false) }
    var showEmojiRow by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var manualLocationText by remember { mutableStateOf("") }
    var showContactShare by remember { mutableStateOf(false) }
    var contactQuery by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<ActionMessageState?>(null) }
    var deleteMode by remember { mutableStateOf("message") }
    var pendingCallIsVideo by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }
    var fileSizeError by remember { mutableStateOf<String?>(null) }
    val pendingAttachments = remember { mutableStateListOf<AttachmentDraft>() }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }
    var cameraCapture by remember { mutableStateOf<CameraCapture?>(null) }
    var voiceState by remember { mutableStateOf(VoiceRecordingState()) }
    var recordingElapsedSeconds by remember { mutableStateOf(0L) }
    var hasAutoScrolledInitial by remember(chat.id) { mutableStateOf(false) }
    var mediaViewerState by remember { mutableStateOf<MediaViewerState?>(null) }
    var locallyTyping by remember(chat.id) { mutableStateOf(false) }
    var previousMessageCount by remember(chat.id) { mutableStateOf(0) }
    var previousLastMessageId by remember(chat.id) { mutableStateOf<String?>(null) }
    var lastReadMessageId by remember(chat.id) { mutableStateOf<String?>(null) }
    var typingExpirations by remember(chat.id) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var pendingBelowMessageIds by remember(chat.id) { mutableStateOf<Set<String>>(emptySet()) }
    val latestCloudChatEnabled by rememberUpdatedState(settingsState.cloudChatEnabled)
    val typingNames by remember(typingExpirations) {
        derivedStateOf {
            typingExpirations.entries
                .sortedBy { it.value }
                .map { it.key }
                .takeLast(3)
        }
    }

    val unreadBelowCount = maxOf(chat.unreadCount ?: 0, pendingBelowMessageIds.size)
    LaunchedEffect(currentUserId, chat.isGroup, unreadBelowCount) {
        viewModel.configureMessagePresentation(
            currentUserId = currentUserId,
            chatIsGroup = chat.isGroup,
            unreadCount = unreadBelowCount
        )
    }
    val preparedMessages = (preparedMessagesState as? ResultState.Success)?.data
    val visibleMessages = preparedMessages?.visibleMessages.orEmpty()
    val visibleRows = remember(preparedMessages?.rows, settingsState.showCallLogsInChat) {
        preparedMessages?.rows.orEmpty().let { rows ->
            if (settingsState.showCallLogsInChat) {
                rows
            } else {
                rows.filterNot { row ->
                    row.message.callInfo != null || row.message.messageType == "call_log"
                }
            }
        }
    }
    val isNearBottom = rememberNearBottom(
        listState = listState,
        itemCount = visibleRows.size + if (hasMoreOlderMessages || isLoadingOlderMessages) 1 else 0
    )

    val title = chat.displayName(currentUserId)
    val other = chat.otherParticipant(currentUserId)
    val bubbleOpacity = chatTheme.bubbleOpacity.coerceIn(40, 100) / 100f
    val subtitle = when {
        chat.isGroup -> "${chat.participantCount()} participants"
        other?.online == true -> "Online"
        other != null -> "Hello user"
        else -> "Private family chat"
    }
    val visibleSubtitle = if (typingNames.isNotEmpty()) {
        typingNames.joinToString(limit = 2, truncated = "others") + if (typingNames.size == 1) " is typing" else " are typing"
    } else {
        subtitle
    }

    LaunchedEffect(Unit) {
        HelloDebugLog.d("ChatRoom", "configureCloudChat chatId=${chat.id} currentUserId=$currentUserId")
        viewModel.configureCloudChat(context)
    }

    LaunchedEffect(chat.id, settingsState.cloudChatEnabled) {
        HelloDebugLog.d("ChatRoom", "loadMessages chatId=${chat.id} cloudChatEnabled=${settingsState.cloudChatEnabled}")
        viewModel.loadMessages(chat.id, cloudChatEnabled = settingsState.cloudChatEnabled)
    }

    LaunchedEffect(showContactShare, contactQuery, settingsState.cloudChatEnabled) {
        if (showContactShare) {
            HelloDebugLog.d("ChatRoom", "loadUsersForShare chatId=${chat.id} query=${HelloDebugLog.snippet(contactQuery)}")
            viewModel.loadUsers(currentUserId, contactQuery, cloudChatEnabled = settingsState.cloudChatEnabled)
        }
    }

    LaunchedEffect(voiceState.active, voiceState.startedAt) {
        while (voiceState.active) {
            recordingElapsedSeconds = ((System.currentTimeMillis() - voiceState.startedAt).coerceAtLeast(0L) / 1000L)
            delay(500)
        }
        recordingElapsedSeconds = 0L
    }

    fun stopTypingIndicator() {
        if (!locallyTyping) return
        locallyTyping = false
        socketManager.typing(chat.id, currentUserId, currentUserName, isTyping = false)
    }

    LaunchedEffect(chat.id, currentUserId, currentUserName) {
        snapshotFlow { messageText }
            .collectLatest { draft ->
                if (draft.isBlank()) {
                    stopTypingIndicator()
                    return@collectLatest
                }
                if (!locallyTyping) {
                    locallyTyping = true
                    socketManager.typing(chat.id, currentUserId, currentUserName, isTyping = true)
                }
                delay(1500)
                stopTypingIndicator()
            }
    }

    LaunchedEffect(chat.id) {
        snapshotFlow { typingExpirations.values.minOrNull() }
            .collectLatest { nextExpiryAt ->
                if (nextExpiryAt == null) return@collectLatest
                val delayMs = (nextExpiryAt - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(delayMs)
                val now = System.currentTimeMillis()
                typingExpirations = typingExpirations.filterValues { it > now }
            }
    }

    LaunchedEffect(chat.id, settingsState.cloudChatEnabled) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, hasMoreOlderMessages, isLoadingOlderMessages)
        }
            .distinctUntilChanged()
            .collect { (firstVisibleIndex, hasOlder, loadingOlder) ->
                if (hasAutoScrolledInitial && hasOlder && !loadingOlder && firstVisibleIndex <= 1) {
                    viewModel.loadOlderMessages(chat.id, cloudChatEnabled = settingsState.cloudChatEnabled)
                }
            }
    }

    val lastVisibleMessage = visibleMessages.lastOrNull()

    LaunchedEffect(chat.id, lastVisibleMessage?.id, visibleMessages.size, visibleRows.size) {
        if (visibleMessages.isNotEmpty() && visibleRows.isNotEmpty()) {
            if (!hasAutoScrolledInitial) {
                delay(16)
                listState.scrollToItem(visibleRows.lastIndex)
                hasAutoScrolledInitial = true
            } else {
                val messageAppended =
                    visibleMessages.size > previousMessageCount && lastVisibleMessage?.id != previousLastMessageId
                val sentByCurrentUser = lastVisibleMessage?.senderId == currentUserId
                val shouldStickToBottom = messageAppended && (isNearBottom || sentByCurrentUser)
                if (shouldStickToBottom) {
                    delay(16)
                    listState.scrollToItem(visibleRows.lastIndex)
                }
            }
            previousMessageCount = visibleMessages.size
            previousLastMessageId = lastVisibleMessage?.id
        } else {
            previousMessageCount = 0
            previousLastMessageId = null
        }
    }

    LaunchedEffect(chat.id, lastVisibleMessage?.id, isNearBottom) {
        if (!isNearBottom) return@LaunchedEffect
        if (pendingBelowMessageIds.isNotEmpty()) {
            pendingBelowMessageIds = emptySet()
        }
        val latestMessage = lastVisibleMessage ?: return@LaunchedEffect
        if (latestMessage.senderId == currentUserId) return@LaunchedEffect
        if (lastReadMessageId == latestMessage.id) return@LaunchedEffect
        viewModel.clearUnreadForChat(chat.id, currentUserId, latestCloudChatEnabled)
        lastReadMessageId = latestMessage.id
    }

    LaunchedEffect(sendMessageState) {
        if (sendMessageState is ResultState.Success) {
            viewModel.resetSendMessageState()
        }
    }

    fun queuePickedAttachments(uris: List<Uri>?) {
        uris?.forEach { uri ->
            readPickedFile(context, uri)?.let { selected ->
                if (selected.bytes.size > 100 * 1024 * 1024) {
                    fileSizeError = "Maximum file size is 100 MB"
                    return@let
                }
                pendingAttachments += selected
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        queuePickedAttachments(uris)
    }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        queuePickedAttachments(uris)
    }

    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        queuePickedAttachments(uri?.let { listOf(it) })
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val capture = cameraCapture
        if (saved && capture != null && capture.file.exists()) {
            pendingAttachments += AttachmentDraft(
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val location = getLastKnownHelloLocation(context)
        if (location != null) {
            viewModel.shareLocation(
                chat.id,
                currentUserId,
                currentUserName,
                currentUserAvatar,
                location.latitude,
                location.longitude,
                cloudChatEnabled = settingsState.cloudChatEnabled,
                chat = chat
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val needsCamera = pendingCallIsVideo
        val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true || context.checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO)
        val hasCamera = !needsCamera || grants[Manifest.permission.CAMERA] == true || context.checkSelfPermissionCompat(Manifest.permission.CAMERA)
        if (hasAudio && hasCamera) {
            val startAsVideo = pendingCallIsVideo
            pendingCallIsVideo = false
            callViewModel.startCall(
                context = context,
                chat = chat,
                user = User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar),
                isVideo = startAsVideo
            )
        } else if (hasAudio && pendingCallIsVideo) {
            pendingCallIsVideo = false
            callViewModel.startCall(
                context = context,
                chat = chat,
                user = User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar),
                isVideo = false
            )
        } else {
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
        if (permissions.all { context.checkSelfPermissionCompat(it) }) {
            pendingCallIsVideo = false
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

    fun pickAttachment(action: AttachmentAction) {
        showAttachmentMenu = false
        when (action) {
            AttachmentAction.Gallery -> galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AttachmentAction.File -> multiPicker.launch(arrayOf("*/*"))
            AttachmentAction.Audio -> multiPicker.launch(arrayOf("audio/*"))
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
                        viewModel.shareLocation(
                            chat.id,
                            currentUserId,
                            currentUserName,
                            currentUserAvatar,
                            location.latitude,
                            location.longitude,
                            cloudChatEnabled = settingsState.cloudChatEnabled,
                            chat = chat
                        )
                    } else {
                        showLocationDialog = true
                    }
                } else {
                    locationPermissionLauncher.launch(permissions)
                }
            }
            AttachmentAction.Contact -> showContactShare = true
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
            val optimistic = OptimisticMessageManager.createOptimisticMessage(
                chatId = chat.id,
                text = "",
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                attachmentUrl = Uri.fromFile(file).toString(),
                attachmentType = "audio",
                attachmentName = file.name,
                attachmentSize = file.length()
            )
            viewModel.addOptimisticMessage(optimistic.message)
            ChatFeedback.playSent(settingsState.chatSounds)
            viewModel.uploadAndSendAttachment(
                chatId = chat.id,
                fileName = file.name,
                mimeType = "audio/mp4",
                bytes = file.readBytes(),
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                caption = "",
                optimisticTempId = optimistic.tempId,
                cloudChatEnabled = settingsState.cloudChatEnabled,
                chat = chat
            )
        }
    }

    fun sendCurrentMessage() {
        val trimmed = messageText.trim()
        val attachments = pendingAttachments.toList()
        HelloDebugLog.d("ChatRoom", "sendCurrentMessage chatId=${chat.id} textLength=${trimmed.length} attachments=${attachments.size} replyTo=${replyTo?.id}")
        val replySnapshot = replyTo?.let {
            ChatModels.ReplyTo(
                id = it.id,
                text = it.text.ifBlank { "Attachment" },
                senderName = it.senderName,
                senderId = it.senderId
            )
        }

        if (trimmed.isBlank() && attachments.isEmpty()) return

        if (attachments.isNotEmpty()) {
            messageText = ""
            pendingAttachments.clear()
            replyTo = null
            AnimationUtils.Haptics.sendMessage(context)
            ChatFeedback.playSent(settingsState.chatSounds)
            stopTypingIndicator()
            viewModel.uploadAndSendAttachments(
                chatId = chat.id,
                attachments = attachments,
                senderId = currentUserId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                caption = trimmed,
                replyTo = replySnapshot,
                cloudChatEnabled = settingsState.cloudChatEnabled,
                chat = chat
            )
            return
        }

        val optimistic = OptimisticMessageManager.createOptimisticMessage(
            chatId = chat.id,
            text = trimmed,
            senderId = currentUserId,
            senderName = currentUserName,
            senderAvatar = currentUserAvatar,
            replyTo = replySnapshot
        )
        viewModel.addOptimisticMessage(optimistic.message)
        messageText = ""
        replyTo = null
        AnimationUtils.Haptics.sendMessage(context)
        ChatFeedback.playSent(settingsState.chatSounds)
        stopTypingIndicator()
        viewModel.sendMessage(
            chatId = chat.id,
            text = trimmed,
            senderId = currentUserId,
            senderName = currentUserName,
            senderAvatar = currentUserAvatar,
            replyTo = replySnapshot,
            optimisticTempId = optimistic.tempId,
            cloudChatEnabled = settingsState.cloudChatEnabled,
            chat = chat
        )
    }

    fun sendSticker(sticker: String) {
        val payload = createStickerMessageText(sticker)
        val replySnapshot = replyTo?.let {
            ChatModels.ReplyTo(
                id = it.id,
                text = it.text.ifBlank { "Attachment" },
                senderName = it.senderName,
                senderId = it.senderId
            )
        }
        val optimistic = OptimisticMessageManager.createOptimisticMessage(
            chatId = chat.id,
            text = payload,
            senderId = currentUserId,
            senderName = currentUserName,
            senderAvatar = currentUserAvatar,
            replyTo = replySnapshot
        )
        viewModel.addOptimisticMessage(optimistic.message)
        replyTo = null
        showEmojiRow = false
        AnimationUtils.Haptics.sendMessage(context)
        ChatFeedback.playSent(settingsState.chatSounds)
        stopTypingIndicator()
        viewModel.sendMessage(
            chatId = chat.id,
            text = payload,
            senderId = currentUserId,
            senderName = currentUserName,
            senderAvatar = currentUserAvatar,
            replyTo = replySnapshot,
            optimisticTempId = optimistic.tempId,
            cloudChatEnabled = settingsState.cloudChatEnabled,
            chat = chat
        )
    }

    DisposableEffect(chat.id, currentUserId, currentUserName, settingsState.chatSounds) {
        HelloNotificationCenter.setOpenChat(chat.id)
        val messageListener: (Message) -> Unit = { message ->
            if (message.chatId == chat.id) {
                scope.launch {
                    viewModel.appendFromSocket(
                        message = message,
                        currentUserId = currentUserId,
                        activeChatId = chat.id,
                        baseChat = chat
                    )
                    if (message.senderId != currentUserId) {
                        if (isNearBottom) {
                            socketManager.markMessagesRead(chat.id, currentUserId)
                            viewModel.clearUnreadForChat(chat.id, currentUserId, latestCloudChatEnabled)
                            lastReadMessageId = message.id
                            pendingBelowMessageIds = emptySet()
                        } else {
                            pendingBelowMessageIds = pendingBelowMessageIds + message.id
                        }
                        ChatFeedback.playReceived(settingsState.chatSounds)
                    }
                }
            }
        }
        val updateListener: (Message) -> Unit = { message ->
            if (message.chatId == chat.id) {
                scope.launch { viewModel.updateFromSocket(message) }
            }
        }
        val typingListener: (JSONObject) -> Unit = { payload ->
            if (payload.optString("chatId") == chat.id) {
                val userId = payload.optString("userId")
                val name = payload.optString("senderName", payload.optString("userName", "Someone"))
                    .trim()
                    .takeUnless {
                        it.isBlank() ||
                            it.equals("null", ignoreCase = true) ||
                            it.equals("undefined", ignoreCase = true)
                    }
                    ?: "Someone"
                if (userId != currentUserId && name != currentUserName) {
                    val isTyping = payload.optBoolean("isTyping", true)
                    typingExpirations = if (isTyping) {
                        LinkedHashMap(typingExpirations).apply {
                            remove(name)
                            put(name, System.currentTimeMillis() + 3_200L)
                        }
                    } else {
                        typingExpirations - name
                    }
                }
            }
        }

        socketManager.addMessageListener(messageListener)
        socketManager.addMessageUpdateListener(updateListener)
        socketManager.addTypingListener(typingListener)
        onDispose {
            HelloNotificationCenter.setOpenChat(null)
            stopTypingIndicator()
            typingExpirations = emptyMap()
            pendingBelowMessageIds = emptySet()
            socketManager.leaveChat(chat.id)
            socketManager.removeMessageListener(messageListener)
            socketManager.removeMessageUpdateListener(updateListener)
            socketManager.removeTypingListener(typingListener)
        }
    }

    LaunchedEffect(chat.id, currentUserId, currentUserName, currentUserAvatar) {
        delay(1500)
        socketManager.connect(context, User(id = currentUserId, name = currentUserName, avatar = currentUserAvatar))
        socketManager.joinChat(chat.id)
        viewModel.clearUnreadForChat(chat.id, currentUserId, latestCloudChatEnabled)
        lastReadMessageId = lastVisibleMessage?.takeIf { it.senderId != currentUserId }?.id
        pendingBelowMessageIds = emptySet()
    }

    Box(modifier = modifier.fillMaxSize().background(HelloColors.DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        ) {
            ChatHeader(
                title = title,
                subtitle = visibleSubtitle,
                avatarUrl = other?.avatar ?: chat.avatar,
                onBack = onBack,
                onOpenContactInfo = onOpenContactInfo,
                onAudioCall = { requestCall(false) },
                onVideoCall = { requestCall(true) },
                videoCallEnabled = !chat.isGroup,
                onMore = { showChatActions = true }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (chatTheme.darkMode) HelloColors.DarkBg else HelloColors.Bg)
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
                            onRetry = { viewModel.loadMessages(chat.id, cloudChatEnabled = settingsState.cloudChatEnabled) }
                        )
                        is ResultState.Success -> {
                            if (visibleRows.isEmpty()) {
                                EmptyRoom()
                            } else {
                                ChatMessageList(
                                    rows = visibleRows,
                                    currentUserId = currentUserId,
                                    unreadCount = unreadBelowCount,
                                    typingNames = typingNames,
                                    listState = listState,
                                    hasMoreOlderMessages = hasMoreOlderMessages,
                                    isLoadingOlderMessages = isLoadingOlderMessages,
                                    onOpenMessageMenu = { message ->
                                        selectedMessage = ActionMessageState(message = message, isOwn = message.senderId == currentUserId)
                                    },
                                    onOpenAttachment = { url -> openExternalTarget(context, url) },
                                    onOpenImage = { url, label -> mediaViewerState = MediaViewerState(url, label) },
                                    onDownloadAttachment = { url, fileName -> downloadAttachment(context, url, fileName) },
                                    bubbleOpacity = bubbleOpacity,
                                    onJumpToLatest = {
                                        scope.launch {
                                            if (visibleRows.isNotEmpty()) {
                                                listState.scrollToItem(visibleRows.lastIndex)
                                                pendingBelowMessageIds = emptySet()
                                            }
                                        }
                                    }
                                )
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
            if (pendingAttachments.isNotEmpty()) {
                AttachmentPreviewStrip(
                    files = pendingAttachments.toList(),
                    onRemove = { attachment ->
                        pendingAttachments.remove(attachment)
                        viewModel.resetUploadState()
                    },
                    modifier = Modifier.padding(vertical = HelloSpacing.Xs)
                )
            }
            replyTo?.let {
                ReplyComposerBar(
                    message = it,
                    onClear = { replyTo = null },
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
                )
            }

            ChatComposer(
                text = messageText,
                onTextChange = { messageText = it },
                showEmojiRow = showEmojiRow,
                onToggleEmoji = { showEmojiRow = !showEmojiRow },
                onEmoji = { emoji ->
                    messageText += emoji
                },
                onAttach = { showAttachmentMenu = true },
                onSendOrRecord = {
                    if (messageText.trim().isNotEmpty() || pendingAttachments.isNotEmpty()) {
                        sendCurrentMessage()
                    } else if (voiceState.active) {
                        finishVoiceNote(send = true)
                    } else {
                        startVoiceNote()
                    }
                },
                voiceState = voiceState,
                recordingElapsedSeconds = recordingElapsedSeconds,
                onCancelVoice = { finishVoiceNote(send = false) },
                hasPayload = messageText.trim().isNotEmpty() || pendingAttachments.isNotEmpty(),
                placeholder = if (pendingAttachments.isEmpty()) "Message Hello" else "Add a caption",
                enterSends = settingsState.enterSends,
                onKeyboardSend = { sendCurrentMessage() },
                modifier = Modifier.imePadding()
            )
        }

        selectedMessage?.let { selected ->
            ModalBottomSheet(
                onDismissRequest = { selectedMessage = null },
                containerColor = HelloColors.DarkPanelStrong
            ) {
                MessageActionSheet(
                    message = selected.message,
                    currentUserId = currentUserId,
                    isOwn = selected.isOwn,
                    onReply = {
                        replyTo = selected.message
                        selectedMessage = null
                    },
                    onStar = {
                        viewModel.starMessage(chat.id, selected.message.id, currentUserId, settingsState.cloudChatEnabled)
                        selectedMessage = null
                    },
                    onReact = { emoji ->
                        viewModel.reactToMessage(
                            chat.id,
                            selected.message.id,
                            emoji,
                            currentUserId,
                            cloudChatEnabled = settingsState.cloudChatEnabled
                        )
                        ChatFeedback.playReaction(settingsState.chatSounds)
                        selectedMessage = null
                    },
                    onPin = {
                        if ((selected.message.pinnedUntil ?: 0L) > System.currentTimeMillis()) {
                            viewModel.pinMessage(chat.id, selected.message.id, currentUserId, durationDays = 0, cloudChatEnabled = settingsState.cloudChatEnabled)
                        } else {
                            viewModel.pinMessage(chat.id, selected.message.id, currentUserId, cloudChatEnabled = settingsState.cloudChatEnabled)
                        }
                        selectedMessage = null
                    },
                    onOpen = {
                        normalizeAttachmentUrl(selected.message.attachmentUrl)?.let { openExternalTarget(context, it) }
                        selectedMessage = null
                    },
                    onDownload = {
                        normalizeAttachmentUrl(selected.message.attachmentUrl)?.let { url ->
                            downloadAttachment(context, url, selected.message.attachmentName)
                        }
                        selectedMessage = null
                    },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(selected.message.text.ifBlank { selected.message.attachmentName ?: "Attachment" }))
                        selectedMessage = null
                    },
                    onDeleteForMe = {
                        deleteMode = "message"
                        showDeleteConfirm = true
                        selectedMessage = selected
                    },
                    onDeleteForEveryone = {
                        deleteMode = "for_everyone"
                        showDeleteConfirm = true
                        selectedMessage = selected
                    }
                )
            }
        }

        if (showDeleteConfirm && selectedMessage != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = HelloColors.DarkPanelStrong,
                title = { Text(if (deleteMode == "for_everyone") "Delete for everyone?" else "Delete for me?", color = HelloColors.DarkText) },
                text = {
                    Text(
                        if (deleteMode == "for_everyone") "This removes the message for everyone in the chat." else "This removes the message from your copy of the chat.",
                        color = HelloColors.DarkTextMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteMessage(chat.id, selectedMessage!!.message.id, currentUserId, deleteMode, settingsState.cloudChatEnabled)
                        showDeleteConfirm = false
                        selectedMessage = null
                    }) {
                        Text("Delete", color = HelloColors.DarkDanger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        selectedMessage = null
                    }) {
                        Text("Cancel", color = HelloColors.DarkTextMuted)
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

        if (showEmojiRow) {
            ModalBottomSheet(
                onDismissRequest = { showEmojiRow = false },
                containerColor = HelloColors.DarkPanelStrong
            ) {
                EmojiStickerPickerSheet(
                    onEmojiSelected = { emoji ->
                        messageText += emoji
                    },
                    onStickerSelected = { sticker ->
                        sendSticker(sticker)
                    },
                    onPickGif = {
                        showEmojiRow = false
                        gifPicker.launch("image/gif")
                    }
                )
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
                title = { Text("Share location", color = HelloColors.DarkText) },
                text = {
                    HelloSearchBar(
                        value = manualLocationText,
                        onValueChange = { manualLocationText = it },
                        placeholder = "Type a place or address"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (manualLocationText.isNotBlank()) {
                            viewModel.shareLocation(
                                chat.id,
                                currentUserId,
                                currentUserName,
                                currentUserAvatar,
                                0.0,
                                0.0,
                                manualLocationText.trim(),
                                cloudChatEnabled = settingsState.cloudChatEnabled,
                                chat = chat
                            )
                            manualLocationText = ""
                            showLocationDialog = false
                        }
                    }) { Text("Send", color = HelloColors.DarkAccent) }
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
                    viewModel.shareContact(
                        chat.id,
                        currentUserId,
                        currentUserName,
                        currentUserAvatar,
                        user,
                        cloudChatEnabled = settingsState.cloudChatEnabled,
                        chat = chat
                    )
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
                    viewModel.clearChat(chat.id, currentUserId, settingsState.cloudChatEnabled)
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
                    viewModel.deleteChat(chat.id, currentUserId, settingsState.cloudChatEnabled)
                    showDeleteChatConfirm = false
                    onChatDeleted()
                }
            )
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
                title = { Text("File too large", color = HelloColors.DarkText) },
                text = { Text(fileSizeError.orEmpty(), color = HelloColors.DarkText) },
                confirmButton = {
                    TextButton(onClick = { fileSizeError = null }) {
                        Text("OK", color = HelloColors.DarkAccent)
                    }
                }
            )
        }

        mediaViewerState?.let { state ->
            MediaViewer(state = state, onDismiss = { mediaViewerState = null })
        }

    }
}

private fun Context.checkSelfPermissionCompat(permission: String): Boolean {
    return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private fun classifyAttachment(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "image"
    mimeType.startsWith("audio/") -> "audio"
    mimeType.startsWith("video/") -> "video"
    else -> "file"
}

private object ChatFeedback {
    fun playSent(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_ACK, 70)
    fun playReceived(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_BEEP2, 60)
    fun playReaction(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_BEEP, 45)

    private fun play(enabled: Boolean, tone: Int, durationMs: Int) {
        if (!enabled) return
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 34)
            generator.startTone(tone, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, (durationMs + 40).toLong())
        }
    }
}

private fun readPickedFile(context: Context, uri: Uri): AttachmentDraft? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: "hello-attachment"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return AttachmentDraft(uri = uri, name = name, mimeType = mimeType, bytes = bytes)
}

@Composable
private fun PermissionRequiredDialog(
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Camera/microphone permission", color = HelloColors.DarkText) },
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
private fun CallResultDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Call status", color = HelloColors.DarkText) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
    )
}

@Composable
private fun ConfirmChatDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text(title, color = HelloColors.DarkText) },
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

@Composable
private fun EmptyRoom() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Say hi", color = HelloColors.DarkText)
            Text(text = "Start the conversation and new messages will appear here instantly.", color = HelloColors.DarkTextMuted)
        }
    }
}
