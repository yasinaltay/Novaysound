package com.novay.sound.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.novay.sound.data.db.dao.StatsDao
import com.novay.sound.data.db.entity.PlayEventEntity
import com.novay.sound.data.db.dao.PlaylistDao
import com.novay.sound.data.db.dao.SongDao
import com.novay.sound.data.db.entity.PlaylistEntity
import com.novay.sound.data.db.entity.PlaylistSongCrossRef
import com.novay.sound.data.db.entity.SongEntity

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, PlayEventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "novay_sound.db"

        /** v1 -> v2: sanatçı linki + dinleme istatistikleri. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN artistUrl TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS play_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId TEXT NOT NULL, playedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_songId ON play_events(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_playedAt ON play_events(playedAt)")
            }
        }
    }
}
