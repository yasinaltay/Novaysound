package com.novay.sound.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
    val autoUpdateCheck: Boolean = true,
    val autoBackup: Boolean = false,
    val backupFolder: String? = null      // SAF klasör Uri'si
)

private val Context.dataStore by preferencesDataStore(name = "settings")

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
            autoUpdateCheck = p[Keys.UPDATE_CHECK] ?: true,
            autoBackup = p[Keys.AUTO_BACKUP] ?: false,
            backupFolder = p[Keys.BACKUP_FOLDER]
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
    suspend fun setCrossfade(sec: Int) = set { it[Keys.CROSSFADE] = sec.toString() }
    suspend fun setAutoUpdateCheck(on: Boolean) = set { it[Keys.UPDATE_CHECK] = on }
    suspend fun setAutoBackup(on: Boolean, folder: String?) = set {
        it[Keys.AUTO_BACKUP] = on
        if (folder != null) it[Keys.BACKUP_FOLDER] = folder
    }
}
