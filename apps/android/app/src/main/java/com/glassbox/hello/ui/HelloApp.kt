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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.glassbox.hello.activities.BrowserActivity
import com.glassbox.hello.auth.AuthScreen
import com.glassbox.hello.auth.CloudAuthApi
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.auth.CloudUserRepository
import com.glassbox.hello.ui.components.AppBackground
import com.glassbox.hello.calls.CallViewModel
import com.glassbox.hello.calls.CallUiStatus
import com.glassbox.hello.debug.HelloDebugLog
import com.glassbox.hello.familydrive.FamilyDriveScreen
import com.glassbox.hello.calls.GlobalCallOverlay
import com.glassbox.hello.chat.ChatScreen
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.User
import com.glassbox.hello.notifications.HelloNotificationCenter
import com.glassbox.hello.notifications.FcmPushRegistrar
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.settings.SettingsScreen
import com.glassbox.hello.ui.components.HelloBottomNav
import com.glassbox.hello.ui.components.HelloScreenBackground
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.HelloThemeRuntime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

private enum class MainTab(val label: String, val icon: ImageVector) {
    Chats("Chats", Icons.AutoMirrored.Filled.Chat),
    Drive("Drive", Icons.Default.Cloud),
    Browser("Browser", Icons.Default.Public),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun HelloApp(darkTheme: Boolean = true) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val appScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val cloudSessionManager = remember { CloudSessionManager(context) }
    val cloudUserRepository = remember { CloudUserRepository(context) }
    val cachedCloudUser = remember {
        cloudSessionManager.cachedUser()
            ?.takeIf { !cloudSessionManager.token().isNullOrBlank() }
    }
    val currentUser = remember {
        mutableStateOf<User?>(cachedCloudUser)
    }
    val resolvingCloudIdentity = remember {
        mutableStateOf(!cloudSessionManager.token().isNullOrBlank() && cachedCloudUser == null)
    }
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

    fun clearLocalAccountState() {
        HelloDebugLog.d("App", "clearLocalAccountState currentUser=${currentUser.value?.id} selectedTab=${selectedTab.intValue}")
        SocketManager.getInstance().disconnect()
        HelloNotificationCenter.resetForLogout(context)
        sessionManager.clearSession()
        cloudSessionManager.clear()
        currentUser.value = null
        resolvingCloudIdentity.value = false
        selectedTab.intValue = MainTab.Chats.ordinal
        tabBackStack.clear()
        isChatRoomVisible.value = false
        isSettingsDetailVisible.value = false
    }

    LaunchedEffect(logoutToken.intValue) {
        val token = cloudSessionManager.token()
        HelloDebugLog.d("App", "resolveCloudIdentity tokenPresent=${!token.isNullOrBlank()} logoutToken=${logoutToken.intValue}")
        if (token.isNullOrBlank()) {
            sessionManager.clearSession()
            currentUser.value = null
            resolvingCloudIdentity.value = false
            HelloDebugLog.d("App", "resolveCloudIdentity skipped reason=no_token")
            return@LaunchedEffect
        }
        resolvingCloudIdentity.value = currentUser.value == null
        val cloudUser = withTimeoutOrNull(8_000L) { cloudUserRepository.currentUser().getOrNull() }
        if (cloudUser == null) {
            HelloDebugLog.w("App", "resolveCloudIdentity failed reason=currentUser_null")
            if (currentUser.value == null) {
                clearLocalAccountState()
            }
            resolvingCloudIdentity.value = false
            return@LaunchedEffect
        }
        cloudSessionManager.save(cloudUser)
        sessionManager.saveCurrentUser(cloudUser)
        if (currentUser.value?.id != cloudUser.id || currentUser.value?.name != cloudUser.name) {
            currentUser.value = cloudUser
        }
        resolvingCloudIdentity.value = false
        HelloDebugLog.d("App", "resolveCloudIdentity success userId=${cloudUser.id} name=${cloudUser.name}")
    }

    if (resolvingCloudIdentity.value && !cloudSessionManager.token().isNullOrBlank()) {
        AppBackground(modifier = Modifier.fillMaxSize()) {
            LoadingView(modifier = Modifier.fillMaxSize())
        }
        return
    }

