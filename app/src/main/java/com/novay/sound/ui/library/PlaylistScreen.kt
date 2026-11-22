package com.novay.sound.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongRow
import com.novay.sound.ui.components.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository
) : ViewModel() {
    val id: Long = checkNotNull(savedState.get<Long>("id"))

    val songs = library.playlistSongs(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val name = library.playlists.map { list -> list.firstOrNull { it.playlist.id == id }?.playlist?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun remove(song: Song) = viewModelScope.launch { library.removeFromPlaylist(id, song.id) }
    fun rename(newName: String) = viewModelScope.launch { library.renamePlaylist(id, newName) }
    fun delete() = viewModelScope.launch { library.deletePlaylist(id) }
    fun move(from: Int, to: Int) = viewModelScope.launch { library.movePlaylistSong(id, from, to) }
}

@Composable
fun PlaylistScreen(
    actions: SongActions,
    currentId: String?,
    onBack: () -> Unit,
    vm: PlaylistViewModel = hiltViewModel()
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${songs.size} şarkı", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Listeyi sil") }, onClick = { menu = false; delete = true })
                }
            }
        }

        if (songs.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Liste boş.\nŞarkı menüsünden \"Çalma listesine ekle\" ile ekleyebilirsin.")
        } else {
            val rowActions = actions.copy(remove = vm::remove)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { PlayShuffleRow(onPlay = { actions.play(songs, 0) }, onShuffle = { actions.play(songs.shuffled(), 0) }) }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    SongRow(song, song.id == currentId, rowActions, onClick = { actions.play(songs, i) })
                }
            }
        }
    }

    if (rename) {
        TextInputDialog(title = "Yeniden adlandır", initial = name,
            onDismiss = { rename = false }, onConfirm = { vm.rename(it); rename = false })
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("\"$name\" silinsin mi?") },
            text = { Text("Şarkılar favorilerinden ve geçmişinden silinmez.") },
            confirmButton = { TextButton(onClick = { vm.delete(); delete = false; onBack() }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("İptal") } }
        )
    }
}
