package com.novay.sound.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import com.novay.sound.download.DlState
import com.novay.sound.ui.components.LocalDownloads
import com.novay.sound.ui.components.SectionTitle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.backup.BackupManager
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.playback.LibraryTree
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongRow
import com.novay.sound.ui.components.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val tree: LibraryTree,
    private val backup: BackupManager
) : ViewModel() {
    private fun <T> Flow<List<T>>.state() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = library.favorites.state()
    val recent = library.recent.state()
    val playlists = library.playlists.state()

    private val _offline = MutableStateFlow<List<Song>>(emptyList())
    val offline = _offline.asStateFlow()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun refreshOffline() = viewModelScope.launch { _offline.value = tree.cachedOnlySongs() }
    fun createPlaylist(name: String) = viewModelScope.launch { library.createPlaylist(name) }
    fun clearHistory() = viewModelScope.launch { library.clearHistory(); messages.tryEmit("Geçmiş temizlendi") }
    fun unlike(song: Song) = viewModelScope.launch { library.setLiked(song, false) }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { backup.export(uri) }
            .onSuccess { messages.tryEmit("Yedeklendi: ${it.songs} şarkı, ${it.playlists} liste, ${it.favorites} favori") }
            .onFailure { messages.tryEmit("Yedekleme başarısız: ${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { backup.import(uri) }
            .onSuccess { messages.tryEmit("Geri yüklendi: ${it.songs} şarkı, ${it.playlists} liste") }
            .onFailure { messages.tryEmit("Geri yükleme başarısız: ${it.message}") }
    }
}

private val TABS = listOf("Favoriler", "Çalma listeleri", "Geçmiş", "İndirilenler")

@Composable
fun LibraryScreen(
    actions: SongActions,
    currentId: String?,
    onOpenPlaylist: (Long) -> Unit,
    onOpenStats: () -> Unit,
    vm: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var newPlaylist by rememberSaveable { mutableStateOf(false) }
    var confirmImport by rememberSaveable { mutableStateOf<String?>(null) }

    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> confirmImport = uri.toString() }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(tab) { if (tab == 3) vm.refreshOffline() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kitaplık", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenStats) { Icon(Icons.Default.Insights, "İstatistikler") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Yedekle (JSON)") }, onClick = {
                        menu = false
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("novaysound-yedek-$date.json")
                    })
                    DropdownMenuItem(text = { Text("Yedekten geri yükle") }, onClick = {
                        menu = false
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    })
                    DropdownMenuItem(text = { Text("Geçmişi temizle") }, onClick = { menu = false; vm.clearHistory() })
                }
            }
        }

        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
            TABS.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        when (tab) {
            0 -> SongList(
                songs = favorites, currentId = currentId,
                actions = actions.copy(remove = vm::unlike, removeLabel = "Favorilerden kaldır"),
                empty = { EmptyState(Icons.Default.FavoriteBorder, "Favori şarkın yok") }
            )
            1 -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { newPlaylist = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Yeni çalma listesi", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(playlists, key = { it.playlist.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (p.coverUrl != null) Artwork(p.coverUrl, Modifier.size(56.dp))
                        else Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.playlist.name, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.songCount} şarkı", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            2 -> SongList(recent, currentId, actions) { EmptyState(Icons.Default.History, "Geçmiş boş") }
            3 -> DownloadsTab(offline, currentId, actions)
        }
    }

    if (newPlaylist) {
        TextInputDialog(title = "Yeni çalma listesi", confirm = "Oluştur",
            onDismiss = { newPlaylist = false },
            onConfirm = { vm.createPlaylist(it); newPlaylist = false })
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Geri yüklensin mi?") },
            text = { Text("Mevcut favoriler, geçmiş ve çalma listeleri silinip yedektekilerle değiştirilecek.") },
            confirmButton = { TextButton(onClick = { vm.import(Uri.parse(uri)); confirmImport = null }) { Text("Geri yükle") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("İptal") } }
        )
    }
}

/** İndirilenler (kalıcı) + otomatik önbellekte tamamı olanlar. */
@Composable
private fun DownloadsTab(cached: List<Song>, currentId: String?, actions: SongActions) {
    val items = LocalDownloads.current.values.sortedByDescending { it.updatedAt }
    val done = items.filter { it.state == DlState.DONE }.map { it.song }
    val pending = items.filter { it.state != DlState.DONE }.map { it.song }
    if (items.isEmpty() && cached.isEmpty()) {
        EmptyState(Icons.Default.CloudOff, "İndirilmiş şarkı yok.\nŞarkı menüsünden \"İndir\" diyerek internetsiz dinleyebilirsin.")
        return
    }
    val removable = actions.copy(remove = { actions.removeDownload(it.id) }, removeLabel = "İndirmeyi sil")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (done.isNotEmpty()) {
            item { PlayShuffleRow(onPlay = { actions.play(done, 0) }, onShuffle = { actions.play(done.shuffled(), 0) }) }
            itemsIndexed(done, key = { _, s -> "d" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, removable, onClick = { actions.play(done, i) })
            }
        }
        if (pending.isNotEmpty()) {
            item { SectionTitle("İndiriliyor") }
            itemsIndexed(pending, key = { _, s -> "p" + s.id }) { _, song ->
                SongRow(song, song.id == currentId, removable, onClick = { })
            }
        }
        if (cached.isNotEmpty()) {
            item { SectionTitle("Önbellekte (otomatik)") }
            itemsIndexed(cached, key = { _, s -> "c" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, actions, onClick = { actions.play(cached, i) })
            }
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    currentId: String?,
    actions: SongActions,
    empty: @Composable () -> Unit
) {
    if (songs.isEmpty()) { empty(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PlayShuffleRow(onPlay = { actions.play(songs, 0) }, onShuffle = { actions.play(songs.shuffled(), 0) }) }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(song, song.id == currentId, actions, onClick = { actions.play(songs, i) })
        }
    }
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Çal")
        }
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Karıştır")
        }
    }
}
