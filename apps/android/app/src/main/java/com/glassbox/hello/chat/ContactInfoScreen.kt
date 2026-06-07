package com.glassbox.hello.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.ui.components.AppBackground
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun ContactInfoScreen(
    chat: ChatModels.Chat,
    currentUser: User,
    callViewModel: CallViewModel,
    onBack: () -> Unit,
    onOpenSharedContent: (ChatSharedContentMode) -> Unit,
    onChatDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    val settingsState by rememberHelloSettingsState(context)
    val messagesState by viewModel.messagesState.collectAsState()
    var pendingVideoCall by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showClearChat by remember { mutableStateOf(false) }
    var showDeleteChat by remember { mutableStateOf(false) }

    val other = chat.otherParticipant(currentUser.id)
    val title = chat.displayName(currentUser.id)
    val avatar = other?.avatar ?: chat.avatar
    val subtitle = when {
        chat.isGroup -> "${chat.participantCount()} participants"
        other?.online == true -> "Online"
        other?.lastActive != null -> "Last active ${formatLastActive(other.lastActive)}"
        else -> "Hello user"
    }

    LaunchedEffect(chat.id, settingsState.cloudChatEnabled) {
        viewModel.loadMessages(chat.id, cloudChatEnabled = settingsState.cloudChatEnabled)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasCamera = !pendingVideoCall || grants[Manifest.permission.CAMERA] == true ||
            context.hasPermission(Manifest.permission.CAMERA)
        if (hasAudio && hasCamera) {
            val startAsVideo = pendingVideoCall
            pendingVideoCall = false
            callViewModel.startCall(context, chat, currentUser, startAsVideo)
        } else if (hasAudio && pendingVideoCall) {
            pendingVideoCall = false
            callViewModel.startCall(context, chat, currentUser, false)
        } else {
            showPermissionDialog = true
        }
    }

    fun requestCall(isVideo: Boolean) {
        if (chat.isGroup) return
        pendingVideoCall = isVideo
        val permissions = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.all { context.hasPermission(it) }) {
            pendingVideoCall = false
            callViewModel.startCall(context, chat, currentUser, isVideo)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    AppBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HelloColors.PanelStrong)
                .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            HelloIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.AccentStrong)
            }
            Text("Contact Info", color = HelloColors.AccentStrong, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelloSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HelloAvatar(name = title, online = other?.online == true, size = 132.dp, imageUrl = avatar)
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Text(title, color = HelloColors.AccentStrong, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(subtitle, color = if (other?.online == true) HelloColors.DarkAccent else HelloColors.DarkTextMuted)
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))

            if (!chat.isGroup) {
                Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                    ProfileAction("Audio", Icons.Default.Call, onClick = { requestCall(false) })
                    ProfileAction("Video", Icons.Default.Videocam, onClick = { requestCall(true) })
                }
            }
        }

        val messages = (messagesState as? ResultState.Success)?.data.orEmpty()
        val mediaCount = messages.count { it.attachmentType == "image" || it.attachmentType == "video" }
        val fileCount = messages.count { it.attachmentType == "file" || it.attachmentType == "audio" }
        val linkCount = messages.count { it.text.contains("http://") || it.text.contains("https://") }

        SectionPanel(title = "Media, links, and docs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                item { SummaryCard("Media", mediaCount, Icons.Default.Image) { onOpenSharedContent(ChatSharedContentMode.Media) } }
                item { SummaryCard("Docs", fileCount, Icons.Default.Description) { onOpenSharedContent(ChatSharedContentMode.Files) } }
                item { SummaryCard("Links", linkCount, Icons.Default.Link) { onOpenSharedContent(ChatSharedContentMode.Links) } }
            }
        }

        SectionPanel(title = "Chat actions") {
            ActionRow("Search in chat", Icons.Default.Search, muted = true) {}
            ActionRow("Clear chat locally", Icons.Default.Block, danger = true) { showClearChat = true }
            ActionRow("Delete chat locally", Icons.Default.Delete, danger = true) { showDeleteChat = true }
        }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = HelloColors.DarkPanelStrong,
            title = { Text("Permission needed", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
            text = { Text("Camera/microphone permission is needed for calls.", color = HelloColors.DarkTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Open Settings", color = HelloColors.DarkAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel", color = HelloColors.DarkTextMuted) }
            }
        )
    }

    if (showClearChat) {
        ConfirmChatAction(
            title = "Clear chat?",
            text = "This removes local chat messages for your account.",
            onDismiss = { showClearChat = false },
            onConfirm = {
                showClearChat = false
                viewModel.clearChat(chat.id, currentUser.id, settingsState.cloudChatEnabled)
            }
        )
    }

    if (showDeleteChat) {
        ConfirmChatAction(
            title = "Delete chat?",
            text = "This removes the chat locally for your account.",
            onDismiss = { showDeleteChat = false },
            onConfirm = {
                showDeleteChat = false
                viewModel.deleteChat(chat.id, currentUser.id, settingsState.cloudChatEnabled)
                onChatDeleted()
            }
        )
    }
}

@Composable
private fun ProfileAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    HelloPanel(
        modifier = Modifier
            .size(width = 118.dp, height = 88.dp)
            .clickable(onClick = onClick),
        strong = true,
        shape = HelloShapes.Md
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = HelloColors.Accent, modifier = Modifier.size(32.dp))
            Text(label, color = HelloColors.AccentStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable () -> Unit) {
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
        strong = true,
        shape = HelloShapes.Md
    ) {
        Column(modifier = Modifier.padding(HelloSpacing.Md), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            Text("$title ♡", color = HelloColors.AccentStrong, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    HelloPanel(
        modifier = Modifier
            .size(94.dp)
            .clickable(onClick = onClick),
        strong = false,
        shape = HelloShapes.Md
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(HelloSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = HelloColors.Accent, modifier = Modifier.size(30.dp))
            Text("$count", color = HelloColors.AccentStrong, fontWeight = FontWeight.Black)
            Text(label, color = HelloColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !muted, onClick = onClick)
            .padding(vertical = HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Icon(icon, contentDescription = label, tint = if (danger) HelloColors.DarkDanger else HelloColors.Accent)
        Text(
            text = if (muted) "$label (inside chat)" else label,
            color = if (danger) HelloColors.DarkDanger else if (muted) HelloColors.TextSecondary else HelloColors.Text,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConfirmChatAction(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Confirm", color = HelloColors.DarkDanger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HelloColors.DarkTextMuted) }
        }
    )
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun formatLastActive(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
