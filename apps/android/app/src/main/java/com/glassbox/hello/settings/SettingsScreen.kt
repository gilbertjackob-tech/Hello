package com.glassbox.hello.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.network.HelloApiClient
import com.glassbox.hello.networkstatus.NetworkStatusScreen
import com.glassbox.hello.people.PeopleScreen
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloSettingsCard
import com.glassbox.hello.ui.components.HelloSettingsRow
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.HelloWallpapers
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class SettingsPage {
    Home, Profile, Appearance, Notifications, FamilyNetwork, Privacy, StorageBackup, About, Diagnostics, People
}

@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onDetailVisibilityChanged: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser = sessionManager.getCurrentUser()
    var page by remember { mutableStateOf(SettingsPage.Home) }

    LaunchedEffect(page) {
        onDetailVisibilityChanged(page != SettingsPage.Home)
    }

    when (page) {
        SettingsPage.Home -> SettingsHome(sessionManager, onNavigate = { page = it }, modifier = modifier)
        SettingsPage.Profile -> SettingsSubpage("Profile", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            ProfilePage(sessionManager = sessionManager, onLogout = onLogout)
        }
        SettingsPage.Appearance -> SettingsSubpage("Appearance", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            AppearancePage()
        }
        SettingsPage.Notifications -> SettingsSubpage("Calls and notifications", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            currentUser?.let { CallsNotificationsPage(userId = it.id) }
        }
        SettingsPage.FamilyNetwork -> NetworkStatusScreen(modifier = modifier, onBack = { page = SettingsPage.Home })
        SettingsPage.Privacy -> SettingsSubpage("Privacy", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            currentUser?.let { PrivacyPage(userId = it.id) }
        }
        SettingsPage.StorageBackup -> SettingsSubpage("Storage and backup", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            StorageBackupPage(sessionManager = sessionManager)
        }
        SettingsPage.About -> SettingsSubpage("About Hello", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            AboutPage()
        }
        SettingsPage.Diagnostics -> SettingsSubpage("Developer diagnostics", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            DiagnosticsPage(sessionManager = sessionManager)
        }
        SettingsPage.People -> {
            if (currentUser != null) {
                SettingsSubpage("Contacts", onBack = { page = SettingsPage.Home }, modifier = modifier) {
                    PeopleScreen(currentUserId = currentUser.id, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(
    sessionManager: SessionManager,
    onNavigate: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val user = sessionManager.getCurrentUser()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        item {
            HelloTopBar(
                eyebrow = "HELLO SETTINGS",
                title = "Settings",
                modifier = Modifier.padding(top = HelloSpacing.Sm)
            )
        }
        item {
            if (user != null) {
                HelloPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(SettingsPage.Profile) },
                    strong = true,
                    shape = HelloShapes.Xl
                ) {
                    HelloSettingsRow(
                        title = user.name,
                        subtitle = "Exploring real-time messaging...",
                        leading = { HelloAvatar(user.name, imageUrl = user.avatar, online = true) },
                        onClick = { onNavigate(SettingsPage.Profile) }
                    )
                }
            }
        }
        item { SettingsSectionCard("Appearance", "Theme, chat wallpaper, and typing ergonomics.", Icons.Default.Palette) { AppearanceRows() } }
        item { SettingsSectionCard("Calls and notifications", "Permissions, ring readiness, and desktop alerts.", Icons.Default.Notifications) { CallsNotificationRows(userId = user?.id) } }
        item { SettingsSectionCard("Storage and backup", "Encrypted export/import and local persistence.", Icons.Default.Storage) { StorageRows(sessionManager) } }
        item {
            SettingsSectionCard("Settings map", "", Icons.Default.Info) {
                val rows = listOf(
                    "Account / Profile" to SettingsPage.Profile,
                    "Appearance" to SettingsPage.Appearance,
                    "Calls and notifications" to SettingsPage.Notifications,
                    "Family Network" to SettingsPage.FamilyNetwork,
                    "Privacy" to SettingsPage.Privacy,
                    "Storage and backup" to SettingsPage.StorageBackup,
                    "About Hello" to SettingsPage.About,
                    "Developer diagnostics" to SettingsPage.Diagnostics
                )
                rows.forEach { (label, target) ->
                    HelloSettingsRow(label, "Open $label", onClick = { onNavigate(target) })
                }
                HelloSettingsRow("Contacts and groups", "People, direct chats, and groups", onClick = { onNavigate(SettingsPage.People) }, leading = { RowIcon(Icons.Default.People) })
            }
        }
        item { Spacer(modifier = Modifier.height(HelloSpacing.Xxl)) }
    }
}

@Composable
private fun SettingsSubpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .imePadding()
            .navigationBarsPadding()
    ) {
        HelloTopBar(
            eyebrow = "HELLO SETTINGS",
            title = title,
            modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm)
        ) {
            HelloIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
            }
        }
        content()
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    HelloSettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = HelloColors.DarkTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent)
        }
        content()
    }
}

