package com.novay.sound.playback

import android.content.Context
import android.net.ConnectivityManager
import com.novay.sound.data.settings.AudioQuality
import com.novay.sound.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Oynatma anında hangi kalitede akış isteneceğine karar verir (veri dostu). */
@Singleton
class QualityPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository
) {
    @Volatile private var mode = AudioQuality.HIGH

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { mode = it.audioQuality }
        }
    }

    fun preferLow(): Boolean = when (mode) {
        AudioQuality.HIGH -> false
        AudioQuality.LOW -> true
        AudioQuality.AUTO -> runCatching {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        }.getOrDefault(false)
    }
}
