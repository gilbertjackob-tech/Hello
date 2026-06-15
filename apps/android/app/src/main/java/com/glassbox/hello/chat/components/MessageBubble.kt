package com.glassbox.hello.chat.components
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.OptimisticMessageManager
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.utils.AnimationUtils

@Composable
fun ChatMessageBubble(
    message: ChatModels.Message,
    isOwn: Boolean,
    currentUserId: String,
    onLongPress: (ChatModels.Message) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit,
    bubbleOpacity: Float,
    imageCluster: List<ChatModels.Message>? = null,
    timestampLabel: String,
    reactionSummary: ReactionSummary? = null,
    lightweightMedia: Boolean = false,
    scrollInProgress: Boolean = false,
    modifier: Modifier = Modifier,
    showSenderName: Boolean = false,
    compactWithPrevious: Boolean = false,
    compactWithNext: Boolean = false
) {
    val context = LocalContext.current
    val statusColor = when (message.status) {
        "read" -> HelloColors.ReadReceipt
        "delivered" -> HelloColors.DarkTextMuted
        "failed" -> HelloColors.DarkDanger
        else -> HelloColors.DarkTextMuted
    }
    val themeBubbleAlpha = bubbleOpacity.coerceIn(0.40f, 1f)
    val bubbleColor = when {
        isStickerMessage(message.text) -> Color.Transparent
        message.isDeleted == true -> HelloColors.PanelMuted
        isOwn -> HelloColors.BubbleOut.copy(alpha = themeBubbleAlpha)
        else -> HelloColors.BubbleIn.copy(alpha = themeBubbleAlpha)
    }
    val contentColor = if (isOwn) HelloColors.BubbleOutText else HelloColors.BubbleInText
    val bubbleBorder = when {
        isOwn -> HelloColors.BubbleOutBorder
        else -> HelloColors.BubbleInBorder
    }
    val isImageCluster = !imageCluster.isNullOrEmpty()
    val shellColor = if (isImageCluster) Color.Transparent else bubbleColor
    val shellBorder = if (isImageCluster) Color.Transparent else bubbleBorder
    val bubbleShape = messageBubbleShape(isOwn, compactWithPrevious, compactWithNext)
    val bubbleShadow = when {
        isStickerMessage(message.text) -> 0.dp
        compactWithPrevious || compactWithNext -> 0.dp
        else -> 0.dp
    }
    val interactionModifier = if (scrollInProgress) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                AnimationUtils.Haptics.tapMedium(context)
                onLongPress(message)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (compactWithPrevious) 2.dp else 8.dp,
                bottom = if (compactWithNext) 2.dp else 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp),
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
            ) {
                if (showSenderName && !isOwn) {
                    Text(
                        text = message.senderName,
                        color = HelloColors.DarkAccentStrong,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 14.dp, bottom = 3.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .then(if (bubbleShadow > 0.dp) Modifier.shadow(bubbleShadow, bubbleShape, ambientColor = HelloColors.Accent.copy(alpha = 0.12f)) else Modifier)
                        .clip(bubbleShape)
                        .background(shellColor)
                        .border(1.2.dp, shellBorder, bubbleShape)
                        .then(interactionModifier)
                        .padding(
                            horizontal = if (isImageCluster) 0.dp else 12.dp,
                            vertical = if (isImageCluster) 0.dp else 10.dp
                        )
                ) {
                    Column {
                        if (!scrollInProgress) {
                            MessageBadges(message = message, currentUserId = currentUserId)
                            ReplyPreview(message = message, isOwn = isOwn)
                        }
                        MessageBody(
                            message = message,
                            isOwn = isOwn,
                            textColor = contentColor,
                            bubbleColor = bubbleColor,
                            bubbleBorder = bubbleBorder,
                            timestampLabel = timestampLabel,
                            onOpenAttachment = onOpenAttachment,
                            onOpenImage = onOpenImage,
                            onDownloadAttachment = onDownloadAttachment,
                            imageCluster = imageCluster,
                            lightweightMedia = lightweightMedia
                        )
                        MessageMeta(
                            message = message,
                            isOwn = isOwn,
                            statusColor = statusColor,
                            compact = message.text.length <= 32 && message.attachmentUrl.isNullOrBlank(),
                            timestampLabel = timestampLabel
                        )
                    }
                }
                if (!scrollInProgress) {
                    ReactionStrip(reactionSummary)
                }
            }
        }
    }
}

