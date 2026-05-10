package com.glassbox.hello.chat.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.ui.theme.HelloAnimations
import com.glassbox.hello.ui.theme.HelloColors

@Composable
fun ImageCard(
    message: ChatModels.Message,
    resolvedUrl: String,
    onOpenImage: (String, String) -> Unit,
    onDownload: (String, String?) -> Unit
) {
    Box {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(resolvedUrl)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = message.attachmentName ?: "Image attachment",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF071219))
                .combinedClickable(onClick = { onOpenImage(resolvedUrl, message.attachmentName ?: "Image") }),
            loading = { AttachmentPlaceholder("Loading image") },
            error = { AttachmentPlaceholder("Image unavailable") }
        )
        DownloadChip(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            onClick = { onDownload(resolvedUrl, message.attachmentName) }
        )
    }
}

@Composable
fun AudioCard(message: ChatModels.Message, resolvedUrl: String, onDownload: (String, String?) -> Unit) {
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
            .widthIn(max = 340.dp)
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
        DownloadChip(onClick = { onDownload(resolvedUrl, message.attachmentName) })
    }
}

@Composable
fun FileCard(
    message: ChatModels.Message,
    resolvedUrl: String,
    onOpen: (String) -> Unit,
    onDownload: (String, String?) -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(onClick = { onOpen(resolvedUrl) })
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
            Text(
                text = attachmentTypeLabel(message),
                color = HelloColors.DarkAccentStrong,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
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
                    append(attachmentTypeLabel(message))
                    message.attachmentSize?.let { append(" - ").append(formatBytes(it)) }
                },
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        DownloadChip(onClick = { onDownload(resolvedUrl, message.attachmentName) })
    }
}

@Composable
fun LocationCard(message: ChatModels.Message, onOpen: (String) -> Unit) {
    val location = message.location ?: return
    val target = when {
        message.text.startsWith("http", ignoreCase = true) -> message.text
        message.text.isNotBlank() && message.text != "Location shared" -> "https://maps.google.com/?q=${Uri.encode(message.text)}"
        else -> "https://maps.google.com/?q=${location.lat},${location.lng}"
    }
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(onClick = { onOpen(target) })
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
fun ContactCard(message: ChatModels.Message) {
    val lines = message.text.lines()
    val name = lines.firstOrNull()?.removePrefix("Contact:")?.trim().orEmpty().ifBlank { "Contact" }
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
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
fun LinkCard(text: String, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(onClick = { onOpen(text) })
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
private fun AttachmentPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF071219)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

fun openExternalTarget(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
private fun DownloadChip(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Download, contentDescription = "Download attachment", tint = Color.White)
    }
}
