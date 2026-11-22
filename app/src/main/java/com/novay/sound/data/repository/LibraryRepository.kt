package com.novay.sound.data.repository

import com.novay.sound.data.db.dao.PlaylistDao
import com.novay.sound.data.db.dao.SongDao
import com.novay.sound.data.db.entity.PlaylistEntity
import com.novay.sound.data.db.entity.PlaylistWithCount
import com.novay.sound.data.model.Song
import com.novay.sound.data.model.toEntity
import com.novay.sound.data.model.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Kitaplık işlemleri için tek giriş noktası (UI + PlaybackService + Android Auto). */
@Singleton
class LibraryRepository @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {
    // ---- Akışlar (Compose) ----
    val recent: Flow<List<Song>> = songDao.observeRecent().map { it.map { e -> e.toSong() } }
    val favorites: Flow<List<Song>> = songDao.observeFavorites().map { it.map { e -> e.toSong() } }
    val mostPlayed: Flow<List<Song>> = songDao.observeMostPlayed().map { it.map { e -> e.toSong() } }
    val playlists: Flow<List<PlaylistWithCount>> = playlistDao.observePlaylists()

    fun isLiked(id: String): Flow<Boolean> = songDao.observeIsLiked(id).map { it == true }
    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observeSongs(playlistId).map { it.map { e -> e.toSong() } }

    // ---- Şarkı ----
    suspend fun onPlayed(song: Song) {
        songDao.upsertMeta(song.toEntity())
        songDao.markPlayed(song.id)
    }

    suspend fun setLiked(song: Song, liked: Boolean) {
        songDao.upsertMeta(song.toEntity())
        songDao.setLiked(song.id, if (liked) System.currentTimeMillis() else null)
    }

    suspend fun toggleLike(song: Song) {
        val current = songDao.getById(song.id)?.likedAt != null
        setLiked(song, !current)
    }

    suspend fun clearHistory() = songDao.clearHistory()

    // ---- Çalma listesi ----
    suspend fun createPlaylist(name: String): Long =
        playlistDao.create(PlaylistEntity(name = name.trim()))

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name.trim())
    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)

    suspend fun addToPlaylist(playlistId: Long, song: Song): Boolean {
        songDao.upsertMeta(song.toEntity())
        return playlistDao.addSong(playlistId, song.id)
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        playlistDao.removeSong(playlistId, songId)

    suspend fun movePlaylistSong(playlistId: Long, from: Int, to: Int) =
        playlistDao.move(playlistId, from, to)

    // ---- Android Auto / servis için anlık okumalar ----
    suspend fun getRecent(): List<Song> = songDao.getRecent().map { it.toSong() }
    suspend fun getFavorites(): List<Song> = songDao.getFavorites().map { it.toSong() }
    suspend fun getPlaylists(): List<PlaylistWithCount> = playlistDao.getPlaylists()
    suspend fun getPlaylistSongs(id: Long): List<Song> = playlistDao.getSongs(id).map { it.toSong() }
    suspend fun getSong(id: String): Song? = songDao.getById(id)?.toSong()
    suspend fun getSongs(ids: List<String>): List<Song> {
        val byId = songDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toSong() } // istenen sırayı koru
    }
    suspend fun searchLocal(query: String): List<Song> = songDao.searchLocal(query).map { it.toSong() }
}
