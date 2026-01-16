package com.podcapture.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.AudioFileTag
import com.podcapture.data.model.Capture
import com.podcapture.data.model.CaptureTag
import com.podcapture.data.model.Tag

@Database(
    entities = [AudioFile::class, Capture::class, Tag::class, AudioFileTag::class, CaptureTag::class],
    version = 4,
    exportSchema = true
)
abstract class PodCaptureDatabase : RoomDatabase() {

    abstract fun audioFileDao(): AudioFileDao
    abstract fun captureDao(): CaptureDao
    abstract fun tagDao(): TagDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
