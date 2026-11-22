package com.novay.sound.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.novay.sound.data.db.entity.PlaylistEntity
import com.novay.sound.data.db.entity.PlaylistSongCrossRef
import com.novay.sound.data.db.entity.PlaylistWithCount
import com.novay.sound.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    private companion object {
        const val WITH_COUNT = """
            SELECT p.*, COUNT(ps.songId) AS songCount,
              (SELECT s.thumbnailUrl FROM playlist_songs ps2
               JOIN songs s ON s.id = ps2.songId
               WHERE ps2.playlistId = p.id ORDER BY ps2.position LIMIT 1) AS coverUrl
            FROM playlists p
            LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
            GROUP BY p.id ORDER BY p.updatedAt DESC
        """
        const val SONGS_OF = """
            SELECT s.* FROM songs s
            JOIN playlist_songs ps ON ps.songId = s.id
            WHERE ps.playlistId = :playlistId ORDER BY ps.position
        """
    }

    // ---- Çalma listesi ----
    @Insert
    abstract suspend fun create(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    protected abstract suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    @Query(WITH_COUNT)
    abstract fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query(WITH_COUNT)
    abstract suspend fun getPlaylists(): List<PlaylistWithCount>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract suspend fun getById(id: Long): PlaylistEntity?

    // ---- Liste içindeki şarkılar ----
    @Query(SONGS_OF)
    abstract fun observeSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query(SONGS_OF)
    abstract suspend fun getSongs(playlistId: Long): List<SongEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    protected abstract suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRef(ref: PlaylistSongCrossRef): Long

    /** Şarkıyı listenin sonuna ekler (şarkı önceden songs tablosunda olmalı). */
    @Transaction
    open suspend fun addSong(playlistId: Long, songId: String): Boolean {
        val added = insertRef(PlaylistSongCrossRef(playlistId, songId, nextPosition(playlistId))) != -1L
        if (added) touch(playlistId)
        return added
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    protected abstract suspend fun deleteRef(playlistId: Long, songId: String)

    @Transaction
    open suspend fun removeSong(playlistId: Long, songId: String) {
        deleteRef(playlistId, songId)
        touch(playlistId)
    }

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    protected abstract suspend fun songIds(playlistId: Long): List<String>

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    protected abstract suspend fun setPosition(playlistId: Long, songId: String, position: Int)

    /** Sürükle-bırak sıralama. */
    @Transaction
    open suspend fun move(playlistId: Long, from: Int, to: Int) {
        val ids = songIds(playlistId).toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        ids.forEachIndexed { i, id -> setPosition(playlistId, id, i) }
        touch(playlistId)
    }

    // ---- Yedekleme ----
    @Query("SELECT * FROM playlists")
    abstract suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs")
    abstract suspend fun getAllRefs(): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRefs(refs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlists")
    abstract suspend fun deleteAll()
}
