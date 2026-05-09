package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.network.HelloApi

class ChatRepository(private val api: HelloApi) {

    suspend fun fetchUsers(query: String? = null): Result<List<User>> {
        return api.fetchUsers(query)
    }

    suspend fun createDirectChat(currentUserId: String, targetUserId: String): Result<Chat> {
        return api.createDirectChat(currentUserId, targetUserId)
    }

    suspend fun createGroupChat(name: String, members: List<String>): Result<Chat> {
        return api.createChat(name, isGroup = true, members = members)
    }

    suspend fun fetchChats(userId: String): Result<List<Chat>> {
        return api.fetchChats(userId)
    }

    suspend fun fetchMessages(chatId: String, limit: Int? = null, offset: Int? = null): Result<List<Message>> {
        return api.fetchMessages(chatId, limit, offset)
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String? = null,
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long? = null,
        location: ChatModels.LocationData? = null,
        replyTo: ChatModels.ReplyTo? = null
    ): Result<Message> {
        return api.sendMessage(
            chatId = chatId,
            text = text,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            attachmentUrl = attachmentUrl,
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize,
            location = location,
            replyTo = replyTo
        )
    }

    suspend fun uploadFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String) =
        api.uploadFile(fileName, mimeType, bytes, uploaderId)

    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String, userId: String) =
        api.reactToMessage(chatId, messageId, emoji, userId)

    suspend fun starMessage(chatId: String, messageId: String, userId: String) =
        api.starMessage(chatId, messageId, userId)

    suspend fun pinMessage(chatId: String, messageId: String, durationDays: Int) =
        api.pinMessage(chatId, messageId, durationDays)

    suspend fun deleteMessage(chatId: String, messageId: String, userId: String, type: String) =
        api.deleteMessage(chatId, messageId, userId, type)

    suspend fun clearChat(chatId: String, userId: String) = api.clearChat(chatId, userId)

    suspend fun deleteChat(chatId: String, userId: String) = api.deleteChat(chatId, userId)
}
