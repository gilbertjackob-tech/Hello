package com.glassbox.hello.calls

import android.content.Context
import android.util.Log
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class CallSignalingClient(context: Context) : CallSocket {
    private val appContext = context.applicationContext
    private val sessionManager = CloudSessionManager(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    private var currentUser: User? = null
    private var connected: Boolean = false

    override var onCallEvent: ((String, JSONObject) -> Unit)? = null
    override var onConnectedChanged: ((Boolean) -> Unit)? = null

    override fun connect(user: User) {
        currentUser = user
        socket?.close(1000, "reconnect")
        val token = sessionManager.token() ?: user.sessionToken
        if (token.isNullOrBlank()) {
            onConnectedChanged?.invoke(false)
            return
        }
        val wsUrl = "${AppConfig.CHAT_CLOUD_BASE_URL}/api/calls/ws?token=${encode(token)}"
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Cloud call signaling connected for ${user.id}")
                connected = true
                onConnectedChanged?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val envelope = JSONObject(text)
                    val event = envelope.optString("event")
                    val payload = envelope.optJSONObject("payload") ?: envelope
                    if (event.startsWith("call:")) {
                        onCallEvent?.invoke(event, payload)
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to parse cloud call event", it)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                onConnectedChanged?.invoke(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Cloud call signaling failed", t)
                connected = false
                onConnectedChanged?.invoke(false)
            }
        })
    }

    override fun startCall(payload: JSONObject) = Unit
    override fun ringing(payload: JSONObject) = send("call:ringing", payload)
    override fun acceptCall(payload: JSONObject) = send("call:accepted", payload)
    override fun connected(payload: JSONObject) = send("call:connected", payload)
    override fun ack(payload: JSONObject) = send("call:ack", payload)
    override fun declineCall(payload: JSONObject) = send("call:declined", payload)
    override fun busy(payload: JSONObject) = send("call:busy", payload)
    override fun missed(payload: JSONObject) = send("call:missed", payload)
    override fun failed(payload: JSONObject) = send("call:failed", payload)
    override fun endCall(payload: JSONObject) = send("call:ended", payload)
    override fun sendOffer(payload: JSONObject) = send("call:offer", payload)
    override fun sendAnswer(payload: JSONObject) = send("call:answer", payload)
    override fun sendIceCandidate(payload: JSONObject) = send("call:ice-candidate", payload)

    override fun createRoom(payload: JSONObject) = Unit
    override fun joinRoom(payload: JSONObject) = Unit
    override fun leaveRoom(payload: JSONObject) = Unit
    override fun participantState(payload: JSONObject) = send("call:participant-state", payload)
    override fun sendRoomOffer(payload: JSONObject) = send("call:room-offer", payload)
    override fun sendRoomAnswer(payload: JSONObject) = send("call:room-answer", payload)
    override fun sendRoomIceCandidate(payload: JSONObject) = send("call:room-ice-candidate", payload)

    override fun isConnected(): Boolean = connected

    private fun send(event: String, payload: JSONObject) {
        val current = currentUser
        if (current != null && !payload.has("fromUserId")) payload.put("fromUserId", current.id)
        if (!payload.has("callId") && payload.has("roomId")) payload.put("callId", payload.optString("roomId"))
        payload.put("event", event)
        payload.put("eventId", payload.optString("eventId").ifBlank { "evt_${UUID.randomUUID().toString().replace("-", "")}" })
        socket?.send(JSONObject().put("event", event).put("payload", payload).toString())
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private const val TAG = "CloudCallSignaling"
    }
}
