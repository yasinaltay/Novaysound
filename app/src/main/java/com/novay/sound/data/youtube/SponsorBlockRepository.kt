package com.novay.sound.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class SbSegment(val category: String, val segment: List<Double>)

/** SponsorBlock: klip videolarındaki müzik dışı kısımlar (konuşma, sponsor, abone ol çağrısı). */
@Singleton
class SponsorBlockRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val cache = ConcurrentHashMap<String, List<LongRange>>()

    suspend fun segments(videoId: String): List<LongRange> = cache[videoId] ?: withContext(Dispatchers.IO) {
        val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("categories", """["music_offtopic","sponsor","selfpromo","interaction"]""")
            .build()
        val result = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.code == 404 || !r.isSuccessful) return@use emptyList()
                json.decodeFromString<List<SbSegment>>(r.body?.string().orEmpty())
                    .filter { it.segment.size == 2 }
                    .map { (it.segment[0] * 1000).toLong()..(it.segment[1] * 1000).toLong() }
            }
        }.getOrDefault(emptyList())
        cache[videoId] = result
        result
    }
}
