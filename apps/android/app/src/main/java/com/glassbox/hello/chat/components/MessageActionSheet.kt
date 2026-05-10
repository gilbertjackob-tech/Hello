package com.glassbox.hello.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun ChatActionSheet(
    onContactInfo: () -> Unit,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onLinks: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        Text("Chat actions", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SheetRow("Contact info", Icons.Default.Person, onContactInfo)
        SheetRow("Shared media", Icons.Default.Image, onMedia)
        SheetRow("Shared files", Icons.Default.Description, onFiles)
        SheetRow("Shared links", Icons.Default.Link, onLinks)
        SheetRow("Clear chat locally", Icons.Default.Delete, onClearChat, danger = true)
        SheetRow("Delete chat locally", Icons.Default.Delete, onDeleteChat, danger = true)
    }
}

@Composable
fun MessageActionSheet(
    message: ChatModels.Message,
    currentUserId: String,
    isOwn: Boolean,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        Text("Message options", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HelloSpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickReactions.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .background(HelloColors.DarkPanelMuted, CircleShape)
                        .clickable { onReact(emoji) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
        SheetRow("Reply", Icons.AutoMirrored.Filled.ArrowBack, onReply)
        SheetRow(if (message.starredBy.orEmpty().contains(currentUserId)) "Unstar" else "Star", Icons.Default.Star, onStar)
        SheetRow(if ((message.pinnedUntil ?: 0L) > System.currentTimeMillis()) "Unpin" else "Pin", Icons.Default.PushPin, onPin)
        SheetRow("Copy text", Icons.Default.ContentCopy, onCopy)
        if (!message.attachmentUrl.isNullOrBlank()) {
            SheetRow("Open attachment", Icons.Default.Folder, onOpen)
        }
        SheetRow("Delete for me", Icons.Default.Delete, onDeleteForMe, danger = true)
        if (isOwn && message.isDeleted != true) {
            SheetRow("Delete for everyone", Icons.Default.Delete, onDeleteForEveryone, danger = true)
        }
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HelloSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Icon(icon, contentDescription = label, tint = if (danger) HelloColors.DarkDanger else HelloColors.DarkTextMuted)
        Text(label, color = if (danger) HelloColors.DarkDanger else HelloColors.DarkText, fontWeight = FontWeight.Medium)
    }
}
