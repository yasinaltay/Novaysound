package com.novay.sound.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Şarkılar arası geçiş: şarkının son N saniyesinde ikinci bir oynatıcı sonraki şarkıyı
 * sessizce başlatır ve sesler karşılıklı kayar. Ana oynatıcı sonraki şarkıya geçince
 * aynı noktaya atlar (veri önbellekte olduğu için anında) ve yardımcı oynatıcı kapanır.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val factory: AudioDataSourceFactory,
    private val scope: CoroutineScope,
    private val main: ExoPlayer
) {
    var seconds = 0
    private var fader: ExoPlayer? = null
    private var rampJob: Job? = null
    private var watchJob: Job? = null
    private var handoff = false

    fun start() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                check()
                delay(250)
            }
        }
    }

    private fun check() {
        if (seconds <= 0 || fader != null || handoff || !main.isPlaying) return
        if (main.repeatMode == Player.REPEAT_MODE_ONE || !main.hasNextMediaItem() || main.pauseAtEndOfMediaItems) return
        val duration = main.duration
        if (duration <= 0 || duration < seconds * 3000L) return
        val left = duration - main.currentPosition
        if (left > seconds * 1000L || left < 800) return
        val next = main.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        begin(main.getMediaItemAt(next), left)
    }

    private fun begin(next: MediaItem, durationMs: Long) {
        val f = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory.create()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false
            )
            .build()
        fader = f
        f.volume = 0f
        f.setMediaItem(next)
        f.prepare()
        f.play()
        rampJob = scope.launch {
            val steps = (durationMs / 100).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                main.volume = 1f - t
                f.volume = t
                delay(100)
            }
        }
    }

    /** Ana oynatıcı kendiliğinden sonraki şarkıya geçti: yardımcının konumuna atla. */
    fun onAutoTransition() {
        val f = fader ?: return
        rampJob?.cancel()
        handoff = true
        main.volume = 0f
        main.seekTo(f.currentPosition + 150)
        scope.launch { delay(5000); if (handoff) finish() } // güvenlik
    }

    /** Ana oynatıcı tekrar çalmaya başladı. */
    fun onMainPlaying() { if (handoff) finish() }

    private fun finish() {
        handoff = false
        main.volume = 1f
        fader?.release()
        fader = null
    }

    /** Kullanıcı atladı / durdurdu: geçişi iptal et. */
    fun cancel() {
        if (fader == null && !handoff) return
        rampJob?.cancel()
        finish()
    }

    val isHandoff get() = handoff

    fun release() {
        watchJob?.cancel()
        cancel()
    }
}
