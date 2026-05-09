package com.glassbox.hello.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.chat.ChatModels.Message
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.ui.components.HelloFileCard
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    isFromCurrentUser: Boolean,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    val bubbleColor = if (isFromCurrentUser) HelloColors.MessageMineDark else HelloColors.MessageOtherDark
    val textColor = HelloColors.AuthText

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongPress
            ),
        verticalAlignment = Alignment.Bottom
    ) {
        if (isFromCurrentUser) Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.widthIn(max = 292.dp),
            horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
        ) {
            if (!isFromCurrentUser) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = HelloColors.ReadReceipt,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }

            Column(
                modifier = Modifier
                    .background(
                        color = bubbleColor,
                        shape = if (isFromCurrentUser) HelloShapes.MessageMine else HelloShapes.MessageOther
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.isDeleted == true) {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HelloColors.AuthMuted
                    )
                } else {
                    if (message.attachmentType == "image" && !message.attachmentUrl.isNullOrBlank()) {
                        val resolved = UrlResolver.resolve(message.attachmentUrl)
                        if (resolved != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(resolved)
                                    .decoderFactory(SvgDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = message.attachmentName ?: "Image attachment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .padding(bottom = HelloSpacing.Sm)
                                    .background(HelloColors.DarkPanelMuted, HelloShapes.Md),
                                contentScale = ContentScale.Crop,
                                loading = { ImagePlaceholder(message.attachmentName ?: "Loading image") },
                                error = { ImagePlaceholder(message.attachmentName ?: "Image unavailable") }
                            )
                        }
                    } else if (!message.attachmentName.isNullOrBlank()) {
                        HelloFileCard(
                            title = message.attachmentName,
                            detail = message.attachmentType ?: "Attachment",
                            modifier = Modifier.padding(bottom = HelloSpacing.Sm)
                        )
                    }
                    Text(
                        text = message.text.ifBlank { "Attachment" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
                Text(
                    text = buildString {
                        append(timeString)
                        if (isFromCurrentUser && !message.status.isNullOrBlank()) {
                            append("  ")
                            append(
                                when (message.status) {
                                    "read" -> "Read"
                                    "delivered" -> "Delivered"
                                    else -> "Sent"
                                }
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFromCurrentUser && message.status == "read") HelloColors.ReadReceipt else HelloColors.AuthMuted
                )
            }
        }

        if (!isFromCurrentUser) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ImagePlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(HelloColors.DarkPanelMuted, HelloShapes.Md),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(HelloSpacing.Md)
        ) {
            Text("🖼️", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(HelloSpacing.Xs))
            Text(
                label,
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
