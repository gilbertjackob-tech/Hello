package com.glassbox.hello.debug

import com.glassbox.hello.debug.AppLog as Log

object HelloDebugLog {
    private const val TAG = "HelloDebug"

    fun d(area: String, message: String) {
        Log.d(TAG, "[$area] $message")
    }

    fun w(area: String, message: String, error: Throwable? = null) {
        if (error == null) {
            Log.w(TAG, "[$area] $message")
        } else {
            Log.w(TAG, "[$area] $message", error)
        }
    }

    fun e(area: String, message: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(TAG, "[$area] $message")
        } else {
            Log.e(TAG, "[$area] $message", error)
        }
    }

    fun snippet(value: String?, maxLength: Int = 220): String {
        val normalized = value
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            .orEmpty()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength) + "..."
    }
}
