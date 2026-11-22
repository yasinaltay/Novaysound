package com.novay.sound.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.toSong
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SectionTitle
import com.novay.sound.ui.components.SongActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class StatsPeriod(val label: String) { WEEK("Bu hafta"), MONTH("Bu ay"), YEAR("Bu yıl"), ALL("Tüm zamanlar") }

private fun StatsPeriod.since(): Long {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    return when (this) {
        StatsPeriod.WEEK -> c.apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.timeInMillis
        StatsPeriod.MONTH -> c.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        StatsPeriod.YEAR -> c.apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        StatsPeriod.ALL -> 0L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val period = MutableStateFlow(StatsPeriod.MONTH)
    private fun <T> flow(init: T, f: (Long) -> kotlinx.coroutines.flow.Flow<T>) =
        period.flatMapLatest { f(it.since()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    val totalMs = flow(0L) { library.totalListened(it) }
    val plays = flow(0) { library.totalPlays(it) }
    val topSongs = flow(emptyList()) { library.topSongs(it) }
    val topArtists = flow(emptyList()) { library.topArtists(it) }
}

@Composable
fun StatsScreen(actions: SongActions, onBack: () -> Unit, onOpenArtist: (String) -> Unit, onOpenWrapped: () -> Unit = {},
                vm: StatsViewModel = hiltViewModel()) {
    val period by vm.period.collectAsStateWithLifecycle()
    val totalMs by vm.totalMs.collectAsStateWithLifecycle()
    val plays by vm.plays.collectAsStateWithLifecycle()
    val songs by vm.topSongs.collectAsStateWithLifecycle()
    val artists by vm.topArtists.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text("İstatistikler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.FilledTonalButton(onClick = onOpenWrapped, modifier = Modifier.padding(end = 12.dp)) {
                    Text("✨ Yıllık özet")
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsPeriod.entries) { p ->
                    FilterChip(selected = p == period, onClick = { vm.period.value = p }, label = { Text(p.label) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Dinleme süresi", formatListen(totalMs), Modifier.weight(1f))
                StatCard("Çalınan şarkı", plays.toString(), Modifier.weight(1f))
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState(Icons.Default.Insights, "Bu dönemde henüz kayıt yok.\nŞarkıları 30 saniyeden fazla dinledikçe burada görünür.") }
            return@LazyColumn
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle("En çok dinlenen sanatçılar") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists) { a ->
                        Column(
                            Modifier.width(96.dp).clickable(enabled = a.artistUrl != null) { a.artistUrl?.let(onOpenArtist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Artwork(a.thumb, Modifier.size(88.dp).clip(CircleShape), corner = 44.dp)
                            Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp))
                            Text("${a.plays} kez", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { SectionTitle("En çok dinlenen şarkılar") }
        itemsIndexed(songs, key = { _, s -> s.song.id }) { i, stat ->
            val list = songs.map { it.song.toSong() }
            Row(
                Modifier.fillMaxWidth().clickable { actions.play(list, i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                Artwork(stat.song.thumbnailUrl, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stat.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stat.song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${stat.plays}×", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun formatListen(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} sa ${min % 60} dk" else "$min dk"
}
