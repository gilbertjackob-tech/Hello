package com.glassbox.hello.chat

import com.google.gson.annotations.SerializedName

object ChatModels {
    data class Chat(
        @SerializedName("id") val id: String,
        @SerializedName("type") val type: String? = null,
        @SerializedName("directKey") val directKey: String? = null,
        @SerializedName("name") val name: String,
        @SerializedName("avatar") val avatar: String? = null,
        @SerializedName("lastMessage") val lastMessage: String? = null,
        @SerializedName("lastMessageTime") val lastMessageTime: Long? = null,
        @SerializedName("unreadCount") val unreadCount: Int? = null,
        @SerializedName("isGroup") val isGroup: Boolean = false,
        @SerializedName("members") val members: List<String>? = null,
        @SerializedName("participants") val participants: List<User>? = null
    )

    data class User(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("avatar") val avatar: String? = null,
        @SerializedName("phone") val phone: String? = null,
        @SerializedName("email") val email: String? = null,
        @SerializedName("online") val online: Boolean? = null,
        @SerializedName("lastActive") val lastActive: Long? = null,
        @SerializedName("privacy") val privacy: String? = null,
        @SerializedName("lastActivePrivacy") val lastActivePrivacy: String? = null
    )

    data class Reaction(
        @SerializedName("emoji") val emoji: String,
        @SerializedName("userId") val userId: String
    )

    data class LocationData(
        @SerializedName("lat") val lat: Double,
        @SerializedName("lng") val lng: Double,
        @SerializedName("isLive") val isLive: Boolean? = null,
        @SerializedName("expiresAt") val expiresAt: Long? = null
    )

    data class ReplyTo(
        @SerializedName("id") val id: String,
        @SerializedName("text") val text: String,
        @SerializedName("senderName") val senderName: String,
        @SerializedName("senderId") val senderId: String? = null
    )

    data class Message(
        @SerializedName("id") val id: String,
        @SerializedName("chatId") val chatId: String,
        @SerializedName("senderId") val senderId: String,
        @SerializedName("senderName") val senderName: String,
        @SerializedName("senderAvatar") val senderAvatar: String? = null,
        @SerializedName("text") val text: String,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("attachmentUrl") val attachmentUrl: String? = null,
        @SerializedName("attachmentType") val attachmentType: String? = null,
        @SerializedName("attachmentName") val attachmentName: String? = null,
        @SerializedName("attachmentSize") val attachmentSize: Long? = null,
        @SerializedName("status") val status: String? = null,
        @SerializedName("isDeleted") val isDeleted: Boolean? = null,
        @SerializedName("deletedFor") val deletedFor: List<String>? = null,
        @SerializedName("reactions") val reactions: List<Reaction>? = null,
        @SerializedName("starredBy") val starredBy: List<String>? = null,
        @SerializedName("pinnedUntil") val pinnedUntil: Long? = null,
        @SerializedName("location") val location: LocationData? = null,
        @SerializedName("replyTo") val replyTo: ReplyTo? = null
    )

    data class UploadedFile(
        @SerializedName("id") val id: String? = null,
        @SerializedName("url") val url: String,
        @SerializedName("mimeType") val mimeType: String,
        @SerializedName("originalName") val originalName: String,
        @SerializedName("size") val size: Long
    )

    data class AttachmentItem(
        @SerializedName("id") val id: String? = null,
        @SerializedName("messageId") val messageId: String? = null,
        @SerializedName("fileName") val fileName: String? = null,
        @SerializedName("mimeType") val mimeType: String? = null,
        @SerializedName("size") val size: Long? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("text") val text: String? = null,
        @SerializedName("senderId") val senderId: String? = null,
        @SerializedName("senderName") val senderName: String? = null,
        @SerializedName("createdAt") val createdAt: Long? = null
    )

    data class ChatAttachments(
        @SerializedName("media") val media: List<AttachmentItem> = emptyList(),
        @SerializedName("files") val files: List<AttachmentItem> = emptyList(),
        @SerializedName("links") val links: List<AttachmentItem> = emptyList()
    )

    data class CallHistoryItem(
        @SerializedName("id") val id: String,
        @SerializedName("roomId") val roomId: String? = null,
        @SerializedName("chatId") val chatId: String,
        @SerializedName("callerId") val callerId: String,
        @SerializedName("calleeId") val calleeId: String,
        @SerializedName("type") val type: String,
        @SerializedName("direction") val direction: String,
        @SerializedName("status") val status: String,
        @SerializedName("startedAt") val startedAt: Long,
        @SerializedName("durationSeconds") val durationSeconds: Long? = null,
        @SerializedName("endReason") val endReason: String? = null,
        @SerializedName("otherUser") val otherUser: User
    )

    data class StatusItem(
        @SerializedName("id") val id: String,
        @SerializedName("userId") val userId: String,
        @SerializedName("text") val text: String? = null,
        @SerializedName("attachmentUrl") val attachmentUrl: String? = null,
        @SerializedName("attachmentType") val attachmentType: String? = null,
        @SerializedName("backgroundColor") val backgroundColor: String? = null,
        @SerializedName("duration") val duration: Long? = null,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("userName") val userName: String? = null,
        @SerializedName("userAvatar") val userAvatar: String? = null,
        @SerializedName("views") val views: List<Map<String, Any>>? = null
    )
}
