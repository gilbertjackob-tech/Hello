package com.glassbox.hello.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glassbox.hello.debug.AppLog as Log
import androidx.core.app.RemoteInput
import com.glassbox.hello.auth.CloudSessionManager
import com.glassbox.hello.network.HelloApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatNotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val results = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString().orEmpty().trim()
        if (chatId.isBlank() || replyText.isBlank()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = CloudSessionManager(context.applicationContext).cachedUser()
                if (session == null) {
                    Log.w(TAG, "Reply ignored because no cloud session was available")
                    return@launch
                }
                HelloApiClient()
                    .sendMessage(
                        chatId = chatId,
                        text = replyText,
                        senderId = session.id,
                        senderName = session.name,
                        senderAvatar = session.avatar
                    )
                    .onFailure { error -> Log.w(TAG, "Reply send failed chatId=$chatId error=${error.message}") }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY_MESSAGE = "com.glassbox.hello.notifications.ACTION_REPLY_MESSAGE"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val TAG = "HelloChatReply"
    }
}
