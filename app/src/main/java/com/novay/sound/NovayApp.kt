package com.novay.sound

import android.app.Application
import com.novay.sound.data.youtube.NewPipeDownloader
import com.novay.sound.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject

@HiltAndroidApp
class NovayApp : Application() {

    @Inject lateinit var downloader: NewPipeDownloader
    @Inject lateinit var settings: com.novay.sound.data.settings.SettingsRepository
    @Inject lateinit var okHttp: okhttp3.OkHttpClient
    @Inject lateinit var follows: com.novay.sound.data.follow.FollowRepository

    override fun onCreate() {
        CrashLogger.install(this)
        super.onCreate() // Hilt alanları burada enjekte eder
        // NewPipe'ı bir kez başlat: Türkçe sonuçlar, Türkiye içerik bölgesi
        NewPipe.init(downloader, Localization("tr", "TR"), ContentCountry("TR"))
        com.novay.sound.alarm.AlarmScheduler.reschedule(this)
        // Önceki çökmeleri Telegram'a gönder (ayarlandıysa)
        com.novay.sound.util.TelegramReporter.sendPending(this, settings, okHttp)
        // Takip edilen sanatçı varsa günlük yeni çıkan kontrolü
        if (follows.artists.value.isNotEmpty()) com.novay.sound.data.follow.NewReleaseWorker.schedule(this)
        // Chromecast cihaz keşfi için (Google Play Hizmetleri yoksa sessizce atlanır)
        @Suppress("DEPRECATION")
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(this) }
    }
}
