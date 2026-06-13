package com.glassbox.hello.chat

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Headers
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudChatApi(context: Context? = null) {
    companion object {
        private const val TAG = "HelloInbox"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sessionManager = context?.applicationContext?.let(::CloudSessionManager)

    suspend fun upsertUser(userId: String, name: String, avatar: String?): Result<ChatModels.User> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/users/upsert",
            mapOf("id" to userId, "displayName" to name, "avatarUrl" to avatar).filterValues { it != null }
        )
        sanitizeUser(parseRequired(response, "Upsert user", ChatModels.User::class.java))
            ?: throw Exception("Upsert user response was invalid")
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
        sanitizeChat(parseRequired(response, "Ensure conversation", ChatModels.Chat::class.java))
            ?: throw Exception("Ensure conversation response was invalid")
    }

    suspend fun ensureDirectConversation(
        currentUserId: String,
        targetUserId: String,
        currentUserName: String,
        targetUserName: String? = null
    ): Result<ChatModels.Chat> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/conversations/direct",
            mapOf(
                "type" to "direct",
                "createdBy" to currentUserId,
                "createdByName" to currentUserName,
                "targetUserId" to targetUserId,
                "memberIds" to listOf(currentUserId, targetUserId),
                "title" to targetUserName
            ).filterValues { it != null }
        )
        sanitizeChat(parseRequired(response, "Ensure direct conversation", ChatModels.Chat::class.java))
            ?: throw Exception("Ensure direct conversation response was invalid")
    }

    suspend fun fetchConversations(userId: String): Result<List<ChatModels.Chat>> = safeCloudCall {
        Log.d(TAG, "api_fetch_conversations_start userId=$userId tokenPresent=${!sessionManager?.token().isNullOrBlank()}")
        val response = getWithFallback("/api/chat/conversations?userId=${encode(userId)}")
        logResponseSnippet("api_fetch_conversations_body", response)
        parseChatList(response).also { chats ->
            Log.d(TAG, "api_fetch_conversations_success userId=$userId count=${chats.size} ids=${chats.take(8).joinToString(",") { it.id }}")
        }
    }

    suspend fun fetchUsers(query: String? = null): Result<List<ChatModels.User>> = safeCloudCall {
        val path = if (query.isNullOrBlank()) {
            "/api/users"
        } else {
            "/api/users?q=${encode(query)}"
        }
        Log.d(TAG, "api_fetch_users_start query=${query.orEmpty()} path=$path tokenPresent=${!sessionManager?.token().isNullOrBlank()}")
        val response = getWithFallback(path)
        logResponseSnippet("api_fetch_users_body", response)
        parseUserList(response).also { users ->
            Log.d(TAG, "api_fetch_users_success query=${query.orEmpty()} count=${users.size} ids=${users.take(8).joinToString(",") { it.id }}")
        }
    }

    suspend fun fetchContacts(token: String): Result<List<ChatModels.User>> = safeCloudCall {
        Log.d(TAG, "api_fetch_contacts_start tokenPresent=${token.isNotBlank()}")
        val response = requestWithFallback { base ->
            Request.Builder()
                .url("$base/api/contacts")
                .headers(authHeaders(explicitToken = token))
                .get()
                .build()
        }
        logResponseSnippet("api_fetch_contacts_body", response)
        parseUserList(response).also { users ->
            Log.d(TAG, "api_fetch_contacts_success count=${users.size} ids=${users.take(8).joinToString(",") { it.id }}")
        }
    }

    suspend fun fetchUser(userId: String): Result<ChatModels.User> = safeCloudCall {
        val response = getWithFallback("/api/users/${encode(userId)}")
        sanitizeUser(parseRequired(response, "Fetch user", ChatModels.User::class.java))
            ?: throw Exception("Fetch user response was invalid")
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
        parseMessageList(response)
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        attachmentId: String? = null
    ): Result<ChatModels.Message> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/conversations/${encode(conversationId)}/messages",
            mapOf(
                "text" to text,
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "attachmentId" to attachmentId
            ).filterValues { it != null }
        )
        sanitizeMessage(parseRequired(response, "Send message", ChatModels.Message::class.java))
            ?: throw Exception("Send message response was invalid")
    }

    suspend fun markRead(messageId: String, userId: String): Result<Unit> = safeCloudCall {
        postWithFallback("/api/chat/messages/${encode(messageId)}/read", mapOf("userId" to userId))
        Unit
    }

    suspend fun reactToMessage(messageId: String, emoji: String, userId: String): Result<ChatModels.Message> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/messages/${encode(messageId)}/react",
            mapOf("emoji" to emoji, "userId" to userId)
        )
        sanitizeMessage(parseRequired(response, "React message", ChatModels.Message::class.java))
            ?: throw Exception("React message response was invalid")
    }

    suspend fun starMessage(messageId: String, userId: String): Result<ChatModels.Message> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/messages/${encode(messageId)}/star",
            mapOf("userId" to userId)
        )
        sanitizeMessage(parseRequired(response, "Star message", ChatModels.Message::class.java))
            ?: throw Exception("Star message response was invalid")
    }

    suspend fun pinMessage(messageId: String, userId: String, durationDays: Int): Result<ChatModels.Message> = safeCloudCall {
        val response = postWithFallback(
            "/api/chat/messages/${encode(messageId)}/pin",
            mapOf("userId" to userId, "durationDays" to durationDays)
        )
        sanitizeMessage(parseRequired(response, "Pin message", ChatModels.Message::class.java))
            ?: throw Exception("Pin message response was invalid")
    }

    suspend fun deleteMessage(messageId: String, userId: String, type: String): Result<ChatModels.Message> = safeCloudCall {
        val response = deleteWithFallback(
            "/api/chat/messages/${encode(messageId)}",
            mapOf("userId" to userId, "type" to type)
        )
        sanitizeMessage(parseRequired(response, "Delete message", ChatModels.Message::class.java))
            ?: throw Exception("Delete message response was invalid")
    }

    suspend fun clearConversation(conversationId: String, userId: String): Result<Unit> = safeCloudCall {
        deleteWithFallback("/api/chat/conversations/${encode(conversationId)}/clear", mapOf("userId" to userId))
        Unit
    }

    suspend fun deleteConversation(conversationId: String, userId: String): Result<Unit> = safeCloudCall {
        deleteWithFallback("/api/chat/conversations/${encode(conversationId)}", mapOf("userId" to userId))
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
                .headers(authHeaders())
                .post(body)
                .build()
        }
        sanitizeUploadedFile(parseRequired(response, "Upload attachment", ChatModels.UploadedFile::class.java))
            ?: throw Exception("Upload attachment response was invalid")
    }

    private suspend fun getWithFallback(pathAndQuery: String): String =
        requestWithFallback { base ->
            Request.Builder()
                .url("$base$pathAndQuery")
                .headers(authHeaders())
                .get()
                .build()
        }

    private suspend fun postWithFallback(path: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return requestWithFallback { base ->
            Request.Builder()
                .url("$base$path")
                .headers(authHeaders())
                .post(requestBody)
                .build()
        }
    }

    private suspend fun deleteWithFallback(path: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return requestWithFallback { base ->
            Request.Builder()
                .url("$base$path")
                .headers(authHeaders())
                .delete(requestBody)
                .build()
        }
    }

    private fun authHeaders(explicitToken: String? = null): Headers {
        val token = explicitToken ?: sessionManager?.token()
        val builder = Headers.Builder()
        if (!token.isNullOrBlank()) {
            builder.add("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    private suspend fun requestWithFallback(build: (String) -> Request): String {
        val primary = runCatching { request(build(AppConfig.CHAT_CLOUD_BASE_URL)) }
        return primary.getOrElse {
            if (AppConfig.CHAT_CLOUD_FALLBACK_URL == AppConfig.CHAT_CLOUD_BASE_URL) {
                throw it
            }
            request(build(AppConfig.CHAT_CLOUD_FALLBACK_URL))
        }
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception(parseErrorMessage(response.code, response.message, responseBody))
            }
            responseBody ?: throw Exception("Empty cloud chat response")
        }
    }

    private suspend inline fun <T> safeCloudCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Log.w(TAG, "api_call_error message=${error.message}", error)
            Result.failure(error)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseErrorMessage(code: Int, message: String, body: String?): String {
        val backendError = if (body.isNullOrBlank()) null else {
            runCatching { (gson.fromJson(body, Map::class.java) as? Map<*, *>)?.get("error") as? String }
                .getOrNull()
        }
        return when {
            backendError == "not_found" -> "Cloud chat route is not deployed on this domain"
            !backendError.isNullOrBlank() -> backendError
            else -> "Cloud chat HTTP $code: $message"
        }
    }

    private fun logResponseSnippet(label: String, response: String) {
        val compact = response.replace('\n', ' ').replace('\r', ' ').trim()
        Log.d(TAG, "$label length=${response.length} snippet=${compact.take(240)}")
    }

    private fun <T> parseRequired(raw: String, label: String, type: Class<T>): T =
        when (type) {
            ChatModels.User::class.java -> type.cast(objectToUser(parseObject(raw, label)))
            ChatModels.Chat::class.java -> type.cast(objectToChat(parseObject(raw, label)))
            ChatModels.Message::class.java -> type.cast(objectToMessage(parseObject(raw, label)))
            ChatModels.UploadedFile::class.java -> type.cast(objectToUploadedFile(parseObject(raw, label)))
            else -> gson.fromJson(raw, type)
        } ?: throw Exception("$label response was empty")

    private fun parseUserList(raw: String): List<ChatModels.User> =
        parseArray(raw)
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::objectToUser) }
            .mapNotNull(::sanitizeUser)

    private fun parseChatList(raw: String): List<ChatModels.Chat> =
        parseArray(raw)
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::objectToChat) }
            .mapNotNull(::sanitizeChat)

    private fun parseMessageList(raw: String): List<ChatModels.Message> =
        parseArray(raw)
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::objectToMessage) }
            .mapNotNull(::sanitizeMessage)

    private fun parseArray(raw: String): JsonArray =
        JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun parseObject(raw: String, label: String): JsonObject =
        JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("$label response was not an object")

    private fun objectToUser(obj: JsonObject): ChatModels.User? {
        val id = obj.string("id")
        if (id.isBlank()) return null
        return ChatModels.User(
            id = id,
            name = obj.string("name").ifBlank { obj.string("displayName").ifBlank { id } },
            avatar = obj.string("avatar").ifBlank { obj.string("avatarUrl") }.ifBlank { null },
            username = obj.string("username").ifBlank { null },
            phone = obj.string("phone").ifBlank { null },
            email = obj.string("email").ifBlank { null },
            online = obj.boolOrNull("online"),
            lastActive = obj.longOrNull("lastActive"),
            privacy = obj.string("privacy").ifBlank { null },
            lastActivePrivacy = obj.string("lastActivePrivacy").ifBlank { null }
        )
    }

    private fun objectToChat(obj: JsonObject): ChatModels.Chat? {
        val id = obj.string("id")
        if (id.isBlank()) return null
        val participants = obj.array("participants")
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::objectToUser) }
        val members = obj.array("members")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        return ChatModels.Chat(
            id = id,
            type = obj.string("type").ifBlank { null },
            directKey = obj.string("directKey").ifBlank { null },
            name = obj.string("name"),
            avatar = obj.string("avatar").ifBlank { null },
            lastMessage = obj.string("lastMessage").ifBlank { null },
            lastMessageTime = obj.longOrNull("lastMessageTime"),
            unreadCount = obj.intOrNull("unreadCount"),
            isGroup = obj.bool("isGroup") || obj.string("type").equals("group", ignoreCase = true),
            members = members.ifEmpty { null },
            participants = participants.ifEmpty { null }
        )
    }

    private fun objectToMessage(obj: JsonObject): ChatModels.Message? {
        val id = obj.string("id")
        val chatId = obj.string("chatId")
        val senderId = obj.string("senderId")
        if (id.isBlank() || chatId.isBlank() || senderId.isBlank()) return null
        val deletedFor = obj.array("deletedFor")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        val reactions = obj.array("reactions")
            .mapNotNull { element ->
                val reaction = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val emoji = reaction.string("emoji")
                val reactionUserId = reaction.string("userId")
                if (emoji.isBlank() || reactionUserId.isBlank()) null else ChatModels.Reaction(emoji, reactionUserId)
            }
        val starredBy = obj.array("starredBy")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        val replyTo = obj.objectOrNull("replyTo")?.let { reply ->
            val replyId = reply.string("id")
            if (replyId.isBlank()) null else ChatModels.ReplyTo(
                id = replyId,
                text = reply.string("text"),
                senderName = reply.string("senderName"),
                senderId = reply.string("senderId").ifBlank { null }
            )
        }
        val location = obj.objectOrNull("location")?.let { locationObj ->
            val lat = locationObj.doubleOrNull("lat")
            val lng = locationObj.doubleOrNull("lng")
            if (lat == null || lng == null) null else ChatModels.LocationData(
                lat = lat,
                lng = lng,
                isLive = locationObj.boolOrNull("isLive"),
                expiresAt = locationObj.longOrNull("expiresAt")
            )
        }
        val callInfo = obj.objectOrNull("callInfo")?.let { call ->
            val callId = call.string("callId")
            if (callId.isBlank()) null else ChatModels.CallInfo(
                callId = callId,
                chatId = call.string("chatId").ifBlank { null },
                callerId = call.string("callerId").ifBlank { null },
                calleeId = call.string("calleeId").ifBlank { null },
                callType = call.string("callType").ifBlank { null },
                status = call.string("status").ifBlank { null },
                startedAt = call.longOrNull("startedAt"),
                answeredAt = call.longOrNull("answeredAt"),
                endedAt = call.longOrNull("endedAt"),
                durationSeconds = call.longOrNull("durationSeconds"),
                endReason = call.string("endReason").ifBlank { null },
                mode = call.string("mode").ifBlank { null }
            )
        }
        return ChatModels.Message(
            id = id,
            chatId = chatId,
            senderId = senderId,
            senderName = obj.string("senderName").ifBlank { senderId },
            senderAvatar = obj.string("senderAvatar").ifBlank { null },
            text = obj.string("text"),
            messageType = obj.string("messageType").ifBlank { null },
            timestamp = obj.longOrNull("timestamp") ?: 0L,
            attachmentUrl = obj.string("attachmentUrl").ifBlank { null },
            attachmentType = obj.string("attachmentType").ifBlank { null },
            attachmentName = obj.string("attachmentName").ifBlank { null },
            attachmentSize = obj.longOrNull("attachmentSize"),
            status = obj.string("status").ifBlank { null },
            isDeleted = obj.boolOrNull("isDeleted"),
            deletedFor = deletedFor.ifEmpty { null },
            reactions = reactions.ifEmpty { null },
            starredBy = starredBy.ifEmpty { null },
            pinnedUntil = obj.longOrNull("pinnedUntil"),
            location = location,
            replyTo = replyTo,
            callInfo = callInfo
        )
    }

    private fun objectToUploadedFile(obj: JsonObject): ChatModels.UploadedFile? {
        val url = obj.string("url")
        val mimeType = obj.string("mimeType")
        val originalName = obj.string("originalName")
        if (url.isBlank() || mimeType.isBlank() || originalName.isBlank()) return null
        return ChatModels.UploadedFile(
            id = obj.string("id").ifBlank { null },
            url = url,
            mimeType = mimeType,
            originalName = originalName,
            size = obj.longOrNull("size") ?: 0L
        )
    }

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

    private fun rawString(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return when {
            normalized.equals("null", ignoreCase = true) -> ""
            normalized.equals("undefined", ignoreCase = true) -> ""
            else -> normalized
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject

    private fun JsonElement.stringOrNull(): String? =
        takeIf { isJsonPrimitive && asJsonPrimitive.isString }?.asString

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.let { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonPrimitive -> element.asJsonPrimitive.toString()
                else -> ""
            }
        }.orEmpty()

    private fun JsonObject.longOrNull(name: String): Long? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull()

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()

    private fun JsonObject.bool(name: String): Boolean =
        boolOrNull(name) == true

    private fun JsonObject.boolOrNull(name: String): Boolean? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asBoolean }?.getOrNull()

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}

