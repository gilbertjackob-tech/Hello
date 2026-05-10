package com.glassbox.hello.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.glassbox.hello.chat.AttachmentAction
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun AttachmentPreviewBar(
    file: AttachmentDraft,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Md) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            if (file.mimeType.startsWith("image/")) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file.uri)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    modifier = Modifier
                        .size(56.dp)
                        .background(HelloColors.DarkPanelMuted, HelloShapes.Md),
                    contentScale = ContentScale.Crop,
                    loading = { AttachmentIcon() },
                    error = { AttachmentIcon() }
                )
            } else {
                AttachmentIcon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = HelloColors.DarkText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.mimeType} - ${formatBytes(file.sizeBytes)}",
                    color = HelloColors.DarkTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Caption will be sent with this attachment.",
                    color = HelloColors.DarkAccent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            HelloIconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
fun ReplyComposerBar(message: ChatModels.Message, onClear: () -> Unit, modifier: Modifier = Modifier) {
    HelloPanel(
        modifier = modifier.fillMaxWidth(),
        strong = true,
        shape = HelloShapes.Md
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(HelloColors.DarkAccent, HelloShapes.Pill)
            )
            Spacer(modifier = Modifier.width(HelloSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to ${message.senderName}",
                    color = HelloColors.DarkAccentStrong,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = messagePreviewText(message),
                    color = HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HelloIconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear reply", tint = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
fun AttachmentBottomSheet(onAction: (AttachmentAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Text("Attach", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val actions = listOf(
            Triple(AttachmentAction.Gallery, "Gallery", Icons.Default.Image),
            Triple(AttachmentAction.Camera, "Camera", Icons.Default.CameraAlt),
            Triple(AttachmentAction.File, "Document", Icons.Default.Description),
            Triple(AttachmentAction.Location, "Location", Icons.Default.LocationOn),
            Triple(AttachmentAction.Contact, "Contact", Icons.Default.Person),
            Triple(AttachmentAction.Audio, "Audio", Icons.Default.Mic)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            items(actions) { (action, label, icon) ->
                HelloPanel(
                    modifier = Modifier
                        .size(84.dp)
                        .clickable { onAction(action) },
                    strong = true,
                    shape = HelloShapes.Md
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(HelloSpacing.Sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = HelloColors.DarkAccent)
                        Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                        Text(label, color = HelloColors.DarkText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
    }
}

@Composable
fun ContactShareDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    usersState: ResultState<List<ChatModels.User>>,
    onDismiss: () -> Unit,
    onShare: (ChatModels.User) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Share contact", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                HelloSearchBar(value = query, onValueChange = onQueryChange, placeholder = "Search contacts")
                when (usersState) {
                    is ResultState.Loading -> Text("Loading contacts...", color = HelloColors.DarkTextMuted)
                    is ResultState.Error -> Text(usersState.message, color = HelloColors.DarkDanger)
                    is ResultState.Success -> {
                        LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            items(usersState.data, key = { it.id }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onShare(user) }
                                        .padding(HelloSpacing.Sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                                ) {
                                    HelloAvatar(name = user.name, online = user.online == true, size = 40.dp, imageUrl = user.avatar)
                                    Text(user.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

@Composable
private fun AttachmentIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(HelloColors.DarkAccentSoft, HelloShapes.Md),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = HelloColors.DarkAccent)
    }
}
