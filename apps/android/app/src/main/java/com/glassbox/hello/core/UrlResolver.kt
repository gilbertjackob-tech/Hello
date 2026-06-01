package com.glassbox.hello.core

object UrlResolver {
    fun resolve(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("https://") || value.startsWith("http://") || value.startsWith("data:")) return value

        val path = when {
            value.startsWith("/") -> value
            value.startsWith("hello/uploads/") -> "/$value"
            value.startsWith("uploads/") -> "/hello/$value"
            value.startsWith("api/files/") -> "/hello/$value"
            value.startsWith("api/drive/") -> "/hello/$value"
            value.startsWith("hello/api/files/") -> "/$value"
            value.startsWith("hello/api/drive/") -> "/$value"
            value.startsWith("file_") -> "/hello/uploads/$value"
            else -> "/hello/uploads/$value"
        }
        val origin = when {
            path.startsWith("/hello/api/drive/") -> AppConfig.DRIVE_SERVER_ORIGIN
            else -> AppConfig.CHAT_SERVER_ORIGIN
        }
        return origin.trimEnd('/') + path
    }
}
