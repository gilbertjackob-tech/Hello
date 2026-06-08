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
import org.json.JSONObject
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
    private val baseUrl = AppConfig.CHAT_API_BASE
    private val callBaseUrl = AppConfig.CALL_API_BASE
    private val driveBaseUrl = AppConfig.DRIVE_API_BASE

    override suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User> = safeApiCall {
        val response = post("$baseUrl/register", mapOf("name" to name, "securityQuestion" to securityQuestion, "securityAnswer" to securityAnswer))
        parseRequired(response, "Register", User::class.java)
    }

    override suspend fun getUserQuestion(name: String): Result<String> = safeApiCall {
        val response = get("$baseUrl/user-question?name=${encodeQueryValue(name)}")
        val responseMap = parseMap(response)
        responseMap["securityQuestion"] as? String ?: throw Exception("Security question not found")
    }

    override suspend fun login(name: String, securityAnswer: String): Result<User> = safeApiCall {
        val response = post("$baseUrl/login", mapOf("name" to name, "securityAnswer" to securityAnswer))
        parseRequired(response, "Login", User::class.java)
    }

    override suspend fun fetchUser(userId: String): Result<ChatModels.User> = safeApiCall {
        sanitizeUser(parseRequired(get("$baseUrl/users/${encodePathValue(userId)}"), "Fetch user", ChatModels.User::class.java))
            ?: throw Exception("Fetch user response was invalid")
    }

    override suspend fun updateUserPrivacy(userId: String, lastActivePrivacy: String): Result<Unit> = safeApiCall {
        post("$baseUrl/users/${encodePathValue(userId)}/privacy", mapOf("lastActivePrivacy" to lastActivePrivacy))
        Unit
    }

    override suspend fun fetchUsers(query: String?): Result<List<ChatModels.User>> = safeApiCall {
        val url = if (query.isNullOrBlank()) "$baseUrl/users" else "$baseUrl/users?q=${encodeQueryValue(query)}"
        val type = object : TypeToken<List<ChatModels.User>>() {}.type
        gson.fromJson<List<ChatModels.User>>(get(url), type).orEmpty().mapNotNull(::sanitizeUser)
    }

    override suspend fun createDirectChat(currentUserId: String, targetUserId: String): Result<ChatModels.Chat> = safeApiCall {
        val response = post("$baseUrl/chats/direct", mapOf("currentUserId" to currentUserId, "targetUserId" to targetUserId))
        sanitizeChat(parseRequired(response, "Create direct chat", ChatModels.Chat::class.java))
            ?: throw Exception("Create direct chat response was invalid")
    }

    override suspend fun createChat(name: String, isGroup: Boolean, members: List<String>): Result<ChatModels.Chat> = safeApiCall {
        val response = post("$baseUrl/chats", mapOf("name" to name, "isGroup" to isGroup, "members" to members))
        sanitizeChat(parseRequired(response, "Create chat", ChatModels.Chat::class.java))
            ?: throw Exception("Create chat response was invalid")
    }

    override suspend fun fetchChats(userId: String): Result<List<ChatModels.Chat>> = safeApiCall {
        val type = object : TypeToken<List<ChatModels.Chat>>() {}.type
        gson.fromJson<List<ChatModels.Chat>>(get("$baseUrl/chats?userId=${encodeQueryValue(userId)}"), type).orEmpty().mapNotNull(::sanitizeChat)
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
        gson.fromJson<List<ChatModels.Message>>(get(url), type).orEmpty().mapNotNull(::sanitizeMessage)
    }

    override suspend fun fetchChatAttachments(chatId: String): Result<ChatModels.ChatAttachments> = safeApiCall {
        sanitizeChatAttachments(
            parseRequired(
                get("$baseUrl/chats/${encodePathValue(chatId)}/attachments"),
                "Fetch chat attachments",
                ChatModels.ChatAttachments::class.java
            )
        )
    }

    override suspend fun fetchCalls(userId: String): Result<List<ChatModels.CallHistoryItem>> = safeApiCall {
        val type = object : TypeToken<List<ChatModels.CallHistoryItem>>() {}.type
        gson.fromJson<List<ChatModels.CallHistoryItem>>(get("$callBaseUrl/calls?userId=${encodeQueryValue(userId)}"), type)
            .orEmpty()
            .mapNotNull(::sanitizeCallHistoryItem)
    }

    override suspend fun fetchCallIceServers(): Result<List<CallIceServer>> = safeApiCall {
        val response = get("$callBaseUrl/calls/ice-config")
        val responseMap = parseMap(response)
        val rawServers = responseMap["iceServers"] ?: emptyList<Any>()
        val type = object : TypeToken<List<CallIceServer>>() {}.type
        gson.fromJson<List<CallIceServer>>(gson.toJson(rawServers), type) ?: emptyList()
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
            "$callBaseUrl/calls",
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
        val responseMap = parseMap(response)
        responseMap["id"] as? String ?: throw Exception("Call id not returned")
    }

    override suspend fun createCallRoom(
        chatId: String,
        hostId: String,
        type: String,
        participantIds: List<String>
    ): Result<CallRoom> = safeApiCall {
        val response = post(
            "$callBaseUrl/call-rooms",
            mapOf(
                "chatId" to chatId,
                "hostId" to hostId,
                "type" to type,
                "participantIds" to participantIds
            )
        )
        parseRequired(response, "Create call room", CallRoom::class.java)
    }

    override suspend fun fetchCallRoom(roomId: String): Result<CallRoom> = safeApiCall {
        parseRequired(get("$callBaseUrl/call-rooms/${encodePathValue(roomId)}"), "Fetch call room", CallRoom::class.java)
    }

    override suspend fun joinCallRoom(roomId: String, userId: String): Result<CallRoom> = safeApiCall {
        val response = post("$callBaseUrl/call-rooms/${encodePathValue(roomId)}/join", mapOf("userId" to userId))
        parseRequired(response, "Join call room", CallRoom::class.java)
    }

    override suspend fun leaveCallRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom> = safeApiCall {
        val response = post(
            "$callBaseUrl/call-rooms/${encodePathValue(roomId)}/leave",
            mapOf("userId" to userId, "ended" to ended)
        )
        parseRequired(response, "Leave call room", CallRoom::class.java)
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
        sanitizeMessage(parseRequired(response, "Send message", ChatModels.Message::class.java))
            ?: throw Exception("Send message response was invalid")
    }

    override suspend fun reactToMessage(chatId: String, messageId: String, emoji: String, userId: String): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/react", mapOf("emoji" to emoji, "userId" to userId))
        sanitizeMessage(parseRequired(response, "React message", ChatModels.Message::class.java))
            ?: throw Exception("React message response was invalid")
    }

    override suspend fun starMessage(chatId: String, messageId: String, userId: String): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/star", mapOf("userId" to userId))
        sanitizeMessage(parseRequired(response, "Star message", ChatModels.Message::class.java))
            ?: throw Exception("Star message response was invalid")
    }

    override suspend fun pinMessage(chatId: String, messageId: String, durationDays: Int): Result<ChatModels.Message> = safeApiCall {
        val response = post("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}/pin", mapOf("durationDays" to durationDays))
        sanitizeMessage(parseRequired(response, "Pin message", ChatModels.Message::class.java))
            ?: throw Exception("Pin message response was invalid")
    }

    override suspend fun deleteMessage(chatId: String, messageId: String, userId: String, type: String): Result<ChatModels.Message> = safeApiCall {
        val response = delete("$baseUrl/chats/${encodePathValue(chatId)}/messages/${encodePathValue(messageId)}", mapOf("userId" to userId, "type" to type))
        sanitizeMessage(parseRequired(response, "Delete message", ChatModels.Message::class.java))
            ?: throw Exception("Delete message response was invalid")
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
        sanitizeUploadedFile(parseRequired(response, "Upload file", ChatModels.UploadedFile::class.java))
            ?: throw Exception("Upload file response was invalid")
    }

    override suspend fun fetchDriveItems(limit: Int, before: Long?): Result<DriveItemsResponse> = safeApiCall {
        val url = buildString {
            append("$driveBaseUrl/drive/items?limit=$limit")
            if (before != null) append("&before=$before")
        }
        val response = get(url)
        gson.fromJson(response, DriveItemsResponse::class.java) ?: DriveItemsResponse()
    }

    override suspend fun uploadDriveFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<DriveUploadResponse> = safeApiCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("userId", uploaderId)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = request(Request.Builder().url("$driveBaseUrl/drive/upload").post(body).build())
        gson.fromJson(response, DriveUploadResponse::class.java) ?: DriveUploadResponse()
    }

    override suspend fun deleteDriveItem(itemId: String): Result<Unit> = safeApiCall {
        delete("$driveBaseUrl/drive/items/${encodePathValue(itemId)}", emptyMap())
        Unit
    }

    override suspend fun checkCloudChatHealth(useFallback: Boolean): Result<Boolean> = safeApiCall {
        val url = if (useFallback) AppConfig.CHAT_CLOUD_FALLBACK_HEALTH_URL else AppConfig.CHAT_CLOUD_HEALTH_URL
        JSONObject(get(url)).optBoolean("ok", false)
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
                val responseMap = parseMap(body)
                val backendError = responseMap["error"] as? String
                if (!backendError.isNullOrBlank()) return backendError
            } catch (_: Exception) {
            }
        }
        return "HTTP $code: $message"
    }

    private fun parseMap(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { gson.fromJson(raw, Map::class.java) as? Map<*, *> }
            .getOrNull()
            ?.entries
            ?.associate { it.key.toString() to it.value }
            ?: emptyMap()
    }

    private fun <T> parseRequired(raw: String, label: String, type: Class<T>): T =
        gson.fromJson(raw, type) ?: throw Exception("$label response was empty")

    private fun sanitizeChat(chat: ChatModels.Chat?): ChatModels.Chat? {
        if (chat == null) return null
        val id = rawString(chat.id)
        if (id.isBlank()) return null
        val participants = chat.participants.orEmpty().mapNotNull(::sanitizeUser)
        val members = chat.members.orEmpty().mapNotNull { value ->
            rawString(value).takeIf { it.isNotBlank() }
        }
        val name = rawString(chat.name)
            .ifBlank { participants.firstOrNull()?.name.orEmpty() }
            .ifBlank { members.firstOrNull().orEmpty() }
            .ifBlank { id }
        val type = rawString(chat.type).ifBlank { null }
        val directKey = rawString(chat.directKey).ifBlank { null }
        return chat.copy(
            id = id,
            type = type,
            directKey = directKey,
            name = name,
            avatar = rawString(chat.avatar).ifBlank { null },
            lastMessage = rawString(chat.lastMessage).ifBlank { null },
            members = members.ifEmpty { null },
            participants = participants.ifEmpty { null }
        )
    }

    private fun sanitizeUser(user: ChatModels.User?): ChatModels.User? {
        if (user == null) return null
        val id = rawString(user.id)
        if (id.isBlank()) return null
        val name = rawString(user.name).ifBlank { id }
        return user.copy(
            id = id,
            name = name,
            avatar = rawString(user.avatar).ifBlank { null },
            username = rawString(user.username).ifBlank { null },
            phone = rawString(user.phone).ifBlank { null },
            email = rawString(user.email).ifBlank { null },
            privacy = rawString(user.privacy).ifBlank { null },
            lastActivePrivacy = rawString(user.lastActivePrivacy).ifBlank { null }
        )
    }

    private fun sanitizeMessage(message: ChatModels.Message?): ChatModels.Message? {
        if (message == null) return null
        val id = rawString(message.id)
        val chatId = rawString(message.chatId)
        val senderId = rawString(message.senderId)
        if (id.isBlank() || chatId.isBlank() || senderId.isBlank()) return null
        return message.copy(
            id = id,
            chatId = chatId,
            senderId = senderId,
            senderName = rawString(message.senderName).ifBlank { senderId },
            senderAvatar = rawString(message.senderAvatar).ifBlank { null },
            text = rawString(message.text),
            messageType = rawString(message.messageType).ifBlank { null },
            attachmentUrl = rawString(message.attachmentUrl).ifBlank { null },
            attachmentType = rawString(message.attachmentType).ifBlank { null },
            attachmentName = rawString(message.attachmentName).ifBlank { null },
            status = rawString(message.status).ifBlank { null },
            deletedFor = message.deletedFor.orEmpty().mapNotNull { value ->
                rawString(value).takeIf { it.isNotBlank() }
            }.ifEmpty { null },
            reactions = message.reactions.orEmpty().mapNotNull { reaction ->
                val emoji = rawString(reaction.emoji)
                val reactionUserId = rawString(reaction.userId)
                if (emoji.isBlank() || reactionUserId.isBlank()) null else ChatModels.Reaction(emoji = emoji, userId = reactionUserId)
            }.ifEmpty { null },
            starredBy = message.starredBy.orEmpty().mapNotNull { value ->
                rawString(value).takeIf { it.isNotBlank() }
            }.ifEmpty { null }
        )
    }

    private fun sanitizeUploadedFile(file: ChatModels.UploadedFile?): ChatModels.UploadedFile? {
        if (file == null) return null
        val url = rawString(file.url)
        val mimeType = rawString(file.mimeType)
        val originalName = rawString(file.originalName)
        if (url.isBlank() || mimeType.isBlank() || originalName.isBlank()) return null
        return file.copy(
            id = rawString(file.id).ifBlank { null },
            url = url,
            mimeType = mimeType,
            originalName = originalName
        )
    }

    private fun sanitizeAttachmentItem(item: ChatModels.AttachmentItem?): ChatModels.AttachmentItem? {
        if (item == null) return null
        val url = rawString(item.url).ifBlank { null }
        val text = rawString(item.text).ifBlank { null }
        val fileName = rawString(item.fileName).ifBlank { null }
        if (url == null && text == null && fileName == null) return null
        return item.copy(
            id = rawString(item.id).ifBlank { null },
            messageId = rawString(item.messageId).ifBlank { null },
            fileName = fileName,
            mimeType = rawString(item.mimeType).ifBlank { null },
            url = url,
            text = text,
            senderId = rawString(item.senderId).ifBlank { null },
            senderName = rawString(item.senderName).ifBlank { null }
        )
    }

    private fun sanitizeChatAttachments(attachments: ChatModels.ChatAttachments?): ChatModels.ChatAttachments {
        if (attachments == null) return ChatModels.ChatAttachments()
        return attachments.copy(
            media = attachments.media.mapNotNull(::sanitizeAttachmentItem),
            files = attachments.files.mapNotNull(::sanitizeAttachmentItem),
            links = attachments.links.mapNotNull(::sanitizeAttachmentItem)
        )
    }

    private fun sanitizeCallHistoryItem(item: ChatModels.CallHistoryItem?): ChatModels.CallHistoryItem? {
        if (item == null) return null
        val id = rawString(item.id)
        val chatId = rawString(item.chatId)
        val callerId = rawString(item.callerId)
        val calleeId = rawString(item.calleeId)
        val type = rawString(item.type)
        val direction = rawString(item.direction)
        val status = rawString(item.status)
        val otherUser = sanitizeUser(item.otherUser) ?: return null
        if (id.isBlank() || chatId.isBlank() || callerId.isBlank() || calleeId.isBlank() || type.isBlank() || direction.isBlank() || status.isBlank()) {
            return null
        }
        return item.copy(
            id = id,
            roomId = rawString(item.roomId).ifBlank { null },
            chatId = chatId,
            callerId = callerId,
            calleeId = calleeId,
            type = type,
            direction = direction,
            status = status,
            endReason = rawString(item.endReason).ifBlank { null },
            otherUser = otherUser
        )
    }

    private fun rawString(value: String?): String = value?.trim().orEmpty()
}
