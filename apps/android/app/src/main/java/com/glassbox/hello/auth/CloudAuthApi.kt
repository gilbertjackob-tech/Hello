package com.glassbox.hello.auth

import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudAuthApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(name: String, securityQuestion: String, securityAnswer: String): Result<User> = safeCloudCall {
        val response = postWithFallback(
            "/api/auth/register",
            mapOf("name" to name, "securityQuestion" to securityQuestion, "securityAnswer" to securityAnswer)
        )
        parseAuthUser(response)
    }

    suspend fun getUserQuestion(name: String): Result<String> = safeCloudCall {
        val response = getWithFallback("/api/user-question?name=${encode(name)}")
        val responseMap = gson.fromJson(response, Map::class.java)
        (responseMap["securityQuestion"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("User needs registration")
    }

    suspend fun login(name: String, securityAnswer: String): Result<User> = safeCloudCall {
        val response = postWithFallback("/api/auth/login", mapOf("name" to name, "securityAnswer" to securityAnswer))
        parseAuthUser(response)
    }

    suspend fun me(token: String): Result<User> = safeCloudCall {
        val response = getWithFallback("/api/auth/me", token)
        parseAuthUser(response).copy(sessionToken = token)
    }

    suspend fun logout(token: String): Result<Unit> = safeCloudCall {
        postWithFallback("/api/auth/logout", emptyMap(), token)
        Unit
    }

    private suspend fun getWithFallback(pathAndQuery: String): String =
        getWithFallback(pathAndQuery, null)

    private suspend fun getWithFallback(pathAndQuery: String, token: String?): String =
        requestWithFallback { base ->
            Request.Builder()
                .url("$base$pathAndQuery")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .get()
                .build()
        }

    private suspend fun postWithFallback(path: String, body: Map<String, Any?>, token: String? = null): String {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        return requestWithFallback { base ->
            Request.Builder()
                .url("$base$path")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .post(requestBody)
                .build()
        }
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
                val message = runCatching {
                    gson.fromJson(responseBody, Map::class.java)["error"] as? String
                }.getOrNull()
                throw Exception(message ?: "Cloud auth HTTP ${response.code}")
            }
            responseBody ?: throw Exception("Empty cloud auth response")
        }
    }

    private suspend inline fun <T> safeCloudCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseAuthUser(response: String): User {
        val map = gson.fromJson(response, Map::class.java)
        val token = map["token"] as? String
        val nested = map["user"]
        val userJson = if (nested != null) gson.toJson(nested) else response
        val user = gson.fromJson(userJson, User::class.java)
        return user.copy(sessionToken = token)
    }
}
