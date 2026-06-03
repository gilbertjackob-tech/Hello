package com.glassbox.hello.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glassbox.hello.MainActivity
import com.glassbox.hello.R
import com.glassbox.hello.calls.CallSignal
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.network.SocketManager
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _bannerState = MutableStateFlow<InAppMessageNotification?>(null)
    val bannerState: StateFlow<InAppMessageNotification?> = _bannerState
    private val _incomingCallState = MutableStateFlow<IncomingCallLaunch?>(null)
    val incomingCallState: StateFlow<IncomingCallLaunch?> = _incomingCallState

    private var appContext: Context? = null
    private var currentUserId: String? = null
    private var initializedForUserId: String? = null
    private var appForeground = true
    private var openChatId: String? = null

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
        handleRealtimeNotification(payload, forceSystemNotification = true)
    }

    private fun handleIncomingMessage(message: ChatModels.Message) {
        val context = appContext ?: return
        val userId = currentUserId ?: return
        if (message.senderId == userId || message.chatId == openChatId) return
        val body = notificationBodyFor(message)
        val payload = HelloPushPayload(
            type = "message",
            channel = NotificationPrefs.CHANNEL_MESSAGES,
            priority = "high",
            senderName = message.senderName.ifBlank { "New message" },
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
            senderName = raw.optString("senderName", "Hello"),
            senderAvatar = raw.optString("senderAvatar").ifBlank { null },
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
            postSystemNotification(context, payload, title, body)
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

    private fun postSystemNotification(context: Context, payload: HelloPushPayload, title: String, body: String) {
        val launchIntent = notificationIntent(context, payload, null)
        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.collapseKey.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, payload.channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priorityFor(payload.priority))
            .setCategory(categoryFor(payload.channel))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (payload.channel == NotificationPrefs.CHANNEL_CALLS || payload.type == "call_incoming") {
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                (payload.collapseKey + "_fs").hashCode(),
                notificationIntent(context, payload, null),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(45_000L)
                .addAction(
                    0,
                    "Decline",
                    PendingIntent.getActivity(
                        context,
                        (payload.collapseKey + "_decline").hashCode(),
                        notificationIntent(context, payload, CALL_ACTION_DECLINE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .addAction(
                    0,
                    "Accept",
                    PendingIntent.getActivity(
                        context,
                        (payload.collapseKey + "_accept").hashCode(),
                        notificationIntent(context, payload, CALL_ACTION_ACCEPT),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            if (!canUseFullScreenIntent(context)) {
                Log.w(TAG, "Full-screen intent permission denied; falling back to expanded heads-up call notification")
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(payload.collapseKey.hashCode(), builder.build())
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
            callerName = payload.senderName.ifBlank { "Hello" },
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
            callerName = raw.optString("callerName", raw.optString("senderName", "Hello call")),
            callerAvatar = raw.optString("callerAvatar").ifBlank { null },
            calleeName = raw.optString("calleeName").ifBlank { null },
            calleeAvatar = raw.optString("calleeAvatar").ifBlank { null },
            type = raw.optString("type", if (raw.optBoolean("isVideo", false)) "video" else "audio"),
            isVideo = raw.optBoolean("isVideo", raw.optString("type") == "video"),
            offerSdp = offer?.optString("sdp")?.ifBlank { null },
            answerSdp = answer?.optString("sdp")?.ifBlank { null },
            candidate = candidate?.optString("candidate")?.ifBlank { null },
            sdpMid = candidate?.optString("sdpMid")?.ifBlank { null },
            sdpMLineIndex = candidate?.takeIf { it.has("sdpMLineIndex") }?.optInt("sdpMLineIndex"),
            reason = raw.optString("reason").ifBlank { null },
            timestamp = if (raw.has("timestamp") && !raw.isNull("timestamp")) raw.optLong("timestamp") else null,
            attempt = raw.optInt("attempt", 1),
            event = raw.optString("event").ifBlank { null }
        )
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat.from(context).canUseFullScreenIntent()
        } else {
            true
        }
    }

    private fun titleFor(payload: HelloPushPayload): String = when (payload.type) {
        "call_incoming" -> "${payload.senderName} is calling..."
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
        "call_incoming" -> "Tap to answer"
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

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
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

    private fun channel(id: String, name: String, importance: Int, sound: Boolean, vibration: Boolean): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            description = name
            enableVibration(vibration)
            if (!sound) setSound(null, null) else {
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
            }
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
