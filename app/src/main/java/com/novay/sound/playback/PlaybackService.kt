package com.novay.sound.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.YouTubeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var dataSourceFactory: AudioDataSourceFactory
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var youtube: YouTubeRepository
    @Inject lateinit var tree: LibraryTree

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    /** Kuyruk bitmek üzereyken benzer şarkıları otomatik ekle (sonsuz radyo). */
    var autoRadio = true
    private var radioJob: Job? = null
    private val retryCount = mutableMapOf<String, Int>()

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory.create()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true   // arama/navigasyon gelince duraklat/kıs
            )
            .setHandleAudioBecomingNoisy(true)  // Bluetooth kopunca / kulaklık çıkınca duraklat
            .setWakeMode(C.WAKE_MODE_NETWORK)   // ekran kilitliyken CPU + Wi-Fi uyanık
            .build()
            .apply { addListener(PlayerListener()) }

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .apply { openApp?.let { setSessionActivity(it) } }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Uygulama son kullanılanlardan kaydırılınca: çalıyorsa devam, değilse kapat. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ======================= Oynatıcı olayları =======================

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.toSongOrNull()?.let { song -> scope.launch { library.onPlayed(song) } }
            maybeExtendRadio()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.videoId ?: return
            val tries = retryCount.getOrDefault(id, 0)
            val http = generateSequence(error.cause) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()

            when {
                // Süresi dolmuş/engellenmiş URL -> yeniden çöz ve kaldığı yerden devam
                tries < 2 && (http?.responseCode == 403 || http?.responseCode == 410) -> {
                    retryCount[id] = tries + 1
                    youtube.invalidate(id)
                    player.prepare()
                    player.play()
                }
                // Çözülemeyen şarkıyı atla
                player.hasNextMediaItem() -> {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun maybeExtendRadio() {
        if (!autoRadio || radioJob?.isActive == true) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount >= 300) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > 2) return
        val seed = player.currentMediaItem?.videoId ?: return

        radioJob = scope.launch {
            val existing = (0 until player.mediaItemCount)
                .map { player.getMediaItemAt(it).videoId }.toSet()
            val more = runCatching { youtube.radio(seed) }.getOrDefault(emptyList())
                .filter { it.id !in existing }
                .take(25)
            if (more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    // ======================= Android Auto / Controller =======================

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /** Kök + Android Auto ipuçları (arama desteği, liste/ızgara görünümü). */
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // liste
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)  // liste
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                LibraryResult.ofItem(tree.root, LibraryParams.Builder().setExtras(extras).build())
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = tree.children(parentId)
            val paged = if (pageSize in 1 until Int.MAX_VALUE) {
                all.drop(page * pageSize).take(pageSize)
            } else all
            LibraryResult.ofItemList(paged, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        // ---- Android Auto arama çubuğu ----
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            session.notifySearchResultChanged(browser, query, 20, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(searchSongs(query).map { it.toMediaItem() }, params)
        }

        // ---- Kuyruğa ekleme (telefon arayüzü, Auto, sesli komut) ----
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            if (query != null) playFromSearch(query).mediaItems.toMutableList()
            else resolve(mediaItems).toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            if (single != null) {
                // Sesli komut: "Ok Google, Novay Sound'da Sezen Aksu çal"
                val query = single.requestMetadata.searchQuery
                if (query != null || single.mediaId.isEmpty()) {
                    return@future playFromSearch(query.orEmpty())
                }
                // Android Auto'da listeden şarkı seçildi -> tüm listeyi kuyruğa al
                val parent = single.mediaId.substringBeforeLast('/', "")
                if (parent.isNotEmpty()) {
                    val songs = tree.songsOf(parent)
                    val index = songs.indexOfFirst { it.id == single.videoId }
                    if (index >= 0) {
                        return@future MediaItemsWithStartPosition(
                            songs.map { it.toMediaItem() }, index, C.TIME_UNSET
                        )
                    }
                }
            }
            MediaItemsWithStartPosition(resolve(mediaItems), startIndex, startPositionMs)
        }

        /** Bluetooth/araçta "oynat" tuşu, uygulama kapalıyken: son çalınanlardan devam. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val recent = library.getRecent().take(50)
            MediaItemsWithStartPosition(recent.map { it.toMediaItem() }, 0, C.TIME_UNSET)
        }
    }

    // ======================= Yardımcılar =======================

    /**
     * Controller'dan gelen MediaItem'lar URI'siz gelir (güvenlik gereği Media3 siler).
     * Başlık varsa ondan, yoksa DB'den, o da yoksa YouTube'dan yeniden kurulur.
     */
    private suspend fun resolve(items: List<MediaItem>): List<MediaItem> = items.mapNotNull { item ->
        val song = item.toSongOrNull()
            ?: library.getSong(item.videoId)
            ?: runCatching { youtube.getSong(item.videoId) }.getOrNull()
        song?.toMediaItem()
    }

    private suspend fun searchSongs(query: String): List<Song> =
        runCatching { youtube.search(query).songs }
            .getOrElse { library.searchLocal(query) } // internet yoksa kitaplıkta ara
            .ifEmpty { library.searchLocal(query) }

    /** Boş komut ("müzik çal") -> karışık favoriler, yoksa son çalınanlar. */
    private suspend fun playFromSearch(query: String): MediaItemsWithStartPosition {
        val songs = if (query.isBlank()) {
            library.getFavorites().shuffled().ifEmpty { library.getRecent() }
        } else searchSongs(query)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
    }
}
