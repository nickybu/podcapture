package com.podcapture.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.podcapture.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps episode downloads running even when the app is in the background.
 *
 * The service owns no work: [DownloadManager] does the downloading and starts this service on
 * every enqueue. The service mirrors [DownloadManager.activity] into its notification and stops
 * itself once the queue is idle.
 */
class EpisodeDownloadService : Service() {

    private val downloadManager: DownloadManager by inject()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Most recent startId, so stopping on idle can't race a start request that just arrived.
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // startForeground must happen promptly after startForegroundService.
        startInForeground(buildNotification(NotificationContent.from(downloadManager.currentActivity())))
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val content = NotificationContent.from(downloadManager.currentActivity())
        // Answer every startForegroundService call with startForeground.
        startInForeground(buildNotification(content))
        if (content.isIdle) {
            // Nothing to do, e.g. the download finished or failed before the service came up.
            stopSelf(startId)
        }
        // Download state lives only in memory, so a restarted service would have nothing to do.
        return START_NOT_STICKY
    }

    /**
     * Called on API 35+ when the dataSync foreground-service time limit is reached. The service
     * must stop within a few seconds or the system crashes the app.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service time limit reached; stopping download service")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    @OptIn(FlowPreview::class)
    private fun observeDownloads() {
        serviceScope.launch {
            downloadManager.activity
                .map { NotificationContent.from(it) }
                .distinctUntilChanged()
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .collect { content ->
                    if (content.isIdle) {
                        // Only stops if no newer start request has arrived; if one has, its
                        // onStartCommand re-checks.
                        stopSelfResult(lastStartId)
                    } else {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
                    }
                }
        }
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
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

    private fun buildNotification(content: NotificationContent): Notification {
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
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, content.percent, content.indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /** Only what the notification shows, so byte-level progress changes don't re-post it. */
    private data class NotificationContent(
        val title: String,
        val text: String,
        val percent: Int,
        val indeterminate: Boolean,
        val isIdle: Boolean
    ) {
        companion object {
            fun from(activity: DownloadActivity): NotificationContent {
                val current = activity.current
                val title = if (current != null) {
                    "Downloading: ${current.episodeTitle}"
                } else {
                    "Preparing download..."
                }
                val text = buildString {
                    if (current != null) {
                        append("${current.percent}%")
                        if (current.podcastTitle.isNotBlank()) {
                            append(" - ${current.podcastTitle}")
                        }
                    }
                    if (activity.queuedCount > 0) {
                        append(" (${activity.queuedCount} more in queue)")
                    }
                }
                return NotificationContent(
                    title = title,
                    text = text,
                    percent = current?.percent ?: 0,
                    indeterminate = current == null,
                    isIdle = activity.isIdle
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "episode_downloads"
        const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
        private const val TAG = "EpisodeDownloadService"
    }
}
