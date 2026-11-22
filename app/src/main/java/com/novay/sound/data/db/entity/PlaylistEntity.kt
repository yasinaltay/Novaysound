package com.novay.sound.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Kitaplık listesi için: şarkı sayısı + ilk şarkının kapağı. */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
    val coverUrl: String?
)
