package com.novay.sound.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.novay.sound.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Telefondaki müzik dosyaları (MediaStore). Şarkı kimliği "local:<id>" biçimindedir. */
@Singleton
class LocalMusicRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun songs(): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID
        )
        val result = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artBase = Uri.parse("content://media/external/audio/albumart")
            while (c.moveToNext()) {
                val a = c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Bilinmeyen sanatçı"
                result += Song(
                    id = "local:${c.getLong(id)}",
                    title = c.getString(title) ?: "Adsız",
                    artist = a,
                    thumbnailUrl = ContentUris.withAppendedId(artBase, c.getLong(album)).toString(),
                    durationSec = c.getLong(duration) / 1000
                )
            }
        }
        result
    }
}
