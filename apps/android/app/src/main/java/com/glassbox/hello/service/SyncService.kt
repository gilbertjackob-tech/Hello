package com.glassbox.hello.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glassbox.hello.R
import com.glassbox.hello.utils.Constants

/**
 * Foreground entry point for browser sync requests.
 */
class SyncService : Service() {
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val profileId = intent?.getIntExtra(SyncWorker.KEY_PROFILE_ID, ALL_PROFILES) ?: ALL_PROFILES
        val forceFullSync = intent?.getBooleanExtra(SyncWorker.KEY_FORCE_FULL_SYNC, false) ?: false
        when (intent?.action) {
            ACTION_SCHEDULE_PERIODIC_SYNC -> SyncWorker.schedulePeriodicSync(applicationContext, profileId)
            ACTION_SYNC_NOW -> SyncWorker.requestImmediateSync(applicationContext, profileId, forceFullSync)
            else -> SyncWorker.requestImmediateSync(applicationContext, profileId, forceFullSync)
        }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.SYNC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.sync_in_progress))
            .setContentText(getString(R.string.sync_in_progress_detail))
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.SYNC_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.background_sync),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        private const val NOTIFICATION_ID: Int = 8802
        private const val ALL_PROFILES: Int = -1

        const val ACTION_SYNC_NOW: String = "com.glassbox.hello.action.SYNC_NOW"
        const val ACTION_SCHEDULE_PERIODIC_SYNC: String = "com.glassbox.hello.action.SCHEDULE_PERIODIC_SYNC"

        /**
         * Requests immediate background sync.
         */
        fun requestImmediateSync(context: Context, profileId: Int = ALL_PROFILES, forceFullSync: Boolean = false) {
            val intent = Intent(context, SyncService::class.java).apply {
                action = ACTION_SYNC_NOW
                putExtra(SyncWorker.KEY_PROFILE_ID, profileId)
                putExtra(SyncWorker.KEY_FORCE_FULL_SYNC, forceFullSync)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Schedules periodic background sync.
         */
        fun schedulePeriodicSync(context: Context, profileId: Int = ALL_PROFILES) {
            val intent = Intent(context, SyncService::class.java).apply {
                action = ACTION_SCHEDULE_PERIODIC_SYNC
                putExtra(SyncWorker.KEY_PROFILE_ID, profileId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
