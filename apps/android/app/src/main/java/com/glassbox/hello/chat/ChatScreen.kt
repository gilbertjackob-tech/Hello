package com.glassbox.hello.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glassbox.hello.attachments.SharedFilesScreen
import com.glassbox.hello.attachments.SharedLinksScreen
import com.glassbox.hello.attachments.SharedMediaScreen
import com.glassbox.hello.auth.AuthScreen
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.User
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.theme.HelloColors

@Composable
fun ChatScreen(
    sessionManager: SessionManager,
    logoutToken: Int = 0,
    callViewModel: CallViewModel,
    onChatRoomVisibilityChanged: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cloudSessionManager = remember { CloudSessionManager(context) }
    var currentUser by remember { mutableStateOf<User?>(sessionManager.getCurrentUser() ?: cloudSessionManager.cachedUser()) }
    var route by remember { mutableStateOf<ChatRoute>(ChatRoute.List) }

    LaunchedEffect(logoutToken) {
        currentUser = sessionManager.getCurrentUser() ?: cloudSessionManager.cachedUser()
        route = ChatRoute.List
    }

    LaunchedEffect(route) {
        onChatRoomVisibilityChanged(route !is ChatRoute.List)
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { authenticated ->
                sessionManager.saveCurrentUser(authenticated)
                cloudSessionManager.save(authenticated)
                currentUser = authenticated
                route = ChatRoute.List
            },
            modifier = modifier
        )
        return
    }

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
