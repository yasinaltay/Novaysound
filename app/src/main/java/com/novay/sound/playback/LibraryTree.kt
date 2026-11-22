package com.novay.sound.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.di.PlaybackCache
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.SmartPlaylists
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
    private val downloads: DownloadTracker,
    private val youtube: com.novay.sound.data.youtube.YouTubeRepository,
    private val smart: SmartPlaylists
) {
    companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val OFFLINE = "offline"
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
        const val PLAYLIST_PREFIX = "playlist:"
        const val FOR_YOU = "foryou"
        const val TRENDING = "trending"
        const val TAB_DISCOVER = "tab_discover"
        const val TAB_LIBRARY = "tab_library"
        const val SMART_PREFIX = "smart:"
    }

    val root: MediaItem = folder(ROOT, "Novay Sound")

    /** Android Auto kökte en fazla 4 sekme gösterir. */
    private val categories = listOf(
        folder(TAB_DISCOVER, "Keşfet", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(TAB_LIBRARY, "Kitaplık", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(PLAYLISTS, "Listeler", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, gridChildren = true),
        folder(OFFLINE, "Çevrimdışı", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
    )

    private val discoverFolders = listOf(
        folder(FOR_YOU, "Senin için (radyo)"),
        folder(SMART_PREFIX + SmartList.DISCOVER.name, SmartList.DISCOVER.title),
        folder(TRENDING, "Türkiye'de trend")
    )

    private val libraryFolders = listOf(
        folder(FAVORITES, "Favoriler"),
        folder(RECENT, "Son çalınanlar"),
        folder(SMART_PREFIX + SmartList.MONTH_TOP.name, SmartList.MONTH_TOP.title),
        folder(SMART_PREFIX + SmartList.FORGOTTEN.name, SmartList.FORGOTTEN.title)
    )

    @Volatile private var forYouCache: Pair<Long, List<Song>>? = null

    /** Son dinlenen şarkıdan radyo (30 dk önbellek). */
    private suspend fun forYou(): List<Song> {
        forYouCache?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000 }?.let { return it.second }
        val seed = library.getRecent().firstOrNull() ?: return library.getFavorites().shuffled().take(30)
        val songs = runCatching { youtube.radio(seed.id) }.getOrDefault(emptyList()).take(40)
        if (songs.isNotEmpty()) forYouCache = System.currentTimeMillis() to songs
        return songs.ifEmpty { library.getRecent() }
    }

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> categories
        TAB_DISCOVER -> discoverFolders
        TAB_LIBRARY -> libraryFolders
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
        parentId == FOR_YOU -> forYou()
        parentId == TRENDING -> runCatching { youtube.trending() }.getOrDefault(emptyList())
        parentId.startsWith(SMART_PREFIX) ->
            runCatching { smart.songs(SmartList.valueOf(parentId.removePrefix(SMART_PREFIX))) }.getOrDefault(emptyList())
        parentId.startsWith(PLAYLIST_PREFIX) ->
            parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
                ?.let { library.getPlaylistSongs(it) }.orEmpty()
        else -> emptyList()
    }

    suspend fun item(mediaId: String): MediaItem? {
        (categories + discoverFolders + libraryFolders + root).firstOrNull { it.mediaId == mediaId }?.let { return it }
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
        artwork: String? = null,
        gridChildren: Boolean = false
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
                .apply {
                    if (gridChildren) setExtras(android.os.Bundle().apply {
                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // ızgara
                    })
                }
                .build()
        )
        .build()
}
