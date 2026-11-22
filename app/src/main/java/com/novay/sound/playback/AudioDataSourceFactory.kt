package com.novay.sound.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.novay.sound.data.youtube.NewPipeDownloader
import com.novay.sound.data.youtube.YouTubeRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zincir: ResolvingDataSource -> CacheDataSource -> OkHttp
 * 1) Şarkı önbellekte tamamen varsa ağa hiç çıkmadan oynatılır (çevrimdışı).
 * 2) Yoksa NewPipe ile gerçek ses URL'si çözülür, indirilen her bayt önbelleğe yazılır.
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioDataSourceFactory @Inject constructor(
    private val cache: SimpleCache,
    private val okHttp: OkHttpClient,
    private val youtube: YouTubeRepository
) {
    fun create(): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(okHttp)
            .setUserAgent(NewPipeDownloader.USER_AGENT)

        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ResolvingDataSource.Factory(cached) { spec ->
            val id = spec.key ?: spec.uri.lastPathSegment
                ?: throw IOException("Video kimliği yok: ${spec.uri}")

            val remaining = when {
                spec.length != C.LENGTH_UNSET.toLong() -> spec.length
                else -> cache.contentLength(id).let { if (it > 0) it - spec.position else -1L }
            }
            if (remaining > 0 && cache.isCached(id, spec.position, remaining)) {
                return@Factory spec.buildUpon().setKey(id).build()
            }

            val source = try {
                runBlocking { youtube.resolveAudio(id) }
            } catch (e: Exception) {
                throw IOException("Ses akışı çözülemedi ($id): ${e.message}", e)
            }
            spec.buildUpon().setUri(source.url).setKey(id).build()
        }
    }
}

@OptIn(UnstableApi::class)
fun SimpleCache.contentLength(key: String): Long =
    ContentMetadata.getContentLength(getContentMetadata(key))

/** Şarkı önbellekte baştan sona var mı? (Çevrimdışı listesi için) */
@OptIn(UnstableApi::class)
fun SimpleCache.isFullyCached(key: String): Boolean {
    val len = contentLength(key)
    return len > 0 && isCached(key, 0, len)
}
