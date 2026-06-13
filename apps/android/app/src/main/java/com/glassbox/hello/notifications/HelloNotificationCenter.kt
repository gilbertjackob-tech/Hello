package com.glassbox.hello.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.glassbox.hello.debug.AppLog as Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.glassbox.hello.MainActivity
import com.glassbox.hello.R
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.calls.CallSignal
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.ChatInboxPrefs
import com.glassbox.hello.network.SocketManager
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class InAppMessageNotification(
    val chatId: String,
    val senderName: String,
    val body: String,
    val channel: String = NotificationPrefs.CHANNEL_MESSAGES,
    val deepLink: String? = null
)

data class IncomingCallLaunch(
    val signal: CallSignal,
    val action: String? = null
)

private data class HelloPushPayload(
    val type: String,
    val channel: String,
    val priority: String,
    val senderName: String,
    val senderAvatar: String? = null,
    val groupName: String?,
    val previewText: String,
    val emoji: String?,
    val callId: String? = null,
    val chatId: String? = null,
    val callerId: String? = null,
    val calleeId: String? = null,
    val isVideo: Boolean = false,
    val targetId: String?,
    val targetType: String?,
    val deepLink: String?,
    val collapseKey: String
)

private data class NotificationVisualTheme(
    val isCute: Boolean,
    val rootBackgroundRes: Int,
    val callBackgroundRes: Int,
    val titleColor: Int,
    val bodyColor: Int,
    val metaColor: Int,
    val accentColor: Int
)

object HelloNotificationCenter {
    private const val PREFS_MODE = Context.MODE_PRIVATE
    private const val DUPLICATE_WINDOW_MS = 30_000L
    private const val KEY_LAST_SIGNATURE = "last_notification_signature"
    private const val KEY_LAST_SIGNATURE_AT = "last_notification_signature_at"
    private const val TAG = "HelloNotificationCenter"
    const val EXTRA_CALL_SIGNAL_JSON = "hello_call_signal_json"
    const val EXTRA_CALL_ACTION = "hello_call_action"
    const val CALL_ACTION_ACCEPT = "accept"
    const val CALL_ACTION_DECLINE = "decline"
    private const val CHANNEL_CALLS_RINGTONE = "incoming_calls_ringtone_v2"

    private val _bannerState = MutableStateFlow<InAppMessageNotification?>(null)
    val bannerState: StateFlow<InAppMessageNotification?> = _bannerState
    private val _incomingCallState = MutableStateFlow<IncomingCallLaunch?>(null)
    val incomingCallState: StateFlow<IncomingCallLaunch?> = _incomingCallState

    private var appContext: Context? = null
    private var currentUserId: String? = null
    private var initializedForUserId: String? = null
    private var appForeground = true
    private var openChatId: String? = null
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageListener: (ChatModels.Message) -> Unit = { message ->
        handleIncomingMessage(message)
    }
    private val notificationListener: (JSONObject) -> Unit = { payload ->
        handleRealtimeNotification(payload)
    }

    fun initialize(context: Context, userId: String) {
        appContext = context.applicationContext
        currentUserId = userId
        ensureChannels(context.applicationContext)
        if (initializedForUserId == userId) return
        initializedForUserId = userId
        val socketManager = SocketManager.getInstance()
        socketManager.removeMessageListener(messageListener)
        socketManager.addMessageListener(messageListener)
        socketManager.onNotification = notificationListener
    }

    fun resetForLogout(context: Context) {
        appContext = context.applicationContext
        currentUserId = null
        initializedForUserId = null
        openChatId = null
        _bannerState.value = null
        _incomingCallState.value = null
        val socketManager = SocketManager.getInstance()
        socketManager.removeMessageListener(messageListener)
        socketManager.onNotification = null
        NotificationManagerCompat.from(context.applicationContext).cancelAll()
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
    }

    fun setOpenChat(chatId: String?) {
        openChatId = chatId
    }

