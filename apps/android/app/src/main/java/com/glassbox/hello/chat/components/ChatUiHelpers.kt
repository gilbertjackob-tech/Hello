package com.glassbox.hello.chat.components

import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.UrlResolver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val QuickReactions = listOf(
    "\uD83D\uDC4D",
    "\u2764\uFE0F",
    "\uD83D\uDE02",
    "\uD83D\uDE2E",
    "\uD83D\uDE22",
    "\uD83D\uDE4F"
)

val ComposerEmojis = QuickReactions + listOf(
    "\uD83D\uDC4F",
    "\uD83D\uDD25",
    "\uD83D\uDE0D",
    "\uD83D\uDE4C"
)

fun List<ChatModels.Message>.visibleForUser(currentUserId: String): List<ChatModels.Message> {
    return asSequence()
        .filterNot { it.deletedFor.orEmpty().contains(currentUserId) }
        .sortedBy { it.timestamp }
        .toList()
}

fun buildTimelineGrouping(
    messages: List<ChatModels.Message>,
    index: Int,
    currentUserId: String,
    chatIsGroup: Boolean,
    unreadCount: Int
): TimelineGrouping {
    val message = messages[index]
    val previous = messages.getOrNull(index - 1)
    val next = messages.getOrNull(index + 1)
    val showDayDivider = previous == null || !sameMessageDay(previous.timestamp, message.timestamp)
    val compactWithPrevious = previous != null && shouldGroupTogether(previous, message)
    val compactWithNext = next != null && shouldGroupTogether(message, next)
    val showSenderName = chatIsGroup && message.senderId != currentUserId && !compactWithPrevious
    val unreadStartIndex = unreadBoundaryIndex(messages, currentUserId, unreadCount)
    return TimelineGrouping(
        showDayDivider = showDayDivider,
        dayLabel = if (showDayDivider) formatMessageDate(message.timestamp) else null,
        showUnreadDivider = unreadStartIndex == index,
        showSenderName = showSenderName,
        compactWithPrevious = compactWithPrevious,
        compactWithNext = compactWithNext
    )
}

fun normalizeAttachmentUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return UrlResolver.resolve(url) ?: url.takeIf {
        it.startsWith("content://") || it.startsWith("file://") || it.startsWith("http://") || it.startsWith("https://")
    }
}

fun isUrlOnly(text: String): Boolean {
    val value = text.trim()
    return value.startsWith("http://") || value.startsWith("https://")
}

fun attachmentTypeLabel(message: ChatModels.Message): String {
    val mime = message.attachmentType.orEmpty()
    return when {
        message.attachmentName?.lowercase()?.endsWith(".pdf") == true -> "PDF"
        mime.startsWith("image/") || mime == "image" -> "Image"
        mime.startsWith("video/") || mime == "video" -> "Video"
        mime.startsWith("audio/") || mime == "audio" -> "Audio"
        mime.contains("word", ignoreCase = true) -> "DOC"
        mime.contains("sheet", ignoreCase = true) || mime.contains("excel", ignoreCase = true) -> "XLS"
        mime.contains("presentation", ignoreCase = true) || mime.contains("powerpoint", ignoreCase = true) -> "PPT"
        message.attachmentName?.substringAfterLast('.', "")?.isNotBlank() == true ->
            message.attachmentName.substringAfterLast('.').uppercase(Locale.getDefault())
        else -> "FILE"
    }
}

fun attachmentKind(message: ChatModels.Message): String? {
    val mime = message.attachmentType.orEmpty().trim().lowercase(Locale.getDefault())
    val extension = message.attachmentName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase(Locale.getDefault())
        .orEmpty()
    return when {
        mime.startsWith("image/") || mime == "image" || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg") -> "image"
        mime.startsWith("video/") || mime == "video" || extension in setOf("mp4", "mov", "mkv", "webm", "avi", "m4v", "3gp") -> "video"
        mime.startsWith("audio/") || mime == "audio" || extension in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus") -> "audio"
        message.attachmentUrl.isNullOrBlank() && message.attachmentName.isNullOrBlank() -> null
        else -> "file"
    }
}

fun messagePreviewText(message: ChatModels.Message): String {
    return when {
        message.callInfo != null -> callSummaryLabel(message.callInfo)
        isStickerMessage(message.text) -> "Sticker"
        message.isDeleted == true -> "Deleted message"
        message.location != null -> message.text.ifBlank { "Location" }
        message.text.isNotBlank() -> message.text
        !message.attachmentName.isNullOrBlank() -> message.attachmentName
        attachmentKind(message) != null -> attachmentTypeLabel(message)
        else -> "Message"
    }
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "$minutes:${remaining.toString().padStart(2, '0')}"
}

fun callSummaryLabel(callInfo: ChatModels.CallInfo?): String {
    if (callInfo == null) return "Call"
    val callType = if (callInfo.callType.equals("video", ignoreCase = true)) "Video" else "Audio"
    val duration = callInfo.durationSeconds ?: 0L
    return when (callInfo.status?.lowercase()) {
        "missed" -> "Missed ${callType.lowercase()} call"
        "declined" -> "$callType call declined"
        "busy" -> "$callType call busy"
        "unavailable" -> "$callType call unavailable"
        "failed" -> "$callType call failed"
        "cancelled" -> "$callType call cancelled"
        else -> if (duration > 0L) "$callType call • ${formatDuration(duration)}" else "$callType call"
    }
}

fun formatMessageDate(timestamp: Long): String {
    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameMessageDay(today.timeInMillis, timestamp) -> "Today"
        sameMessageDay(yesterday.timeInMillis, timestamp) -> "Yesterday"
        today.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

fun sameMessageDay(first: Long, second: Long): Boolean {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = first
    val firstYear = calendar.get(Calendar.YEAR)
    val firstDay = calendar.get(Calendar.DAY_OF_YEAR)
    calendar.timeInMillis = second
    return firstYear == calendar.get(Calendar.YEAR) && firstDay == calendar.get(Calendar.DAY_OF_YEAR)
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff in 0 until 60_000 -> "now"
        diff in 60_000 until 3_600_000 -> "${diff / 60_000}m"
        diff in 3_600_000 until 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(Locale.US, mb) else "%.0f KB".format(Locale.US, kb.coerceAtLeast(1.0))
}

private fun shouldGroupTogether(previous: ChatModels.Message, current: ChatModels.Message): Boolean {
    return previous.senderId == current.senderId &&
        sameMessageDay(previous.timestamp, current.timestamp) &&
        current.timestamp - previous.timestamp < 5 * 60 * 1000
}

private fun unreadBoundaryIndex(messages: List<ChatModels.Message>, currentUserId: String, unreadCount: Int): Int? {
    if (unreadCount <= 0 || messages.isEmpty()) return null
    val candidates = messages.withIndex().filter { (_, message) -> message.senderId != currentUserId }
    if (candidates.isEmpty()) return null
    val position = (candidates.size - unreadCount).coerceAtLeast(0)
    return candidates.getOrNull(position)?.index
}
