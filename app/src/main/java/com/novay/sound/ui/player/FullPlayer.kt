package com.novay.sound.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.novay.sound.data.model.Song
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.playback.PlayerState
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.formatDuration

@Composable
fun FullPlayer(
    state: PlayerState,
    player: PlayerConnection,
    accent: Color,
    isLiked: Boolean,
    onLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onClose: () -> Unit
) {
    val song = state.song ?: return
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Color.Black, Color.Black)))
            .pointerInput(Unit) {   // aşağı kaydırınca kapat
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal > 180f) onClose() },
                    onVerticalDrag = { _, amount -> dragTotal += amount }
                )
            }
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Kapat", Modifier.size(32.dp)) }
                Text("Şimdi çalıyor", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Sıra") }
            }

            Spacer(Modifier.weight(0.6f))
            Artwork(song.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp)
            Spacer(Modifier.weight(0.6f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(song.artist, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        modifier = Modifier.basicMarquee())
                }
                IconButton(onClick = { onAddToPlaylist(song) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle")
                }
                IconButton(onClick = { onLike(song) }) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favori", tint = if (isLiked) accent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val duration = state.durationMs.coerceAtLeast(1L)
            val position = if (dragging) dragValue else state.positionMs.toFloat() / duration
            Slider(
                value = position.coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { player.seekTo((dragValue * duration).toLong()); dragging = false },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((position * duration).toLong()).ifEmpty { "0:00" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, "Karıştır",
                        tint = if (state.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = player::previous, Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Önceki", Modifier.size(40.dp))
                }
                FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(76.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (state.isPlaying) "Duraklat" else "Oynat", Modifier.size(44.dp))
                    }
                }
                IconButton(onClick = player::next, Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Sonraki", Modifier.size(40.dp))
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Tekrar",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.weight(0.5f))
        }
    }

    if (showQueue) QueueSheet(state, player, onDismiss = { showQueue = false })
}
