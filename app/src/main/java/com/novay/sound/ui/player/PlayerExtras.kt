package com.novay.sound.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novay.sound.data.lyrics.Lyrics
import com.novay.sound.data.model.Song
import com.novay.sound.playback.SleepState
import com.novay.sound.ui.components.EmptyState

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object NotFound : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

/** Senkron sözler: çalan satır vurgulanır ve ortada tutulur, satıra dokununca o ana gider. */
@Composable
fun LyricsPane(
    song: Song,
    positionMs: Long,
    load: suspend (Song) -> Lyrics?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by produceState<LyricsUi>(LyricsUi.Loading, song.id) {
        value = runCatching { load(song) }.getOrNull()?.let { LyricsUi.Ready(it) } ?: LyricsUi.NotFound
    }
    Box(modifier) {
        when (val s = ui) {
            LyricsUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LyricsUi.NotFound -> EmptyState(Icons.Default.MusicOff, "Bu şarkının sözleri bulunamadı", Modifier.align(Alignment.Center))
            is LyricsUi.Ready -> if (s.lyrics.isSynced) SyncedLyrics(s.lyrics, positionMs, onSeek)
            else Text(
                s.lyrics.plain.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                lineHeight = 30.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val lines = lyrics.synced
    val current by remember(lines, positionMs) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= positionMs + 300 }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }

    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 120.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                fontSize = if (active) 26.sp else 22.sp,
                lineHeight = 32.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = if (i < current) 0.35f else 0.5f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepState,
    onStart: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text("Uyku zamanlayıcısı", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        listOf(5, 15, 30, 45, 60, 90).forEach { m ->
            ListItem(
                headlineContent = { Text("$m dakika") },
                leadingContent = { Icon(Icons.Default.Timer, null) },
                colors = colors,
                modifier = Modifier.clickable { onStart(m); onDismiss() }
            )
        }
        ListItem(
            headlineContent = { Text("Şarkı bitince") },
            leadingContent = { Icon(Icons.Default.Bedtime, null) },
            colors = colors,
            modifier = Modifier.clickable { onEndOfSong(); onDismiss() }
        )
        if (state.active) ListItem(
            headlineContent = { Text("Zamanlayıcıyı kapat", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Default.TimerOff, null, tint = MaterialTheme.colorScheme.error) },
            colors = colors,
            modifier = Modifier.clickable { onCancel(); onDismiss() }
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}
