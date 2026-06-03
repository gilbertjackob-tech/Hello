package com.glassbox.hello.chat

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.ui.theme.HelloAnimations
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloDimens
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.utils.AnimationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnimatedMessageBubble(
    message: ChatModels.Message,
    isOwn: Boolean,
    index: Int,
    currentUserId: String,
    context: Context,
    onReply: (ChatModels.Message) -> Unit,
    onLongPress: (ChatModels.Message) -> Unit,
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
    val statusColor by animateColorAsState(
        targetValue = when (message.status) {
            "read" -> HelloColors.ReadReceipt
            "delivered" -> HelloColors.DarkTextMuted
            "failed" -> HelloColors.DarkDanger
            else -> HelloColors.DarkTextMuted
        },
        animationSpec = tween(durationMillis = HelloAnimations.Duration.NORMAL),
        label = "statusColor"
    )
    val bubbleColor = when {
        message.isDeleted == true -> Color(0xB31D2930)
        isOwn -> HelloColors.BubbleOut
        else -> HelloColors.BubbleIn
    }
    val bubbleBorderColor = if (isOwn) HelloColors.BubbleOutBorder else HelloColors.BubbleInBorder
    val bubbleShape = messageBubbleShape(isOwn, compactWithPrevious, compactWithNext)

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { if (isOwn) it / 3 else -it / 3 },
            animationSpec = tween(HelloAnimations.Duration.MEDIUM)
        ) + fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (compactWithPrevious) 1.dp else 7.dp,
                    bottom = if (compactWithNext) 1.dp else 7.dp
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isOwn) {
                    ReplyHint(visible = showReplyIcon)
                }

                Column(
                    modifier = Modifier.widthIn(max = 314.dp),
                    horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
                ) {
                    if (showSenderName && !isOwn) {
                        Text(
                            text = message.senderName,
                            color = HelloColors.TealPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp, bottom = 3.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                            .fillMaxWidth()
                            .clip(bubbleShape)
                            .background(bubbleColor)
                            .border(0.5.dp, bubbleBorderColor, bubbleShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    AnimationUtils.Haptics.tapMedium(context)
                                    onLongPress(message)
                                }
                            )
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Column {
                            MessageBadges(message = message, currentUserId = currentUserId)
                            ReplyPreview(message = message, isOwn = isOwn)
                            MessageBody(message = message, isOwn = isOwn, context = context)
                            MessageMeta(
                                message = message,
                                isOwn = isOwn,
                                statusColor = statusColor,
                                compact = message.text.length <= 32 && message.attachmentUrl.isNullOrBlank()
                            )
                        }
                    }
                    if (message.reactions.orEmpty().isNotEmpty()) {
                        val reactions = message.reactions.orEmpty()
                            .groupBy { it.emoji }
                            .map { (emoji, list) ->
                                com.glassbox.hello.chat.components.Reaction(
                                    emoji = emoji,
                                    count = list.size,
                                    selectedByMe = list.any { it.userId == currentUserId }
                                )
                            }
                        com.glassbox.hello.chat.components.ReactionRow(
                            reactions = reactions,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                }

                if (isOwn) {
                    ReplyHint(visible = showReplyIcon)
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
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOwn) Color(0x3328C0A4) else Color(0x1FE2E8F0))
            .border(1.dp, HelloColors.DarkAccentSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
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
private fun MessageBody(message: ChatModels.Message, isOwn: Boolean, context: Context) {
    if (message.isDeleted == true) {
        Text(
            text = "This message was deleted",
            color = HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )
        return
    }

    when {
        message.location != null -> LocationCard(message)
        message.text.startsWith("Contact:", ignoreCase = true) -> ContactCard(message)
        isLinkOnly(message.text) && message.attachmentUrl.isNullOrBlank() -> LinkCard(message.text, context)
    }

    val resolved = UrlResolver.resolve(message.attachmentUrl)
    if (!resolved.isNullOrBlank()) {
        when (message.attachmentType) {
            "image" -> ImageAttachment(message, resolved, context)
            "audio" -> AudioAttachment(message, resolved)
            else -> FileAttachment(message, resolved, context)
        }
    }

    val shouldShowText = message.text.isNotBlank() &&
        message.text != " " &&
        message.location == null &&
        !message.text.startsWith("Contact:", ignoreCase = true)
    if (shouldShowText) {
        if (resolved != null) Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = message.text,
            color = HelloColors.DarkText,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}

@Composable
private fun ImageAttachment(message: ChatModels.Message, resolvedUrl: String, context: Context) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedUrl)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .build(),
        contentDescription = message.attachmentName ?: "Image attachment",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.08f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF071219)),
        loading = { AttachmentPlaceholder("Loading image") },
        error = { AttachmentPlaceholder("Image unavailable") }
    )
}

