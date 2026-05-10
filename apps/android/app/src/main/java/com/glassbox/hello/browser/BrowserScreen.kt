package com.glassbox.hello.browser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.glassbox.hello.ui.components.HelloFilterChip
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloListItem
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.safeDrawingPadding

private enum class BrowserToolTab(val label: String) {
    Summary("Summary"),
    History("History"),
    Downloads("Downloads"),
    Passwords("Passwords"),
    Inspect("Inspect"),
    Request("Request")
}

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: BrowserViewModel = viewModel()
    val uiState by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedToolTab by remember { mutableStateOf(BrowserToolTab.Summary) }
    var addressInput by rememberSaveable { mutableStateOf("") }
    var newProfileOpen by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var newProfileEmail by rememberSaveable { mutableStateOf("") }
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

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFileChooser?.invoke(uris?.toTypedArray())
        pendingFileChooser = null
    }

    val runtime = remember(context, viewModel, scope) {
        BrowserRuntime(
            context = context,
            viewModel = viewModel,
            scope = scope,
            onFileChooserRequest = { acceptTypes, multiple, callback ->
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

    LaunchedEffect(uiState.activeProfileId) {
        runtime.syncProfile(uiState.activeProfileId)
        uiState.activeTab?.let { activeSession = runtime.ensureSession(it) }
    }

    LaunchedEffect(uiState.activeTabId) {
        uiState.activeTab?.let {
            activeSession = runtime.ensureSession(it)
            addressInput = it.url
        }
    }

    LaunchedEffect(uiState.activeTab?.url) {
        uiState.activeTab?.url?.let { addressInput = it }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runtime.closeAll() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Sm)
    ) {
        HelloTopBar(
            eyebrow = "GLASSBOX",
            title = "GlassBox Browser",
            modifier = Modifier.padding(bottom = HelloSpacing.Md)
        ) {
            HelloFilterChip(
                label = uiState.activeProfile?.name ?: "Default",
                active = true,
                onClick = { },
                dark = true
            )
        }

        ProfileRow(
            profiles = uiState.profiles,
            activeProfileId = uiState.activeProfileId,
            onProfileClick = viewModel::selectProfile,
            onNewProfileClick = { newProfileOpen = true }
        )

        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        TabStrip(
            tabs = uiState.activeTabs,
            activeTabId = uiState.activeTabId,
            onSelectTab = viewModel::selectTab,
            onCloseTab = { tabId ->
                if (uiState.activeTabId == tabId) {
                    runtime.closeSession(tabId)
                }
                viewModel.closeTab(tabId)
            },
            onNewTab = {
                viewModel.createTab()
                selectedToolTab = BrowserToolTab.Summary
            }
        )

        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        BrowserCommandBar(
            addressInput = addressInput,
            onAddressInputChange = { addressInput = it },
            onGo = {
                uiState.activeTab?.let { tab ->
                    runtime.loadUrl(tab.id, addressInput)
                    viewModel.updateTabState(tab.id) { current -> current.copy(url = addressInput) }
                }
            },
            onBack = { uiState.activeTab?.let { runtime.goBack(it.id) } },
            onForward = { uiState.activeTab?.let { runtime.goForward(it.id) } },
            onReload = { uiState.activeTab?.let { runtime.reload(it.id) } },
            onStop = { uiState.activeTab?.let { runtime.stop(it.id) } },
            onParse = {
                uiState.activeTab?.let { tab ->
                    scope.launch {
                        viewModel.setSummary(runtime.captureSummary(tab.id))
                        viewModel.setDomSnapshot(runtime.captureDomSnapshot(tab.id))
                        viewModel.setActionTargets(runtime.captureActionTargets(tab.id))
                    }
                }
                selectedToolTab = BrowserToolTab.Summary
            }
        )

        uiState.errorMessage?.let { message ->
            HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Md) {
                Text(
                    text = message,
                    color = HelloColors.DarkText,
                    modifier = Modifier.padding(HelloSpacing.Md),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                val session = activeSession
                if (session != null && uiState.activeTab != null) {
                    key(session.tabId) {
                        AndroidView(
                            factory = { session.webView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    HelloPanel(
                        modifier = Modifier.fillMaxSize(),
                        strong = true,
                        shape = HelloShapes.Xl
                    ) {
                        Column(
                            modifier = Modifier.padding(HelloSpacing.Xxl),
                            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                        ) {
                            Text("No browser tab selected", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                            Text(
                                "Create a tab to start browsing. Each profile keeps its own tabs, history, downloads, and saved browser data.",
                                color = HelloColors.DarkTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(HelloSpacing.Sm))

            HelloPanel(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                strong = true,
                shape = HelloShapes.Xl
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HelloSpacing.Md)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                ) {
                    ToolTabs(selectedToolTab = selectedToolTab, onSelect = { selectedToolTab = it })
                    when (selectedToolTab) {
                        BrowserToolTab.Summary -> SummaryPanel(
                            summary = uiState.activePageSummary,
                            onParse = {
                                uiState.activeTab?.let { tab ->
                                    scope.launch {
                                        viewModel.setSummary(runtime.captureSummary(tab.id))
                                        viewModel.setDomSnapshot(runtime.captureDomSnapshot(tab.id))
                                        viewModel.setActionTargets(runtime.captureActionTargets(tab.id))
                                    }
                                }
                            }
                        )
                        BrowserToolTab.History -> HistoryPanel(
                            history = uiState.history,
                            onOpen = { viewModel.createTab(url = it) },
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
                                val origin = (passwordOrigin.ifBlank { viewModel.resolveActiveOrigin().orEmpty() }).trim()
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
                                    scope.launch { viewModel.setDomSnapshot(runtime.captureDomSnapshot(tab.id)) }
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
            }
        }
    }

    if (newProfileOpen) {
        androidx.compose.material3.AlertDialog(
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
                Button(onClick = {
                    viewModel.createProfile(newProfileName, newProfileEmail)
                    newProfileOpen = false
                    newProfileName = ""
                    newProfileEmail = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newProfileOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    profiles: List<BrowserProfileRecord>,
    activeProfileId: String,
    onProfileClick: (String) -> Unit,
    onNewProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        profiles.forEach { profile ->
            HelloFilterChip(
                label = profile.name,
                active = profile.id == activeProfileId,
                onClick = { onProfileClick(profile.id) },
                dark = true
            )
        }
        HelloIconButton(onClick = onNewProfileClick, active = true) {
            Icon(Icons.Default.Add, contentDescription = "New profile", tint = HelloColors.DarkAccent)
        }
    }
}

@Composable
private fun TabStrip(
    tabs: List<BrowserTabRecord>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                ) {
                    Text(
                        text = tab.title.ifBlank { tab.url },
                        color = HelloColors.DarkText,
                        maxLines = 1
                    )
                    IconButton(onClick = { onCloseTab(tab.id) }, modifier = Modifier.height(20.dp)) {
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

@Composable
private fun BrowserCommandBar(
    addressInput: String,
    onAddressInputChange: (String) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onParse: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            BrowserNavButton(icon = Icons.Default.ArrowBack, contentDescription = "Back", onClick = onBack)
            BrowserNavButton(icon = Icons.Default.ArrowForward, contentDescription = "Forward", onClick = onForward)
            BrowserNavButton(icon = Icons.Default.Refresh, contentDescription = "Reload", onClick = onReload)
            BrowserNavButton(icon = Icons.Default.Close, contentDescription = "Stop", onClick = onStop)
            Button(onClick = onGo) {
                Text("Go")
            }
            BrowserNavButton(icon = Icons.Default.Terminal, contentDescription = "Parse page", onClick = onParse)
        }
        OutlinedTextField(
            value = addressInput,
            onValueChange = onAddressInputChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search or enter address") }
        )
    }
}

@Composable
private fun BrowserNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    HelloIconButton(onClick = onClick, active = true) {
        Icon(icon, contentDescription = contentDescription, tint = HelloColors.DarkAccent)
    }
}

@Composable
private fun ToolTabs(
    selectedToolTab: BrowserToolTab,
    onSelect: (BrowserToolTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        BrowserToolTab.values().forEach { tab ->
            HelloFilterChip(
                label = tab.label,
                active = tab == selectedToolTab,
                onClick = { onSelect(tab) },
                dark = true
            )
        }
    }
}

@Composable
private fun SummaryPanel(
    summary: BrowserPageSummary?,
    onParse: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Text("Page Summary", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
            HelloIconButton(onClick = onParse, active = true) {
                Icon(Icons.Default.Visibility, contentDescription = "Parse page", tint = HelloColors.DarkAccent)
            }
        }
        if (summary == null) {
            Text("Run parse to inspect the active page.", color = HelloColors.DarkTextMuted)
        } else {
            Text(summary.title.ifBlank { summary.url }, color = HelloColors.DarkText, fontWeight = FontWeight.SemiBold)
            Text(summary.url, color = HelloColors.DarkTextMuted)
            summary.description?.let { Text(it, color = HelloColors.DarkTextMuted) }
            if (summary.headings.isNotEmpty()) Text("Headings: ${summary.headings.joinToString(" · ")}", color = HelloColors.DarkTextMuted)
            if (summary.links.isNotEmpty()) Text("Links: ${summary.links.take(5).joinToString(" · ")}", color = HelloColors.DarkTextMuted)
            if (summary.buttons.isNotEmpty()) Text("Buttons: ${summary.buttons.take(5).joinToString(" · ")}", color = HelloColors.DarkTextMuted)
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
        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontWeight = FontWeight.Bold, color = HelloColors.DarkText)
            HelloIconButton(onClick = onClear, active = true) {
                Icon(Icons.Default.History, contentDescription = "Clear history", tint = HelloColors.DarkAccent)
            }
        }
        if (history.isEmpty()) {
            Text("No history yet.", color = HelloColors.DarkTextMuted)
        } else {
            history.take(20).forEach { entry ->
                HelloListItem(
                    title = entry.title,
                    subtitle = entry.url,
                    leading = {
                        HelloIconButton(onClick = { onOpen(entry.url) }, active = true) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = HelloColors.DarkAccent)
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
            downloads.take(20).forEach { item ->
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
        OutlinedTextField(value = origin, onValueChange = onOriginChange, label = { Text("Origin") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = onUsernameChange, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            Button(onClick = onSave) { Text("Save") }
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
        OutlinedTextField(value = querySelector, onValueChange = onQuerySelectorChange, label = { Text("CSS selector") }, modifier = Modifier.fillMaxWidth())
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
        OutlinedTextField(value = method, onValueChange = onMethodChange, label = { Text("Method") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = url, onValueChange = onUrlChange, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = headers, onValueChange = onHeadersChange, label = { Text("Headers") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(value = body, onValueChange = onBodyChange, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = onSend) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send")
        }
        response?.let {
            Text("Response", fontWeight = FontWeight.Medium, color = HelloColors.DarkText)
            Text(it, color = HelloColors.DarkTextMuted)
        }
    }
}
