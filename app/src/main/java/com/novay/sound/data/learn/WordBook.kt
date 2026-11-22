package com.novay.sound.data.learn

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SavedWord(
    val word: String,
    val meaning: String,
    val lang: String,
    val song: String,      // "Şarkı – Sanatçı"
    val line: String,      // kelimenin geçtiği söz satırı (bağlam)
    val savedAt: Long = System.currentTimeMillis()
)

/** Şarkı sözlerinden kaydedilen kelimeler (kelime defteri). */
@Singleton
class WordBook @Inject constructor(@ApplicationContext context: Context, private val json: Json) {
    private val file = File(context.filesDir, "saved_words.json")
    private val ser = ListSerializer(SavedWord.serializer())
    private val _words = MutableStateFlow(runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyList()))
    val words = _words.asStateFlow()

    fun contains(word: String, lang: String) = _words.value.any { it.word.equals(word, true) && it.lang == lang }

    @Synchronized fun add(w: SavedWord) {
        if (contains(w.word, w.lang)) return
        save(listOf(w) + _words.value)
    }

    @Synchronized fun remove(w: SavedWord) = save(_words.value.filter { it != w })

    private fun save(list: List<SavedWord>) {
        _words.value = list
        runCatching { file.writeText(json.encodeToString(ser, list)) }
    }
}