    if (currentUser.value == null) {
        AuthScreen(
            onAuthSuccess = { user ->
                HelloDebugLog.d("App", "authSuccess userId=${user.id} name=${user.name}")
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
    fun handleSessionRevoked() {
        appScope.launch {
            HelloDebugLog.w("App", "sessionRevoked activeUser=${currentUser.value?.id}")
            clearLocalAccountState()
            callViewModel.dismissCallOverlay()
        }
    }
    callViewModel.onSessionRevoked = { handleSessionRevoked() }
    SocketManager.getInstance().onSessionRevoked = { handleSessionRevoked() }
    val callState by callViewModel.state.collectAsState()
    val incomingCallLaunch by HelloNotificationCenter.incomingCallState.collectAsState()
    val bannerNotification by HelloNotificationCenter.bannerState.collectAsState()
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
        HelloDebugLog.d("Call", "acceptIncomingFromNotification callId=${signal.callId} isVideo=${signal.isVideo}")
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
            HelloDebugLog.d("App", "userReady userId=${it.id} initializing notifications and calls")
            HelloNotificationCenter.initialize(context, it.id)
            delay(1500)
            FcmPushRegistrar.registerPendingToken(context, "user_ready")
            FcmPushRegistrar.refreshAndRegister(context)
            callViewModel.connect(context, it)
        }
    }
    LaunchedEffect(incomingCallLaunch?.signal?.callId) {
        incomingCallLaunch?.let {
            HelloDebugLog.d("Call", "incomingCallLaunch callId=${it.signal.callId} action=${it.action}")
            callViewModel.showIncomingCall(it.signal)
        }
    }
    LaunchedEffect(incomingCallLaunch?.action, callState.signal?.callId) {
        val launch = incomingCallLaunch ?: return@LaunchedEffect
        val currentSignal = callState.signal ?: return@LaunchedEffect
        if (launch.signal.callId != currentSignal.callId) return@LaunchedEffect
        when (launch.action) {
            HelloNotificationCenter.CALL_ACTION_ACCEPT -> {
                HelloDebugLog.d("Call", "notificationAction accept callId=${launch.signal.callId}")
                acceptIncomingFromNotification()
                HelloNotificationCenter.consumeIncomingCallAction()
                HelloNotificationCenter.consumeIncomingCall(launch.signal.callId)
            }
            HelloNotificationCenter.CALL_ACTION_DECLINE -> {
                HelloDebugLog.d("Call", "notificationAction decline callId=${launch.signal.callId}")
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
    LaunchedEffect(bannerNotification?.chatId, bannerNotification?.body) {
        val active = bannerNotification ?: return@LaunchedEffect
        delay(4200)
        if (HelloNotificationCenter.bannerState.value == active) {
            HelloNotificationCenter.clearBanner()
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
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
                            currentUser = currentUser.value!!,
                            logoutToken = logoutToken.intValue,
                            callViewModel = callViewModel,
                            onChatRoomVisibilityChanged = { isChatRoomVisible.value = it },
                            onOpenSettings = { selectedTab.intValue = MainTab.Settings.ordinal },
                            modifier = Modifier.fillMaxSize()
                        )
                        MainTab.Drive -> FamilyDriveScreen(
                            currentUserId = currentUser.value!!.id,
                            modifier = Modifier.fillMaxSize()
                        )
                        MainTab.Browser -> Box(modifier = Modifier.fillMaxSize())
                        MainTab.Settings -> SettingsScreen(
                            sessionManager = sessionManager,
                            currentUser = currentUser.value,
                            onDetailVisibilityChanged = { isSettingsDetailVisible.value = it },
                            onLogout = {
                                val token = cloudSessionManager.token()
                                clearLocalAccountState()
                                callViewModel.dismissCallOverlay()
                                logoutToken.intValue += 1
                                if (!token.isNullOrBlank()) {
                                    appScope.launch {
                                        FcmPushRegistrar.unregisterRegisteredDevice(context, token, "logout")
                                        CloudAuthApi().logout(token)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (showBottomNav) {
                HelloBottomNav(
                    dark = darkTheme,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    MainTab.values().forEach { tab ->
                        val selected = selectedTab.intValue == tab.ordinal
                        val cute = HelloThemeRuntime.activePalette.value.id == "cute"
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
                                modifier = Modifier
                                    .scale(scale)
                                    .clip(RoundedCornerShape(if (cute) 24.dp else 1.dp))
                                    .background(if (cute && selected) HelloColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(
                                        if (cute && selected) 1.dp else 0.dp,
                                        if (cute && selected) HelloColors.BorderStrong else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(if (cute) 24.dp else 1.dp)
                                    )
                                    .padding(horizontal = if (cute) 12.dp else 0.dp, vertical = if (cute) 6.dp else 0.dp)
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
                                if (cute && selected) {
                                    Text("♡", color = HelloColors.Accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        InAppNotificationBanner(
            sender = bannerNotification?.senderName,
            body = bannerNotification?.body,
            onDismiss = { HelloNotificationCenter.clearBanner() }
        )
        GlobalCallOverlay(callViewModel = callViewModel, modifier = Modifier.fillMaxSize())
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun InAppNotificationBanner(
    sender: String?,
    body: String?,
    onDismiss: () -> Unit
) {
    val visible = !sender.isNullOrBlank() && !body.isNullOrBlank()
    if (!visible) return
    val cute = HelloThemeRuntime.activePalette.value.id == "cute"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (cute) 28.dp else 22.dp))
                    .background(
                        if (cute) HelloColors.PanelStrong.copy(alpha = 0.96f)
                        else HelloColors.DarkPanelStrong.copy(alpha = 0.95f)
                    )
                    .border(
                        1.dp,
                        if (cute) HelloColors.BorderStrong else HelloColors.GlassBorder,
                        RoundedCornerShape(if (cute) 28.dp else 22.dp)
                    )
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (cute) HelloColors.AccentSoft else HelloColors.Accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (cute) "Hello message" else "New message",
                            color = if (cute) HelloColors.AccentStrong else HelloColors.AccentStrong,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(modifier = Modifier.padding(top = 10.dp)) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (cute) HelloColors.AccentSoft else HelloColors.Accent.copy(alpha = 0.14f))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = HelloColors.AccentStrong)
                            }
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = sender.orEmpty(),
                                    color = HelloColors.DarkText,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = body.orEmpty(),
                                    color = HelloColors.DarkTextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
