package com.novay.sound.data.youtube

import android.net.Uri
import com.novay.sound.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchFilter(val value: String) {
    SONGS(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),   // YouTube Music şarkıları
    VIDEOS(YoutubeSearchQueryHandlerFactory.VIDEOS)        // normal YouTube videoları
}

data class SearchPage(
    val songs: List<Song>,
    internal val handler: SearchQueryHandler,
    internal val nextPage: Page?
) {
    val hasMore: Boolean get() = Page.isValid(nextPage)
}

sealed interface YouTubeLink {
    data class Video(val id: String) : YouTubeLink
    data class Playlist(val url: String) : YouTubeLink
}

data class AudioSource(val url: String, val mimeType: String?, val bitrate: Int, val expiresAt: Long)

/** YouTube erişiminin tamamı (NewPipeExtractor). Tüm fonksiyonlar IO thread'inde çalışır. */
@Singleton
class YouTubeRepository @Inject constructor() {

    private val service get() = ServiceList.YouTube
    private val urlCache = ConcurrentHashMap<String, AudioSource>()

    // ---------------- Arama ----------------

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): SearchPage =
        withContext(Dispatchers.IO) {
            val handler = service.searchQHFactory.fromQuery(query, listOf(filter.value), "")
            val info = SearchInfo.getInfo(service, handler)
            val songs = info.relatedItems.toSongs()
            // Müzik filtresi boş dönerse normal video aramasına düş
            if (songs.isEmpty() && filter == SearchFilter.SONGS) search(query, SearchFilter.VIDEOS)
            else SearchPage(songs, handler, info.nextPage)
        }

    suspend fun searchMore(page: SearchPage): SearchPage = withContext(Dispatchers.IO) {
        val more = SearchInfo.getMoreItems(service, page.handler, page.nextPage)
        SearchPage(more.items.toSongs(), page.handler, more.nextPage)
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    // ---------------- Ses akışı ----------------

    /** En yüksek kaliteli ses URL'si (önce önbellek; YouTube URL'leri ~6 saat geçerli). */
    suspend fun resolveAudio(videoId: String): AudioSource = withContext(Dispatchers.IO) {
        urlCache[videoId]?.takeIf { it.expiresAt > System.currentTimeMillis() + 60_000 }
            ?.let { return@withContext it }

        val info = StreamInfo.getInfo(service, videoUrl(videoId))
        val best = pickBestAudio(info.audioStreams)
            ?: error("Bu video için ses akışı bulunamadı")
        val source = AudioSource(
            url = best.content,
            mimeType = best.format?.mimeType,
            bitrate = best.averageBitrate.takeIf { it > 0 } ?: best.bitrate,
            expiresAt = parseExpire(best.content)
        )
        urlCache[videoId] = source
        source
    }

    /** Süresi dolan (403) URL'yi unut, bir sonraki istekte yeniden çözülsün. */
    fun invalidate(videoId: String) { urlCache.remove(videoId) }

    private fun pickBestAudio(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Dublaj/çeviri ses izlerini ele, orijinali tercih et
        val original = progressive.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { progressive }
        return original.maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
    }

    private fun parseExpire(url: String): Long =
        runCatching { Uri.parse(url).getQueryParameter("expire")!!.toLong() * 1000 }
            .getOrElse { System.currentTimeMillis() + 5 * 60 * 60 * 1000L }

    // ---------------- Şarkı bilgisi / radyo ----------------

    suspend fun getSong(videoId: String): Song = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, videoUrl(videoId))
        Song(
            id = videoId,
            title = info.name,
            artist = cleanArtist(info.uploaderName),
            thumbnailUrl = bestThumb(info.thumbnails, videoId),
            durationSec = info.duration.coerceAtLeast(0)
        )
    }

    /**
     * "Benzer şarkılar" radyosu: önce YouTube Music mix'i (RDAMVM),
     * olmazsa videonun önerilen (related) listesi.
     */
    suspend fun radio(seedId: String): List<Song> = withContext(Dispatchers.IO) {
        val mix = runCatching {
            PlaylistInfo.getInfo(service, "https://www.youtube.com/watch?v=$seedId&list=RDAMVM$seedId")
                .relatedItems.toSongs()
        }.getOrDefault(emptyList())
        mix.filter { it.id != seedId }.ifEmpty {
            runCatching {
                StreamInfo.getInfo(service, videoUrl(seedId)).relatedItems.toSongs()
            }.getOrDefault(emptyList())
        }
    }

    /** Yapıştırılan YouTube çalma listesi linkini şarkılara çevirir (en fazla [limit]). */
    suspend fun getPlaylist(url: String, limit: Int = 300): Pair<String, List<Song>> =
        withContext(Dispatchers.IO) {
            val info = PlaylistInfo.getInfo(service, url)
            val songs = info.relatedItems.toSongs().toMutableList()
            var next = info.nextPage
            while (Page.isValid(next) && songs.size < limit) {
                val more = PlaylistInfo.getMoreItems(service, url, next)
                songs += more.items.toSongs()
                next = more.nextPage
            }
            info.name to songs.take(limit)
        }

    // ---------------- Link ayrıştırma ----------------

    /** Metin içindeki YouTube / YouTube Music linkini bulur. */
    fun parseLink(text: String): YouTubeLink? {
        val url = Regex("""https?://\S+""").find(text)?.value ?: return null
        val isPlaylist = url.contains("list=") && !url.contains("list=RD") &&
            runCatching { service.playlistLHFactory.acceptUrl(url) }.getOrDefault(false)
        if (isPlaylist && !url.contains("v=")) return YouTubeLink.Playlist(url)
        val id = runCatching {
            if (service.streamLHFactory.acceptUrl(url)) service.streamLHFactory.getId(url) else null
        }.getOrNull()
        return when {
            id != null -> YouTubeLink.Video(id)
            isPlaylist -> YouTubeLink.Playlist(url)
            else -> null
        }
    }

    // ---------------- Yardımcılar ----------------

    private fun List<InfoItem>.toSongs(): List<Song> =
        filterIsInstance<StreamInfoItem>().mapNotNull { it.toSong() }.distinctBy { it.id }

    private fun StreamInfoItem.toSong(): Song? {
        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) return null
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Song(
            id = id,
            title = name,
            artist = cleanArtist(uploaderName),
            thumbnailUrl = bestThumb(thumbnails, id),
            durationSec = duration.coerceAtLeast(0)
        )
    }

    private fun cleanArtist(name: String?): String =
        name.orEmpty().removeSuffix(" - Topic").removeSuffix("VEVO").trim()

    private fun bestThumb(images: List<Image>, id: String): String =
        images.maxByOrNull { it.height }?.url ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    private fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"
}
