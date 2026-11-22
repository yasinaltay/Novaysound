package com.novay.sound.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.novay.sound.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SongDao {

    // ---- Yazma ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(song: SongEntity): Long

    @Query("UPDATE songs SET title = :title, artist = :artist, thumbnailUrl = :thumb, durationSec = :duration, artistUrl = COALESCE(:artistUrl, artistUrl) WHERE id = :id")
    protected abstract suspend fun updateMeta(id: String, title: String, artist: String, thumb: String?, duration: Long, artistUrl: String?)

    /** Şarkıyı ekler; varsa sadece başlık/kapak günceller (favori ve geçmiş korunur). */
    @Transaction
    open suspend fun upsertMeta(song: SongEntity) {
        if (insertIgnore(song) == -1L) {
            updateMeta(song.id, song.title, song.artist, song.thumbnailUrl, song.durationSec, song.artistUrl)
        }
    }

    @Query("UPDATE songs SET lastPlayedAt = :now, playCount = playCount + 1 WHERE id = :id")
    abstract suspend fun markPlayed(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET likedAt = :likedAt WHERE id = :id")
    abstract suspend fun setLiked(id: String, likedAt: Long?)

    @Query("UPDATE songs SET lastPlayedAt = NULL")
    abstract suspend fun clearHistory()

    // ---- Okuma (UI: Flow) ----
    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    abstract fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT likedAt IS NOT NULL FROM songs WHERE id = :id")
    abstract fun observeIsLiked(id: String): Flow<Boolean?>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    abstract fun observeMostPlayed(limit: Int = 20): Flow<List<SongEntity>>

    // ---- Okuma (Android Auto / servis: suspend) ----
    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract suspend fun getRecent(limit: Int = 50): List<SongEntity>

    @Query("SELECT * FROM songs WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    abstract suspend fun getFavorites(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun getById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun getByIds(ids: List<String>): List<SongEntity>

    /** Çevrimdışı arama (sesli komut yedeği). */
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' ORDER BY playCount DESC LIMIT :limit")
    abstract suspend fun searchLocal(q: String, limit: Int = 30): List<SongEntity>

    // ---- Yedekleme ----
    @Query("SELECT * FROM songs")
    abstract suspend fun getAll(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    abstract suspend fun deleteAll()
}
