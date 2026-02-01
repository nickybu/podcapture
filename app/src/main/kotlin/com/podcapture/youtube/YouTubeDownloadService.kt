package com.podcapture.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.podcapture.MainActivity
import com.podcapture.data.repository.AudioFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Foreground service for YouTube audio downloads.
 * Runs the download in the background with a progress notification.
 */
class YouTubeDownloadService : Service() {

    private val downloadManager: YouTubeDownloadManager by inject()
    private val audioFileRepository: AudioFileRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var notificationUpdateJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle cancel action
        if (intent?.action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel action received")
            downloadManager.cancelDownload()
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID)

        if (url == null || videoId == null) {
            Log.e(TAG, "Missing URL or video ID")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground immediately
        startForeground(NOTIFICATION_ID, createNotification())

        // Start notification updates
        startNotificationUpdates()

        // Execute the download
        downloadJob = serviceScope.launch {
            downloadManager.executeDownload(url, videoId) { result ->
                result.fold(
                    onSuccess = { (filePath, title) ->
                        // Save to repository
                        serviceScope.launch {
                            try {
                                val duration = getAudioDuration(filePath)
                                val format = filePath.substringAfterLast('.', "m4a")
                                val audioFile = audioFileRepository.addOrUpdateFile(
                                    name = title,
                                    filePath = filePath,
                                    durationMs = duration,
                                    format = format
                                )
                                downloadManager.onDownloadCompleted(audioFile.id, title)
                                showCompletionNotification(title)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save audio file", e)
                            }
                            stopSelf()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Download failed: ${error.message}")
                        showErrorNotification(error.message ?: "Download failed")
                        stopSelf()
                    }
                )
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of YouTube audio downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // Also create completion channel with higher importance
            val completionChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                "Download Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when YouTube downloads complete"
            }
            notificationManager.createNotificationChannel(completionChannel)
        }
    }

    private fun createNotification(): Notification {
        val downloadInfo = downloadManager.getCurrentDownloadInfo()

        val title = when {
            downloadInfo != null -> "Downloading: ${downloadInfo.title}"
            else -> "Preparing download..."
        }

        val text = buildString {
            if (downloadInfo != null) {
                append("${downloadInfo.percent}%")
                downloadInfo.eta?.let { append(" - ETA: $it") }
            } else {
                append("Initializing...")
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, YouTubeDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, downloadInfo?.percent ?: 0, downloadInfo == null)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun startNotificationUpdates() {
        notificationUpdateJob = serviceScope.launch {
            while (true) {
                delay(500) // Update every 500ms
                val state = downloadManager.downloadState.value
                if (state is YouTubeDownloadState.Completed ||
                    state is YouTubeDownloadState.Error ||
                    state is YouTubeDownloadState.Cancelled) {
                    break
                }
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }
        }
    }

    private fun showCompletionNotification(title: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DOWNLOADED_FILE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Open", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setContentTitle("Download failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    private fun getAudioDuration(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio duration", e)
            0L
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "YouTubeDownloadService"
        const val CHANNEL_ID = "youtube_downloads"
        const val CHANNEL_ID_COMPLETE = "youtube_downloads_complete"
        const val NOTIFICATION_ID = 3001
        const val NOTIFICATION_ID_COMPLETE = 3002
        const val NOTIFICATION_ID_ERROR = 3003

        const val EXTRA_URL = "url"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_OPEN_DOWNLOADED_FILE = "open_downloaded_file"
        const val ACTION_CANCEL = "com.podcapture.youtube.CANCEL"
    }
}
