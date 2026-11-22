package com.novay.sound.data.smart

import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.SearchFilter
import com.novay.sound.data.youtube.YouTubeRepository
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
    private val youtube: YouTubeRepository
) {
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
