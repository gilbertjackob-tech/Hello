package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.User

fun Chat.otherParticipant(currentUserId: String): User? {
    if (isGroup) return null
    val rawParticipants = participants ?: return null
    for (participant in rawParticipants) {
        val participantId = participant?.id.rawString()
        if (participant != null && participantId.isNotBlank() && participantId != currentUserId) return participant
    }
    return null
}

fun Chat.displayName(currentUserId: String): String {
    val other = otherParticipant(currentUserId)
    val otherName = other?.name.rawString()
    val chatName = name.rawString()
    return when {
        otherName.isNotBlank() -> otherName
        isGroup && chatName.isNotBlank() -> chatName
        !chatName.isGeneratedDisplayName() -> chatName
        else -> "Cloud chat"
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
    val unique = uniqueMemberIds().sorted()
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
                acc[key] = chat.copy(unreadCount = chat.unreadCount ?: existing?.unreadCount ?: 0)
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
    val safeName = name.rawString()
    if (safeName.isNotBlank() && !safeName.isGeneratedDisplayName()) {
        return false
    }
    return safeName.isGeneratedDisplayName() && id.rawString().isGeneratedDisplayName()
}

fun Chat.isProfessionalInboxItem(currentUserId: String): Boolean {
    if (lastMessage?.isNotBlank() == true) return true
    val chatName = name.rawString()
    if (isGroup) return chatName.isNotBlank() && !chatName.isGeneratedDisplayName()
    val other = otherParticipant(currentUserId)
    if (other != null) return true
    if (uniqueMemberIds().size >= 2) return true
    val title = chatName.ifBlank { id.rawString() }
    return !title.isGeneratedDisplayName()
}

fun Chat.participantCount(): Int {
    val memberCount = uniqueMemberIds().size
    if (memberCount > 0) return memberCount
    var participantCount = 0
    val rawParticipants = participants ?: return 0
    for (participant in rawParticipants) {
        if (participant != null) participantCount += 1
    }
    return participantCount
}

fun Chat.memberIds(): List<String> {
    val ids = mutableListOf<String>()
    val rawMembers = members
    if (rawMembers != null) {
        for (member in rawMembers) {
            if (member != null && member.isNotBlank()) ids += member
        }
    }
    if (ids.isNotEmpty()) return ids

    val rawParticipants = participants ?: return emptyList()
    for (participant in rawParticipants) {
        val participantId = participant?.id
        if (!participantId.isNullOrBlank()) ids += participantId
    }
    return ids
}

private fun Chat.uniqueMemberIds(): List<String> {
    val seen = linkedSetOf<String>()
    for (memberId in memberIds()) {
        if (memberId.isNotBlank()) seen += memberId
    }
    return seen.toList()
}

private fun String?.isGeneratedDisplayName(): Boolean {
    val value = this.rawString()
    if (value.isBlank()) return true
    val lower = value.lowercase()
    return lower.startsWith("usr_") ||
        lower.startsWith("user_") ||
        lower.startsWith("direct_") ||
        lower.startsWith("codex ") ||
        lower.matches(Regex("""codex.*\d{8,}.*"""))
}

private fun String?.rawString(): String = this?.trim().orEmpty()
