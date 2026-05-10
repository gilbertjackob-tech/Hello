package com.glassbox.hello.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glassbox.hello.MainActivity
import com.glassbox.hello.R
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InAppMessageNotification(
    val chatId: String,
    val senderName: String,
    val body: String
)

object HelloNotificationCenter {
    private const val CHANNEL_MESSAGES = "hello_messages"
    private const val PREFS_MODE = Context.MODE_PRIVATE

    private val _bannerState = MutableStateFlow<InAppMessageNotification?>(null)
    val bannerState: StateFlow<InAppMessageNotification?> = _bannerState

    private var appContext: Context? = null
    private var currentUserId: String? = null
    private var initializedForUserId: String? = null
    private var appForeground = true
    private var openChatId: String? = null

    private val messageListener: (ChatModels.Message) -> Unit = { message ->
        handleIncomingMessage(message)
    }

    fun initialize(context: Context, userId: String) {
        appContext = context.applicationContext
        currentUserId = userId
        ensureChannels(context.applicationContext)
        if (initializedForUserId == userId) {
            return
        }
        initializedForUserId = userId
        val socketManager = SocketManager.getInstance()
        socketManager.removeMessageListener(messageListener)
        socketManager.addMessageListener(messageListener)
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

    private fun handleIncomingMessage(message: ChatModels.Message) {
        val context = appContext ?: return
        val userId = currentUserId ?: return
        if (message.senderId == userId) return
        if (message.chatId == openChatId) return

        val prefs = context.getSharedPreferences(NotificationPrefs.PREFS_NAME, PREFS_MODE)
        val allowSystem = prefs.getBoolean(NotificationPrefs.KEY_MESSAGE_NOTIFICATIONS, true)
        val allowInApp = prefs.getBoolean(NotificationPrefs.KEY_IN_APP_NOTIFICATIONS, true)
        val body = notificationBodyFor(message)

        if (appForeground && allowInApp) {
            _bannerState.value = InAppMessageNotification(
                chatId = message.chatId,
                senderName = message.senderName.ifBlank { "New message" },
                body = body
            )
        }
        if (!appForeground && allowSystem && canPostNotifications(context)) {
            postSystemNotification(context, message, body)
        }
    }

    private fun postSystemNotification(context: Context, message: ChatModels.Message, body: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", message.chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            message.chatId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.senderName.ifBlank { "New message" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(context).notify(message.chatId.hashCode(), notification)
    }

    private fun notificationBodyFor(message: ChatModels.Message): String {
        val trimmed = message.text.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        return when {
            !message.attachmentName.isNullOrBlank() -> "Sent ${message.attachmentName}"
            !message.attachmentType.isNullOrBlank() -> "Sent ${message.attachmentType}"
            message.location != null -> "Shared a location"
            else -> "New message"
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Hello messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New message alerts for Hello"
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
