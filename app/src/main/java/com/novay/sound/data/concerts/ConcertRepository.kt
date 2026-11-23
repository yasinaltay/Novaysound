package com.novay.sound.data.concerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class Concert(
    val id: String,
    val artist: String,
    val title: String,
    val date: String,      // yyyy-MM-dd
    val venue: String,
    val city: String,
    val country: String,
    val url: String
)

/** Ticketmaster Discovery API (ücretsiz anahtar) ile Almanya ve Türkiye'deki konserler. */
@Singleton
class ConcertRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    val countries = listOf("DE", "TR")

    suspend fun upcoming(artist: String, apiKey: String): List<Concert> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || artist.isBlank()) return@withContext emptyList()
        countries.flatMap { cc -> runCatching { fetch(artist, apiKey, cc) }.getOrDefault(emptyList()) }
            .distinctBy { it.id }.sortedBy { it.date }
    }

    /** Anahtar doğru mu? Doğruysa null, değilse hata mesajı. */
    suspend fun test(apiKey: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val code = client.newCall(Request.Builder().url(url("music", apiKey.trim(), "DE")).build()).execute().use { it.code }
            when (code) { 200 -> null; 401 -> "Anahtar geçersiz"; else -> "Sunucu hatası ($code)" }
        }.getOrElse { "Bağlantı hatası" }
    }

    private fun url(keyword: String, key: String, cc: String) =
        "https://app.ticketmaster.com/discovery/v2/events.json".toHttpUrl().newBuilder()
            .addQueryParameter("apikey", key)
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("classificationName", "music")
            .addQueryParameter("countryCode", cc)
            .addQueryParameter("sort", "date,asc")
            .addQueryParameter("size", "20")
            .build()

    private fun fetch(artist: String, key: String, cc: String): List<Concert> {
        val body = client.newCall(Request.Builder().url(url(artist, key.trim(), cc)).build()).execute()
            .use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        return parse(body, artist, cc)
    }

    fun parse(body: String, artist: String, cc: String): List<Concert> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val events = (root["_embedded"] as? JsonObject)?.get("events") as? JsonArray ?: return emptyList()
        val want = norm(artist)
        return events.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val emb = o["_embedded"] as? JsonObject
            // Sadece sanatçının gerçekten yer aldığı etkinlikler (benzer isimli başka etkinlikleri ele)
            val attractions = (emb?.get("attractions") as? JsonArray)?.mapNotNull { a -> (a as? JsonObject)?.str("name") }.orEmpty()
            if (attractions.none { norm(it) == want }) return@mapNotNull null
            val venue = (emb?.get("venues") as? JsonArray)?.firstOrNull() as? JsonObject
            val date = ((o["dates"] as? JsonObject)?.get("start") as? JsonObject)?.str("localDate") ?: return@mapNotNull null
            Concert(
                id = o.str("id") ?: return@mapNotNull null,
                artist = artist,
                title = o.str("name").orEmpty(),
                date = date,
                venue = venue?.str("name").orEmpty(),
                city = (venue?.get("city") as? JsonObject)?.str("name").orEmpty(),
                country = cc,
                url = o.str("url").orEmpty()
            )
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
