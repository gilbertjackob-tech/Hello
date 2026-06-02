package com.glassbox.hello.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.attachments.SharedFilesScreen
import com.glassbox.hello.attachments.SharedLinksScreen
import com.glassbox.hello.attachments.SharedMediaScreen
import com.glassbox.hello.chat.ChatModels.Chat
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.core.HelloPreferences
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User as CoreUser
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.networkstatus.NetworkStatus
import com.glassbox.hello.networkstatus.checkCloudChatNetwork
import com.glassbox.hello.networkstatus.checkHelloNetwork
import com.glassbox.hello.ui.components.ErrorView
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
import com.glassbox.hello.ui.theme.HelloColors
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

@Composable
fun ChatListScreen(
    currentUserId: String,
    currentUserName: String,
    onChatSelected: (Chat) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel()
    val chatsState by viewModel.chatsState.collectAsState()
    val chatsRefreshing by viewModel.chatsRefreshing.collectAsState()
    val usersState by viewModel.usersState.collectAsState()
    val createChatState by viewModel.createChatState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val cloudChatEnabled = HelloPreferences.read(context).cloudChatEnabled
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
        socketManager.onChatUpdated = {
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
        }
        socketManager.onMessageReceived = {
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
        }
        socketManager.onPresenceUpdated = {
            viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
            viewModel.loadUsers(currentUserId, null, cloudChatEnabled = cloudChatEnabled)
        }
        socketManager.connect(context, CoreUser(id = currentUserId, name = currentUserName))
        onDispose {
            socketManager.onChatUpdated = null
            socketManager.onMessageReceived = null
            socketManager.onPresenceUpdated = null
        }
    }

    LaunchedEffect(showNewChat, showGroupChat, userSearchQuery, groupSearchQuery) {
        when {
            showNewChat -> viewModel.loadUsers(currentUserId, userSearchQuery, cloudChatEnabled = cloudChatEnabled)
            showGroupChat -> viewModel.loadUsers(currentUserId, groupSearchQuery, cloudChatEnabled = cloudChatEnabled)
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .background(HelloColors.DarkBg)
                .padding(horizontal = HelloSpacing.Lg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hello",
                        style = MaterialTheme.typography.headlineSmall,
                        color = HelloColors.DarkText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Inbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = HelloColors.DarkAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ServiceHealthPills(
                    checking = vpnChecking,
                    cloudOnline = cloudChatOnline,
                    pcOnline = vpnEnabled
                )
                HelloIconButton(onClick = { showNewChat = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = HelloColors.DarkAccent)
                }
                HelloIconButton(onClick = {
                    refreshNetworkStatus()
                    viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh chats", tint = HelloColors.DarkTextMuted)
                }
                if (chatsRefreshing) {
                    Text(
                        text = "Syncing",
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box {
                    HelloIconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = HelloColors.DarkTextMuted)
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
                                if (showNewChat) {
                                    showNewChat = false
                                }
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

            HelloSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search people, groups, files, or messages",
                leading = { Icon(Icons.Default.Search, contentDescription = null, tint = HelloColors.DarkTextMuted) }
            )

            LazyRow(
                modifier = Modifier.padding(vertical = HelloSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
                contentPadding = PaddingValues(end = HelloSpacing.Lg)
            ) {
                items(ChatFilter.values()) { filter ->
                    HelloFilterChip(
                        label = filter.label,
                        active = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            when (chatsState) {
                is ResultState.Loading -> LoadingView(modifier = Modifier.weight(1f))
                is ResultState.Error -> ErrorView(
                    message = (chatsState as ResultState.Error).message,
                    onRetry = { viewModel.loadChats(currentUserId, cloudChatEnabled = cloudChatEnabled) },
                    modifier = Modifier.weight(1f)
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
                            title = "No chats yet",
                            message = "Start a direct chat with another Hello user.",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            action = {
                                HelloPrimaryButton(
                                    text = "New chat",
                                    onClick = { showNewChat = true },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
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
                                if (other?.online == true) {
                                    Spacer(modifier = Modifier.height(0.dp))
                                }
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
                    viewModel.startDirectChat(currentUserId, currentUserName, user.id, cloudChatEnabled)
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
        Text(
            text = if (checking) "Checking" else if (cloudOnline) "Cloud On" else "Cloud Off",
            color = if (cloudOnline) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (pcOnline) "PC On" else "PC Off",
            color = if (pcOnline) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                            Text("No users found.", color = HelloColors.DarkTextMuted)
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
        !email.isNullOrBlank() -> email
        !phone.isNullOrBlank() -> phone
        else -> "Hello contact"
    }
}
