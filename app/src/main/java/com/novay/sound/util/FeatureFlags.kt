package com.novay.sound.util

import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.Toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ayarların anlık kopyası: suspend olmayan yerlerden (repo, servis) okumak için. */
@Singleton
class FeatureFlags @Inject constructor(settings: SettingsRepository) {
    @Volatile var current: AppSettings = AppSettings()
        private set

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { current = it }
        }
    }

    fun on(t: Toggle) = current.on(t)
}
