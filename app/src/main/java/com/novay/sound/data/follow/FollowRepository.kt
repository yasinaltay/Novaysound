package com.novay.sound.data.follow

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FollowedArtist(
    val name: String,
    val url: String,
    val avatar: String? = null,
    val knownIds: Set<String> = emptySet()   // daha önce görülen şarkılar (yeni çıkanı bulmak için)
)

/** Takip edilen sanatçılar; yeni şarkı çıkınca NewReleaseWorker bildirim gönderir. */
@Singleton
class FollowRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val file = File(context.filesDir, "followed_artists.json")
    private val ser = ListSerializer(FollowedArtist.serializer())
    private val _artists = MutableStateFlow(runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyList()))
    val artists = _artists.asStateFlow()

    fun isFollowing(url: String) = _artists.value.any { it.url == url }

    @Synchronized
    fun follow(artist: FollowedArtist) {
        if (isFollowing(artist.url)) return
        save(_artists.value + artist)
    }

    @Synchronized
    fun unfollow(url: String) = save(_artists.value.filter { it.url != url })

    @Synchronized
    fun updateKnown(url: String, ids: Set<String>) =
        save(_artists.value.map { if (it.url == url) it.copy(knownIds = (it.knownIds + ids).toList().takeLast(300).toSet()) else it })

    private fun save(list: List<FollowedArtist>) {
        _artists.value = list
        runCatching { file.writeText(json.encodeToString(ser, list)) }
    }
}
