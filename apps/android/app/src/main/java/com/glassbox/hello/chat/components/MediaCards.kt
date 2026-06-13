package com.glassbox.hello.chat.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import coil.compose.AsyncImage
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
    var aspectRatio by remember(resolvedUrl) { mutableStateOf(4f / 3f) }
    BoxWithConstraints {
        val ratio = aspectRatio.coerceIn(0.62f, 1.9f)
        Box(
            modifier = Modifier
                .widthIn(min = 156.dp, max = maxWidth)
                .heightIn(max = 360.dp)
                .aspectRatio(ratio)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(resolvedUrl)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(false)
                    .allowHardware(true)
                    .memoryCacheKey(message.id)
                    .diskCacheKey(message.id)
                    .build(),
                contentDescription = message.attachmentName ?: "Image attachment",
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val drawable = state.result.drawable
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    if (width > 0 && height > 0) {
                        aspectRatio = width.toFloat() / height.toFloat()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF071219))
                    .combinedClickable(onClick = { onOpenImage(resolvedUrl, message.attachmentName ?: "Image") }),
                placeholder = null,
                error = null
            )
            DownloadChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                onClick = { onDownload(resolvedUrl, message.attachmentName) }
            )
        }
    }
}

@Composable
fun ImageCollageCard(
    messages: List<ChatModels.Message>,
    onOpenImage: (String, String) -> Unit,
    onDownload: (String, String?) -> Unit
) {
    val items = remember(messages) {
        messages.mapNotNull { message ->
            normalizeAttachmentUrl(message.attachmentUrl)?.let { resolved ->
                message to resolved
            }
        }
    }
    if (items.isEmpty()) return
    val visibleItems = items.take(4)
    Box(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 320.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .padding(6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 6.dp
            val leftWide = (maxWidth - gap) * 0.58f
            val rightNarrow = maxWidth - leftWide - gap
            val halfWidth = (maxWidth - gap) / 2
            when (visibleItems.size) {
                1 -> {
                    val (message, resolvedUrl) = visibleItems.first()
                    CollageImageTile(
                        message = message,
                        resolvedUrl = resolvedUrl,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.22f),
                        onOpenImage = onOpenImage
                    )
                }
                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        val (firstMessage, firstUrl) = visibleItems[0]
                        val (secondMessage, secondUrl) = visibleItems[1]
                        CollageImageTile(firstMessage, firstUrl, RoundedCornerShape(topStart = 24.dp, topEnd = 18.dp, bottomStart = 22.dp, bottomEnd = 16.dp), Modifier.width(leftWide).aspectRatio(0.92f), onOpenImage)
                        CollageImageTile(secondMessage, secondUrl, RoundedCornerShape(topStart = 18.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 22.dp), Modifier.width(rightNarrow).aspectRatio(0.92f), onOpenImage)
                    }
                }
                3 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        val (heroMessage, heroUrl) = visibleItems[0]
                        CollageImageTile(heroMessage, heroUrl, RoundedCornerShape(24.dp), Modifier.fillMaxWidth().aspectRatio(1.38f), onOpenImage)
                        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                            visibleItems.drop(1).forEachIndexed { index, entry ->
                                val (message, resolvedUrl) = entry
                                val shape = if (index == 0) {
                                    RoundedCornerShape(topStart = 18.dp, topEnd = 14.dp, bottomStart = 22.dp, bottomEnd = 14.dp)
                                } else {
                                    RoundedCornerShape(topStart = 14.dp, topEnd = 18.dp, bottomStart = 14.dp, bottomEnd = 22.dp)
                                }
                                CollageImageTile(message, resolvedUrl, shape, Modifier.width(halfWidth).aspectRatio(1f), onOpenImage)
                            }
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        val (heroMessage, heroUrl) = visibleItems[0]
                        CollageImageTile(heroMessage, heroUrl, RoundedCornerShape(topStart = 24.dp, topEnd = 18.dp, bottomStart = 24.dp, bottomEnd = 18.dp), Modifier.width(leftWide).aspectRatio(0.96f), onOpenImage)
                        Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.width(rightNarrow)) {
                            visibleItems.drop(1).forEachIndexed { index, entry ->
                                val (message, resolvedUrl) = entry
                                val extraCount = items.size - visibleItems.size
                                CollageImageTile(
                                    message = message,
                                    resolvedUrl = resolvedUrl,
                                    shape = when (index) {
                                        0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 22.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                                        1 -> RoundedCornerShape(14.dp)
                                        else -> RoundedCornerShape(topStart = 14.dp, topEnd = 18.dp, bottomStart = 16.dp, bottomEnd = 24.dp)
                                    },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1.08f),
                                    onOpenImage = onOpenImage,
                                    overlayLabel = if (index == 2 && extraCount > 0) "+$extraCount" else null
                                )
                            }
                        }
                    }
                }
            }
        }
        DownloadChip(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            onClick = {
                val lead = items.first()
                onDownload(lead.second, lead.first.attachmentName)
            }
        )
    }
}

