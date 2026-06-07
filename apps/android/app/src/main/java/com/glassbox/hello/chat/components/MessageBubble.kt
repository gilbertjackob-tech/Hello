package com.glassbox.hello.chat.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.OptimisticMessageManager
import com.glassbox.hello.ui.theme.HelloAnimations
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.utils.AnimationUtils
import kotlin.math.roundToInt

@Composable
fun ChatMessageBubble(
    message: ChatModels.Message,
    isOwn: Boolean,
    currentUserId: String,
    context: Context,
    onReply: (ChatModels.Message) -> Unit,
    onLongPress: (ChatModels.Message) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit,
    outgoingBubbleColor: Color,
    incomingBubbleColor: Color,
    bubbleOpacity: Float,
    modifier: Modifier = Modifier,
    showSenderName: Boolean = false,
    compactWithPrevious: Boolean = false,
    compactWithNext: Boolean = false
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 64f
    val showReplyIcon = swipeOffset > 18f
    val bubbleScale by animateFloatAsState(
        targetValue = if (OptimisticMessageManager.isTempId(message.id)) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.78f),
        label = "bubbleScale"
    )
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
    val bubbleShape = messageBubbleShape(isOwn, compactWithPrevious, compactWithNext)

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { 8 },
            animationSpec = tween(HelloAnimations.Duration.MEDIUM)
        ) + fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (compactWithPrevious) 2.dp else 8.dp,
                    bottom = if (compactWithNext) 2.dp else 8.dp
                )
        ) {
            BoxWithConstraints {
                val maxBubbleWidth = maxWidth * 0.76f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isOwn) ReplyHint(visible = showReplyIcon)
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .wrapContentWidth(if (isOwn) Alignment.End else Alignment.Start),
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
                                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                                .shadow(if (isStickerMessage(message.text)) 0.dp else 8.dp, bubbleShape, ambientColor = HelloColors.Accent.copy(alpha = 0.18f))
                                .clip(bubbleShape)
                                .background(bubbleColor)
                                .border(1.2.dp, bubbleBorder, bubbleShape)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        AnimationUtils.Haptics.tapMedium(context)
                                        onLongPress(message)
                                    }
                                )
                                .pointerInput(message.id, isOwn) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            val next = swipeOffset + dragAmount
                                            swipeOffset = if (isOwn) {
                                                next.coerceIn(-swipeThreshold * 1.45f, 0f)
                                            } else {
                                                next.coerceIn(0f, swipeThreshold * 1.45f)
                                            }
                                            if (kotlin.math.abs(swipeOffset) > swipeThreshold) {
                                                AnimationUtils.Haptics.threshold(context)
                                            }
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            if (kotlin.math.abs(swipeOffset) > swipeThreshold) {
                                                onReply(message)
                                                AnimationUtils.Haptics.tapMedium(context)
                                            }
                                            swipeOffset = 0f
                                        }
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier.graphicsLayer(
                                    scaleX = bubbleScale,
                                    scaleY = bubbleScale
                                )
                            ) {
                                MessageBadges(message = message, currentUserId = currentUserId)
                                ReplyPreview(message = message, isOwn = isOwn)
                                MessageBody(
                                    message = message,
                                    isOwn = isOwn,
                                    textColor = contentColor,
                                    onOpenAttachment = onOpenAttachment,
                                    onOpenImage = onOpenImage,
                                    onDownloadAttachment = onDownloadAttachment
                                )
                                MessageMeta(
                                    message = message,
                                    isOwn = isOwn,
                                    statusColor = statusColor,
                                    compact = message.text.length <= 32 && message.attachmentUrl.isNullOrBlank()
                                )
                            }
                        }
                        ReactionStrip(message.reactions.orEmpty())
                    }
                    if (isOwn) ReplyHint(visible = showReplyIcon)
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
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit
) {
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
        CallSummaryCard(message = message, isOwn = isOwn, textColor = textColor)
        return
    }

    when {
        message.location != null -> LocationCard(message, onOpenAttachment)
        message.text.startsWith("Contact:", ignoreCase = true) -> ContactCard(message)
        isUrlOnly(message.text) && message.attachmentUrl.isNullOrBlank() -> LinkCard(message.text, onOpenAttachment)
    }

    val resolved = normalizeAttachmentUrl(message.attachmentUrl)
    if (!resolved.isNullOrBlank()) {
        when (message.attachmentType) {
            "image" -> ImageCard(message, resolved, onOpenImage, onDownloadAttachment)
            "audio" -> AudioCard(message, resolved, onDownloadAttachment)
            "video" -> VideoCard(message, resolved, onOpenAttachment, onDownloadAttachment)
            else -> FileCard(message, resolved, onOpenAttachment, onDownloadAttachment)
        }
    }

    val shouldShowText = message.text.isNotBlank() &&
        message.text != " " &&
        message.location == null &&
        !message.text.startsWith("Contact:", ignoreCase = true) &&
        !(isUrlOnly(message.text) && message.attachmentUrl.isNullOrBlank())
    if (shouldShowText) {
        if (resolved != null) Spacer(modifier = Modifier.size(7.dp))
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
    textColor: Color
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
                val detail = when {
                    callInfo?.startedAt != null -> formatTimestamp(callInfo.startedAt)
                    message.timestamp > 0L -> formatTimestamp(message.timestamp)
                    else -> ""
                }
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

private fun readableMessageTextColor(color: Color): Color {
    return if (color.luminance() > 0.52f) Color(0xFF071219) else Color.White
}

@Composable
private fun MessageMeta(message: ChatModels.Message, isOwn: Boolean, statusColor: Color, compact: Boolean) {
    Row(
        modifier = Modifier
            .wrapContentWidth(Alignment.End)
            .padding(top = if (compact) 2.dp else 7.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatTimestamp(message.timestamp), color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelSmall)
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
private fun ReactionStrip(reactions: List<ChatModels.Reaction>) {
    if (reactions.isEmpty()) return
    val grouped = reactions.groupBy { it.emoji }.entries.sortedByDescending { it.value.size }
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "reactionChipBounce"
    )
    Row(
        modifier = Modifier
            .padding(top = 3.dp, start = 8.dp, end = 8.dp)
            .scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(HelloColors.WarmGlow)
            .border(1.dp, HelloColors.WarmAccent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        grouped.take(4).forEach { entry ->
            Text(entry.key, style = MaterialTheme.typography.bodySmall)
        }
        Text(reactions.size.toString(), color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReplyHint(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = HelloColors.DarkAccent, modifier = Modifier.size(18.dp))
        }
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
