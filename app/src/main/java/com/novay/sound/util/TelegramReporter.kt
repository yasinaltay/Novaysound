package com.novay.sound.util

import android.content.Context
import com.novay.sound.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Hata kaydındaki yeni çökmeleri Telegram botuna gönderir (uygulama açılışında).
 * Çökme anında ağ isteği güvenilir olmadığı için bir sonraki açılış beklenir.
 */
object TelegramReporter {
    private const val PREF = "tg_reporter"
    private const val LAST = "last_sent_head"

    fun sendPending(context: Context, settings: SettingsRepository, client: OkHttpClient) {
        CoroutineScope(Dispatchers.IO).launch {
            val s = settings.current()
            if (s.telegramToken.isBlank() || s.telegramChatId.isBlank()) return@launch
            val log = CrashLogger.read()
            if (log.isBlank()) return@launch
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val head = log.lineSequence().firstOrNull().orEmpty()   // en yeni kaydın başlığı
            if (head == prefs.getString(LAST, null)) return@launch
            // Son gönderilenden sonraki kayıtlar
            val last = prefs.getString(LAST, null)
            val fresh = if (last != null && log.contains(last)) log.substringBefore(last) else log
            if (send(client, s.telegramToken, s.telegramChatId, "🐞 Novay Sound hata kaydı\n\n" + fresh.take(3800))) {
                prefs.edit().putString(LAST, head).apply()
            }
        }
    }

    fun send(client: OkHttpClient, token: String, chatId: String, text: String): Boolean = runCatching {
        client.newCall(
            Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(FormBody.Builder().add("chat_id", chatId).add("text", text).build())
                .build()
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