@Composable
private fun MessageBadges(message: ChatModels.Message, currentUserId: String) {
    val badges = buildList {
        if ((message.pinnedUntil ?: 0L) > System.currentTimeMillis()) add("Pinned")
        if (message.starredBy.orEmpty().contains(currentUserId)) add("Starred")
        if (OptimisticMessageManager.isTempId(message.id)) add("Sending")
    }
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 5.dp)) {
        badges.forEach { badge ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                if (badge == "Pinned") {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = HelloColors.DarkAccentStrong, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                } else if (badge == "Starred") {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(badge, color = HelloColors.DarkText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReplyPreview(message: ChatModels.Message, isOwn: Boolean) {
    val reply = message.replyTo ?: return
    Box(
        modifier = Modifier
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (isOwn) 0.08f else 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = reply.senderName,
                color = HelloColors.DarkAccentStrong,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = reply.text.ifBlank { "Attachment" },
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageBody(
    message: ChatModels.Message,
    isOwn: Boolean,
    textColor: Color,
    bubbleColor: Color,
    bubbleBorder: Color,
    timestampLabel: String,
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit,
    imageCluster: List<ChatModels.Message>? = null,
    lightweightMedia: Boolean = false
) {
    val resolved = remember(message.attachmentUrl) { normalizeAttachmentUrl(message.attachmentUrl) }
    val resolvedAttachmentKind = remember(message.attachmentType, message.attachmentName, message.attachmentUrl) {
        attachmentKind(message)
    }

    if (message.isDeleted == true) {
        Text(
            text = "This message was deleted",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )
        return
    }

    if (isStickerMessage(message.text) && message.attachmentUrl.isNullOrBlank()) {
        Text(
            text = stickerPayload(message.text),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    if (message.callInfo != null || message.messageType == "call_log") {
        CallSummaryCard(
            message = message,
            isOwn = isOwn,
            textColor = textColor,
            timestampLabel = timestampLabel
        )
        return
    }

    when {
        message.location != null -> LocationCard(message, onOpenAttachment)
        message.text.startsWith("Contact:", ignoreCase = true) -> ContactCard(message)
        isUrlOnly(message.text) && message.attachmentUrl.isNullOrBlank() -> LinkCard(message.text, onOpenAttachment)
    }

    if (!imageCluster.isNullOrEmpty()) {
        ImageCollageCard(
            messages = imageCluster,
            onOpenImage = onOpenImage,
            onDownload = onDownloadAttachment,
            lightweight = lightweightMedia,
            frameColor = bubbleColor.copy(alpha = 0.94f),
            frameBorderColor = bubbleBorder
        )
    } else if (!resolved.isNullOrBlank()) {
        when (resolvedAttachmentKind) {
            "image" -> ImageCard(message, resolved, onOpenImage, onDownloadAttachment, lightweightMedia, bubbleColor.copy(alpha = 0.94f), bubbleBorder)
            "audio" -> AudioCard(message, resolved, onDownloadAttachment, lightweightMedia)
            "video" -> VideoCard(message, resolved, onOpenAttachment, onDownloadAttachment, lightweightMedia, bubbleColor.copy(alpha = 0.94f), bubbleBorder)
            else -> FileCard(message, resolved, onOpenAttachment, onDownloadAttachment)
        }
    }

    val shouldShowText = message.text.isNotBlank() &&
        message.text != " " &&
        message.location == null &&
        !message.text.startsWith("Contact:", ignoreCase = true) &&
        !(isUrlOnly(message.text) && message.attachmentUrl.isNullOrBlank())
    if (shouldShowText) {
        if (resolved != null || !imageCluster.isNullOrEmpty()) Spacer(modifier = Modifier.size(7.dp))
        Text(
            text = message.text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}

@Composable
private fun CallSummaryCard(
    message: ChatModels.Message,
    isOwn: Boolean,
    textColor: Color,
    timestampLabel: String
) {
    val callInfo = message.callInfo
    val accent = if (callInfo?.status.equals("missed", ignoreCase = true)) HelloColors.DarkDanger else HelloColors.DarkAccentStrong
    val icon = when {
        callInfo?.callType.equals("video", ignoreCase = true) -> Icons.Default.Videocam
        else -> Icons.Default.Phone
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isOwn) 0.08f else 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = callSummaryLabel(callInfo),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val detail = timestampLabel
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (callInfo?.durationSeconds ?: 0L > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CallMade, contentDescription = null, tint = HelloColors.DarkTextMuted, modifier = Modifier.size(14.dp))
                    Text(
                        text = formatDuration(callInfo?.durationSeconds ?: 0L),
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageMeta(
    message: ChatModels.Message,
    isOwn: Boolean,
    statusColor: Color,
    compact: Boolean,
    timestampLabel: String
) {
    Row(
        modifier = Modifier
            .wrapContentWidth(Alignment.End)
            .padding(top = if (compact) 2.dp else 7.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(timestampLabel, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelSmall)
        if (isOwn) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when (message.status) {
                    "sending" -> "\u23F3"
                    "sent" -> "\u2713"
                    "delivered" -> "\u2713\u2713"
                    "read" -> "\u2713\u2713"
                    "failed" -> "\u2715"
                    else -> "\u2713"
                },
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReactionStrip(summary: ReactionSummary?) {
    if (summary == null) return
    Row(
        modifier = Modifier
            .padding(top = 3.dp, start = 8.dp, end = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HelloColors.WarmGlow)
            .border(1.dp, HelloColors.WarmAccent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        summary.emojis.forEach { emoji ->
            Text(emoji, style = MaterialTheme.typography.bodySmall)
        }
        Text(summary.totalCount.toString(), color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun messageBubbleShape(isOwn: Boolean, compactWithPrevious: Boolean, compactWithNext: Boolean): RoundedCornerShape {
    val large = 10.dp
    val compact = 6.dp
    val tail = 3.dp
    return if (isOwn) {
        RoundedCornerShape(
            topStart = large,
            topEnd = if (compactWithPrevious) compact else large,
            bottomEnd = if (compactWithNext) compact else tail,
            bottomStart = large
        )
    } else {
        RoundedCornerShape(
            topStart = if (compactWithPrevious) compact else large,
            topEnd = large,
            bottomEnd = large,
            bottomStart = if (compactWithNext) compact else tail
        )
    }
}
