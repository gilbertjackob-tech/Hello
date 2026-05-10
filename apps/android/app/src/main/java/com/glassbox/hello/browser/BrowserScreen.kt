package com.glassbox.hello.browser

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.R
import com.glassbox.hello.core.HelloPreferences
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.ui.components.HelloFilterChip
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloListItem
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class BrowserToolTab(val label: String) {
    Profiles("Profiles"),
    History("History"),
    Downloads("Downloads"),
    Passwords("Passwords"),
    Inspect("Inspect"),
    Request("Request")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    launchUrl: String? = null,
    launchProfileId: String? = null,
    launchTabId: String? = null,
    showReturnBubble: Boolean = false,
    onReturnToHello: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: BrowserViewModel = viewModel()
    val uiState by viewModel.state.collectAsState()
    val settingsState by rememberHelloSettingsState(context)
    val scope = rememberCoroutineScope()

    var selectedToolTab by rememberSaveable { mutableStateOf(BrowserToolTab.Profiles) }
    var addressInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(DEFAULT_BROWSER_HOME_URL)) }
    var addressBarFocused by remember { mutableStateOf(false) }
    var newProfileOpen by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var newProfileEmail by rememberSaveable { mutableStateOf("") }
    var toolsVisible by rememberSaveable { mutableStateOf(false) }
    var querySelector by rememberSaveable { mutableStateOf("button") }
    var requestMethod by rememberSaveable { mutableStateOf("GET") }
    var requestUrl by rememberSaveable { mutableStateOf("") }
    var requestHeaders by rememberSaveable { mutableStateOf("Accept: */*") }
    var requestBody by rememberSaveable { mutableStateOf("") }
    var passwordOrigin by rememberSaveable { mutableStateOf("") }
    var passwordUsername by rememberSaveable { mutableStateOf("") }
    var passwordValue by rememberSaveable { mutableStateOf("") }
    var activeSession by remember { mutableStateOf<BrowserTabSession?>(null) }
    var pendingFileChooser by remember { mutableStateOf<((Array<Uri>?) -> Unit)?>(null) }
    var launchHandled by rememberSaveable { mutableStateOf(false) }
    var tabsVisible by rememberSaveable { mutableStateOf(false) }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFileChooser?.invoke(uris?.toTypedArray())
        pendingFileChooser = null
    }

    val runtime = remember(context, viewModel, scope) {
        BrowserRuntime(
            context = context,
            viewModel = viewModel,
            scope = scope,
            onFileChooserRequest = { acceptTypes, _, callback ->
                pendingFileChooser = callback
                fileChooserLauncher.launch(
                    if (acceptTypes.isNotBlank()) {
                        acceptTypes.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toTypedArray()
                    } else {
                        arrayOf("*/*")
                    }
                )
            }
        )
    }

    LaunchedEffect(launchHandled, launchProfileId, launchTabId, launchUrl) {
        if (launchHandled) return@LaunchedEffect
        launchHandled = true
        when {
            !launchTabId.isNullOrBlank() -> viewModel.selectTab(launchTabId)
            else -> {
                if (!launchProfileId.isNullOrBlank()) {
                    viewModel.selectProfileAndTab(launchProfileId)
                }
                if (!launchUrl.isNullOrBlank()) {
                    viewModel.createTab(profileId = viewModel.state.value.activeProfileId, url = launchUrl)
                }
            }
        }
    }

    LaunchedEffect(uiState.activeProfileId) {
        runtime.syncProfile(uiState.activeProfileId)
        uiState.activeTab?.let { activeSession = runtime.ensureSession(it) }
    }

    LaunchedEffect(uiState.activeTabId) {
        uiState.activeTab?.let {
            activeSession = runtime.ensureSession(it)
            addressInput = TextFieldValue(it.url)
        }
    }

    LaunchedEffect(uiState.activeTab?.url) {
        uiState.activeTab?.url?.let { url ->
            if (!addressBarFocused) {
                addressInput = TextFieldValue(url)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runtime.closeAll() }
    }

    fun navigateActiveTab(rawValue: String) {
        uiState.activeTab?.let { tab ->
            val normalized = normalizeBrowserUrl(rawValue)
            runtime.loadUrl(tab.id, normalized)
            viewModel.updateTabState(tab.id) { current ->
                current.copy(url = normalized, title = if (normalized == DEFAULT_BROWSER_HOME_URL) "Google" else current.title)
            }
            addressInput = TextFieldValue(normalized)
        }
    }

    fun openTool(tab: BrowserToolTab) {
        selectedToolTab = tab
        toolsVisible = true
    }

    val isHomeTab = uiState.activeTab?.url == DEFAULT_BROWSER_HOME_URL

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isHomeTab) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                ) {
                    ClassicBrowserChrome(
                        addressInput = addressInput,
                        activeProfileName = uiState.activeProfile?.name ?: "Default",
                        canGoBack = uiState.activeTab?.canGoBack == true,
                        canGoForward = uiState.activeTab?.canGoForward == true,
                        isLoading = uiState.activeTab?.isLoading == true,
                        progress = uiState.activeTab?.progress ?: 0,
                        onAddressInputChange = { addressInput = it },
                        onAddressFocusChanged = { focused ->
                            if (focused && !addressBarFocused) {
                                addressInput = addressInput.copy(selection = TextRange(0, addressInput.text.length))
                            }
                            addressBarFocused = focused
                        },
                        onGo = { navigateActiveTab(addressInput.text) },
                        onBack = { uiState.activeTab?.let { runtime.goBack(it.id) } },
                        onForward = { uiState.activeTab?.let { runtime.goForward(it.id) } },
                        onReload = { uiState.activeTab?.let { runtime.reload(it.id) } },
                        onStop = { uiState.activeTab?.let { runtime.stop(it.id) } },
                        onOpenProfile = { openTool(BrowserToolTab.Profiles) },
                        onOpenTools = { toolsVisible = true }
                    )

                    uiState.errorMessage?.let { message ->
                        BrowserStatusBanner(
                            text = message,
                            tone = HelloColors.DarkDanger.copy(alpha = 0.18f),
                            textColor = HelloColors.DarkText
                        )
                    } ?: uiState.statusMessage?.let { message ->
                        BrowserStatusBanner(
                            text = message,
                            tone = HelloColors.DarkAccentSoft,
                            textColor = HelloColors.DarkText
                        )
                    }
                }
            }

            BrowserCanvas(
                session = activeSession,
                activeTab = uiState.activeTab,
                activeProfile = uiState.activeProfile,
                addressInput = addressInput,
                onAddressInputChange = { addressInput = it },
                onAddressFocusChanged = { focused ->
                    if (focused && !addressBarFocused) {
                        addressInput = addressInput.copy(selection = TextRange(0, addressInput.text.length))
                    }
                    addressBarFocused = focused
                },
                onNavigate = { navigateActiveTab(it) },
                onOpenProfile = { openTool(BrowserToolTab.Profiles) },
                onOpenTool = { openTool(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            BottomBrowserBar(
                canGoBack = uiState.activeTab?.canGoBack == true,
                canGoForward = uiState.activeTab?.canGoForward == true,
                tabCount = uiState.activeTabs.size,
                onBack = { uiState.activeTab?.let { runtime.goBack(it.id) } },
                onForward = { uiState.activeTab?.let { runtime.goForward(it.id) } },
                onNewTab = { viewModel.createTab() },
                onTabs = { tabsVisible = true },
                onMenu = { toolsVisible = true }
            )
        }

        if (showReturnBubble && onReturnToHello != null) {
            FloatingHelloBubble(
                modifier = Modifier.matchParentSize(),
                onClick = onReturnToHello
            )
        }
    }

    if (toolsVisible) {
        ModalBottomSheet(
            onDismissRequest = { toolsVisible = false },
            containerColor = HelloColors.DarkBgStrong,
            contentColor = HelloColors.DarkText,
            dragHandle = null
        ) {
            BrowserToolsSheet(
                selectedToolTab = selectedToolTab,
                onSelectTool = { selectedToolTab = it },
                activeProfileName = uiState.activeProfile?.name ?: "Default",
                themeMode = settingsState.themeMode,
                onCreateProfile = {
                    toolsVisible = false
                    newProfileOpen = true
                },
                onSetThemeMode = { mode -> HelloPreferences.setThemeMode(context, mode) },
                content = {
                    when (selectedToolTab) {
                        BrowserToolTab.Profiles -> ProfilesPanel(
                            profiles = uiState.profiles,
                            activeProfileId = uiState.activeProfileId,
                            onProfileClick = { profileId ->
                                viewModel.selectProfileAndTab(profileId)
                            },
                            onNewProfileClick = {
                                toolsVisible = false
                                newProfileOpen = true
                            }
                        )
                        BrowserToolTab.History -> HistoryPanel(
                            history = uiState.history,
                            onOpen = { url ->
                                val activeTab = uiState.activeTab
                                if (activeTab != null) {
                                    runtime.loadUrl(activeTab.id, url)
                                    viewModel.updateTabState(activeTab.id) { current -> current.copy(url = url) }
                                } else {
                                    viewModel.createTab(url = url)
                                }
                                toolsVisible = false
                            },
                            onClear = { viewModel.clearHistory(uiState.activeProfileId) }
                        )
                        BrowserToolTab.Downloads -> DownloadsPanel(downloads = uiState.downloads)
                        BrowserToolTab.Passwords -> PasswordsPanel(
                            passwords = uiState.passwords,
                            origin = passwordOrigin,
                            username = passwordUsername,
                            password = passwordValue,
                            onOriginChange = { passwordOrigin = it },
                            onUsernameChange = { passwordUsername = it },
                            onPasswordChange = { passwordValue = it },
                            onSave = {
                                val origin = passwordOrigin.ifBlank { viewModel.resolveActiveOrigin().orEmpty() }.trim()
                                viewModel.addPassword(uiState.activeProfileId, origin, passwordUsername, passwordValue)
                                viewModel.setStatusMessage("Saved password")
                            },
                            onAutofill = { record ->
                                passwordOrigin = record.origin
                                passwordUsername = record.username
                                passwordValue = record.password
                            }
                        )
                        BrowserToolTab.Inspect -> InspectPanel(
                            querySelector = querySelector,
                            onQuerySelectorChange = { querySelector = it },
                            domSnapshot = uiState.domSnapshot,
                            queryResult = uiState.queryResult,
                            actionTargets = uiState.actionTargets,
                            onQuery = {
                                uiState.activeTab?.let { tab ->
                                    scope.launch { viewModel.setQueryResult(runtime.query(tab.id, querySelector)) }
                                }
                            },
                            onSnapshot = {
                                uiState.activeTab?.let { tab ->
                                    scope.launch {
                                        viewModel.setSummary(runtime.captureSummary(tab.id))
                                        viewModel.setDomSnapshot(runtime.captureDomSnapshot(tab.id))
                                    }
                                }
                            },
                            onTargets = {
                                uiState.activeTab?.let { tab ->
                                    scope.launch { viewModel.setActionTargets(runtime.captureActionTargets(tab.id)) }
                                }
                            }
                        )
                        BrowserToolTab.Request -> RequestPanel(
                            method = requestMethod,
                            url = requestUrl.ifBlank { uiState.activeTab?.url.orEmpty() },
                            headers = requestHeaders,
                            body = requestBody,
                            response = uiState.requestResult,
                            onMethodChange = { requestMethod = it },
                            onUrlChange = { requestUrl = it },
                            onHeadersChange = { requestHeaders = it },
                            onBodyChange = { requestBody = it },
                            onSend = {
                                uiState.activeTab?.let { tab ->
                                    scope.launch {
                                        viewModel.setRequestResult(
                                            runtime.request(
                                                tabId = tab.id,
                                                method = requestMethod,
                                                url = requestUrl.ifBlank { tab.url },
                                                headersText = requestHeaders,
                                                bodyText = requestBody
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    }

    if (newProfileOpen) {
        AlertDialog(
            onDismissRequest = { newProfileOpen = false },
            title = { Text("Create profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProfileEmail,
                        onValueChange = { newProfileEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createProfile(newProfileName, newProfileEmail)
                        newProfileName = ""
                        newProfileEmail = ""
                        newProfileOpen = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { newProfileOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (tabsVisible) {
        ModalBottomSheet(
            onDismissRequest = { tabsVisible = false },
            containerColor = HelloColors.DarkBgStrong,
            contentColor = HelloColors.DarkText,
            dragHandle = null
        ) {
            TabsSheet(
                tabs = uiState.activeTabs,
                activeTabId = uiState.activeTabId,
                onSelectTab = {
                    viewModel.selectTab(it)
                    tabsVisible = false
                },
                onCloseTab = { tabId ->
                    if (uiState.activeTabId == tabId) {
                        runtime.closeSession(tabId)
                    }
                    viewModel.closeTab(tabId)
                },
                onNewTab = {
                    viewModel.createTab()
                    tabsVisible = false
                }
            )
        }
    }
}

@Composable
private fun ClassicBrowserChrome(
    addressInput: TextFieldValue,
    activeProfileName: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    progress: Int,
    onAddressInputChange: (TextFieldValue) -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTools: () -> Unit
) {
    HelloPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(HelloSpacing.Sm), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
            ) {
                BrowserNavButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    active = canGoBack,
                    onClick = onBack
                )
                BrowserNavButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    active = canGoForward,
                    onClick = onForward
                )
                BrowserNavButton(
                    icon = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Stop" else "Reload",
                    active = true,
                    onClick = if (isLoading) onStop else onReload
                )
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = onAddressInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onAddressFocusChanged(it.isFocused) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = HelloColors.DarkTextMuted)
                    },
                    placeholder = { Text("Search or enter address") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onGo() }, onGo = { onGo() })
                )
                HelloFilterChip(
                    label = activeProfileName,
                    active = true,
                    onClick = onOpenProfile,
                    dark = true
                )
                BrowserNavButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Open browser tools",
                    active = true,
                    onClick = onOpenTools
                )
            }
            BrowserProgressBar(progress = progress, visible = isLoading)
        }
    }
}

@Composable
private fun BrowserProgressBar(progress: Int, visible: Boolean) {
    val clamped = progress.coerceIn(0, 100)
    val alpha = if (visible || clamped in 1..99) 1f else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(HelloColors.DarkBorder.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped / 100f)
                .height(3.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkAccent.copy(alpha = alpha))
        )
    }
}

@Composable
private fun ClassicTabStrip(
    tabs: List<BrowserTabRecord>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    HelloPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = false,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                Surface(
                    onClick = { onSelectTab(tab.id) },
                    shape = HelloShapes.Pill,
                    color = if (tab.id == activeTabId) HelloColors.DarkAccentSoft else HelloColors.DarkPanelMuted,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (tab.id == activeTabId) HelloColors.DarkAccent else HelloColors.DarkBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = if (tab.id == activeTabId) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tab.title.ifBlank { tab.url },
                            color = HelloColors.DarkText,
                            maxLines = 1
                        )
                        IconButton(onClick = { onCloseTab(tab.id) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab", tint = HelloColors.DarkTextMuted)
                        }
                    }
                }
            }
            HelloIconButton(onClick = onNewTab, active = true) {
                Icon(Icons.Default.Add, contentDescription = "New tab", tint = HelloColors.DarkAccent)
            }
        }
    }
}

