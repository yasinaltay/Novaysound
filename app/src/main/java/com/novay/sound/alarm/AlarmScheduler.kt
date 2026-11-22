package com.novay.sound.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.Toggle
import com.novay.sound.playback.PlaybackService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** Müzikli alarm: her gün aynı saatte PlaybackService'i alarm modunda başlatır. */
object AlarmScheduler {
    private const val REQUEST = 4242

    fun nextTrigger(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.cancel(pi)
        if (!enabled) return
        val at = nextTrigger(hour, minute)
        val show = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, REQUEST + 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun settings(): SettingsRepository }

    /** Ayarlara göre yeniden kur (açılış, ayar değişimi, alarm sonrası, cihaz yeniden başlayınca). */
    fun reschedule(context: Context, onDone: () -> Unit = {}) {
        val settings = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).settings()
        CoroutineScope(Dispatchers.IO).launch {
            val s = settings.current()
            schedule(context, s.on(Toggle.ALARM), s.alarmHour, s.alarmMinute)
            onDone()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(
            context, Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_ALARM)
        )
        val pending = goAsync()
        AlarmScheduler.reschedule(context) { pending.finish() } // yarın için
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        AlarmScheduler.reschedule(context) { pending.finish() }
    }
}
