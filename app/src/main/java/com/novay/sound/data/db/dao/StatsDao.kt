package com.novay.sound.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.novay.sound.data.db.entity.ArtistStat
import com.novay.sound.data.db.entity.PlayEventEntity
import com.novay.sound.data.db.entity.SongStat
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Insert suspend fun insert(event: PlayEventEntity)

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events WHERE playedAt >= :since")
    fun totalListened(since: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_events WHERE playedAt >= :since")
    fun totalPlays(since: Long): Flow<Int>

    @Query("""
        SELECT s.*, COUNT(e.id) AS plays, SUM(e.listenedMs) AS ms FROM play_events e
        JOIN songs s ON s.id = e.songId WHERE e.playedAt >= :since
        GROUP BY e.songId ORDER BY plays DESC, ms DESC LIMIT :limit
    """)
    fun topSongs(since: Long, limit: Int = 20): Flow<List<SongStat>>

    @Query("""
        SELECT s.artist AS artist, COUNT(e.id) AS plays, SUM(e.listenedMs) AS ms,
               MAX(s.thumbnailUrl) AS thumb, MAX(s.artistUrl) AS artistUrl
        FROM play_events e JOIN songs s ON s.id = e.songId
        WHERE e.playedAt >= :since AND s.artist != ''
        GROUP BY s.artist ORDER BY plays DESC LIMIT :limit
    """)
    fun topArtists(since: Long, limit: Int = 10): Flow<List<ArtistStat>>

    @Query("SELECT * FROM play_events") suspend fun getAll(): List<PlayEventEntity>
    @Insert suspend fun insertAll(events: List<PlayEventEntity>)
    @Query("DELETE FROM play_events") suspend fun deleteAll()
}
