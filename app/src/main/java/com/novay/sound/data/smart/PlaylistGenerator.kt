package com.novay.sound.data.smart

import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.SearchFilter
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.util.TitleCleaner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/** Süreye göre liste ve çoklu sanatçı radyosu üretir. */
@Singleton
class PlaylistGenerator @Inject constructor(
    private val library: LibraryRepository,
    private val youtube: YouTubeRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val json: kotlinx.serialization.json.Json
) {
    @kotlinx.serialization.Serializable
    private data class CachedPicks(val at: Long, val songs: List<Song>)

    /** Kayıtlı hızlı seçimler 6 saatten yeniyse onları döndürür; [force] ile her zaman yeniden üretir. */
    suspend fun quickPicksCached(force: Boolean = false): List<Song> {
        val file = java.io.File(context.filesDir, "quick_picks.json")
        if (!force) runCatching { json.decodeFromString(CachedPicks.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.at < 6 * 3_600_000 && it.songs.isNotEmpty() }
            ?.let { return it.songs }
        val fresh = quickPicks()
        if (fresh.isNotEmpty()) runCatching {
            file.writeText(json.encodeToString(CachedPicks.serializer(), CachedPicks(System.currentTimeMillis(), fresh)))
        }
        return fresh
    }
    /** Sevdiklerin + benzerlerinden, toplamı hedef süreye ±1,5 dk uyan liste. */
    suspend fun byDuration(minutes: Int): List<Song> = coroutineScope {
        val target = minutes * 60L
        val ok = { s: Song -> s.durationSec in 60..900 && !s.isRadio }
        val own = (library.getFavorites() + library.getRecent()).distinctBy { it.id }.filter(ok).shuffled()
        val seeds = own.take(3).ifEmpty { runCatching { youtube.trending().take(3) }.getOrDefault(emptyList()) }
        val similar = seeds.map { s -> async { runCatching { youtube.radio(s.id).take(15) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().filter(ok).shuffled()
        // Kendi şarkıların ve benzerleri sırayla harmanlanır
        val pool = buildList {
            val a = own.iterator(); val b = similar.iterator()
            while (a.hasNext() || b.hasNext()) { if (a.hasNext()) add(a.next()); if (b.hasNext()) add(b.next()) }
        }.distinctBy { it.id }

        val picked = mutableListOf<Song>()
        var total = 0L
        for (s in pool) {
            if (total + s.durationSec <= target + 90) { picked += s; total += s.durationSec }
            if (total >= target - 90) break
        }
        // Kalan boşluğa en yakın süreli şarkıyı ekle
        val gap = target - total
        if (gap > 90) pool.filter { it !in picked }.minByOrNull { abs(it.durationSec - gap) }
            ?.takeIf { abs(it.durationSec - gap) < gap }?.let { picked += it }
        smartShuffle(picked)
    }

    /** 2-5 sanatçının popüler şarkıları + benzerleri, sırayla karışık. */
    suspend fun multiArtist(names: List<String>): List<Song> = coroutineScope {
        val perArtist = names.map { name ->
            async {
                val top = runCatching { youtube.search(name, SearchFilter.SONGS).songs }.getOrDefault(emptyList())
                    .filter { it.artist.contains(name, true) || name.contains(it.artist, true) }.take(8)
                val similar = top.firstOrNull()?.let { runCatching { youtube.radio(it.id).take(6) }.getOrDefault(emptyList()) }.orEmpty()
                (top + similar).shuffled()
            }
        }.awaitAll()
        val merged = buildList {
            val its = perArtist.map { it.iterator() }
            while (its.any { it.hasNext() }) its.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.id }
        smartShuffle(merged)
    }

    /**
     * Hızlı seçimler: son dinlediklerin ve favorilerinden 3 tohum şarkı, her birinin benzerleri.
     * Birden çok tohumun önerisinde geçen ve tanıdık sanatçılardan olan şarkılar öne çıkar;
     * mix, derleme, slowed/sped up, karaoke gibi "şarkı olmayan" sonuçlar ve son dinlediklerin elenir.
     */
    suspend fun quickPicks(count: Int = 10): List<Song> = coroutineScope {
        val ok = { s: Song -> !s.isRadio && !s.isLocal && s.durationSec in 90..480 && !isJunkTitle(s.title) }
        val recent = library.getRecent().filter(ok)
        val favs = library.getFavorites().filter(ok)
        val seeds = (recent.take(6) + favs.shuffled().take(4)).distinctBy { it.id }.shuffled().take(3)
        if (seeds.isEmpty()) return@coroutineScope runCatching { youtube.trending() }.getOrDefault(emptyList()).filter(ok).take(count)

        val knownArtists = (recent + favs).map { it.artist.lowercase() }.toSet()
        val skip = recent.take(25).map { it.id }.toSet() + seeds.map { it.id }
        val all = seeds.map { s -> async { runCatching { youtube.radio(s.id).take(25) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
            .filter { ok(it) && it.id !in skip }
        val freq = all.groupingBy { TitleCleaner.key(it.title, it.artist) }.eachCount()
        val ranked = all.distinctBy { TitleCleaner.key(it.title, it.artist) }
            .shuffled()
            .sortedByDescending { s ->
                (freq[TitleCleaner.key(s.title, s.artist)] ?: 1) * 2 + if (s.artist.lowercase() in knownArtists) 1 else 0
            }
        smartShuffle(ranked.take(count + 5)).take(count)
    }

    companion object {
        fun isJunkTitle(title: String): Boolean = com.novay.sound.util.JunkFilter.isJunk(title)
    }

    /** Aynı sanatçının şarkıları arka arkaya gelmesin. */
    fun smartShuffle(list: List<Song>): List<Song> {
        val left = list.toMutableList()
        val out = mutableListOf<Song>()
        while (left.isNotEmpty()) {
            val prev = out.lastOrNull()?.artist
            val i = left.indexOfFirst { !it.artist.equals(prev, true) }.takeIf { it >= 0 } ?: 0
            out += left.removeAt(i)
        }
        return out
    }
}
