package com.novay.sound.playback

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Uygulama son kullanılanlardan kaydırılınca haber veren küçük yardımcı servis.
 * Bazı telefonlarda ön plandaki müzik servisine bu haber gelmiyor; bu servis ön planda olmadığı için güvenilir.
 */
class TaskWatcherService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackService.instance?.get()?.onAppTaskRemoved("watcher")
        stopSelf()
    }
}