object CloudChatPayloadParser {
    private fun parseObject(raw: String): JsonObject? =
        runCatching { JsonParser.parseString(raw) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

    fun parseUser(raw: String): ChatModels.User? =
        parseObject(raw)?.let(::objectToUser)?.let(::sanitizeUser)

    fun parseChat(raw: String): ChatModels.Chat? =
        parseObject(raw)?.let(::objectToChat)?.let(::sanitizeChat)

    fun parseMessage(raw: String): ChatModels.Message? =
        parseObject(raw)?.let(::objectToMessage)?.let(::sanitizeMessage)

    private fun objectToUser(obj: JsonObject): ChatModels.User? {
        val id = obj.string("id")
        if (id.isBlank()) return null
        return ChatModels.User(
            id = id,
            name = obj.string("name").ifBlank { obj.string("displayName").ifBlank { id } },
            avatar = obj.string("avatar").ifBlank { obj.string("avatarUrl") }.ifBlank { null },
            username = obj.string("username").ifBlank { null },
            phone = obj.string("phone").ifBlank { null },
            email = obj.string("email").ifBlank { null },
            online = obj.boolOrNull("online"),
            lastActive = obj.longOrNull("lastActive"),
            privacy = obj.string("privacy").ifBlank { null },
            lastActivePrivacy = obj.string("lastActivePrivacy").ifBlank { null }
        )
    }

    private fun objectToChat(obj: JsonObject): ChatModels.Chat? {
        val id = obj.string("id")
        if (id.isBlank()) return null
        val participants = obj.array("participants")
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::objectToUser) }
        val members = obj.array("members")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        return ChatModels.Chat(
            id = id,
            type = obj.string("type").ifBlank { null },
            directKey = obj.string("directKey").ifBlank { null },
            name = obj.string("name"),
            avatar = obj.string("avatar").ifBlank { null },
            lastMessage = obj.string("lastMessage").ifBlank { null },
            lastMessageTime = obj.longOrNull("lastMessageTime"),
            unreadCount = obj.intOrNull("unreadCount"),
            isGroup = obj.bool("isGroup") || obj.string("type").equals("group", ignoreCase = true),
            members = members.ifEmpty { null },
            participants = participants.ifEmpty { null }
        )
    }

    private fun objectToMessage(obj: JsonObject): ChatModels.Message? {
        val id = obj.string("id")
        val chatId = obj.string("chatId")
        val senderId = obj.string("senderId")
        if (id.isBlank() || chatId.isBlank() || senderId.isBlank()) return null
        val deletedFor = obj.array("deletedFor")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        val reactions = obj.array("reactions")
            .mapNotNull { element ->
                val reaction = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val emoji = reaction.string("emoji")
                val reactionUserId = reaction.string("userId")
                if (emoji.isBlank() || reactionUserId.isBlank()) null else ChatModels.Reaction(emoji, reactionUserId)
            }
        val starredBy = obj.array("starredBy")
            .mapNotNull { element -> element.stringOrNull()?.trim()?.takeIf { it.isNotBlank() } }
        val replyTo = obj.objectOrNull("replyTo")?.let { reply ->
            val replyId = reply.string("id")
            if (replyId.isBlank()) null else ChatModels.ReplyTo(
                id = replyId,
                text = reply.string("text"),
                senderName = reply.string("senderName"),
                senderId = reply.string("senderId").ifBlank { null }
            )
        }
        val location = obj.objectOrNull("location")?.let { locationObj ->
            val lat = locationObj.doubleOrNull("lat")
            val lng = locationObj.doubleOrNull("lng")
            if (lat == null || lng == null) null else ChatModels.LocationData(
                lat = lat,
                lng = lng,
                isLive = locationObj.boolOrNull("isLive"),
                expiresAt = locationObj.longOrNull("expiresAt")
            )
        }
        val callInfo = obj.objectOrNull("callInfo")?.let { call ->
            val callId = call.string("callId")
            if (callId.isBlank()) null else ChatModels.CallInfo(
                callId = callId,
                chatId = call.string("chatId").ifBlank { null },
                callerId = call.string("callerId").ifBlank { null },
                calleeId = call.string("calleeId").ifBlank { null },
                callType = call.string("callType").ifBlank { null },
                status = call.string("status").ifBlank { null },
                startedAt = call.longOrNull("startedAt"),
                answeredAt = call.longOrNull("answeredAt"),
                endedAt = call.longOrNull("endedAt"),
                durationSeconds = call.longOrNull("durationSeconds"),
                endReason = call.string("endReason").ifBlank { null },
                mode = call.string("mode").ifBlank { null }
            )
        }
        return ChatModels.Message(
            id = id,
            chatId = chatId,
            senderId = senderId,
            senderName = obj.string("senderName").ifBlank { senderId },
            senderAvatar = obj.string("senderAvatar").ifBlank { null },
            text = obj.string("text"),
            messageType = obj.string("messageType").ifBlank { null },
            timestamp = obj.longOrNull("timestamp") ?: 0L,
            attachmentUrl = obj.string("attachmentUrl").ifBlank { null },
            attachmentType = obj.string("attachmentType").ifBlank { null },
            attachmentName = obj.string("attachmentName").ifBlank { null },
            attachmentSize = obj.longOrNull("attachmentSize"),
            status = obj.string("status").ifBlank { null },
            isDeleted = obj.boolOrNull("isDeleted"),
            deletedFor = deletedFor.ifEmpty { null },
            reactions = reactions.ifEmpty { null },
            starredBy = starredBy.ifEmpty { null },
            pinnedUntil = obj.longOrNull("pinnedUntil"),
            location = location,
            replyTo = replyTo,
            callInfo = callInfo
        )
    }

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
        return chat.copy(
            id = id,
            type = rawString(chat.type).ifBlank { null },
            directKey = rawString(chat.directKey).ifBlank { null },
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
        return user.copy(
            id = id,
            name = rawString(user.name).ifBlank { id },
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

    private fun rawString(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return when {
            normalized.equals("null", ignoreCase = true) -> ""
            normalized.equals("undefined", ignoreCase = true) -> ""
            else -> normalized
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject

    private fun JsonElement.stringOrNull(): String? =
        takeIf { isJsonPrimitive && asJsonPrimitive.isString }?.asString

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.let { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonPrimitive -> element.asJsonPrimitive.toString()
                else -> ""
            }
        }.orEmpty()

    private fun JsonObject.longOrNull(name: String): Long? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull()

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()

    private fun JsonObject.bool(name: String): Boolean =
        boolOrNull(name) == true

    private fun JsonObject.boolOrNull(name: String): Boolean? =
        get(name)?.takeIf { !it.isJsonNull }?.runCatching { asBoolean }?.getOrNull()

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}
