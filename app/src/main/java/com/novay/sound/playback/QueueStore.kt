package com.novay.sound.playback

import android.content.Context
import com.novay.sound.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SavedQueue(val songs: List<Song>, val index: Int, val positionMs: Long)

/** Çalma sırasını ve konumu dosyaya yazar; uygulama yeniden açılınca kaldığı yerden devam. */
@Singleton
class QueueStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val file = File(context.filesDir, "queue.json")

    @Synchronized
    fun save(queue: SavedQueue) {
        runCatching {
            val tmp = File(file.parentFile, "queue.json.tmp")
            tmp.writeText(json.encodeToString(SavedQueue.serializer(), queue))
            tmp.renameTo(file)
        }
    }

    fun clear() { runCatching { file.delete() } }

    fun load(): SavedQueue? = runCatching {
        json.decodeFromString(SavedQueue.serializer(), file.readText())
    }.getOrNull()?.takeIf { it.songs.isNotEmpty() }
}
