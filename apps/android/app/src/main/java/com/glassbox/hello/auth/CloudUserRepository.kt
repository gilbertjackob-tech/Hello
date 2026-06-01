package com.glassbox.hello.auth

import android.content.Context
import android.net.Uri
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CloudChatPreferences(
    val readReceiptsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true
)

class CloudUserRepository(context: Context) {
    private val sessionManager = CloudSessionManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun cachedCurrentUser(): User? = sessionManager.cachedUser()

    suspend fun currentUser(): Result<User> = safeCloudCall {
        val token = requireToken()
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/auth/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }
        val map = gson.fromJson(response, Map::class.java)
        val userJson = gson.toJson(map["user"] ?: map)
        gson.fromJson(userJson, User::class.java).copy(sessionToken = token)
    }

    suspend fun contacts(): Result<List<User>> = safeCloudCall {
        val token = requireToken()
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/contacts")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }
        gson.fromJson(response, Array<User>::class.java).toList()
    }

    suspend fun chatPreferences(): Result<CloudChatPreferences> = safeCloudCall {
        val token = requireToken()
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/preferences/chat")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }
        gson.fromJson(response, CloudChatPreferences::class.java)
    }

    suspend fun updateChatPreferences(preferences: CloudChatPreferences): Result<CloudChatPreferences> = safeCloudCall {
        val token = requireToken()
        val body = gson.toJson(preferences).toRequestBody(jsonMediaType)
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/preferences/chat")
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build()
        }
        gson.fromJson(response, CloudChatPreferences::class.java)
    }

    suspend fun uploadAvatar(context: Context, userId: String, uri: Uri): Result<User> = safeCloudCall {
        val token = requireToken()
        val resolver = context.applicationContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) throw Exception("Avatar must be an image")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("Could not read selected image")
        val fileName = "profile.${mimeType.substringAfter("/", "jpeg")}"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/users/$userId/avatar")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        }
        val user = gson.fromJson(response, User::class.java).copy(sessionToken = token)
        sessionManager.save(user)
        user
    }

    private fun requireToken(): String = sessionManager.token() ?: throw Exception("No cloud session")

    private suspend fun requestWithFallback(build: (String) -> Request): String {
        val primary = runCatching { request(build(AppConfig.CHAT_CLOUD_BASE_URL)) }
        return primary.getOrElse { request(build(AppConfig.CHAT_CLOUD_FALLBACK_URL)) }
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(body, Map::class.java)["error"] as? String
                }.getOrNull()
                throw Exception(message ?: "Cloud account HTTP ${response.code}")
            }
            body ?: throw Exception("Empty cloud account response")
        }
    }

    private suspend inline fun <T> safeCloudCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Result.failure(error)
        }
}
