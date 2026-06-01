package com.glassbox.hello.auth

import android.content.Context
import com.glassbox.hello.core.User
import com.google.gson.Gson

class CloudSessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(user: User) {
        val token = user.sessionToken
        prefs.edit().apply {
            putString(KEY_USER_JSON, gson.toJson(user.copy(securityAnswer = null)))
            if (!token.isNullOrBlank()) putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun cachedUser(): User? {
        val raw = prefs.getString(KEY_USER_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, User::class.java) }.getOrNull()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "hello_cloud_session"
        private const val KEY_USER_JSON = "current_user"
        private const val KEY_TOKEN = "session_token"
    }
}
