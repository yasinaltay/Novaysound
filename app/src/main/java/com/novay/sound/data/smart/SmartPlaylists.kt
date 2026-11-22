package com.novay.sound.data.smart

import android.content.Context
import com.novay.sound.data.db.dao.SongDao
import com.novay.sound.data.db.dao.StatsDao
import com.novay.sound.data.model.Song
import com.novay.sound.data.model.toSong
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.YouTubeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

enum class SmartList(val title: String, val subtitle: String) {
    MONTH_TOP("Bu ay en çok dinlediklerin", "Ayın favorileri"),
    FORGOTTEN("Unuttuğun favoriler", "Uzun süredir çalmadın"),
    DISCOVER("Haftalık keşif", "Zevkine göre yeni şarkılar")
}

@Serializable private data class DiscoverCache(val week: String, val songs: List<Song>)

/** Dinleme geçmişinden otomatik oluşan listeler. */
@Singleton
class SmartPlaylists @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val statsDao: StatsDao,
    private val library: LibraryRepository,
    private val youtube: YouTubeRepository,
    private val json: Json
) {
    private val discoverFile get() = File(context.filesDir, "weekly_discover.json")

    suspend fun songs(type: SmartList): List<Song> = when (type) {
        SmartList.MONTH_TOP -> statsDao.topSongsOnce(monthStart(), 50).map { it.song.toSong() }
        SmartList.FORGOTTEN -> songDao.forgottenFavorites(System.currentTimeMillis() - 30L * 86_400_000).map { it.toSong() }
        SmartList.DISCOVER -> discover()
    }

    /** Haftada bir yenilenir: son 30 günün en çok dinlenenlerinden radyo, bilinen şarkılar hariç. */
    private suspend fun discover(force: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val week = weekKey()
        if (!force) runCatching {
            json.decodeFromString(DiscoverCache.serializer(), discoverFile.readText())
        }.getOrNull()?.takeIf { it.week == week && it.songs.isNotEmpty() }?.let { return@withContext it.songs }

        val seeds = statsDao.topSongsOnce(System.currentTimeMillis() - 30L * 86_400_000, 6).map { it.song.toSong() }
            .ifEmpty { library.getFavorites().shuffled().take(4) }
            .ifEmpty { library.getRecent().take(4) }
        if (seeds.isEmpty()) return@withContext emptyList()

        val known = songDao.knownIds().toSet()
        val songs = coroutineScope {
            seeds.take(4).map { s -> async { runCatching { youtube.radio(s.id).take(20) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
        }.filter { it.id !in known }.distinctBy { it.id }.shuffled().take(40)

        if (songs.isNotEmpty()) discoverFile.writeText(json.encodeToString(DiscoverCache.serializer(), DiscoverCache(week, songs)))
        songs
    }

    suspend fun refreshDiscover(): List<Song> = discover(force = true)

    private fun monthStart() = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    private fun weekKey() = Calendar.getInstance().run { "${get(Calendar.YEAR)}-${get(Calendar.WEEK_OF_YEAR)}" }
}