@Composable
private fun BrowserCanvas(
    session: BrowserTabSession?,
    activeTab: BrowserTabRecord?,
    activeProfile: BrowserProfileRecord?,
    addressInput: TextFieldValue,
    onAddressInputChange: (TextFieldValue) -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTool: (BrowserToolTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(if (activeTab?.url == DEFAULT_BROWSER_HOME_URL) HelloColors.DarkBg else Color.Black)) {
        if (activeTab?.url == DEFAULT_BROWSER_HOME_URL) {
            GlassBoxHomePage(
                activeProfile = activeProfile,
                addressInput = addressInput,
                onAddressInputChange = onAddressInputChange,
                onAddressFocusChanged = onAddressFocusChanged,
                onNavigate = onNavigate,
                onOpenProfile = onOpenProfile,
                onOpenTool = onOpenTool,
                modifier = Modifier.fillMaxSize()
            )
        } else if (session != null) {
            key(session.tabId) {
                AndroidView(
                    factory = { session.webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(HelloSpacing.Xxl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No active tab", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(HelloSpacing.Sm))
                Text(
                    "Open a new tab to start browsing.",
                    color = HelloColors.DarkTextMuted
                )
            }
        }
    }
}

@Composable
private fun GlassBoxHomePage(
    activeProfile: BrowserProfileRecord?,
    addressInput: TextFieldValue,
    onAddressInputChange: (TextFieldValue) -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTool: (BrowserToolTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { onOpenTool(BrowserToolTab.Request) },
                shape = RoundedCornerShape(18.dp),
                color = HelloColors.DarkPanelStrong,
                border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorderStrong)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = "Browser tools",
                    tint = HelloColors.DarkAccent,
                    modifier = Modifier.padding(14.dp).size(22.dp)
                )
            }
            ProfileAvatar(
                profile = activeProfile,
                onClick = onOpenProfile
            )
        }

        GoogleWordmark(modifier = Modifier.align(Alignment.CenterHorizontally))

        OutlinedTextField(
            value = addressInput,
            onValueChange = onAddressInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onFocusChanged { state ->
                    onAddressFocusChanged(state.isFocused)
                },
            singleLine = true,
            shape = RoundedCornerShape(32.dp),
            leadingIcon = {
                GoogleGMark()
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = HelloColors.DarkTextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            },
            placeholder = { Text("Search Google or type URL") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNavigate(addressInput.text) }, onGo = { onNavigate(addressInput.text) })
        )

        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md), modifier = Modifier.fillMaxWidth()) {
            HomePill(label = "Google", modifier = Modifier.weight(1f), onClick = { onNavigate("google") })
            HomePill(label = "Incognito", modifier = Modifier.weight(1f), onClick = { onOpenTool(BrowserToolTab.Profiles) })
        }

        HomeSection(title = "Common sites") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
            ) {
                SiteShortcut(label = "Google", symbol = "G", onClick = { onNavigate("google") })
                SiteShortcut(label = "YouTube", symbol = "Y", onClick = { onNavigate("youtube") })
                SiteShortcut(label = "Gmail", symbol = "M", onClick = { onNavigate("mail") })
                SiteShortcut(label = "Facebook", symbol = "F", onClick = { onNavigate("facebook") })
                SiteShortcut(label = "WhatsApp", symbol = "W", onClick = { onNavigate("whatsapp") })
                SiteShortcut(label = "Add", symbol = "+", onClick = { onOpenTool(BrowserToolTab.History) })
            }
        }

        HomeSection(title = "Shortcuts") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HomeAction(label = "Profiles", icon = Icons.Default.Person, onClick = onOpenProfile)
                HomeAction(label = "History", icon = Icons.Default.History, onClick = { onOpenTool(BrowserToolTab.History) })
                HomeAction(label = "Downloads", icon = Icons.Default.Download, onClick = { onOpenTool(BrowserToolTab.Downloads) })
                HomeAction(label = "Settings", icon = Icons.Default.Settings, onClick = { onOpenTool(BrowserToolTab.Profiles) })
            }
        }
    }
}

