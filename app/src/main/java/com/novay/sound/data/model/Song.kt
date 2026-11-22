package com.novay.sound.data.model

import com.novay.sound.data.db.entity.SongEntity
import kotlinx.serialization.Serializable

/** UI, arama sonuçları ve oynatıcı arasında taşınan sade şarkı modeli. */
@Serializable
data class Song(
    val id: String,              // YouTube videoId
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val artistUrl: String? = null,  // YouTube kanal linki (sanatçı sayfası)
    val streamUrl: String? = null   // radyo istasyonu yayın adresi
) {
    val isRadio: Boolean get() = id.startsWith("radio:")
    val youtubeUrl: String get() = "https://www.youtube.com/watch?v=$id"
    val isLocal: Boolean get() = id.startsWith("local:")
    val shareUrl: String get() = "https://music.youtube.com/watch?v=$id"
}

fun Song.toEntity() = SongEntity(
    id = id, title = title, artist = artist,
    thumbnailUrl = thumbnailUrl, durationSec = durationSec, artistUrl = artistUrl
)

fun SongEntity.toSong() = Song(
    id = id, title = title, artist = artist,
    thumbnailUrl = thumbnailUrl, durationSec = durationSec, artistUrl = artistUrl
)
