package com.glassbox.hello.networkstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.glassbox.hello.auth.CloudAuthApi
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.familydrive.FamilyDrivePendingStore
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloSettingsCard
import com.glassbox.hello.ui.components.HelloSettingsRow
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NetworkStatusScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var networkStatus by remember { mutableStateOf<NetworkStatus>(NetworkStatus.Checking) }
    var checkedUrl by remember { mutableStateOf(AppConfig.DRIVE_HEALTH_URL) }
    var diagnosticText by remember { mutableStateOf("Not checked yet") }
    var cloudChatDiagnostic by remember { mutableStateOf("Cloud chat not checked yet") }
    var cloudChatOnline by remember { mutableStateOf(false) }
    var cloudAccountStatus by remember { mutableStateOf("Checking") }
    val pendingUploads by FamilyDrivePendingStore.getInstance(context).observeActive().collectAsState(initial = emptyList())

    fun checkNetworkStatus() {
        coroutineScope.launch {
            networkStatus = NetworkStatus.Checking
            checkedUrl = AppConfig.HELLO_STATUS_URL
            diagnosticText = "Checking..."
            cloudChatDiagnostic = "Checking cloud chat..."
            cloudAccountStatus = "Checking"
            try {
                val result = withContext(Dispatchers.IO) { checkPcDriveNetwork() }
                val cloudResult = withContext(Dispatchers.IO) { checkCloudChatNetwork() }
                val token = CloudSessionManager(context).token()
                val cloudAccountResult = if (token.isNullOrBlank()) {
                    "Cached only"
                } else {
                    CloudAuthApi().me(token)
                        .fold(onSuccess = { "Online: ${it.name}" }, onFailure = { "Offline: cached session" })
                }
                checkedUrl = result.checkedUrl
                diagnosticText = result.detail
                cloudChatDiagnostic = "${cloudResult.detail}\n${cloudResult.checkedUrl}"
                cloudChatOnline = cloudResult.status == NetworkStatus.Connected || cloudResult.status == NetworkStatus.HelloApiReachable
                cloudAccountStatus = cloudAccountResult
                networkStatus = result.status
            } catch (e: Exception) {
                checkedUrl = AppConfig.DRIVE_HEALTH_URL
                diagnosticText = e.message ?: "Unknown error"
                cloudAccountStatus = "Offline: cached session"
                networkStatus = NetworkStatus.Offline
            }
        }
    }

    LaunchedEffect(Unit) {
        checkNetworkStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HelloTopBar(
            eyebrow = "SERVICE STATUS",
            title = "Network",
            modifier = Modifier.padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Md)
        ) {
            if (onBack != null) {
                HelloIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
                }
            }
            HelloPill(
                text = when (networkStatus) {
                    NetworkStatus.Connected -> "Connected"
                    NetworkStatus.HelloApiReachable -> "API"
                    NetworkStatus.Checking -> "Checking"
                    else -> "Offline"
                },
                active = networkStatus == NetworkStatus.Connected || networkStatus == NetworkStatus.HelloApiReachable,
                danger = networkStatus == NetworkStatus.Offline || networkStatus == NetworkStatus.Error
            )
        }

        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
            Column(
                modifier = Modifier.padding(HelloSpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
            ) {
                Icon(
                    imageVector = if (networkStatus == NetworkStatus.Offline || networkStatus == NetworkStatus.Error) Icons.Default.WifiOff else Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (networkStatus == NetworkStatus.Offline || networkStatus == NetworkStatus.Error) HelloColors.DarkDanger else HelloColors.DarkAccent
                )
                Text(
                    text = "Cloud Account: $cloudAccountStatus\nCloud Chat: ${if (cloudChatOnline) "Online" else "Offline"}\nCloud Calls: ${if (cloudChatOnline) "Available" else "Offline"}\nPC Drive: ${pcDriveLabel(networkStatus, pendingUploads.size)}",
                    color = HelloColors.DarkText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Diagnostic URL:\n$checkedUrl",
                    textAlign = TextAlign.Center,
                    color = HelloColors.DarkTextMuted
                )
                Text(
                    text = diagnosticText,
                    textAlign = TextAlign.Center,
                    color = HelloColors.DarkTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(HelloSpacing.Lg))

        HelloPrimaryButton(text = "Retry", onClick = { checkNetworkStatus() })

        Spacer(modifier = Modifier.height(HelloSpacing.Sm))

        HelloSettingsCard {
            HelloSettingsRow(
                title = "Cloud Account",
                subtitle = cloudAccountStatus
            )
            HelloSettingsRow(title = "Cloud Chat", subtitle = if (cloudChatOnline) "Online" else "Offline")
            HelloSettingsRow(title = "Cloud Calls", subtitle = if (cloudChatOnline) "Available through Cloudflare" else "Offline")
            HelloSettingsRow(title = "Cloud chat health", subtitle = cloudChatDiagnostic)
            HelloSettingsRow(title = "Cloud chat fallback", subtitle = AppConfig.CHAT_CLOUD_FALLBACK_URL)
            HelloSettingsRow(title = "PC Drive", subtitle = pcDriveLabel(networkStatus, pendingUploads.size))
            HelloSettingsRow(title = "PC Drive backend", subtitle = AppConfig.DRIVE_SERVER_ORIGIN)
            HelloSettingsRow(title = "PC Drive connection", subtitle = "Cloudflare Tunnel to home.bookhelloctg.com")
            HelloSettingsRow(title = "PC Drive health", subtitle = AppConfig.DRIVE_HEALTH_URL)
        }
    }
}

private fun pcDriveLabel(status: NetworkStatus, pendingCount: Int): String {
    val base = when (status) {
        NetworkStatus.Checking -> "Checking"
        NetworkStatus.Connected, NetworkStatus.HelloApiReachable -> "Online through Cloudflare Tunnel"
        else -> "Offline / pending sync"
    }
    return if (pendingCount > 0) "$base / $pendingCount pending uploads" else base
}
