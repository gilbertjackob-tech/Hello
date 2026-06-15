package com.glassbox.hello.chat

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.chat.components.messagePreviewText
import com.glassbox.hello.chat.components.callSummaryLabel
import com.google.gson.Gson
import com.google.gson.JsonParser

class CloudChatRepository(
    context: Context,
    private val api: CloudChatApi = CloudChatApi(context.applicationContext)
) {
    companion object {
        private const val TAG = "HelloInbox"
    }

    private val sessionManager = CloudSessionManager(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("hello_cloud_chat_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun ensureConversation(chat: ChatModels.Chat, currentUserId: String, currentUserName: String, currentUserAvatar: String?) {
        api.upsertUser(currentUserId, currentUserName, currentUserAvatar)
        val members = chat.memberIds().ifEmpty { listOf(currentUserId) }
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

    suspend fun fetchChats(userId: String, displayName: String? = null): Result<List<ChatModels.Chat>> {
        val resolvedUserId = resolveCloudUserId(userId, displayName)
        val sessionUserId = currentCloudUserId()
        val effectiveUserId = resolvedUserId.takeIf { it.startsWith("usr_") }
            ?: sessionUserId
            ?: resolvedUserId
        Log.d(
            TAG,
            "repo_fetch_chats_start requestedUserId=$userId displayName=${displayName.orEmpty()} resolvedUserId=$resolvedUserId effectiveUserId=$effectiveUserId tokenPresent=${!sessionManager.token().isNullOrBlank()} cachedUserId=${sessionManager.cachedUser()?.id.orEmpty()}"
        )
        val result = api.fetchConversations(effectiveUserId)
        val remoteChats = result.getOrNull()?.map(::normalizeChat)
        val deduped = remoteChats
            ?.let { dedupeChats(it, effectiveUserId) }
        deduped?.let {
            saveChats(userId, it)
            if (resolvedUserId != userId) saveChats(resolvedUserId, it)
            if (effectiveUserId != userId) saveChats(effectiveUserId, it)
        }
        if (deduped != null) {
            Log.d(
                TAG,
                "repo_fetch_chats_success requestedUserId=$userId resolvedUserId=$resolvedUserId effectiveUserId=$effectiveUserId count=${deduped.size} ids=${deduped.take(8).joinToString(",") { chat -> chat.id }}"
            )
        } else {
            Log.w(
                TAG,
                "repo_fetch_chats_error requestedUserId=$userId resolvedUserId=$resolvedUserId effectiveUserId=$effectiveUserId error=${result.exceptionOrNull()?.message ?: "unknown"}"
            )
        }
        return if (deduped != null) Result.success(deduped) else {
            val cached = (cachedChats(userId) + cachedChats(resolvedUserId) + cachedChats(effectiveUserId))
                .let { dedupeChats(it, effectiveUserId) }
            Log.d(
                TAG,
                "repo_fetch_chats_fallback_cache requestedUserId=$userId resolvedUserId=$resolvedUserId effectiveUserId=$effectiveUserId cachedCount=${cached.size}"
            )
            if (cached.isNotEmpty()) Result.success(cached) else result
        }
    }

    suspend fun fetchUsers(query: String? = null): Result<List<ChatModels.User>> {
        Log.d(
            TAG,
            "repo_fetch_users_start query=${query.orEmpty()} tokenPresent=${!sessionManager.token().isNullOrBlank()} cachedUserId=${sessionManager.cachedUser()?.id.orEmpty()}"
        )
        val usersResult = api.fetchUsers(query)
        val contacts = sessionManager.token()
            ?.let { token -> api.fetchContacts(token).getOrNull() }
            .orEmpty()
            .filter { contact ->
                query.isNullOrBlank() ||
                    contact.safeName().contains(query, ignoreCase = true) ||
                    contact.safeId().contains(query, ignoreCase = true) ||
                    contact.phone?.contains(query, ignoreCase = true) == true ||
                    contact.email?.contains(query, ignoreCase = true) == true
            }
        val users = usersResult.getOrNull().orEmpty()
        val merged = dedupeUsers(contacts + users)
        Log.d(
            TAG,
            "repo_fetch_users_result query=${query.orEmpty()} usersCount=${users.size} contactsCount=${contacts.size} mergedCount=${merged.size} ids=${merged.take(8).joinToString(",") { user -> user.id }}"
        )
        if (usersResult.isFailure) {
            Log.w(
                TAG,
                "repo_fetch_users_users_error query=${query.orEmpty()} error=${usersResult.exceptionOrNull()?.message ?: "unknown"}"
            )
        }
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
        val effectiveCurrentUserId = resolveCloudUserId(currentUserId, currentUserName)
        val effectiveTargetUserId = resolveCloudUserId(targetUserId, targetUserName)
        val result = api.ensureDirectConversation(
            currentUserId = effectiveCurrentUserId,
            targetUserId = effectiveTargetUserId,
            currentUserName = currentUserName,
            targetUserName = targetUserName
        )
        result.getOrNull()?.let {
            cacheDirectConversationId(effectiveCurrentUserId, effectiveTargetUserId, it.id)
            upsertCachedChat(currentUserId, it)
            if (effectiveCurrentUserId != currentUserId) upsertCachedChat(effectiveCurrentUserId, it)
        }
        return result
    }

    suspend fun createGroupChat(currentUserId: String, currentUserName: String, name: String, members: List<String>): Result<ChatModels.Chat> {
        val effectiveCurrentUserId = resolveCloudUserId(currentUserId, currentUserName)
        val effectiveMembers = members.map { memberId -> resolveCloudUserId(memberId) }.distinct()
        val conversationId = "group_${System.currentTimeMillis()}_${name.hashCode()}"
        val result = api.ensureConversation(
            conversationId = conversationId,
            title = name,
            isGroup = true,
            createdBy = effectiveCurrentUserId,
            createdByName = currentUserName,
            memberIds = effectiveMembers
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
        val effectiveSenderId = resolveCloudUserId(senderId, senderName)
        val preparedChat = if (!chat.isGroup) {
            val targetUserId = chat.otherParticipant(effectiveSenderId)?.id
                ?: chat.memberIds().firstOrNull { it != effectiveSenderId }
            if (targetUserId != null) {
                createDirectChat(
                    effectiveSenderId,
                    targetUserId,
                    senderName,
                    chat.otherParticipant(effectiveSenderId)?.name
                ).getOrElse { error ->
                    return Result.failure(error)
                }
            } else {
                val ensure = runCatching { ensureConversation(chat, effectiveSenderId, senderName, senderAvatar) }
                if (ensure.isFailure) return Result.failure(ensure.exceptionOrNull() ?: Exception("Failed to prepare cloud conversation"))
                chat
            }
        } else {
            val ensure = runCatching { ensureConversation(chat, effectiveSenderId, senderName, senderAvatar) }
            if (ensure.isFailure) return Result.failure(ensure.exceptionOrNull() ?: Exception("Failed to prepare cloud conversation"))
            chat
        }
        val result = api.sendMessage(preparedChat.id, text, effectiveSenderId, senderName, senderAvatar, attachmentId)
        result.getOrNull()?.let { message ->
            upsertCachedMessage(message.chatId, message)
            val updatedChat = preparedChat.copy(
                id = message.chatId,
                lastMessage = messagePreview(message),
                lastMessageTime = message.timestamp,
                unreadCount = 0
            )
            upsertCachedChat(effectiveSenderId, updatedChat)
            if (effectiveSenderId != senderId) upsertCachedChat(senderId, updatedChat)
        }
        return result
    }

    suspend fun uploadAttachment(fileName: String, mimeType: String, bytes: ByteArray): Result<ChatModels.UploadedFile> =
        api.uploadAttachment(fileName, mimeType, bytes)

    suspend fun reactToMessage(messageId: String, emoji: String, userId: String): Result<ChatModels.Message> =
        api.reactToMessage(messageId, emoji, userId)

    suspend fun starMessage(messageId: String, userId: String): Result<ChatModels.Message> =
        api.starMessage(messageId, userId)

    suspend fun pinMessage(messageId: String, userId: String, durationDays: Int): Result<ChatModels.Message> =
        api.pinMessage(messageId, userId, durationDays)

    suspend fun deleteMessage(messageId: String, userId: String, type: String): Result<ChatModels.Message> =
        api.deleteMessage(messageId, userId, type)

    suspend fun clearChat(chatId: String, userId: String): Result<Unit> =
        api.clearConversation(chatId, userId)

    suspend fun deleteChat(chatId: String, userId: String): Result<Unit> =
        api.deleteConversation(chatId, userId)

    fun cachedChats(userId: String): List<ChatModels.Chat> {
        val raw = prefs.getString(chatsCacheKey(userId), null) ?: return emptyList()
        return runCatching {
            JsonParser.parseString(raw)
                .takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.let { jsonElement ->
                        CloudChatPayloadParser.parseChat(jsonElement.toString())
                    }
                }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun cachedMessages(chatId: String): List<ChatModels.Message> {
        val raw = prefs.getString(cacheKey(chatId), null) ?: return emptyList()
        return runCatching {
            JsonParser.parseString(raw)
                .takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.let { jsonElement ->
                        CloudChatPayloadParser.parseMessage(jsonElement.toString())
                    }
                }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun saveMessages(chatId: String, messages: List<ChatModels.Message>) {
        val recent = messages
            .map(::normalizeMessage)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
            .takeLast(30)
        prefs.edit().putString(cacheKey(chatId), gson.toJson(recent)).apply()
    }

    private fun saveChats(userId: String, chats: List<ChatModels.Chat>) {
        val recent = dedupeChats(chats.map(::normalizeChat), userId)
            .sortedByDescending { it.lastMessageTime ?: 0L }
            .take(100)
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
        val unique = chat.memberIds().distinct().sorted()
        if (unique.size == 2) return unique.joinToString(":")
        val other = chat.otherParticipant(currentUserId)?.id
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

    private fun normalizeChat(chat: ChatModels.Chat): ChatModels.Chat =
        CloudChatPayloadParser.parseChat(gson.toJson(chat)) ?: chat

    private fun normalizeMessage(message: ChatModels.Message): ChatModels.Message =
        CloudChatPayloadParser.parseMessage(gson.toJson(message)) ?: message

    private fun dedupeUsers(users: List<ChatModels.User>): List<ChatModels.User> =
        users
            .filter { it.safeId().isNotBlank() }
            .fold(linkedMapOf<String, ChatModels.User>()) { acc, user ->
                val userId = user.safeId()
                val existing = acc[userId]
                acc[userId] = when {
                    existing == null -> user
                    existing.online != true && user.online == true -> user
                    existing.safeName().isGeneratedIdentityName() && !user.safeName().isGeneratedIdentityName() -> user
                    else -> existing
                }
                acc
            }
            .values
            .toList()

    private suspend fun resolveCloudUserId(rawUserId: String, displayName: String? = null): String {
        val raw = rawString(rawUserId)
        if (raw.startsWith("usr_")) return raw
        val searchTerms = listOf(raw, rawString(displayName)).filter { it.isNotBlank() }.distinct()
        for (term in searchTerms) {
            val users = api.fetchUsers(term).getOrNull().orEmpty()
            val match = users.firstOrNull { user ->
                user.safeId().equals(term, ignoreCase = true) ||
                    user.safeName().equals(term, ignoreCase = true) ||
                    rawString(user.username).equals(term, ignoreCase = true)
            } ?: users.firstOrNull()
            val id = match?.safeId().orEmpty()
            if (id.isNotBlank()) {
                Log.d(
                    TAG,
                    "resolve_cloud_user_id_match rawUserId=$rawUserId displayName=${displayName.orEmpty()} term=$term resolvedId=$id candidateCount=${users.size}"
                )
                return id
            }
        }
        Log.w(
            TAG,
            "resolve_cloud_user_id_fallback rawUserId=$rawUserId displayName=${displayName.orEmpty()} searchTerms=${searchTerms.joinToString(",")}"
        )
        return raw
    }

    private fun currentCloudUserId(): String? {
        if (sessionManager.token().isNullOrBlank()) return null
        return rawString(sessionManager.cachedUser()?.id)
            .takeIf { it.startsWith("usr_") }
    }

    private fun messagePreview(message: ChatModels.Message): String =
        messagePreviewText(message)

    private fun String.isGeneratedIdentityName(): Boolean {
        val lower = trim().lowercase()
        return lower.startsWith("usr_") || lower.startsWith("user_") || lower.startsWith("direct_")
    }

    private fun ChatModels.User.safeId(): String = rawString(id)

    private fun ChatModels.User.safeName(): String = rawString(name).ifBlank { safeId() }

    private fun rawString(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return when {
            normalized.equals("null", ignoreCase = true) -> ""
            normalized.equals("undefined", ignoreCase = true) -> ""
            else -> normalized
        }
    }
}
