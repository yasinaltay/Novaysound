package com.novay.sound.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.novay.sound.data.model.Song

private const val EXTRA_DURATION = "novay.duration"
private const val EXTRA_ARTIST_URL = "novay.artistUrl"

/**
 * Şarkı -> MediaItem. URI sadece yer tutucu; gerçek ses URL'si oynatma anında
 * ResolvingDataSource'ta çözülür. Önbellek anahtarı = videoId (URL değişse de sabit).
 * [parent] verilirse mediaId "parent/videoId" olur (Android Auto listeleri için).
 */
fun Song.toMediaItem(parent: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(if (parent == null) id else "$parent/$id")
        .setUri("https://youtu.be/$id")
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setArtworkUri(thumbnailUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putLong(EXTRA_DURATION, durationSec)
                    putString(EXTRA_ARTIST_URL, artistUrl)
                })
                .build()
        )
        .build()

/** mediaId'nin son parçası her zaman YouTube videoId'sidir. */
val MediaItem.videoId: String get() = mediaId.substringAfterLast('/')

/** MediaItem içinde başlık varsa Song'a çevir, yoksa null (DB/YouTube'dan doldurulur). */
fun MediaItem.toSongOrNull(): Song? {
    val title = mediaMetadata.title?.toString() ?: return null
    return Song(
        id = videoId,
        title = title,
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
        artistUrl = mediaMetadata.extras?.getString(EXTRA_ARTIST_URL)
    )
}
