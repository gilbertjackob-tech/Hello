package com.glassbox.hello.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glassbox.hello.attachments.SharedFilesScreen
import com.glassbox.hello.attachments.SharedLinksScreen
import com.glassbox.hello.attachments.SharedMediaScreen
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.core.User
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.theme.HelloColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ChatScreen(
    currentUser: User,
    logoutToken: Int = 0,
    callViewModel: CallViewModel,
    onChatRoomVisibilityChanged: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var route by remember { mutableStateOf<ChatRoute>(ChatRoute.List) }

    LaunchedEffect(currentUser.id, logoutToken) {
        route = ChatRoute.List
    }

    LaunchedEffect(route) {
        onChatRoomVisibilityChanged(route !is ChatRoute.List)
    }

    BackHandler(enabled = route !is ChatRoute.List) {
        route = when (val currentRoute = route) {
            ChatRoute.List -> ChatRoute.List
            is ChatRoute.Room -> ChatRoute.List
            is ChatRoute.ContactInfo -> ChatRoute.Room(currentRoute.chat)
            is ChatRoute.SharedContent -> ChatRoute.ContactInfo(currentRoute.chat)
        }
    }

    val user = currentUser

    key(chatRouteKey(route)) {
        when (val currentRoute = route) {
            ChatRoute.List -> {
                ChatListScreen(
                    currentUserId = user.id,
                    currentUserName = user.name,
                    onOpenSettings = onOpenSettings,
                    onChatSelected = { chat -> route = ChatRoute.Room(chat) },
                    modifier = modifier
                )
            }

            is ChatRoute.Room -> {
                ChatRoomScreen(
                    chat = currentRoute.chat,
                    currentUserId = user.id,
                    currentUserName = user.name,
                    currentUserAvatar = user.avatar,
                    callViewModel = callViewModel,
                    onBack = { route = ChatRoute.List },
                    onOpenContactInfo = { route = ChatRoute.ContactInfo(currentRoute.chat) },
                    onOpenSharedContent = { mode -> route = ChatRoute.SharedContent(currentRoute.chat, mode) },
                    onChatDeleted = { route = ChatRoute.List },
                    modifier = modifier
                )
            }

            is ChatRoute.ContactInfo -> {
                ContactInfoScreen(
                    chat = currentRoute.chat,
                    currentUser = user,
                    callViewModel = callViewModel,
                    onBack = { route = ChatRoute.Room(currentRoute.chat) },
                    onOpenSharedContent = { mode -> route = ChatRoute.SharedContent(currentRoute.chat, mode) },
                    onChatDeleted = { route = ChatRoute.List },
                    modifier = modifier
                )
            }

            is ChatRoute.SharedContent -> {
                Box(modifier = modifier.fillMaxSize()) {
                    when (currentRoute.mode) {
                        ChatSharedContentMode.Media -> SharedMediaScreen(chatId = currentRoute.chat.id, modifier = Modifier.fillMaxSize())
                        ChatSharedContentMode.Files -> SharedFilesScreen(chatId = currentRoute.chat.id, modifier = Modifier.fillMaxSize())
                        ChatSharedContentMode.Links -> SharedLinksScreen(chatId = currentRoute.chat.id, modifier = Modifier.fillMaxSize())
                    }
                    HelloIconButton(
                        onClick = { route = ChatRoute.ContactInfo(currentRoute.chat) },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkAccent)
                    }
                }
            }
        }
    }
}

private fun chatRouteKey(route: ChatRoute): String = when (route) {
    ChatRoute.List -> "list"
    is ChatRoute.Room -> "room:${route.chat.id}"
    is ChatRoute.ContactInfo -> "contact:${route.chat.id}"
    is ChatRoute.SharedContent -> "shared:${route.chat.id}:${route.mode.name}"
}
