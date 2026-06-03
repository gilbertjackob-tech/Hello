package com.glassbox.hello.notifications

import android.content.Context
import android.util.Log
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.core.AppConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FcmPushRegistrar {
    private const val TAG = "HelloFCM"
    private const val PREFS_NAME = "hello_push"
    private const val KEY_PENDING_TOKEN = "pending_fcm_token"
    private const val KEY_REGISTERED_TOKEN = "registered_fcm_token"
    private const val KEY_DEVICE_ID = "fcm_device_id"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun refreshAndRegister(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> registerToken(context, token, "startup") }
            .addOnFailureListener { error -> Log.w(TAG, "Could not fetch FCM token", error) }
    }

    fun registerToken(context: Context, token: String, reason: String = "refresh") {
        val appContext = context.applicationContext
        if (token.isBlank()) return
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TOKEN, token)
            .apply()
        CoroutineScope(Dispatchers.IO).launch {
            registerPendingToken(appContext, reason)
        }
    }

    suspend fun registerPendingToken(context: Context, reason: String = "retry") {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_PENDING_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return
        val sessionToken = CloudSessionManager(appContext).token()
        if (sessionToken.isNullOrBlank()) {
            Log.d(TAG, "FCM token pending registration; no cloud session yet")
            return
        }
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
            ?: "android_${Integer.toHexString(token.hashCode())}"
        val body = JSONObject()
            .put("id", deviceId)
            .put("deviceId", deviceId)
            .put("token", token)
            .put("platform", "android")
            .put("deviceName", android.os.Build.MODEL)
            .toString()
            .toRequestBody(jsonMediaType)
        runCatching {
            requestWithFallback { base ->
                Request.Builder()
                    .url("$base/api/devices/register")
                    .header("Authorization", "Bearer $sessionToken")
                    .post(body)
                    .build()
            }
        }.onSuccess {
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_REGISTERED_TOKEN, token)
                .remove(KEY_PENDING_TOKEN)
                .apply()
            Log.d(TAG, "FCM token registered ($reason)")
        }.onFailure { error ->
            Log.w(TAG, "FCM token registration failed; will retry later", error)
        }
    }

    private suspend fun requestWithFallback(build: (String) -> Request): String {
        val primary = runCatching { request(build(AppConfig.CHAT_CLOUD_BASE_URL)) }
        return primary.getOrElse { request(build(AppConfig.CHAT_CLOUD_FALLBACK_URL)) }
    }

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("push token HTTP ${response.code}: ${responseBody ?: response.message}")
            }
            responseBody.orEmpty()
        }
    }
}
