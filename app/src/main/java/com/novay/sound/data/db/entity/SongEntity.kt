package com.novay.sound.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Tek şarkı tablosu. Favori = likedAt dolu, Son Çalınan = lastPlayedAt dolu.
 * id = YouTube videoId (ör. "dQw4w9WgXcQ").
 */
@Serializable
@Entity(
    tableName = "songs",
    indices = [Index("lastPlayedAt"), Index("likedAt")]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val likedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val artistUrl: String? = null
)
