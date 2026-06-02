package com.glassbox.hello.networkstatus

import com.glassbox.hello.core.AppConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NetworkProbeResult(
    val status: NetworkStatus,
    val checkedUrl: String,
    val detail: String
)

fun checkHelloNetwork(): NetworkProbeResult = checkPcDriveNetwork()

fun checkPcDriveNetwork(): NetworkProbeResult {
    val healthProbe = probeJsonOk(AppConfig.DRIVE_HEALTH_URL)
    if (healthProbe.status == NetworkStatus.Connected) {
        return healthProbe.copy(
            status = NetworkStatus.HelloApiReachable,
            detail = "PC Drive health succeeded: ${healthProbe.detail}"
        )
    }

    return NetworkProbeResult(
        status = NetworkStatus.Offline,
        checkedUrl = AppConfig.DRIVE_HEALTH_URL,
        detail = "PC Drive health failed: ${healthProbe.detail}"
    )
}

fun checkCloudChatNetwork(useFallback: Boolean = false): NetworkProbeResult {
    val url = if (useFallback) AppConfig.CHAT_CLOUD_FALLBACK_HEALTH_URL else AppConfig.CHAT_CLOUD_HEALTH_URL
    val probe = probeJsonOk(url)
    return probe.copy(
        detail = "${probe.detail}\n${if (useFallback) "Cloud chat fallback" else "Cloud chat production"}"
    )
}

private fun probeJsonOk(urlText: String): NetworkProbeResult {
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

        NetworkProbeResult(
            status = if (ok) NetworkStatus.Connected else NetworkStatus.Error,
            checkedUrl = urlText,
            detail = "HTTP $responseCode${if (ok) ", ok=true" else ""}"
        )
    } catch (e: Exception) {
        NetworkProbeResult(
            status = NetworkStatus.Offline,
            checkedUrl = urlText,
            detail = e.message ?: "Unknown error"
        )
    } finally {
        connection?.disconnect()
    }
}
