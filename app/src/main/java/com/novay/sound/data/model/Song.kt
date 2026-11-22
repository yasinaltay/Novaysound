package com.novay.sound.data.model

import com.novay.sound.data.db.entity.SongEntity

/** UI, arama sonuçları ve oynatıcı arasında taşınan sade şarkı modeli. */
data class Song(
    val id: String,              // YouTube videoId
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long
) {
    val youtubeUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

fun Song.toEntity() = SongEntity(
    id = id, title = title, artist = artist,
    thumbnailUrl = thumbnailUrl, durationSec = durationSec
)

fun SongEntity.toSong() = Song(
    id = id, title = title, artist = artist,
    thumbnailUrl = thumbnailUrl, durationSec = durationSec
)
