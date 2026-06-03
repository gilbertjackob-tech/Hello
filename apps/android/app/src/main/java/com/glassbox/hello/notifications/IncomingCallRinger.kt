package com.glassbox.hello.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log

object IncomingCallRinger {
    private const val TAG = "IncomingCallRinger"
    private var player: MediaPlayer? = null
    private var activeCallId: String? = null

    @Synchronized
    fun start(context: Context?, callId: String?) {
        if (context == null || callId.isNullOrBlank()) return
        if (activeCallId == callId && player?.isPlaying == true) return
        stop()
        val appContext = context.applicationContext
        val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
            activeCallId = callId
        }.onFailure {
            Log.w(TAG, "Could not play incoming call ringtone", it)
            stop()
        }
    }

    @Synchronized
    fun stop(callId: String? = null) {
        if (!callId.isNullOrBlank() && activeCallId != callId) return
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        activeCallId = null
    }
}
