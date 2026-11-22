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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * Sözler + (açıksa) satır altında Türkçe çeviri. Öğrenme modunda kelimeye dokununca anlamı çıkar,
 * kelime defterine kaydedilebilir. Senkron sözlerde çalan satır vurgulanır ve ortada tutulur.
 */
@Composable
fun LyricsPane(
    song: Song,
    positionMs: Long,
    vm: com.novay.sound.ui.MainViewModel,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val translateOn = settings.on(com.novay.sound.data.settings.Toggle.LYRICS_TRANSLATE)
    val learnOn = settings.on(com.novay.sound.data.settings.Toggle.LEARN_MODE)
    val downloading by vm.translationDownloading.collectAsStateWithLifecycle()

    val ui by produceState<LyricsUi>(LyricsUi.Loading, song.id) {
        value = runCatching { vm.lyrics(song) }.getOrNull()?.let { LyricsUi.Ready(it) } ?: LyricsUi.NotFound
    }
    val lyrics = (ui as? LyricsUi.Ready)?.lyrics
    val lines = remember(lyrics) {
        lyrics?.let { l -> if (l.isSynced) l.synced.map { it.text.ifBlank { "♪" } } else l.plain.orEmpty().lines() }.orEmpty()
    }
    // Çeviri (öğrenme modu da dili bilmek için çeviriye ihtiyaç duyar)
    val translation by produceState<com.novay.sound.data.learn.LyricsTranslation?>(null, song.id, lines, translateOn || learnOn) {
        value = null
        if (lines.isNotEmpty() && (translateOn || learnOn)) value = runCatching { vm.translateLyrics(song, lines) }.getOrNull()
    }
    var wordDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // kelime, satır

    Column(modifier) {
        if (lyrics != null) Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.FilterChip(selected = translateOn,
                onClick = { vm.setToggle(com.novay.sound.data.settings.Toggle.LYRICS_TRANSLATE, !translateOn) },
                label = { Text("🌐 Çeviri", maxLines = 1) })
            androidx.compose.material3.FilterChip(selected = learnOn,
                onClick = { vm.setToggle(com.novay.sound.data.settings.Toggle.LEARN_MODE, !learnOn) },
                label = { Text("📖 Öğren", maxLines = 1) })
            Text(
                when {
                    downloading -> "Çeviri modeli indiriliyor (~30 MB)…"
                    translation != null -> "${translation!!.languageName} → Türkçe"
                    translateOn || learnOn -> "Türkçe ya da çevrilemiyor"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = ui) {
                LyricsUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LyricsUi.NotFound -> EmptyState(Icons.Default.MusicOff, "Bu şarkının sözleri bulunamadı", Modifier.align(Alignment.Center))
                is LyricsUi.Ready -> {
                    val synced = s.lyrics.isSynced
                    val current = if (synced) s.lyrics.synced.indexOfLast { it.timeMs <= positionMs + 300 }.coerceAtLeast(0) else -1
                    val listState = rememberLazyListState()
                    if (synced) LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = if (synced) 120.dp else 16.dp),
                        modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(lines) { i, line ->
                            val active = synced && i == current
                            val alpha = when { !synced || active -> 1f; i < current -> 0.35f; else -> 0.5f }
                            val tr = translation?.lines?.getOrNull(i)?.takeIf { translateOn && it.isNotBlank() && it != line }
                            Column(
                                Modifier.fillMaxWidth()
                                    .then(if (synced && !learnOn) Modifier.clickable { onSeek(s.lyrics.synced[i].timeMs) } else Modifier)
                                    .padding(vertical = 8.dp)
                            ) {
                                val style = androidx.compose.ui.text.TextStyle(
                                    fontSize = if (!synced) 18.sp else if (active) 26.sp else 22.sp,
                                    lineHeight = if (synced) 32.sp else 26.sp,
                                    fontWeight = if (active) FontWeight.ExtraBold else if (synced) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                )
                                if (learnOn && translation != null && line != "♪") {
                                    // Her kelime dokunulabilir
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        line.split(' ').filter { it.isNotBlank() }.forEach { w ->
                                            Text(w, style = style.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                                                modifier = Modifier.clickable {
                                                    val clean = w.trim { !it.isLetterOrDigit() }
                                                    if (clean.isNotEmpty()) wordDialog = clean to line
                                                })
                                        }
                                    }
                                } else Text(line, style = style)
                                if (tr != null) Text(
                                    tr,
                                    fontSize = if (synced) 16.sp else 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (active || !synced) 0.95f else alpha * 0.9f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    wordDialog?.let { (word, line) ->
        val lang = translation?.sourceLang ?: return@let
        WordDialog(word, line, lang, song, vm, onDismiss = { wordDialog = null })
    }
}

@Composable
private fun WordDialog(word: String, line: String, lang: String, song: Song, vm: com.novay.sound.ui.MainViewModel, onDismiss: () -> Unit) {
    val meaning by produceState<String?>(null, word) { value = vm.translateWord(word, lang) ?: "Çevrilemedi" }
    val saved = remember(word) { vm.isWordSaved(word, lang) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (meaning == null) CircularProgressIndicator(Modifier.size(24.dp))
                else Text(meaning!!, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("\u201C$line\u201D", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = meaning != null && !saved, onClick = {
                vm.saveWord(com.novay.sound.data.learn.SavedWord(word, meaning.orEmpty(), lang, "${song.title} – ${song.artist}", line))
                onDismiss()
            }) { Text(if (saved) "Defterde ✓" else "Defterime ekle") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(speed: Float, pitch: Float, onChange: (Float, Float) -> Unit, onDismiss: () -> Unit) {
    var sp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(speed) }
    var pt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(pitch) }
    var linked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(speed == pitch && speed != 1f) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Hız ve ton", style = MaterialTheme.typography.titleLarge)
            Text("Hız: %.2fx".format(sp), modifier = Modifier.padding(top = 16.dp))
            androidx.compose.material3.Slider(value = sp, valueRange = 0.5f..2f, onValueChange = {
                sp = it; if (linked) pt = it
            }, onValueChangeFinished = { onChange(sp, pt) })
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                    androidx.compose.material3.FilterChip(selected = sp == v, onClick = {
                        sp = v; if (linked) pt = v; onChange(sp, pt)
                    }, label = { Text("${v}x") })
                }
            }
            Text("Ton: %.2f".format(pt), modifier = Modifier.padding(top = 16.dp))
            androidx.compose.material3.Slider(value = pt, valueRange = 0.5f..2f, enabled = !linked,
                onValueChange = { pt = it }, onValueChangeFinished = { onChange(sp, pt) })
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tonu hızla birlikte değiştir (plak efekti)", modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = linked, onCheckedChange = { linked = it; if (it) { pt = sp; onChange(sp, pt) } })
            }
            androidx.compose.material3.TextButton(onClick = { sp = 1f; pt = 1f; linked = false; onChange(1f, 1f) }) { Text("Sıfırla") }
        }
    }
}
