package com.glassbox.hello.utils

import com.glassbox.hello.core.AppConfig

/**
 * Shared constants for browser networking, storage, sync, and UI workflows.
 */
object Constants {
    const val APP_SCHEME: String = "glassbox"
    const val OAUTH_HOST: String = "oauth"

    const val PROVIDER_GMAIL: String = "gmail"
    const val PROVIDER_OUTLOOK: String = "outlook"
    const val PROVIDER_ICLOUD: String = "icloud"

    const val GMAIL_CLIENT_ID: String = ""
    const val OUTLOOK_CLIENT_ID: String = ""
    const val ICLOUD_CLIENT_ID: String = ""

    const val GMAIL_REDIRECT_URI: String = "$APP_SCHEME://$OAUTH_HOST/gmail/callback"
    const val OUTLOOK_REDIRECT_URI: String = "$APP_SCHEME://$OAUTH_HOST/outlook/callback"
    const val ICLOUD_REDIRECT_URI: String = "$APP_SCHEME://$OAUTH_HOST/icloud/callback"

    val API_BASE_URL: String = AppConfig.HELLO_API_BASE
    val SYNC_WEB_SOCKET_URL: String = "$API_BASE_URL/sync/ws"
    const val REQUEST_TIMEOUT_MILLIS: Long = 30_000L
    const val CALL_TIMEOUT_MILLIS: Long = 60_000L
    const val TOKEN_EXPIRY_BUFFER_MILLIS: Long = 5 * 60 * 1000L

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID: String = "glassbox_downloads"
    const val SYNC_NOTIFICATION_CHANNEL_ID: String = "glassbox_sync"
    const val DOWNLOAD_DIRECTORY_NAME: String = "GlassBox"
    const val MAX_CACHE_BYTES: Long = 128L * 1024L * 1024L
    const val DEFAULT_PAGE_SIZE: Int = 100

    const val DEFAULT_HOME_URL: String = "https://www.google.com"
    const val DEFAULT_SEARCH_URL: String = "https://www.google.com/search?q="
}
