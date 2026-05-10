package com.glassbox.hello.chat.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

fun downloadAttachment(context: Context, url: String, fileName: String? = null) {
    runCatching {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle(fileName ?: "Hello attachment")
            setDescription("Downloading attachment")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName ?: Uri.parse(url).lastPathSegment ?: "hello-attachment")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        downloadManager.enqueue(request)
    }
}
