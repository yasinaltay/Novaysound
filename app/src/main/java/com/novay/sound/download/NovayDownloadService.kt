package com.novay.sound.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.novay.sound.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val CHANNEL_ID = "downloads"

/** Ön planda indirme (bildirimde ilerleme), uygulama kapansa da devam eder. */
@OptIn(UnstableApi::class)
class NovayDownloadService : DownloadService(
    2, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, CHANNEL_ID, R.string.download_channel, 0
) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun downloadManager(): DownloadManager }

    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager =
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).downloadManager()

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this, R.drawable.ic_stat_music, null,
            "${downloads.size} şarkı indiriliyor", downloads, notMetRequirements
        )
}
