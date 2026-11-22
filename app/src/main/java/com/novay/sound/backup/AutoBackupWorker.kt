package com.novay.sound.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.novay.sound.data.backup.BackupManager
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.util.CrashLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Haftada bir, seçilen klasöre JSON yedek yazar. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backup(): BackupManager
        fun settings(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val folder = deps.settings().current().backupFolder ?: return Result.failure()
        return runCatching {
            val tree = Uri.parse(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val doc = DocumentsContract.createDocument(
                applicationContext.contentResolver, parent, "application/json", "novaysound-otomatik-$date.json"
            ) ?: error("Dosya oluşturulamadı")
            deps.backup().export(doc)
            Result.success()
        }.getOrElse {
            CrashLogger.log("Otomatik yedek", it)
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "auto_backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                wm.enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
                )
            } else wm.cancelUniqueWork(NAME)
        }

        /** "Şimdi yedekle" için tek seferlik. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            )
        }
    }
}
