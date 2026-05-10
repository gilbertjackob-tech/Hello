package com.glassbox.hello.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.glassbox.hello.R
import com.glassbox.hello.database.entities.DownloadEntity
import com.glassbox.hello.repository.DownloadRepository
import com.glassbox.hello.utils.Constants
import com.glassbox.hello.utils.FileUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Foreground download service with progress tracking, pause/resume, and notifications.
 */
class DownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()
    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.create(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action in setOf(ACTION_PAUSE_DOWNLOAD, ACTION_CANCEL_DOWNLOAD)) {
            startForeground(
                CONTROL_NOTIFICATION_ID,
                buildProgressNotification(getString(R.string.downloads), 0, getString(R.string.download_control_in_progress))
            )
        }
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> handleStart(intent)
            ACTION_PAUSE_DOWNLOAD -> pauseDownload(intent.getIntExtra(EXTRA_DOWNLOAD_ID, 0))
            ACTION_RESUME_DOWNLOAD -> resumeDownload(intent)
            ACTION_CANCEL_DOWNLOAD -> cancelDownload(intent.getIntExtra(EXTRA_DOWNLOAD_ID, 0))
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        jobs.values.forEach { job -> job.cancel() }
        jobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val profileId = intent.getIntExtra(EXTRA_PROFILE_ID, 0)
        if (url.isBlank() || profileId <= 0) {
            Log.w(TAG, "Ignoring download request with missing URL or profile id.")
            stopSelf()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: URLUtil.guessFileName(url, null, intent.getStringExtra(EXTRA_MIME_TYPE))
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
        val notificationId = notificationId(url, fileName)
        startForeground(notificationId, buildProgressNotification(fileName, 0, "Preparing download"))

        val job = serviceScope.launch {
            val downloadId = repository.enqueueDownload(
                url = url,
                fileName = fileName,
                filePath = FileUtils.createDownloadFile(applicationContext, fileName).absolutePath,
                fileSize = DownloadEntity.UNKNOWN_SIZE,
                profileId = profileId,
                mimeType = mimeType
            ).toInt()
            runDownload(downloadId, url, fileName, mimeType, notificationId, resume = false)
        }
    }

    private fun resumeDownload(intent: Intent) {
        val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, 0)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: URLUtil.guessFileName(url, null, intent.getStringExtra(EXTRA_MIME_TYPE))
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
        if (downloadId <= 0 || url.isBlank()) return
        val notificationId = notificationId(url, fileName)
        startForeground(notificationId, buildProgressNotification(fileName, 0, "Resuming download"))
        serviceScope.launch {
            runDownload(downloadId, url, fileName, mimeType, notificationId, resume = true)
        }
    }

    private suspend fun runDownload(
        downloadId: Int,
        url: String,
        fileName: String,
        mimeType: String?,
        notificationId: Int,
        resume: Boolean
    ) {
        jobs[downloadId]?.cancel()
        val currentJob = coroutineContext[Job]
        if (currentJob != null) {
            jobs[downloadId] = currentJob
        }

        try {
            runCatching {
                if (resume) repository.resumeDownload(downloadId) else repository.startDownload(downloadId)
            }
            val file = FileUtils.createDownloadFile(applicationContext, fileName)
            val partialFile = File(file.absolutePath + PARTIAL_SUFFIX)
            downloadToFile(downloadId, url, partialFile, mimeType, notificationId, resume)
            if (file.exists()) file.delete()
            check(partialFile.renameTo(file)) { "Could not move partial download into place." }
            repository.completeDownload(downloadId, file.length())
            notificationManager.notify(notificationId, buildCompleteNotification(fileName, file, mimeType))
        } catch (error: CancellationException) {
            repository.pauseDownload(downloadId)
            notificationManager.notify(notificationId, buildProgressNotification(fileName, 0, "Download paused"))
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Download failed for $url.", error)
            runCatching { repository.failDownload(downloadId) }
            notificationManager.notify(
                notificationId,
                buildErrorNotification(fileName, error.message ?: "Download failed")
            )
        } finally {
            jobs.remove(downloadId)
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private suspend fun downloadToFile(
        downloadId: Int,
        url: String,
        file: File,
        mimeType: String?,
        notificationId: Int,
        resume: Boolean
    ) = withContext(Dispatchers.IO) {
        val existingBytes = if (resume && file.exists()) file.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
            if (!mimeType.isNullOrBlank()) {
                setRequestProperty("Accept", mimeType)
            }
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("Server returned HTTP $responseCode.")
            }
            val serverLength = connection.contentLengthLong.takeIf { length -> length > 0L } ?: -1L
            val totalBytes = if (serverLength > 0L) serverLength + existingBytes else DownloadEntity.UNKNOWN_SIZE
            var downloadedBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL) existingBytes else 0L
            if (responseCode != HttpURLConnection.HTTP_PARTIAL && file.exists()) {
                file.delete()
            }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(file, downloadedBytes > 0L).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastNotificationAt = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        repository.updateProgress(downloadId, downloadedBytes, totalBytes)
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationAt >= NOTIFICATION_INTERVAL_MILLIS) {
                            lastNotificationAt = now
                            val progress = DownloadEntity.calculateProgress(downloadedBytes, totalBytes)
                            notificationManager.notify(
                                notificationId,
                                buildProgressNotification(file.name.removeSuffix(PARTIAL_SUFFIX), progress, "Downloading")
                            )
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun pauseDownload(downloadId: Int) {
        if (downloadId <= 0) return
        jobs.remove(downloadId)?.cancel()
        serviceScope.launch {
            runCatching { repository.pauseDownload(downloadId) }
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun cancelDownload(downloadId: Int) {
        if (downloadId <= 0) return
        jobs.remove(downloadId)?.cancel()
        serviceScope.launch {
            runCatching { repository.cancelDownload(downloadId) }
            notificationManager.cancel(downloadId)
            if (jobs.isEmpty()) stopSelf()
        }
    }

    private fun buildProgressNotification(fileName: String, progress: Int, status: String): Notification {
        return NotificationCompat.Builder(this, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(status)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    private fun buildCompleteNotification(fileName: String, file: File, mimeType: String?): Notification {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: FileUtils.getMimeType(applicationContext, file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            file.absolutePath.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.download_complete))
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun buildErrorNotification(fileName: String, error: String): Notification {
        return NotificationCompat.Builder(this, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.download_failed))
            .setContentText("$fileName: $error")
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun notificationId(url: String, fileName: String): Int {
        return "$url:$fileName".hashCode()
    }

    companion object {
        private const val TAG: String = "DownloadService"
        private const val BUFFER_SIZE: Int = 64 * 1024
        private const val NOTIFICATION_INTERVAL_MILLIS: Long = 500L
        private const val PARTIAL_SUFFIX: String = ".part"
        private const val CONTROL_NOTIFICATION_ID: Int = 7781

        const val ACTION_START_DOWNLOAD: String = "com.glassbox.hello.action.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD: String = "com.glassbox.hello.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD: String = "com.glassbox.hello.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD: String = "com.glassbox.hello.action.CANCEL_DOWNLOAD"

        const val EXTRA_PROFILE_ID: String = "extra_profile_id"
        const val EXTRA_DOWNLOAD_ID: String = "extra_download_id"
        const val EXTRA_URL: String = "extra_url"
        const val EXTRA_FILE_NAME: String = "extra_file_name"
        const val EXTRA_MIME_TYPE: String = "extra_mime_type"

        /**
         * Starts a foreground download.
         */
        fun startDownload(
            context: Context,
            profileId: Int,
            url: String,
            fileName: String? = null,
            mimeType: String? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_MIME_TYPE, mimeType)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Requests pausing a running download.
         */
        fun pauseDownload(context: Context, downloadId: Int) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_PAUSE_DOWNLOAD
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
            )
        }

        /**
         * Requests canceling a running download.
         */
        fun cancelDownload(context: Context, downloadId: Int) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
            )
        }
    }
}
