package com.podcapture.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.podcapture.MainActivity
import com.podcapture.R
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps episode downloads running even when the app is in the background.
 */
class EpisodeDownloadService : Service() {

    private val downloadManager: DownloadManager by inject()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Register for notification updates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_NOTIFICATION), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_NOTIFICATION))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Episode Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of episode downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val downloadInfo = downloadManager.getCurrentDownloadInfo()
        val pendingCount = downloadManager.getPendingCount()

        val title = if (downloadInfo != null) {
            "Downloading: ${downloadInfo.episodeTitle}"
        } else {
            "Preparing download..."
        }

        val text = buildString {
            if (downloadInfo != null) {
                append("${downloadInfo.percent}%")
                if (downloadInfo.podcastTitle.isNotBlank()) {
                    append(" - ${downloadInfo.podcastTitle}")
                }
            }
            if (pendingCount > 1) {
                append(" (${pendingCount - 1} more in queue)")
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, downloadInfo?.percent ?: 0, downloadInfo == null)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    companion object {
        const val CHANNEL_ID = "episode_downloads"
        const val NOTIFICATION_ID = 2001
        const val ACTION_UPDATE_NOTIFICATION = "com.podcapture.UPDATE_DOWNLOAD_NOTIFICATION"
    }
}
