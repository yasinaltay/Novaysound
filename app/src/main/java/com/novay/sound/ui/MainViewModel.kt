package com.novay.sound.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.YouTubeLink
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.ui.components.SongActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val player: PlayerConnection,
    private val library: LibraryRepository,
    private val youtube: YouTubeRepository
) : ViewModel() {

    val playerState = player.state
    val playlists = library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** Çalma listesine eklenmek üzere seçilen şarkı (dialog açık). */
    val playlistTarget = MutableStateFlow<Song?>(null)

    val actions = SongActions(
        play = player::play,
        playNext = { player.playNext(it); msg("Sıradaki: ${it.title}") },
        enqueue = { player.enqueue(it); msg("Sıraya eklendi") },
        radio = player::startRadio,
        toggleLike = { s -> viewModelScope.launch { library.toggleLike(s) } },
        addToPlaylist = { playlistTarget.value = it }
    )

    fun isLiked(id: String): Flow<Boolean> = library.isLiked(id)

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch {
        val added = library.addToPlaylist(playlistId, song)
        msg(if (added) "Listeye eklendi" else "Bu şarkı zaten listede")
        playlistTarget.value = null
    }

    fun createPlaylistAndAdd(name: String, song: Song) = viewModelScope.launch {
        val id = library.createPlaylist(name)
        library.addToPlaylist(id, song)
        msg("\"$name\" oluşturuldu")
        playlistTarget.value = null
    }

    /** Paylaşılan / yapıştırılan metin YouTube linkiyse işle. Link değilse false. */
    fun handleText(text: String): Boolean {
        val link = youtube.parseLink(text) ?: return false
        viewModelScope.launch {
            when (link) {
                is YouTubeLink.Video -> {
                    val song = runCatching { youtube.getSong(link.id) }.getOrElse {
                        msg("Video açılamadı: ${it.message}"); return@launch
                    }
                    player.startRadio(song)
                }
                is YouTubeLink.Playlist -> {
                    msg("Çalma listesi alınıyor…")
                    val (name, songs) = runCatching { youtube.getPlaylist(link.url) }.getOrElse {
                        msg("Liste alınamadı: ${it.message}"); return@launch
                    }
                    if (songs.isEmpty()) { msg("Liste boş"); return@launch }
                    val id = library.createPlaylist(name)
                    songs.forEach { library.addToPlaylist(id, it) }
                    player.play(songs)
                    msg("\"$name\" kitaplığa kaydedildi (${songs.size} şarkı)")
                }
            }
        }
        return true
    }

    /** Sesli komut (MEDIA_PLAY_FROM_SEARCH) uygulama ekranına gelirse. */
    fun playFromSearch(query: String) = viewModelScope.launch {
        if (query.isBlank()) {
            val favs = library.getFavorites().shuffled().ifEmpty { library.getRecent() }
            if (favs.isNotEmpty()) player.play(favs) else msg("Kitaplık boş")
            return@launch
        }
        val songs = runCatching { youtube.search(query).songs }.getOrElse { library.searchLocal(query) }
        songs.firstOrNull()?.let(player::startRadio) ?: msg("\"$query\" bulunamadı")
    }

    private fun msg(text: String) { _messages.tryEmit(text) }
}