@Composable
private fun AppearanceRows() {
    val prefs = LocalContext.current.getSharedPreferences("hello_settings", 0)
    var theme by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    var enterSends by remember { mutableStateOf(prefs.getBoolean("enter_sends", true)) }
    var wallpaper by remember { mutableStateOf(prefs.getString("wallpaper", "default") ?: "default") }
    var opacity by remember { mutableFloatStateOf(prefs.getInt("wallpaper_opacity", 100).toFloat()) }
    var customImageDialog by remember { mutableStateOf(false) }

    OptionRow("Theme", "dark only in beta", listOf("dark only in beta")) {
        theme = "dark"
        prefs.edit().putString("theme", "dark").apply()
    }
    Text(
        "Light/System are disabled in this beta so the Android app does not show a half-light, half-dark UI.",
        color = HelloColors.DarkTextMuted,
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
    )
    ToggleRow("Enter sends", enterSends) {
        enterSends = it
        prefs.edit().putBoolean("enter_sends", it).apply()
    }
    OptionRow("Wallpaper", wallpaper, HelloWallpapers.Options) {
        if (it == HelloWallpapers.CustomImage) {
            customImageDialog = true
        } else {
            wallpaper = it
            prefs.edit().putString("wallpaper", it).apply()
        }
    }
    Column(modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wallpaper opacity", color = HelloColors.DarkText, modifier = Modifier.weight(1f))
            Text("${opacity.toInt()}%", color = HelloColors.DarkTextMuted)
        }
        Slider(
            value = opacity,
            onValueChange = {
                opacity = it
                prefs.edit().putInt("wallpaper_opacity", it.toInt()).apply()
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = HelloColors.DarkAccent,
                activeTrackColor = HelloColors.DarkAccent,
                inactiveTrackColor = HelloColors.DarkBorderStrong
            )
        )
    }
    if (customImageDialog) {
        InfoDialog(
            title = "Custom image",
            message = "Custom image coming later.",
            onDismiss = { customImageDialog = false }
        )
    }
}

@Composable
private fun CallsNotificationRows(userId: String?) {
    val prefs = LocalContext.current.getSharedPreferences("hello_settings", 0)
    var desktopNotifications by remember { mutableStateOf(prefs.getBoolean("desktop_notifications", false)) }
    var cameraDialog by remember { mutableStateOf(false) }
    userId?.let { PrivacyInlineRow(it) }
    ToggleRow("Desktop notifications", desktopNotifications) {
        desktopNotifications = it
        prefs.edit().putBoolean("desktop_notifications", it).apply()
    }
    HelloSettingsRow(
        title = "Camera / microphone",
        subtitle = "Native permission and call media test will be connected in the call stage.",
        onClick = { cameraDialog = true },
        leading = { RowIcon(Icons.Default.VideoCall) }
    )
    if (cameraDialog) {
        InfoDialog(
            title = "Camera/microphone test",
            message = "Native permission and call media test will be connected in the call stage.",
            onDismiss = { cameraDialog = false }
        )
    }
}

@Composable
private fun StorageRows(sessionManager: SessionManager) {
    val context = LocalContext.current
    var importDialog by remember { mutableStateOf(false) }
    HelloSettingsRow(
        title = "Export",
        subtitle = "Export local settings/session metadata as JSON. Not an encrypted backup.",
        onClick = {
            val prefs = context.getSharedPreferences("hello_settings", 0)
            val user = sessionManager.getCurrentUser()
            val json = JSONObject()
                .put("theme", prefs.getString("theme", "system"))
                .put("enterSends", prefs.getBoolean("enter_sends", true))
                .put("wallpaper", prefs.getString("wallpaper", "default"))
                .put("wallpaperOpacity", prefs.getInt("wallpaper_opacity", 100))
                .put("currentUserId", user?.id)
                .toString(2)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
            }
            context.startActivity(Intent.createChooser(intent, "Export Hello settings"))
        }
    )
    HelloSettingsRow(
        title = "Import",
        subtitle = "Encrypted restore is not wired yet.",
        onClick = { importDialog = true }
    )
    if (importDialog) {
        InfoDialog(
            title = "Import",
            message = "Encrypted restore is not wired yet. No import was performed.",
            onDismiss = { importDialog = false }
        )
    }
}

