package com.novay.sound.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Ayarlardan açılıp kapatılabilen özellikler. */
enum class Toggle(val key: String, val default: Boolean, val title: String, val subtitle: String) {
    KEEP_PLAYING("f_keep_playing", false, "Uygulama kapatılınca çalmaya devam et", "Kapalıysa son kullanılanlardan kaydırınca müzik durur"),
    AUTO_RADIO("f_auto_radio", true, "Sonsuz radyo", "Sıra bitince benzer şarkıları otomatik ekle"),
    CLEAN_TITLES("f_clean_titles", true, "Başlık temizleme", "\"(Official Video)\" gibi ekleri sil, sanatçıyı ayır"),
    DEDUPE("f_dedupe", true, "Kopyaları gizle", "Aynı şarkının klip/lyric/audio sürümlerini aramada tek göster"),
    SHOW_SMART("f_show_smart", true, "Akıllı listeler (ana ekran)", "Bu ay en çok, unuttuğun favoriler, haftalık keşif"),
    SHOW_MOODS("f_show_moods", true, "Ruh hali listeleri (ana ekran)", "Spor, Odak, Yol, Sakin…"),
    SHOW_QUICK("f_show_quick", true, "Hızlı seçimler (ana ekran)", "Son dinlediğine benzer şarkılar"),
    SHOW_TRENDING("f_show_trending", true, "Trendler (ana ekran)", "Türkiye'de trend olan şarkılar"),
    RECORD_STATS("f_record_stats", true, "Dinleme istatistiği", "Dinlediklerini istatistik ve akıllı listeler için kaydet"),
    SEARCH_HISTORY("f_search_history", true, "Arama geçmişi", "Son aramaları kaydet"),
    LYRICS_TRANSLATE("f_lyrics_tr", true, "Söz çevirisi", "Almanca, İngilizce… sözlerin altında Türkçe çeviri (internetsiz)"),
    LEARN_MODE("f_learn", false, "Öğrenme modu", "Sözlerde kelimeye dokununca anlamı; kelime defterine kaydet"),
    BLUR_BG("f_blur_bg", true, "Bulanık kapak arka planı", "Tam ekran oynatıcıda şarkı kapağı arka plan olur"),
    LOCK_LYRICS("f_lock_lyrics", false, "Kilit ekranında sözler (deneysel)", "Bildirim ve kilit ekranında sanatçı yerine o anki söz satırı"),
    AUTO_PLAY_BT("f_auto_play_bt", false, "Bluetooth bağlanınca çal", "Araba/kulaklık bağlanınca kaldığın yerden devam et"),
    EQ_PER_DEVICE("f_eq_per_device", false, "Cihaza özel ekolayzer", "Hoparlör, kulaklık ve her Bluetooth cihazı için ayrı ayar"),
    AUTO_SLEEP("f_auto_sleep", false, "Otomatik uyku zamanlayıcısı", "Gece çalmaya başlayınca zamanlayıcıyı kendiliğinden kur"),
    ALARM("f_alarm", false, "Müzikli alarm", "Her sabah seçtiğin saatte müzikle uyandır"),
    SKIP_SILENCE("f_skip_silence", false, "Sessizliği atla", "Şarkı başı/sonundaki uzun sessiz kısımları atla"),
    AUTO_DL_LIKED("f_auto_dl_liked", false, "Beğenilenleri otomatik indir", "Beğendiğin şarkılar Wi-Fi'dayken çevrimdışına iner")
}

enum class AudioQuality(val label: String) { AUTO("Otomatik"), HIGH("Yüksek"), LOW("Düşük") }

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Aydınlık"), DARK("Karanlık") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = true,        // karanlık temada AMOLED siyah
    val artworkColors: Boolean = true,    // vurgu rengini albüm kapağından al
    val sponsorBlock: Boolean = true,     // müzik olmayan kısımları atla
    val normalize: Boolean = false,       // ses dengeleme
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,               // -1 = özel
    val eqBands: List<Int> = emptyList(), // millibel
    val crossfadeSec: Int = 0,            // 0 = kapalı
    val audioQuality: AudioQuality = AudioQuality.HIGH, // AUTO: Wi-Fi yüksek, mobil veri düşük
    val wifiOnlyDownloads: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    val autoBackup: Boolean = false,
    val backupFolder: String? = null,     // SAF klasör Uri'si
    val toggles: Map<Toggle, Boolean> = emptyMap(),
    val autoSleepHour: Int = 23,
    val autoSleepMinutes: Int = 30,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 30,
    val alarmSource: String = "FAVORITES", // FAVORITES | DISCOVER
    val boostDb: Int = 0,                  // ses yükseltme 0..6
    val cacheSizeMb: Int = 2048,
    val btDevices: Set<String> = emptySet(),        // otomatik çalacak Bluetooth adresleri (boş = tüm ses cihazları)
    val eqProfiles: Map<String, String> = emptyMap(), // çıkış -> "açık|preset|b1,b2,…"
    val telegramToken: String = "",
    val telegramChatId: String = "",
    val ticketmasterKey: String = ""
) {
    fun on(t: Toggle): Boolean = toggles[t] ?: t.default
}

