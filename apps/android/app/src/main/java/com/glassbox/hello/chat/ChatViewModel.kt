package com.glassbox.hello.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.chat.components.AttachmentDraft
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.network.HelloApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatViewModel : ViewModel() {
    private val api = HelloApiClient()
    private var repository = ChatRepository(api)

    private val _chatsState = MutableStateFlow<ResultState<List<Chat>>>(ResultState.Loading)
    val chatsState: StateFlow<ResultState<List<Chat>>> = _chatsState

    private val _messagesState = MutableStateFlow<ResultState<List<Message>>>(ResultState.Loading)
    val messagesState: StateFlow<ResultState<List<Message>>> = _messagesState

    private val _usersState = MutableStateFlow<ResultState<List<User>>>(ResultState.Success(emptyList()))
    val usersState: StateFlow<ResultState<List<User>>> = _usersState

    private val _createChatState = MutableStateFlow<ResultState<Chat>?>(null)
    val createChatState: StateFlow<ResultState<Chat>?> = _createChatState

    private val _sendMessageState = MutableStateFlow<ResultState<Message>?>(null)
    val sendMessageState: StateFlow<ResultState<Message>?> = _sendMessageState

    private val _uploadState = MutableStateFlow<ResultState<ChatModels.UploadedFile>?>(null)
    val uploadState: StateFlow<ResultState<ChatModels.UploadedFile>?> = _uploadState
    private val _chatsRefreshing = MutableStateFlow(false)
    val chatsRefreshing: StateFlow<Boolean> = _chatsRefreshing
    private val _messagesRefreshing = MutableStateFlow(false)
    val messagesRefreshing: StateFlow<Boolean> = _messagesRefreshing

    // Pagination state
    private val _messagesPaginationOffset = MutableStateFlow(0)
    val messagesPaginationOffset: StateFlow<Int> = _messagesPaginationOffset
    private val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages
    private val _hasMoreOlderMessages = MutableStateFlow(true)
    val hasMoreOlderMessages: StateFlow<Boolean> = _hasMoreOlderMessages

    companion object {
        private const val MESSAGE_PAGE_SIZE = 50
        private const val TAG = "HelloInbox"
    }

    fun configureCloudChat(context: Context) {
        Log.d(TAG, "configure_cloud_chat context=${context.applicationContext.javaClass.simpleName}")
        repository = ChatRepository(api, CloudChatRepository(context.applicationContext))
    }

    fun loadUsers(currentUserId: String, query: String? = null, cloudChatEnabled: Boolean = true) {
        Log.d(TAG, "vm_load_users_start currentUserId=$currentUserId query=${query.orEmpty()} cloudChatEnabled=$cloudChatEnabled")
        _usersState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.fetchUsers(query, cloudChatEnabled)
            _usersState.value = when {
                result.isSuccess -> {
                    val users = result.getOrNull()
                        .orEmpty()
                        .filter { it.id != currentUserId }
                        .filter { cloudChatEnabled || query?.isNotBlank() == true || !it.isGeneratedIdentity() }
                        .sortedWith(compareByDescending<User> { it.online == true }.thenBy { normalizedSortKey(it.name) })
                    Log.d(
                        TAG,
                        "vm_load_users_success currentUserId=$currentUserId query=${query.orEmpty()} cloudChatEnabled=$cloudChatEnabled rawCount=${result.getOrNull().orEmpty().size} visibleCount=${users.size} ids=${users.take(8).joinToString(",") { it.id }}"
                    )
                    ResultState.Success(users)
                }
                result.isFailure -> {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load users"
                    Log.w(TAG, "vm_load_users_error currentUserId=$currentUserId query=${query.orEmpty()} cloudChatEnabled=$cloudChatEnabled error=$error")
                    ResultState.Error(error)
                }
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun startDirectChat(
        currentUserId: String,
        currentUserName: String,
        targetUserId: String,
        targetUserName: String? = null,
        cloudChatEnabled: Boolean = true
    ) {
        _createChatState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.createDirectChat(currentUserId, targetUserId, currentUserName, targetUserName, cloudChatEnabled)
            _createChatState.value = when {
                result.isSuccess -> {
                    val chat = result.getOrNull()
                        ?: return@launch run {
                            _createChatState.value = ResultState.Error("Start chat response was empty")
                        }
                    upsertChat(chat, currentUserId)
                    ResultState.Success(chat)
                }
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to start chat")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun createGroupChat(
        currentUserId: String,
        currentUserName: String,
        name: String,
        memberIds: List<String>,
        cloudChatEnabled: Boolean = true
    ) {
        _createChatState.value = ResultState.Loading
        viewModelScope.launch {
            val members = (memberIds + currentUserId).distinct()
            val result = repository.createGroupChat(currentUserId, currentUserName, name, members, cloudChatEnabled)
            _createChatState.value = when {
                result.isSuccess -> result.getOrNull()
                    ?.let { ResultState.Success(it) }
                    ?: ResultState.Error("Create group response was empty")
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to create group")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun loadChats(userId: String, cloudChatEnabled: Boolean = true) {
        val hasCached = _chatsState.value is ResultState.Success
        val cachedCloudChats = repository.cachedChats(userId, cloudChatEnabled)
            .dedupeDirectChats(userId)
            .filter { it.isProfessionalInboxItem(userId) }
        Log.d(
            TAG,
            "vm_load_chats_start userId=$userId cloudChatEnabled=$cloudChatEnabled hadVisibleState=$hasCached cachedVisibleCount=${cachedCloudChats.size} cachedIds=${cachedCloudChats.take(8).joinToString(",") { it.id }}"
        )
        if (cachedCloudChats.isNotEmpty()) {
            _chatsState.value = ResultState.Success(cachedCloudChats)
        }
        val hasVisibleChats = hasCached || cachedCloudChats.isNotEmpty()
        if (!hasVisibleChats) {
            _chatsState.value = ResultState.Loading
        } else {
            _chatsRefreshing.value = true
        }
        viewModelScope.launch {
            val result = repository.fetchChats(userId, cloudChatEnabled)
            if (result.isSuccess) {
                val chats = result.getOrNull()
                    .orEmpty()
                    .dedupeDirectChats(userId)
                    .filter { it.isProfessionalInboxItem(userId) }
                Log.d(
                    TAG,
                    "vm_load_chats_success userId=$userId cloudChatEnabled=$cloudChatEnabled visibleCount=${chats.size} ids=${chats.take(8).joinToString(",") { it.id }} previews=${chats.take(4).joinToString(" | ") { "${it.id}:${it.lastMessage.orEmpty().take(30)}" }}"
                )
                _chatsState.value = ResultState.Success(
                    chats
                )
            } else if (!hasVisibleChats) {
                val error = result.exceptionOrNull()?.message ?: "Failed to load chats"
                Log.w(TAG, "vm_load_chats_error userId=$userId cloudChatEnabled=$cloudChatEnabled error=$error")
                _chatsState.value = ResultState.Error(error)
            } else {
                Log.w(
                    TAG,
                    "vm_load_chats_refresh_error userId=$userId cloudChatEnabled=$cloudChatEnabled error=${result.exceptionOrNull()?.message ?: "Failed to load chats"}"
                )
            }
            _chatsRefreshing.value = false
        }
    }

    fun loadMessages(chatId: String, cloudChatEnabled: Boolean = true) {
        _messagesPaginationOffset.value = 0
        _hasMoreOlderMessages.value = true
        val hasCached = _messagesState.value is ResultState.Success
        if (!hasCached) {
            _messagesState.value = ResultState.Loading
        } else {
            _messagesRefreshing.value = true
        }
        viewModelScope.launch {
            val result = repository.fetchMessages(chatId, limit = MESSAGE_PAGE_SIZE, offset = 0, cloudChatEnabled = cloudChatEnabled)
            if (result.isSuccess) {
                val messages = (result.getOrNull() ?: emptyList()).sortedBy { it.timestamp }.distinctBy { it.id }
                _messagesState.value = ResultState.Success(messages)
                _hasMoreOlderMessages.value = messages.size >= MESSAGE_PAGE_SIZE
                _messagesPaginationOffset.value = messages.size
            } else if (!hasCached) {
                _messagesState.value = ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load messages")
            }
            _messagesRefreshing.value = false
        }
    }

    /**
     * Load older messages for pagination (scroll to top to load history)
     */
    fun loadOlderMessages(chatId: String, cloudChatEnabled: Boolean = true) {
        if (_isLoadingOlderMessages.value || !_hasMoreOlderMessages.value) return
        
        _isLoadingOlderMessages.value = true
        viewModelScope.launch {
            val result = repository.fetchMessages(
                chatId,
                limit = MESSAGE_PAGE_SIZE,
                offset = _messagesPaginationOffset.value,
                cloudChatEnabled = cloudChatEnabled
            )
            if (result.isSuccess) {
                val newMessages = (result.getOrNull() ?: emptyList()).sortedBy { it.timestamp }.distinctBy { it.id }
                val current = _messagesState.value
                val currentMessages = if (current is ResultState.Success) current.data else emptyList()
                val unseenMessages = newMessages.filter { incoming -> currentMessages.none { it.id == incoming.id } }
                val allMessages = if (current is ResultState.Success) {
                    (unseenMessages + current.data).distinctBy { it.id }.sortedBy { it.timestamp }
                } else {
                    unseenMessages
                }
                _messagesState.value = ResultState.Success(allMessages)
                _hasMoreOlderMessages.value = unseenMessages.isNotEmpty() && newMessages.size >= MESSAGE_PAGE_SIZE
                _messagesPaginationOffset.value += newMessages.size
            }
            _isLoadingOlderMessages.value = false
        }
    }

    /**
     * React to message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun reactToMessage(
        chatId: String,
        messageId: String,
        emoji: String,
        userId: String,
        cloudChatEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            applyReactionPatch(messageId, emoji, userId)  // Optimistic UI update
            val result = repository.reactToMessage(chatId, messageId, emoji, userId, cloudChatEnabled)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId, cloudChatEnabled = cloudChatEnabled)
            }
        }
    }

    /**
     * Star message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun starMessage(chatId: String, messageId: String, userId: String, cloudChatEnabled: Boolean = true) {
        viewModelScope.launch {
            applyStarPatch(messageId, userId)  // Optimistic UI update
            val result = repository.starMessage(chatId, messageId, userId, cloudChatEnabled)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId, cloudChatEnabled = cloudChatEnabled)
            }
        }
    }

    /**
     * Pin message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun pinMessage(chatId: String, messageId: String, userId: String, durationDays: Int = 7, cloudChatEnabled: Boolean = true) {
        viewModelScope.launch {
            applyPinPatch(messageId, durationDays)  // Optimistic UI update
            val result = repository.pinMessage(chatId, messageId, userId, durationDays, cloudChatEnabled)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId, cloudChatEnabled = cloudChatEnabled)
            }
        }
    }

    /**
     * Delete message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun deleteMessage(chatId: String, messageId: String, userId: String, type: String = "message", cloudChatEnabled: Boolean = true) {
        viewModelScope.launch {
            applyDeletePatch(messageId, userId)  // Optimistic UI update
            val result = repository.deleteMessage(chatId, messageId, userId, type, cloudChatEnabled)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId, cloudChatEnabled = cloudChatEnabled)
            }
        }
    }

    fun clearChat(chatId: String, userId: String, cloudChatEnabled: Boolean = true) {
        viewModelScope.launch {
            val previous = _messagesState.value
            _messagesState.value = ResultState.Success(emptyList())
            val result = repository.clearChat(chatId, userId, cloudChatEnabled)
            if (result.isFailure) {
                _messagesState.value = previous
            }
        }
    }

    fun deleteChat(chatId: String, userId: String, cloudChatEnabled: Boolean = true) {
        viewModelScope.launch {
            repository.deleteChat(chatId, userId, cloudChatEnabled)
            _messagesState.value = ResultState.Success(emptyList())
        }
    }

    fun shareLocation(
        chatId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        lat: Double,
        lng: Double,
        label: String? = null,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ) {
        viewModelScope.launch {
            val mapUrl = "https://maps.google.com/?q=$lat,$lng"
            val text = label ?: if (cloudChatEnabled && chat != null) "Location shared: $mapUrl" else "Location shared"
            val optimistic = OptimisticMessageManager.createOptimisticMessage(
                chatId = chatId,
                text = text,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar
            )
            addOptimisticMessage(optimistic.message.copy(location = ChatModels.LocationData(lat = lat, lng = lng, isLive = false)))
            val result = repository.sendMessage(
                chatId = chatId,
                text = text,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                location = if (cloudChatEnabled && chat != null) null else ChatModels.LocationData(lat = lat, lng = lng, isLive = false),
                cloudChatEnabled = cloudChatEnabled,
                chat = chat
            )
            result.getOrNull()?.let { upsertMessage(it, optimistic.tempId) }
        }
    }

    fun shareContact(
        chatId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        contact: User,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ) {
        val details = buildString {
            append("Contact: ${contact.name}")
            contact.phone?.takeIf { it.isNotBlank() }?.let { append("\nPhone: $it") }
            contact.email?.takeIf { it.isNotBlank() }?.let { append("\nEmail: $it") }
        }
        sendMessage(
            chatId = chatId,
            text = details,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            cloudChatEnabled = cloudChatEnabled,
            chat = chat
        )
    }

    fun sendMessage(
        chatId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String? = null,
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long? = null,
        replyTo: ChatModels.ReplyTo? = null,
        optimisticTempId: String? = null,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ) {
        _sendMessageState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.sendMessage(
                chatId = chatId,
                text = text,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                attachmentUrl = attachmentUrl,
                attachmentType = attachmentType,
                attachmentName = attachmentName,
                attachmentSize = attachmentSize,
                replyTo = replyTo,
                cloudChatEnabled = cloudChatEnabled,
                chat = chat
            )
            _sendMessageState.value = when {
                result.isSuccess -> {
                    val message = result.getOrNull()
                        ?: return@launch run {
                            optimisticTempId?.let { markMessageFailed(it) }
                            _sendMessageState.value = ResultState.Error("Send message response was empty")
                        }
                    upsertMessage(message, optimisticTempId)
                    upsertChatFromMessage(
                        message = message,
                        currentUserId = senderId,
                        activeChatId = chat?.id ?: chatId,
                        baseChat = chat,
                        incoming = false
                    )
                    ResultState.Success(message)
                }
                result.isFailure -> {
                    optimisticTempId?.let { markMessageFailed(it) }
                    ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to send message")
                }
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun uploadAndSendAttachment(
        chatId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        senderId: String,
        senderName: String,
        senderAvatar: String? = null,
        caption: String = "",
        replyTo: ChatModels.ReplyTo? = null,
        optimisticTempId: String? = null,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ) {
        _uploadState.value = ResultState.Loading
        viewModelScope.launch {
            val upload = if (cloudChatEnabled && chat != null) {
                repository.uploadCloudAttachment(fileName, mimeType, bytes)
            } else {
                repository.uploadFile(fileName, mimeType, bytes, senderId)
            }
            if (upload.isFailure) {
                optimisticTempId?.let { markMessageFailed(it) }
                _uploadState.value = ResultState.Error(upload.exceptionOrNull()?.message ?: "Failed to upload file")
                return@launch
            }
            val file = upload.getOrNull()
                ?: run {
                    optimisticTempId?.let { markMessageFailed(it) }
                    _uploadState.value = ResultState.Error("Upload response was empty")
                    return@launch
                }
            _uploadState.value = ResultState.Success(file)
            sendMessage(
                chatId = chatId,
                text = caption,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                attachmentUrl = if (cloudChatEnabled && chat != null) "cloud:${file.id ?: file.url}" else file.url,
                attachmentType = classifyAttachment(file.mimeType),
                attachmentName = file.originalName,
                attachmentSize = file.size,
                replyTo = replyTo,
                optimisticTempId = optimisticTempId,
                cloudChatEnabled = cloudChatEnabled,
                chat = chat
            )
        }
    }

    fun uploadAndSendAttachments(
        chatId: String,
        attachments: List<AttachmentDraft>,
        senderId: String,
        senderName: String,
        senderAvatar: String? = null,
        caption: String = "",
        replyTo: ChatModels.ReplyTo? = null,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ) {
        if (attachments.isEmpty()) return

        _uploadState.value = ResultState.Loading
        viewModelScope.launch {
            var lastUploaded: ChatModels.UploadedFile? = null
            var firstError: String? = null

            for (attachment in attachments) {
                val optimistic = OptimisticMessageManager.createOptimisticMessage(
                    chatId = chatId,
                    text = caption,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    attachmentUrl = attachment.previewUrl,
                    attachmentType = classifyAttachment(attachment.mimeType),
                    attachmentName = attachment.name,
                    attachmentSize = attachment.sizeBytes,
                    replyTo = replyTo
                )
                addOptimisticMessage(optimistic.message)

                val upload = if (cloudChatEnabled && chat != null) {
                    repository.uploadCloudAttachment(attachment.name, attachment.mimeType, attachment.bytes)
                } else {
                    repository.uploadFile(attachment.name, attachment.mimeType, attachment.bytes, senderId)
                }
                if (upload.isFailure) {
                    markMessageFailed(optimistic.tempId)
                    firstError = firstError ?: (upload.exceptionOrNull()?.message ?: "Failed to upload file")
                    continue
                }

                val file = upload.getOrNull()
                if (file == null) {
                    markMessageFailed(optimistic.tempId)
                    firstError = firstError ?: "Upload response was empty"
                    continue
                }
                lastUploaded = file

                val result = repository.sendMessage(
                    chatId = chatId,
                    text = caption,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    attachmentUrl = if (cloudChatEnabled && chat != null) "cloud:${file.id ?: file.url}" else file.url,
                    attachmentType = classifyAttachment(file.mimeType),
                    attachmentName = file.originalName,
                    attachmentSize = file.size,
                    replyTo = replyTo,
                    cloudChatEnabled = cloudChatEnabled,
                    chat = chat
                )

                if (result.isSuccess) {
                    result.getOrNull()?.let {
                        upsertMessage(it, optimistic.tempId)
                        upsertChatFromMessage(
                            message = it,
                            currentUserId = senderId,
                            activeChatId = chat?.id ?: chatId,
                            baseChat = chat,
                            incoming = false
                        )
                    }
                } else {
                    markMessageFailed(optimistic.tempId)
                    firstError = firstError ?: (result.exceptionOrNull()?.message ?: "Failed to send message")
                }
            }

            _uploadState.value = when {
                firstError != null -> ResultState.Error(firstError!!)
                lastUploaded != null -> ResultState.Success(lastUploaded!!)
                else -> null
            }
        }
    }

    fun appendFromSocket(
        message: Message,
        currentUserId: String? = null,
        activeChatId: String? = null,
        baseChat: Chat? = null
    ) {
        Log.d(
            TAG,
            "vm_append_from_socket currentUserId=${currentUserId.orEmpty()} chatId=${message.chatId} senderId=${message.senderId} activeChatId=${activeChatId.orEmpty()} text=${message.text.take(60)}"
        )
        upsertMessage(message)
        if (currentUserId != null) {
            upsertChatFromMessage(
                message = message,
                currentUserId = currentUserId,
                activeChatId = activeChatId,
                baseChat = baseChat,
                incoming = message.senderId != currentUserId
            )
        }
    }

    fun updateFromSocket(message: Message) {
        upsertMessage(message)
    }

    fun upsertChatFromSocket(chat: Chat, currentUserId: String) {
        Log.d(
            TAG,
            "vm_upsert_chat_from_socket currentUserId=$currentUserId chatId=${chat.id} directKey=${chat.directKey.orEmpty()} members=${chat.memberIds().joinToString(",")} lastMessage=${chat.lastMessage.orEmpty().take(60)}"
        )
        upsertChat(chat, currentUserId)
    }

    fun clearUnreadForChat(chatId: String, currentUserId: String, cloudChatEnabled: Boolean = true) {
        val current = _chatsState.value
        if (current is ResultState.Success) {
            _chatsState.value = ResultState.Success(
                current.data.map { chat ->
                    if (chat.id == chatId) chat.copy(unreadCount = 0) else chat
                }
            )
        }
        repository.clearCachedUnread(currentUserId, chatId, cloudChatEnabled)
    }

    fun applyPresenceUpdate(payload: JSONObject) {
        val userId = payload.optString("userId").ifBlank { payload.optString("id") }
        if (userId.isBlank()) return

        val updatedUser = User(
            id = userId,
            name = payload.optString("name").ifBlank { payload.optString("displayName") }.ifBlank { "Hello user" },
            avatar = payload.optString("avatar").ifBlank { payload.optString("avatarUrl") }.ifBlank { null },
            username = payload.optString("username").ifBlank { null },
            phone = payload.optString("phone").ifBlank { null },
            email = payload.optString("email").ifBlank { null },
            online = payload.takeIf { it.has("online") }?.optBoolean("online"),
            lastActive = payload.takeIf { it.has("lastActive") }?.optLong("lastActive"),
            privacy = payload.optString("privacy").ifBlank { null },
            lastActivePrivacy = payload.optString("lastActivePrivacy").ifBlank { null }
        )

        val currentUsers = _usersState.value
        if (currentUsers is ResultState.Success) {
            var changed = false
            val nextUsers = currentUsers.data.map { existing ->
                if (existing.id != userId) {
                    existing
                } else {
                    val merged = existing.mergePresence(updatedUser)
                    changed = changed || merged != existing
                    merged
                }
            }
            if (changed) {
                _usersState.value = ResultState.Success(nextUsers)
            }
        }

        val currentChats = _chatsState.value
        if (currentChats is ResultState.Success) {
            var changed = false
            val nextChats = currentChats.data.map { chat ->
                val participants = chat.participants.orEmpty()
                if (participants.none { it.id == userId }) {
                    chat
                } else {
                    val nextParticipants = participants.map { participant ->
                        if (participant.id == userId) {
                            val merged = participant.mergePresence(updatedUser)
                            changed = changed || merged != participant
                            merged
                        } else {
                            participant
                        }
                    }
                    if (nextParticipants == participants) chat else chat.copy(participants = nextParticipants)
                }
            }
            if (changed) {
                _chatsState.value = ResultState.Success(nextChats)
            }
        }
    }

    /**
     * Add an optimistic (temporary) message to the list immediately
     */
    fun addOptimisticMessage(message: Message) {
        upsertMessage(message)
    }

    private fun upsertMessage(message: Message, replaceTempId: String? = null) {
        val current = _messagesState.value
        val messages = if (current is ResultState.Success) current.data else emptyList()
        val tempReplacementId = replaceTempId ?: findMatchingOptimisticMessageId(messages, message)
        val next = when {
            messages.any { it.id == message.id } -> {
                messages.map { if (it.id == message.id) message else it }
            }
            tempReplacementId != null -> {
                messages.map { candidate ->
                    if (candidate.id == tempReplacementId) {
                        message.copy(status = message.status ?: "sent")
                    } else {
                        candidate
                    }
                }
            }
            else -> {
                messages + message
            }
        }.sortedBy { it.timestamp }
        _messagesState.value = ResultState.Success(next)
    }

    private fun upsertChat(chat: Chat, currentUserId: String) {
        val current = _chatsState.value
        val chats = if (current is ResultState.Success) current.data else emptyList()
        val next = (chats.filterNot { existing ->
            existing.id == chat.id ||
                (!existing.isGroup && !chat.isGroup && existing.directDedupeKey(currentUserId) == chat.directDedupeKey(currentUserId))
        } + chat)
            .dedupeDirectChats(currentUserId)
            .filter { it.isProfessionalInboxItem(currentUserId) }
            .sortedByDescending { it.lastMessageTime ?: 0L }
        _chatsState.value = ResultState.Success(next)
    }

    private fun upsertChatFromMessage(
        message: Message,
        currentUserId: String,
        activeChatId: String?,
        baseChat: Chat?,
        incoming: Boolean
    ) {
        val current = _chatsState.value
        val chats = if (current is ResultState.Success) current.data else emptyList()
        val existing = chats.firstOrNull { it.id == message.chatId || it.id == baseChat?.id }
        val preview = message.text.takeIf { it.isNotBlank() }
            ?: message.attachmentName?.takeIf { it.isNotBlank() }
            ?: if (message.attachmentUrl != null) "Attachment" else ""
        val unread = when {
            !incoming -> 0
            activeChatId == message.chatId || activeChatId == baseChat?.id -> 0
            else -> (existing?.unreadCount ?: baseChat?.unreadCount ?: 0) + 1
        }
        val fallbackOther = ChatModels.User(
            id = message.senderId,
            name = message.senderName,
            avatar = message.senderAvatar
        )
        val updated = (existing ?: baseChat ?: Chat(
            id = message.chatId,
            type = "direct",
            name = message.senderName.ifBlank { "Cloud chat" },
            isGroup = false,
            members = listOf(currentUserId, message.senderId).distinct(),
            participants = listOf(fallbackOther)
        )).copy(
            id = message.chatId,
            lastMessage = preview,
            lastMessageTime = message.timestamp,
            unreadCount = unread
        )
        upsertChat(updated, currentUserId)
    }

    private fun classifyAttachment(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("audio/") -> "audio"
        else -> "file"
    }

    private fun mutateMessages(transform: (Message) -> Message) {
        val current = _messagesState.value
        if (current !is ResultState.Success) return
        _messagesState.value = ResultState.Success(current.data.map(transform))
    }

    private fun applyReactionPatch(messageId: String, emoji: String, userId: String) {
        mutateMessages { message ->
            if (message.id != messageId) return@mutateMessages message
            val current = message.reactions.orEmpty()
            val exists = current.any { it.userId == userId && it.emoji == emoji }
            val next = if (exists) {
                current.filterNot { it.userId == userId && it.emoji == emoji }
            } else {
                current + ChatModels.Reaction(emoji = emoji, userId = userId)
            }
            message.copy(reactions = next)
        }
    }

    private fun applyStarPatch(messageId: String, userId: String) {
        mutateMessages { message ->
            if (message.id != messageId) return@mutateMessages message
            val current = message.starredBy.orEmpty()
            val next = if (current.contains(userId)) current - userId else current + userId
            message.copy(starredBy = next)
        }
    }

    private fun applyPinPatch(messageId: String, durationDays: Int) {
        val now = System.currentTimeMillis()
        val until = now + durationDays * 24L * 60L * 60L * 1000L
        mutateMessages { message ->
            if (message.id == messageId) message.copy(pinnedUntil = until) else message
        }
    }

    private fun applyDeletePatch(messageId: String, userId: String) {
        mutateMessages { message ->
            if (message.id != messageId) return@mutateMessages message
            val deletedFor = (message.deletedFor.orEmpty() + userId).distinct()
            message.copy(
                text = "",
                isDeleted = true,
                deletedFor = deletedFor,
                attachmentUrl = null,
                attachmentName = null
            )
        }
    }

    private fun markMessageFailed(tempId: String) {
        mutateMessages { message ->
            if (message.id == tempId) message.copy(status = "failed") else message
        }
    }

    private fun findMatchingOptimisticMessageId(messages: List<Message>, incoming: Message): String? {
        return messages.firstOrNull { candidate ->
            OptimisticMessageManager.isTempId(candidate.id) &&
                candidate.chatId == incoming.chatId &&
                candidate.senderId == incoming.senderId &&
                candidate.replyTo == incoming.replyTo &&
                candidate.text.trim() == incoming.text.trim() &&
                candidate.attachmentName == incoming.attachmentName &&
                kotlin.math.abs(candidate.timestamp - incoming.timestamp) <= 120_000
        }?.id
    }

    private fun normalizedSortKey(value: String?): String =
        value?.trim()?.lowercase().orEmpty()

    private fun User.mergePresence(incoming: User): User {
        return copy(
            name = incoming.name.takeIf { it.isNotBlank() } ?: name,
            avatar = incoming.avatar ?: avatar,
            username = incoming.username ?: username,
            phone = incoming.phone ?: phone,
            email = incoming.email ?: email,
            online = incoming.online ?: online,
            lastActive = incoming.lastActive ?: lastActive,
            privacy = incoming.privacy ?: privacy,
            lastActivePrivacy = incoming.lastActivePrivacy ?: lastActivePrivacy
        )
    }

    fun resetSendMessageState() {
        _sendMessageState.value = null
    }

    fun resetUploadState() {
        _uploadState.value = null
    }

    fun resetCreateChatState() {
        _createChatState.value = null
    }
}
