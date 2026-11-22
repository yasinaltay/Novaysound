package com.novay.sound.ui.car

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novay.sound.data.smart.SmartList
import com.novay.sound.ui.MainViewModel
import com.novay.sound.ui.components.Artwork

/** Android Auto olmayan araçlar için: dev butonlar, sesli arama, ekran açık kalır. */
@Composable
fun CarModeScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val song = state.song
    val liked by remember(song?.id) { vm.isLiked(song?.id.orEmpty()) }.collectAsStateWithLifecycle(false)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::playFromSearch)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Araç modu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Close, "Kapat", Modifier.size(32.dp)) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song?.thumbnailUrl, Modifier.size(110.dp), corner = 16.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(song?.title ?: "Bir şey çalmıyor", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song?.artist.orEmpty(), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (song != null) IconButton(onClick = { vm.actions.toggleLike(song) }, modifier = Modifier.size(64.dp)) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favori", Modifier.size(40.dp),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = vm.player::previous, modifier = Modifier.size(96.dp)) {
                Icon(Icons.Default.SkipPrevious, "Önceki", Modifier.size(56.dp))
            }
            FilledIconButton(onClick = vm.player::togglePlay, modifier = Modifier.size(132.dp)) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Oynat", Modifier.size(80.dp))
            }
            FilledTonalIconButton(onClick = vm.player::next, modifier = Modifier.size(96.dp)) {
                Icon(Icons.Default.SkipNext, "Sonraki", Modifier.size(56.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        FilledTonalButton(
            onClick = {
                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ne çalalım?"))
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(76.dp)
        ) {
            Icon(Icons.Default.Mic, null, Modifier.size(32.dp))
            Text("  Sesle ara", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { vm.playFromSearch("") }, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).height(72.dp)) {
                Icon(Icons.Default.Shuffle, null)
                Text(" Favoriler", maxLines = 1, textAlign = TextAlign.Center)
            }
            FilledTonalButton(onClick = { vm.playSmart(SmartList.DISCOVER) }, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).height(72.dp)) {
                Icon(Icons.Default.AutoAwesome, null)
                Text(" Keşif", maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}
