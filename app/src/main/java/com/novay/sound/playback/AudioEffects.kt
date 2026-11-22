package com.novay.sound.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ekolayzer ekranının ihtiyaç duyduğu cihaz bilgisi. */
data class EqInfo(
    val bandFreqsHz: List<Int>,
    val minLevel: Int,     // millibel
    val maxLevel: Int,
    val presets: List<String>,
    val presetLevels: List<List<Int>>
)

/**
 * Ekolayzer + ses dengeleme (DynamicsProcessing limiter/kompresör).
 * PlaybackService oynatıcının audioSessionId'sini verir; ayarlar değiştikçe uygulanır.
 */
@Singleton
class AudioEffects @Inject constructor(private val settings: SettingsRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var equalizer: Equalizer? = null
    private var dynamics: DynamicsProcessing? = null

    private val _info = MutableStateFlow<EqInfo?>(null)
    val info = _info.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return
        equalizer = runCatching { Equalizer(0, sessionId) }.onFailure { CrashLogger.log("Ekolayzer açılamadı", it) }.getOrNull()
        equalizer?.let { eq ->
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val presetNames = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            val presetLevels = (0 until eq.numberOfPresets).map { p ->
                runCatching {
                    eq.usePreset(p.toShort())
                    (0 until bands).map { b -> eq.getBandLevel(b.toShort()).toInt() }
                }.getOrDefault(List(bands) { 0 })
            }
            _info.value = EqInfo(
                bandFreqsHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                minLevel = range[0].toInt(), maxLevel = range[1].toInt(),
                presets = presetNames, presetLevels = presetLevels
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics = runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, false, 0, false, 0, true
                ).build()
                DynamicsProcessing(0, sessionId, config).apply {
                    // attack, release, oran, eşik (dB), sonrası kazanç (dB)
                    setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 1, 1f, 80f, 8f, -14f, 7f))
                }
            }.onFailure { CrashLogger.log("Ses dengeleme açılamadı", it) }.getOrNull()
        }
        job = scope.launch { settings.settings.collect(::apply) }
    }

    private fun apply(s: AppSettings) {
        equalizer?.runCatching {
            enabled = s.eqEnabled
            if (s.eqEnabled) {
                val bands = numberOfBands.toInt()
                when {
                    s.eqBands.size == bands -> s.eqBands.forEachIndexed { i, lvl -> setBandLevel(i.toShort(), lvl.toShort()) }
                    s.eqPreset >= 0 -> usePreset(s.eqPreset.toShort())
                }
            }
        }
        dynamics?.runCatching { enabled = s.normalize }
    }

    fun release() {
        job?.cancel()
        equalizer?.runCatching { release() }
        dynamics?.runCatching { release() }
        equalizer = null
        dynamics = null
    }
}
