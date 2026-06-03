package com.glassbox.hello.chat.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.ui.components.DateSeparator
import com.glassbox.hello.ui.components.ScrollToBottomFab
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun rememberNearBottom(listState: androidx.compose.foundation.lazy.LazyListState): Boolean {
    val state by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    return state
}

@Composable
fun ChatMessageList(
    messages: List<ChatModels.Message>,
    currentUserId: String,
    chatIsGroup: Boolean,
    unreadCount: Int,
    typingNames: List<String>,
    context: Context,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    hasMoreOlderMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    onReply: (ChatModels.Message) -> Unit,
    onOpenMessageMenu: (ChatModels.Message) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit,
    outgoingBubbleColor: Color,
    incomingBubbleColor: Color,
    bubbleOpacity: Float,
    onJumpToLatest: () -> Unit
) {
    val isNearBottom = rememberNearBottom(listState)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = HelloSpacing.Md, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (hasMoreOlderMessages || isLoadingOlderMessages) {
                item(key = "older-loader") {
                    OlderMessagesRow(isLoading = isLoadingOlderMessages)
                }
            }
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val grouping = buildTimelineGrouping(messages, index, currentUserId, chatIsGroup, unreadCount)
                if (grouping.showDayDivider) {
                    DateDivider(label = grouping.dayLabel.orEmpty())
                }
                if (grouping.showUnreadDivider) {
                    UnreadDivider()
                }
                ChatMessageBubble(
                    message = message,
                    isOwn = message.senderId == currentUserId,
                    currentUserId = currentUserId,
                    context = context,
                    onReply = onReply,
                    onLongPress = onOpenMessageMenu,
                    onOpenAttachment = onOpenAttachment,
                    onOpenImage = onOpenImage,
                    onDownloadAttachment = onDownloadAttachment,
                    outgoingBubbleColor = outgoingBubbleColor,
                    incomingBubbleColor = incomingBubbleColor,
                    bubbleOpacity = bubbleOpacity,
                    showSenderName = grouping.showSenderName,
                    compactWithPrevious = grouping.compactWithPrevious,
                    compactWithNext = grouping.compactWithNext
                )
            }
            if (typingNames.isNotEmpty()) {
                item(key = "typing-indicator") {
                    TypingIndicatorBubble(names = typingNames)
                }
            }
        }

        AnimatedVisibility(
            visible = !isNearBottom && messages.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = HelloSpacing.Lg, bottom = HelloSpacing.Lg)
        ) {
            ScrollToBottomFab(
                visible = true,
                unreadBelow = unreadCount,
                onClick = onJumpToLatest
            )
        }
    }
}

@Composable
private fun OlderMessagesRow(isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HelloSpacing.Sm),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLoading) "Loading older messages..." else "Scroll up for older messages",
            color = HelloColors.DarkTextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(HelloShapes.Pill)
                .background(HelloColors.DarkPanelMuted)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DateDivider(label: String) {
    DateSeparator(
        label = label,
        modifier = Modifier.padding(vertical = HelloSpacing.Sm)
    )
}

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(HelloColors.DarkDanger.copy(alpha = 0.4f)))
        Text("Unread", color = HelloColors.DarkDanger, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(HelloColors.DarkDanger.copy(alpha = 0.4f)))
    }
}

@Composable
private fun TypingIndicatorBubble(names: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = HelloSpacing.Md)
                .clip(HelloShapes.MessageOther)
                .background(HelloColors.DarkPanelStrong)
                .border(1.dp, HelloColors.DarkBorderStrong, HelloShapes.MessageOther)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = names.joinToString(limit = 2, truncated = "others") + if (names.size == 1) " is typing" else " are typing",
                color = HelloColors.DarkTextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size((if (index == 1) 7 else 6).dp)
                    .clip(CircleShape)
                    .background(HelloColors.DarkTextMuted.copy(alpha = 0.75f))
            )
        }
    }
}
