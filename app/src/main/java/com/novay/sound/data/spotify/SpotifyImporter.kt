package com.novay.sound.data.spotify

import com.novay.sound.data.model.Song
import com.novay.sound.data.youtube.NewPipeDownloader
import com.novay.sound.data.youtube.SearchFilter
import com.novay.sound.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyTrack(val title: String, val artist: String)
data class SpotifyLink(val type: String, val id: String)

/**
 * Spotify çalma listesi / albüm / şarkı linkini YouTube şarkılarına çevirir.
 * API anahtarı gerekmez: herkese açık "embed" sayfasındaki veriler okunur.
 */
@Singleton
class SpotifyImporter @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val youtube: YouTubeRepository
) {
    private val linkRe = Regex("""open\.spotify\.com/(?:intl-[\w-]+/)?(playlist|album|track)/([A-Za-z0-9]+)""")

    fun parse(text: String): SpotifyLink? =
        linkRe.find(text)?.let { SpotifyLink(it.groupValues[1], it.groupValues[2]) }

    suspend fun fetch(link: SpotifyLink): Pair<String, List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val html = client.newCall(
            Request.Builder().url("https://open.spotify.com/embed/${link.type}/${link.id}")
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                .build()
        ).execute().use { r ->
            if (!r.isSuccessful) error("Spotify yanıtı: ${r.code}")
            r.body?.string().orEmpty()
        }
        val data = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Spotify sayfası okunamadı")
        val entity = json.parseToJsonElement(data)["props"]["pageProps"]["state"]["data"]["entity"]
            ?: error("Liste bulunamadı (özel liste olabilir)")
        val name = entity["name"].str() ?: entity["title"].str() ?: "Spotify listesi"

        val tracks = (entity["trackList"] as? JsonArray)?.mapNotNull { t ->
            val title = t["title"].str() ?: return@mapNotNull null
            SpotifyTrack(title, t["subtitle"].str().orEmpty().replace(' ', ' '))
        } ?: listOf(
            SpotifyTrack(
                name,
                (entity["artists"] as? JsonArray)?.mapNotNull { it["name"].str() }?.joinToString(", ").orEmpty()
            )
        )
        name to tracks
    }

    /** Her şarkıyı YouTube Music'te arar (aynı anda 4 arama), sırayı korur. */
    suspend fun match(tracks: List<SpotifyTrack>, onProgress: (done: Int, total: Int) -> Unit): List<Song> = coroutineScope {
        val gate = Semaphore(4)
        val done = AtomicInteger(0)
        tracks.map { t ->
            async {
                gate.withPermit {
                    val first = t.artist.substringBefore(',').trim()
                    val song = runCatching { youtube.search("$first ${t.title}", SearchFilter.SONGS).songs.firstOrNull() }.getOrNull()
                    onProgress(done.incrementAndGet(), tracks.size)
                    song
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    private operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}
