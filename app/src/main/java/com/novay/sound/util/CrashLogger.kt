package com.novay.sound.util

import android.content.Context
import android.os.Build
import com.novay.sound.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Çökmeleri ve önemli hataları dosyaya yazar (Ayarlar > Hata kaydı). */
object CrashLogger {
    private const val MAX_BYTES = 96 * 1024
    private lateinit var file: File

    fun install(context: Context) {
        file = File(context.filesDir, "crash_log.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write("ÇÖKME (${thread.name})", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun log(tag: String, error: Throwable) { runCatching { write(tag, error) } }

    fun read(): String = if (::file.isInitialized && file.exists()) file.readText() else ""
    fun clear() { if (::file.isInitialized) file.delete() }

    @Synchronized
    private fun write(tag: String, error: Throwable) {
        if (!::file.isInitialized) return
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("===== $time | $tag\n")
            append("Sürüm ${BuildConfig.VERSION_NAME} | Android ${Build.VERSION.RELEASE} | ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append(trace.lines().take(40).joinToString("\n"))
            append("\n\n")
        }
        val text = (entry + (if (file.exists()) file.readText() else "")).take(MAX_BYTES) // en yeni üstte
        file.writeText(text)
    }
}
