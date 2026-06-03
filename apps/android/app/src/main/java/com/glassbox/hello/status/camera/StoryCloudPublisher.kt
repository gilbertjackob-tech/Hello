package com.glassbox.hello.status.camera

import android.content.Context
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.network.HelloApiClient
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class StoryCloudPublisher(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val legacyApi = HelloApiClient()

    suspend fun publishPhoto(userId: String, bytes: ByteArray): Result<ChatModels.StatusItem> {
        val token = CloudSessionManager(context.applicationContext).token()
        if (!token.isNullOrBlank()) {
            val storyResult = runCatching {
                val upload = uploadStoryMedia(bytes, token)
                createStory(upload.id, token)
                val mediaUrl = "${AppConfig.CHAT_API_BASE}/stories/media/${encodePath(upload.key)}"
                legacyApi.createStatus(
                    userId = userId,
                    text = "",
                    attachmentUrl = mediaUrl,
                    attachmentType = "image/jpeg",
                    backgroundColor = "",
                    duration = 5000
                ).getOrThrow()
            }
            if (storyResult.isSuccess) return Result.success(storyResult.getOrThrow())
        }
        val uploaded = legacyApi.uploadFile(
            fileName = "story-${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            bytes = bytes,
            uploaderId = userId
        ).getOrElse { return Result.failure(it) }
        return legacyApi.createStatus(
            userId = userId,
            text = "",
            attachmentUrl = uploaded.url,
            attachmentType = uploaded.mimeType,
            backgroundColor = "",
            duration = 5000
        )
    }

    private fun uploadStoryMedia(bytes: ByteArray, token: String): StoryUpload {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "story-${System.currentTimeMillis()}.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val response = request(
            Request.Builder()
                .url("${AppConfig.CHAT_API_BASE}/stories/upload")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        )
        val map = gson.fromJson(response, Map::class.java)
        val id = map["id"] as? String ?: throw IllegalStateException("Story upload id missing")
        val key = map["key"] as? String ?: throw IllegalStateException("Story upload key missing")
        return StoryUpload(id, key)
    }

    private fun createStory(mediaId: String, token: String) {
        val responseBody = gson.toJson(mapOf("type" to "photo", "mediaIds" to listOf(mediaId)))
            .toRequestBody(jsonMediaType)
        request(
            Request.Builder()
                .url("${AppConfig.CHAT_API_BASE}/stories")
                .header("Authorization", "Bearer $token")
                .post(responseBody)
                .build()
        )
    }

    private fun request(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(body.ifBlank { "HTTP ${response.code}: ${response.message}" })
            }
            return body
        }
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

private data class StoryUpload(val id: String, val key: String)
