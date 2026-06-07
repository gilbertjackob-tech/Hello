package com.glassbox.hello.calls

import android.content.Context
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudCallApi(context: Context) {
    private val sessionManager = CloudSessionManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun startCall(callerId: String, calleeId: String, chatId: String, type: String): Result<String> = safeCall {
        val response = post(
            "/api/calls/start",
            mapOf(
                "callerUserId" to callerId,
                "receiverUserId" to calleeId,
                "chatId" to chatId,
                "type" to type
            )
        )
        val map = parseMap(response)
        (map["callId"] as? String) ?: (map["id"] as? String) ?: throw Exception("Call id not returned")
    }

    suspend fun history(userId: String): Result<List<ChatModels.CallHistoryItem>> = safeCall {
        val response = get("/api/calls/history?userId=${encode(userId)}")
        val type = object : TypeToken<List<ChatModels.CallHistoryItem>>() {}.type
        gson.fromJson<List<ChatModels.CallHistoryItem>>(response, type) ?: emptyList()
    }

    suspend fun iceServers(): Result<List<CallIceServer>> = safeCall {
        val response = get("/api/calls/ice-servers")
        val map = parseMap(response)
        val raw = map["iceServers"] ?: emptyList<Any>()
        val type = object : TypeToken<List<CallIceServer>>() {}.type
        gson.fromJson<List<CallIceServer>>(gson.toJson(raw), type) ?: emptyList()
    }

    suspend fun createGroupRoom(chatId: String, hostId: String, type: String, participantIds: List<String>): Result<CallRoom> = safeCall {
        val response = post(
            "/api/calls/group/start",
            mapOf(
                "chatId" to chatId,
                "hostId" to hostId,
                "type" to type,
                "participantIds" to participantIds
            )
        )
        parseRequired(response, "Create group call room", CallRoom::class.java)
    }

    suspend fun joinGroupRoom(roomId: String, userId: String): Result<CallRoom> = safeCall {
        val response = post("/api/calls/group/${encode(roomId)}/join", mapOf("userId" to userId))
        parseRequired(response, "Join group call room", CallRoom::class.java)
    }

    suspend fun leaveGroupRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom> = safeCall {
        val response = post("/api/calls/group/${encode(roomId)}/leave", mapOf("userId" to userId, "ended" to ended))
        parseRequired(response, "Leave group call room", CallRoom::class.java)
    }

    private suspend fun get(path: String): String =
        requestWithFallback { base -> Request.Builder().url("$base$path").headers(authHeaders()).get().build() }

    private suspend fun post(path: String, body: Map<String, Any?>): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return requestWithFallback { base ->
            Request.Builder().url("$base$path").headers(authHeaders()).post(requestBody).build()
        }
    }

    private fun authHeaders(): okhttp3.Headers {
        val token = sessionManager.token() ?: throw IllegalStateException("Cloud account session required for calls")
        return okhttp3.Headers.Builder().add("Authorization", "Bearer $token").build()
    }

    private suspend fun requestWithFallback(build: (String) -> Request): String {
        val primary = runCatching { request(build(AppConfig.CHAT_CLOUD_BASE_URL)) }
        return primary.getOrElse { request(build(AppConfig.CHAT_CLOUD_FALLBACK_URL)) }
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val message = parseMap(body)["error"] as? String
                throw Exception(message ?: "Cloud call HTTP ${response.code}")
            }
            body ?: throw Exception("Empty cloud call response")
        }
    }

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

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
}
