package com.novay.sound.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.di.PlaybackCache
import com.novay.sound.download.DownloadTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Auto gezinme ağacı:
 *   ROOT
 *    ├─ Son Çalınanlar   (recent)
 *    ├─ Çevrimdışı       (offline)  -> önbellekte tamamı olan şarkılar
 *    ├─ Çalma Listeleri  (playlists) -> playlist:<id> -> şarkılar
 *    └─ Favoriler        (favorites)
 */
@OptIn(UnstableApi::class)
@Singleton
class LibraryTree @Inject constructor(
    private val library: LibraryRepository,
    @PlaybackCache private val cache: SimpleCache,
    private val downloads: DownloadTracker
) {
    companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val OFFLINE = "offline"
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
        const val PLAYLIST_PREFIX = "playlist:"
    }

    val root: MediaItem = folder(ROOT, "Novay Sound")

    private val categories = listOf(
        folder(RECENT, "Son Çalınanlar", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(OFFLINE, "Çevrimdışı", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(PLAYLISTS, "Çalma Listeleri", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folder(FAVORITES, "Favoriler", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
    )

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> categories
        PLAYLISTS -> library.getPlaylists().map {
            folder(
                id = PLAYLIST_PREFIX + it.playlist.id,
                title = it.playlist.name,
                type = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                subtitle = "${it.songCount} şarkı",
                artwork = it.coverUrl
            )
        }
        else -> songsOf(parentId).map { it.toMediaItem(parent = parentId) }
    }

    /** Bir klasörün şarkıları (onSetMediaItems'ta tüm listeyi kuyruğa almak için de kullanılır). */
    suspend fun songsOf(parentId: String): List<Song> = when {
        parentId == RECENT -> library.getRecent()
        parentId == FAVORITES -> library.getFavorites()
        parentId == OFFLINE -> offlineSongs()
        parentId.startsWith(PLAYLIST_PREFIX) ->
            parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
                ?.let { library.getPlaylistSongs(it) }.orEmpty()
        else -> emptyList()
    }

    suspend fun item(mediaId: String): MediaItem? {
        (categories + root).firstOrNull { it.mediaId == mediaId }?.let { return it }
        return library.getSong(mediaId.substringAfterLast('/'))?.toMediaItem()
    }

    /** Önce indirilenler, sonra önbellekte tamamı olanlar. */
    private suspend fun offlineSongs(): List<Song> {
        val downloaded = downloads.completedSongs()
        val ids = withContext(Dispatchers.IO) { cache.keys.filter { cache.isFullyCached(it) } }
        val cached = library.getSongs(ids)
        return (downloaded + cached).distinctBy { it.id }
    }

    suspend fun cachedOnlySongs(): List<Song> {
        val ids = withContext(Dispatchers.IO) { cache.keys.filter { cache.isFullyCached(it) } }
        val dl = downloads.completedSongs().map { it.id }.toSet()
        return library.getSongs(ids).filter { it.id !in dl }
    }

    private fun folder(
        id: String,
        title: String,
        type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        subtitle: String? = null,
        artwork: String? = null
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork?.let(android.net.Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build()
        )
        .build()
}
