package com.novay.sound.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.novay.sound.data.db.dao.PlaylistDao
import com.novay.sound.data.db.dao.SongDao
import com.novay.sound.data.db.entity.PlaylistEntity
import com.novay.sound.data.db.entity.PlaylistSongCrossRef
import com.novay.sound.data.db.entity.SongEntity

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "novay_sound.db"
    }
}