@Composable
private fun GoogleWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        val letters = listOf(
            "G" to Color(0xFF4285F4),
            "o" to Color(0xFFEA4335),
            "o" to Color(0xFFFBBC05),
            "g" to Color(0xFF4285F4),
            "l" to Color(0xFF34A853),
            "e" to Color(0xFFEA4335)
        )
        letters.forEach { (letter, color) ->
            Text(letter, color = color, fontWeight = FontWeight.Bold, fontSize = 56.sp)
        }
    }
}

@Composable
private fun GoogleGMark() {
    Text(
        text = "G",
        color = Color(0xFF4285F4),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun HomePill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(28.dp),
        color = HelloColors.DarkPanelMuted,
        border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = HelloColors.DarkText, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    content: @Composable () -> Unit
) {
    HelloPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(HelloSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SiteShortcut(
    label: String,
    symbol: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            color = HelloColors.DarkPanelMuted,
            border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(symbol, color = HelloColors.DarkAccentStrong, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = HelloColors.DarkTextMuted, maxLines = 1)
    }
}

@Composable
private fun HomeAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            shape = RoundedCornerShape(16.dp),
            color = HelloColors.DarkAccentSoft,
            border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = HelloColors.DarkAccentStrong)
            }
        }
        Text(label, color = HelloColors.DarkTextMuted, maxLines = 1)
    }
}

