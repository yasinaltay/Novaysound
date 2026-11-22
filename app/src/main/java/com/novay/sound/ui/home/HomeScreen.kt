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
import com.novay.sound.ui.theme.BrandTitle
import com.novay.sound.ui.components.CastButton
import com.novay.sound.ui.smart.SmartListCards
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.Mood
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.Toggle
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
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

/** Ana ekrandaki "Araçlar" bölümünün eylemleri. */
data class HomeTools(
    val onLyricsSearch: () -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onMultiArtist: (List<String>) -> Unit = {},
    val loadArtists: suspend () -> List<String> = { emptyList() }
)

@Composable
fun HomeScreen(
    actions: SongActions,
    currentId: String?,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSmart: (SmartList) -> Unit,
    onOpenCar: () -> Unit,
    onMood: (Mood) -> Unit,
    tools: HomeTools = HomeTools(),
    vm: HomeViewModel = hiltViewModel()
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val quick by vm.quickPicks.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var durationDialog by rememberSaveable { mutableStateOf(false) }
    var artistDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.logo_mark), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                BrandTitle(Modifier.weight(1f))
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

        item { SectionTitle("Araçlar") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = tools.onLyricsSearch, label = { Text("🎤  Sözden şarkı bul") }) }
                item { AssistChip(onClick = { durationDialog = true }, label = { Text("⏱  Süreye göre liste") }) }
                item { AssistChip(onClick = { artistDialog = true }, label = { Text("📻  Çoklu sanatçı radyosu") }) }
            }
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

    if (durationDialog) DurationDialog(onDismiss = { durationDialog = false }, onCreate = { durationDialog = false; tools.onDuration(it) })
    if (artistDialog) MultiArtistDialog(tools.loadArtists, onDismiss = { artistDialog = false },
        onStart = { artistDialog = false; tools.onMultiArtist(it) })
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

@Composable
private fun DurationDialog(onDismiss: () -> Unit, onCreate: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf(45f) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Süreye göre liste") },
        text = {
            Column {
                Text("${minutes.toInt()} dakika", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 15f..180f, steps = 10)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90).forEach { m ->
                        androidx.compose.material3.FilterChip(selected = minutes.toInt() == m, onClick = { minutes = m.toFloat() },
                            label = { Text("$m") })
                    }
                }
                Text("Favorilerin, son dinlediklerin ve benzerlerinden hazırlanır, kitaplığa kaydedilir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(minutes.toInt()) }) { Text("Hazırla ve çal") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
private fun MultiArtistDialog(load: suspend () -> List<String>, onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    val candidates by androidx.compose.runtime.produceState(emptyList<String>()) { value = load() }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var custom by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çoklu sanatçı radyosu") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("2-5 sanatçı seç (${selected.size} seçili)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (selected.filter { it !in candidates } + candidates).forEach { a ->
                        androidx.compose.material3.FilterChip(
                            selected = a in selected,
                            onClick = { if (a in selected) selected.remove(a) else if (selected.size < 5) selected.add(a) },
                            label = { Text(a, maxLines = 1) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(custom, { custom = it }, singleLine = true,
                        label = { Text("Başka sanatçı ekle") }, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(enabled = custom.isNotBlank() && selected.size < 5, onClick = {
                        if (custom.trim() !in selected) selected.add(custom.trim()); custom = ""
                    }) { Text("Ekle") }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = selected.size >= 2, onClick = { onStart(selected.toList()) }) { Text("Radyoyu başlat") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
