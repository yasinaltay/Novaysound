package com.novay.sound.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.novay.sound.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class GhAsset(val name: String, val browser_download_url: String, val size: Long = 0)
@Serializable private data class GhRelease(val tag_name: String, val name: String? = null, val body: String? = null, val assets: List<GhAsset> = emptyList())

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** GitHub Releases üzerinden uygulama içi güncelleme. */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    companion object {
        const val REPO = "yasinaltay/Novaysound"
    }

    /** Daha yeni sürüm varsa döner, yoksa null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub yanıtı: ${r.code}")
            r.body?.string().orEmpty()
        }
        val rel = json.decodeFromString(GhRelease.serializer(), body)
        val version = rel.tag_name.removePrefix("v")
        val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        if (isNewer(version, BuildConfig.VERSION_NAME)) UpdateInfo(version, rel.body.orEmpty(), apk.browser_download_url, apk.size)
        else null
    }

    /** APK'yı indirir, ilerlemeyi 0..1 olarak bildirir. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "NovaySound-${info.version}.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) error("İndirme başarısız: ${r.code}")
            val body = r.body ?: error("Boş yanıt")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        out
    }

    /** "Bilinmeyen uygulamaları yükle" izni gerekiyorsa false. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val l = local.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
