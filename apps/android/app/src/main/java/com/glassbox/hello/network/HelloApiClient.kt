package com.glassbox.hello.network

import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.calls.CallIceServer
import com.glassbox.hello.calls.CallRoom
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.glassbox.hello.familydrive.DriveItemsResponse
import com.glassbox.hello.familydrive.DriveUploadResponse
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

class HelloApiClient : HelloApi {
    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS)
            .build()
    }

    private val client = sharedClient
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = AppConfig.HELLO_API_BASE

    override suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User> = safeApiCall {
        val response = post("$baseUrl/register", mapOf("name" to name, "securityQuestion" to securityQuestion, "securityAnswer" to securityAnswer))
        gson.fromJson(response, User::class.java)
    }

    override suspend fun getUserQuestion(name: String): Result<String> = safeApiCall {
        val response = get("$baseUrl/user-question?name=${encodeQueryValue(name)}")
        val responseMap = gson.fromJson(response, Map::class.java)
        responseMap["securityQuestion"] as? String ?: throw Exception("Security question not found")
    }

    override suspend fun login(name: String, securityAnswer: String): Result<User> = safeApiCall {
        val response = post("$baseUrl/login", mapOf("name" to name, "securityAnswer" to securityAnswer))
        gson.fromJson(response, User::class.java)
    }

    override suspend fun fetchUser(userId: String): Result<ChatModels.User> = safeApiCall {
        gson.fromJson(get("$baseUrl/users/${encodePathValue(userId)}"), ChatModels.User::class.java)
    }

    override suspend fun updateUserPrivacy(userId: String, lastActivePrivacy: String): Result<Unit> = safeApiCall {
        post("$baseUrl/users/${encodePathValue(userId)}/privacy", mapOf("lastActivePrivacy" to lastActivePrivacy))
        Unit
    }

    override suspend fun fetchUsers(query: String?): Result<List<ChatModels.User>> = safeApiCall {
        val url = if (query.isNullOrBlank()) "$baseUrl/users" else "$baseUrl/users?q=${encodeQueryValue(query)}"
        val type = object : TypeToken<List<ChatModels.User>>() {}.type
        gson.fromJson(get(url), type)
    }

    override suspend fun createDirectChat(currentUserId: String, targetUserId: String): Result<ChatModels.Chat> = safeApiCall {
        val response = post("$baseUrl/chats/direct", mapOf("currentUserId" to currentUserId, "targetUserId" to targetUserId))
        gson.fromJson(response, ChatModels.Chat::class.java)
    }

    override suspend fun createChat(name: String, isGroup: Boolean, members: List<String>): Result<ChatModels.Chat> = safeApiCall {
        val response = post("$baseUrl/chats", mapOf("name" to name, "isGroup" to isGroup, "members" to members))
        gson.fromJson(response, ChatModels.Chat::class.java)
    }

    override suspend fun fetchChats(userId: String): Result<List<ChatModels.Chat>> = safeApiCall {
        val type = object : TypeToken<List<ChatModels.Chat>>() {}.type
        gson.fromJson(get("$baseUrl/chats?userId=${encodeQueryValue(userId)}"), type)
    }

    override suspend fun fetchMessages(chatId: String, limit: Int?, offset: Int?): Result<List<ChatModels.Message>> = safeApiCall {
        val url = buildString {
            append("$baseUrl/chats/${encodePathValue(chatId)}/messages")
            if (limit != null || offset != null) {
                append("?")
                if (limit != null) append("limit=$limit")
                if (offset != null) {
                    if (limit != null) append("&")
                    append("offset=$offset")
                }
            }
        }
        val type = object : TypeToken<List<ChatModels.Message>>() {}.type
        gson.fromJson(get(url), type)
    }

    override suspend fun fetchChatAttachments(chatId: String): Result<ChatModels.ChatAttachments> = safeApiCall {
        gson.fromJson(get("$baseUrl/chats/${encodePathValue(chatId)}/attachments"), ChatModels.ChatAttachments::class.java)
    }

    override suspend fun fetchCalls(userId: String): Result<List<ChatModels.CallHistoryItem>> = safeApiCall {
        val type = object : TypeToken<List<ChatModels.CallHistoryItem>>() {}.type
        gson.fromJson(get("$baseUrl/calls?userId=${encodeQueryValue(userId)}"), type)
    }

    override suspend fun fetchCallIceServers(): Result<List<CallIceServer>> = safeApiCall {
        val response = get("$baseUrl/calls/ice-config")
        val responseMap = gson.fromJson(response, Map::class.java)
        val rawServers = responseMap["iceServers"] ?: emptyList<Any>()
        val type = object : TypeToken<List<CallIceServer>>() {}.type
        gson.fromJson(gson.toJson(rawServers), type)
    }

    override suspend fun createCall(
        callerId: String,
        calleeId: String,
        chatId: String,
        type: String,
        status: String,
        startedAt: Long
    ): Result<String> = safeApiCall {
        val response = post(
            "$baseUrl/calls",
            mapOf(
                "callerId" to callerId,
                "calleeId" to calleeId,
                "chatId" to chatId,
                "type" to type,
                "status" to status,
                "startedAt" to startedAt,
                "mode" to "direct",
                "participantIds" to listOf(callerId, calleeId)
            )
        )
        val responseMap = gson.fromJson(response, Map::class.java)
        responseMap["id"] as? String ?: throw Exception("Call id not returned")
    }

    override suspend fun createCallRoom(
        chatId: String,
        hostId: String,
        type: String,
        participantIds: List<String>
    ): Result<CallRoom> = safeApiCall {
        val response = post(
            "$baseUrl/call-rooms",
            mapOf(
                "chatId" to chatId,
                "hostId" to hostId,
                "type" to type,
                "participantIds" to participantIds
            )
        )
        gson.fromJson(response, CallRoom::class.java)
    }

    override suspend fun fetchCallRoom(roomId: String): Result<CallRoom> = safeApiCall {
        gson.fromJson(get("$baseUrl/call-rooms/${encodePathValue(roomId)}"), CallRoom::class.java)
    }

    override suspend fun joinCallRoom(roomId: String, userId: String): Result<CallRoom> = safeApiCall {
        val response = post("$baseUrl/call-rooms/${encodePathValue(roomId)}/join", mapOf("userId" to userId))
        gson.fromJson(response, CallRoom::class.java)
    }

    override suspend fun leaveCallRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom> = safeApiCall {
        val response = post(
            "$baseUrl/call-rooms/${encodePathValue(roomId)}/leave",
            mapOf("userId" to userId, "ended" to ended)
        )
        gson.fromJson(response, CallRoom::class.java)
    }

    override suspend fun fetchStatuses(userId: String): Result<List<ChatModels.StatusItem>> = safeApiCall {
        val type = object : TypeToken<List<ChatModels.StatusItem>>() {}.type
        gson.fromJson(get("$baseUrl/statuses?userId=${encodeQueryValue(userId)}"), type)
    }

    override suspend fun createStatus(
        userId: String,
        text: String,
        attachmentUrl: String?,
        attachmentType: String?,
        backgroundColor: String,
        duration: Long
    ): Result<ChatModels.StatusItem> = safeApiCall {
        val response = post(
            "$baseUrl/statuses",
            mapOf(
                "userId" to userId,
                "text" to text,
                "attachmentUrl" to attachmentUrl,
                "attachmentType" to attachmentType,
                "backgroundColor" to backgroundColor,
                "duration" to duration
            ).filterValues { it != null }
        )
        gson.fromJson(response, ChatModels.StatusItem::class.java)
    }

    override suspend fun markStatusViewed(statusId: String, userId: String): Result<Unit> = safeApiCall {
        post("$baseUrl/statuses/${encodePathValue(statusId)}/view", mapOf("userId" to userId))
        Unit
    }

    override suspend fun sendMessage(
        chatId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        attachmentUrl: String?,
        attachmentType: String?,
        attachmentName: String?,
        attachmentSize: Long?,
        location: ChatModels.LocationData?,
        replyTo: ChatModels.ReplyTo?
    ): Result<ChatModels.Message> = safeApiCall {
        val response = post(
            "$baseUrl/chats/${encodePathValue(chatId)}/messages",
            mapOf(
                "text" to text,
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "attachmentUrl" to attachmentUrl,
                "attachmentType" to attachmentType,
                "attachmentName" to attachmentName,
                "attachmentSize" to attachmentSize,
                "location" to location,
                "replyTo" to replyTo
            ).filterValues { it != null }
        )
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    override suspend fun reactToMessage(chatId: String, messageId: String, emoji: String, userId: String): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/react", mapOf("emoji" to emoji, "userId" to userId))
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    override suspend fun starMessage(chatId: String, messageId: String, userId: String): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/star", mapOf("userId" to userId))
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    override suspend fun pinMessage(chatId: String, messageId: String, durationDays: Int): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/pin", mapOf("durationDays" to durationDays))
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    override suspend fun deleteMessage(chatId: String, messageId: String, userId: String, type: String): Result<ChatModels.Message> = safeApiCall {
        val response = delete("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}", mapOf("userId" to userId, "type" to type))
        gson.fromJson(response, ChatModels.Message::class.java)
    }

    override suspend fun clearChat(chatId: String, userId: String): Result<Unit> = safeApiCall {
        delete("$baseUrl/chats/${encodePathValue(chatId)}/clear", mapOf("userId" to userId))
        Unit
    }

    override suspend fun deleteChat(chatId: String, userId: String): Result<Unit> = safeApiCall {
        delete("$baseUrl/chats/${encodePathValue(chatId)}", mapOf("userId" to userId))
        Unit
    }

    override suspend fun uploadFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<ChatModels.UploadedFile> = safeApiCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = request(Request.Builder().url("$baseUrl/files/upload").post(body).build())
        gson.fromJson(response, ChatModels.UploadedFile::class.java)
    }

    override suspend fun fetchDriveItems(limit: Int, before: Long?): Result<DriveItemsResponse> = safeApiCall {
        val url = buildString {
            append("$baseUrl/drive/items?limit=$limit")
            if (before != null) append("&before=$before")
        }
        val response = get(url)
        gson.fromJson(response, DriveItemsResponse::class.java)
    }

    override suspend fun uploadDriveFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<DriveUploadResponse> = safeApiCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = request(Request.Builder().url("$baseUrl/drive/upload").post(body).build())
        gson.fromJson(response, DriveUploadResponse::class.java)
    }

    override suspend fun deleteDriveItem(itemId: String): Result<Unit> = safeApiCall {
        delete("$baseUrl/drive/items/${encodePathValue(itemId)}", emptyMap())
        Unit
    }

    private suspend fun get(url: String): String = request(Request.Builder().url(url).get().build())

    private suspend fun post(url: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return request(Request.Builder().url(url).post(requestBody).build())
    }

    private suspend fun delete(url: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return request(Request.Builder().url(url).delete(requestBody).build())
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception(parseErrorMessage(response.code, response.message, responseBody))
            }
            responseBody ?: throw Exception("Empty response")
        }
    }

    private fun encodeQueryValue(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun encodePathValue(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, message: String, body: String?): String {
        if (!body.isNullOrBlank()) {
            try {
                val responseMap = gson.fromJson(body, Map::class.java)
                val backendError = responseMap["error"] as? String
                if (!backendError.isNullOrBlank()) return backendError
            } catch (_: Exception) {
            }
        }
        return "HTTP $code: $message"
    }
}
