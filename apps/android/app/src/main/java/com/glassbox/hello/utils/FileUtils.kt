package com.glassbox.hello.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

/**
 * File helpers for app-scoped downloads and shared content.
 */
object FileUtils {
    /**
     * Returns the app-scoped download directory, creating it when needed.
     */
    fun getDownloadDirectory(context: Context): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(base, Constants.DOWNLOAD_DIRECTORY_NAME).also { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }
    }

    /**
     * Returns a safe destination file for a download.
     */
    fun createDownloadFile(context: Context, fileName: String): File {
        val sanitized = sanitizeFileName(fileName).ifBlank { "download" }
        return uniqueFile(getDownloadDirectory(context), sanitized)
    }

    /**
     * Returns a MIME type from a file name, URL, or URI.
     */
    fun getMimeType(context: Context, value: String): String {
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val resolverType = uri?.takeIf { parsed -> parsed.scheme == "content" }
            ?.let { parsed -> context.contentResolver.getType(parsed) }
        if (!resolverType.isNullOrBlank()) return resolverType

        val extension = MimeTypeMap.getFileExtensionFromUrl(value)
            .ifBlank { value.substringAfterLast('.', missingDelimiterValue = "") }
            .lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    /**
     * Returns the display name for a content URI when available.
     */
    fun getDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    /**
     * Returns a file size in bytes, or -1 when unknown.
     */
    fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else -1L
    }

    /**
     * Formats bytes for compact display.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 0L) return "Unknown size"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }

    /**
     * Removes path separators and unsupported file-name characters.
     */
    fun sanitizeFileName(fileName: String): String {
        return fileName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .take(180)
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            candidate = File(directory, nextName)
            index += 1
        }
        return candidate
    }
}
