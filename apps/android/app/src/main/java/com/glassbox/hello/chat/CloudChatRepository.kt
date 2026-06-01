package com.glassbox.hello.chat

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CloudChatRepository(
    context: Context,
    private val api: CloudChatApi = CloudChatApi()
) {
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

    suspend fun sendTextMessage(
        chat: ChatModels.Chat,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?
    ): Result<ChatModels.Message> {
        val ensure = runCatching { ensureConversation(chat, senderId, senderName, senderAvatar) }
        if (ensure.isFailure) return Result.failure(ensure.exceptionOrNull() ?: Exception("Failed to prepare cloud conversation"))
        val result = api.sendMessage(chat.id, text, senderId, senderName, senderAvatar)
        result.getOrNull()?.let { upsertCachedMessage(chat.id, it) }
        return result
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

    private fun upsertCachedMessage(chatId: String, message: ChatModels.Message) {
        val next = (cachedMessages(chatId).filterNot { it.id == message.id } + message)
            .sortedBy { it.timestamp }
            .takeLast(100)
        saveMessages(chatId, next)
    }

    private fun cacheKey(chatId: String): String = "messages_$chatId"
}
