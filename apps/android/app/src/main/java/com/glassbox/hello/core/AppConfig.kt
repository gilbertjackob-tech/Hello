package com.glassbox.hello.core

import com.glassbox.hello.BuildConfig

object AppConfig {
    const val SERVER_ORIGIN = "https://desktop-8u23cj0.tail69a9e8.ts.net"
    const val HELLO_API_BASE = "$SERVER_ORIGIN/hello/api"
    const val HELLO_SOCKET_PATH = "/hello/socket.io"
    const val HELLO_UPLOADS_BASE = "$SERVER_ORIGIN/hello/uploads"
    const val HELLO_STATUS_URL = "$SERVER_ORIGIN/api/hello/status"
    const val HELLO_HEALTH_URL = "$SERVER_ORIGIN/hello/api/health"
    const val HELLO_WEB_URL = "$SERVER_ORIGIN/hello"
    val WEBRTC_FORCE_RELAY: Boolean = BuildConfig.WEBRTC_FORCE_RELAY
}
