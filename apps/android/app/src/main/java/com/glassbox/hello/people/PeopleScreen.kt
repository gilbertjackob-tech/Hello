package com.glassbox.hello.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.chat.ChatModels.User
import com.glassbox.hello.chat.ChatViewModel
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloListItem
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.components.HelloSectionHeader
import com.glassbox.hello.ui.components.HelloTextField
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun PeopleScreen(
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    val usersState by viewModel.usersState.collectAsState()
    val createChatState by viewModel.createChatState.collectAsState()
    var query by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(currentUserId, query) {
        viewModel.configureCloudChat(context)
        viewModel.loadUsers(currentUserId, query, cloudChatEnabled = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg)
    ) {
        HelloTopBar(
            eyebrow = "HELLO PEOPLE",
            title = "Contacts",
            modifier = Modifier.padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Md)
        ) {
            HelloPill("Groups", active = true)
        }

        HelloSearchBar(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search people or groups",
            leading = { Icon(Icons.Default.Search, contentDescription = null, tint = HelloColors.DarkTextMuted) }
        )

        Spacer(modifier = Modifier.height(HelloSpacing.Lg))
        HelloSectionHeader("Create group")
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
            Column(modifier = Modifier.padding(HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                HelloTextField(value = groupName, onValueChange = { groupName = it }, label = "Group name", auth = true)
                Text("${selectedMemberIds.size} selected from the users list below.", color = HelloColors.DarkTextMuted)
                HelloPrimaryButton(
                    text = "Create group",
                    enabled = groupName.trim().isNotBlank() && selectedMemberIds.isNotEmpty(),
                    onClick = {
                        viewModel.createGroupChat(
                            currentUserId = currentUserId,
                            currentUserName = currentUserId,
                            name = groupName.trim(),
                            memberIds = selectedMemberIds.toList(),
                            cloudChatEnabled = true
                        )
                        groupName = ""
                        selectedMemberIds = emptySet()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(HelloSpacing.Lg))
        HelloSectionHeader("People")
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        if (createChatState is ResultState.Error) {
            Text((createChatState as ResultState.Error).message, color = HelloColors.DarkDanger)
        }

        when (usersState) {
            is ResultState.Loading -> LoadingView(modifier = Modifier.weight(1f))
            is ResultState.Error -> ErrorView(
                message = (usersState as ResultState.Error).message,
                onRetry = { viewModel.loadUsers(currentUserId, query, cloudChatEnabled = true) },
                modifier = Modifier.weight(1f)
            )
            is ResultState.Success -> {
                val users = (usersState as ResultState.Success<List<User>>).data
                if (users.isEmpty()) {
                    HelloEmptyState(
                        title = "No people found",
                        message = "Hello contacts will appear here.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = HelloSpacing.Xxl),
                        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                    ) {
                        items(users, key = { it.id }) { user ->
                            HelloPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMemberIds = if (selectedMemberIds.contains(user.id)) {
                                            selectedMemberIds - user.id
                                        } else {
                                            selectedMemberIds + user.id
                                        }
                                    },
                                strong = true,
                                shape = HelloShapes.Lg
                            ) {
                                HelloListItem(
                                    title = user.name,
                                    subtitle = when {
                                        user.online == true -> "Online"
                                        !user.email.isNullOrBlank() -> user.email
                                        !user.phone.isNullOrBlank() -> user.phone
                                        else -> "Hello contact"
                                    },
                                    leading = { HelloAvatar(user.name, online = user.online == true, imageUrl = user.avatar) },
                                    trailing = {
                                        androidx.compose.foundation.layout.Row {
                                            Checkbox(
                                                checked = selectedMemberIds.contains(user.id),
                                                onCheckedChange = { checked ->
                                                    selectedMemberIds = if (checked) selectedMemberIds + user.id else selectedMemberIds - user.id
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = HelloColors.DarkAccent)
                                            )
                                            HelloIconButton(onClick = { viewModel.startDirectChat(currentUserId, currentUserId, user.id, cloudChatEnabled = true) }) {
                                                Icon(Icons.Default.GroupAdd, contentDescription = "Start chat", tint = HelloColors.DarkAccent)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
