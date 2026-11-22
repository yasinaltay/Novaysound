package com.novay.sound.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.novay.sound.data.db.AppDatabase
import com.novay.sound.data.db.entity.PlaylistEntity
import com.novay.sound.data.db.entity.PlaylistSongCrossRef
import com.novay.sound.data.db.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val app: String = "NovaySound",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val songs: List<SongEntity>,
    val playlists: List<PlaylistEntity>,
    val playlistSongs: List<PlaylistSongCrossRef>
)

data class BackupSummary(val songs: Int, val playlists: Int, val favorites: Int)

/**
 * JSON yedekleme / geri yükleme. Uri'ler SAF ile alınır:
 * dışa aktar -> ActivityResultContracts.CreateDocument("application/json")
 * içe aktar  -> ActivityResultContracts.OpenDocument()
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val json: Json
) {
    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val file = BackupFile(
            songs = db.songDao().getAll(),
            playlists = db.playlistDao().getAllPlaylists(),
            playlistSongs = db.playlistDao().getAllRefs()
        )
        context.contentResolver.openOutputStream(uri, "wt")
            ?.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
            ?: error("Dosya yazılamadı")
        file.summary()
    }

    /** Mevcut kitaplığı SİLER ve yedektekiyle değiştirir. */
    suspend fun import(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().decodeToString() }
            ?: error("Dosya okunamadı")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.app == "NovaySound") { "Bu bir Novay Sound yedeği değil" }

        db.withTransaction {
            db.playlistDao().deleteAll()   // playlist_songs CASCADE ile silinir
            db.songDao().deleteAll()
            db.songDao().insertAll(file.songs)
            db.playlistDao().insertPlaylists(file.playlists)
            db.playlistDao().insertRefs(file.playlistSongs)
        }
        file.summary()
    }

    private fun BackupFile.summary() = BackupSummary(
        songs = songs.size,
        playlists = playlists.size,
        favorites = songs.count { it.likedAt != null }
    )
}
