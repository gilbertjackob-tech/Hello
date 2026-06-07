package com.glassbox.hello.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.CloudChatApi
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloFileCard
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun SharedMediaScreen(chatId: String? = null, modifier: Modifier = Modifier) {
    SharedScaffold(eyebrow = "HELLO MEDIA", title = "Shared media", modifier = modifier) {
        SharedAttachmentList(chatId = chatId, selector = { it.media }, emptyTitle = "No shared media", emptyMessage = "Photos and videos shared in Hello chats will appear here.")
    }
}

@Composable
fun SharedFilesScreen(chatId: String? = null, modifier: Modifier = Modifier) {
    SharedScaffold(eyebrow = "HELLO FILES", title = "Shared files", modifier = modifier) {
        SharedAttachmentList(chatId = chatId, selector = { it.files }, emptyTitle = "No shared files", emptyMessage = "Files shared in the selected chat will appear here.")
    }
}

@Composable
fun SharedLinksScreen(chatId: String? = null, modifier: Modifier = Modifier) {
    SharedScaffold(eyebrow = "HELLO LINKS", title = "Shared links", modifier = modifier) {
        SharedAttachmentList(chatId = chatId, selector = { it.links }, emptyTitle = "No shared links", emptyMessage = "Links shared in the selected chat will appear here.")
    }
}

@Composable
fun AttachmentPreview(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Md) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloIconButton(onClick = {}) {
                Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Column {
                Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(detail, color = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
private fun SharedAttachmentList(
    chatId: String?,
    selector: (ChatModels.ChatAttachments) -> List<ChatModels.AttachmentItem>,
    emptyTitle: String,
    emptyMessage: String
) {
    val context = LocalContext.current
    if (chatId.isNullOrBlank()) {
        HelloEmptyState(title = emptyTitle, message = "Open a chat first. $emptyMessage")
        return
    }

    val api = remember(context) { CloudChatApi(context) }
    var state by remember { mutableStateOf<ResultState<List<ChatModels.AttachmentItem>>>(ResultState.Loading) }

    LaunchedEffect(chatId) {
        val result = api.fetchMessages(chatId)
        state = if (result.isSuccess) {
            ResultState.Success(selector(attachmentsFromMessages(result.getOrNull().orEmpty())))
        } else {
            ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load shared content")
        }
    }

    when (val current = state) {
        is ResultState.Loading -> LoadingView()
        is ResultState.Error -> ErrorView(message = current.message)
        is ResultState.Success -> {
            if (current.data.isEmpty()) {
                HelloEmptyState(title = emptyTitle, message = emptyMessage)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = HelloSpacing.Xxl), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                    items(current.data) { item ->
                        HelloFileCard(title = item.fileName ?: item.url ?: "Shared item", detail = item.mimeType ?: item.text ?: "Shared content")
                    }
                }
            }
        }
    }
}

private fun attachmentsFromMessages(messages: List<ChatModels.Message>): ChatModels.ChatAttachments {
    val items = messages.mapNotNull { message ->
        val url = message.attachmentUrl ?: return@mapNotNull null
        ChatModels.AttachmentItem(
            id = message.id,
            messageId = message.id,
            fileName = message.attachmentName,
            mimeType = message.attachmentType,
            size = message.attachmentSize,
            url = url,
            text = message.text,
            senderId = message.senderId,
            senderName = message.senderName,
            createdAt = message.timestamp
        )
    }
    return ChatModels.ChatAttachments(
        media = items.filter { it.mimeType == "image" || it.mimeType?.startsWith("video/") == true },
        files = items.filter { it.mimeType != "image" && it.mimeType?.startsWith("video/") != true },
        links = messages
            .filter { it.text.startsWith("http://") || it.text.startsWith("https://") }
            .map {
                ChatModels.AttachmentItem(
                    id = it.id,
                    messageId = it.id,
                    url = it.text,
                    text = it.text,
                    senderId = it.senderId,
                    senderName = it.senderName,
                    createdAt = it.timestamp
                )
            }
    )
}

@Composable
private fun SharedScaffold(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg)
    ) {
        HelloTopBar(
            eyebrow = eyebrow,
            title = title,
            modifier = Modifier.padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Md)
        )
        content()
    }
}
