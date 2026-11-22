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

    override fun onCreate() {
        CrashLogger.install(this)
        super.onCreate() // Hilt alanları burada enjekte eder
        // NewPipe'ı bir kez başlat: Türkçe sonuçlar, Türkiye içerik bölgesi
        NewPipe.init(downloader, Localization("tr", "TR"), ContentCountry("TR"))
        // Chromecast cihaz keşfi için (Google Play Hizmetleri yoksa sessizce atlanır)
        @Suppress("DEPRECATION")
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(this) }
    }
}
