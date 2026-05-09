package com.glassbox.hello.chat

import java.util.UUID

/**
 * Optimistic messaging state manager for Android
 * Handles temporary message IDs during send, replacement on success, and failure states
 */
object OptimisticMessageManager {
    /**
     * Generate a unique temporary message ID
     */
    fun generateTempId(): String = "temp-${UUID.randomUUID()}"

    /**
     * Represents a message in optimistic state
     */
    data class OptimisticMessage(
        val tempId: String,
        val message: ChatModels.Message,
        val isOptimistic: Boolean = true,
        val status: String = "sending" // sending, sent, failed
    )

    /**
     * Create an optimistic message from current input
     */
    fun createOptimisticMessage(
        chatId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long? = null,
        replyTo: ChatModels.ReplyTo? = null
    ): OptimisticMessage {
        val tempId = generateTempId()
        val now = System.currentTimeMillis()
        val message = ChatModels.Message(
            id = tempId,
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            text = text,
            timestamp = now,
            attachmentUrl = attachmentUrl,
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize,
            status = "sending",
            replyTo = replyTo
        )
        return OptimisticMessage(tempId = tempId, message = message, isOptimistic = true, status = "sending")
    }

    /**
     * Mark optimistic message as sent (update status, keep tempId until server confirms)
     */
    fun markAsSent(optimistic: OptimisticMessage): OptimisticMessage {
        return optimistic.copy(
            message = optimistic.message.copy(status = "sent"),
            status = "sent"
        )
    }

    /**
     * Mark optimistic message as failed (for retry UI)
     */
    fun markAsFailed(optimistic: OptimisticMessage): OptimisticMessage {
        return optimistic.copy(
            message = optimistic.message.copy(status = "failed"),
            status = "failed"
        )
    }

    /**
     * Replace optimistic message with real server message
     */
    fun replaceWithServerMessage(
        optimistic: OptimisticMessage,
        realMessage: ChatModels.Message
    ): ChatModels.Message {
        return realMessage.copy(
            id = if (realMessage.id.isEmpty()) optimistic.message.id else realMessage.id
        )
    }

    /**
     * Check if a message ID is temporary
     */
    fun isTempId(id: String): Boolean = id.startsWith("temp-")
}
