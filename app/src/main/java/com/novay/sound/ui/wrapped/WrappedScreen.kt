package com.novay.sound.ui.wrapped

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.theme.BrandTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class WrappedViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val year: Int = Calendar.getInstance().get(Calendar.YEAR)
    private val since = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    }.timeInMillis
    val totalMs = library.totalListened(since).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val plays = library.totalPlays(since).stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val songs = library.topSongs(since).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val artists = library.topArtists(since).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

private val pageColors = listOf(
    listOf(Color(0xFFFFB000), Color(0xFFFF3D77)),
    listOf(Color(0xFFFF3D77), Color(0xFF8E2DE2)),
    listOf(Color(0xFF8E2DE2), Color(0xFF2D1B69)),
    listOf(Color(0xFF1A1A2E), Color(0xFFFF3D77))
)

/** Yıllık özet: hikâye tarzı 4 sayfa, her sayfa görsel olarak paylaşılabilir. */
@Composable
fun WrappedScreen(onClose: () -> Unit, vm: WrappedViewModel = hiltViewModel()) {
    val totalMs by vm.totalMs.collectAsStateWithLifecycle()
    val plays by vm.plays.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 4 }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val minutes = totalMs / 60_000

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
        ) { page ->
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(pageColors[page]))
                    .statusBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val white = Color.White
                when (page) {
                    0 -> {
                        Text("${vm.year} özetin", color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Text("%,d".format(minutes), color = white, fontSize = 72.sp, fontWeight = FontWeight.Black)
                        Text("dakika müzik dinledin", color = white, fontSize = 22.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("$plays şarkı çaldın • yaklaşık ${minutes / 60} saat", color = white.copy(alpha = 0.85f))
                    }
                    1 -> {
                        val top = artists.firstOrNull()
                        Text("Yılın sanatçın", color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Artwork(top?.thumb, Modifier.size(200.dp).clip(CircleShape), corner = 100.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(top?.artist ?: "—", color = white, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        top?.let { Text("${it.plays} kez dinledin • ${it.ms / 60_000} dk", color = white.copy(alpha = 0.85f)) }
                    }
                    2 -> {
                        Text("En çok dinlediğin şarkılar", color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        songs.take(5).forEachIndexed { i, s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = white, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
                                Artwork(s.song.thumbnailUrl, Modifier.size(56.dp), corner = 8.dp)
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(s.song.title, color = white, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s.song.artist} • ${s.plays}×", color = white.copy(alpha = 0.8f), maxLines = 1)
                                }
                            }
                        }
                        if (songs.isEmpty()) Text("Henüz yeterli dinleme yok", color = white)
                    }
                    else -> {
                        BrandTitle(fontSize = 30.sp)
                        Spacer(Modifier.height(24.dp))
                        Text("${vm.year}", color = white, fontSize = 56.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(16.dp))
                        listOf(
                            "⏱  %,d dakika".format(minutes),
                            "🎵  $plays şarkı",
                            "🎤  ${artists.firstOrNull()?.artist ?: "—"}",
                            "🔥  ${songs.firstOrNull()?.song?.title ?: "—"}"
                        ).forEach { Text(it, color = white, fontSize = 22.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                        Spacer(Modifier.height(16.dp))
                        Text("Top 5 sanatçı: " + artists.take(5).joinToString(", ") { it.artist },
                            color = white.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Üstte sayfa göstergesi + kapat
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i ->
                Box(Modifier.weight(1f).height(4.dp).padding(horizontal = 2.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.35f)))
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
        }

        FilledTonalButton(
            onClick = {
                scope.launch {
                    val bmp = layer.toImageBitmap().asAndroidBitmap()
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    val f = File(dir, "novaysound-ozet-${vm.year}.png")
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Özetini paylaş"
                    ))
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
        ) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Bu sayfayı paylaş")
        }
    }
}
