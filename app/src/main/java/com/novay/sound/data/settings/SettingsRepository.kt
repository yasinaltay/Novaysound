package com.novay.sound.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Aydınlık"), DARK("Karanlık") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = true,       // karanlık temada AMOLED siyah
    val artworkColors: Boolean = true    // vurgu rengini albüm kapağından al
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val ARTWORK = booleanPreferencesKey("artwork_colors")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            pureBlack = p[Keys.PURE_BLACK] ?: true,
            artworkColors = p[Keys.ARTWORK] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setPureBlack(on: Boolean) = context.dataStore.edit { it[Keys.PURE_BLACK] = on }
    suspend fun setArtworkColors(on: Boolean) = context.dataStore.edit { it[Keys.ARTWORK] = on }
}
