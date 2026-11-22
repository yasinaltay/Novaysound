package com.novay.sound.data.follow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.novay.sound.R
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Günde bir kez takip edilen sanatçıların son yüklemelerine bakar, yeni şarkı varsa bildirir. */
class NewReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun follows(): FollowRepository
        fun youtube(): YouTubeRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val follows = deps.follows()
        follows.artists.value.forEach { a ->
            val latest = runCatching { deps.youtube().artist(a.url).latest.take(15) }.getOrNull() ?: return@forEach
            val ids = latest.map { it.id }.toSet()
            // İlk kontrolde bildirim yok: sadece mevcut şarkıları öğren
            if (a.knownIds.isNotEmpty()) {
                latest.filter { it.id !in a.knownIds }.take(3).forEach { song ->
                    notify(a, "${a.name} yeni şarkı yayınladı", song.title)
                }
            }
            follows.updateKnown(a.url, ids)
        }
        return Result.success()
    }

    private fun notify(a: FollowedArtist, title: String, text: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("releases", "Yeni çıkanlar", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            applicationContext, a.url.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .setAction("com.novay.sound.action.OPEN_ARTIST")
                .putExtra("url", a.url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            (a.url + text).hashCode(),
            NotificationCompat.Builder(applicationContext, "releases")
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "new_releases", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewReleaseWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }
    }
}
