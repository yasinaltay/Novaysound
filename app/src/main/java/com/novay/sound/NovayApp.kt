package com.novay.sound

import android.app.Application
import com.novay.sound.data.youtube.NewPipeDownloader
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject

@HiltAndroidApp
class NovayApp : Application() {

    @Inject lateinit var downloader: NewPipeDownloader

    override fun onCreate() {
        super.onCreate() // Hilt alanları burada enjekte eder
        // NewPipe'ı bir kez başlat: Türkçe sonuçlar, Türkiye içerik bölgesi
        NewPipe.init(downloader, Localization("tr", "TR"), ContentCountry("TR"))
    }
}
