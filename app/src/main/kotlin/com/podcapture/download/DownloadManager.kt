package com.podcapture.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.model.Podcast
import com.podcapture.data.repository.PodcastRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Download progress information for an episode.
 */
data class DownloadProgress(
    val episodeId: Long,
    val state: EpisodeDownloadState,
    val percent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val episodeTitle: String = "",
    val podcastTitle: String = ""
)

/**
 * What the download service needs to render its notification.
 *
 * @property current Progress of the episode being downloaded right now, or null if none.
 * @property queuedCount Number of episodes waiting behind the current one.
 */
data class DownloadActivity(
    val current: DownloadProgress?,
    val queuedCount: Int,
    val isIdle: Boolean
)

/**
 * Centralized download manager that handles episode downloads in the background.
 * Uses a foreground service to ensure downloads continue when the user leaves the screen.
 *
 * Downloads run strictly one at a time: a single worker coroutine pulls requests off
 * [pending] and runs each one in its own [Job], so [cancelDownload] can stop the active
 * download without killing the worker. All queue state is guarded by [lock].
 */
class DownloadManager internal constructor(
    private val backend: Backend,
    private val serviceStarter: ServiceStarter,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logWarning: (String, Throwable) -> Unit = { _, _ -> }
) {
    constructor(context: Context, podcastRepository: PodcastRepository) : this(
        backend = RepositoryBackend(podcastRepository),
        serviceStarter = AndroidServiceStarter(context.applicationContext),
        logWarning = { message, error -> Log.w(TAG, message, error) }
    )

    /** The repository operations the manager needs; an interface so tests can fake it. */
    internal interface Backend {
        fun getLocalEpisodePath(episodeId: Long): String?

        /** Bookmarks the podcast if needed and caches the episode row (FK constraint). */
        suspend fun prepare(request: DownloadRequest)

        suspend fun download(
            episode: CachedEpisode,
            onProgress: (PodcastRepository.DownloadProgress) -> Unit
        ): Result<String>
    }

    /** Starts the foreground service; the service stops itself once the queue is idle. */
    internal fun interface ServiceStarter {
        fun start()
    }

    data class DownloadRequest(
        val episode: CachedEpisode,
        val podcastTitle: String,
        val podcast: Podcast? = null
    )

    private class ActiveDownload(val request: DownloadRequest, val job: Job)

    private data class QueueSnapshot(val activeEpisodeId: Long? = null, val queuedCount: Int = 0)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Map of episodeId to download progress
    private val _downloadStates = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadProgress>> = _downloadStates.asStateFlow()

    private val lock = Any()
    private val pending = ArrayDeque<DownloadRequest>() // guarded by lock
    private var active: ActiveDownload? = null // guarded by lock
    private val queueSnapshot = MutableStateFlow(QueueSnapshot()) // written under lock

    // Wakes the worker when something is queued. Conflated: one pending signal is enough.
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /**
     * Current download and queue length, for the service notification. Emits on every
     * progress change; collectors should throttle.
     */
    val activity: Flow<DownloadActivity> =
        combine(queueSnapshot, _downloadStates) { queue, states -> buildActivity(queue, states) }
            .distinctUntilChanged()

    init {
        scope.launch { runWorker() }
    }

    /**
     * Start downloading an episode. The download will continue in the background
     * even if the user leaves the screen.
     *
     * @param episode The episode to download
     * @param podcastTitle Title of the podcast for notifications
     * @param podcast Optional podcast object - if provided and podcast is not bookmarked,
     *                it will be auto-bookmarked to satisfy the database foreign key constraint
     */
    fun downloadEpisode(episode: CachedEpisode, podcastTitle: String, podcast: Podcast? = null) {
        // Check if already downloaded
        val localPath = backend.getLocalEpisodePath(episode.id)
        if (localPath != null) {
            _downloadStates.update { states ->
                states + (episode.id to DownloadProgress(
                    episodeId = episode.id,
                    state = EpisodeDownloadState.Downloaded,
                    percent = 100,
                    episodeTitle = episode.title,
                    podcastTitle = podcastTitle
                ))
            }
            return
        }

        synchronized(lock) {
            // A cancelled active job is still winding down; allow re-queueing that episode.
            val alreadyQueued =
                active?.takeIf { !it.job.isCancelled }?.request?.episode?.id == episode.id ||
                    pending.any { it.episode.id == episode.id }
            if (alreadyQueued) return

            pending.addLast(DownloadRequest(episode, podcastTitle, podcast))

            // Update state to queued/downloading
            _downloadStates.update { states ->
                states + (episode.id to DownloadProgress(
                    episodeId = episode.id,
                    state = EpisodeDownloadState.Downloading,
                    percent = 0,
                    totalBytes = episode.audioSize,
                    episodeTitle = episode.title,
                    podcastTitle = podcastTitle
                ))
            }
            publishQueueLocked()
        }

        // Every enqueue (re)starts the service; onStartCommand re-posts the foreground
        // notification, so repeated starts are harmless.
        serviceStarter.start()
        wakeUp.trySend(Unit)
    }

    /**
     * Cancel a queued or in-progress download.
     */
    fun cancelDownload(episodeId: Long) {
        val jobToCancel = synchronized(lock) {
            pending.removeAll { it.episode.id == episodeId }
            _downloadStates.update { states -> states - episodeId }
            publishQueueLocked()
            active?.takeIf { it.request.episode.id == episodeId }?.job
        }
        // The worker moves on once the job completes; the service stops itself when idle.
        jobToCancel?.cancel()
    }

    /**
     * Get the current download state for an episode.
     */
    fun getDownloadState(episodeId: Long): DownloadProgress? {
        return _downloadStates.value[episodeId]
    }

    /**
     * Check if an episode is downloaded.
     */
    fun isDownloaded(episodeId: Long): Boolean {
        return backend.getLocalEpisodePath(episodeId) != null
    }

    /**
     * Get the episode currently being downloaded, for the notification.
     */
    fun getCurrentDownloadInfo(): DownloadProgress? = currentActivity().current

    /**
     * Get the number of downloads waiting behind the current one.
     */
    fun getPendingCount(): Int = synchronized(lock) { pending.size }

    /** Synchronous snapshot of [activity]. */
    fun currentActivity(): DownloadActivity =
        buildActivity(queueSnapshot.value, _downloadStates.value)

    private fun buildActivity(
        queue: QueueSnapshot,
        states: Map<Long, DownloadProgress>
    ): DownloadActivity = DownloadActivity(
        current = queue.activeEpisodeId?.let { states[it] },
        queuedCount = queue.queuedCount,
        isIdle = queue.activeEpisodeId == null && queue.queuedCount == 0
    )

    private fun publishQueueLocked() {
        queueSnapshot.value = QueueSnapshot(active?.request?.episode?.id, pending.size)
    }

    private suspend fun runWorker() {
        while (true) {
            val next = synchronized(lock) {
                pending.removeFirstOrNull()?.let { request ->
                    // LAZY so the job is registered as active before it can run or be cancelled.
                    val job = scope.launch(start = CoroutineStart.LAZY) { runDownload(request) }
                    active = ActiveDownload(request, job)
                    publishQueueLocked()
                    job
                }
            }
            if (next == null) {
                wakeUp.receive()
                continue
            }
            // join() starts the lazy job and waits for it, whether it succeeds, fails or is cancelled.
            next.join()
            synchronized(lock) {
                active = null
                publishQueueLocked()
            }
        }
    }

    private suspend fun runDownload(request: DownloadRequest) {
        val episode = request.episode
        val result = try {
            backend.prepare(request)
            backend.download(episode) { progress ->
                _downloadStates.update { states ->
                    // Don't resurrect an entry that cancelDownload already removed.
                    if (episode.id !in states) return@update states
                    states + (episode.id to DownloadProgress(
                        episodeId = episode.id,
                        state = EpisodeDownloadState.Downloading,
                        percent = progress.percent,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        episodeTitle = episode.title,
                        podcastTitle = request.podcastTitle
                    ))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        // If cancelled meanwhile, cancelDownload already cleared the state.
        currentCoroutineContext().ensureActive()

        val finalState = result.fold(
            onSuccess = {
                DownloadProgress(
                    episodeId = episode.id,
                    state = EpisodeDownloadState.Downloaded,
                    percent = 100,
                    episodeTitle = episode.title,
                    podcastTitle = request.podcastTitle
                )
            },
            onFailure = { error ->
                logWarning("Download failed for episode ${episode.id}", error)
                DownloadProgress(
                    episodeId = episode.id,
                    state = EpisodeDownloadState.Error,
                    percent = 0,
                    episodeTitle = episode.title,
                    podcastTitle = request.podcastTitle
                )
            }
        )
        _downloadStates.update { states -> states + (episode.id to finalState) }
    }

    private class RepositoryBackend(private val repository: PodcastRepository) : Backend {
        override fun getLocalEpisodePath(episodeId: Long): String? =
            repository.getLocalEpisodePath(episodeId)

        override suspend fun prepare(request: DownloadRequest) {
            val isBookmarked = repository.isPodcastBookmarked(request.episode.podcastId).first()
            if (!isBookmarked && request.podcast != null) {
                repository.bookmarkPodcast(request.podcast)
            }
            repository.cacheEpisode(request.episode)
        }

        override suspend fun download(
            episode: CachedEpisode,
            onProgress: (PodcastRepository.DownloadProgress) -> Unit
        ): Result<String> = repository.downloadEpisode(episode, onProgress)
    }

    private class AndroidServiceStarter(private val context: Context) : ServiceStarter {
        override fun start() {
            val intent = Intent(context, EpisodeDownloadService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when started from the
                // background. The download still runs while the process is alive.
                Log.w(TAG, "Could not start download service", e)
            }
        }
    }

    private companion object {
        const val TAG = "DownloadManager"
    }
}
