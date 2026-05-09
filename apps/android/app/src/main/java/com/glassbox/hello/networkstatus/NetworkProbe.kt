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

fun checkHelloNetwork(): NetworkProbeResult {
    val statusProbe = probeJsonOk(AppConfig.HELLO_STATUS_URL)
    if (statusProbe.status == NetworkStatus.Connected) {
        return statusProbe.copy(detail = "${statusProbe.detail}\n${AppConfig.HELLO_STATUS_URL}")
    }

    val healthProbe = probeJsonOk(AppConfig.HELLO_HEALTH_URL)
    if (healthProbe.status == NetworkStatus.Connected) {
        return healthProbe.copy(
            status = NetworkStatus.HelloApiReachable,
            detail = "Status failed: ${statusProbe.detail}\nHealth succeeded: ${healthProbe.detail}"
        )
    }

    return NetworkProbeResult(
        status = NetworkStatus.Offline,
        checkedUrl = "${AppConfig.HELLO_STATUS_URL}\n${AppConfig.HELLO_HEALTH_URL}",
        detail = "Status failed: ${statusProbe.detail}\nHealth failed: ${healthProbe.detail}"
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
