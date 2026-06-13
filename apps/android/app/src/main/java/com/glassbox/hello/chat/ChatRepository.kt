package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.network.HelloApi

class ChatRepository(
    private val api: HelloApi,
    private val cloudRepository: CloudChatRepository? = null
) {

    suspend fun fetchUsers(query: String? = null, cloudChatEnabled: Boolean = true): Result<List<User>> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.fetchUsers(query)
        }
        return api.fetchUsers(query)
    }

    suspend fun createDirectChat(
        currentUserId: String,
        targetUserId: String,
        currentUserName: String,
        targetUserName: String? = null,
        cloudChatEnabled: Boolean = true
    ): Result<Chat> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.createDirectChat(currentUserId, targetUserId, currentUserName, targetUserName)
        }
        return api.createDirectChat(currentUserId, targetUserId)
    }

    suspend fun createGroupChat(
        currentUserId: String,
        currentUserName: String,
        name: String,
        members: List<String>,
        cloudChatEnabled: Boolean = true
    ): Result<Chat> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.createGroupChat(currentUserId, currentUserName, name, members)
        }
        return api.createChat(name, isGroup = true, members = members)
    }

    suspend fun fetchChats(userId: String, currentUserName: String? = null, cloudChatEnabled: Boolean = true): Result<List<Chat>> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.fetchChats(userId, currentUserName)
        }
        return api.fetchChats(userId)
    }

    fun cachedChats(userId: String, cloudChatEnabled: Boolean = true): List<Chat> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.cachedChats(userId)
        }
        return emptyList()
    }

    fun cachedMessages(chatId: String, cloudChatEnabled: Boolean = true): List<Message> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.cachedMessages(chatId)
        }
        return emptyList()
    }

    fun clearCachedUnread(userId: String, chatId: String, cloudChatEnabled: Boolean = true) {
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.clearCachedUnread(userId, chatId)
        }
    }

    suspend fun fetchMessages(
        chatId: String,
        limit: Int? = null,
        offset: Int? = null,
        cloudChatEnabled: Boolean = true
    ): Result<List<Message>> {
        if (cloudChatEnabled && cloudRepository != null) {
            return cloudRepository.fetchMessages(chatId, limit, offset)
        }
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
        replyTo: ChatModels.ReplyTo? = null,
        cloudChatEnabled: Boolean = true,
        chat: Chat? = null
    ): Result<Message> {
        if (
            cloudChatEnabled &&
            cloudRepository != null &&
            chat != null &&
            (attachmentUrl == null || attachmentUrl.startsWith("cloud:")) &&
            location == null
        ) {
            return cloudRepository.sendTextMessage(
                chat = chat,
                text = text,
                senderId = senderId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                attachmentId = attachmentUrl?.takeIf { it.startsWith("cloud:") }?.removePrefix("cloud:")
            )
        }
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

    suspend fun uploadCloudAttachment(fileName: String, mimeType: String, bytes: ByteArray): Result<ChatModels.UploadedFile> =
        cloudRepository?.uploadAttachment(fileName, mimeType, bytes)
            ?: Result.failure(IllegalStateException("Cloud chat is not configured"))

    suspend fun reactToMessage(
        chatId: String,
        messageId: String,
        emoji: String,
        userId: String,
        cloudChatEnabled: Boolean = true
    ) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.reactToMessage(messageId, emoji, userId)
        } else {
            api.reactToMessage(chatId, messageId, emoji, userId)
        }

    suspend fun starMessage(chatId: String, messageId: String, userId: String, cloudChatEnabled: Boolean = true) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.starMessage(messageId, userId)
        } else {
            api.starMessage(chatId, messageId, userId)
        }

    suspend fun pinMessage(chatId: String, messageId: String, userId: String, durationDays: Int, cloudChatEnabled: Boolean = true) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.pinMessage(messageId, userId, durationDays)
        } else {
            api.pinMessage(chatId, messageId, durationDays)
        }

    suspend fun deleteMessage(chatId: String, messageId: String, userId: String, type: String, cloudChatEnabled: Boolean = true) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.deleteMessage(messageId, userId, type)
        } else {
            api.deleteMessage(chatId, messageId, userId, type)
        }

    suspend fun clearChat(chatId: String, userId: String, cloudChatEnabled: Boolean = true) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.clearChat(chatId, userId)
        } else {
            api.clearChat(chatId, userId)
        }

    suspend fun deleteChat(chatId: String, userId: String, cloudChatEnabled: Boolean = true) =
        if (cloudChatEnabled && cloudRepository != null) {
            cloudRepository.deleteChat(chatId, userId)
        } else {
            api.deleteChat(chatId, userId)
        }
}
