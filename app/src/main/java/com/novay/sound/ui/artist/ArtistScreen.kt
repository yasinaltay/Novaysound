package com.novay.sound.ui.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.youtube.Artist
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SectionTitle
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongCard
import com.novay.sound.ui.components.SongRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArtistUi {
    data object Loading : ArtistUi
    data class Ready(val artist: Artist) : ArtistUi
    data class Error(val message: String) : ArtistUi
}

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val youtube: YouTubeRepository
) : ViewModel() {
    private val url: String = checkNotNull(savedState.get<String>("url"))
    private val _ui = MutableStateFlow<ArtistUi>(ArtistUi.Loading)
    val ui = _ui.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _ui.value = ArtistUi.Loading
        _ui.value = runCatching { ArtistUi.Ready(youtube.artist(url)) }
            .getOrElse { ArtistUi.Error(it.message ?: "Sanatçı yüklenemedi") }
    }
}

@Composable
fun ArtistScreen(
    actions: SongActions,
    currentId: String?,
    onBack: () -> Unit,
    followed: Set<String> = emptySet(),
    onToggleFollow: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenAlbum: (String) -> Unit = {},
    vm: ArtistViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val s = ui) {
            ArtistUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is ArtistUi.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Default.ErrorOutline, s.message)
                Button(onClick = { vm.load() }) { Text("Tekrar dene") }
            }
            is ArtistUi.Ready -> ArtistContent(
                s.artist, actions, currentId,
                following = s.artist.url in followed,
                onFollow = { onToggleFollow(s.artist.name, s.artist.url, s.artist.avatar) },
                onOpenAlbum = onOpenAlbum
            )
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
        }
    }
}

@Composable
private fun ArtistContent(
    artist: Artist, actions: SongActions, currentId: String?,
    following: Boolean, onFollow: () -> Unit, onOpenAlbum: (String) -> Unit
) {
    val all = (artist.popular + artist.latest).distinctBy { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
                Artwork(artist.banner ?: artist.avatar, Modifier.fillMaxSize(), corner = 0.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
                    )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(artist.avatar, Modifier.size(72.dp).clip(CircleShape), corner = 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(artist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        if (artist.subscribers > 0) Text(
                            "${formatCount(artist.subscribers)} abone",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (following) FilledTonalButton(onClick = onFollow, modifier = Modifier.padding(top = 6.dp)) {
                            Text("Takip ediliyor ✓")
                        } else androidx.compose.material3.OutlinedButton(onClick = onFollow, modifier = Modifier.padding(top = 6.dp)) {
                            Text("Takip et")
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { actions.play(all, 0) }, enabled = all.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Çal", maxLines = 1)
                }
                FilledTonalIconButton(
                    onClick = { actions.play(all.shuffled(), 0) }, enabled = all.isNotEmpty(),
                    modifier = Modifier.size(52.dp)
                ) { Icon(Icons.Default.Shuffle, "Karıştır") }
                FilledTonalIconButton(
                    onClick = { all.firstOrNull()?.let(actions.radio) }, enabled = all.isNotEmpty(),
                    modifier = Modifier.size(52.dp)
                ) { Icon(Icons.Default.Radio, "Sanatçı radyosu") }
            }
        }
        if (artist.popular.isNotEmpty()) {
            item { SectionTitle("Popüler") }
            itemsIndexed(artist.popular, key = { _, s -> "p" + s.id }) { i, song ->
                SongRow(song, song.id == currentId, actions, onClick = { actions.play(artist.popular, i) })
            }
        }
        if (artist.albums.isNotEmpty()) {
            item { SectionTitle("Albümler") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(artist.albums, key = { _, a -> a.url }) { _, a ->
                        Column(Modifier.width(140.dp).clickable { onOpenAlbum(a.url) }) {
                            Artwork(a.cover, Modifier.size(140.dp), corner = 10.dp)
                            Text(a.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        if (artist.latest.isNotEmpty()) {
            item { SectionTitle("Son yüklenenler") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(artist.latest, key = { _, s -> s.id }) { i, song ->
                        SongCard(song, onClick = { actions.play(artist.latest, i) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1f Mn".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0f B".format(n / 1_000.0)
    else -> n.toString()
}
