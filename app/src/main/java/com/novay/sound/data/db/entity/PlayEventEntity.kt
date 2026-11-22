package com.novay.sound.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Her anlamlı dinleme (30 sn+ veya şarkının yarısı) bir kayıt. */
@Serializable
@Entity(tableName = "play_events", indices = [Index("songId"), Index("playedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
    val listenedMs: Long
)

data class SongStat(@Embedded val song: SongEntity, val plays: Int, val ms: Long)
data class ArtistStat(val artist: String, val plays: Int, val ms: Long, val thumb: String?, val artistUrl: String?)