@Composable
private fun ProfileAvatar(
    profile: BrowserProfileRecord?,
    onClick: () -> Unit
) {
    val initial = profile?.email?.firstOrNull()?.uppercaseChar()?.toString()
        ?: profile?.name?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "G"
    Surface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = HelloColors.DarkAccentSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkAccent)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initial, color = HelloColors.DarkAccentStrong, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomBrowserBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelloColors.DarkBgStrong)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrowserNavButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", canGoBack, onBack)
        BrowserNavButton(Icons.AutoMirrored.Filled.ArrowForward, "Forward", canGoForward, onForward)
        HelloIconButton(onClick = onNewTab, active = true) {
            Icon(Icons.Default.Add, contentDescription = "New tab", tint = HelloColors.DarkAccent)
        }
        Surface(
            onClick = onTabs,
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, HelloColors.DarkTextMuted)
        ) {
            Text(
                text = tabCount.coerceAtLeast(1).toString(),
                color = HelloColors.DarkText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        BrowserNavButton(Icons.Default.MoreVert, "Menu", true, onMenu)
    }
}

@Composable
private fun TabsSheet(
    tabs: List<BrowserTabRecord>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tabs", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            HelloIconButton(onClick = onNewTab, active = true) {
                Icon(Icons.Default.Add, contentDescription = "New tab", tint = HelloColors.DarkAccent)
            }
        }
        tabs.forEach { tab ->
            Surface(
                onClick = { onSelectTab(tab.id) },
                shape = RoundedCornerShape(18.dp),
                color = if (tab.id == activeTabId) HelloColors.DarkAccentSoft else HelloColors.DarkPanelMuted,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (tab.id == activeTabId) HelloColors.DarkAccent else HelloColors.DarkBorder
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(HelloSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = HelloColors.DarkAccent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tab.title.ifBlank { "New Tab" }, color = HelloColors.DarkText, fontWeight = FontWeight.Medium)
                        Text(tab.url, color = HelloColors.DarkTextMuted, maxLines = 1)
                    }
                    IconButton(onClick = { onCloseTab(tab.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close tab", tint = HelloColors.DarkTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserStatusBanner(
    text: String,
    tone: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tone)
            .border(1.dp, HelloColors.DarkBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm)
    ) {
        Text(text = text, color = textColor)
    }
}

@Composable
private fun BrowserNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    HelloIconButton(onClick = onClick, active = active) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) HelloColors.DarkAccent else HelloColors.DarkTextMuted
        )
    }
}

