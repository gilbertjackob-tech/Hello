package com.glassbox.hello.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.CloudChatApi
import com.glassbox.hello.auth.CloudChatPreferences
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.auth.CloudUserRepository
import com.glassbox.hello.browser.BrowserClearRange
import com.glassbox.hello.browser.BrowserViewModel
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.HelloPreferences
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.SessionManager
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.demo.voice.VoiceAssistantDemoScreen
import com.glassbox.hello.networkstatus.NetworkStatusScreen
import com.glassbox.hello.notifications.NotificationPrefs
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
import com.glassbox.hello.ui.theme.HelloAppPalette
import com.glassbox.hello.ui.theme.HelloAppThemes
import com.glassbox.hello.ui.theme.ChatThemeStore
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class SettingsPage {
    Home, Profile, Appearance, ChatTheme, Notifications, FamilyNetwork, Privacy, StorageBackup, About, Diagnostics, People, VoiceDemo
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

    BackHandler(enabled = page != SettingsPage.Home) {
        page = when (page) {
            SettingsPage.ChatTheme -> SettingsPage.Appearance
            else -> SettingsPage.Home
        }
    }

    when (page) {
        SettingsPage.Home -> SettingsHome(sessionManager, onNavigate = { page = it }, modifier = modifier)
        SettingsPage.Profile -> SettingsSubpage("Profile", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            ProfilePage(sessionManager = sessionManager, onLogout = onLogout)
        }
        SettingsPage.Appearance -> SettingsSubpage("Appearance", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            AppearancePage(userId = currentUser?.id.orEmpty(), onOpenChatTheme = { page = SettingsPage.ChatTheme })
        }
        SettingsPage.ChatTheme -> {
            ChatThemeRoute(
                userId = currentUser?.id.orEmpty(),
                onBack = { page = SettingsPage.Appearance },
                modifier = modifier
            )
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
        SettingsPage.VoiceDemo -> SettingsSubpage("Voice assistant demo", onBack = { page = SettingsPage.Home }, modifier = modifier) {
            VoiceAssistantDemoScreen(modifier = Modifier.fillMaxSize())
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
        item {
            SettingsSectionCard("Account", "Profile, contacts, and identity.", Icons.Default.Person) {
                HelloSettingsRow(
                    "Profile",
                    "Name, photo, account sync, and logout",
                    onClick = { onNavigate(SettingsPage.Profile) },
                    leading = { RowIcon(Icons.Default.Person) }
                )
                HelloSettingsRow(
                    "Contacts and groups",
                    "People, direct chats, and groups",
                    onClick = { onNavigate(SettingsPage.People) },
                    leading = { RowIcon(Icons.Default.People) }
                )
            }
        }
        item {
            SettingsSectionCard("Preferences", "Theme, chat, calls, and privacy.", Icons.Default.Palette) {
                HelloSettingsRow(
                    "Appearance",
                    "App theme, chat wallpaper, opacity, and typing",
                    onClick = { onNavigate(SettingsPage.Appearance) },
                    leading = { RowIcon(Icons.Default.Palette) }
                )
                HelloSettingsRow(
                    "Calls and notifications",
                    "Permissions, ringtone, full-screen calls, and alerts",
                    onClick = { onNavigate(SettingsPage.Notifications) },
                    leading = { RowIcon(Icons.Default.Notifications) }
                )
                HelloSettingsRow(
                    "Privacy",
                    "Last active, read receipts, and browser data",
                    onClick = { onNavigate(SettingsPage.Privacy) },
                    leading = { RowIcon(Icons.Default.Lock) }
                )
            }
        }
        item {
            SettingsSectionCard("Data and network", "Backups, device storage, and connectivity.", Icons.Default.Storage) {
                HelloSettingsRow(
                    "Storage and backup",
                    "Encrypted export/import and local persistence",
                    onClick = { onNavigate(SettingsPage.StorageBackup) },
                    leading = { RowIcon(Icons.Default.Storage) }
                )
                HelloSettingsRow(
                    "Family Network",
                    "Cloud Chat, Cloud Calls, and PC Drive status",
                    onClick = { onNavigate(SettingsPage.FamilyNetwork) },
                    leading = { RowIcon(Icons.Default.Public) }
                )
            }
        }
        item {
            SettingsSectionCard("App", "About and diagnostics.", Icons.Default.Info) {
                HelloSettingsRow(
                    "About Hello",
                    "App package, backend, and socket details",
                    onClick = { onNavigate(SettingsPage.About) },
                    leading = { RowIcon(Icons.Default.Info) }
                )
                HelloSettingsRow(
                    "Developer diagnostics",
                    "Current user, server origins, and resolved avatar URL",
                    onClick = { onNavigate(SettingsPage.Diagnostics) },
                    leading = { RowIcon(Icons.Default.BugReport) }
                )
                HelloSettingsRow(
                    "Voice assistant demo",
                    "Recognition and language demo tools",
                    onClick = { onNavigate(SettingsPage.VoiceDemo) },
                    leading = { RowIcon(Icons.Default.VideoCall) }
                )
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
private fun AppearanceRows(userId: String, onOpenChatTheme: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(context) { HelloPreferences.read(context) }
    val initialChatTheme = remember(context, userId) { ChatThemeStore.read(context, userId) }
    var theme by remember { mutableStateOf(initial.themeMode) }
    var wallpaperOpacity by remember { mutableStateOf(initialChatTheme.wallpaperOpacity) }
    var enterSends by remember { mutableStateOf(initial.enterSends) }
    var chatSounds by remember { mutableStateOf(initial.chatSounds) }
    var cloudChatEnabled by remember { mutableStateOf(initial.cloudChatEnabled) }

    HelloSettingsRow(
        title = "Chat theme",
        subtitle = "Color, wallpaper, and preview",
        onClick = onOpenChatTheme,
        leading = { RowIcon(Icons.Default.Palette) }
    )
    AppThemeSelector(
        selectedId = theme,
        onSelected = {
            theme = it
            HelloPreferences.setThemeMode(context, it)
            ChatThemeStore.applyAppTheme(context, userId, it, wallpaperOpacity)
        }
    )
    Column(
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Chat background opacity", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text("$wallpaperOpacity%", color = HelloColors.DarkTextMuted)
            }
        }
        Slider(
            value = wallpaperOpacity.toFloat(),
            onValueChange = { value ->
                wallpaperOpacity = value.toInt().coerceIn(35, 100)
            },
            onValueChangeFinished = {
                ChatThemeStore.applyAppTheme(context, userId, theme, wallpaperOpacity)
            },
            valueRange = 35f..100f
        )
    }
    ToggleRow("Enter sends", enterSends) {
        enterSends = it
        HelloPreferences.setEnterSends(context, it)
    }
    ToggleRow("Chat sounds", chatSounds) {
        chatSounds = it
        HelloPreferences.setChatSounds(context, it)
    }
    ToggleRow("Chat sync", cloudChatEnabled) {
        cloudChatEnabled = it
        HelloPreferences.setCloudChatEnabled(context, it)
    }
    Text(
        "Chat sync keeps conversations available across your signed-in devices. Drive photos and videos continue to use the PC backend.",
        color = HelloColors.DarkTextMuted,
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
    )
}

@Composable
private fun AppThemeSelector(
    selectedId: String,
    onSelected: (String) -> Unit
) {
    val normalized = HelloAppThemes.normalizeId(selectedId)
    Column(
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Text("App theme", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
        Text(
            "Choose a full palette for pages, cards, message surfaces, text, buttons, and soft background texture.",
            color = HelloColors.DarkTextMuted
        )
        HelloAppThemes.All.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
            ) {
                rowThemes.forEach { palette ->
                    AppThemePreviewCard(
                        palette = palette,
                        selected = normalized == palette.id,
                        onClick = { onSelected(palette.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowThemes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppThemePreviewCard(
    palette: HelloAppPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = palette.panelStrong,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) palette.accentStrong else palette.borderStrong
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(palette.bgDeep)
            ) {
                ThemeTextureCanvas(palette = palette, modifier = Modifier.fillMaxSize())
                ThemePreviewChrome(palette = palette, modifier = Modifier.fillMaxSize())
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = palette.outgoingText, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Column(
                modifier = Modifier.padding(HelloSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(palette.label, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(palette.subtitle, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.previewPalette.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(0.5.dp, palette.border, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeTextureCanvas(palette: HelloAppPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Brush.verticalGradient(listOf(palette.bgBase, palette.bgDeep)))) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbA, Color.Transparent),
                center = Offset(w * 0.88f, h * 0.12f),
                radius = w * 0.65f
            ),
            center = Offset(w * 0.88f, h * 0.12f),
            radius = w * 0.65f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbB, Color.Transparent),
                center = Offset(w * 0.12f, h * 0.76f),
                radius = w * 0.55f
            ),
            center = Offset(w * 0.12f, h * 0.76f),
            radius = w * 0.55f
        )
        repeat(7) { index ->
            val y = h * (0.18f + index * 0.1f)
            drawLine(
                color = palette.border.copy(alpha = if (palette.isDark) 0.22f else 0.12f),
                start = Offset(0f, y),
                end = Offset(w, y + (index % 2) * 5f),
                strokeWidth = 0.8f
            )
        }
    }
}

@Composable
private fun ThemePreviewChrome(palette: HelloAppPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(palette.accent))
            PreviewLine(color = palette.textSecondary, width = 72.dp, height = 6.dp)
            PreviewLine(color = Color.Black.copy(alpha = if (palette.isDark) 0.38f else 0.08f), width = 84.dp, height = 8.dp)
        }
        PreviewMessageBlock(palette.incoming, palette.incomingBorder, alignEnd = false, wide = false)
        PreviewMessageBlock(palette.outgoing, palette.outgoingBorder, alignEnd = true, wide = true)
        PreviewMessageBlock(palette.incoming, palette.incomingBorder, alignEnd = false, wide = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.elevated)
                .border(1.dp, palette.border, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(palette.textMuted))
            Spacer(Modifier.width(8.dp))
            PreviewLine(color = palette.textSecondary, width = 86.dp, height = 4.dp)
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(palette.accent))
        }
    }
}

@Composable
private fun PreviewMessageBlock(
    color: Color,
    border: Color,
    alignEnd: Boolean,
    wide: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (wide) 0.42f else 0.34f)
                .height(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color)
                .border(1.dp, border, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PreviewLine(color = Color.White.copy(alpha = 0.62f), width = if (wide) 78.dp else 56.dp, height = 4.dp)
            PreviewLine(color = Color.White.copy(alpha = 0.42f), width = if (wide) 54.dp else 40.dp, height = 4.dp)
        }
    }
}

@Composable
private fun PreviewLine(color: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}

@Composable
private fun CallsNotificationRows(userId: String?) {
    val context = LocalContext.current
    val prefs = LocalContext.current.getSharedPreferences("hello_settings", 0)
    var desktopNotifications by remember { mutableStateOf(prefs.getBoolean("desktop_notifications", false)) }
    var calls by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_CALL_NOTIFICATIONS, true)) }
    var missedCalls by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_MISSED_CALL_NOTIFICATIONS, true)) }
    var messages by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_MESSAGE_NOTIFICATIONS, true)) }
    var mentions by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_MENTION_NOTIFICATIONS, true)) }
    var moments by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_STATUS_POST_NOTIFICATIONS, true)) }
    var activity by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_STATUS_ACTIVITY_NOTIFICATIONS, true)) }
    var nudges by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_RE_ENGAGEMENT_NOTIFICATIONS, true)) }
    var system by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_SYSTEM_NOTIFICATIONS, true)) }
    var inApp by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_IN_APP_NOTIFICATIONS, true)) }
    var quietHours by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_QUIET_HOURS_ENABLED, false)) }
    var allowMentions by remember { mutableStateOf(prefs.getBoolean(NotificationPrefs.KEY_ALLOW_MENTIONS_DND, true)) }
    var cameraDialog by remember { mutableStateOf(false) }
    val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        NotificationManagerCompat.from(context).canUseFullScreenIntent()
    } else {
        true
    }
    userId?.let { PrivacyInlineRow(it) }
    ToggleRow("Incoming calls", calls) {
        calls = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_CALL_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Missed calls", missedCalls) {
        missedCalls = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_MISSED_CALL_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Messages", messages) {
        messages = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_MESSAGE_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Mentions and replies", mentions) {
        mentions = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_MENTION_NOTIFICATIONS, it).apply()
    }
    ToggleRow("New moments", moments) {
        moments = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_STATUS_POST_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Reactions and views", activity) {
        activity = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_STATUS_ACTIVITY_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Family nudges", nudges) {
        nudges = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_RE_ENGAGEMENT_NOTIFICATIONS, it).apply()
    }
    ToggleRow("System updates", system) {
        system = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_SYSTEM_NOTIFICATIONS, it).apply()
    }
    ToggleRow("In-app banners", inApp) {
        inApp = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_IN_APP_NOTIFICATIONS, it).apply()
    }
    ToggleRow("Quiet hours", quietHours) {
        quietHours = it
        prefs.edit()
            .putBoolean(NotificationPrefs.KEY_QUIET_HOURS_ENABLED, it)
            .putInt(NotificationPrefs.KEY_QUIET_HOURS_START_MINUTES, 22 * 60)
            .putInt(NotificationPrefs.KEY_QUIET_HOURS_END_MINUTES, 8 * 60)
            .apply()
    }
    ToggleRow("Allow mentions in quiet hours", allowMentions) {
        allowMentions = it
        prefs.edit().putBoolean(NotificationPrefs.KEY_ALLOW_MENTIONS_DND, it).apply()
    }
    ToggleRow("Desktop notifications", desktopNotifications) {
        desktopNotifications = it
        prefs.edit().putBoolean("desktop_notifications", it).apply()
    }
    HelloSettingsRow(
        title = "Full-screen call alerts",
        subtitle = if (canUseFullScreenIntent) {
            "Allowed. Incoming calls can open over the lock screen."
        } else {
            "Denied on Android 14+. Allow it so incoming calls can open full screen."
        },
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        },
        leading = { RowIcon(Icons.Default.Notifications) }
    )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionUser = sessionManager.getCurrentUser()
    val api = remember { CloudChatApi() }
    val userRepository = remember { CloudUserRepository(context) }
    var state by remember { mutableStateOf<ResultState<ChatModels.User>?>(null) }
    var avatarUploadState by remember { mutableStateOf<ResultState<Unit>?>(null) }
    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarZoom by remember { mutableStateOf(1f) }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && sessionUser != null) {
            pendingAvatarUri = uri
            avatarZoom = 1f
        }
    }
    fun uploadSelectedAvatar(uri: Uri, zoom: Float) {
        val user = sessionUser ?: return
        avatarUploadState = ResultState.Loading
        pendingAvatarUri = null
        scope.launch {
            userRepository.uploadAvatar(context, user.id, uri, zoom)
                .onSuccess { updated ->
                    sessionManager.saveCurrentUser(updated)
                    CloudSessionManager(context).save(updated)
                    state = ResultState.Success(
                        ChatModels.User(
                            id = updated.id,
                            name = updated.name,
                            avatar = updated.avatar,
                            phone = updated.phone,
                            email = updated.email
                        )
                    )
                    avatarUploadState = ResultState.Success(Unit)
                }
                .onFailure {
                    avatarUploadState = ResultState.Error(it.message ?: "Profile photo update failed")
                }
        }
    }
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
                is ResultState.Success -> ProfileCard(
                    user = s.data,
                    uploadState = avatarUploadState,
                    onChangePhoto = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                null -> sessionUser?.let {
                    ProfileCard(
                        user = ChatModels.User(id = it.id, name = it.name, avatar = it.avatar, phone = it.phone, email = it.email),
                        uploadState = avatarUploadState,
                        onChangePhoto = {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            }
        }
        item {
            HelloSettingsCard {
                HelloSettingsRow(
                    "Change profile photo",
                    when (val upload = avatarUploadState) {
                        is ResultState.Loading -> "Uploading cropped photo..."
                        is ResultState.Error -> upload.message
                        is ResultState.Success -> "Profile photo updated"
                        null -> "Choose a photo, crop it, and preview the avatar"
                    },
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    leading = { RowIcon(Icons.Default.CameraAlt) }
                )
                HelloSettingsRow("Account sync", "Signed in as ${sessionUser?.name.orEmpty()}")
                HelloSettingsRow("User id", sessionUser?.id.orEmpty())
            }
        }
        item { HelloPrimaryButton(text = "Logout", onClick = onLogout) }
    }
    pendingAvatarUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            zoom = avatarZoom,
            onZoomChange = { avatarZoom = it },
            onDismiss = { pendingAvatarUri = null },
            onApply = { uploadSelectedAvatar(uri, avatarZoom) }
        )
    }
}

