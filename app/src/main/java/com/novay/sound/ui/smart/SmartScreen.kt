package com.novay.sound.ui.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.SmartPlaylists
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.library.SongList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun SmartList.icon(): ImageVector = when (this) {
    SmartList.MONTH_TOP -> Icons.Default.EmojiEvents
    SmartList.FORGOTTEN -> Icons.Default.HistoryToggleOff
    SmartList.DISCOVER -> Icons.Default.AutoAwesome
}

private fun SmartList.colors(): List<Color> = when (this) {
    SmartList.MONTH_TOP -> listOf(Color(0xFFFF8A00), Color(0xFFE5294A))
    SmartList.FORGOTTEN -> listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    SmartList.DISCOVER -> listOf(Color(0xFFFFB000), Color(0xFFFF3D77), Color(0xFF8E2DE2))
}

/** Ana ekran ve kitaplıkta gösterilen renkli akıllı liste kartları. */
@Composable
fun SmartListCards(onOpen: (SmartList) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(SmartList.entries) { type ->
            Box(
                Modifier.width(150.dp).height(110.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(type.colors()))
                    .clickable { onOpen(type) }
                    .padding(14.dp)
            ) {
                Icon(type.icon(), null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text(
                    type.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

sealed interface SmartUi {
    data object Loading : SmartUi
    data class Ready(val songs: List<Song>) : SmartUi
}

@HiltViewModel
class SmartViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val smart: SmartPlaylists
) : ViewModel() {
    val type: SmartList = SmartList.valueOf(checkNotNull(savedState.get<String>("type")))
    private val _ui = MutableStateFlow<SmartUi>(SmartUi.Loading)
    val ui = _ui.asStateFlow()

    init { load(false) }

    fun load(refresh: Boolean) = viewModelScope.launch {
        _ui.value = SmartUi.Loading
        val songs = runCatching { if (refresh && type == SmartList.DISCOVER) smart.refreshDiscover() else smart.songs(type) }
            .getOrDefault(emptyList())
        _ui.value = SmartUi.Ready(songs)
    }
}

@Composable
fun SmartScreen(actions: SongActions, currentId: String?, onBack: () -> Unit, vm: SmartViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Column(Modifier.weight(1f)) {
                Text(vm.type.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.type.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (vm.type == SmartList.DISCOVER) IconButton(onClick = { vm.load(true) }) { Icon(Icons.Default.Refresh, "Yenile") }
        }
        when (val s = ui) {
            SmartUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is SmartUi.Ready -> SongList(s.songs, currentId, actions) {
                EmptyState(vm.type.icon(), when (vm.type) {
                    SmartList.MONTH_TOP -> "Bu ay henüz yeterince dinleme yok."
                    SmartList.FORGOTTEN -> "Unuttuğun favori yok, hepsini yakın zamanda dinlemişsin."
                    SmartList.DISCOVER -> "Keşif için biraz daha şarkı dinlemelisin."
                })
            }
        }
    }
}
