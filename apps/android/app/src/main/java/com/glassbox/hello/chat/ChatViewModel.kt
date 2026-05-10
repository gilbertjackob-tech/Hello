package com.glassbox.hello.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.network.HelloApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val api = HelloApiClient()
    private val repository = ChatRepository(api)

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
    }

    fun loadUsers(currentUserId: String, query: String? = null) {
        _usersState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.fetchUsers(query)
            _usersState.value = when {
                result.isSuccess -> {
                    val users = result.getOrNull()
                        .orEmpty()
                        .filter { it.id != currentUserId }
                        .sortedWith(compareByDescending<User> { it.online == true }.thenBy { it.name.lowercase() })
                    ResultState.Success(users)
                }
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load users")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun startDirectChat(currentUserId: String, targetUserId: String) {
        _createChatState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.createDirectChat(currentUserId, targetUserId)
            _createChatState.value = when {
                result.isSuccess -> ResultState.Success(result.getOrNull()!!)
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to start chat")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun createGroupChat(currentUserId: String, name: String, memberIds: List<String>) {
        _createChatState.value = ResultState.Loading
        viewModelScope.launch {
            val members = (memberIds + currentUserId).distinct()
            val result = repository.createGroupChat(name, members)
            _createChatState.value = when {
                result.isSuccess -> ResultState.Success(result.getOrNull()!!)
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to create group")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun loadChats(userId: String) {
        val hasCached = _chatsState.value is ResultState.Success
        if (!hasCached) {
            _chatsState.value = ResultState.Loading
        } else {
            _chatsRefreshing.value = true
        }
        viewModelScope.launch {
            val result = repository.fetchChats(userId)
            if (result.isSuccess) {
                _chatsState.value = ResultState.Success(result.getOrNull() ?: emptyList())
            } else if (!hasCached) {
                _chatsState.value = ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load chats")
            }
            _chatsRefreshing.value = false
        }
    }

    fun loadMessages(chatId: String) {
        _messagesPaginationOffset.value = 0
        _hasMoreOlderMessages.value = true
        val hasCached = _messagesState.value is ResultState.Success
        if (!hasCached) {
            _messagesState.value = ResultState.Loading
        } else {
            _messagesRefreshing.value = true
        }
        viewModelScope.launch {
            val result = repository.fetchMessages(chatId, limit = MESSAGE_PAGE_SIZE, offset = 0)
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
    fun loadOlderMessages(chatId: String) {
        if (_isLoadingOlderMessages.value || !_hasMoreOlderMessages.value) return
        
        _isLoadingOlderMessages.value = true
        viewModelScope.launch {
            val result = repository.fetchMessages(
                chatId,
                limit = MESSAGE_PAGE_SIZE,
                offset = _messagesPaginationOffset.value
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
    fun reactToMessage(chatId: String, messageId: String, emoji: String, userId: String) {
        viewModelScope.launch {
            applyReactionPatch(messageId, emoji, userId)  // Optimistic UI update
            val result = repository.reactToMessage(chatId, messageId, emoji, userId)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId)
            }
        }
    }

    /**
     * Star message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun starMessage(chatId: String, messageId: String, userId: String) {
        viewModelScope.launch {
            applyStarPatch(messageId, userId)  // Optimistic UI update
            val result = repository.starMessage(chatId, messageId, userId)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId)
            }
        }
    }

    /**
     * Pin message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun pinMessage(chatId: String, messageId: String, durationDays: Int = 7) {
        viewModelScope.launch {
            applyPinPatch(messageId, durationDays)  // Optimistic UI update
            val result = repository.pinMessage(chatId, messageId, durationDays)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId)
            }
        }
    }

    /**
     * Delete message with optimistic update - NO FULL RELOAD
     * Updates message instantly, reverts on API failure
     */
    fun deleteMessage(chatId: String, messageId: String, userId: String, type: String = "message") {
        viewModelScope.launch {
            applyDeletePatch(messageId, userId)  // Optimistic UI update
            val result = repository.deleteMessage(chatId, messageId, userId, type)
            if (result.isSuccess) {
                result.getOrNull()?.let { upsertMessage(it) }  // Patch with server response
            } else {
                // Only reload on error (rare)
                loadMessages(chatId)
            }
        }
    }

    fun clearChat(chatId: String, userId: String) {
        viewModelScope.launch {
            val previous = _messagesState.value
            _messagesState.value = ResultState.Success(emptyList())
            val result = repository.clearChat(chatId, userId)
            if (result.isFailure) {
                _messagesState.value = previous
            }
        }
    }

    fun deleteChat(chatId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId, userId)
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
        label: String? = null
    ) {
        viewModelScope.launch {
            val text = label ?: "Location shared"
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
                location = ChatModels.LocationData(lat = lat, lng = lng, isLive = false)
            )
            result.getOrNull()?.let { upsertMessage(it) }
        }
    }

    fun shareContact(
        chatId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        contact: User
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
            senderAvatar = senderAvatar
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
        optimisticTempId: String? = null
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
                replyTo = replyTo
            )
            _sendMessageState.value = when {
                result.isSuccess -> {
                    val message = result.getOrNull()!!
                    upsertMessage(message, optimisticTempId)
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
        optimisticTempId: String? = null
    ) {
        _uploadState.value = ResultState.Loading
        viewModelScope.launch {
            val upload = repository.uploadFile(fileName, mimeType, bytes, senderId)
            if (upload.isFailure) {
                optimisticTempId?.let { markMessageFailed(it) }
                _uploadState.value = ResultState.Error(upload.exceptionOrNull()?.message ?: "Failed to upload file")
                return@launch
            }
            val file = upload.getOrNull()!!
            _uploadState.value = ResultState.Success(file)
            sendMessage(
                chatId = chatId,
                text = caption,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                attachmentUrl = file.url,
                attachmentType = classifyAttachment(file.mimeType),
                attachmentName = file.originalName,
                attachmentSize = file.size,
                replyTo = replyTo,
                optimisticTempId = optimisticTempId
            )
        }
    }

    fun appendFromSocket(message: Message) {
        upsertMessage(message)
    }

    fun updateFromSocket(message: Message) {
        upsertMessage(message)
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
