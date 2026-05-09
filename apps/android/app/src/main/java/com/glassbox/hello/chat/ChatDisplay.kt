package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.User

fun Chat.otherParticipant(currentUserId: String): User? {
    if (isGroup) return null
    return participants?.firstOrNull { it.id != currentUserId }
}

fun Chat.displayName(currentUserId: String): String {
    return otherParticipant(currentUserId)?.name ?: name
}

fun Chat.displayAvatar(currentUserId: String): String? {
    return otherParticipant(currentUserId)?.avatar ?: avatar
}

fun Chat.presenceSubtitle(currentUserId: String): String {
    val other = otherParticipant(currentUserId)
    return when {
        lastMessage?.isNotBlank() == true -> lastMessage
        other?.online == true -> "Online"
        other != null -> "Tap to get started"
        else -> "No messages yet"
    }
}

fun String.avatarInitial(): String {
    return trim().firstOrNull()?.uppercase() ?: "?"
}
