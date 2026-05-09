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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloListItem
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class ProbeResult(
    val status: NetworkStatus,
    val checkedUrl: String,
    val detail: String
)

@Composable
fun NetworkStatusScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var networkStatus by remember { mutableStateOf<NetworkStatus>(NetworkStatus.Checking) }
    var checkedUrl by remember { mutableStateOf(AppConfig.HELLO_STATUS_URL) }
    var diagnosticText by remember { mutableStateOf("Not checked yet") }

    // Function to check network status
    val checkNetworkStatus = {
        coroutineScope.launch {
            networkStatus = NetworkStatus.Checking
            checkedUrl = AppConfig.HELLO_STATUS_URL
            diagnosticText = "Checking..."
            try {
                val result = withContext(Dispatchers.IO) {
                    checkHelloStatus()
                }
                checkedUrl = result.checkedUrl
                diagnosticText = result.detail
                networkStatus = result.status
            } catch (e: Exception) {
                checkedUrl = AppConfig.HELLO_STATUS_URL
                diagnosticText = e.message ?: "Unknown error"
                networkStatus = NetworkStatus.Offline
            }
        }
    }

    // Auto-check on first load
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
            eyebrow = "FAMILY NETWORK",
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
                    text = when (networkStatus) {
                        NetworkStatus.Checking -> "Checking..."
                        NetworkStatus.Connected -> "Family Network Connected"
                        NetworkStatus.HelloApiReachable -> "Hello API Reachable"
                        NetworkStatus.Offline -> "Family Network Off / Server Unreachable"
                        NetworkStatus.Error -> "Server Unreachable"
                    },
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
                title = "Open Tailscale",
                subtitle = "Connect the private Family Network, then retry.",
                onClick = { TailscaleHelper.openTailscale(context) },
                leading = {
                    HelloIconButton(onClick = { TailscaleHelper.openTailscale(context) }, active = true) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = HelloColors.DarkAccent)
                    }
                }
            )
            HelloSettingsRow(title = "Server origin", subtitle = AppConfig.SERVER_ORIGIN)
            HelloSettingsRow(title = "Status endpoint", subtitle = AppConfig.HELLO_STATUS_URL)
            HelloSettingsRow(title = "Health endpoint", subtitle = AppConfig.HELLO_HEALTH_URL)
        }
    }
}

private fun checkHelloStatus(): ProbeResult {
    val statusProbe = probeJsonOk(AppConfig.HELLO_STATUS_URL)
    if (statusProbe.status == NetworkStatus.Connected) {
        return statusProbe.copy(
            detail = "${statusProbe.detail}\n${AppConfig.HELLO_STATUS_URL}"
        )
    }

    val healthProbe = probeJsonOk(AppConfig.HELLO_HEALTH_URL)
    if (healthProbe.status == NetworkStatus.Connected) {
        return healthProbe.copy(
            status = NetworkStatus.HelloApiReachable,
            detail = "Status failed: ${statusProbe.detail}\nHealth succeeded: ${healthProbe.detail}"
        )
    }

    return ProbeResult(
        status = NetworkStatus.Offline,
        checkedUrl = "${AppConfig.HELLO_STATUS_URL}\n${AppConfig.HELLO_HEALTH_URL}",
        detail = "Status failed: ${statusProbe.detail}\nHealth failed: ${healthProbe.detail}"
    )
}

private fun probeJsonOk(urlText: String): ProbeResult {
    var connection: HttpURLConnection? = null
    return try {
        val url = URL(urlText)
        connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        val ok = responseCode == 200 && JSONObject(body).optBoolean("ok", false)

        ProbeResult(
            status = if (ok) NetworkStatus.Connected else NetworkStatus.Error,
            checkedUrl = urlText,
            detail = "HTTP $responseCode${if (ok) ", ok=true" else ""}"
        )
    } catch (e: Exception) {
        ProbeResult(
            status = NetworkStatus.Offline,
            checkedUrl = urlText,
            detail = e.message ?: "Unknown error"
        )
    } finally {
        connection?.disconnect()
    }
}
