package com.novay.sound.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    /** Dinlenen şarkılar otomatik buraya kaydedilir (çevrimdışı dinleme). */
    private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

    /**
     * SimpleCache uygulama boyunca TEK örnek olmalı (aynı klasöre iki örnek = çökme).
     * filesDir kullanılıyor: cacheDir'i sistem yer açmak için silebilir.
     */
    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.filesDir, "audio_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        )
}
