package com.glassbox.hello.chat.components
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.ui.components.DateSeparator
import com.glassbox.hello.ui.components.ScrollToBottomFab
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val CHAT_ROW_RESTORE_SETTLE_MS = 250L

@Composable
fun rememberNearBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int = listState.layoutInfo.totalItemsCount
): Boolean {
    val state by remember(itemCount) {
        derivedStateOf {
            itemCount > 0 && listState.firstVisibleItemIndex >= (itemCount - 8).coerceAtLeast(0)
        }
    }
    return state
}

@Composable
fun ChatMessageList(
    rows: List<ChatRenderRow>,
    currentUserId: String,
    unreadCount: Int,
    typingNames: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    hasMoreOlderMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    onOpenMessageMenu: (ChatModels.Message) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    onDownloadAttachment: (String, String?) -> Unit,
    bubbleOpacity: Float,
    onJumpToLatest: () -> Unit
) {
    val extraRowCount = if (hasMoreOlderMessages || isLoadingOlderMessages) 1 else 0
    val isNearBottom = rememberNearBottom(listState, rows.size + extraRowCount)
    var deferExpensiveRowContent by remember(listState) {
        mutableStateOf(listState.isScrollInProgress)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (isScrolling) {
                    deferExpensiveRowContent = true
                } else {
                    // A new fling cancels this restoration instead of rebuilding rows between gestures.
                    delay(CHAT_ROW_RESTORE_SETTLE_MS)
                    deferExpensiveRowContent = false
                }
            }
    }
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
            items(
                items = rows,
                key = { row -> row.key },
                contentType = { row ->
                    row.contentType
                }
            ) { row ->
                if (row.grouping.showDayDivider) {
                    DateDivider(label = row.grouping.dayLabel.orEmpty())
                }
                if (row.showUnreadDivider) {
                    UnreadDivider()
                }
                ChatMessageBubble(
                    message = row.message,
                    isOwn = row.message.senderId == currentUserId,
                    currentUserId = currentUserId,
                    onLongPress = onOpenMessageMenu,
                    onOpenAttachment = onOpenAttachment,
                    onOpenImage = onOpenImage,
                    onDownloadAttachment = onDownloadAttachment,
                    bubbleOpacity = bubbleOpacity,
                    imageCluster = row.imageCluster,
                    timestampLabel = row.timestampLabel,
                    reactionSummary = row.reactionSummary,
                    lightweightMedia = deferExpensiveRowContent,
                    scrollInProgress = deferExpensiveRowContent,
                    showSenderName = row.grouping.showSenderName,
                    compactWithPrevious = row.grouping.compactWithPrevious,
                    compactWithNext = row.grouping.compactWithNext
                )
            }
        }

        if (typingNames.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = HelloSpacing.Md, bottom = HelloSpacing.Lg)
            ) {
                TypingIndicatorBubble(names = typingNames)
            }
        }
        if (!isNearBottom && rows.isNotEmpty()) {
            Box(
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
        modifier = Modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
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
