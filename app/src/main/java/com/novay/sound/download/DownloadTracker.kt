package com.novay.sound.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.novay.sound.data.lyrics.LyricsRepository
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class DlState { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadItem(val song: Song, val state: DlState, val percent: Int, val bytes: Long, val updatedAt: Long)

/** Media3 DownloadManager'ı sade bir StateFlow'a çevirir (UI + Android Auto). */
@OptIn(UnstableApi::class)
@Singleton
class DownloadTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val json: Json,
    private val settings: SettingsRepository,
    private val lyrics: LyricsRepository,
    private val library: com.novay.sound.data.repository.LibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _items = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val items = _items.asStateFlow()
    private var poller: Job? = null

    init {
        scope.launch {
            val initial = mutableMapOf<String, DownloadItem>()
            manager.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) c.download.toItem()?.let { initial[it.song.id] = it }
            }
            _items.value = initial
            ensurePolling()
        }
        // Beğenilenleri Wi-Fi'dayken otomatik indir
        scope.launch {
            kotlinx.coroutines.flow.combine(
                settings.settings.map { it.on(com.novay.sound.data.settings.Toggle.AUTO_DL_LIKED) }.distinctUntilChanged(),
                library.favorites,
                unmetered()
            ) { on, favs, wifi -> if (on && wifi) favs else emptyList() }
                .collect { favs ->
                    favs.filter { !it.isLocal && !it.isRadio && _items.value[it.id]?.state != DlState.FAILED }
                        .take(300).forEach(::download)
                }
        }
        // "Sadece Wi-Fi'da indir" ayarı
        scope.launch {
            settings.settings.collect {
                manager.requirements = Requirements(if (it.wifiOnlyDownloads) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
            }
        }
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(m: DownloadManager, download: Download, finalException: Exception?) {
                download.toItem()?.let {
                    _items.value = _items.value + (it.song.id to it)
                    // İndirme bitince sözleri de kaydet (çevrimdışı sözler)
                    if (it.state == DlState.DONE) scope.launch { runCatching { lyrics.get(it.song) } }
                }
                ensurePolling()
            }
            override fun onDownloadRemoved(m: DownloadManager, download: Download) {
                _items.value = _items.value - download.request.id
            }
        })
    }

    /** Ölçülmeyen (Wi-Fi) ağa bağlı mı? Değiştikçe yayınlar. */
    private fun unmetered() = kotlinx.coroutines.flow.callbackFlow {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun now() = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        trySend(now())
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: android.net.Network, c: android.net.NetworkCapabilities) {
                trySend(c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }
            override fun onLost(n: android.net.Network) { trySend(false) }
        }
        cm.registerDefaultNetworkCallback(cb)
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.distinctUntilChanged()

    fun isDownloaded(id: String) = _items.value[id]?.state == DlState.DONE
    fun completedSongs(): List<Song> =
        _items.value.values.filter { it.state == DlState.DONE }.sortedByDescending { it.updatedAt }.map { it.song }

    fun download(song: Song) {
        if (song.isLocal) return
        if (_items.value[song.id]?.state in setOf(DlState.DONE, DlState.DOWNLOADING, DlState.QUEUED)) return
        val request = DownloadRequest.Builder(song.id, Uri.parse("https://youtu.be/${song.id}"))
            .setCustomCacheKey(song.id)
            .setData(json.encodeToString(Song.serializer(), song).toByteArray())
            .build()
        DownloadService.sendAddDownload(context, NovayDownloadService::class.java, request, false)
    }

    fun downloadAll(songs: List<Song>) = songs.forEach(::download)

    fun remove(id: String) =
        DownloadService.sendRemoveDownload(context, NovayDownloadService::class.java, id, false)

    /** İlerleme yüzdesi callback ile gelmez; indirme sürerken saniyede bir oku. */
    private fun ensurePolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive && manager.currentDownloads.isNotEmpty()) {
                val updated = _items.value.toMutableMap()
                manager.currentDownloads.forEach { d -> d.toItem()?.let { updated[it.song.id] = it } }
                _items.value = updated
                delay(1000)
            }
        }
    }

    private fun Download.toItem(): DownloadItem? {
        val song = runCatching { json.decodeFromString(Song.serializer(), request.data.decodeToString()) }
            .getOrNull() ?: return null
        val st = when (state) {
            Download.STATE_COMPLETED -> DlState.DONE
            Download.STATE_DOWNLOADING -> DlState.DOWNLOADING
            Download.STATE_FAILED -> DlState.FAILED
            else -> DlState.QUEUED
        }
        return DownloadItem(song, st, percentDownloaded.toInt().coerceIn(0, 100), bytesDownloaded, updateTimeMs)
    }
}
