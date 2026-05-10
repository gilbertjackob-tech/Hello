package com.glassbox.hello.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Closeable
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/**
 * OkHttp WebSocket client for real-time browser sync events.
 */
class WebSocketClient(
    context: Context,
    private val scope: CoroutineScope,
    private val config: WebSocketClientConfig = WebSocketClientConfig(),
    private val tokenProvider: AuthTokenProvider = SharedPreferencesAuthTokenStore(context.applicationContext)
) : Closeable {
    private val applicationContext = context.applicationContext
    private val preferences: SharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val queueLock = Any()
    private val socketLock = Any()
    private val eventBus = MutableSharedFlow<SyncEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val _connectionState = MutableStateFlow(WebSocketConnectionState.Disconnected)
    private val pendingMessages = ArrayDeque<QueuedWebSocketMessage>()
    private val client = OkHttpClient.Builder()
        .readTimeout(0L, TimeUnit.MILLISECONDS)
        .pingInterval(config.protocolPingIntervalMillis, TimeUnit.MILLISECONDS)
        .certificatePinner(config.certificatePinning.toCertificatePinner())
        .build()

    private var webSocket: WebSocket? = null
    private var currentUserId: String? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempt = 0
    private var closedByClient = false

    init {
        loadQueuedMessages()
    }

    /**
     * Observes parsed WebSocket sync events.
     */
    fun observeEvents(): Flow<SyncEvent> = eventBus.asSharedFlow()

    /**
     * Observes the current socket connection state.
     */
    fun observeConnectionState(): StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    /**
     * Opens a WebSocket connection for a user.
     */
    fun connect(userId: String) {
        require(userId.isNotBlank()) { "User id cannot be blank." }
        synchronized(socketLock) {
            currentUserId = userId
            closedByClient = false
            reconnectJob?.cancel()
            webSocket?.cancel()
            webSocket = null
            _connectionState.value = WebSocketConnectionState.Connecting
        }

        val request = buildRequest(userId)
        webSocket = client.newWebSocket(request, createListener(userId))
    }

    /**
     * Sends a raw JSON message or queues it while the socket is offline.
     */
    fun sendMessage(message: String): Boolean {
        require(message.isNotBlank()) { "WebSocket message cannot be blank." }
        val sent = synchronized(socketLock) {
            val socket = webSocket
            if (_connectionState.value == WebSocketConnectionState.Connected && socket != null) {
                socket.send(message)
            } else {
                false
            }
        }

        if (!sent) {
            enqueueMessage(message)
        }
        return sent
    }

    /**
     * Sends a structured sync message or queues it while the socket is offline.
     */
    fun sendMessage(message: SyncSocketMessage): Boolean {
        return sendMessage(gson.toJson(message))
    }

    /**
     * Closes the socket and cancels reconnect and heartbeat work.
     */
    override fun close() {
        val socket = synchronized(socketLock) {
            closedByClient = true
            reconnectJob?.cancel()
            reconnectJob = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            _connectionState.value = WebSocketConnectionState.Disconnected
            val existing = webSocket
            webSocket = null
            existing
        }
        socket?.close(NORMAL_CLOSURE_STATUS, "Client closed")
        persistQueuedMessages()
    }

    private fun buildRequest(userId: String): Request {
        val builder = Request.Builder()
            .url(appendQueryParameter(config.normalizedWebSocketUrl(), "userId", userId))
            .header("Accept", "application/json")
            .header("User-Agent", "GlassBox-Hello-Android-WebSocket")

        val token = tokenProvider.currentToken()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return builder.build()
    }

    private fun createListener(userId: String): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(socketLock) {
                    this@WebSocketClient.webSocket = webSocket
                    reconnectAttempt = 0
                    _connectionState.value = WebSocketConnectionState.Connected
                }
                emitEvent(SyncEvent.Connected)
                startHeartbeat()
                flushQueuedMessages()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                emitEvent(parseSyncEvent(text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                emitEvent(parseSyncEvent(bytes.utf8()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleClosed(userId, code, reason, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleClosed(userId, response?.code, t.message ?: "WebSocket failure", t)
            }
        }
    }

    private fun handleClosed(userId: String, code: Int?, reason: String, error: Throwable?) {
        synchronized(socketLock) {
            if (webSocket != null) {
                webSocket = null
            }
            heartbeatJob?.cancel()
            heartbeatJob = null
            _connectionState.value = WebSocketConnectionState.Disconnected
        }

        if (error == null) {
            emitEvent(SyncEvent.Disconnected(code, reason))
        } else {
            Log.w(TAG, "WebSocket disconnected with failure.", error)
            emitEvent(SyncEvent.Error(reason, error.javaClass.simpleName))
        }

        if (!closedByClient) {
            scheduleReconnect(userId)
        }
    }

    private fun scheduleReconnect(userId: String) {
        reconnectJob?.cancel()
        val attempt = reconnectAttempt
        val delayMillis = config.reconnectDelayMillis(attempt)
        reconnectAttempt += 1
        reconnectJob = scope.launch {
            delay(delayMillis)
            if (!closedByClient && currentUserId == userId) {
                connect(userId)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (_connectionState.value == WebSocketConnectionState.Connected) {
                delay(config.heartbeatIntervalMillis)
                val heartbeat = SyncSocketMessage(
                    type = HEARTBEAT_TYPE,
                    payload = mapOf("timestamp" to System.currentTimeMillis())
                )
                synchronized(socketLock) {
                    webSocket?.send(gson.toJson(heartbeat))
                }
            }
        }
    }

    private fun enqueueMessage(message: String) {
        synchronized(queueLock) {
            while (pendingMessages.size >= config.maxQueuedMessages) {
                pendingMessages.removeFirstOrNull()
            }
            pendingMessages.addLast(
                QueuedWebSocketMessage(
                    id = UUID.randomUUID().toString(),
                    payload = message,
                    queuedAt = System.currentTimeMillis()
                )
            )
            persistQueuedMessages()
        }
        emitEvent(SyncEvent.Queued(pendingMessages.size))
    }

    private fun flushQueuedMessages() {
        synchronized(queueLock) {
            val socket = webSocket ?: return
            while (pendingMessages.isNotEmpty()) {
                val queuedMessage = pendingMessages.removeFirst()
                if (!socket.send(queuedMessage.payload)) {
                    pendingMessages.addFirst(queuedMessage.copy(attempts = queuedMessage.attempts + 1))
                    break
                }
            }
            persistQueuedMessages()
        }
    }

    private fun loadQueuedMessages() {
        val rawQueue = preferences.getString(KEY_PENDING_MESSAGES, null) ?: return
        try {
            val type = object : TypeToken<List<QueuedWebSocketMessage>>() {}.type
            val messages: List<QueuedWebSocketMessage> = gson.fromJson(rawQueue, type) ?: emptyList()
            synchronized(queueLock) {
                pendingMessages.clear()
                pendingMessages.addAll(messages.takeLast(config.maxQueuedMessages))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to load queued WebSocket messages.", error)
            preferences.edit().remove(KEY_PENDING_MESSAGES).apply()
        }
    }

    private fun persistQueuedMessages() {
        val snapshot = synchronized(queueLock) {
            pendingMessages.toList()
        }
        preferences.edit().putString(KEY_PENDING_MESSAGES, gson.toJson(snapshot)).apply()
    }

    private fun parseSyncEvent(text: String): SyncEvent {
        return try {
            val json = JSONObject(text)
            val type = json.optString("type", MESSAGE_TYPE)
            when (type) {
                HEARTBEAT_ACK_TYPE, "pong" -> SyncEvent.HeartbeatAck(json.optLong("timestamp", System.currentTimeMillis()))
                else -> SyncEvent.Message(
                    type = type,
                    payload = text,
                    messageId = json.optString("messageId").takeIf { id -> id.isNotBlank() },
                    receivedAt = System.currentTimeMillis()
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse WebSocket message.", error)
            SyncEvent.Message(
                type = MESSAGE_TYPE,
                payload = text,
                messageId = null,
                receivedAt = System.currentTimeMillis()
            )
        }
    }

    private fun emitEvent(event: SyncEvent) {
        scope.launch {
            eventBus.emit(event)
        }
    }

    private fun appendQueryParameter(url: String, name: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator${encode(name)}=${encode(value)}"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    companion object {
        private const val TAG: String = "WebSocketClient"
        private const val PREFERENCES_NAME: String = "glassbox_browser_websocket"
        private const val KEY_PENDING_MESSAGES: String = "pending_messages"
        private const val EVENT_BUFFER_CAPACITY: Int = 64
        private const val NORMAL_CLOSURE_STATUS: Int = 1000
        private const val HEARTBEAT_TYPE: String = "heartbeat"
        private const val HEARTBEAT_ACK_TYPE: String = "heartbeat_ack"
        private const val MESSAGE_TYPE: String = "message"
    }
}

/**
 * WebSocket client configuration.
 */
data class WebSocketClientConfig(
    val endpointUrl: String = "${AppConfig.HELLO_API_BASE}/sync/ws",
    val maxQueuedMessages: Int = 100,
    val initialReconnectDelayMillis: Long = 1_000L,
    val maxReconnectDelayMillis: Long = 30_000L,
    val heartbeatIntervalMillis: Long = 25_000L,
    val protocolPingIntervalMillis: Long = 30_000L,
    val certificatePinning: CertificatePinningConfig = CertificatePinningConfig()
) {
    /**
     * Returns a WebSocket URL derived from the configured HTTP endpoint.
     */
    fun normalizedWebSocketUrl(): String {
        val cleanUrl = endpointUrl.trim().trimEnd('/')
        return when {
            cleanUrl.startsWith("wss://") || cleanUrl.startsWith("ws://") -> cleanUrl
            cleanUrl.startsWith("https://") -> cleanUrl.replaceFirst("https://", "wss://")
            cleanUrl.startsWith("http://") -> cleanUrl.replaceFirst("http://", "ws://")
            else -> error("Unsupported WebSocket endpoint URL: $endpointUrl")
        }
    }

    /**
     * Returns the reconnect delay for an attempt.
     */
    fun reconnectDelayMillis(attempt: Int): Long {
        val multiplier = 1L shl attempt.coerceAtMost(10)
        return (initialReconnectDelayMillis * multiplier).coerceAtMost(maxReconnectDelayMillis)
    }
}

/**
 * Structured outbound WebSocket message.
 */
data class SyncSocketMessage(
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val messageId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * WebSocket connection state.
 */
enum class WebSocketConnectionState {
    Connecting,
    Connected,
    Disconnected
}

/**
 * Parsed inbound WebSocket events.
 */
sealed class SyncEvent {
    /**
     * Socket opened successfully.
     */
    data object Connected : SyncEvent()

    /**
     * Socket closed without a transport exception.
     */
    data class Disconnected(
        val code: Int?,
        val reason: String
    ) : SyncEvent()

    /**
     * Application-level message from the sync server.
     */
    data class Message(
        val type: String,
        val payload: String,
        val messageId: String?,
        val receivedAt: Long
    ) : SyncEvent()

    /**
     * Application-level heartbeat acknowledgement.
     */
    data class HeartbeatAck(
        val timestamp: Long
    ) : SyncEvent()

    /**
     * Message queued because the socket is offline.
     */
    data class Queued(
        val queuedCount: Int
    ) : SyncEvent()

    /**
     * Transport or parsing error.
     */
    data class Error(
        val message: String,
        val type: String? = null
    ) : SyncEvent()
}

private data class QueuedWebSocketMessage(
    val id: String,
    val payload: String,
    val queuedAt: Long,
    val attempts: Int = 0
)
