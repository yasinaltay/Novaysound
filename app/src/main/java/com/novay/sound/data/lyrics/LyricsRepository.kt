package com.novay.sound.data.lyrics

import com.novay.sound.BuildConfig
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.novay.sound.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
private data class LrcResult(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false
)

@Serializable
data class LyricLine(val timeMs: Long, val text: String)
@Serializable
data class Lyrics(val synced: List<LyricLine>, val plain: String?) {
    val isSynced get() = synced.isNotEmpty()
}

/** LRCLIB (ücretsiz, anahtarsız) üzerinden senkron şarkı sözleri. */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val base = "https://lrclib.net/api".toHttpUrl()
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /** Bulunan sözler diske de yazılır: bir kez açılan/indirilen şarkının sözleri internetsiz görünür. */
    suspend fun get(song: Song): Lyrics? {
        cache[song.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(dir, song.id.replace(':', '_') + ".json")
            runCatching { json.decodeFromString(Lyrics.serializer(), file.readText()) }.getOrNull()?.let {
                cache[song.id] = it
                return@withContext it
            }
            if (song.id in misses) return@withContext null
            val (artist, title) = cleanup(song)
            val result = exact(artist, title, song.durationSec)
                ?: search("$artist $title", song.durationSec)
                ?: search(title, song.durationSec)
            val lyrics = result?.let {
                Lyrics(LrcParser.parse(it.syncedLyrics.orEmpty()), it.plainLyrics?.takeIf(String::isNotBlank))
            }?.takeIf { it.isSynced || it.plain != null }
            if (lyrics != null) {
                cache[song.id] = lyrics
                runCatching { file.writeText(json.encodeToString(Lyrics.serializer(), lyrics)) }
            } else misses += song.id
            lyrics
        }
    }

    private fun exact(artist: String, title: String, duration: Long): LrcResult? {
        val url = base.newBuilder().addPathSegment("get")
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .apply { if (duration > 0) addQueryParameter("duration", duration.toString()) }
            .build()
        return fetch(url)?.let { runCatching { json.decodeFromString(LrcResult.serializer(), it) }.getOrNull() }
            ?.takeIf { !it.instrumental }
    }

    private fun search(query: String, duration: Long): LrcResult? {
        val url = base.newBuilder().addPathSegment("search").addQueryParameter("q", query).build()
        val list = fetch(url)?.let { runCatching { json.decodeFromString<List<LrcResult>>(it) }.getOrNull() }.orEmpty()
        val close = list.filter { duration <= 0 || it.duration == null || abs(it.duration - duration) < 8 }
        return close.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: close.firstOrNull { !it.plainLyrics.isNullOrBlank() }
    }

    private fun fetch(url: HttpUrl): String? = runCatching {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "NovaySound/${BuildConfig.VERSION_NAME} (https://github.com/yasinaltay/Novaysound)")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** "Sanatçı - Şarkı (Official Video) [4K]" gibi başlıkları temizle. */
    private fun cleanup(song: Song): Pair<String, String> {
        var title = song.title
            .replace(Regex("""[(\[{].*?[)\]}]"""), "")
            .replace(Regex("""(?i)\b(official|video|klip|lyrics?|audio|hd|4k|mv)\b"""), "")
        var artist = song.artist
        if (title.contains(" - ")) {
            val (a, t) = title.split(" - ", limit = 2)
            artist = a; title = t
        }
        title = title.split(Regex("""(?i)\s(ft\.?|feat\.?)\s""")).first()
        return artist.trim() to title.replace(Regex("\\s+"), " ").trim()
    }

}

/** "[01:23.45] söz" biçimindeki LRC metnini zamanlı satırlara çevirir. */
object LrcParser {
    private val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
    fun parse(lrc: String): List<LyricLine> = lrc.lines().flatMap { line ->
        val text = line.replace(re, "").trim()
        re.findAll(line).map { m ->
            val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()
            LyricLine(ms, text)
        }.toList()
    }.sortedBy { it.timeMs }
}
