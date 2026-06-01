package com.glassbox.hello.core

import com.glassbox.hello.BuildConfig

object AppConfig {
    const val SERVER_ORIGIN = "https://desktop-8u23cj0.tail69a9e8.ts.net"
    const val CHAT_CLOUD_BASE_URL = "https://chat.bookhelloctg.com"
    const val CHAT_CLOUD_FALLBACK_URL = "https://hello-chat-worker.gilbert-jackob3.workers.dev"
    const val CHAT_SERVER_ORIGIN = SERVER_ORIGIN
    const val CALL_SERVER_ORIGIN = SERVER_ORIGIN
    const val DRIVE_SERVER_ORIGIN = SERVER_ORIGIN
    const val HELLO_API_BASE = "$SERVER_ORIGIN/hello/api"
    const val CHAT_CLOUD_HEALTH_URL = "$CHAT_CLOUD_BASE_URL/health"
    const val CHAT_CLOUD_FALLBACK_HEALTH_URL = "$CHAT_CLOUD_FALLBACK_URL/health"
    const val CHAT_API_BASE = "$CHAT_SERVER_ORIGIN/hello/api"
    const val CALL_API_BASE = "$CALL_SERVER_ORIGIN/hello/api"
    const val DRIVE_API_BASE = "$DRIVE_SERVER_ORIGIN/hello/api"
    const val CHAT_SOCKET_ORIGIN = CHAT_SERVER_ORIGIN
    const val CALL_SOCKET_ORIGIN = CALL_SERVER_ORIGIN
    const val HELLO_SOCKET_PATH = "/hello/socket.io"
    const val CHAT_SOCKET_PATH = HELLO_SOCKET_PATH
    const val CALL_SOCKET_PATH = HELLO_SOCKET_PATH
    const val HELLO_UPLOADS_BASE = "$SERVER_ORIGIN/hello/uploads"
    const val HELLO_STATUS_URL = "$SERVER_ORIGIN/api/hello/status"
    const val HELLO_HEALTH_URL = "$SERVER_ORIGIN/hello/api/health"
    const val HELLO_WEB_URL = "$SERVER_ORIGIN/hello"
    const val ENABLE_PC_CALL_SIGNALING = false
    val WEBRTC_FORCE_RELAY: Boolean = BuildConfig.WEBRTC_FORCE_RELAY
}
