package com.novay.sound.data.radio

import android.content.Context
import com.novay.sound.BuildConfig
import com.novay.sound.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RbStation(
    val stationuuid: String,
    val name: String = "",
    val url_resolved: String = "",
    val url: String = "",
    val favicon: String = "",
    val tags: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val lastcheckok: Int = 1,
    val clickcount: Int = 0,
    val votes: Int = 0
)

enum class RadioCountry(val code: String, val label: String, val flag: String, val popular: List<String>) {
    // Herkesin bildiği büyük istasyonlar (sıra = öncelik). İsimler sadeleştirilmiş halde aranır.
    TR("TR", "Türkiye", "🇹🇷", listOf(
        "kralfm", "powerturk", "superfm", "joyturk", "fenomen", "kralpop", "powerfm", "metrofm",
        "number1", "virgin", "bestfm", "slowturk", "joyfm", "radyod", "alemfm", "trtfm",
        "trtturku", "radyo45lik", "radyoseymen", "palstation", "kralturku", "radyoviva"
    )),
    DE("DE", "Almanya", "🇩🇪", listOf(
        "1live", "swr3", "bayern3", "antennebayern", "ndr2", "wdr2", "bigfm", "energy",
        "radiohamburg", "hr3", "ffh", "ffn", "deutschlandfunk", "rockantenne", "radiobob",
        "sunshinelive", "metropolfm", "jamfm", "njoy", "fritz", "swr1", "bayern1"
    ))
}

/** "Süper FM" -> "superfm": Türkçe harfler sadeleşir, boşluk/işaret silinir. */
fun radioKey(name: String): String = name.lowercase(java.util.Locale.forLanguageTag("tr"))
    .map { when (it) { 'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'; else -> it } }
    .filter { it.isLetterOrDigit() }.joinToString("")

/** Popüler listedeki sırası (yoksa -1). */
fun RadioCountry.popularRank(name: String): Int {
    val key = radioKey(name)
    return popular.indexOfFirst { key.contains(it) }
}

/** Radio Browser (radio-browser.info): ücretsiz, anahtarsız, topluluk tarafından güncellenen istasyon listesi. */
@Singleton
class RadioRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val servers = listOf(
        "https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info"
    )
    private val cache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val favFile = File(context.filesDir, "radio_favorites.json")
    private val listSer = ListSerializer(Song.serializer())

    private val _favorites = MutableStateFlow(
        runCatching { json.decodeFromString(listSer, favFile.readText()) }.getOrDefault(emptyList())
    )
    val favorites = _favorites.asStateFlow()

    /** Ülkenin en çok dinlenen istasyonları (6 saat önbellek). */
    suspend fun top(country: RadioCountry): List<Song> = query(country, null)

    suspend fun search(country: RadioCountry, name: String): List<Song> = query(country, name.trim())

    private suspend fun query(country: RadioCountry, name: String?): List<Song> = withContext(Dispatchers.IO) {
        val key = "${country.code}|${name.orEmpty()}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000 }?.let { return@withContext it.second }
        var lastError: Throwable? = null
        for (server in servers) {
            val url = "$server/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("countrycode", country.code)
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("limit", if (name.isNullOrBlank()) "200" else "100")
                .apply { if (!name.isNullOrBlank()) addQueryParameter("name", name) }
                .build()
            val result = runCatching {
                client.newCall(
                    Request.Builder().url(url).header("User-Agent", "NovaySound/${BuildConfig.VERSION_NAME}").build()
                ).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    json.decodeFromString(ListSerializer(RbStation.serializer()), r.body?.string().orEmpty())
                }
            }
            result.onFailure { lastError = it }
            val stations = result.getOrNull() ?: continue
            // Sıralama: önce bilinen popüler istasyonlar (listedeki sırayla), sonra tıklanma + oy puanı
            val score = { st: RbStation -> st.clickcount * 3L + st.votes }
            val songs = stations
                .filter { it.lastcheckok == 1 && (it.url_resolved.ifBlank { it.url }).startsWith("http") }
                .sortedByDescending(score)
                .distinctBy { radioKey(it.name) }   // aynı istasyonun kopyalarından en çok dinleneni kalır
                .sortedWith(compareBy<RbStation> { st ->
                    country.popularRank(st.name).let { if (it < 0) Int.MAX_VALUE else it }
                }.thenByDescending(score))
                .map { it.toSong(country) }
            cache[key] = System.currentTimeMillis() to songs
            return@withContext songs
        }
        throw lastError ?: IllegalStateException("Radyo listesi alınamadı")
    }

    fun isFavorite(id: String) = _favorites.value.any { it.id == id }

    fun toggleFavorite(station: Song) {
        val list = _favorites.value
        _favorites.value = if (list.any { it.id == station.id }) list.filter { it.id != station.id } else list + station
        runCatching { favFile.writeText(json.encodeToString(listSer, _favorites.value)) }
    }

    private fun RbStation.toSong(country: RadioCountry): Song {
        val genre = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString(", ")
        val quality = if (bitrate > 0) "$bitrate kbps" else codec
        return Song(
            id = "radio:$stationuuid",
            title = name.trim(),
            artist = listOf("${country.flag} Canlı", genre, quality).filter { it.isNotBlank() }.joinToString(" • "),
            thumbnailUrl = favicon.takeIf { it.startsWith("http") },
            durationSec = 0,
            streamUrl = url_resolved.ifBlank { url }
        )
    }
}
