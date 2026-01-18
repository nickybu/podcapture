package com.podcapture.data.repository

import android.content.Context
import com.podcapture.data.api.PodcastIndexApi
import com.podcapture.data.db.AudioFileDao
import com.podcapture.data.db.PodcastDao
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.Episode
import com.podcapture.data.model.LatestEpisode
import com.podcapture.data.model.EpisodePlaybackHistory
import com.podcapture.data.model.Podcast
import com.podcapture.data.model.PodcastTag
import com.podcapture.data.model.Tag
import com.podcapture.data.model.toDomain
import com.podcapture.data.opml.OpmlFeed
import com.podcapture.data.opml.OpmlManager
import com.podcapture.data.settings.SettingsDataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Repository for managing bookmarked podcasts, cached episodes, and playback history.
 */
class PodcastRepository(
    private val context: Context,
    private val api: PodcastIndexApi,
    private val podcastDao: PodcastDao,
    private val audioFileDao: AudioFileDao,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_EPISODES = 1000
    }

    private suspend fun trackApiCall() {
        settingsDataStore.incrementApiCallCount()
    }

    // ============ Bookmarked Podcasts ============

    fun getAllBookmarkedPodcasts(): Flow<List<BookmarkedPodcast>> {
        return podcastDao.getAllBookmarkedPodcasts()
    }

    fun isPodcastBookmarked(podcastId: Long): Flow<Boolean> {
        return podcastDao.isPodcastBookmarked(podcastId)
    }

    suspend fun bookmarkPodcast(podcast: Podcast): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bookmarked = BookmarkedPodcast(
                id = podcast.id,
                title = podcast.title,
                author = podcast.author,
                description = podcast.description,
                artworkUrl = podcast.artworkUrl,
                feedUrl = podcast.feedUrl,
                language = podcast.language,
                episodeCount = podcast.episodeCount,
                lastUpdateTime = podcast.lastUpdateTime
            )

            podcastDao.insertBookmarkedPodcast(bookmarked)

            // Fetch all episodes and cache them
            fetchAndCacheEpisodes(podcast.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unbookmarkPodcast(podcastId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            podcastDao.deleteBookmarkedPodcastById(podcastId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ Latest Episodes (Across All Bookmarked Podcasts) ============

    /**
     * Gets recent episodes from the last 7 days from all bookmarked podcasts.
     * First refreshes episodes if this is a new day since the last cache.
     */
    suspend fun getLatestEpisodes(forceRefresh: Boolean = false): List<LatestEpisode> = withContext(Dispatchers.IO) {
        val today = settingsDataStore.getTodayDate()
        val lastCacheDate = settingsDataStore.getLatestEpisodesCacheDate()

        // Refresh episodes for all bookmarked podcasts if it's a new day or forced
        if (forceRefresh || lastCacheDate != today) {
            val bookmarkedPodcasts = podcastDao.getAllBookmarkedPodcasts().first()
            for (podcast in bookmarkedPodcasts) {
                refreshEpisodes(podcast.id)
            }
            settingsDataStore.setLatestEpisodesCacheDate(today)
        }

        // Get episodes from the last 7 days
        val sevenDaysAgo = System.currentTimeMillis() / 1000 - (7 * 24 * 60 * 60)
        val recentEpisodes = podcastDao.getRecentEpisodesFromBookmarkedPodcasts(sevenDaysAgo)

        // Get podcast info for all episodes
        val podcastIds = recentEpisodes.map { it.podcastId }.distinct()
        val podcasts = podcastDao.getBookmarkedPodcastsByIds(podcastIds)
        val podcastMap = podcasts.associateBy { it.id }

        // Map to LatestEpisode with podcast info
        recentEpisodes.mapNotNull { episode ->
            val podcast = podcastMap[episode.podcastId]
            if (podcast != null) {
                LatestEpisode(
                    episode = episode,
                    podcastTitle = podcast.title,
                    podcastArtworkUrl = podcast.artworkUrl
                )
            } else null
        }
    }

    // ============ Episode Caching & Pagination ============

    fun getEpisodesForPodcast(podcastId: Long): Flow<List<CachedEpisode>> {
        return podcastDao.getEpisodesForPodcast(podcastId)
    }

    /**
     * Fetches and caches all episodes for a podcast.
     * On first load, fetches up to MAX_EPISODES.
     * On subsequent loads, only fetches new episodes using 'since' parameter.
     */
    private suspend fun fetchAndCacheEpisodes(podcastId: Long): Result<List<CachedEpisode>> {
        return try {
            // Check if we have cached episodes - if so, only fetch new ones
            val newestTimestamp = podcastDao.getNewestEpisodeTimestamp(podcastId)

            trackApiCall()
            val response = if (newestTimestamp != null) {
                // Fetch only episodes newer than what we have
                api.getEpisodesByPodcastId(podcastId, MAX_EPISODES, since = newestTimestamp, fulltext = true)
            } else {
                // First load - fetch all episodes
                api.getEpisodesByPodcastId(podcastId, MAX_EPISODES, fulltext = true)
            }

            if (response.status == "true") {
                val episodes = response.items.map { it.toDomain().toCachedEpisode() }
                if (episodes.isNotEmpty()) {
                    podcastDao.insertEpisodes(episodes)
                }
                Result.success(episodes)
            } else {
                Result.failure(Exception("Failed to fetch episodes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refreshes episodes for a podcast from the API.
     * Only fetches episodes newer than what we have cached.
     */
    suspend fun refreshEpisodes(podcastId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get newest episode timestamp to only fetch new episodes
            val newestTimestamp = podcastDao.getNewestEpisodeTimestamp(podcastId)

            trackApiCall()
            val response = if (newestTimestamp != null) {
                api.getEpisodesByPodcastId(podcastId, MAX_EPISODES, since = newestTimestamp, fulltext = true)
            } else {
                api.getEpisodesByPodcastId(podcastId, MAX_EPISODES, fulltext = true)
            }

            if (response.status == "true") {
                val episodes = response.items.map { it.toDomain().toCachedEpisode() }
                if (episodes.isNotEmpty()) {
                    podcastDao.insertEpisodes(episodes) // REPLACE strategy handles duplicates
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to refresh episodes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEpisodeById(episodeId: Long): CachedEpisode? {
        return podcastDao.getEpisodeById(episodeId)
    }

    /**
     * Caches a single episode to the database.
     * Used when downloading an episode for a non-bookmarked podcast.
     */
    suspend fun cacheEpisode(episode: CachedEpisode) = withContext(Dispatchers.IO) {
        podcastDao.insertEpisode(episode)
    }

    // ============ Podcast Tags ============

    fun getTagsForPodcast(podcastId: Long): Flow<List<Tag>> {
        return podcastDao.getTagsForPodcast(podcastId)
    }

    suspend fun addTagToPodcast(podcastId: Long, tagId: String) = withContext(Dispatchers.IO) {
        podcastDao.insertPodcastTag(PodcastTag(podcastId, tagId))
    }

    suspend fun removeTagFromPodcast(podcastId: Long, tagId: String) = withContext(Dispatchers.IO) {
        podcastDao.removePodcastTag(podcastId, tagId)
    }

    fun getBookmarkedPodcastsByTag(tagId: String): Flow<List<BookmarkedPodcast>> {
        return podcastDao.getBookmarkedPodcastsByTag(tagId)
    }

    // ============ Episode Playback History ============

    fun getRecentPlaybackHistory(limit: Int = 20): Flow<List<EpisodePlaybackHistory>> {
        return podcastDao.getRecentPlaybackHistory(limit)
    }

    suspend fun recordEpisodePlayback(
        episode: CachedEpisode,
        podcast: BookmarkedPodcast,
        localFilePath: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = podcastDao.getPlaybackHistoryForEpisode(episode.id)

        val history = EpisodePlaybackHistory(
            episodeId = episode.id,
            podcastId = podcast.id,
            podcastTitle = podcast.title,
            podcastArtworkUrl = podcast.artworkUrl,
            episodeTitle = episode.title,
            duration = episode.duration,
            positionMs = existing?.positionMs ?: 0,
            firstPlayedAt = existing?.firstPlayedAt ?: System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            localFilePath = localFilePath ?: existing?.localFilePath
        )

        podcastDao.insertPlaybackHistory(history)
    }

    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        podcastDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun getPlaybackHistoryForEpisode(episodeId: Long): EpisodePlaybackHistory? {
        return podcastDao.getPlaybackHistoryForEpisode(episodeId)
    }

    // ============ Episode Download ============

    /**
     * Download progress info with percentage and byte counts.
     */
    data class DownloadProgress(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    /**
     * Downloads an episode to local storage for offline playback.
     * Also creates an AudioFile entry so captures work the same as regular files.
     *
     * @return Local file path on success
     */
    suspend fun downloadEpisode(
        episode: CachedEpisode,
        onProgress: (DownloadProgress) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val episodesDir = File(context.filesDir, "episodes")
            if (!episodesDir.exists()) {
                episodesDir.mkdirs()
            }

            val extension = when {
                episode.audioType.contains("mp3") -> ".mp3"
                episode.audioType.contains("m4a") -> ".m4a"
                episode.audioType.contains("mp4") -> ".m4a"
                episode.audioType.contains("aac") -> ".aac"
                episode.audioType.contains("ogg") -> ".ogg"
                else -> ".mp3"
            }

            val filename = "episode_${episode.id}$extension"
            val targetFile = File(episodesDir, filename)
            val audioFileId = "episode_${episode.id}"

            // If already downloaded, ensure AudioFile entry exists and return
            if (targetFile.exists() && targetFile.length() > 0) {
                ensureAudioFileEntry(audioFileId, episode, targetFile)
                return@withContext Result.success(targetFile.absolutePath)
            }

            val tempFile = File(episodesDir, "$filename.tmp")

            val connection = URL(episode.audioUrl).openConnection()
            val contentLength = connection.contentLengthLong.takeIf { it > 0 }
                ?: episode.audioSize.takeIf { it > 0 }
                ?: 0L

            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 99)
                        } else 0
                        onProgress(DownloadProgress(progress, totalBytesRead, contentLength))
                    }
                }
            }

            tempFile.renameTo(targetFile)
            val finalSize = targetFile.length()
            onProgress(DownloadProgress(100, finalSize, finalSize))

            // Create AudioFile entry for captures to work
            ensureAudioFileEntry(audioFileId, episode, targetFile)

            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates or updates an AudioFile entry for a downloaded episode.
     * This allows captures, markdown export, and Obsidian export to work.
     */
    private suspend fun ensureAudioFileEntry(
        audioFileId: String,
        episode: CachedEpisode,
        file: File
    ) {
        val existingFile = audioFileDao.getFileById(audioFileId)
        if (existingFile == null) {
            val audioFile = AudioFile(
                id = audioFileId,
                name = episode.title,
                filePath = file.absolutePath,
                durationMs = episode.duration * 1000L, // Convert seconds to milliseconds
                format = episode.audioType.substringAfter("/").ifEmpty { "mp3" },
                firstPlayedAt = null,
                lastPlayedAt = null,
                lastPositionMs = 0,
                addedAt = System.currentTimeMillis(),
                playCount = 0,
                isBookmarked = false,
                bookmarkedAt = null
            )
            audioFileDao.insertFile(audioFile)
        }
    }

    /**
     * Checks if an episode is downloaded locally.
     * First checks the file system directly, then falls back to playback history.
     */
    fun getLocalEpisodePath(episodeId: Long): String? {
        val episodesDir = File(context.filesDir, "episodes")
        if (!episodesDir.exists()) return null

        // Check for common audio extensions
        val extensions = listOf(".mp3", ".m4a", ".aac", ".ogg")
        for (ext in extensions) {
            val file = File(episodesDir, "episode_$episodeId$ext")
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * Deletes a downloaded episode file while preserving:
     * - Playback history (position, timestamps)
     * - Captures (transcriptions, notes, tags)
     *
     * Only removes the local file and clears localFilePath from history.
     */
    suspend fun deleteDownloadedEpisode(episodeId: Long) = withContext(Dispatchers.IO) {
        val audioFileId = "episode_$episodeId"

        // Delete the physical file
        val localPath = getLocalEpisodePath(episodeId)
        localPath?.let { path ->
            File(path).delete()
        }

        // Clear localFilePath from history but keep the history record
        val history = podcastDao.getPlaybackHistoryForEpisode(episodeId)
        if (history != null) {
            podcastDao.insertPlaybackHistory(history.copy(localFilePath = null))
        }

        // Remove the AudioFile entry (captures are NOT deleted because they have no FK constraint)
        audioFileDao.deleteFile(audioFileId)
    }

    /**
     * Checks if an episode has any captures saved.
     */
    suspend fun hasEpisodeCaptures(episodeId: Long): Boolean = withContext(Dispatchers.IO) {
        val audioFileId = "episode_$episodeId"
        val audioFile = audioFileDao.getFileById(audioFileId)
        audioFile != null
    }

    // ============ OPML Import/Export ============

    private val opmlManager = OpmlManager()

    /**
     * Result of an OPML import operation.
     */
    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>
    )

    /**
     * Exports all bookmarked podcasts to OPML format.
     */
    suspend fun exportToOpml(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val podcasts = podcastDao.getAllBookmarkedPodcasts().first()
            val podcastsWithFeeds = podcasts.filter { it.feedUrl.isNotBlank() }
            opmlManager.writeOpml(outputStream, podcastsWithFeeds)
            Result.success(podcastsWithFeeds.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports podcasts from an OPML file.
     * Looks up each feed URL via the Podcast Index API and bookmarks the podcast.
     * Skips podcasts that are already bookmarked.
     */
    suspend fun importFromOpml(
        inputStream: InputStream,
        onProgress: (current: Int, total: Int, podcastTitle: String) -> Unit
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val parseResult = opmlManager.parseOpml(inputStream)
            if (parseResult.isFailure) {
                return@withContext Result.failure(parseResult.exceptionOrNull() ?: Exception("Failed to parse OPML"))
            }

            val document = parseResult.getOrThrow()
            val feeds = document.feeds
            var imported = 0
            var skipped = 0
            var failed = 0
            val errors = mutableListOf<String>()

            feeds.forEachIndexed { index, feed ->
                onProgress(index + 1, feeds.size, feed.title)

                try {
                    val result = importSingleFeed(feed)
                    when (result) {
                        ImportFeedResult.IMPORTED -> imported++
                        ImportFeedResult.SKIPPED -> skipped++
                        ImportFeedResult.FAILED -> {
                            failed++
                            errors.add("${feed.title}: Not found in Podcast Index")
                        }
                    }
                } catch (e: Exception) {
                    failed++
                    errors.add("${feed.title}: ${e.message}")
                }
            }

            Result.success(ImportResult(imported, skipped, failed, errors))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private enum class ImportFeedResult { IMPORTED, SKIPPED, FAILED }

    private suspend fun importSingleFeed(feed: OpmlFeed): ImportFeedResult {
        // Try to look up by feed URL
        trackApiCall()
        val response = try {
            api.getPodcastByFeedUrl(feed.feedUrl)
        } catch (e: Exception) {
            return ImportFeedResult.FAILED
        }

        if (response.status != "true" || response.feed == null) {
            return ImportFeedResult.FAILED
        }

        val podcast = response.feed.toDomain()

        // Check if already bookmarked
        val isBookmarked = podcastDao.isPodcastBookmarked(podcast.id).first()
        if (isBookmarked) {
            return ImportFeedResult.SKIPPED
        }

        // Bookmark the podcast
        bookmarkPodcast(podcast)
        return ImportFeedResult.IMPORTED
    }

    /**
     * Generates OPML content as a string (for sharing).
     */
    suspend fun generateOpmlString(): String = withContext(Dispatchers.IO) {
        val podcasts = podcastDao.getAllBookmarkedPodcasts().first()
        val podcastsWithFeeds = podcasts.filter { it.feedUrl.isNotBlank() }
        opmlManager.generateOpml(podcastsWithFeeds)
    }
}

/**
 * Extension function to convert Episode to CachedEpisode.
 */
private fun Episode.toCachedEpisode() = CachedEpisode(
    id = id,
    podcastId = podcastId,
    title = title,
    description = description,
    link = link,
    publishedDate = publishedDate,
    duration = duration,
    audioUrl = audioUrl,
    audioType = audioType,
    audioSize = audioSize,
    imageUrl = imageUrl,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl
)
