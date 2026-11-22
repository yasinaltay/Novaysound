package com.novay.sound

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novay.sound.data.db.AppDatabase
import com.novay.sound.data.db.entity.PlayEventEntity
import com.novay.sound.data.model.Song
import com.novay.sound.di.NetworkModule
import com.novay.sound.playback.QueueStore
import com.novay.sound.playback.SavedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** v0.2'deki şema ile kurulmuş veritabanı v2'ye veri kaybı olmadan geçmeli (yoksa açılışta çöker). */
    @Test fun v1denV2yeGecis() {
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `songs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationSec` INTEGER NOT NULL, `likedAt` INTEGER, `lastPlayedAt` INTEGER, `playCount` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_lastPlayedAt` ON `songs` (`lastPlayedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_likedAt` ON `songs` (`likedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")
            db.execSQL("INSERT INTO songs VALUES ('abc', 'Git', 'Blok3', NULL, 159, 1000, 2000, 3, 500)")
            db.version = 1
        }

        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val song = room.songDao().getById("abc")!!
            assertEquals("Git", song.title)
            assertEquals(1000L, song.likedAt)
            assertNull(song.artistUrl)
            room.statsDao().insert(PlayEventEntity(songId = "abc", playedAt = 1, listenedMs = 40_000))
            assertEquals(1, room.statsDao().getAll().size)
        }
        room.close()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QueueStoreTest {
    @Test fun siraKaydedilipGeriOkunur() {
        val store = QueueStore(ApplicationProvider.getApplicationContext(), NetworkModule.provideJson())
        store.clear()
        assertNull(store.load())
        val q = SavedQueue(listOf(Song("a", "Bir", "X", null, 10), Song("radio:r", "FM", "Canlı", null, 0, streamUrl = "https://s")), 1, 4321)
        store.save(q)
        assertEquals(q, store.load())
    }
}