@Composable
private fun BrowserToolsSheet(
    selectedToolTab: BrowserToolTab,
    onSelectTool: (BrowserToolTab) -> Unit,
    activeProfileName: String,
    themeMode: String,
    onCreateProfile: () -> Unit,
    onSetThemeMode: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xxs)) {
            Text("GlassBox menu", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text("Profile: $activeProfileName", color = HelloColors.DarkTextMuted)
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            MenuQuickAction("Settings", Icons.Default.Settings, onClick = { onSelectTool(BrowserToolTab.Profiles) })
            MenuQuickAction("History", Icons.Default.History, onClick = { onSelectTool(BrowserToolTab.History) })
            MenuQuickAction("Downloads", Icons.Default.Download, onClick = { onSelectTool(BrowserToolTab.Downloads) })
            MenuQuickAction("Passwords", Icons.Default.Lock, onClick = { onSelectTool(BrowserToolTab.Passwords) })
            MenuQuickAction("New Profile", Icons.Default.Add, onClick = onCreateProfile)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Theme", color = HelloColors.DarkText, fontWeight = FontWeight.Medium)
            listOf("system", "light", "dark").forEach { mode ->
                HelloFilterChip(
                    label = mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    active = themeMode.equals(mode, ignoreCase = true),
                    onClick = { onSetThemeMode(mode) },
                    dark = true
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            BrowserToolTab.entries.forEach { tab ->
                HelloFilterChip(
                    label = tab.label,
                    active = tab == selectedToolTab,
                    onClick = { onSelectTool(tab) },
                    dark = true
                )
            }
        }
        HorizontalDivider(color = HelloColors.DarkBorder)
        content()
    }
}

@Composable
private fun MenuQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(18.dp),
            color = HelloColors.DarkPanelMuted,
            border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = HelloColors.DarkAccentStrong)
            }
        }
        Text(label, color = HelloColors.DarkText, maxLines = 1)
    }
}

