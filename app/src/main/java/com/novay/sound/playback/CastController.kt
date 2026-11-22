package com.novay.sound.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Chromecast'e geçiş: sıradaki şarkıların gerçek ses URL'leri (m4a) çözülüp alıcıya verilir,
 * MediaSession'ın oynatıcısı CastPlayer ile değiştirilir. Bağlantı kesilince telefonda kaldığı yerden devam.
 */
@OptIn(UnstableApi::class)
class CastController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val youtube: YouTubeRepository,
    private val local: ExoPlayer,
    private val onPlayerChanged: (Player) -> Unit
) {
    private var castPlayer: CastPlayer? = null
    var isCasting = false
        private set

    fun init() {
        @Suppress("DEPRECATION")
        val castContext = runCatching { CastContext.getSharedInstance(context) }
            .onFailure { CrashLogger.log("Chromecast başlatılamadı", it) }.getOrNull() ?: return
        castPlayer = CastPlayer(castContext).apply {
            setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = toCast()
                override fun onCastSessionUnavailable() = toLocal()
            })
        }
    }

    private fun toCast() {
        val cast = castPlayer ?: return
        val items = (0 until local.mediaItemCount).map { local.getMediaItemAt(it) }
        val index = local.currentMediaItemIndex.coerceAtLeast(0)
        val position = local.currentPosition
        val wasPlaying = local.playWhenReady
        local.pause()
        if (items.isEmpty()) { isCasting = true; onPlayerChanged(cast); return }

        scope.launch {
            val window = items.drop(index).take(30)
            val resolved = window.map { item ->
                async {
                    if (item.videoId.startsWith("radio:")) return@async item // radyo yayını doğrudan
                    runCatching {
                        val src = youtube.resolveCastAudio(item.videoId)
                        item.buildUpon().setUri(src.url).setMimeType(src.mimeType).build()
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
            if (resolved.isEmpty()) return@launch
            cast.setMediaItems(resolved, 0, position)
            cast.playWhenReady = wasPlaying
            cast.prepare()
            local.stop()
            isCasting = true
            onPlayerChanged(cast)
        }
    }

    private fun toLocal() {
        val cast = castPlayer ?: return
        val items = (0 until cast.mediaItemCount).mapNotNull { cast.getMediaItemAt(it).toSongOrNull()?.toMediaItem() }
        if (items.isNotEmpty()) {
            local.setMediaItems(items, cast.currentMediaItemIndex.coerceIn(0, items.lastIndex), cast.currentPosition)
            local.prepare()
        }
        isCasting = false
        onPlayerChanged(local)
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
    }
}
