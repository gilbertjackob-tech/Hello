package com.glassbox.hello.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.activities.BrowserActivity
import com.glassbox.hello.auth.AuthScreen
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.calls.CallsScreen
import com.glassbox.hello.calls.GlobalCallOverlay
import com.glassbox.hello.chat.ChatScreen
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.User
import com.glassbox.hello.settings.SettingsScreen
import com.glassbox.hello.status.StatusScreen
import com.glassbox.hello.ui.components.HelloBottomNav
import com.glassbox.hello.ui.components.HelloScreenBackground
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloSpacing
import androidx.compose.runtime.LaunchedEffect

private enum class MainTab(val label: String, val icon: ImageVector) {
    Chats("Chats", Icons.AutoMirrored.Filled.Chat),
    Calls("Calls", Icons.Default.Call),
    Status("Status", Icons.Default.Circle),
    Browser("Browser", Icons.Default.Public),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun HelloApp(darkTheme: Boolean = true) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val currentUser = remember { mutableStateOf<User?>(sessionManager.getCurrentUser()) }
    val selectedTab = remember { mutableIntStateOf(0) }
    val logoutToken = remember { mutableIntStateOf(0) }
    val isChatRoomVisible = remember { mutableStateOf(false) }
    val isSettingsDetailVisible = remember { mutableStateOf(false) }

    val showBottomNav = when (MainTab.values()[selectedTab.intValue]) {
        MainTab.Chats -> !isChatRoomVisible.value
        MainTab.Settings -> !isSettingsDetailVisible.value
        else -> true
    }

    if (currentUser.value == null) {
        AuthScreen(
            onAuthSuccess = { user ->
                sessionManager.saveCurrentUser(user)
                currentUser.value = user
                selectedTab.intValue = MainTab.Chats.ordinal
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val callViewModel: CallViewModel = viewModel(key = "hello-global-call")
    LaunchedEffect(currentUser.value?.id) {
        currentUser.value?.let { callViewModel.connect(it) }
    }

    HelloScreenBackground(modifier = Modifier.fillMaxSize(), dark = darkTheme) {
        Column(modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(bottom = HelloSpacing.Sm)
            ) {
                when (MainTab.values()[selectedTab.intValue]) {
                    MainTab.Chats -> ChatScreen(
                        sessionManager = sessionManager,
                        logoutToken = logoutToken.intValue,
                        callViewModel = callViewModel,
                        onChatRoomVisibilityChanged = { isChatRoomVisible.value = it },
                        onOpenSettings = { selectedTab.intValue = MainTab.Settings.ordinal },
                        modifier = Modifier.fillMaxSize()
                    )
                    MainTab.Calls -> CallsScreen(
                        currentUserId = currentUser.value!!.id,
                        callViewModel = callViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    MainTab.Status -> StatusScreen(currentUserId = currentUser.value!!.id, modifier = Modifier.fillMaxSize())
                    MainTab.Browser -> Box(modifier = Modifier.fillMaxSize())
                    MainTab.Settings -> SettingsScreen(
                        sessionManager = sessionManager,
                        onDetailVisibilityChanged = { isSettingsDetailVisible.value = it },
                        onLogout = {
                            sessionManager.clearSession()
                            currentUser.value = null
                            logoutToken.intValue += 1
                            selectedTab.intValue = MainTab.Chats.ordinal
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (showBottomNav) {
                HelloBottomNav(dark = darkTheme) {
                    MainTab.values().forEach { tab ->
                        val selected = selectedTab.intValue == tab.ordinal
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (tab == MainTab.Browser) {
                                    context.startActivity(BrowserActivity.createIntent(context))
                                } else {
                                    selectedTab.intValue = tab.ordinal
                                    isChatRoomVisible.value = false
                                    if (tab != MainTab.Settings) {
                                        isSettingsDetailVisible.value = false
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) HelloColors.DarkAccent else HelloColors.DarkTextMuted
                                )
                                Text(
                                    text = tab.label,
                                    color = if (selected) HelloColors.DarkAccent else HelloColors.DarkTextMuted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        GlobalCallOverlay(callViewModel = callViewModel, modifier = Modifier.fillMaxSize())
    }
}
