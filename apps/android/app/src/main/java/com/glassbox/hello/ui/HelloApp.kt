package com.glassbox.hello.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.glassbox.hello.activities.BrowserActivity
import com.glassbox.hello.auth.AuthScreen
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.ui.components.AppBackground
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.calls.CallUiStatus
import com.glassbox.hello.familydrive.FamilyDriveScreen
import com.glassbox.hello.calls.GlobalCallOverlay
import com.glassbox.hello.chat.ChatScreen
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.User
import com.glassbox.hello.notifications.HelloNotificationCenter
import com.glassbox.hello.notifications.FcmPushRegistrar
import com.glassbox.hello.settings.SettingsScreen
import com.glassbox.hello.status.StatusScreen
import com.glassbox.hello.ui.components.HelloBottomNav
import com.glassbox.hello.ui.components.HelloScreenBackground
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloSpacing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class MainTab(val label: String, val icon: ImageVector) {
    Chats("Chats", Icons.AutoMirrored.Filled.Chat),
    Drive("Drive", Icons.Default.Cloud),
    Status("Status", Icons.Default.Circle),
    Browser("Browser", Icons.Default.Public),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun HelloApp(darkTheme: Boolean = true) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val sessionManager = remember { SessionManager(context) }
    val cloudSessionManager = remember { CloudSessionManager(context) }
    val currentUser = remember { mutableStateOf<User?>(sessionManager.getCurrentUser() ?: cloudSessionManager.cachedUser()) }
    val selectedTab = remember { mutableIntStateOf(0) }
    val tabBackStack = remember { mutableStateListOf<Int>() }
    val lastExitBackAt = remember { mutableStateOf(0L) }
    val logoutToken = remember { mutableIntStateOf(0) }
    val isChatRoomVisible = remember { mutableStateOf(false) }
    val isSettingsDetailVisible = remember { mutableStateOf(false) }

    val showBottomNav = when (MainTab.values()[selectedTab.intValue]) {
        MainTab.Chats -> !isChatRoomVisible.value
        MainTab.Settings -> !isSettingsDetailVisible.value
        else -> true
    }

    fun selectMainTab(tab: MainTab) {
        val current = selectedTab.intValue
        if (tab.ordinal == current) return
        tabBackStack.add(current)
        selectedTab.intValue = tab.ordinal
        isChatRoomVisible.value = false
        if (tab != MainTab.Settings) {
            isSettingsDetailVisible.value = false
        }
    }

    if (currentUser.value == null) {
        AuthScreen(
            onAuthSuccess = { user ->
                sessionManager.saveCurrentUser(user)
                cloudSessionManager.save(user)
                currentUser.value = user
                selectedTab.intValue = MainTab.Chats.ordinal
                tabBackStack.clear()
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    BackHandler {
        if (tabBackStack.isNotEmpty()) {
            selectedTab.intValue = tabBackStack.removeAt(tabBackStack.lastIndex)
            isChatRoomVisible.value = false
            isSettingsDetailVisible.value = false
            return@BackHandler
        }

        if (selectedTab.intValue != MainTab.Chats.ordinal) {
            selectedTab.intValue = MainTab.Chats.ordinal
            isChatRoomVisible.value = false
            isSettingsDetailVisible.value = false
            return@BackHandler
        }

        val now = System.currentTimeMillis()
        if (now - lastExitBackAt.value < 1800L) {
            activity?.finish()
        } else {
            lastExitBackAt.value = now
            Toast.makeText(context, "Tap back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    val callViewModel: CallViewModel = viewModel(key = "hello-global-call")
    val callState by callViewModel.state.collectAsState()
    val incomingCallLaunch by HelloNotificationCenter.incomingCallState.collectAsState()
    var pendingIncomingAccept by remember { mutableStateOf(false) }
    val incomingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (pendingIncomingAccept) {
            pendingIncomingAccept = false
            val signal = callState.signal
            if (signal != null) {
                val needsCamera = signal.isVideo
                val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val hasCamera = !needsCamera || grants[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasAudio && hasCamera) {
                    callViewModel.acceptIncoming(context)
                } else if (hasAudio && needsCamera) {
                    callViewModel.acceptIncoming(context, forceAudio = true)
                }
            }
        }
    }

    fun acceptIncomingFromNotification() {
        val signal = callState.signal ?: return
        val needsCamera = signal.isVideo
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = !needsCamera || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasAudio && hasCamera) {
            callViewModel.acceptIncoming(context)
            return
        }
        pendingIncomingAccept = true
        incomingPermissionLauncher.launch(
            if (needsCamera) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
        )
    }

    LaunchedEffect(currentUser.value?.id) {
        currentUser.value?.let {
            HelloNotificationCenter.initialize(context, it.id)
            FcmPushRegistrar.refreshAndRegister(context)
            callViewModel.connect(context, it)
        }
    }
    LaunchedEffect(incomingCallLaunch?.signal?.callId) {
        incomingCallLaunch?.let { callViewModel.showIncomingCall(it.signal) }
    }
    LaunchedEffect(incomingCallLaunch?.action, callState.signal?.callId) {
        val launch = incomingCallLaunch ?: return@LaunchedEffect
        val currentSignal = callState.signal ?: return@LaunchedEffect
        if (launch.signal.callId != currentSignal.callId) return@LaunchedEffect
        when (launch.action) {
            HelloNotificationCenter.CALL_ACTION_ACCEPT -> {
                acceptIncomingFromNotification()
                HelloNotificationCenter.consumeIncomingCallAction()
                HelloNotificationCenter.consumeIncomingCall(launch.signal.callId)
            }
            HelloNotificationCenter.CALL_ACTION_DECLINE -> {
                callViewModel.declineCall()
                HelloNotificationCenter.consumeIncomingCallAction()
                HelloNotificationCenter.consumeIncomingCall(launch.signal.callId)
            }
        }
    }
    LaunchedEffect(callState.status, callState.signal?.callId) {
        val launch = incomingCallLaunch ?: return@LaunchedEffect
        if (callState.signal?.callId == launch.signal.callId && callState.status != CallUiStatus.Incoming) {
            HelloNotificationCenter.consumeIncomingCall(launch.signal.callId)
            HelloNotificationCenter.cancelCallNotifications(context, launch.signal.callId)
        }
    }
    LaunchedEffect(callState.status, callState.signal?.callId, callState.activeRoom?.id) {
        if (callState.status != CallUiStatus.Incoming) {
            HelloNotificationCenter.cancelCallNotifications(
                context,
                callState.signal?.callId ?: callState.activeRoom?.id
            )
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(bottom = HelloSpacing.Sm)
            ) {
                Crossfade(
                    targetState = MainTab.values()[selectedTab.intValue],
                    animationSpec = tween(180),
                    label = "mainTabCrossfade"
                ) { tab ->
                    when (tab) {
                        MainTab.Chats -> ChatScreen(
                            sessionManager = sessionManager,
                            logoutToken = logoutToken.intValue,
                            callViewModel = callViewModel,
                            onChatRoomVisibilityChanged = { isChatRoomVisible.value = it },
                            onOpenSettings = { selectedTab.intValue = MainTab.Settings.ordinal },
                            onOpenStories = { selectMainTab(MainTab.Status) },
                            modifier = Modifier.fillMaxSize()
                        )
                        MainTab.Drive -> FamilyDriveScreen(
                            currentUserId = currentUser.value!!.id,
                            modifier = Modifier.fillMaxSize()
                        )
                        MainTab.Status -> StatusScreen(currentUserId = currentUser.value!!.id, modifier = Modifier.fillMaxSize())
                        MainTab.Browser -> Box(modifier = Modifier.fillMaxSize())
                        MainTab.Settings -> SettingsScreen(
                            sessionManager = sessionManager,
                            onDetailVisibilityChanged = { isSettingsDetailVisible.value = it },
                            onLogout = {
                                sessionManager.clearSession()
                                cloudSessionManager.clear()
                                currentUser.value = null
                                logoutToken.intValue += 1
                                selectedTab.intValue = MainTab.Chats.ordinal
                                tabBackStack.clear()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (showBottomNav) {
                HelloBottomNav(dark = darkTheme) {
                    MainTab.values().forEach { tab ->
                        val selected = selectedTab.intValue == tab.ordinal
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = tween(160),
                            label = "bottomNavScale${tab.name}"
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (tab == MainTab.Browser) {
                                    context.startActivity(BrowserActivity.createIntent(context))
                                } else {
                                    selectMainTab(tab)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.scale(scale)
                            ) {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
