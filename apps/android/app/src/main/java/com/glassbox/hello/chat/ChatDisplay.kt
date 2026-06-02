package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.User

fun Chat.otherParticipant(currentUserId: String): User? {
    if (isGroup) return null
    return participants?.firstOrNull { it.id != currentUserId }
}

fun Chat.displayName(currentUserId: String): String {
    val other = otherParticipant(currentUserId)
    return when {
        other != null && !other.isGeneratedIdentity() -> other.name
        isGroup && name.isNotBlank() -> name
        !name.isGeneratedDisplayName() -> name
        else -> "Contact"
    }
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

fun Chat.directDedupeKey(currentUserId: String): String {
    if (isGroup) return id
    directKey?.takeIf { it.isNotBlank() }?.let { return it }
    val memberIds = members?.takeIf { it.size >= 2 }
        ?: participants?.map { it.id }
    val unique = memberIds.orEmpty().filter { it.isNotBlank() }.distinct().sorted()
    if (unique.size == 2) return unique.joinToString(":")
    return otherParticipant(currentUserId)?.id
        ?.let { listOf(currentUserId, it).sorted().joinToString(":") }
        ?: id
}

fun List<Chat>.dedupeDirectChats(currentUserId: String): List<Chat> {
    return sortedByDescending { it.lastMessageTime ?: 0L }
        .fold(linkedMapOf<String, Chat>()) { acc, chat ->
            val key = chat.directDedupeKey(currentUserId)
            val existing = acc[key]
            if (existing == null || (chat.lastMessageTime ?: 0L) >= (existing.lastMessageTime ?: 0L)) {
                acc[key] = chat.copy(unreadCount = maxOf(existing?.unreadCount ?: 0, chat.unreadCount ?: 0))
            }
            acc
        }
        .values
        .toList()
}

fun String.avatarInitial(): String {
    return trim().firstOrNull()?.uppercase() ?: "?"
}

fun User.isGeneratedIdentity(): Boolean {
    return name.isGeneratedDisplayName() || id.isGeneratedDisplayName()
}

fun Chat.isProfessionalInboxItem(currentUserId: String): Boolean {
    if (isGroup) return name.isNotBlank() && !name.isGeneratedDisplayName()
    val other = otherParticipant(currentUserId)
    if (other != null) return !other.isGeneratedIdentity()
    val title = name.ifBlank { id }
    return !title.isGeneratedDisplayName()
}

private fun String?.isGeneratedDisplayName(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return true
    val lower = value.lowercase()
    return lower.startsWith("usr_") ||
        lower.startsWith("user_") ||
        lower.startsWith("direct_") ||
        lower.startsWith("codex ") ||
        lower.matches(Regex("""codex.*\d{8,}.*"""))
}
