package com.glassbox.hello.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
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
    private val firestore = FirebaseFirestore.getInstance()
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
        val map = parseMap(response)
        parseUser(map, token, "Cloud account response")
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
        gson.fromJson(response, Array<User>::class.java)?.toList().orEmpty()
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
        gson.fromJson(response, CloudChatPreferences::class.java) ?: CloudChatPreferences()
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
        gson.fromJson(response, CloudChatPreferences::class.java) ?: preferences
    }

    suspend fun uploadAvatar(context: Context, userId: String, uri: Uri, zoom: Float = 1f): Result<User> = safeCloudCall {
        uploadAvatar(context, userId, AvatarCropRequest(uri = uri, scale = zoom.coerceIn(1f, 3f))).getOrThrow()
    }

    suspend fun uploadAvatar(context: Context, userId: String, crop: AvatarCropRequest): Result<User> = safeCloudCall {
        val token = requireToken()
        val resolver = context.applicationContext.contentResolver
        val mimeType = resolver.getType(crop.uri) ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) throw Exception("Avatar must be an image")
        val source = resolver.openInputStream(crop.uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw Exception("Could not read selected image")
        val bytes = source.toSquareAvatarBytes(crop)
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
        val user = parseUser(parseMap(response), token, "Avatar response")
        syncProfileDocument(user)
        sessionManager.save(user)
        user
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String? = null,
        about: String? = null
    ): Result<User> = safeCloudCall {
        val token = requireToken()
        val payload = linkedMapOf<String, Any>()
        displayName?.let { payload["displayName"] = it.trim() }
        about?.let { payload["about"] = it.trim().replace(Regex("\\s+"), " ") }
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/users/$userId/profile")
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build()
        }
        val user = parseUser(parseMap(response), token, "Profile response")
        syncProfileDocument(user)
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
                val message = parseMap(body)["error"] as? String
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

    private fun parseMap(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { gson.fromJson(raw, Map::class.java) as? Map<*, *> }
            .getOrNull()
            ?.entries
            ?.associate { it.key.toString() to it.value }
            ?: emptyMap()
    }

    private fun parseUser(map: Map<String, Any?>, token: String, label: String): User {
        val nested = map["user"]
        val userMap = when (nested) {
            is Map<*, *> -> nested.entries.associate { it.key.toString() to it.value }
            else -> map
        }
        val id = (userMap["id"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw Exception("$label did not include a user id")
        val name = ((userMap["name"] as? String) ?: (userMap["displayName"] as? String))
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("$label did not include a user name")
        val avatar = (userMap["avatar"] as? String) ?: (userMap["avatarUrl"] as? String)
        val about = userMap["about"] as? String
        val phone = userMap["phone"] as? String
        val email = userMap["email"] as? String
        return User(
            id = id,
            name = name,
            avatar = avatar,
            about = about,
            phone = phone,
            email = email,
            sessionToken = token
        )
    }

    private suspend fun syncProfileDocument(user: User) {
        val values = mutableMapOf<String, Any>(
            "displayName" to user.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        user.about?.let { values["about"] = it }
        user.avatar?.let { values["avatarUrl"] = it; values["photoUrl"] = it }
        user.phone?.let { values["phone"] = it }
        user.email?.let { values["email"] = it }
        firestore.collection("users")
            .document(user.id)
            .set(values, SetOptions.merge())
            .await()
    }

    private fun Bitmap.toSquareAvatarBytes(crop: AvatarCropRequest): ByteArray {
        val outputSize = 512
        val viewport = crop.viewportPx.coerceAtLeast(1f)
        val baseScale = maxOf(viewport / width.toFloat(), viewport / height.toFloat())
        val renderScale = baseScale * crop.scale.coerceIn(1f, 4f)
        val cropSide = (viewport / renderScale).coerceAtLeast(1f)
        val rawLeft = width / 2f - cropSide / 2f - (crop.offsetX / renderScale)
        val rawTop = height / 2f - cropSide / 2f - (crop.offsetY / renderScale)
        val maxLeft = (width - cropSide).coerceAtLeast(0f)
        val maxTop = (height - cropSide).coerceAtLeast(0f)
        val left = rawLeft.coerceIn(0f, maxLeft).toInt()
        val top = rawTop.coerceIn(0f, maxTop).toInt()
        val side = cropSide.coerceAtMost(minOf(width, height).toFloat()).toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(this, left, top, side.coerceAtMost(width - left), side.coerceAtMost(height - top))
        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            cropped,
            null,
            Rect(0, 0, outputSize, outputSize),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        val stream = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        if (cropped != this) cropped.recycle()
        if (output != this) output.recycle()
        return stream.toByteArray()
    }
}

data class AvatarCropRequest(
    val uri: Uri,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val viewportPx: Float = 1f
)
