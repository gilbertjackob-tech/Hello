package com.glassbox.hello.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.glassbox.hello.chat.ChatModels

data class User(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val securityQuestion: String? = null,
    val securityAnswer: String? = null,
    val sessionToken: String? = null
)

/**
 * Secure session manager - stores only non-sensitive user data.
 * Never stores security answers or passwords.
 */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hello_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
    }

    /**
     * Save authenticated user session (without security answer).
     * SECURITY: Never stores securityAnswer or any password-like data locally.
     */
    fun saveCurrentUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_AVATAR, user.avatar)
            apply()
        }
    }

    /**
     * Retrieve current user from cache.
     * Returns null security answer - it should never be cached locally.
     */
    fun getCurrentUser(): User? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val userName = prefs.getString(KEY_USER_NAME, null) ?: return null
        val avatar = prefs.getString(KEY_USER_AVATAR, null)
        
        return User(
            id = userId,
            name = userName,
            avatar = avatar,
            phone = null,
            email = null,
            securityQuestion = null,
            securityAnswer = null  // ✅ NEVER cached
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.getString(KEY_USER_ID, null) != null
}
