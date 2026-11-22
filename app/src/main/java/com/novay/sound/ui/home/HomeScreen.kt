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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.novay.sound.R
import com.novay.sound.ui.theme.BrandTitleStyle
import com.novay.sound.ui.components.CastButton
import com.novay.sound.ui.smart.SmartListCards
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.Mood
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.Toggle
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
    private val youtube: YouTubeRepository,
    settings: SettingsRepository
) : ViewModel() {
    val settings = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.state() =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recent = library.recent.state()
    val favorites = library.favorites.state()
    val mostPlayed = library.mostPlayed.state()

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks = _quickPicks.asStateFlow()

    private val _trending = MutableStateFlow<List<Song>>(emptyList())
    val trending = _trending.asStateFlow()

    init {
        refreshQuickPicks()
        viewModelScope.launch { _trending.value = runCatching { youtube.trending() }.getOrDefault(emptyList()).take(20) }
    }

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
    onOpenSettings: () -> Unit,
    onOpenSmart: (SmartList) -> Unit,
    onOpenCar: () -> Unit,
    onMood: (Mood) -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val quick by vm.quickPicks.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.logo_mark), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text("Novay Sound", style = BrandTitleStyle, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenCar) { Icon(Icons.Default.DirectionsCar, "Araç modu") }
                CastButton()
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Ayarlar") }
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

        if (s.on(Toggle.SHOW_SMART) && (recent.isNotEmpty() || favorites.isNotEmpty())) {
            item { SectionTitle("Senin için") }
            item { SmartListCards(onOpenSmart) }
        }

        if (s.on(Toggle.SHOW_MOODS)) {
            item { SectionTitle("Ruh haline göre") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Mood.entries.toList()) { m ->
                        AssistChip(onClick = { onMood(m) }, label = { Text("${m.emoji}  ${m.label}") })
                    }
                }
            }
        }

        if (s.on(Toggle.SHOW_QUICK) && quick.isNotEmpty()) {
            item { SectionTitle("Hızlı seçimler") }
            itemsIndexed(quick, key = { _, s -> "q" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, actions, onClick = { actions.play(quick, i) })
            }
        }

        if (s.on(Toggle.SHOW_TRENDING)) horizontalSection("Türkiye'de trend", trending, actions)
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
