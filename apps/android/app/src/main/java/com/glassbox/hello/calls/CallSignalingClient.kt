package com.glassbox.hello.calls

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.glassbox.hello.debug.AppLog as Log
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
    private val mainHandler = Handler(Looper.getMainLooper())
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
    var onSessionRevoked: ((JSONObject) -> Unit)? = null

    override fun connect(user: User) {
        currentUser = user
        socket?.close(1000, "reconnect")
        val token = sessionManager.token() ?: user.sessionToken
        if (token.isNullOrBlank()) {
            onConnectedChanged?.invoke(false)
            return
        }
        connectSocket(user, token, AppConfig.CHAT_CLOUD_BASE_URL, allowFallback = true)
    }

    override fun disconnect() {
        Log.d(TAG, "Disconnecting cloud call signaling userId=${currentUser?.id}")
        connected = false
        socket?.close(1000, "disconnect")
        socket = null
        currentUser = null
        dispatchOnMain { onConnectedChanged?.invoke(false) }
    }

    private fun connectSocket(user: User, token: String, origin: String, allowFallback: Boolean) {
        val wsUrl = cloudWebSocketUrl(origin, token)
        Log.d(TAG, "Connecting cloud call signaling userId=${user.id} origin=$origin")
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Cloud call signaling connected for ${user.id} origin=$origin")
                connected = true
                dispatchOnMain { onConnectedChanged?.invoke(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val envelope = JSONObject(text)
                    val event = envelope.optString("event")
                    val payload = envelope.optJSONObject("payload") ?: envelope
                    if (event == "session_revoked") {
                        dispatchOnMain { onSessionRevoked?.invoke(payload) }
                        webSocket.close(1000, "session_revoked")
                    } else if (event.startsWith("call:")) {
                        dispatchOnMain { onCallEvent?.invoke(event, payload) }
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to parse cloud call event", it)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Cloud call signaling closed for ${user.id} origin=$origin code=$code reason=$reason")
                connected = false
                dispatchOnMain { onConnectedChanged?.invoke(false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Cloud call signaling failed origin=$origin", t)
                connected = false
                if (allowFallback && origin != AppConfig.CHAT_CLOUD_FALLBACK_URL) {
                    connectSocket(user, token, AppConfig.CHAT_CLOUD_FALLBACK_URL, allowFallback = false)
                    return
                }
                dispatchOnMain { onConnectedChanged?.invoke(false) }
            }
        })
    }

    override fun startCall(payload: JSONObject) = send("call:start", payload)
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

    override fun createRoom(payload: JSONObject) = send("call:room-created", payload)
    override fun joinRoom(payload: JSONObject) = send("call:room-join", payload)
    override fun leaveRoom(payload: JSONObject) = send("call:room-leave", payload)
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
        val activeSocket = socket
        if (activeSocket == null) {
            Log.w(TAG, "Skipping cloud call send event=$event callId=${payload.optString("callId")} reason=no_socket connected=$connected")
            return
        }
        val envelope = JSONObject().put("event", event).put("payload", payload).toString()
        val sent = activeSocket.send(envelope)
        Log.d(
            TAG,
            "Cloud call send event=$event callId=${payload.optString("callId")} eventId=${payload.optString("eventId")} connected=$connected sent=$sent"
        )
        if (!sent) {
            Log.w(TAG, "Cloud call send failed event=$event callId=${payload.optString("callId")} eventId=${payload.optString("eventId")}")
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun cloudWebSocketUrl(origin: String, token: String): String =
        "$origin/api/calls/ws?token=${encode(token)}"
            .replace("https://", "wss://")
            .replace("http://", "ws://")

    private fun dispatchOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "CloudCallSignaling"
    }
}
