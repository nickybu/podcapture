package com.podcapture.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.AudioFileTag
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.Capture
import com.podcapture.data.model.CaptureTag
import com.podcapture.data.model.EpisodePlaybackHistory
import com.podcapture.data.model.PodcastTag
import com.podcapture.data.model.Tag

@Database(
    entities = [
        AudioFile::class,
        Capture::class,
        Tag::class,
        AudioFileTag::class,
        CaptureTag::class,
        BookmarkedPodcast::class,
        CachedEpisode::class,
        EpisodePlaybackHistory::class,
        PodcastTag::class
    ],
    version = 8,
    exportSchema = true
)
abstract class PodCaptureDatabase : RoomDatabase() {

    abstract fun audioFileDao(): AudioFileDao
    abstract fun captureDao(): CaptureDao
    abstract fun tagDao(): TagDao
    abstract fun podcastDao(): PodcastDao

    companion object {
        private const val DATABASE_NAME = "podcapture.db"

        @Volatile
        private var INSTANCE: PodCaptureDatabase? = null

        // Migration from version 1 to 2: Add firstPlayedAt column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE audio_files ADD COLUMN firstPlayedAt INTEGER DEFAULT NULL"
                )
            }
        }

        // Migration from version 2 to 3: Add notes column to captures
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE captures ADD COLUMN notes TEXT DEFAULT NULL"
                )
            }
        }

        // Migration from version 3 to 4: Add tags tables
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create tags table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL DEFAULT ${0xFF6750A4}
                    )
                """)

                // Create audio_file_tags junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS audio_file_tags (
                        audioFileId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(audioFileId, tagId),
                        FOREIGN KEY(audioFileId) REFERENCES audio_files(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_audio_file_tags_audioFileId ON audio_file_tags(audioFileId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_audio_file_tags_tagId ON audio_file_tags(tagId)")

                // Create capture_tags junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS capture_tags (
                        captureId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(captureId, tagId),
                        FOREIGN KEY(captureId) REFERENCES captures(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_capture_tags_captureId ON capture_tags(captureId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_capture_tags_tagId ON capture_tags(tagId)")
            }
        }

        // Migration from version 4 to 5: Add podcast tables
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create bookmarked_podcasts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookmarked_podcasts (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        description TEXT NOT NULL,
                        artworkUrl TEXT NOT NULL,
                        feedUrl TEXT NOT NULL,
                        language TEXT NOT NULL DEFAULT 'en',
                        episodeCount INTEGER NOT NULL DEFAULT 0,
                        lastUpdateTime INTEGER NOT NULL DEFAULT 0,
                        bookmarkedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create cached_episodes table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_episodes (
                        id INTEGER NOT NULL PRIMARY KEY,
                        podcastId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        publishedDate INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        audioUrl TEXT NOT NULL,
                        audioType TEXT NOT NULL DEFAULT 'audio/mpeg',
                        audioSize INTEGER NOT NULL DEFAULT 0,
                        imageUrl TEXT NOT NULL DEFAULT '',
                        chaptersUrl TEXT,
                        transcriptUrl TEXT,
                        cachedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(podcastId) REFERENCES bookmarked_podcasts(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_episodes_podcastId ON cached_episodes(podcastId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_episodes_publishedDate ON cached_episodes(publishedDate)")

                // Create episode_playback_history table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS episode_playback_history (
                        episodeId INTEGER NOT NULL PRIMARY KEY,
                        podcastId INTEGER NOT NULL,
                        podcastTitle TEXT NOT NULL,
                        podcastArtworkUrl TEXT NOT NULL,
                        episodeTitle TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        positionMs INTEGER NOT NULL DEFAULT 0,
                        firstPlayedAt INTEGER NOT NULL DEFAULT 0,
                        lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                        localFilePath TEXT,
                        FOREIGN KEY(episodeId) REFERENCES cached_episodes(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_playback_history_episodeId ON episode_playback_history(episodeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_playback_history_lastPlayedAt ON episode_playback_history(lastPlayedAt)")

                // Create podcast_tags junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS podcast_tags (
                        podcastId INTEGER NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(podcastId, tagId),
                        FOREIGN KEY(podcastId) REFERENCES bookmarked_podcasts(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_podcast_tags_podcastId ON podcast_tags(podcastId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_podcast_tags_tagId ON podcast_tags(tagId)")
            }
        }

        // Migration from version 5 to 6: Add isBookmarked and bookmarkedAt to audio_files
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE audio_files ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE audio_files ADD COLUMN bookmarkedAt INTEGER DEFAULT NULL"
                )
            }
        }

        // Migration from version 6 to 7: Remove foreign key from captures table
        // (allows audioFileId to reference either AudioFile.id or "episode_{episodeId}")
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new table without foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS captures_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        audioFileId TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        windowStartMs INTEGER NOT NULL,
                        windowEndMs INTEGER NOT NULL,
                        transcription TEXT NOT NULL,
                        notes TEXT DEFAULT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                // Copy data
                database.execSQL("""
                    INSERT INTO captures_new (id, audioFileId, timestampMs, windowStartMs, windowEndMs, transcription, notes, createdAt)
                    SELECT id, audioFileId, timestampMs, windowStartMs, windowEndMs, transcription, notes, createdAt
                    FROM captures
                """)
                // Drop old table
                database.execSQL("DROP TABLE captures")
                // Rename new table
                database.execSQL("ALTER TABLE captures_new RENAME TO captures")
                // Recreate index
                database.execSQL("CREATE INDEX IF NOT EXISTS index_captures_audioFileId ON captures(audioFileId)")
            }
        }

        // Migration from version 7 to 8: Add link column to cached_episodes
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE cached_episodes ADD COLUMN link TEXT DEFAULT NULL"
                )
            }
        }

        fun getInstance(context: Context): PodCaptureDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): PodCaptureDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PodCaptureDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
        }
    }
}
