package com.glassbox.hello.core

import com.glassbox.hello.BuildConfig

object AppConfig {
    const val PC_DRIVE_ORIGIN = "https://home.bookhelloctg.com"
    const val CHAT_CLOUD_BASE_URL = "https://chat.bookhelloctg.com"
    const val CHAT_CLOUD_FALLBACK_URL = "https://hello-chat-worker.gilbert-jackob3.workers.dev"
    const val CHAT_SERVER_ORIGIN = CHAT_CLOUD_BASE_URL
    const val CALL_SERVER_ORIGIN = CHAT_CLOUD_BASE_URL
    val DRIVE_PC_BASE_URL: String = BuildConfig.DRIVE_PC_BASE_URL.trimEnd('/')
    val DRIVE_SERVER_ORIGIN: String = DRIVE_PC_BASE_URL.removeSuffix("/hello/api")
    val SERVER_ORIGIN: String = DRIVE_SERVER_ORIGIN
    val HELLO_API_BASE: String = DRIVE_PC_BASE_URL
    const val CHAT_CLOUD_HEALTH_URL = "$CHAT_CLOUD_BASE_URL/health"
    const val CHAT_CLOUD_FALLBACK_HEALTH_URL = "$CHAT_CLOUD_FALLBACK_URL/health"
    const val CHAT_API_BASE = "$CHAT_CLOUD_BASE_URL/api"
    const val CALL_API_BASE = "$CHAT_CLOUD_BASE_URL/api"
    val DRIVE_API_BASE: String = DRIVE_PC_BASE_URL
    val DRIVE_HEALTH_URL: String = "$DRIVE_API_BASE/drive/health"
    const val CHAT_SOCKET_ORIGIN = CHAT_SERVER_ORIGIN
    const val CALL_SOCKET_ORIGIN = CALL_SERVER_ORIGIN
    const val HELLO_SOCKET_PATH = "/hello/socket.io"
    const val CHAT_SOCKET_PATH = HELLO_SOCKET_PATH
    const val CALL_SOCKET_PATH = HELLO_SOCKET_PATH
    val HELLO_UPLOADS_BASE: String = "$SERVER_ORIGIN/hello/uploads"
    val HELLO_STATUS_URL: String = "$SERVER_ORIGIN/api/hello/status"
    val HELLO_HEALTH_URL: String = "$SERVER_ORIGIN/hello/api/health"
    val HELLO_WEB_URL: String = "$SERVER_ORIGIN/hello"
    const val ENABLE_PC_CALL_SIGNALING = false
    val WEBRTC_FORCE_RELAY: Boolean = BuildConfig.WEBRTC_FORCE_RELAY
}
