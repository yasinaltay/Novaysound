package com.novay.sound.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.novay.sound.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Oynatma servisinden çağrılır; ekranda widget yoksa hiçbir şey yapmaz. */
@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastArtUrl: String? = null

    fun update(song: Song?, playing: Boolean) {
        job?.cancel()
        job = scope.launch {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(NovayWidget::class.java)
                if (ids.isEmpty()) return@launch
                val artFile = File(context.filesDir, "widget_art.png")
                val url = song?.thumbnailUrl
                if (url != null && url != lastArtUrl) {
                    val req = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
                    (context.imageLoader.execute(req) as? SuccessResult)?.image?.toBitmap()?.let { bmp ->
                        artFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        lastArtUrl = url
                    }
                }
                ids.forEach { id ->
                    updateAppWidgetState(context, id) { p ->
                        p[WidgetKeys.TITLE] = song?.title ?: "Novay Sound"
                        p[WidgetKeys.ARTIST] = song?.artist ?: "Çalmak için dokun"
                        p[WidgetKeys.PLAYING] = playing
                        if (song != null && artFile.exists()) p[WidgetKeys.ART] = artFile.absolutePath else p.remove(WidgetKeys.ART)
                    }
                    NovayWidget().update(context, id)
                }
            }
        }
    }
}