private val Context.dataStore by preferencesDataStore(name = "settings")
private fun intKey(name: String) = intPreferencesKey(name)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val ARTWORK = booleanPreferencesKey("artwork_colors")
        val SPONSOR = booleanPreferencesKey("sponsorblock")
        val NORMALIZE = booleanPreferencesKey("normalize")
        val EQ_ON = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val CROSSFADE = stringPreferencesKey("crossfade_sec")
        val QUALITY = stringPreferencesKey("audio_quality")
        val WIFI_DL = booleanPreferencesKey("wifi_only_downloads")
        val HISTORY = stringPreferencesKey("search_history")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val BACKUP_FOLDER = stringPreferencesKey("backup_folder")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            pureBlack = p[Keys.PURE_BLACK] ?: true,
            artworkColors = p[Keys.ARTWORK] ?: true,
            sponsorBlock = p[Keys.SPONSOR] ?: true,
            normalize = p[Keys.NORMALIZE] ?: false,
            eqEnabled = p[Keys.EQ_ON] ?: false,
            eqPreset = p[Keys.EQ_PRESET]?.toIntOrNull() ?: -1,
            eqBands = p[Keys.EQ_BANDS]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
            crossfadeSec = p[Keys.CROSSFADE]?.toIntOrNull() ?: 0,
            audioQuality = p[Keys.QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: AudioQuality.HIGH,
            wifiOnlyDownloads = p[Keys.WIFI_DL] ?: false,
            autoUpdateCheck = p[Keys.UPDATE_CHECK] ?: true,
            autoBackup = p[Keys.AUTO_BACKUP] ?: false,
            backupFolder = p[Keys.BACKUP_FOLDER],
            toggles = Toggle.entries.associateWith { t -> p[booleanPreferencesKey(t.key)] ?: t.default },
            autoSleepHour = p[intKey("auto_sleep_hour")] ?: 23,
            autoSleepMinutes = p[intKey("auto_sleep_minutes")] ?: 30,
            alarmHour = p[intKey("alarm_hour")] ?: 7,
            alarmMinute = p[intKey("alarm_minute")] ?: 30,
            alarmSource = p[stringPreferencesKey("alarm_source")] ?: "FAVORITES",
            boostDb = p[intKey("boost_db")] ?: 0,
            cacheSizeMb = p[intKey("cache_size_mb")] ?: 2048,
            btDevices = p[stringPreferencesKey("bt_devices")]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            eqProfiles = p.asMap().filterKeys { it.name.startsWith("eqp_") }
                .map { (k, v) -> k.name.removePrefix("eqp_") to v.toString() }.toMap(),
            telegramToken = p[stringPreferencesKey("tg_token")].orEmpty(),
            telegramChatId = p[stringPreferencesKey("tg_chat")].orEmpty(),
            ticketmasterKey = p[stringPreferencesKey("tm_key")].orEmpty()
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun set(block: (MutablePreferences) -> Unit) { context.dataStore.edit { block(it) } }

    suspend fun setThemeMode(mode: ThemeMode) = set { it[Keys.THEME] = mode.name }
    suspend fun setPureBlack(on: Boolean) = set { it[Keys.PURE_BLACK] = on }
    suspend fun setArtworkColors(on: Boolean) = set { it[Keys.ARTWORK] = on }
    suspend fun setSponsorBlock(on: Boolean) = set { it[Keys.SPONSOR] = on }
    suspend fun setNormalize(on: Boolean) = set { it[Keys.NORMALIZE] = on }
    suspend fun setEqEnabled(on: Boolean) = set { it[Keys.EQ_ON] = on }
    suspend fun setEq(preset: Int, bands: List<Int>) = set {
        it[Keys.EQ_PRESET] = preset.toString()
        it[Keys.EQ_BANDS] = bands.joinToString(",")
    }
    suspend fun setToggle(t: Toggle, on: Boolean) = set { it[booleanPreferencesKey(t.key)] = on }
    suspend fun setInt(name: String, value: Int) = set { it[intKey(name)] = value }
    suspend fun setBtDevices(addrs: Set<String>) = set { it[stringPreferencesKey("bt_devices")] = addrs.joinToString(",") }
    suspend fun setEqProfile(output: String, enabled: Boolean, preset: Int, bands: List<Int>) = set {
        it[stringPreferencesKey("eqp_$output")] = "$enabled|$preset|${bands.joinToString(",")}"
    }
    suspend fun setTelegram(token: String, chatId: String) = set {
        it[stringPreferencesKey("tg_token")] = token.trim()
        it[stringPreferencesKey("tg_chat")] = chatId.trim()
    }

    suspend fun setTicketmasterKey(key: String) = set { it[stringPreferencesKey("tm_key")] = key.trim() }

    suspend fun setAlarmSource(src: String) = set { it[stringPreferencesKey("alarm_source")] = src }

    suspend fun setAudioQuality(q: AudioQuality) = set { it[Keys.QUALITY] = q.name }
    suspend fun setWifiOnlyDownloads(on: Boolean) = set { it[Keys.WIFI_DL] = on }

    // ---- Arama geçmişi (son 15) ----
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { p ->
        p[Keys.HISTORY]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }
    suspend fun addSearch(q: String) = set { p ->
        val list = p[Keys.HISTORY]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        p[Keys.HISTORY] = (listOf(q.trim()) + list.filter { !it.equals(q.trim(), true) }).take(15).joinToString("\n")
    }
    suspend fun removeSearch(q: String) = set { p ->
        p[Keys.HISTORY] = p[Keys.HISTORY]?.split('\n')?.filter { it != q }?.joinToString("\n").orEmpty()
    }
    suspend fun clearSearches() = set { it.remove(Keys.HISTORY) }

    suspend fun setCrossfade(sec: Int) = set { it[Keys.CROSSFADE] = sec.toString() }
    suspend fun setAutoUpdateCheck(on: Boolean) = set { it[Keys.UPDATE_CHECK] = on }
    suspend fun setAutoBackup(on: Boolean, folder: String?) = set {
        it[Keys.AUTO_BACKUP] = on
        if (folder != null) it[Keys.BACKUP_FOLDER] = folder
    }
}
