package com.glassbox.hello.network

import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.calls.CallIceServer
import com.glassbox.hello.calls.CallRoom
import com.glassbox.hello.core.User

interface HelloApi {
    suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User>
    suspend fun getUserQuestion(name: String): Result<String>
    suspend fun login(name: String, securityAnswer: String): Result<User>
    suspend fun fetchUser(userId: String): Result<ChatModels.User>
    suspend fun updateUserPrivacy(userId: String, lastActivePrivacy: String): Result<Unit>
    suspend fun fetchUsers(query: String? = null): Result<List<ChatModels.User>>
    suspend fun createDirectChat(currentUserId: String, targetUserId: String): Result<ChatModels.Chat>
    suspend fun createChat(name: String, isGroup: Boolean, members: List<String>): Result<ChatModels.Chat>
    suspend fun fetchChats(userId: String): Result<List<ChatModels.Chat>>
    
    /**
     * Fetch messages with optional pagination support.
     * @param chatId Chat ID to fetch messages for
     * @param limit Maximum number of messages to fetch (default: 50, load all if null)
     * @param offset Number of messages to skip from the beginning (for pagination)
     */
    suspend fun fetchMessages(chatId: String, limit: Int? = null, offset: Int? = null): Result<List<ChatModels.Message>>
    suspend fun fetchChatAttachments(chatId: String): Result<ChatModels.ChatAttachments>
    suspend fun fetchCalls(userId: String): Result<List<ChatModels.CallHistoryItem>>
    suspend fun fetchCallIceServers(): Result<List<CallIceServer>>
    suspend fun createCall(
        callerId: String,
        calleeId: String,
        chatId: String,
        type: String,
        status: String,
        startedAt: Long
    ): Result<String>
    suspend fun createCallRoom(chatId: String, hostId: String, type: String, participantIds: List<String>): Result<CallRoom>
    suspend fun fetchCallRoom(roomId: String): Result<CallRoom>
    suspend fun joinCallRoom(roomId: String, userId: String): Result<CallRoom>
    suspend fun leaveCallRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom>
    suspend fun fetchStatuses(userId: String): Result<List<ChatModels.StatusItem>>
    suspend fun createStatus(
        userId: String,
        text: String,
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        backgroundColor: String = "#0b141a",
        duration: Long = 5000
    ): Result<ChatModels.StatusItem>
    suspend fun markStatusViewed(statusId: String, userId: String): Result<Unit>
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String, userId: String): Result<ChatModels.Message>
    suspend fun starMessage(chatId: String, messageId: String, userId: String): Result<ChatModels.Message>
    suspend fun pinMessage(chatId: String, messageId: String, durationDays: Int): Result<ChatModels.Message>
    suspend fun deleteMessage(chatId: String, messageId: String, userId: String, type: String): Result<ChatModels.Message>
    suspend fun clearChat(chatId: String, userId: String): Result<Unit>
    suspend fun deleteChat(chatId: String, userId: String): Result<Unit>
    suspend fun uploadFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<ChatModels.UploadedFile>
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
    ): Result<ChatModels.Message>
}
