package com.glassbox.hello.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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
import java.io.ByteArrayOutputStream
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

    suspend fun uploadAvatar(context: Context, userId: String, uri: Uri, zoom: Float = 1f): Result<User> = safeCloudCall {
        val token = requireToken()
        val resolver = context.applicationContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) throw Exception("Avatar must be an image")
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw Exception("Could not read selected image")
        val bytes = source.toSquareAvatarBytes(zoom)
        val fileName = "profile.jpg"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("image/jpeg".toMediaType()))
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

    private fun Bitmap.toSquareAvatarBytes(zoom: Float): ByteArray {
        val outputSize = 512
        val safeZoom = zoom.coerceIn(1f, 3f)
        val sourceSide = (minOf(width, height) / safeZoom).toInt().coerceAtLeast(1)
        val left = ((width - sourceSide) / 2).coerceAtLeast(0)
        val top = ((height - sourceSide) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(this, left, top, sourceSide.coerceAtMost(width), sourceSide.coerceAtMost(height))
        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            cropped,
            null,
            android.graphics.Rect(0, 0, outputSize, outputSize),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        val stream = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        if (cropped != this) cropped.recycle()
        if (output != this) output.recycle()
        return stream.toByteArray()
    }
}