@Composable
private fun ProfilesPanel(
    profiles: List<BrowserProfileRecord>,
    activeProfileId: String,
    onProfileClick: (String) -> Unit,
    onNewProfileClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Text("Profiles", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
        profiles.forEach { profile ->
            HelloListItem(
                title = profile.name,
                subtitle = profile.email ?: if (profile.id == activeProfileId) "Active profile" else profile.id,
                leading = {
                    HelloIconButton(onClick = { onProfileClick(profile.id) }, active = profile.id == activeProfileId) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = HelloColors.DarkAccent)
                    }
                }
            )
        }
        TextButton(onClick = onNewProfileClick) {
            Text("Create new profile")
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<BrowserHistoryRecord>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("History", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
        if (history.isEmpty()) {
            Text("No history yet.", color = HelloColors.DarkTextMuted)
        } else {
            history.take(24).forEach { entry ->
                HelloListItem(
                    title = entry.title,
                    subtitle = entry.url,
                    leading = {
                        HelloIconButton(onClick = { onOpen(entry.url) }, active = true) {
                            Icon(Icons.Default.History, contentDescription = null, tint = HelloColors.DarkAccent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadsPanel(downloads: List<BrowserDownloadRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Text("Downloads", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
        if (downloads.isEmpty()) {
            Text("No downloads yet.", color = HelloColors.DarkTextMuted)
        } else {
            downloads.take(24).forEach { item ->
                HelloListItem(
                    title = item.fileName,
                    subtitle = item.url,
                    leading = {
                        HelloIconButton(onClick = {}, active = true) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = HelloColors.DarkAccent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PasswordsPanel(
    passwords: List<BrowserPasswordRecord>,
    origin: String,
    username: String,
    password: String,
    onOriginChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onAutofill: (BrowserPasswordRecord) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Text("Passwords", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
        OutlinedTextField(
            value = origin,
            onValueChange = onOriginChange,
            label = { Text("Origin") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSave) {
            Text("Save password")
        }
        if (passwords.isEmpty()) {
            Text("No saved passwords yet.", color = HelloColors.DarkTextMuted)
        } else {
            passwords.take(20).forEach { record ->
                HelloListItem(
                    title = record.origin,
                    subtitle = record.username,
                    leading = {
                        HelloIconButton(onClick = { onAutofill(record) }, active = true) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = HelloColors.DarkAccent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InspectPanel(
    querySelector: String,
    onQuerySelectorChange: (String) -> Unit,
    domSnapshot: List<BrowserDomNode>,
    queryResult: List<BrowserDomNode>,
    actionTargets: List<BrowserActionTarget>,
    onQuery: () -> Unit,
    onSnapshot: () -> Unit,
    onTargets: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Text("Inspect", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
        OutlinedTextField(
            value = querySelector,
            onValueChange = onQuerySelectorChange,
            label = { Text("CSS selector") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            Button(onClick = onQuery) { Text("Query") }
            Button(onClick = onSnapshot) { Text("Snapshot") }
            Button(onClick = onTargets) { Text("Targets") }
        }
        if (queryResult.isNotEmpty()) {
            Text("Query results", fontWeight = FontWeight.Medium, color = HelloColors.DarkText)
            queryResult.take(10).forEach { node ->
                Text("${node.tag} ${node.text ?: node.selector.orEmpty()}", color = HelloColors.DarkTextMuted)
            }
        }
        if (domSnapshot.isNotEmpty()) {
            Text("DOM snapshot", fontWeight = FontWeight.Medium, color = HelloColors.DarkText)
            domSnapshot.take(10).forEach { node ->
                Text("${node.tag} ${node.text ?: node.placeholder ?: node.selector.orEmpty()}", color = HelloColors.DarkTextMuted)
            }
        }
        if (actionTargets.isNotEmpty()) {
            Text("Action targets", fontWeight = FontWeight.Medium, color = HelloColors.DarkText)
            actionTargets.take(10).forEach { target ->
                Text("${target.tag ?: "node"} ${target.label ?: target.selector}", color = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
private fun RequestPanel(
    method: String,
    url: String,
    headers: String,
    body: String,
    response: String?,
    onMethodChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Text("Request", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
        OutlinedTextField(
            value = method,
            onValueChange = onMethodChange,
            label = { Text("Method") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = headers,
            onValueChange = onHeadersChange,
            label = { Text("Headers") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            label = { Text("Body") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Button(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send")
        }
        response?.let {
            Text("Response", fontWeight = FontWeight.Medium, color = HelloColors.DarkText)
            Text(it, color = HelloColors.DarkTextMuted)
        }
    }
}

@Composable
private fun FloatingHelloBubble(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(BUBBLE_PREFERENCES, Context.MODE_PRIVATE)
    }
    BoxWithConstraints(modifier = modifier) {
        val bubbleSize = 60.dp
        val bubblePadding = 20.dp
        val bubbleSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { bubbleSize.toPx() }
        val paddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { bubblePadding.toPx() }
        val bubbleScope = rememberCoroutineScope()
        val maxX = (constraints.maxWidth.toFloat() - bubbleSizePx - paddingPx).coerceAtLeast(paddingPx)
        val maxY = (constraints.maxHeight.toFloat() - bubbleSizePx - paddingPx).coerceAtLeast(paddingPx)
        val offsetX = remember { Animatable(0f) }
        val offsetY = remember { Animatable(0f) }
        var initialized by remember { mutableStateOf(false) }

        LaunchedEffect(maxX, maxY) {
            val storedX = prefs.getFloat(BUBBLE_KEY_X, Float.NaN)
            val storedY = prefs.getFloat(BUBBLE_KEY_Y, Float.NaN)
            val startX = if (storedX.isNaN()) maxX else storedX.coerceIn(paddingPx, maxX)
            val startY = if (storedY.isNaN()) (maxY * 0.78f).coerceIn(paddingPx, maxY) else storedY.coerceIn(paddingPx, maxY)
            if (!initialized) {
                offsetX.snapTo(startX)
                offsetY.snapTo(startY)
                initialized = true
            } else {
                offsetX.snapTo(offsetX.value.coerceIn(paddingPx, maxX))
                offsetY.snapTo(offsetY.value.coerceIn(paddingPx, maxY))
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(bubbleSize)
                .clip(CircleShape)
                .background(HelloColors.DarkPanelStrong.copy(alpha = 0.96f))
                .border(1.dp, HelloColors.DarkBorderStrong, CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            bubbleScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(paddingPx, maxX))
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(paddingPx, maxY))
                            }
                        },
                        onDragEnd = {
                            val snapTargetX = if (offsetX.value + (bubbleSizePx / 2f) < constraints.maxWidth / 2f) {
                                paddingPx
                            } else {
                                maxX
                            }
                            bubbleScope.launch {
                                offsetX.animateTo(snapTargetX, animationSpec = tween(durationMillis = 220))
                                prefs.edit()
                                    .putFloat(BUBBLE_KEY_X, offsetX.value)
                                    .putFloat(BUBBLE_KEY_Y, offsetY.value)
                                    .apply()
                            }
                        }
                    )
                }
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_round),
                    contentDescription = "Return to Hello",
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private const val BUBBLE_PREFERENCES = "browser_fab_position"
private const val BUBBLE_KEY_X = "x"
private const val BUBBLE_KEY_Y = "y"
