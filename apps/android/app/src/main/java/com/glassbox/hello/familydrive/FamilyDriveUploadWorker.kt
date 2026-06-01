package com.glassbox.hello.familydrive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class FamilyDriveUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uploaderId = inputData.getString(KEY_UPLOADER_ID).orEmpty()
        if (uploaderId.isBlank()) return Result.failure()

        val uploadedCount = FamilyDriveRepository()
            .retryPendingUploads(applicationContext, uploaderId)
            .getOrElse { return Result.retry() }

        if (uploadedCount > 0) {
            showCompletedNotification(applicationContext, uploadedCount)
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "family_drive_uploads"
        private const val CHANNEL_NAME = "Family Drive uploads"
        private const val NOTIFICATION_ID = 8026
        private const val UNIQUE_WORK_NAME = "family_drive_pending_uploads"
        private const val KEY_UPLOADER_ID = "uploader_id"

        fun enqueue(context: Context, uploaderId: String) {
            if (uploaderId.isBlank()) return
            val request = OneTimeWorkRequestBuilder<FamilyDriveUploadWorker>()
                .setInputData(workDataOf(KEY_UPLOADER_ID to uploaderId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        private fun showCompletedNotification(context: Context, uploadedCount: Int) {
            val appContext = context.applicationContext
            ensureChannel(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val body = if (uploadedCount == 1) {
                "Pending upload completed. Your photo/video is saved to PC."
            } else {
                "$uploadedCount pending uploads completed. Your photos/videos are saved to PC."
            }
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Family Drive")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
