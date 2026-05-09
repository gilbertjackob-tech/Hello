package com.glassbox.hello.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.HelloAnimations
import com.glassbox.hello.ui.utils.AnimationUtils
import kotlin.math.roundToInt

/**
 * Premium animated message bubble with clear own/other distinction
 * - Own messages: right-aligned, green accent, read ticks
 * - Other messages: left-aligned, dark neutral, no ticks
 */
@Composable
fun AnimatedMessageBubble(
    message: ChatModels.Message,
    isOwn: Boolean,
    index: Int,
    currentUserId: String,
    context: Context,
    onReply: (ChatModels.Message) -> Unit,
    onLongPress: (ChatModels.Message) -> Unit,
    modifier: Modifier = Modifier
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 60f
    val shouldShowReplyIcon = swipeOffset > swipeThreshold

    // Status color animation (blue for read, gray for sending)
    val statusColor by animateColorAsState(
        targetValue = when (message.status) {
            "sending" -> Color(0xFF9CA3AF)   // gray
            "sent" -> Color(0xFF9CA3AF)      // light gray
            "delivered" -> Color(0xFF60A5FA) // light blue
            "read" -> Color(0xFF3B82F6)      // blue
            "failed" -> Color(0xFFEF4444)    // red
            else -> Color(0xFF9CA3AF)
        },
        animationSpec = tween(durationMillis = HelloAnimations.Duration.NORMAL),
        label = "messageStatus"
    )

    // Animation direction based on own/other
    val enterAnimation = if (isOwn) {
        slideInHorizontally(initialOffsetX = { it }) + fadeIn()
    } else {
        slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
    }

    AnimatedVisibility(
        visible = true,
        enter = enterAnimation,
        exit = slideOutHorizontally() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HelloSpacing.Xxs)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (!isOwn) {
                                swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, swipeThreshold * 1.5f)
                                if (shouldShowReplyIcon && dragAmount > 5) {
                                    AnimationUtils.Haptics.threshold(context)
                                }
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (swipeOffset > swipeThreshold && !isOwn) {
                                onReply(message)
                                AnimationUtils.Haptics.tapMedium(context)
                            }
                            swipeOffset = 0f
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HelloSpacing.Lg),
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Swipe reply icon (appears on swipe right for other messages)
                AnimatedVisibility(
                    visible = shouldShowReplyIcon && !isOwn,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = HelloColors.DarkAccent,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = HelloSpacing.Sm)
                    )
                }

                // Message Bubble Container
                Box(
                    modifier = Modifier
                        .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                        .fillMaxWidth(0.72f)
                        .background(
                            color = if (isOwn) Color(0xFF005C4B) else Color(0xFF111B21),
                            shape = RoundedCornerShape(
                                topStart = if (isOwn) 20.dp else 20.dp,
                                topEnd = if (isOwn) 20.dp else 20.dp,
                                bottomStart = if (isOwn) 20.dp else 6.dp,
                                bottomEnd = if (isOwn) 6.dp else 20.dp
                            )
                        )
                        .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm)
                ) {
                    Column {
                        // Reply preview if applicable
                        if (message.replyTo != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isOwn) Color(0xFF00A884) else Color(0xFF1F2937),
                                        shape = RoundedCornerShape(HelloSpacing.Xs)
                                    )
                                    .padding(HelloSpacing.Xs)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "↳ ${message.replyTo.senderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = message.replyTo.text.take(50),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOwn) Color(0xFFD1D5DB) else Color(0xFF9CA3AF),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                        }

                        // Message text
                        if (message.text.isNotEmpty()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        // Image attachment
                        if (message.attachmentType == "image" && message.attachmentUrl != null) {
                            Spacer(modifier = Modifier.height(HelloSpacing.Sm))
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.attachmentUrl)
                                    .build(),
                                contentDescription = message.attachmentName ?: "Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(
                                        color = Color(0xFF0F1419),
                                        shape = RoundedCornerShape(HelloSpacing.Md)
                                    )
                            )
                        }

                        // Timestamp and status (only on own messages show status ticks)
                        if (message.text.isNotEmpty() || message.attachmentUrl != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = HelloSpacing.Xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTimestamp(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFAAADAA)
                                )

                                // Status indicator - ONLY for own messages
                                if (isOwn) {
                                    Text(
                                        text = when (message.status) {
                                            "sending" -> "⏱"
                                            "sent" -> "✓"
                                            "delivered" -> "✓✓"
                                            "read" -> "✓✓"
                                            "failed" -> "✗"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Optimistic message indicator
                        if (OptimisticMessageManager.isTempId(message.id)) {
                            Text(
                                text = "Sending...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFAAADAA),
                                modifier = Modifier.padding(top = HelloSpacing.Xs)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "now"
        diff < 3600000 -> "${(diff / 60000).toInt()}m"
        diff < 86400000 -> "${(diff / 3600000).toInt()}h"
        else -> {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}


