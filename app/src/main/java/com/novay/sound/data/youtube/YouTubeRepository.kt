package com.novay.sound.data.youtube

import android.net.Uri
import com.novay.sound.data.model.Song
import com.novay.sound.data.settings.Toggle
import com.novay.sound.util.FeatureFlags
import com.novay.sound.util.TitleCleaner
import com.novay.sound.util.JunkFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
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
    VIDEOS(YoutubeSearchQueryHandlerFactory.VIDEOS),       // normal YouTube videoları
    ARTISTS(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), // YouTube Music sanatçıları
    ALBUMS(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),   // YouTube Music albümleri
    LYRICS(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)     // sözden şarkı bulma (LyricsFinder)
}

data class AlbumItem(val title: String, val url: String, val artist: String, val cover: String?, val count: Long)
data class Album(val title: String, val url: String, val artist: String, val cover: String?, val songs: List<Song>)

data class ArtistItem(val name: String, val url: String, val avatar: String?, val subscribers: Long)

data class Artist(
    val name: String,
    val url: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    val popular: List<Song>,
    val latest: List<Song>,
    val albums: List<AlbumItem> = emptyList()
)

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
class YouTubeRepository @Inject constructor(
    private val flags: FeatureFlags,
    private val client: okhttp3.OkHttpClient,
    private val json: kotlinx.serialization.json.Json
) {

    private val service get() = ServiceList.YouTube
    private val urlCache = ConcurrentHashMap<String, AudioSource>()

    // ---------------- Arama ----------------

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): SearchPage =
        withContext(Dispatchers.IO) {
            val handler = service.searchQHFactory.fromQuery(query, listOf(filter.value), "")
            val info = SearchInfo.getInfo(service, handler)
            val songs = info.relatedItems.toSongs().dedupe()
            // Müzik filtresi boş dönerse normal video aramasına düş
            if (songs.isEmpty() && filter == SearchFilter.SONGS) search(query, SearchFilter.VIDEOS)
            else SearchPage(songs, handler, info.nextPage)
        }

    suspend fun searchMore(page: SearchPage): SearchPage = withContext(Dispatchers.IO) {
        val more = SearchInfo.getMoreItems(service, page.handler, page.nextPage)
        SearchPage(more.items.toSongs().dedupe(), page.handler, more.nextPage)
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    // ---------------- Ses akışı ----------------

    /** En yüksek kaliteli ses URL'si (önce önbellek; YouTube URL'leri ~6 saat geçerli). */
    suspend fun resolveAudio(videoId: String, low: Boolean = false): AudioSource = withContext(Dispatchers.IO) {
        val key = "$videoId|$low"
        urlCache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() + 60_000 }
            ?.let { return@withContext it }

        val info = StreamInfo.getInfo(service, videoUrl(videoId))
        val best = pickAudio(info.audioStreams, low)
            ?: error("Bu video için ses akışı bulunamadı")
        val source = AudioSource(
            url = best.content,
            mimeType = best.format?.mimeType,
            bitrate = best.averageBitrate.takeIf { it > 0 } ?: best.bitrate,
            expiresAt = parseExpire(best.content)
        )
        urlCache[key] = source
        source
    }

    /** Chromecast için: m4a (AAC) tercih edilir, her alıcıda çalar. */
    suspend fun resolveCastAudio(videoId: String): AudioSource = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, videoUrl(videoId))
        val progressive = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val m4a = progressive.filter { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A }
        val best = (m4a.ifEmpty { progressive }).maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
            ?: error("Ses akışı yok")
        AudioSource(best.content, best.format?.mimeType ?: "audio/mp4", best.averageBitrate, parseExpire(best.content))
    }

    /** Süresi dolan (403) URL'yi unut, bir sonraki istekte yeniden çözülsün. */
    fun invalidate(videoId: String) {
        urlCache.remove("$videoId|false")
        urlCache.remove("$videoId|true")
    }

    /** low=true: veri tasarrufu için ~50-70 kbps akış; aksi halde en yüksek. */
    private fun pickAudio(streams: List<AudioStream>, low: Boolean): AudioStream? {
        if (!low) return pickBestAudio(streams)
        val usable = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
        val rate = { s: AudioStream -> s.averageBitrate.takeIf { it > 0 } ?: s.bitrate }
        return usable.filter { rate(it) >= 45_000 }.minByOrNull(rate) ?: usable.minByOrNull(rate)
    }

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
        val (title, artist) = tidy(info.name, cleanArtist(info.uploaderName))
        Song(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = bestThumb(info.thumbnails, videoId),
            durationSec = info.duration.coerceAtLeast(0),
            artistUrl = info.uploaderUrl
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
        }.filter { !JunkFilter.isJunk(it.title) && JunkFilter.isSongLength(it.durationSec) }
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

    // ---------------- Sanatçı ----------------

    suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf(SearchFilter.ARTISTS.value), "")
        SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<ChannelInfoItem>().map {
            ArtistItem(cleanArtist(it.name), it.url, it.thumbnails.maxByOrNull { t -> t.height }?.url, it.subscriberCount)
        }.distinctBy { it.url }
    }

    /** Şarkının kanal linki yoksa sanatçı adını aratıp ilk sonucu al. */
    suspend fun findArtistUrl(name: String): String? =
        runCatching { searchArtists(name).firstOrNull()?.url }.getOrNull()

    suspend fun artist(url: String): Artist = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(service, url)
        val name = cleanArtist(info.name)
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: info.tabs.firstOrNull()
        val latest = tab?.let { runCatching { ChannelTabInfo.getInfo(service, it).relatedItems.toSongs() }.getOrNull() }.orEmpty()
        val popular = runCatching { search(name, SearchFilter.SONGS).songs }.getOrDefault(emptyList())
            .filter { it.artist.contains(name, ignoreCase = true) || name.contains(it.artist, ignoreCase = true) }
            .take(15)
        val albumsTab = info.tabs.firstOrNull { ChannelTabs.ALBUMS in it.contentFilters }
        val albums = albumsTab?.let {
            runCatching { ChannelTabInfo.getInfo(service, it).relatedItems.toAlbums() }.getOrNull()
        }.orEmpty()
        Artist(
            albums = albums,
            name = name, url = url,
            avatar = info.avatars.maxByOrNull { it.height }?.url,
            banner = info.banners.maxByOrNull { it.height }?.url,
            subscribers = info.subscriberCount,
            popular = popular.ifEmpty { latest.take(10) },
            latest = latest
        )
    }

    // ---------------- Albüm ----------------

    suspend fun searchAlbums(query: String): List<AlbumItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf(SearchFilter.ALBUMS.value), "")
        SearchInfo.getInfo(service, handler).relatedItems.toAlbums()
    }

    suspend fun album(url: String): Album = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(service, url)
        val songs = info.relatedItems.toSongs().toMutableList()
        var next = info.nextPage
        while (Page.isValid(next) && songs.size < 200) {
            val more = PlaylistInfo.getMoreItems(service, url, next)
            songs += more.items.toSongs()
            next = more.nextPage
        }
        val artist = cleanArtist(info.uploaderName).ifBlank { songs.firstOrNull()?.artist.orEmpty() }
        Album(
            title = info.name.removePrefix("Album - ").trim(),
            url = url, artist = artist,
            cover = info.thumbnails.maxByOrNull { it.height }?.url ?: songs.firstOrNull()?.thumbnailUrl,
            songs = songs.map { if (it.artist.isBlank()) it.copy(artist = artist) else it }
        )
    }

    private fun List<InfoItem>.toAlbums(): List<AlbumItem> =
        filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>().map {
            AlbumItem(it.name.removePrefix("Album - ").trim(), it.url, cleanArtist(it.uploaderName),
                it.thumbnails.maxByOrNull { t -> t.height }?.url, it.streamCount)
        }.distinctBy { it.url }

    // ---------------- Trendler ----------------

    @Volatile private var trendCache: Pair<Long, List<Song>>? = null

    /**
     * Türkiye'de trend (6 saat önbellek):
     * 1) Apple Music'in resmi, herkese açık "Türkiye'de en çok dinlenenler" listesi -> YouTube'da eşleştirilir
     * 2) olmazsa YouTube müzik trendleri, 3) o da olmazsa "yeni çıkanlar" araması.
     */
    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        trendCache?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000 }?.let { return@withContext it.second }
        val charts = runCatching { appleChart("tr", 25) }.getOrDefault(emptyList())
        if (charts.size >= 10) {
            trendCache = System.currentTimeMillis() to charts
            return@withContext charts
        }
        val kiosk = runCatching {
            val kl = service.kioskList
            val id = kl.availableKiosks.firstOrNull { it.contains("music", ignoreCase = true) } ?: kl.defaultKioskId
            val ex = kl.getExtractorById(id, null)
            ex.fetchPage()
            ex.initialPage.items.toSongs()
        }.getOrDefault(emptyList())
        val result = kiosk.ifEmpty {
            runCatching { search("yeni çıkan türkçe şarkılar", SearchFilter.SONGS).songs }.getOrDefault(emptyList())
        }
        if (result.isNotEmpty()) trendCache = System.currentTimeMillis() to result
        result
    }

    /** Apple Music RSS (rss.applemarketingtools.com) listesini YouTube şarkılarına çevirir, sırayı korur. */
    private suspend fun appleChart(country: String, limit: Int): List<Song> = kotlinx.coroutines.coroutineScope {
        val body = client.newCall(
            okhttp3.Request.Builder()
                .url("https://rss.applemarketingtools.com/api/v2/$country/music/most-played/$limit/songs.json")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@coroutineScope emptyList()
        val results = (json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)
            ?.get("feed")?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("results") as? kotlinx.serialization.json.JsonArray ?: return@coroutineScope emptyList()
        val tracks = results.mapNotNull { r ->
            val o = r as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val artist = (o["artistName"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            name to artist
        }
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        tracks.map { (name, artist) ->
            async {
                gate.acquire()
                try {
                    val found = runCatching { search("$artist $name", SearchFilter.SONGS).songs }.getOrDefault(emptyList())
                    val key = name.lowercase().filter { it.isLetterOrDigit() }.take(12)
                    found.firstOrNull { it.title.lowercase().filter { c -> c.isLetterOrDigit() }.contains(key) } ?: found.firstOrNull()
                } finally { gate.release() }
            }
        }.map { it.await() }.filterNotNull().distinctBy { it.id }
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
        val (title, artist) = tidy(name, cleanArtist(uploaderName))
        return Song(
            id = id,
            title = title,
            artist = artist,
            thumbnailUrl = bestThumb(thumbnails, id),
            durationSec = duration.coerceAtLeast(0),
            artistUrl = uploaderUrl
        )
    }

    private fun tidy(title: String, artist: String): Pair<String, String> =
        if (flags.on(Toggle.CLEAN_TITLES)) TitleCleaner.clean(title, artist) else title to artist

    private fun List<Song>.dedupe(): List<Song> =
        if (flags.on(Toggle.DEDUPE)) distinctBy { TitleCleaner.key(it.title, it.artist) } else this

    private fun cleanArtist(name: String?): String =
        name.orEmpty().removeSuffix(" - Topic").removeSuffix("VEVO").trim()

    private fun bestThumb(images: List<Image>, id: String): String =
        images.maxByOrNull { it.height }?.url ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    private fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"
}
