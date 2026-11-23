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
class AudioEffects @Inject constructor(
    private val settings: SettingsRepository,
    private val output: AudioOutput
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var equalizer: Equalizer? = null
    private var dynamics: DynamicsProcessing? = null
    private var enhancer: android.media.audiofx.LoudnessEnhancer? = null

    /** Android Auto bağlıyken true (PlaybackService ayarlar). */
    val carMode = MutableStateFlow(false)

    private val _info = MutableStateFlow<EqInfo?>(null)
    val info = _info.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return
        equalizer = runCatching { Equalizer(0, sessionId) }.onFailure { CrashLogger.log("Ekolayzer açılamadı", it) }.getOrNull()
        equalizer?.runCatching {
            val eq = this
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
        }?.onFailure { CrashLogger.log("Ekolayzer bilgisi okunamadı", it); _info.value = null }
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
        enhancer = runCatching { android.media.audiofx.LoudnessEnhancer(sessionId) }
            .onFailure { CrashLogger.log("Ses yükseltme açılamadı", it) }.getOrNull()
        // Ayar veya ses çıkışı (hoparlör/kulaklık/Bluetooth) değişince yeniden uygula
        job = scope.launch {
            kotlinx.coroutines.flow.combine(settings.settings, output.current, carMode) { s, o, car -> Triple(s, o, car) }
                .collect { (s, o, car) -> apply(s, o.key, car) }
        }
    }

    /** Araba için eğri: bas +4 dB, orta düz, üst orta +1 dB, tiz +3 dB (millibel). */
    private fun carLevels(): List<Int>? {
        val info = _info.value ?: return null
        return info.bandFreqsHz.map { hz ->
            when {
                hz < 250 -> 400
                hz < 1000 -> 0
                hz < 4000 -> 100
                else -> 300
            }.coerceIn(info.minLevel, info.maxLevel)
        }
    }

    private fun apply(s: AppSettings, outputKey: String, car: Boolean = false) {
        // Cihaza özel profil varsa onu, yoksa genel ayarı kullan
        val deviceProfile = if (s.on(com.novay.sound.data.settings.Toggle.EQ_PER_DEVICE)) s.eqProfiles[outputKey]?.let(::parseProfile) else null
        val carProfile = if (car && deviceProfile == null && s.on(com.novay.sound.data.settings.Toggle.CAR_SOUND))
            carLevels()?.let { Triple(true, -1, it) } else null
        val profile = deviceProfile ?: carProfile
        val on = profile?.first ?: s.eqEnabled
        val preset = profile?.second ?: s.eqPreset
        val levels = profile?.third ?: s.eqBands
        equalizer?.runCatching {
            enabled = on
            if (on) {
                val bands = numberOfBands.toInt()
                when {
                    levels.size == bands -> levels.forEachIndexed { i, lvl -> setBandLevel(i.toShort(), lvl.toShort()) }
                    preset >= 0 -> usePreset(preset.toShort())
                }
            }
        }
        dynamics?.runCatching { enabled = s.normalize || carProfile != null }
        val boost = if (carProfile != null) maxOf(s.boostDb, 2) else s.boostDb
        enhancer?.runCatching {
            setTargetGain(boost * 100) // millibel
            enabled = boost > 0
        }
    }

    private fun parseProfile(v: String): Triple<Boolean, Int, List<Int>>? = runCatching {
        val (a, b, c) = v.split('|')
        Triple(a.toBoolean(), b.toInt(), c.split(',').mapNotNull { it.toIntOrNull() })
    }.getOrNull()

    fun release() {
        job?.cancel()
        equalizer?.runCatching { release() }
        dynamics?.runCatching { release() }
        enhancer?.runCatching { release() }
        enhancer = null
        equalizer = null
        dynamics = null
    }
}