@Composable
private fun AudioAttachment(message: ChatModels.Message, resolvedUrl: String) {
    var player by remember(resolvedUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(resolvedUrl) {
        onDispose {
            runCatching { player?.stop() }
            player?.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .combinedClickable(
                        onClick = {
                            val activePlayer = player ?: MediaPlayer().apply {
                                setDataSource(resolvedUrl)
                                setOnCompletionListener { isPlaying = false }
                                prepareAsync()
                                setOnPreparedListener {
                                    start()
                                    isPlaying = true
                                }
                            }.also { player = it }
                            if (activePlayer.isPlaying) {
                                activePlayer.pause()
                                isPlaying = false
                            } else if (player === activePlayer) {
                                runCatching {
                                    activePlayer.start()
                                    isPlaying = true
                                }
                            }
                        }
                    )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Voice note", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(
                text = message.attachmentName ?: "Tap to listen",
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            VoiceWaveform(isPlaying = isPlaying)
        }
    }
}

@Composable
private fun VoiceWaveform(isPlaying: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 6.dp)) {
        val bars = listOf(0.35f, 0.62f, 0.42f, 0.78f, 0.5f, 0.92f, 0.44f, 0.7f, 0.38f, 0.58f)
        bars.forEachIndexed { index, height ->
            val animated by animateFloatAsState(
                targetValue = if (isPlaying && index % 2 == 0) 1f else height,
                animationSpec = tween(HelloAnimations.Duration.MEDIUM),
                label = "waveform$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((18 * animated).dp.coerceAtLeast(5.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isPlaying) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun FileAttachment(message: ChatModels.Message, resolvedUrl: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(onClick = { openUrl(context, resolvedUrl) })
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(HelloColors.DarkAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = HelloColors.DarkAccentStrong)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.attachmentName ?: "Attachment",
                color = HelloColors.DarkText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(message.attachmentType ?: "File")
                    message.attachmentSize?.let { append(" - ").append(formatBytes(it)) }
                },
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LocationCard(message: ChatModels.Message) {
    val location = message.location ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = HelloColors.DarkAccentStrong)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Location", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(
                text = message.text.ifBlank { "${"%.5f".format(location.lat)}, ${"%.5f".format(location.lng)}" },
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactCard(message: ChatModels.Message) {
    val lines = message.text.lines()
    val name = lines.firstOrNull()?.removePrefix("Contact:")?.trim().orEmpty().ifBlank { "Contact" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = HelloColors.DarkAccentStrong)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(
                text = lines.drop(1).joinToString("  ").ifBlank { "Shared contact" },
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LinkCard(text: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(onClick = { openUrl(context, text) })
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(HelloColors.DarkAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("link", color = HelloColors.DarkAccentStrong, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Open link", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(text, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AttachmentPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.08f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF071219)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageMeta(message: ChatModels.Message, isOwn: Boolean, statusColor: Color, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    val large = HelloDimens.BubbleCorner
    val tight = 8.dp
    val tail = HelloDimens.BubbleCornerSmall
    return if (isOwn) {
        RoundedCornerShape(
            topStart = large,
            topEnd = if (compactWithPrevious) tight else large,
            bottomEnd = if (compactWithNext) tight else tail,
            bottomStart = large
        )
    } else {
        RoundedCornerShape(
            topStart = if (compactWithPrevious) tight else large,
            topEnd = large,
            bottomEnd = large,
            bottomStart = if (compactWithNext) tight else tail
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff in 0 until 60_000 -> "now"
        diff in 60_000 until 3_600_000 -> "${diff / 60_000}m"
        diff in 3_600_000 until 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(kb.coerceAtLeast(1.0))
}

private fun isLinkOnly(text: String): Boolean {
    val value = text.trim()
    return value.startsWith("http://") || value.startsWith("https://")
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
