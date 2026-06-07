package com.glassbox.hello.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.attachments.SharedFilesScreen
import com.glassbox.hello.attachments.SharedLinksScreen
import com.glassbox.hello.attachments.SharedMediaScreen
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.core.HelloPreferences
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User as CoreUser
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.networkstatus.NetworkStatus
import com.glassbox.hello.networkstatus.checkCloudChatNetwork
import com.glassbox.hello.networkstatus.checkHelloNetwork
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.AppBackground
import com.glassbox.hello.ui.components.FilterChip
import com.glassbox.hello.ui.components.GlassSearchBar
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloChatCard
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloFilterChip
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.components.HelloTextField
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.components.ShimmerChatCard
import com.glassbox.hello.ui.components.StatusPill
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloDimens
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChatFilter(val label: String) {
    All("All"),
    Unread("Unread"),
    Groups("Groups"),
    Calls("Calls"),
    Files("Files"),
    Pinned("Pinned")
}

private const val INBOX_DEBUG_TAG = "HelloInbox"

@Composable
fun ChatListScreen(
    currentUserId: String,
    currentUserName: String,
    onChatSelected: (Chat) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(key = "chat-list-$currentUserId")
    val chatsState by viewModel.chatsState.collectAsState()
    val chatsRefreshing by viewModel.chatsRefreshing.collectAsState()
    val usersState by viewModel.usersState.collectAsState()
    val createChatState by viewModel.createChatState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val settingsState by rememberHelloSettingsState(context)
    val cloudChatEnabled = settingsState.cloudChatEnabled
    val cloudSessionManager = remember { CloudSessionManager(context) }
    val cloudSessionUser = cloudSessionManager.cachedUser()
    val cloudTokenPresent = !cloudSessionManager.token().isNullOrBlank()
    val socketManager = remember { SocketManager.getInstance() }

    var showNewChat by remember { mutableStateOf(false) }
    var showGroupChat by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showNetworkDiagnostics by remember { mutableStateOf(false) }
    var showSharedContentChooser by remember { mutableStateOf(false) }
    var sharedContentMode by remember { mutableStateOf<SharedContentMode?>(null) }
    var userSearchQuery by remember { mutableStateOf("") }
    var groupSearchQuery by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChatFilter.All) }
    var vpnEnabled by remember { mutableStateOf(false) }
    var vpnChecking by remember { mutableStateOf(false) }
    var vpnDetail by remember { mutableStateOf("PC Drive not checked yet") }
    var cloudChatOnline by remember { mutableStateOf(false) }
    var cloudChatDetail by remember { mutableStateOf("Cloud Chat not checked yet") }

    LaunchedEffect(currentUserId, cloudChatEnabled) {
        Log.d(
            INBOX_DEBUG_TAG,
            "screen_init userId=$currentUserId userName=$currentUserName cloudChatEnabled=$cloudChatEnabled cloudTokenPresent=$cloudTokenPresent cloudSessionUserId=${cloudSessionUser?.id.orEmpty()} cloudSessionUserName=${cloudSessionUser?.name.orEmpty()}"
        )
        viewModel.configureCloudChat(context)
        viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
        vpnChecking = true
        val cloudProbe = withContext(Dispatchers.IO) { checkCloudChatNetwork() }
        val pcProbe = withContext(Dispatchers.IO) { checkHelloNetwork() }
        cloudChatOnline = cloudProbe.status == NetworkStatus.Connected || cloudProbe.status == NetworkStatus.HelloApiReachable
        cloudChatDetail = cloudProbe.detail
        vpnEnabled = pcProbe.status == NetworkStatus.Connected || pcProbe.status == NetworkStatus.HelloApiReachable
        vpnDetail = pcProbe.detail
        vpnChecking = false
    }

    DisposableEffect(currentUserId, currentUserName, cloudChatEnabled) {
        val chatListener: (Chat) -> Unit = { chat ->
            Log.d(
                INBOX_DEBUG_TAG,
                "socket_chat_updated currentUserId=$currentUserId chatId=${chat.id} directKey=${chat.directKey.orEmpty()} memberCount=${chat.memberIds().size} lastMessage=${chat.lastMessage.orEmpty().take(60)}"
            )
            viewModel.upsertChatFromSocket(chat, currentUserId)
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
        }
        val messageListener: (com.glassbox.hello.chat.ChatModels.Message) -> Unit = { message ->
            Log.d(
                INBOX_DEBUG_TAG,
                "socket_message currentUserId=$currentUserId chatId=${message.chatId} senderId=${message.senderId} text=${message.text.take(60)}"
            )
            viewModel.appendFromSocket(message, currentUserId = currentUserId)
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
        }
        val presenceListener: (org.json.JSONObject) -> Unit = presenceListener@{
            val payloadUserId = it.optString("userId").ifBlank { it.optString("id") }
            if (payloadUserId == currentUserId) {
                Log.d(INBOX_DEBUG_TAG, "socket_presence_ignored_self currentUserId=$currentUserId payload=$it")
                return@presenceListener
            }
            Log.d(INBOX_DEBUG_TAG, "socket_presence currentUserId=$currentUserId payload=$it")
            viewModel.applyPresenceUpdate(it)
        }
        socketManager.addChatUpdateListener(chatListener)
        socketManager.addMessageListener(messageListener)
        socketManager.addPresenceListener(presenceListener)
        socketManager.connect(context, CoreUser(id = currentUserId, name = currentUserName))
        onDispose {
            socketManager.removeChatUpdateListener(chatListener)
            socketManager.removeMessageListener(messageListener)
            socketManager.removePresenceListener(presenceListener)
        }
    }

    LaunchedEffect(showNewChat, showGroupChat, userSearchQuery, groupSearchQuery) {
        when {
            showNewChat -> {
                Log.d(
                    INBOX_DEBUG_TAG,
                    "open_new_chat currentUserId=$currentUserId query=$userSearchQuery cloudChatEnabled=$cloudChatEnabled cloudTokenPresent=$cloudTokenPresent"
                )
                viewModel.loadUsers(currentUserId, userSearchQuery, cloudChatEnabled = cloudChatEnabled)
            }
            showGroupChat -> {
                Log.d(
                    INBOX_DEBUG_TAG,
                    "open_group_chat currentUserId=$currentUserId query=$groupSearchQuery cloudChatEnabled=$cloudChatEnabled cloudTokenPresent=$cloudTokenPresent"
                )
                viewModel.loadUsers(currentUserId, groupSearchQuery, cloudChatEnabled = cloudChatEnabled)
            }
        }
    }

    LaunchedEffect(createChatState) {
        val state = createChatState
        if (state is ResultState.Success) {
            showNewChat = false
            showGroupChat = false
            viewModel.resetCreateChatState()
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
            onChatSelected(state.data)
        }
    }

    fun refreshNetworkStatus() {
        vpnChecking = true
        coroutineScope.launch {
            val cloudProbe = withContext(Dispatchers.IO) { checkCloudChatNetwork() }
            val pcProbe = withContext(Dispatchers.IO) { checkHelloNetwork() }
            cloudChatOnline = cloudProbe.status == NetworkStatus.Connected || cloudProbe.status == NetworkStatus.HelloApiReachable
            cloudChatDetail = cloudProbe.detail
            vpnEnabled = pcProbe.status == NetworkStatus.Connected || pcProbe.status == NetworkStatus.HelloApiReachable
            vpnDetail = pcProbe.detail
            vpnChecking = false
            if (showNetworkDiagnostics) {
                showNetworkDiagnostics = true
            }
        }
    }

    fun openNetworkDiagnostics() {
        showNetworkDiagnostics = true
        refreshNetworkStatus()
    }

    AppBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // ─ Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HelloDimens.SpaceL, vertical = HelloDimens.SpaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hello",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = HelloColors.AccentStrong
                    )
                    Text(
                        text = "Inbox ♡",
                        fontSize = 18.sp,
                        color = HelloColors.TealPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                ServiceHealthPills(
                    checking = vpnChecking,
                    cloudOnline = cloudChatOnline,
                    pcOnline = vpnEnabled
                )

                Spacer(modifier = Modifier.width(HelloDimens.SpaceS))

                HelloIconButton(onClick = { showNewChat = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = HelloColors.TextSecondary)
                }
                HelloIconButton(onClick = {
                    refreshNetworkStatus()
                    viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = HelloColors.TextSecondary)
                }
                Box {
                    HelloIconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = HelloColors.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New chat") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showNewChat = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create group chat") },
                            leadingIcon = { Icon(Icons.Default.People, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showGroupChat = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Network diagnostics") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                openNetworkDiagnostics()
                            }
                        )
                    }
                }
            }

            // ─ Search
            GlassSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search people, groups, or messages",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HelloDimens.SpaceL)
            )

            Spacer(Modifier.height(HelloDimens.SpaceM))

            if (!cloudChatEnabled || !cloudTokenPresent) {
                CloudChatStatusBanner(
                    cloudChatEnabled = cloudChatEnabled,
                    cloudSessionUserName = cloudSessionUser?.name,
                    onEnableSync = { HelloPreferences.setCloudChatEnabled(context, true) },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HelloDimens.SpaceL)
                )

                Spacer(Modifier.height(HelloDimens.SpaceM))
            }

            // ─ Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = HelloDimens.SpaceL),
                horizontalArrangement = Arrangement.spacedBy(HelloDimens.SpaceS)
            ) {
                items(ChatFilter.values()) { filter ->
                    FilterChip(
                        label = filter.label,
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Spacer(Modifier.height(HelloDimens.SpaceM))

            // ─ Chat list
            when (chatsState) {
                is ResultState.Loading -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = HelloDimens.SpaceL,
                        end = HelloDimens.SpaceL,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(HelloDimens.SpaceS)
                ) {
                    items(6) { ShimmerChatCard() }
                }
                is ResultState.Error -> ErrorView(
                    message = (chatsState as ResultState.Error).message,
                    onRetry = { viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled) },
                    modifier = Modifier.weight(1f).padding(horizontal = HelloDimens.SpaceL)
                )
                is ResultState.Success -> {
                    val chats = (chatsState as ResultState.Success<List<Chat>>)
                        .data
                        .dedupeDirectChats(currentUserId)
                        .sortedByDescending { it.lastMessageTime ?: 0L }
                        .filter { chat ->
                            val title = chat.displayName(currentUserId)
                            val preview = chat.lastMessage.orEmpty()
                            val matchesSearch = searchQuery.isBlank() ||
                                title.contains(searchQuery, ignoreCase = true) ||
                                preview.contains(searchQuery, ignoreCase = true)
                            val matchesFilter = when (selectedFilter) {
                                ChatFilter.All -> true
                                ChatFilter.Unread -> (chat.unreadCount ?: 0) > 0
                                ChatFilter.Groups -> chat.isGroup
                                ChatFilter.Calls -> false
                                ChatFilter.Files -> chat.lastMessage?.contains("file", ignoreCase = true) == true
                                ChatFilter.Pinned -> false
                            }
                            matchesSearch && matchesFilter
                        }

                    if (chats.isEmpty()) {
                        HelloEmptyState(
                            title = "No messages yet",
                            message = "Your Hello inbox is clear. Start a chat and say hi to someone from the + button.",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = HelloDimens.SpaceL),
                            action = {
                                HelloPrimaryButton(
                                    text = "Say hi",
                                    onClick = { showNewChat = true },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = HelloDimens.SpaceL,
                                end = HelloDimens.SpaceL,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(HelloDimens.SpaceS)
                        ) {
                            items(chats, key = { it.id }) { chat ->
                                val title = chat.displayName(currentUserId)
                                val other = chat.otherParticipant(currentUserId)
                                HelloChatCard(
                                    title = title,
                                    subtitle = chat.lastMessage ?: chat.presenceSubtitle(currentUserId),
                                    time = chat.lastMessageTime?.let {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                                    }.orEmpty(),
                                    unreadCount = chat.unreadCount ?: 0,
                                    modifier = Modifier.clickable { onChatSelected(chat) },
                                    active = (chat.unreadCount ?: 0) > 0,
                                    avatarUrl = other?.avatar ?: chat.avatar,
                                    online = other?.online == true
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNewChat) {
            NewChatDialog(
                query = userSearchQuery,
                onQueryChange = { userSearchQuery = it },
                usersState = usersState,
                createChatState = createChatState,
                onDismiss = {
                    showNewChat = false
                    userSearchQuery = ""
                    viewModel.resetCreateChatState()
                },
                onUserSelected = { user ->
                    viewModel.startDirectChat(currentUserId, currentUserName, user.id, user.name, cloudChatEnabled)
                }
            )
        }

        if (showGroupChat) {
            GroupChatDialog(
                groupName = groupName,
                onGroupNameChange = { groupName = it },
                searchQuery = groupSearchQuery,
                onSearchQueryChange = { groupSearchQuery = it },
                usersState = usersState,
                selectedMemberIds = selectedMemberIds,
                onToggleMember = { userId ->
                    selectedMemberIds = if (selectedMemberIds.contains(userId)) {
                        selectedMemberIds - userId
                    } else {
                        selectedMemberIds + userId
                    }
                },
                createChatState = createChatState,
                onDismiss = {
                    showGroupChat = false
                    groupName = ""
                    groupSearchQuery = ""
                    selectedMemberIds = emptySet()
                    viewModel.resetCreateChatState()
                },
                onCreate = {
                    viewModel.createGroupChat(currentUserId, currentUserName, groupName.trim(), selectedMemberIds.toList(), cloudChatEnabled)
                }
            )
        }

        if (showNetworkDiagnostics) {
            NetworkDiagnosticsDialog(
                checking = vpnChecking,
                enabled = vpnEnabled,
                detail = vpnDetail,
                cloudDetail = cloudChatDetail,
                onRetry = { refreshNetworkStatus() },
                onDismiss = { showNetworkDiagnostics = false }
            )
        }

        if (showSharedContentChooser) {
            AlertDialog(
                onDismissRequest = { showSharedContentChooser = false },
                containerColor = HelloColors.DarkPanelStrong,
                title = { Text("Shared content", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                        TextButton(onClick = {
                            sharedContentMode = SharedContentMode.Media
                            showSharedContentChooser = false
                        }) { Text("Shared media", color = HelloColors.DarkAccent) }
                        TextButton(onClick = {
                            sharedContentMode = SharedContentMode.Files
                            showSharedContentChooser = false
                        }) { Text("Shared files", color = HelloColors.DarkAccent) }
                        TextButton(onClick = {
                            sharedContentMode = SharedContentMode.Links
                            showSharedContentChooser = false
                        }) { Text("Shared links", color = HelloColors.DarkAccent) }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSharedContentChooser = false }) {
                        Text("Cancel", color = HelloColors.DarkTextMuted)
                    }
                }
            )
        }

        if (sharedContentMode != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (sharedContentMode) {
                    SharedContentMode.Media -> SharedMediaScreen(modifier = Modifier.fillMaxSize())
                    SharedContentMode.Files -> SharedFilesScreen(modifier = Modifier.fillMaxSize())
                    SharedContentMode.Links -> SharedLinksScreen(modifier = Modifier.fillMaxSize())
                    null -> Unit
                }
                HelloIconButton(
                    onClick = { sharedContentMode = null },
                    modifier = Modifier.padding(HelloSpacing.Lg)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Back", tint = HelloColors.DarkAccent)
                }
            }
        }
    }
}

private enum class SharedContentMode {
    Media,
    Files,
    Links
}

@Composable
private fun CloudChatStatusBanner(
    cloudChatEnabled: Boolean,
    cloudSessionUserName: String?,
    onEnableSync: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title: String
    val message: String
    if (!cloudChatEnabled) {
        title = "Chat sync is off"
        message = "This inbox is using the old local path. Turn on Chat sync to load cloud chats and cloud users on mobile."
    } else {
        title = "Cloud session missing"
        val activeName = cloudSessionUserName?.takeIf { it.isNotBlank() } ?: "this device"
        message = "Cloud chat is on, but there is no active cloud session for $activeName. Sign in again if chats and users stay empty."
    }

    HelloPanel(
        modifier = modifier,
        strong = false,
        shape = HelloShapes.Md
    ) {
        Column(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            Text(
                text = title,
                color = HelloColors.DarkText,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!cloudChatEnabled) {
                    HelloPrimaryButton(
                        text = "Turn on",
                        onClick = onEnableSync
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Settings", color = HelloColors.DarkAccent)
                }
            }
        }
    }
}

@Composable
private fun ServiceHealthPills(
    checking: Boolean,
    cloudOnline: Boolean,
    pcOnline: Boolean
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusPill(
            label = if (checking) "Checking" else if (cloudOnline) "Cloud On" else "Cloud Off",
            isOnline = cloudOnline && !checking
        )
        StatusPill(
            label = if (pcOnline) "PC On" else "PC Off",
            isOnline = pcOnline
        )
    }
}

@Composable
private fun NewChatDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    usersState: ResultState<List<User>>,
    createChatState: ResultState<Chat>?,
    onDismiss: () -> Unit,
    onUserSelected: (User) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkText,
        shape = HelloShapes.Lg,
        title = { Text("New chat", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                HelloSearchBar(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search users",
                    leading = { Icon(Icons.Default.Search, contentDescription = null, tint = HelloColors.DarkTextMuted) }
                )

                Spacer(modifier = Modifier.height(HelloSpacing.Md))

                if (createChatState is ResultState.Error) {
                    Text(
                        text = createChatState.message,
                        color = HelloColors.DarkDanger,
                        modifier = Modifier.padding(bottom = HelloSpacing.Sm)
                    )
                }

                when (usersState) {
                    is ResultState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = HelloColors.DarkAccent)
                        }
                    }
                    is ResultState.Error -> Text(text = usersState.message, color = HelloColors.DarkDanger)
                    is ResultState.Success -> {
                        val users = usersState.data
                        if (users.isEmpty()) {
                            Text("No Hello users found for that search.", color = HelloColors.DarkTextMuted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(280.dp),
                                verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                            ) {
                                items(users, key = { it.id }) { user ->
                                    UserDiscoveryItem(
                                        user = user,
                                        enabled = createChatState !is ResultState.Loading,
                                        onClick = { onUserSelected(user) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkAccent)
            }
        }
    )
}

@Composable
private fun GroupChatDialog(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    usersState: ResultState<List<User>>,
    selectedMemberIds: Set<String>,
    onToggleMember: (String) -> Unit,
    createChatState: ResultState<Chat>?,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkText,
        shape = HelloShapes.Lg,
        title = { Text("Create group chat", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                HelloTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = "Group name"
                )
                Spacer(modifier = Modifier.height(HelloSpacing.Sm))
                HelloSearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = "Search members",
                    leading = { Icon(Icons.Default.Search, contentDescription = null, tint = HelloColors.DarkTextMuted) }
                )

                Spacer(modifier = Modifier.height(HelloSpacing.Md))

                if (createChatState is ResultState.Error) {
                    Text(
                        text = createChatState.message,
                        color = HelloColors.DarkDanger,
                        modifier = Modifier.padding(bottom = HelloSpacing.Sm)
                    )
                }

                when (usersState) {
                    is ResultState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = HelloColors.DarkAccent)
                        }
                    }
                    is ResultState.Error -> Text(text = usersState.message, color = HelloColors.DarkDanger)
                    is ResultState.Success -> {
                        val users = usersState.data
                        if (users.isEmpty()) {
                            Text("No users found.", color = HelloColors.DarkTextMuted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(280.dp),
                                verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                            ) {
                                items(users, key = { it.id }) { user ->
                                    HelloPanel(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleMember(user.id) },
                                        strong = false,
                                        shape = HelloShapes.Md
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(HelloSpacing.Md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                                        ) {
                                            HelloAvatar(name = user.name, online = user.online == true, size = 44.dp, imageUrl = user.avatar)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.name,
                                                    color = HelloColors.DarkText,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                user.username?.takeIf { it.isNotBlank() }?.let { username ->
                                                    Text(
                                                        text = "@$username",
                                                        color = HelloColors.DarkAccent,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = user.profileSubtitle(),
                                                    color = HelloColors.DarkTextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = if (selectedMemberIds.contains(user.id)) Icons.Default.Check else Icons.Default.Add,
                                                contentDescription = null,
                                                tint = if (selectedMemberIds.contains(user.id)) HelloColors.DarkAccent else HelloColors.DarkTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCreate,
                enabled = groupName.isNotBlank() && selectedMemberIds.isNotEmpty() && createChatState !is ResultState.Loading
            ) {
                Text("Create", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkAccent)
            }
        }
    )
}

@Composable
private fun NetworkDiagnosticsDialog(
    checking: Boolean,
    enabled: Boolean,
    detail: String,
    cloudDetail: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        titleContentColor = HelloColors.DarkText,
        textContentColor = HelloColors.DarkText,
        shape = HelloShapes.Lg,
        title = { Text("Network diagnostics", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                Text(
                    text = if (checking) "Checking..." else if (enabled) "PC Drive online" else "PC Drive offline",
                    color = if (enabled) HelloColors.DarkAccent else HelloColors.DarkDanger,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Cloud Chat:\n$cloudDetail", color = HelloColors.DarkTextMuted)
                Text(text = "PC Drive:\n$detail", color = HelloColors.DarkTextMuted)
                Text(
                    text = "PC Drive uses home.bookhelloctg.com through Cloudflare Tunnel. Chat and calls stay cloud-backed.",
                    color = HelloColors.DarkTextMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = HelloColors.DarkAccent)
            }
        }
    )
}

@Composable
private fun UserDiscoveryItem(
    user: User,
    enabled: Boolean,
    onClick: () -> Unit
) {
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        strong = false,
        shape = HelloShapes.Md
    ) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloAvatar(name = user.name, online = user.online == true, size = 44.dp, imageUrl = user.avatar)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    color = HelloColors.DarkText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.profileSubtitle(),
                    color = HelloColors.DarkTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun User.profileSubtitle(): String {
    return when {
        online == true -> "Online"
        !username.isNullOrBlank() -> "@$username"
        !email.isNullOrBlank() -> email
        !phone.isNullOrBlank() -> phone
        else -> "Hello contact"
    }
}
