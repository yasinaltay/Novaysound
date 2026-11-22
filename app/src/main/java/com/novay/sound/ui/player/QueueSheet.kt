package com.novay.sound.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novay.sound.data.model.Song
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.playback.PlayerState
import com.novay.sound.ui.components.Artwork
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class QueueEntry(val uid: Int, val song: Song)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlayerState, player: PlayerConnection, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.currentIndex > 0) listState.scrollToItem(state.currentIndex) }

    // Aynı şarkı sırada iki kez olabilir: her satıra o anki sırasından benzersiz kimlik
    var entries by remember(state.queue) { mutableStateOf(state.queue.mapIndexed { i, s -> QueueEntry(i, s) }) }
    val currentUid = state.currentIndex
    var dragFrom by remember { mutableIntStateOf(-1) }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = entries.indexOfFirst { it.uid == from.key }
        val t = entries.indexOfFirst { it.uid == to.key }
        if (f >= 0 && t >= 0) entries = entries.toMutableList().apply { add(t, removeAt(f)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text("Sıradakiler (${entries.size})", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(state = listState) {
            items(entries, key = { it.uid }) { entry ->
                ReorderableItem(reorder, key = entry.uid) { dragging ->
                    val current = entry.uid == currentUid
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .clickable { player.skipTo(entries.indexOf(entry)) }
                            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(entry.song.thumbnailUrl, Modifier.size(44.dp), corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(entry.song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!current) IconButton(onClick = { player.removeAt(entries.indexOf(entry)) }) {
                            Icon(Icons.Default.Close, "Kaldır")
                        }
                        Icon(
                            Icons.Default.DragHandle, "Sürükle", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp).draggableHandle(
                                onDragStarted = { dragFrom = entry.uid },
                                onDragStopped = {
                                    val to = entries.indexOf(entry)
                                    if (dragFrom >= 0 && to >= 0 && dragFrom != to) player.move(dragFrom, to)
                                    dragFrom = -1
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}
