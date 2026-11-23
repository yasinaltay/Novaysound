package com.novay.sound.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import com.novay.sound.data.model.Song
import com.novay.sound.data.youtube.NewPipeDownloader
import com.novay.sound.data.youtube.YouTubeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoDeps {
    fun youtube(): YouTubeRepository
    fun okHttp(): OkHttpClient
}

/** İsteğe bağlı klip izleme: müzik duraklar, video aynı saniyeden başlar; kapatınca müzik kaldığı yerden sürer. */
@OptIn(UnstableApi::class)
@Composable
fun VideoDialog(song: Song, startMs: Long, onClose: (positionMs: Long) -> Unit) {
    val context = LocalContext.current
    val deps = remember { EntryPointAccessors.fromApplication(context.applicationContext, VideoDeps::class.java) }
    val exo = remember { ExoPlayer.Builder(context).build() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id) {
        runCatching { deps.youtube().resolveVideo(song.id) }
            .onSuccess { v ->
                val factory = DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(deps.okHttp()).setUserAgent(NewPipeDownloader.USER_AGENT)
                )
                val video = factory.createMediaSource(MediaItem.fromUri(v.videoUrl))
                val source = v.audioUrl?.let { MergingMediaSource(true, video, factory.createMediaSource(MediaItem.fromUri(it))) } ?: video
                exo.setMediaSource(source)
                exo.seekTo(startMs)
                exo.prepare()
                exo.play()
                loading = false
            }
            .onFailure { loading = false; error = "Video açılamadı" }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    val close = { onClose(exo.currentPosition.takeIf { error == null && !loading } ?: startMs) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { PlayerView(it).apply { player = exo } }, modifier = Modifier.fillMaxSize())
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center)) }
            IconButton(onClick = close, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.Close, "Kapat", tint = Color.White)
            }
        }
    }
}
