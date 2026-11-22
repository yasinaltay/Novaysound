package com.novay.sound.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.novay.sound.playback.AudioDataSourceFactory
import com.novay.sound.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/** Dinlerken otomatik dolan önbellek (2 GB, en eskiler silinir). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PlaybackCache

/** Kullanıcının "İndir" dediği şarkılar (asla otomatik silinmez). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DownloadCache

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

    @Provides @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    /** SimpleCache örnekleri TEKİL olmalı (aynı klasöre iki örnek = çökme). */
    @Provides @Singleton @PlaybackCache
    fun providePlaybackCache(
        @ApplicationContext context: Context,
        db: DatabaseProvider,
        settings: SettingsRepository
    ): SimpleCache {
        val mb = runCatching { runBlocking { settings.current().cacheSizeMb } }.getOrDefault(2048)
        return SimpleCache(File(context.filesDir, "audio_cache"), LeastRecentlyUsedCacheEvictor(mb * 1_048_576L), db)
    }

    @Provides @Singleton @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context, db: DatabaseProvider): SimpleCache =
        SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), db)

    @Provides @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        db: DatabaseProvider,
        @DownloadCache cache: SimpleCache,
        factory: AudioDataSourceFactory
    ): DownloadManager =
        DownloadManager(context, db, cache, factory.downloadUpstream(), Executors.newFixedThreadPool(2))
            .apply { maxParallelDownloads = 2 }
}