@Composable
private fun ProfilePage(sessionManager: SessionManager, onLogout: () -> Unit) {
    val sessionUser = sessionManager.getCurrentUser()
    val api = remember { HelloApiClient() }
    var state by remember { mutableStateOf<ResultState<ChatModels.User>?>(null) }
    LaunchedEffect(sessionUser?.id) {
        sessionUser?.id?.let {
            state = ResultState.Loading
            val result = api.fetchUser(it)
            state = if (result.isSuccess) ResultState.Success(result.getOrNull()!!) else ResultState.Error(result.exceptionOrNull()?.message ?: "Failed to load profile")
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
    ) {
        item {
            when (val s = state) {
                is ResultState.Loading -> LoadingView()
                is ResultState.Error -> ErrorView(message = s.message)
                is ResultState.Success -> ProfileCard(s.data)
                null -> sessionUser?.let {
                    ProfileCard(ChatModels.User(id = it.id, name = it.name, avatar = it.avatar, phone = it.phone, email = it.email))
                }
            }
        }
        item {
            HelloSettingsCard {
                HelloSettingsRow("Server origin", AppConfig.SERVER_ORIGIN)
                HelloSettingsRow("User id", sessionUser?.id.orEmpty())
            }
        }
        item { HelloPrimaryButton(text = "Logout", onClick = onLogout) }
    }
}

@Composable
private fun ProfileCard(user: ChatModels.User) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
        Column(modifier = Modifier.padding(HelloSpacing.Xxl), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
            HelloAvatar(user.name, size = 76.dp, online = user.online == true, imageUrl = user.avatar)
            Text(user.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text("User id: ${user.id}", color = HelloColors.DarkTextMuted)
            Text(user.email ?: user.phone ?: "No phone/email on profile", color = HelloColors.DarkTextMuted)
            Text("Last active: ${user.lastActive?.let { formatSettingTime(it) } ?: "Unavailable"}", color = HelloColors.DarkTextMuted)
            Text("Privacy: ${user.privacy ?: user.lastActivePrivacy ?: "everyone"}", color = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun AppearancePage() {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item { SettingsSectionCard("Appearance", "Theme, chat wallpaper, and typing ergonomics.", Icons.Default.Palette) { AppearanceRows() } }
    }
}

@Composable
private fun CallsNotificationsPage(userId: String) {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item { SettingsSectionCard("Calls and notifications", "Permissions, ring readiness, and desktop alerts.", Icons.Default.Notifications) { CallsNotificationRows(userId) } }
    }
}

@Composable
private fun PrivacyPage(userId: String) {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item { SettingsSectionCard("Privacy", "Last active privacy.", Icons.Default.Lock) { PrivacyInlineRow(userId) } }
    }
}

@Composable
private fun StorageBackupPage(sessionManager: SessionManager) {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item { SettingsSectionCard("Storage and backup", "Encrypted export/import and local persistence.", Icons.Default.Storage) { StorageRows(sessionManager) } }
    }
}

@Composable
private fun AboutPage() {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item {
            HelloSettingsCard {
                HelloSettingsRow("Hello", "Private family network messenger")
                HelloSettingsRow("GlassBox", "Built inside GlassBox ecosystem")
                HelloSettingsRow("Android native client", "Package com.glassbox.hello")
                HelloSettingsRow("Web backend", "/hello/api")
                HelloSettingsRow("Socket", AppConfig.HELLO_SOCKET_PATH)
            }
        }
    }
}

@Composable
private fun DiagnosticsPage(sessionManager: SessionManager) {
    val user = sessionManager.getCurrentUser()
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item {
            HelloSettingsCard {
                HelloSettingsRow("Current user id", user?.id ?: "Not signed in")
                HelloSettingsRow("Server origin", AppConfig.SERVER_ORIGIN)
                HelloSettingsRow("API base", AppConfig.HELLO_API_BASE)
                HelloSettingsRow("Socket path", AppConfig.HELLO_SOCKET_PATH)
                HelloSettingsRow("Upload base", AppConfig.HELLO_UPLOADS_BASE)
                HelloSettingsRow("Resolved avatar URL", UrlResolver.resolve(user?.avatar) ?: "No avatar")
                HelloSettingsRow("App package", "com.glassbox.hello")
            }
        }
    }
}

@Composable
private fun PrivacyInlineRow(userId: String) {
    val api = remember { HelloApiClient() }
    val scope = rememberCoroutineScope()
    var privacy by remember { mutableStateOf("everyone") }
    var helper by remember { mutableStateOf("Uses /hello/api/users/:userId/privacy.") }
    OptionRow("Last active privacy", privacy, listOf("everyone", "contacts", "none")) {
        privacy = it
        scope.launch {
            val result = api.updateUserPrivacy(userId, it)
            helper = result.exceptionOrNull()?.message ?: "Saved"
        }
    }
    Text(helper, color = HelloColors.DarkTextMuted, modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs))
}

@Composable
private fun OptionRow(title: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    HelloSettingsRow(
        title = title,
        subtitle = selected.replaceFirstChar { it.uppercase() },
        onClick = { choosing = true },
        trailing = { HelloPill(selected.replaceFirstChar { it.uppercase() }, active = true) }
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            containerColor = HelloColors.DarkPanelStrong,
            title = { Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(onClick = {
                            onSelected(option)
                            choosing = false
                        }) {
                            Text(option.replaceFirstChar { it.uppercase() }, color = HelloColors.DarkAccent)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    HelloSettingsRow(
        title = title,
        subtitle = if (checked) "On" else "Off",
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HelloColors.DarkAccent,
                    checkedTrackColor = HelloColors.DarkAccentSoft,
                    uncheckedThumbColor = HelloColors.DarkTextMuted,
                    uncheckedTrackColor = HelloColors.DarkPanelMuted
                )
            )
        }
    )
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = HelloColors.DarkAccent) }
        }
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    HelloIconButton(onClick = {}, active = true) {
        Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent)
    }
}

private fun formatSettingTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}
