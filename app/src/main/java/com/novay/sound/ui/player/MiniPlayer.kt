package com.novay.sound.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novay.sound.playback.PlayerState
import com.novay.sound.ui.components.Artwork

/** Alt menünün üstünde yüzen, kapak rengiyle hafif renklenen oynatıcı kartı. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {}
) {
    val song = state.song ?: return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val scheme = MaterialTheme.colorScheme
    // Sola kaydır: sonraki, sağa kaydır: önceki. Kart parmağı izler, bırakınca yerine yaylanır.
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    val swipe = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val x = offset.value
                when {
                    x < -threshold -> onNext()
                    x > threshold -> onPrevious()
                }
                scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f)) }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f) } },
            onHorizontalDrag = { change, amount ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + amount * 0.8f) }
            }
        )
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = scheme.primary.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainerHigh),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (kotlin.math.abs(offset.value) / (threshold * 3)).coerceIn(0f, 0.5f)
            }
            .then(swipe)
    ) {
        Column {
            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnailUrl, Modifier.size(44.dp), corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.liveTitle ?: song.artist, style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Duraklat" else "Oynat", Modifier.size(30.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Sonraki") }
            }
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    trackColor = scheme.onSurface.copy(alpha = 0.12f),
                    drawStopIndicator = {}
                )
            }
        }
    }
}