@Composable
private fun CollageImageTile(
    message: ChatModels.Message,
    resolvedUrl: String,
    shape: RoundedCornerShape,
    modifier: Modifier,
    onOpenImage: (String, String) -> Unit,
    overlayLabel: String? = null
) {
    Box(modifier = modifier.clip(shape).background(Color(0xFF071219))) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(resolvedUrl)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(false)
                .allowHardware(true)
                .memoryCacheKey(message.id)
                .diskCacheKey(message.id)
                .build(),
            contentDescription = message.attachmentName ?: "Image attachment",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = { onOpenImage(resolvedUrl, message.attachmentName ?: "Image") }),
            placeholder = null,
            error = null
        )
        if (overlayLabel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(overlayLabel, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
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
    val meta = remember(message.attachmentName, message.attachmentType) { attachmentMeta(message) }
    val failed = message.status == "failed"
    val sending = message.status == "sending"
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = { onOpen(resolvedUrl) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(meta.color.copy(alpha = 0.18f))
                .border(1.dp, meta.color.copy(alpha = 0.35f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = meta.badge,
                color = meta.color,
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
                    append(meta.label)
                    message.attachmentSize?.let { append(" - ").append(formatBytes(it)) }
                    if (sending) append(" - Uploading")
                    if (failed) append(" - Failed")
                },
                color = if (failed) HelloColors.DarkDanger else HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        DownloadChip(onClick = { onDownload(resolvedUrl, message.attachmentName) })
    }
}

@Composable
fun VideoCard(
    message: ChatModels.Message,
    resolvedUrl: String,
    onOpen: (String) -> Unit,
    onDownload: (String, String?) -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 340.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = { onOpen(resolvedUrl) })
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(resolvedUrl)
                .crossfade(false)
                .allowHardware(true)
                .memoryCacheKey(message.id)
                .diskCacheKey(message.id)
                .build(),
            contentDescription = message.attachmentName ?: "Video attachment",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.54f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        DownloadChip(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            onClick = { onDownload(resolvedUrl, message.attachmentName) }
        )
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
private fun AttachmentPlaceholder(label: String, aspectRatio: Float = 4f / 3f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.62f, 1.9f))
            .heightIn(min = 120.dp, max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF071219)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

private data class AttachmentMeta(
    val label: String,
    val badge: String,
    val color: Color
)

private fun attachmentMeta(message: ChatModels.Message): AttachmentMeta {
    val extension = message.attachmentName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    val type = message.attachmentType.orEmpty().lowercase()
    return when {
        extension == "pdf" -> AttachmentMeta("PDF document", "PDF", Color(0xFFEF4444))
        extension in setOf("doc", "docx") -> AttachmentMeta("Word document", "DOC", Color(0xFF2563EB))
        extension in setOf("xls", "xlsx", "csv") -> AttachmentMeta("Spreadsheet", "XLS", Color(0xFF16A34A))
        extension in setOf("ppt", "pptx") -> AttachmentMeta("Presentation", "PPT", Color(0xFFF97316))
        extension in setOf("zip", "rar", "7z") -> AttachmentMeta("Archive", "ZIP", Color(0xFFEAB308))
        extension == "apk" -> AttachmentMeta("Android package", "APK", Color(0xFF22C55E))
        type == "audio" || extension in setOf("mp3", "m4a", "aac", "wav", "ogg") -> AttachmentMeta("Audio", "AUD", Color(0xFF8B5CF6))
        type == "video" || extension in setOf("mp4", "mov", "mkv", "webm") -> AttachmentMeta("Video", "VID", Color(0xFF06B6D4))
        extension.isNotBlank() -> AttachmentMeta(extension.uppercase(), extension.take(3).uppercase(), HelloColors.DarkAccent)
        else -> AttachmentMeta("File", "FILE", HelloColors.DarkAccent)
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
