package com.novay.sound.data.learn

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.novay.sound.util.CrashLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Satır satır Türkçe çeviri; [lines] sözlerin satırlarıyla aynı sırada. */
@Serializable
data class LyricsTranslation(val sourceLang: String, val lines: List<String>) {
    val languageName: String get() = Locale.forLanguageTag(sourceLang).getDisplayLanguage(Locale.forLanguageTag("tr"))
}

/**
 * ML Kit ile telefonda (internetsiz) çeviri. Dil modeli (~30 MB) ilk kullanımda bir kez indirilir.
 * Çeviriler diske yazılır; aynı şarkı tekrar açılınca anında gelir.
 */
@Singleton
class LyricsTranslator @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private val languageId by lazy { LanguageIdentification.getClient() }
    private val translators = ConcurrentHashMap<String, Translator>()

    private val _downloading = MutableStateFlow(false)
    val downloading = _downloading.asStateFlow()

    suspend fun detect(text: String): String? = runCatching {
        languageId.identifyLanguage(text.take(600)).await()
    }.getOrNull()?.takeIf { it != "und" }

    private fun source(lang: String): String? =
        if (lang == "tr") null else TranslateLanguage.fromLanguageTag(lang)

    private suspend fun translator(lang: String): Translator? {
        val src = source(lang) ?: return null
        val t = translators.getOrPut(src) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(TranslateLanguage.TURKISH).build()
            )
        }
        _downloading.value = true
        return try {
            t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            t
        } catch (e: Exception) {
            CrashLogger.log("Çeviri modeli indirilemedi ($lang)", e)
            null
        } finally {
            _downloading.value = false
        }
    }

    /** Sözler Türkçe değilse satır satır çevirir; Türkçe veya desteklenmeyen dilse null. */
    suspend fun translate(songId: String, lines: List<String>): LyricsTranslation? = withContext(Dispatchers.IO) {
        val file = File(dir, songId.replace(':', '_') + ".tr.json")
        runCatching { json.decodeFromString(LyricsTranslation.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.lines.size == lines.size }?.let { return@withContext it }

        val lang = detect(lines.filter { it.isNotBlank() }.joinToString(" ")) ?: return@withContext null
        val t = translator(lang) ?: return@withContext null
        val cache = HashMap<String, String>() // nakaratlar tekrar çevrilmesin
        val out = lines.map { line ->
            if (line.isBlank() || line == "♪") line
            else cache.getOrPut(line) { runCatching { t.translate(line).await() }.getOrDefault("") }
        }
        LyricsTranslation(lang, out).also { tr ->
            runCatching { file.writeText(json.encodeToString(LyricsTranslation.serializer(), tr)) }
        }
    }

    suspend fun translateWord(word: String, lang: String): String? =
        runCatching { translator(lang)?.translate(word)?.await() }.getOrNull()
}
