package com.novay.sound.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.novay.sound.data.youtube.NewPipeDownloader
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.di.DownloadCache
import com.novay.sound.di.PlaybackCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Oynatma zinciri:
 *  1) İndirilmiş mi? -> indirme önbelleğinden oku (ağ yok)
 *  2) Oynatma önbelleğinde tam mı? -> oradan oku (ağ yok)
 *  3) Değilse NewPipe ile URL çöz -> OkHttp ile indir, oynatma önbelleğine yaz
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quality: QualityPolicy,
    @PlaybackCache private val cache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    private val okHttp: OkHttpClient,
    private val youtube: YouTubeRepository
) {
    private val httpFactory by lazy {
        OkHttpDataSource.Factory(okHttp).setUserAgent(NewPipeDownloader.USER_AGENT)
    }

    private fun videoIdOf(spec: DataSpec): String =
        spec.key ?: spec.uri.lastPathSegment ?: throw IOException("Video kimliği yok: ${spec.uri}")

    private fun resolve(spec: DataSpec, id: String, low: Boolean = false): DataSpec {
        val source = try {
            runBlocking { youtube.resolveAudio(id, low) }
        } catch (e: Exception) {
            throw IOException("Ses akışı çözülemedi ($id): ${e.message}", e)
        }
        return spec.buildUpon().setUri(Uri.parse(source.url)).setKey(id).build()
    }

    /** İndirme yöneticisi için: sadece URL çözümü (önbelleğe DownloadManager yazar). */
    fun downloadUpstream(): DataSource.Factory =
        ResolvingDataSource.Factory(httpFactory) { spec -> resolve(spec, videoIdOf(spec)) }

    fun create(): DataSource.Factory {
        val online = ResolvingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ) { spec ->
            val id = videoIdOf(spec)
            val remaining = when {
                spec.length != C.LENGTH_UNSET.toLong() -> spec.length
                else -> cache.contentLength(id).let { if (it > 0) it - spec.position else -1L }
            }
            if (remaining > 0 && cache.isCached(id, spec.position, remaining)) spec.buildUpon().setKey(id).build()
            else resolve(spec, id, quality.preferLow())
        }
        // Salt okunur: indirme önbelleği
        val offline = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)

        return DataSource.Factory {
            SwitchingDataSource(
                local = DefaultDataSource.Factory(context).createDataSource(),
                direct = httpFactory.createDataSource(),
                isDownloaded = { downloadCache.isFullyCached(it) },
                offline = offline.createDataSource(),
                online = online.createDataSource()
            )
        }
    }
}

/** open() anında şarkı indirilmişse çevrimdışı, değilse çevrimiçi kaynağı kullanır. */
@OptIn(UnstableApi::class)
private class SwitchingDataSource(
    private val local: DataSource,
    private val direct: DataSource,
    private val isDownloaded: (String) -> Boolean,
    private val offline: DataSource,
    private val online: DataSource
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
        offline.addTransferListener(transferListener)
        online.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // Telefondaki dosya (content://)
        if (dataSpec.uri.scheme in setOf("content", "file", "android.resource")) {
            current = local
            return local.open(dataSpec)
        }
        // Radyo yayını / HLS parçaları: YouTube çözümü ve önbellek yok
        if (dataSpec.uri.host != "youtu.be") {
            current = direct
            return direct.open(dataSpec)
        }
        val id = dataSpec.key ?: dataSpec.uri.lastPathSegment
        val source = if (id != null && isDownloaded(id)) offline else online
        current = source
        return source.open(dataSpec.buildUpon().setKey(id).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = current?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try { current?.close() } finally { current = null }
    }
}

@OptIn(UnstableApi::class)
fun SimpleCache.contentLength(key: String): Long =
    ContentMetadata.getContentLength(getContentMetadata(key))

/** Şarkı önbellekte baştan sona var mı? */
@OptIn(UnstableApi::class)
fun SimpleCache.isFullyCached(key: String): Boolean {
    val len = contentLength(key)
    return len > 0 && isCached(key, 0, len)
}
