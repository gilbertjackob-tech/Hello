package com.glassbox.hello.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class HelloFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        FcmPushRegistrar.registerToken(applicationContext, token, "onNewToken")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) {
            Log.d(TAG, "Ignoring FCM message without data payload")
            return
        }
        val payload = JSONObject()
        data.forEach { (key, value) -> payload.put(key, value) }
        payload.put("source", "fcm")
        Log.d(TAG, "FCM data received type=${payload.optString("type")} channel=${payload.optString("channel")}")
        HelloNotificationCenter.handleRemoteMessage(applicationContext, payload)
    }

    companion object {
        private const val TAG = "HelloFCM"
    }
}
