package com.novay.sound.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.playback.PlayerState
import com.novay.sound.ui.components.Artwork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlayerState, player: PlayerConnection, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.currentIndex > 0) listState.scrollToItem(state.currentIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text("Sıradakiler (${state.queue.size})", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(state = listState) {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
                val current = i == state.currentIndex
                Row(
                    Modifier.fillMaxWidth().clickable { player.skipTo(i) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(song.thumbnailUrl, Modifier.size(44.dp), corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i > 0) IconButton(onClick = { player.move(i, i - 1) }) { Icon(Icons.Default.KeyboardArrowUp, "Yukarı") }
                    if (i < state.queue.lastIndex) IconButton(onClick = { player.move(i, i + 1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, "Aşağı")
                    }
                    if (!current) IconButton(onClick = { player.removeAt(i) }) { Icon(Icons.Default.Close, "Kaldır") }
                }
            }
        }
    }
}
