package com.novay.sound.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.Album
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumUi {
    data object Loading : AlbumUi
    data class Ready(val album: Album) : AlbumUi
    data class Error(val message: String) : AlbumUi
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository
) : ViewModel() {
    private val url: String = checkNotNull(savedState.get<String>("url"))
    private val _ui = MutableStateFlow<AlbumUi>(AlbumUi.Loading)
    val ui = _ui.asStateFlow()
    val saved = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init { load() }

    fun load() = viewModelScope.launch {
        _ui.value = AlbumUi.Loading
        _ui.value = runCatching { AlbumUi.Ready(youtube.album(url)) }.getOrElse { AlbumUi.Error(it.message ?: "Albüm yüklenemedi") }
    }

    /** Albümü çalma listesi olarak kitaplığa kaydet. */
    fun saveToLibrary(album: Album) = viewModelScope.launch {
        val id = library.createPlaylist("${album.title} – ${album.artist}")
        album.songs.forEach { library.addToPlaylist(id, it) }
        saved.tryEmit("\"${album.title}\" kitaplığa kaydedildi")
    }
}

@Composable
fun AlbumScreen(actions: SongActions, currentId: String?, onBack: () -> Unit, onMessage: (String) -> Unit,
                vm: AlbumViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.saved.collect(onMessage) }
    Box(Modifier.fillMaxSize()) {
        when (val s = ui) {
            AlbumUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is AlbumUi.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Default.ErrorOutline, s.message)
                Button(onClick = { vm.load() }) { Text("Tekrar dene") }
            }
            is AlbumUi.Ready -> {
                val a = s.album
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.background
                            ))).padding(top = 56.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Artwork(a.cover, Modifier.size(220.dp), corner = 16.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(a.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                            Text("${a.artist} • ${a.songs.size} şarkı", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { actions.play(a.songs, 0) }, enabled = a.songs.isNotEmpty(),
                                modifier = Modifier.weight(1f).height(52.dp)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Çal")
                            }
                            FilledTonalIconButton(onClick = { actions.play(a.songs.shuffled(), 0) }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.Shuffle, "Karıştır")
                            }
                            FilledTonalIconButton(onClick = { vm.saveToLibrary(a) }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Kitaplığa kaydet")
                            }
                            FilledTonalIconButton(onClick = { actions.downloadAll(a.songs) }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.Download, "Albümü indir")
                            }
                        }
                    }
                    itemsIndexed(a.songs, key = { _, s -> s.id }) { i, song ->
                        SongRow(song, song.id == currentId, actions, onClick = { actions.play(a.songs, i) })
                    }
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
    }
}
