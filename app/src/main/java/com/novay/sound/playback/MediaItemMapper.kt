package com.novay.sound.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.novay.sound.data.model.Song

private const val EXTRA_DURATION = "novay.duration"
private const val EXTRA_ARTIST_URL = "novay.artistUrl"
private const val EXTRA_ARTIST = "novay.artist"
private const val EXTRA_STREAM = "novay.stream"

/**
 * Şarkı -> MediaItem. URI sadece yer tutucu; gerçek ses URL'si oynatma anında
 * ResolvingDataSource'ta çözülür. Önbellek anahtarı = videoId (URL değişse de sabit).
 * [parent] verilirse mediaId "parent/videoId" olur (Android Auto listeleri için).
 */
fun Song.toMediaItem(parent: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(if (parent == null) id else "$parent/$id")
        .setUri(
            when {
                isRadio -> streamUrl.orEmpty()
                isLocal -> "content://media/external/audio/media/${id.removePrefix("local:")}"
                else -> "https://youtu.be/$id"
            }
        )
        .apply { if (isRadio && streamUrl?.contains("m3u8", ignoreCase = true) == true) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }
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
                .setMediaType(if (isRadio) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putLong(EXTRA_DURATION, durationSec)
                    putString(EXTRA_ARTIST_URL, artistUrl)
                    putString(EXTRA_ARTIST, artist)
                    putString(EXTRA_STREAM, streamUrl)
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
        artist = mediaMetadata.extras?.getString(EXTRA_ARTIST) ?: mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
        artistUrl = mediaMetadata.extras?.getString(EXTRA_ARTIST_URL),
        streamUrl = mediaMetadata.extras?.getString(EXTRA_STREAM)
    )
}
