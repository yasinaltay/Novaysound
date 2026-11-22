package com.novay.sound.data.lyrics

import com.novay.sound.data.model.Song
import com.novay.sound.data.youtube.SearchFilter
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.util.TitleCleaner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LyricsMatch(val songs: List<Song>, val verified: Set<String>)

/**
 * Aklında kalan söz parçasından şarkıyı bulur: YouTube Music'te arar, ilk adayların sözlerini
 * LRCLIB'den indirip parçanın gerçekten geçip geçmediğine bakar. Eşleşenler en üstte.
 */
@Singleton
class LyricsFinder @Inject constructor(
    private val youtube: YouTubeRepository,
    private val lyrics: LyricsRepository
) {
    private val tr = Locale.forLanguageTag("tr")

    suspend fun find(snippet: String): LyricsMatch = coroutineScope {
        val songs = async { runCatching { youtube.search(snippet, SearchFilter.SONGS).songs }.getOrDefault(emptyList()) }
        val videos = async { runCatching { youtube.search("$snippet şarkı sözleri", SearchFilter.VIDEOS).songs }.getOrDefault(emptyList()) }
        val all = (songs.await() + videos.await()).distinctBy { TitleCleaner.key(it.title, it.artist) }.take(15)
        val needle = normalize(snippet)
        val verified = all.take(8).map { s ->
            async {
                val l = runCatching { lyrics.get(s) }.getOrNull() ?: return@async null
                val text = l.synced.joinToString(" ") { it.text } + " " + l.plain.orEmpty()
                if (needle.length >= 6 && normalize(text).contains(needle)) s.id else null
            }
        }.awaitAll().filterNotNull().toSet()
        LyricsMatch(all.filter { it.id in verified } + all.filter { it.id !in verified }, verified)
    }

    private fun normalize(s: String) =
        s.lowercase(tr).map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotBlank() }.joinToString(" ")
}
