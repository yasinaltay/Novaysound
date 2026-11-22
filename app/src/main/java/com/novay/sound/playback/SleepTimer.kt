package com.novay.sound.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SleepState(val endAt: Long? = null, val endOfSong: Boolean = false) {
    val active: Boolean get() = endAt != null || endOfSong
}

/** Uyku zamanlayıcısı: süre dolunca PlaybackService sesi kısarak durdurur. */
@Singleton
class SleepTimer @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow(SleepState())
    val state = _state.asStateFlow()

    private val _fire = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fire = _fire.asSharedFlow()

    fun start(minutes: Int) {
        job?.cancel()
        val ms = minutes * 60_000L
        _state.value = SleepState(endAt = System.currentTimeMillis() + ms)
        job = scope.launch {
            delay(ms)
            _state.value = SleepState()
            _fire.tryEmit(Unit)
        }
    }

    /** Çalan şarkı bitince dur. */
    fun endOfSong() {
        job?.cancel()
        _state.value = SleepState(endOfSong = true)
    }

    fun cancel() {
        job?.cancel()
        _state.value = SleepState()
    }
}