@Composable
private fun ProfileCard(
    user: ChatModels.User,
    uploadState: ResultState<Unit>?,
    onChangePhoto: () -> Unit
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
        Column(modifier = Modifier.padding(HelloSpacing.Xxl), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
            Box {
                HelloAvatar(user.name, size = 96.dp, online = user.online == true, imageUrl = user.avatar)
                HelloIconButton(
                    onClick = onChangePhoto,
                    active = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Change profile photo", tint = HelloColors.DarkAccent)
                }
            }
            Text(user.name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(
                when (uploadState) {
                    is ResultState.Loading -> "Updating profile photo..."
                    is ResultState.Error -> uploadState.message
                    is ResultState.Success -> "Profile photo updated"
                    null -> "Tap the photo to change, crop, and resize your avatar."
                },
                color = HelloColors.DarkTextMuted
            )
            Text("User id: ${user.id}", color = HelloColors.DarkTextMuted)
            Text(user.email ?: user.phone ?: "No phone/email on profile", color = HelloColors.DarkTextMuted)
            Text("Last active: ${user.lastActive?.let { formatSettingTime(it) } ?: "Unavailable"}", color = HelloColors.DarkTextMuted)
            Text("Privacy: ${user.privacy ?: user.lastActivePrivacy ?: "everyone"}", color = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun AvatarCropDialog(
    uri: Uri,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Crop profile photo", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Profile photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoom.coerceIn(1f, 3f),
                                    scaleY = zoom.coerceIn(1f, 3f)
                                )
                        )
                    }
                }
                Text("Zoom", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Slider(
                    value = zoom,
                    onValueChange = onZoomChange,
                    valueRange = 1f..3f
                )
                Text(
                    "The uploaded avatar is saved as a square image, so the chat list, calls, and profile screen all use the same crop.",
                    color = HelloColors.DarkTextMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text("Save photo", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

@Composable
private fun AppearancePage(userId: String, onOpenChatTheme: () -> Unit) {
    LazyColumn(modifier = Modifier.padding(horizontal = HelloSpacing.Lg), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
        item {
            SettingsSectionCard("Appearance", "Theme, chat wallpaper, and typing ergonomics.", Icons.Default.Palette) {
                AppearanceRows(userId = userId, onOpenChatTheme = onOpenChatTheme)
            }
        }
        item {
            Text(
                text = "Chat themes are saved locally for ${userId.ifBlank { "this user" }}.",
                color = HelloColors.DarkTextMuted,
                modifier = Modifier.padding(horizontal = HelloSpacing.Lg)
            )
        }
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
        item { SettingsSectionCard("Browser data", "Clear cookies and site data for the active browser profile.", Icons.Default.Public) { BrowserDataRows() } }
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
                HelloSettingsRow("Chat cloud", AppConfig.CHAT_CLOUD_BASE_URL)
                HelloSettingsRow("Chat cloud fallback", AppConfig.CHAT_CLOUD_FALLBACK_URL)
                HelloSettingsRow("Drive backend", AppConfig.DRIVE_SERVER_ORIGIN)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CloudUserRepository(context) }
    var privacy by remember { mutableStateOf("everyone") }
    var helper by remember { mutableStateOf("Saved locally until Cloud privacy rules are enabled.") }
    var readReceipts by remember { mutableStateOf(true) }
    var cloudNotifications by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        repository.chatPreferences()
            .onSuccess {
                readReceipts = it.readReceiptsEnabled
                cloudNotifications = it.notificationsEnabled
                helper = "Cloud chat preferences loaded"
            }
            .onFailure {
                helper = "Cloud chat preferences will sync when your cloud account is reachable."
            }
    }

    OptionRow("Last active privacy", privacy, listOf("everyone", "contacts", "none")) {
        privacy = it
        scope.launch {
            helper = "Saved for $userId"
        }
    }
    ToggleRow("Read receipts", readReceipts) { enabled ->
        readReceipts = enabled
        scope.launch {
            repository.updateChatPreferences(
                CloudChatPreferences(
                    readReceiptsEnabled = readReceipts,
                    notificationsEnabled = cloudNotifications
                )
            ).onSuccess {
                helper = "Cloud read receipts saved"
            }.onFailure {
                helper = "Could not save read receipts. Cached setting will remain visible."
            }
        }
    }
    ToggleRow("Cloud chat notifications", cloudNotifications) { enabled ->
        cloudNotifications = enabled
        scope.launch {
            repository.updateChatPreferences(
                CloudChatPreferences(
                    readReceiptsEnabled = readReceipts,
                    notificationsEnabled = cloudNotifications
                )
            ).onSuccess {
                helper = "Cloud notification preference saved"
            }.onFailure {
                helper = "Could not save notification preference. Cached setting will remain visible."
            }
        }
    }
    Text(helper, color = HelloColors.DarkTextMuted, modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs))
}

@Composable
private fun BrowserDataRows() {
    val browserViewModel: BrowserViewModel = viewModel()
    val browserState by browserViewModel.state.collectAsState()
    val activeProfileId = browserState.activeProfileId
    val activeProfileName = browserState.activeProfile?.name ?: "Default"
    var pendingClearRange by remember { mutableStateOf<BrowserClearRange?>(null) }

    Text(
        "Active browser profile: $activeProfileName",
        color = HelloColors.DarkTextMuted,
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
    )
    HelloSettingsRow(
        title = "Cookies and site data",
        subtitle = "Choose a Chrome-style time range for this profile only.",
        leading = { RowIcon(Icons.Default.Public) }
    )
    Column(
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
    ) {
        BrowserClearRange.entries.forEach { range ->
            HelloSettingsRow(
                title = range.label,
                subtitle = "Clear cookies and site storage for $activeProfileName",
                onClick = { pendingClearRange = range }
            )
        }
    }
    browserState.statusMessage?.let { message ->
        Text(
            message,
            color = HelloColors.DarkAccent,
            modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
        )
    }
    pendingClearRange?.let { range ->
        AlertDialog(
            onDismissRequest = { pendingClearRange = null },
            containerColor = HelloColors.DarkPanelStrong,
            title = { Text("Clear ${range.label.lowercase()}?", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This clears cookies and site data for $activeProfileName only. Other browser profiles keep their data.",
                    color = HelloColors.DarkTextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        browserViewModel.clearCookies(activeProfileId, range)
                        pendingClearRange = null
                    }
                ) {
                    Text("Clear", color = HelloColors.DarkAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearRange = null }) {
                    Text("Cancel", color = HelloColors.DarkTextMuted)
                }
            }
        )
    }
    Text(
        "History, downloads, passwords, cookies, and site data are partitioned by browser profile.",
        color = HelloColors.DarkTextMuted,
        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xs)
    )
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
