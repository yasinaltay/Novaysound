package com.novay.sound.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SectionTitle
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongCard
import com.novay.sound.ui.components.SongRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val youtube: YouTubeRepository
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.state() =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recent = library.recent.state()
    val favorites = library.favorites.state()
    val mostPlayed = library.mostPlayed.state()

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks = _quickPicks.asStateFlow()

    init { refreshQuickPicks() }

    /** Son dinlenen şarkıya benzeyenlerden "Hızlı seçimler". */
    fun refreshQuickPicks() = viewModelScope.launch {
        val seed = library.getRecent().firstOrNull() ?: return@launch
        _quickPicks.value = runCatching { youtube.radio(seed.id) }.getOrDefault(emptyList()).take(10)
    }
}

@Composable
fun HomeScreen(
    actions: SongActions,
    currentId: String?,
    onOpenSearch: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val quick by vm.quickPicks.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Novay Sound", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "Ara") }
            }
        }

        if (recent.isEmpty() && favorites.isEmpty()) {
            item {
                EmptyState(Icons.Default.MusicNote, "Henüz bir şey dinlemedin.\nArayarak başla!")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = onOpenSearch) { Text("Şarkı ara") }
                }
            }
        }

        if (quick.isNotEmpty()) {
            item { SectionTitle("Hızlı seçimler") }
            itemsIndexed(quick, key = { _, s -> "q" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, actions, onClick = { actions.play(quick, i) })
            }
        }

        horizontalSection("Son çalınanlar", recent, actions)
        horizontalSection("Favoriler", favorites, actions)

        if (mostPlayed.isNotEmpty()) {
            item { SectionTitle("En çok dinlenenler") }
            itemsIndexed(mostPlayed.take(10), key = { _, s -> "m" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, actions, onClick = { actions.play(mostPlayed, i) })
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.horizontalSection(
    title: String, songs: List<Song>, actions: SongActions
) {
    if (songs.isEmpty()) return
    item(key = "title_$title") { SectionTitle(title) }
    item(key = "row_$title") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                SongCard(song, onClick = { actions.play(songs, i) })
            }
        }
    }
}