    fun clearBanner() {
        _bannerState.value = null
    }

    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val signalJson = intent.getStringExtra(EXTRA_CALL_SIGNAL_JSON) ?: return
        val signal = runCatching { notificationCallSignalFromJson(signalJson) }.getOrNull() ?: return
        _incomingCallState.value = IncomingCallLaunch(
            signal = signal,
            action = intent.getStringExtra(EXTRA_CALL_ACTION)?.takeIf { it.isNotBlank() }
        )
    }

    fun consumeIncomingCall(callId: String? = null) {
        val current = _incomingCallState.value ?: return
        if (callId.isNullOrBlank() || current.signal.callId == callId) {
            _incomingCallState.value = null
        }
    }

    fun consumeIncomingCallAction() {
        val current = _incomingCallState.value ?: return
        _incomingCallState.value = current.copy(action = null)
    }

    fun cancelCallNotifications(context: Context, callId: String?) {
        if (callId.isNullOrBlank()) return
        val manager = NotificationManagerCompat.from(context.applicationContext)
        manager.cancel("call_$callId".hashCode())
        manager.cancel("ongoing_call_$callId".hashCode())
    }

    fun handleRemoteMessage(context: Context, payload: JSONObject) {
        appContext = context.applicationContext
        ensureChannels(context.applicationContext)
        val activeUserId = activeUserId(context.applicationContext)
        val recipientId = payload.optString("recipientId")
            .ifBlank { payload.optString("toUserId") }
            .ifBlank { payload.optString("calleeId") }
            .ifBlank { null }
        if (activeUserId.isNullOrBlank()) {
            Log.d(TAG, "Dropping remote notification because no user is signed in")
            return
        }
        if (!recipientId.isNullOrBlank() && recipientId != activeUserId) {
            Log.d(TAG, "Dropping remote notification for inactive user")
            return
        }
        currentUserId = activeUserId
        handleRealtimeNotification(payload, forceSystemNotification = true)
    }

    private fun activeUserId(context: Context): String? =
        currentUserId
            ?: CloudSessionManager(context).let { session ->
                session.cachedUser()?.id?.takeIf { !session.token().isNullOrBlank() }
            }

    private fun handleIncomingMessage(message: ChatModels.Message) {
        val context = appContext ?: return
        val userId = currentUserId ?: return
        if (message.senderId == userId || message.chatId == openChatId) return
        if (ChatInboxPrefs.isMuted(context, message.chatId)) return
        val body = notificationBodyFor(message)
        val payload = HelloPushPayload(
            type = "message",
            channel = NotificationPrefs.CHANNEL_MESSAGES,
            priority = "high",
            senderName = normalizedLabel(message.senderName, "New message"),
            senderAvatar = message.senderAvatar,
            groupName = null,
            previewText = body,
            emoji = null,
            targetId = message.chatId,
            targetType = "chat",
            deepLink = "hello://chat/${message.chatId}",
            collapseKey = "chat_${message.chatId}"
        )
        renderNotification(context, payload)
    }

    private fun handleRealtimeNotification(raw: JSONObject, forceSystemNotification: Boolean = false) {
        val context = appContext ?: return
        val payload = HelloPushPayload(
            type = raw.optString("type", "system"),
            channel = normalizeChannel(raw.optString("channel")),
            priority = raw.optString("priority", "default"),
            senderName = normalizedLabel(raw.optString("senderName"), "Hello"),
            senderAvatar = raw.optString("senderAvatar")
                .ifBlank { raw.optString("callerAvatar") }
                .ifBlank { raw.optString("avatarUrl") }
                .ifBlank { null },
            groupName = raw.optString("groupName").ifBlank { null },
            previewText = raw.optString("previewText", ""),
            emoji = raw.optString("emoji").ifBlank { null },
            callId = raw.optString("callId").ifBlank { raw.optString("targetId").ifBlank { null } },
            chatId = raw.optString("chatId").ifBlank { null },
            callerId = raw.optString("callerId").ifBlank { raw.optString("senderId").ifBlank { null } },
            calleeId = raw.optString("calleeId").ifBlank { raw.optString("toUserId").ifBlank { null } },
            isVideo = raw.optBoolean("isVideo", raw.optString("type") == "call_incoming" && raw.optString("callType", raw.optString("type")) == "video"),
            targetId = raw.optString("targetId").ifBlank { null },
            targetType = raw.optString("targetType").ifBlank { null },
            deepLink = raw.optString("deepLink").ifBlank { null },
            collapseKey = raw.optString("collapseKey").ifBlank { raw.optString("type", "system") }
        )
        renderNotification(context, payload, forceSystemNotification)
    }

    private fun renderNotification(context: Context, payload: HelloPushPayload, forceSystemNotification: Boolean = false) {
        if (!categoryEnabled(context, payload.channel)) return
        if (isQuietHoursBlocked(context, payload.channel)) return
        val mutedChatId = payload.chatId?.takeIf { it.isNotBlank() }
            ?: payload.targetType?.takeIf { it == "chat" }?.let { payload.targetId?.takeIf { id -> id.isNotBlank() } }
        if (mutedChatId != null && ChatInboxPrefs.isMuted(context, mutedChatId)) return
        if (payload.targetType == "chat" && payload.targetId == openChatId) return
        if (isDuplicate(context, payload)) return

        val title = titleFor(payload)
        val body = bodyFor(payload)
        val isCall = payload.channel == NotificationPrefs.CHANNEL_CALLS || payload.type == "call_incoming"
        if (!forceSystemNotification && !isCall && appForeground && inAppAllowed(context)) {
            _bannerState.value = InAppMessageNotification(
                chatId = payload.targetId.orEmpty(),
                senderName = title,
                body = body,
                channel = payload.channel,
                deepLink = payload.deepLink
            )
            return
        }
        if ((!appForeground || isCall || forceSystemNotification) && canPostNotifications(context)) {
            notificationScope.launch {
                postSystemNotification(context.applicationContext, payload, title, body)
            }
        }
    }

    private fun isDuplicate(context: Context, payload: HelloPushPayload): Boolean {
        val signature = listOf(payload.type, payload.collapseKey, payload.previewText, payload.emoji.orEmpty()).joinToString("|")
        val prefs = context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
        val now = System.currentTimeMillis()
        val previous = prefs.getString(KEY_LAST_SIGNATURE, "")
        val previousAt = prefs.getLong(KEY_LAST_SIGNATURE_AT, 0L)
        val duplicate = previous == signature && now - previousAt < DUPLICATE_WINDOW_MS
        if (!duplicate) {
            prefs.edit()
                .putString(KEY_LAST_SIGNATURE, signature)
                .putLong(KEY_LAST_SIGNATURE_AT, now)
                .apply()
        }
        return duplicate
    }

    private suspend fun postSystemNotification(context: Context, payload: HelloPushPayload, title: String, body: String) {
        val isCall = payload.channel == NotificationPrefs.CHANNEL_CALLS || payload.type == "call_incoming"
        val avatarBitmap = loadNotificationBitmap(payload.senderAvatar)
        val visualTheme = notificationVisualTheme(context)
        val launchIntent = notificationIntent(context, payload, null)
        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.collapseKey.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val systemTitle = if (isCall) {
            if (payload.isVideo) "Incoming video call" else "Incoming voice call"
        } else {
            title
        }
        val systemBody = if (isCall) title else body
        val builder = NotificationCompat.Builder(context, notificationChannelFor(payload))
            .setSmallIcon(if (isCall) R.drawable.ic_stat_call else R.drawable.ic_stat_message)
            .setContentTitle(systemTitle)
            .setContentText(systemBody)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priorityFor(payload.priority))
            .setCategory(categoryFor(payload.channel))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00A884.toInt())
            .setWhen(System.currentTimeMillis())

        if (isCall) {
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                (payload.collapseKey + "_fs").hashCode(),
                notificationIntent(context, payload, null),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val declineIntent = PendingIntent.getActivity(
                context,
                (payload.collapseKey + "_decline").hashCode(),
                notificationIntent(context, payload, CALL_ACTION_DECLINE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val acceptIntent = PendingIntent.getActivity(
                context,
                (payload.collapseKey + "_accept").hashCode(),
                notificationIntent(context, payload, CALL_ACTION_ACCEPT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(45_000L)
                .setColorized(true)
                .setCustomContentView(buildCallRemoteViews(context, visualTheme, title, body, avatarBitmap, declineIntent, acceptIntent, false))
                .setCustomBigContentView(buildCallRemoteViews(context, visualTheme, title, body, avatarBitmap, declineIntent, acceptIntent, true))
                .setCustomHeadsUpContentView(buildCallRemoteViews(context, visualTheme, title, body, avatarBitmap, declineIntent, acceptIntent, true))
            if (!canUseFullScreenIntent(context)) {
                Log.w(TAG, "Full-screen intent permission denied; falling back to expanded heads-up call notification")
            }
        } else if (payload.channel == NotificationPrefs.CHANNEL_MESSAGES || payload.channel == NotificationPrefs.CHANNEL_MENTIONS) {
            avatarBitmap?.let { builder.setLargeIcon(it) }
            if (!payload.chatId.isNullOrBlank()) {
                builder.addAction(buildReplyAction(context, payload))
            }
            builder
                .setCustomContentView(buildMessageRemoteViews(context, visualTheme, title, body, avatarBitmap, payload.groupName, false))
                .setCustomBigContentView(buildMessageRemoteViews(context, visualTheme, title, body, avatarBitmap, payload.groupName, true))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else {
            avatarBitmap?.let { builder.setLargeIcon(it) }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(payload.collapseKey.hashCode(), builder.build())
        }
    }

    private fun notificationChannelFor(payload: HelloPushPayload): String {
        return if (payload.channel == NotificationPrefs.CHANNEL_CALLS || payload.type == "call_incoming") {
            CHANNEL_CALLS_RINGTONE
        } else {
            payload.channel
        }
    }

    private fun notificationIntent(context: Context, payload: HelloPushPayload, action: String?): Intent {
        val signal = callSignalFor(payload)
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_SIGNAL_JSON, signal?.let { notificationSignalJson(it) } ?: "")
            action?.let { putExtra(EXTRA_CALL_ACTION, it) }
            putExtra("deepLink", payload.deepLink)
            putExtra("targetId", payload.targetId)
            putExtra("targetType", payload.targetType)
        }
    }

    private fun callSignalFor(payload: HelloPushPayload): CallSignal? {
        val callId = payload.callId ?: payload.targetId ?: return null
        val chatId = payload.chatId ?: return null
        val callerId = payload.callerId ?: return null
        val calleeId = payload.calleeId ?: return null
        return CallSignal(
            callId = callId,
            roomId = null,
            chatId = chatId,
            fromUserId = callerId,
            toUserId = calleeId,
            callerId = callerId,
            calleeId = calleeId,
            callerName = normalizedLabel(payload.senderName, "Hello"),
            callerAvatar = payload.senderAvatar,
            calleeName = payload.groupName,
            calleeAvatar = null,
            type = if (payload.isVideo) "video" else "audio",
            isVideo = payload.isVideo,
            reason = null,
            timestamp = null,
            attempt = 1,
            event = payload.type
        )
    }

    private fun notificationSignalJson(signal: CallSignal): String {
        return JSONObject().apply {
            signal.eventId?.let { put("eventId", it) }
            put("callId", signal.callId)
            signal.roomId?.let { put("roomId", it) }
            put("chatId", signal.chatId)
            put("fromUserId", signal.fromUserId)
            put("toUserId", signal.toUserId)
            put("callerId", signal.callerId)
            put("calleeId", signal.calleeId)
            put("callerName", signal.callerName)
            signal.callerAvatar?.let { put("callerAvatar", it) }
            signal.calleeName?.let { put("calleeName", it) }
            signal.calleeAvatar?.let { put("calleeAvatar", it) }
            put("type", signal.type)
            put("isVideo", signal.isVideo)
            signal.offerSdp?.let { put("offer", JSONObject().put("type", "offer").put("sdp", it)) }
            signal.answerSdp?.let { put("answer", JSONObject().put("type", "answer").put("sdp", it)) }
            signal.reason?.let { put("reason", it) }
            signal.timestamp?.let { put("timestamp", it) }
            put("attempt", signal.attempt)
            signal.event?.let { put("event", it) }
        }.toString()
    }

    private fun notificationCallSignalFromJson(json: String): CallSignal? {
        val raw = JSONObject(json)
        val callId = raw.optString("callId").ifBlank { return null }
        val chatId = raw.optString("chatId").ifBlank { return null }
        val fromUserId = raw.optString("fromUserId").ifBlank { raw.optString("callerId") }
        val toUserId = raw.optString("toUserId").ifBlank { raw.optString("calleeId") }
        val callerId = raw.optString("callerId").ifBlank { fromUserId }
        val calleeId = raw.optString("calleeId").ifBlank { toUserId }
        if (fromUserId.isBlank() || toUserId.isBlank() || callerId.isBlank() || calleeId.isBlank()) return null
        val offer = raw.optJSONObject("offer")
        val answer = raw.optJSONObject("answer")
        val candidate = raw.optJSONObject("candidate")
        return CallSignal(
            eventId = raw.optString("eventId").ifBlank { null },
            callId = callId,
            roomId = raw.optString("roomId").ifBlank { null },
            chatId = chatId,
            fromUserId = fromUserId,
            toUserId = toUserId,
            callerId = callerId,
            calleeId = calleeId,
            callerName = normalizedLabel(raw.optString("callerName", raw.optString("senderName", "Hello call")), "Hello call"),
            callerAvatar = normalizedLabel(raw.optString("callerAvatar"), "").ifBlank { null },
            calleeName = normalizedLabel(raw.optString("calleeName"), "").ifBlank { null },
            calleeAvatar = normalizedLabel(raw.optString("calleeAvatar"), "").ifBlank { null },
            type = normalizedLabel(raw.optString("type", if (raw.optBoolean("isVideo", false)) "video" else "audio"), if (raw.optBoolean("isVideo", false)) "video" else "audio"),
            isVideo = raw.optBoolean("isVideo", raw.optString("type") == "video"),
            offerSdp = offer?.optString("sdp")?.let { normalizedLabel(it, "") }?.ifBlank { null },
            answerSdp = answer?.optString("sdp")?.let { normalizedLabel(it, "") }?.ifBlank { null },
            candidate = candidate?.optString("candidate")?.let { normalizedLabel(it, "") }?.ifBlank { null },
            sdpMid = candidate?.optString("sdpMid")?.let { normalizedLabel(it, "") }?.ifBlank { null },
            sdpMLineIndex = candidate?.takeIf { it.has("sdpMLineIndex") }?.optInt("sdpMLineIndex"),
            reason = normalizedLabel(raw.optString("reason"), "").ifBlank { null },
            timestamp = if (raw.has("timestamp") && !raw.isNull("timestamp")) raw.optLong("timestamp") else null,
            attempt = raw.optInt("attempt", 1),
            event = normalizedLabel(raw.optString("event"), "").ifBlank { null }
        )
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat.from(context).canUseFullScreenIntent()
        } else {
            true
        }
    }

    private fun normalizedLabel(value: String?, fallback: String): String {
        val normalized = value?.trim().orEmpty()
        return when {
            normalized.isBlank() -> fallback
            normalized.equals("null", ignoreCase = true) -> fallback
            normalized.equals("undefined", ignoreCase = true) -> fallback
            else -> normalized
        }
    }

    private fun titleFor(payload: HelloPushPayload): String = when (payload.type) {
        "call_incoming" -> payload.senderName.ifBlank { "Incoming call" }
        "call_missed" -> "Missed call from ${payload.senderName}"
        "mention" -> "${payload.senderName} mentioned you"
        "reply" -> "${payload.senderName} replied to you"
        "status_post" -> "${payload.senderName} just posted a moment"
        "status_reaction" -> "${payload.senderName} reacted ${payload.emoji.orEmpty()}"
        "status_reply" -> "${payload.senderName} replied to your status"
        "archive_complete" -> "Today's moments are safely saved"
        "re_engagement" -> "Share a moment"
        else -> payload.groupName ?: payload.senderName.ifBlank { "Hello" }
    }

    private fun bodyFor(payload: HelloPushPayload): String = when (payload.type) {
        "call_incoming" -> "Incoming ${if (payload.isVideo) "video" else "voice"} call"
        "status_post" -> "Open Today Pulse"
        "archive_complete" -> "Saved to your PC"
        "re_engagement" -> payload.previewText.ifBlank { "The family would love to know what's going on today" }
        else -> payload.previewText.ifBlank { "Open Hello" }
    }

    private fun notificationBodyFor(message: ChatModels.Message): String {
        val trimmed = message.text.trim()
        if (trimmed.isNotEmpty()) return trimmed.take(50)
        return when {
            message.attachmentType?.startsWith("audio") == true -> "Sent you a voice message"
            message.attachmentType?.startsWith("image") == true -> "Sent a photo"
            message.attachmentType?.startsWith("video") == true -> "Sent a video"
            !message.attachmentName.isNullOrBlank() -> "Sent ${message.attachmentName}"
            message.location != null -> "Shared a location"
            else -> "New message"
        }
    }

    private fun notificationVisualTheme(context: Context): NotificationVisualTheme {
        val theme = context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
            .getString("theme", "cute")
            .orEmpty()
            .trim()
            .lowercase()
        val isCute = theme.contains("cute") || theme.contains("pink")
        return if (isCute) {
            NotificationVisualTheme(
                isCute = true,
                rootBackgroundRes = R.drawable.notification_bg_cute,
                callBackgroundRes = R.drawable.notification_bg_call_cute,
                titleColor = 0xFF8D2352.toInt(),
                bodyColor = 0xFFB3567C.toInt(),
                metaColor = 0xFFC16B90.toInt(),
                accentColor = 0xFFE83F86.toInt()
            )
        } else {
            NotificationVisualTheme(
                isCute = false,
                rootBackgroundRes = R.drawable.notification_bg_glass,
                callBackgroundRes = R.drawable.notification_bg_call_glass,
                titleColor = 0xFFF4F7FB.toInt(),
                bodyColor = 0xFFD1D9E6.toInt(),
                metaColor = 0xFF8FA1BA.toInt(),
                accentColor = 0xFF63E6BE.toInt()
            )
        }
    }

    private fun buildMessageRemoteViews(
        context: Context,
        theme: NotificationVisualTheme,
        title: String,
        body: String,
        avatarBitmap: Bitmap?,
        groupName: String?,
        expanded: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_message).apply {
            setInt(R.id.notification_message_root, "setBackgroundResource", theme.rootBackgroundRes)
            setTextViewText(R.id.notification_message_title, title)
            setTextViewText(R.id.notification_message_body, body)
            setTextViewText(R.id.notification_message_time, notificationClock())
            setTextViewText(R.id.notification_message_tag, groupName?.takeIf { it.isNotBlank() } ?: "Hello message")
            setTextColor(R.id.notification_message_title, theme.titleColor)
            setTextColor(R.id.notification_message_body, theme.bodyColor)
            setTextColor(R.id.notification_message_time, theme.metaColor)
            setTextColor(R.id.notification_message_tag, theme.accentColor)
            setViewVisibility(R.id.notification_message_body, if (expanded || body.isNotBlank()) View.VISIBLE else View.GONE)
            if (avatarBitmap != null) {
                setImageViewBitmap(R.id.notification_message_avatar, avatarBitmap)
            } else {
                setImageViewResource(R.id.notification_message_avatar, R.drawable.ic_stat_message)
            }
        }
    }

    private fun buildReplyAction(context: Context, payload: HelloPushPayload): NotificationCompat.Action {
        val replyIntent = PendingIntent.getBroadcast(
            context,
            (payload.collapseKey + "_reply").hashCode(),
            Intent(context, ChatNotificationReplyReceiver::class.java).apply {
                action = ChatNotificationReplyReceiver.ACTION_REPLY_MESSAGE
                putExtra(ChatNotificationReplyReceiver.EXTRA_CHAT_ID, payload.chatId.orEmpty())
                putExtra("senderName", payload.senderName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remoteInput = RemoteInput.Builder(ChatNotificationReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_message,
            "Reply",
            replyIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun buildCallRemoteViews(
        context: Context,
        theme: NotificationVisualTheme,
        title: String,
        body: String,
        avatarBitmap: Bitmap?,
        declineIntent: PendingIntent,
        acceptIntent: PendingIntent,
        expanded: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_call).apply {
            setInt(R.id.notification_call_root, "setBackgroundResource", theme.callBackgroundRes)
            setTextViewText(R.id.notification_call_title, title)
            setTextViewText(R.id.notification_call_body, if (theme.isCute) "Tap to answer" else body)
            setTextViewText(R.id.notification_call_time, notificationClock())
            setTextColor(R.id.notification_call_title, theme.titleColor)
            setTextColor(R.id.notification_call_body, theme.bodyColor)
            setTextColor(R.id.notification_call_time, theme.metaColor)
            if (avatarBitmap != null) {
                setImageViewBitmap(R.id.notification_call_avatar, avatarBitmap)
            } else {
                setImageViewResource(R.id.notification_call_avatar, R.drawable.ic_stat_call)
            }
            setOnClickPendingIntent(R.id.notification_call_decline, declineIntent)
            setOnClickPendingIntent(R.id.notification_call_accept, acceptIntent)
            setViewVisibility(R.id.notification_call_actions, if (expanded) View.VISIBLE else View.GONE)
        }
    }

    private fun notificationClock(): String {
        val now = LocalTime.now()
        val hour = when {
            now.hour == 0 -> 12
            now.hour > 12 -> now.hour - 12
            else -> now.hour
        }
        val suffix = if (now.hour >= 12) "pm" else "am"
        return "%d:%02d %s".format(hour, now.minute, suffix)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            incomingCallChannel(),
            channel(NotificationPrefs.CHANNEL_CALLS, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH, true, true),
            channel(NotificationPrefs.CHANNEL_ONGOING_CALLS, "Ongoing Calls", NotificationManager.IMPORTANCE_LOW, false, false),
            channel(NotificationPrefs.CHANNEL_MISSED_CALLS, "Missed Calls", NotificationManager.IMPORTANCE_HIGH, true, false),
            channel(NotificationPrefs.CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH, true, false),
            channel(NotificationPrefs.CHANNEL_MENTIONS, "Mentions & Replies", NotificationManager.IMPORTANCE_HIGH, true, false),
            channel(NotificationPrefs.CHANNEL_STATUS_POSTS, "New Moments", NotificationManager.IMPORTANCE_DEFAULT, true, false),
            channel(NotificationPrefs.CHANNEL_STATUS_ACTIVITY, "Reactions & Views", NotificationManager.IMPORTANCE_DEFAULT, false, false),
            channel(NotificationPrefs.CHANNEL_SYSTEM, "App Updates", NotificationManager.IMPORTANCE_LOW, false, false),
            channel(NotificationPrefs.CHANNEL_RE_ENGAGEMENT, "Family Nudges", NotificationManager.IMPORTANCE_LOW, false, false)
        )
        manager.createNotificationChannels(channels)
    }

    private fun incomingCallChannel(): NotificationChannel {
        return NotificationChannel(CHANNEL_CALLS_RINGTONE, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen incoming call alerts"
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), attrs)
        }
    }

    private fun channel(id: String, name: String, importance: Int, sound: Boolean, vibration: Boolean): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            description = name
            enableVibration(vibration)
            if (!sound) setSound(null, null) else {
                val attrs = AudioAttributes.Builder()
                    .setUsage(if (id == NotificationPrefs.CHANNEL_CALLS) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val soundUri = if (id == NotificationPrefs.CHANNEL_CALLS) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                setSound(soundUri, attrs)
            }
        }
    }

    private suspend fun loadNotificationBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        val resolved = resolveAvatarUrl(url) ?: return@withContext null
        runCatching {
            val connection = (URL(resolved).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                instanceFollowRedirects = true
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    private fun resolveAvatarUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> null
        }
    }

    private fun normalizeChannel(channel: String): String = when (channel) {
            NotificationPrefs.CHANNEL_CALLS,
            NotificationPrefs.CHANNEL_ONGOING_CALLS,
            NotificationPrefs.CHANNEL_MISSED_CALLS,
        NotificationPrefs.CHANNEL_MESSAGES,
        NotificationPrefs.CHANNEL_MENTIONS,
        NotificationPrefs.CHANNEL_STATUS_POSTS,
        NotificationPrefs.CHANNEL_STATUS_ACTIVITY,
        NotificationPrefs.CHANNEL_SYSTEM,
        NotificationPrefs.CHANNEL_RE_ENGAGEMENT -> channel
        else -> NotificationPrefs.CHANNEL_SYSTEM
    }

    private fun categoryEnabled(context: Context, channel: String): Boolean {
        val prefs = context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
        val key = when (channel) {
            NotificationPrefs.CHANNEL_CALLS -> NotificationPrefs.KEY_CALL_NOTIFICATIONS
            NotificationPrefs.CHANNEL_ONGOING_CALLS -> NotificationPrefs.KEY_CALL_NOTIFICATIONS
            NotificationPrefs.CHANNEL_MISSED_CALLS -> NotificationPrefs.KEY_MISSED_CALL_NOTIFICATIONS
            NotificationPrefs.CHANNEL_MESSAGES -> NotificationPrefs.KEY_MESSAGE_NOTIFICATIONS
            NotificationPrefs.CHANNEL_MENTIONS -> NotificationPrefs.KEY_MENTION_NOTIFICATIONS
            NotificationPrefs.CHANNEL_STATUS_POSTS -> NotificationPrefs.KEY_STATUS_POST_NOTIFICATIONS
            NotificationPrefs.CHANNEL_STATUS_ACTIVITY -> NotificationPrefs.KEY_STATUS_ACTIVITY_NOTIFICATIONS
            NotificationPrefs.CHANNEL_RE_ENGAGEMENT -> NotificationPrefs.KEY_RE_ENGAGEMENT_NOTIFICATIONS
            else -> NotificationPrefs.KEY_SYSTEM_NOTIFICATIONS
        }
        return prefs.getBoolean(key, true)
    }

    private fun inAppAllowed(context: Context): Boolean =
        context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
            .getBoolean(NotificationPrefs.KEY_IN_APP_NOTIFICATIONS, true)

    private fun isQuietHoursBlocked(context: Context, channel: String): Boolean {
        if (channel == NotificationPrefs.CHANNEL_CALLS) return false
        val prefs = context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
        if (!prefs.getBoolean(NotificationPrefs.KEY_QUIET_HOURS_ENABLED, false)) return false
        if (channel == NotificationPrefs.CHANNEL_MENTIONS && prefs.getBoolean(NotificationPrefs.KEY_ALLOW_MENTIONS_DND, true)) return false
        val start = prefs.getInt(NotificationPrefs.KEY_QUIET_HOURS_START_MINUTES, 22 * 60)
        val end = prefs.getInt(NotificationPrefs.KEY_QUIET_HOURS_END_MINUTES, 8 * 60)
        val now = LocalTime.now().hour * 60 + LocalTime.now().minute
        return if (start <= end) now in start until end else now >= start || now < end
    }

    private fun priorityFor(priority: String): Int = when (priority) {
        "urgent" -> NotificationCompat.PRIORITY_MAX
        "high" -> NotificationCompat.PRIORITY_HIGH
        "low" -> NotificationCompat.PRIORITY_LOW
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun categoryFor(channel: String): String = when (channel) {
        NotificationPrefs.CHANNEL_CALLS -> NotificationCompat.CATEGORY_CALL
        NotificationPrefs.CHANNEL_ONGOING_CALLS -> NotificationCompat.CATEGORY_CALL
        NotificationPrefs.CHANNEL_MESSAGES,
        NotificationPrefs.CHANNEL_MENTIONS -> NotificationCompat.CATEGORY_MESSAGE
        NotificationPrefs.CHANNEL_SYSTEM -> NotificationCompat.CATEGORY_STATUS
        else -> NotificationCompat.CATEGORY_SOCIAL
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
