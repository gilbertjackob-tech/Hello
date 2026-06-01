package com.glassbox.hello.chat

import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudChatApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun upsertUser(userId: String, name: String, avatar: String?): Result<ChatModels.User> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/users/upsert",
            mapOf("id" to userId, "displayName" to name, "avatarUrl" to avatar).filterValues { it != null }
        )
        gson.fromJson(response, ChatModels.User::class.java)
    }

    suspend fun ensureConversation(
        conversationId: String,
        title: String,
        isGroup: Boolean,
        createdBy: String,
        createdByName: String,
        memberIds: List<String>
    ): Result<ChatModels.Chat> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/conversations",
            mapOf(
                "id" to conversationId,
                "type" to if (isGroup) "group" else "direct",
                "title" to title,
                "createdBy" to createdBy,
                "createdByName" to createdByName,
                "memberIds" to memberIds
            )
        )
        gson.fromJson(response, ChatModels.Chat::class.java)
    }

    suspend fun fetchConversations(userId: String): Result<List<ChatModels.Chat>> = safeCloudCall {
        val response = getWithFallback("/api/chat/conversations?userId=${encode(userId)}")
        val type = object : TypeToken<List<ChatModels.Chat>>() {}.type
        gson.fromJson(response, type)
    }

    suspend fun fetchMessages(conversationId: String, limit: Int? = null, offset: Int? = null): Result<List<ChatModels.Message>> = safeCloudCall {
        val query = buildString {
            if (limit != null || offset != null) {
                append("?")
                if (limit != null) append("limit=$limit")
                if (offset != null) {
                    if (limit != null) append("&")
                    append("offset=$offset")
                }
            }
        }
        val response = getWithFallback("/api/chat/conversations/${encode(conversationId)}/messages$query")
        val type = object : TypeToken<List<ChatModels.Message>>() {}.type
        gson.fromJson(response, type)
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?
    ): Result<ChatModels.Message> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/conversations/${encode(conversationId)}/messages",
            mapOf(
                "text" to text,
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar
            ).filterValues { it != null }
        )
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    suspend fun markRead(messageId: String, userId: String): Result<Unit> = safeCloudCall {
        postWithFallback("/api/chat/messages/${encode(messageId)}/read", mapOf("userId" to userId))
        Unit
    }

    suspend fun uploadAttachment(fileName: String, mimeType: String, bytes: ByteArray): Result<ChatModels.UploadedFile> = safeCloudCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/chat/attachments/upload")
                .post(body)
                .build()
        }
        gson.fromJson(response, ChatModels.UploadedFile::class.java)
    }

    private suspend fun getWithFallback(pathAndQuery: String): String =
        requestWithFallback { base -> Request.Builder().url("$base$pathAndQuery").get().build() }

    private suspend fun postWithFallback(path: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return requestWithFallback { base -> Request.Builder().url("$base$path").post(requestBody).build() }
    }

    private suspend fun requestWithFallback(build: (String) -> Request): String {
        val primary = runCatching { request(build(AppConfig.CHAT_CLOUD_BASE_URL)) }
        return primary.getOrElse {
            request(build(AppConfig.CHAT_CLOUD_FALLBACK_URL))
        }
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("Cloud chat HTTP ${response.code}: ${responseBody ?: response.message}")
            }
            responseBody ?: throw Exception("Empty cloud chat response")
        }
    }

    private suspend inline fun <T> safeCloudCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
