package com.novay.sound.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.novay.sound.download.DlState
import com.novay.sound.playback.PlayerState
import com.novay.sound.ui.MainViewModel
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.CastButton
import com.novay.sound.ui.components.shareSong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import com.novay.sound.ui.components.LocalDownloads
import com.novay.sound.ui.components.formatDuration
import kotlinx.coroutines.delay

@Composable
fun FullPlayer(
    state: PlayerState,
    vm: MainViewModel,
    accent: Color,
    isLiked: Boolean,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val song = state.song ?: return
    val player = vm.player
    val actions = vm.actions
    val sleep by vm.sleepTimer.state.collectAsStateWithLifecycle()
    val download = LocalDownloads.current[song.id]
    val radioFavs by vm.radioFavorites.collectAsStateWithLifecycle()

    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val bg = MaterialTheme.colorScheme.background
    val context = androidx.compose.ui.platform.LocalContext.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val blur = settings.on(com.novay.sound.data.settings.Toggle.BLUR_BG) &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && song.thumbnailUrl != null

    // Bulanık kapak (Android 12+): en altta, üstüne okunabilirlik için temaya göre perde
    if (blur) Box(Modifier.fillMaxSize().background(bg)) {
        coil3.compose.AsyncImage(
            model = song.thumbnailUrl, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(70.dp).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
        )
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(bg.copy(alpha = 0.35f), bg.copy(alpha = 0.6f), bg.copy(alpha = 0.92f)))
        ))
    }

    Box(
        Modifier.fillMaxSize()
            .then(if (blur) Modifier else Modifier.background(bg)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.40f), bg, bg))))
            .pointerInput(showLyrics) {   // aşağı kaydırınca kapat (sözler açıkken kapalı)
                if (showLyrics) return@pointerInput
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
            // ---- Üst çubuk ----
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Kapat", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Şimdi çalıyor", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sleep.active) SleepCountdown(sleep.endAt, accent)
                }
                CastButton()
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menü") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!song.isRadio) DropdownMenuItem(
                            text = { Text("Hız ve ton (%.2gx)".format(state.speed)) },
                            leadingIcon = { Icon(Icons.Default.Speed, null) },
                            onClick = { menu = false; showSpeed = true })
                        DropdownMenuItem(text = { Text("Ekolayzer") }, leadingIcon = { Icon(Icons.Default.GraphicEq, null) },
                            onClick = { menu = false; onOpenEqualizer() })
                        if (!song.isLocal && !song.isRadio) DropdownMenuItem(text = { Text("Paylaş") }, leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menu = false; shareSong(context, song) })
                        if (!song.isRadio) DropdownMenuItem(text = { Text("Sanatçıya git") }, leadingIcon = { Icon(Icons.Default.Person, null) },
                            onClick = { menu = false; actions.openArtist(song) })
                        if (!song.isRadio) DropdownMenuItem(text = { Text("Çalma listesine ekle") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { menu = false; actions.addToPlaylist(song) })
                        if (song.isLocal || song.isRadio) Unit
                        else if (download == null || download.state == DlState.FAILED) {
                            DropdownMenuItem(text = { Text("İndir") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menu = false; actions.download(song) })
                        } else {
                            DropdownMenuItem(text = { Text("İndirmeyi sil") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = { menu = false; actions.removeDownload(song.id) })
                        }
                    }
                }
            }

            // ---- Kapak veya sözler ----
            AnimatedContent(showLyrics && !song.isRadio, label = "lyrics", modifier = Modifier.weight(1f).fillMaxWidth()) { lyricsMode ->
                if (lyricsMode) {
                    LyricsPane(song, state.positionMs, vm::lyrics, onSeek = player::seekTo, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Artwork(
                            song.thumbnailUrl,
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = accent, spotColor = accent),
                            corner = 20.dp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Başlık + favori ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(state.liveTitle?.let { "♪ $it" } ?: song.artist, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        modifier = Modifier.basicMarquee().clickable(enabled = !song.isRadio) { actions.openArtist(song) })
                }
                val liked = if (song.isRadio) radioFavs.any { it.id == song.id } else isLiked
                IconButton(onClick = { if (song.isRadio) vm.toggleRadioFavorite(song) else actions.toggleLike(song) }) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favori", tint = if (liked) accent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- İlerleme (radyoda canlı göstergesi) ----
            if (song.isRadio) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(0xFFE53935), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text("CANLI YAYIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Column {
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
            }

            Spacer(Modifier.height(8.dp))

            // ---- Kontroller ----
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

            // ---- Alt eylemler: sözler, uyku, sıra ----
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!song.isRadio) IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(Icons.Default.Lyrics, "Sözler", tint = if (showLyrics) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Default.Bedtime, "Uyku zamanlayıcısı",
                        tint = if (sleep.active) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Sıra", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showQueue) QueueSheet(state, player, onDismiss = { showQueue = false })
    if (showSpeed) SpeedSheet(state.speed, state.pitch, onChange = player::setSpeed, onDismiss = { showSpeed = false })
    if (showSleep) SleepTimerSheet(
        state = sleep,
        onStart = vm.sleepTimer::start,
        onEndOfSong = vm.sleepTimer::endOfSong,
        onCancel = vm.sleepTimer::cancel,
        onDismiss = { showSleep = false }
    )
}

@Composable
private fun SleepCountdown(endAt: Long?, accent: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val text = if (endAt == null) "Şarkı bitince duracak" else {
        val left = ((endAt - now) / 1000).coerceAtLeast(0)
        "Uyku: %d:%02d".format(left / 60, left % 60)
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = accent)
}
