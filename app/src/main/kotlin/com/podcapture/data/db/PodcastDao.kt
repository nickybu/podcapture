package com.podcapture.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodePlaybackHistory
import com.podcapture.data.model.PodcastTag
import com.podcapture.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    // ============ Bookmarked Podcasts ============

    @Query("SELECT * FROM bookmarked_podcasts ORDER BY bookmarkedAt DESC")
    fun getAllBookmarkedPodcasts(): Flow<List<BookmarkedPodcast>>

    @Query("SELECT * FROM bookmarked_podcasts WHERE id = :podcastId")
    suspend fun getBookmarkedPodcastById(podcastId: Long): BookmarkedPodcast?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_podcasts WHERE id = :podcastId)")
    fun isPodcastBookmarked(podcastId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_podcasts WHERE id = :podcastId)")
    suspend fun isPodcastBookmarkedSync(podcastId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarkedPodcast(podcast: BookmarkedPodcast)

    @Delete
    suspend fun deleteBookmarkedPodcast(podcast: BookmarkedPodcast)

    @Query("DELETE FROM bookmarked_podcasts WHERE id = :podcastId")
    suspend fun deleteBookmarkedPodcastById(podcastId: Long)

    @Update
    suspend fun updateBookmarkedPodcast(podcast: BookmarkedPodcast)

    // ============ Podcast Tags ============

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN podcast_tags pt ON t.id = pt.tagId
        WHERE pt.podcastId = :podcastId
    """)
    fun getTagsForPodcast(podcastId: Long): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN podcast_tags pt ON t.id = pt.tagId
        WHERE pt.podcastId = :podcastId
    """)
    suspend fun getTagsForPodcastSync(podcastId: Long): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPodcastTag(podcastTag: PodcastTag)

    @Delete
    suspend fun deletePodcastTag(podcastTag: PodcastTag)

    @Query("DELETE FROM podcast_tags WHERE podcastId = :podcastId AND tagId = :tagId")
    suspend fun removePodcastTag(podcastId: Long, tagId: String)

    @Query("""
        SELECT bp.* FROM bookmarked_podcasts bp
        INNER JOIN podcast_tags pt ON bp.id = pt.podcastId
        WHERE pt.tagId = :tagId
        ORDER BY bp.bookmarkedAt DESC
    """)
    fun getBookmarkedPodcastsByTag(tagId: String): Flow<List<BookmarkedPodcast>>

    // ============ Cached Episodes ============

    @Query("SELECT * FROM cached_episodes WHERE podcastId = :podcastId ORDER BY publishedDate DESC")
    fun getEpisodesForPodcast(podcastId: Long): Flow<List<CachedEpisode>>

    @Query("SELECT * FROM cached_episodes WHERE podcastId = :podcastId ORDER BY publishedDate DESC")
    suspend fun getEpisodesForPodcastSync(podcastId: Long): List<CachedEpisode>

    @Query("SELECT * FROM cached_episodes WHERE podcastId = :podcastId ORDER BY publishedDate DESC LIMIT :limit")
    suspend fun getEpisodesForPodcastPaged(podcastId: Long, limit: Int): List<CachedEpisode>

    @Query("""
        SELECT * FROM cached_episodes
        WHERE podcastId = :podcastId AND publishedDate < :beforeTimestamp
        ORDER BY publishedDate DESC
        LIMIT :limit
    """)
    suspend fun getEpisodesBeforeTimestamp(podcastId: Long, beforeTimestamp: Long, limit: Int): List<CachedEpisode>

    @Query("SELECT * FROM cached_episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: Long): CachedEpisode?

    @Query("SELECT MIN(publishedDate) FROM cached_episodes WHERE podcastId = :podcastId")
    suspend fun getOldestEpisodeTimestamp(podcastId: Long): Long?

    @Query("SELECT MAX(publishedDate) FROM cached_episodes WHERE podcastId = :podcastId")
    suspend fun getNewestEpisodeTimestamp(podcastId: Long): Long?

    @Query("SELECT COUNT(*) FROM cached_episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodeCountForPodcast(podcastId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<CachedEpisode>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: CachedEpisode)

    @Query("DELETE FROM cached_episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Long)

    // ============ Episode Playback History ============

    @Query("SELECT * FROM episode_playback_history ORDER BY lastPlayedAt DESC")
    fun getAllPlaybackHistory(): Flow<List<EpisodePlaybackHistory>>

    @Query("SELECT * FROM episode_playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentPlaybackHistory(limit: Int): Flow<List<EpisodePlaybackHistory>>

    @Query("SELECT * FROM episode_playback_history WHERE episodeId = :episodeId")
    suspend fun getPlaybackHistoryForEpisode(episodeId: Long): EpisodePlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(history: EpisodePlaybackHistory)

    @Query("UPDATE episode_playback_history SET positionMs = :positionMs, lastPlayedAt = :lastPlayedAt WHERE episodeId = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long, lastPlayedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM episode_playback_history WHERE episodeId = :episodeId")
    suspend fun deletePlaybackHistory(episodeId: Long)

    // ============ Combined Queries ============

    @Transaction
    suspend fun bookmarkPodcastWithEpisodes(podcast: BookmarkedPodcast, episodes: List<CachedEpisode>) {
        insertBookmarkedPodcast(podcast)
        insertEpisodes(episodes)
    }
}
