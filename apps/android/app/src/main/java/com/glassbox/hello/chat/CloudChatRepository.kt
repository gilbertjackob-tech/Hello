package com.glassbox.hello.chat

import android.content.Context
import com.glassbox.hello.auth.CloudSessionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CloudChatRepository(
    context: Context,
    private val api: CloudChatApi = CloudChatApi()
) {
    private val sessionManager = CloudSessionManager(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("hello_cloud_chat_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun ensureConversation(chat: ChatModels.Chat, currentUserId: String, currentUserName: String, currentUserAvatar: String?) {
        api.upsertUser(currentUserId, currentUserName, currentUserAvatar)
        val members = chat.members
            ?: chat.participants?.map { it.id }
            ?: listOf(currentUserId)
        api.ensureConversation(
            conversationId = chat.id,
            title = chat.name,
            isGroup = chat.isGroup,
            createdBy = currentUserId,
            createdByName = currentUserName,
            memberIds = (members + currentUserId).distinct()
        ).getOrThrow()
    }

    suspend fun fetchMessages(chatId: String, limit: Int? = null, offset: Int? = null): Result<List<ChatModels.Message>> {
        val result = api.fetchMessages(chatId, limit, offset)
        result.getOrNull()?.let { saveMessages(chatId, it) }
        return if (result.isSuccess) result else {
            val cached = cachedMessages(chatId)
            if (cached.isNotEmpty()) Result.success(cached) else result
        }
    }

    suspend fun fetchChats(userId: String): Result<List<ChatModels.Chat>> {
        val result = api.fetchConversations(userId)
        val deduped = result.getOrNull()?.let { dedupeChats(it, userId) }
        deduped?.let { saveChats(userId, it) }
        return if (deduped != null) Result.success(deduped) else {
            val cached = cachedChats(userId)
            if (cached.isNotEmpty()) Result.success(cached) else result
        }
    }

    suspend fun fetchUsers(query: String? = null): Result<List<ChatModels.User>> {
        val usersResult = api.fetchUsers(query)
        val contacts = sessionManager.token()
            ?.let { token -> api.fetchContacts(token).getOrNull() }
            .orEmpty()
            .filter { contact ->
                query.isNullOrBlank() ||
                    contact.name.contains(query, ignoreCase = true) ||
                    contact.id.contains(query, ignoreCase = true) ||
                    contact.phone?.contains(query, ignoreCase = true) == true ||
                    contact.email?.contains(query, ignoreCase = true) == true
            }
        val users = usersResult.getOrNull().orEmpty()
        val merged = dedupeUsers(contacts + users)
        return when {
            merged.isNotEmpty() -> Result.success(merged)
            usersResult.isFailure -> usersResult
            else -> Result.success(emptyList())
        }
    }

    suspend fun createDirectChat(
        currentUserId: String,
        targetUserId: String,
        currentUserName: String,
        targetUserName: String? = null
    ): Result<ChatModels.Chat> {
        val result = api.ensureDirectConversation(
            currentUserId = currentUserId,
            targetUserId = targetUserId,
            currentUserName = currentUserName,
            targetUserName = targetUserName
        )
        result.getOrNull()?.let {
            cacheDirectConversationId(currentUserId, targetUserId, it.id)
            upsertCachedChat(currentUserId, it)
        }
        return result
    }

    suspend fun createGroupChat(currentUserId: String, currentUserName: String, name: String, members: List<String>): Result<ChatModels.Chat> {
        val conversationId = "group_${System.currentTimeMillis()}_${name.hashCode()}"
        val result = api.ensureConversation(
            conversationId = conversationId,
            title = name,
            isGroup = true,
            createdBy = currentUserId,
            createdByName = currentUserName,
            memberIds = members
        )
        result.getOrNull()?.let { upsertCachedChat(currentUserId, it) }
        return result
    }

    suspend fun sendTextMessage(
        chat: ChatModels.Chat,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        attachmentId: String? = null
    ): Result<ChatModels.Message> {
        val preparedChat = if (!chat.isGroup) {
            val targetUserId = chat.participants?.firstOrNull { it.id != senderId }?.id
                ?: chat.members?.firstOrNull { it != senderId }
            if (targetUserId != null) {
                createDirectChat(senderId, targetUserId, senderName, chat.otherParticipant(senderId)?.name).getOrElse { error ->
                    return Result.failure(error)
                }
            } else {
                val ensure = runCatching { ensureConversation(chat, senderId, senderName, senderAvatar) }
                if (ensure.isFailure) return Result.failure(ensure.exceptionOrNull() ?: Exception("Failed to prepare cloud conversation"))
                chat
            }
        } else {
            val ensure = runCatching { ensureConversation(chat, senderId, senderName, senderAvatar) }
            if (ensure.isFailure) return Result.failure(ensure.exceptionOrNull() ?: Exception("Failed to prepare cloud conversation"))
            chat
        }
        val result = api.sendMessage(preparedChat.id, text, senderId, senderName, senderAvatar, attachmentId)
        result.getOrNull()?.let { message ->
            upsertCachedMessage(message.chatId, message)
            upsertCachedChat(
                senderId,
                preparedChat.copy(
                    id = message.chatId,
                    lastMessage = messagePreview(message),
                    lastMessageTime = message.timestamp,
                    unreadCount = 0
                )
            )
        }
        return result
    }

    suspend fun uploadAttachment(fileName: String, mimeType: String, bytes: ByteArray): Result<ChatModels.UploadedFile> =
        api.uploadAttachment(fileName, mimeType, bytes)

    suspend fun reactToMessage(messageId: String, emoji: String, userId: String): Result<ChatModels.Message> =
        api.reactToMessage(messageId, emoji, userId)

    fun cachedChats(userId: String): List<ChatModels.Chat> {
        val raw = prefs.getString(chatsCacheKey(userId), null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ChatModels.Chat>>() {}.type
            gson.fromJson<List<ChatModels.Chat>>(raw, type)
        }.getOrDefault(emptyList())
    }

    fun cachedMessages(chatId: String): List<ChatModels.Message> {
        val raw = prefs.getString(cacheKey(chatId), null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ChatModels.Message>>() {}.type
            gson.fromJson<List<ChatModels.Message>>(raw, type)
        }.getOrDefault(emptyList())
    }

    private fun saveMessages(chatId: String, messages: List<ChatModels.Message>) {
        val recent = messages.distinctBy { it.id }.sortedBy { it.timestamp }.takeLast(100)
        prefs.edit().putString(cacheKey(chatId), gson.toJson(recent)).apply()
    }

    private fun saveChats(userId: String, chats: List<ChatModels.Chat>) {
        val recent = dedupeChats(chats, userId).sortedByDescending { it.lastMessageTime ?: 0L }.take(100)
        prefs.edit().putString(chatsCacheKey(userId), gson.toJson(recent)).apply()
    }

    fun upsertCachedChat(userId: String, chat: ChatModels.Chat) {
        val next = dedupeChats(cachedChats(userId) + chat, userId)
            .sortedByDescending { it.lastMessageTime ?: 0L }
            .take(100)
        saveChats(userId, next)
    }

    fun clearCachedUnread(userId: String, chatId: String) {
        saveChats(
            userId,
            cachedChats(userId).map { chat ->
                if (chat.id == chatId) chat.copy(unreadCount = 0) else chat
            }
        )
    }

    private fun upsertCachedMessage(chatId: String, message: ChatModels.Message) {
        val next = (cachedMessages(chatId).filterNot { it.id == message.id } + message)
            .sortedBy { it.timestamp }
            .takeLast(100)
        saveMessages(chatId, next)
    }

    private fun cacheKey(chatId: String): String = "messages_$chatId"

    private fun chatsCacheKey(userId: String): String = "chats_$userId"

    private fun directKey(firstUserId: String, secondUserId: String): String =
        listOf(firstUserId, secondUserId).sorted().joinToString(separator = ":")

    private fun directKeyForChat(chat: ChatModels.Chat, currentUserId: String): String? {
        if (chat.isGroup) return null
        chat.directKey?.takeIf { it.isNotBlank() }?.let { return it }
        val members = chat.members?.takeIf { it.size >= 2 }
            ?: chat.participants?.map { it.id }
        val unique = members.orEmpty().filter { it.isNotBlank() }.distinct().sorted()
        if (unique.size == 2) return unique.joinToString(":")
        val other = chat.participants?.firstOrNull { it.id != currentUserId }?.id
        return other?.let { directKey(currentUserId, it) }
    }

    private fun dedupeChats(chats: List<ChatModels.Chat>, currentUserId: String): List<ChatModels.Chat> {
        val merged = linkedMapOf<String, ChatModels.Chat>()
        chats.sortedByDescending { it.lastMessageTime ?: 0L }.forEach { chat ->
            val key = directKeyForChat(chat, currentUserId) ?: chat.id
            val existing = merged[key]
            if (existing == null || (chat.lastMessageTime ?: 0L) >= (existing.lastMessageTime ?: 0L)) {
                merged[key] = chat.copy(
                    unreadCount = chat.unreadCount ?: existing?.unreadCount ?: 0
                )
            }
        }
        return merged.values.toList()
    }

    private fun cacheDirectConversationId(firstUserId: String, secondUserId: String, conversationId: String) {
        prefs.edit().putString("direct_${directKey(firstUserId, secondUserId)}", conversationId).apply()
    }

    private fun dedupeUsers(users: List<ChatModels.User>): List<ChatModels.User> =
        users
            .filter { it.id.isNotBlank() }
            .fold(linkedMapOf<String, ChatModels.User>()) { acc, user ->
                val existing = acc[user.id]
                acc[user.id] = when {
                    existing == null -> user
                    existing.online != true && user.online == true -> user
                    existing.name.isGeneratedIdentityName() && !user.name.isGeneratedIdentityName() -> user
                    else -> existing
                }
                acc
            }
            .values
            .toList()

    private fun messagePreview(message: ChatModels.Message): String =
        message.text.takeIf { it.isNotBlank() }
            ?: message.attachmentName?.takeIf { it.isNotBlank() }
            ?: if (message.attachmentUrl != null) "Attachment" else ""

    private fun String.isGeneratedIdentityName(): Boolean {
        val lower = trim().lowercase()
        return lower.startsWith("usr_") || lower.startsWith("user_") || lower.startsWith("direct_")
    }
}
