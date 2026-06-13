package com.glassbox.hello.chat.components

import android.net.Uri
import com.glassbox.hello.chat.ChatModels

data class AttachmentDraft(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    val previewUrl: String get() = uri.toString()
}

data class MediaViewerState(
    val url: String,
    val label: String
)

data class TimelineGrouping(
    val showDayDivider: Boolean,
    val dayLabel: String?,
    val showUnreadDivider: Boolean,
    val showSenderName: Boolean,
    val compactWithPrevious: Boolean,
    val compactWithNext: Boolean
)

data class ImageClusterTimeline(
    val clustersByLeadIndex: Map<Int, List<ChatModels.Message>>,
    val followerToLeadIndex: Map<Int, Int>
)

data class ActionMessageState(
    val message: ChatModels.Message,
    val isOwn: Boolean
)
