package com.glassbox.hello.network

import android.content.Context
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.calls.CallSocket
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit

class SocketManager private constructor() : CallSocket {
    private val gson = Gson()
    private val socketLock = Any()
    private var socket: Socket? = null
    private var cloudSocket: WebSocket? = null
    private var cloudConnected = false
    private val cloudClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var currentUser: User? = null
    private var currentChatId: String? = null
    private var isConnecting = false
    private val messageListeners = CopyOnWriteArraySet<(ChatModels.Message) -> Unit>()
    private val messageUpdateListeners = CopyOnWriteArraySet<(ChatModels.Message) -> Unit>()
    private val typingListeners = CopyOnWriteArraySet<(JSONObject) -> Unit>()

    var onMessageReceived: ((ChatModels.Message) -> Unit)? = null
    var onMessageUpdated: ((ChatModels.Message) -> Unit)? = null
    var onChatUpdated: ((ChatModels.Chat) -> Unit)? = null
    var onPresenceUpdated: ((JSONObject) -> Unit)? = null
    var onTyping: ((JSONObject) -> Unit)? = null
    override var onCallEvent: ((String, JSONObject) -> Unit)? = null
    override var onConnectedChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "HelloSocket"

        @Volatile
        private var instance: SocketManager? = null

        fun getInstance(): SocketManager =
            instance ?: synchronized(this) {
                instance ?: SocketManager().also { instance = it }
            }
    }

    override fun connect(user: User) {
        synchronized(socketLock) {
            currentUser = user
            if (socket?.connected() == true) {
                Log.d(TAG, "[CALL_TRACE] android socket connect_skip reason=already_connected userId=${user.id}")
                identify()
                return
            }
            if (isConnecting) {
                Log.d(TAG, "[CALL_TRACE] android socket connect_skip reason=already_connecting userId=${user.id}")
                return
            }
            socket?.let {
                it.off()
                it.disconnect()
                socket = null
            }
            Log.d(TAG, "[CALL_TRACE] android socket connect_start userId=${user.id}")
            isConnecting = true
        }

        val options = IO.Options().apply {
            path = AppConfig.CHAT_SOCKET_PATH
            transports = arrayOf("websocket", "polling")
            reconnection = true
            reconnectionAttempts = 5
            reconnectionDelay = 1000
        }

        val nextSocket = IO.socket(AppConfig.CHAT_SOCKET_ORIGIN, options).also { s ->
            s.on(Socket.EVENT_CONNECT) {
                synchronized(socketLock) {
                    isConnecting = false
                }
                Log.d(TAG, "[CALL_TRACE] android socket connected userId=${currentUser?.id.orEmpty()}")
                onConnectedChanged?.invoke(true)
                identify()
                currentChatId?.let { s.emit("join_chat", it) }
            }
            s.on(Socket.EVENT_DISCONNECT) {
                synchronized(socketLock) {
                    isConnecting = false
                }
                Log.d(TAG, "[CALL_TRACE] android socket disconnected")
                onConnectedChanged?.invoke(false)
            }
            s.on(Socket.EVENT_CONNECT_ERROR) {
                synchronized(socketLock) {
                    isConnecting = false
                }
                Log.w(TAG, "[CALL_TRACE] android socket disconnected reason=connect_error")
                onConnectedChanged?.invoke(false)
            }
            s.on("receive_message") { args ->
                parse<ChatModels.Message>(args.firstOrNull())?.let { message ->
                    onMessageReceived?.invoke(message)
                    messageListeners.forEach { listener -> listener(message) }
                }
            }
            s.on("message_updated") { args ->
                parse<ChatModels.Message>(args.firstOrNull())?.let {
                    onMessageUpdated?.invoke(it)
                    messageUpdateListeners.forEach { listener -> listener(it) }
                }
            }
            s.on("chat_updated") { args ->
                parse<ChatModels.Chat>(args.firstOrNull())?.let { onChatUpdated?.invoke(it) }
            }
            s.on("new_chat") { args ->
                parse<ChatModels.Chat>(args.firstOrNull())?.let { onChatUpdated?.invoke(it) }
            }
            s.on("user_presence") { args ->
                parseJsonObject(args.firstOrNull())?.let { onPresenceUpdated?.invoke(it) }
            }
            s.on("user_updated") { args ->
                parseJsonObject(args.firstOrNull())?.let { onPresenceUpdated?.invoke(it) }
            }
            s.on("presence_updated") { args ->
                (args.firstOrNull() as? JSONObject)?.let { onPresenceUpdated?.invoke(it) }
            }
            s.on("user_typing") { args ->
                parseJsonObject(args.firstOrNull())?.let {
                    onTyping?.invoke(it)
                    typingListeners.forEach { listener -> listener(it) }
                }
            }
            listOf(
                "call:start",
                "call:offer",
                "call:answer",
                "call:ice-candidate",
                "call:ringing",
                "call:accepted",
                "call:connected",
                "call:reconnecting",
                "call:busy",
                "call:missed",
                "call:unavailable",
                "call:declined",
                "call:ended",
                "call:failed",
                "call:ack",
                "call:history-updated",
                "call:room-created",
                "call:room-join",
                "call:room-leave",
                "call:room-full",
                "call:participant-state",
                "call:room-offer",
                "call:room-answer",
                "call:room-ice-candidate"
            ).forEach { event ->
                s.on(event) { args ->
                    parseJsonObject(args.firstOrNull())?.let {
                        Log.d(
                            TAG,
                            "[CALL_TRACE] android recv event=$event callId=${it.optString("callId")} eventId=${it.optString("eventId")} hasOfferSdp=${it.optJSONObject("offer")?.optString("sdp").isNullOrBlank().not()} hasAnswerSdp=${it.optJSONObject("answer")?.optString("sdp").isNullOrBlank().not()} hasIce=${it.optJSONObject("candidate")?.optString("candidate").isNullOrBlank().not()}"
                        )
                        onCallEvent?.invoke(event, it)
                    }
                }
            }
            s.connect()
        }
        synchronized(socketLock) {
            socket = nextSocket
        }
    }

    fun connect(context: Context, user: User) {
        val token = CloudSessionManager(context.applicationContext).token() ?: user.sessionToken
        if (token.isNullOrBlank()) {
            connect(user)
            return
        }
        synchronized(socketLock) {
            currentUser = user
            socket?.let {
                it.off()
                it.disconnect()
                socket = null
            }
            cloudSocket?.close(1000, "reconnect")
            cloudConnected = false
            isConnecting = true
        }
        val wsUrl = "${AppConfig.CHAT_CLOUD_BASE_URL}/api/calls/ws?token=${encode(token)}"
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val request = Request.Builder().url(wsUrl).build()
        cloudSocket = cloudClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(socketLock) {
                    isConnecting = false
                    cloudConnected = true
                }
                Log.d(TAG, "Cloud chat realtime connected for ${user.id}")
                onConnectedChanged?.invoke(true)
                identify()
                emitCloudPresence("online")
                currentChatId?.let { emitCloud("join_chat", JSONObject(mapOf("chatId" to it))) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val envelope = JSONObject(text)
                    val event = envelope.optString("event")
                    val payload = envelope.optJSONObject("payload") ?: envelope
                    dispatchCloudEvent(event, payload)
                }.onFailure {
                    Log.w(TAG, "Failed to parse cloud realtime event", it)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(socketLock) {
                    isConnecting = false
                    cloudConnected = false
                }
                onConnectedChanged?.invoke(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(socketLock) {
                    isConnecting = false
                    cloudConnected = false
                }
                Log.w(TAG, "Cloud chat realtime failed", t)
                onConnectedChanged?.invoke(false)
            }
        })
    }

    fun identify() {
        val user = currentUser ?: return
        if (cloudConnected) {
            emitCloudPresence("identify")
            return
        }
        socket?.emit("identify", user.id)
    }

    fun joinChat(chatId: String) {
        currentChatId?.takeIf { it != chatId }?.let { socket?.emit("leave_chat", it) }
        currentChatId = chatId
        if (cloudConnected) {
            emitCloud("join_chat", JSONObject(mapOf("chatId" to chatId)))
            return
        }
        socket?.emit("join_chat", chatId)
    }

    fun leaveChat(chatId: String) {
        if (currentChatId == chatId) currentChatId = null
        if (cloudConnected) {
            emitCloud("leave_chat", JSONObject(mapOf("chatId" to chatId)))
            return
        }
        socket?.emit("leave_chat", chatId)
    }

    fun typing(chatId: String, userId: String, userName: String, isTyping: Boolean = true) {
        val payload = JSONObject(
            mapOf(
                "chatId" to chatId,
                "userId" to userId,
                "senderName" to userName,
                "userName" to userName,
                "isTyping" to isTyping
            )
        )
        if (cloudConnected) {
            emitCloud("typing", payload)
            return
        }
        socket?.emit(
            "typing",
            payload
        )
    }

    fun markMessagesRead(chatId: String, readerId: String) {
        if (cloudConnected) {
            emitCloud("mark_messages_read", JSONObject(mapOf("chatId" to chatId, "readerId" to readerId)))
            return
        }
        socket?.emit("mark_messages_read", JSONObject(mapOf("chatId" to chatId, "readerId" to readerId)))
    }

    fun addMessageListener(listener: (ChatModels.Message) -> Unit) {
        messageListeners.add(listener)
    }

    fun removeMessageListener(listener: (ChatModels.Message) -> Unit) {
        messageListeners.remove(listener)
    }

    fun addMessageUpdateListener(listener: (ChatModels.Message) -> Unit) {
        messageUpdateListeners.add(listener)
    }

    fun removeMessageUpdateListener(listener: (ChatModels.Message) -> Unit) {
        messageUpdateListeners.remove(listener)
    }

    fun addTypingListener(listener: (JSONObject) -> Unit) {
        typingListeners.add(listener)
    }

    fun removeTypingListener(listener: (JSONObject) -> Unit) {
        typingListeners.remove(listener)
    }

    override fun startCall(payload: JSONObject) {
        emitCall("call:start", payload)
    }

    override fun ringing(payload: JSONObject) {
        emitCall("call:ringing", payload)
    }

    override fun acceptCall(payload: JSONObject) {
        emitCall("call:accepted", payload)
    }

    override fun connected(payload: JSONObject) {
        emitCall("call:connected", payload)
    }

    override fun ack(payload: JSONObject) {
        Log.d(TAG, "[CALL_TRACE] android emit event=call:ack callId=${payload.optString("callId")} eventId=${payload.optString("eventId")}")
        socket?.emit("call:ack", payload)
    }

    override fun declineCall(payload: JSONObject) {
        emitCall("call:declined", payload)
    }

    override fun busy(payload: JSONObject) {
        emitCall("call:busy", payload)
    }

    override fun missed(payload: JSONObject) {
        emitCall("call:missed", payload)
    }

    override fun failed(payload: JSONObject) {
        emitCall("call:failed", payload)
    }

    override fun endCall(payload: JSONObject) {
        emitCall("call:ended", payload)
    }

    override fun sendOffer(payload: JSONObject) {
        emitCall("call:offer", payload)
    }

    override fun sendAnswer(payload: JSONObject) {
        emitCall("call:answer", payload)
    }

    override fun sendIceCandidate(payload: JSONObject) {
        emitCall("call:ice-candidate", payload)
    }

    override fun createRoom(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-created", payload) else socket?.emit("call:room-created", payload)
    }

    override fun joinRoom(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-join", payload) else socket?.emit("call:room-join", payload)
    }

    override fun leaveRoom(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-leave", payload) else socket?.emit("call:room-leave", payload)
    }

    override fun participantState(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:participant-state", payload) else socket?.emit("call:participant-state", payload)
    }

    override fun sendRoomOffer(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-offer", payload) else socket?.emit("call:room-offer", payload)
    }

    override fun sendRoomAnswer(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-answer", payload) else socket?.emit("call:room-answer", payload)
    }

    override fun sendRoomIceCandidate(payload: JSONObject) {
        if (cloudConnected) emitCloud("call:room-ice-candidate", payload) else socket?.emit("call:room-ice-candidate", payload)
    }

    fun disconnect() {
        val (existing, chatToLeave) = synchronized(socketLock) {
            isConnecting = false
            val existingSocket = socket
            val existingChat = currentChatId
            socket = null
            cloudSocket?.close(1000, "disconnect")
            cloudSocket = null
            cloudConnected = false
            currentUser = null
            currentChatId = null
            existingSocket to existingChat
        }
        chatToLeave?.let { existing?.emit("leave_chat", it) }
        existing?.off()
        existing?.disconnect()
        Log.d(TAG, "[CALL_TRACE] android socket disconnected")
    }

    override fun isConnected(): Boolean = cloudConnected || socket?.connected() == true

    private inline fun <reified T> parse(value: Any?): T? {
        return try {
            when (value) {
                null -> null
                is JSONObject -> gson.fromJson(value.toString(), T::class.java)
                else -> gson.fromJson(value.toString(), T::class.java)
            }
        } catch (_: Exception) {
            Log.w(TAG, "Failed to parse socket payload as ${T::class.java.simpleName}")
            null
        }
    }

    private fun parseJsonObject(value: Any?): JSONObject? {
        return try {
            when (value) {
                null -> null
                is JSONObject -> value
                is String -> JSONObject(value)
                else -> JSONObject(gson.toJson(value))
            }
        } catch (_: Exception) {
            Log.w(TAG, "Failed to parse socket payload as JSONObject")
            null
        }
    }

    private fun emitCall(event: String, payload: JSONObject) {
        val envelope = JSONObject(payload.toString()).apply {
            put("eventId", "android_${System.currentTimeMillis()}_${UUID.randomUUID()}")
            put("timestamp", System.currentTimeMillis())
            put("attempt", optInt("attempt", 1))
            put("event", event)
            if (!has("type") && has("isVideo")) {
                put("type", if (optBoolean("isVideo")) "video" else "audio")
            }
        }
        Log.d(
            TAG,
            "[CALL_TRACE] android emit event=$event callId=${envelope.optString("callId")} eventId=${envelope.optString("eventId")} hasOfferSdp=${envelope.optJSONObject("offer")?.optString("sdp").isNullOrBlank().not()} hasAnswerSdp=${envelope.optJSONObject("answer")?.optString("sdp").isNullOrBlank().not()} hasIce=${envelope.optJSONObject("candidate")?.optString("candidate").isNullOrBlank().not()}"
        )
        if (cloudConnected) {
            emitCloud(event, envelope)
        } else {
            socket?.emit(event, envelope)
        }
    }

    private fun dispatchCloudEvent(event: String, payload: JSONObject) {
        when (event) {
            "receive_message" -> parse<ChatModels.Message>(payload)?.let { message ->
                onMessageReceived?.invoke(message)
                messageListeners.forEach { listener -> listener(message) }
            }
            "message_updated" -> parse<ChatModels.Message>(payload)?.let {
                onMessageUpdated?.invoke(it)
                messageUpdateListeners.forEach { listener -> listener(it) }
            }
            "chat_updated", "new_chat" -> parse<ChatModels.Chat>(payload)?.let { onChatUpdated?.invoke(it) }
            "user_presence", "user_updated", "presence_updated" -> onPresenceUpdated?.invoke(payload)
            "user_typing" -> {
                onTyping?.invoke(payload)
                typingListeners.forEach { listener -> listener(payload) }
            }
            else -> if (event.startsWith("call:")) onCallEvent?.invoke(event, payload)
        }
    }

    private fun emitCloud(event: String, payload: JSONObject) {
        cloudSocket?.send(
            JSONObject()
                .put("event", event)
                .put("payload", payload)
                .toString()
        )
    }

    private fun emitCloudPresence(event: String) {
        val user = currentUser ?: return
        emitCloud(
            event,
            JSONObject()
                .put("userId", user.id)
                .put("id", user.id)
                .put("name", user.name)
                .put("online", true)
                .put("platform", "android")
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

}
