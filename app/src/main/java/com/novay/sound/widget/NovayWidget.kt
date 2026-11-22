package com.novay.sound.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.novay.sound.R
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val ARTIST = stringPreferencesKey("artist")
    val ART = stringPreferencesKey("art_path")
    val PLAYING = booleanPreferencesKey("playing")
    val QUEUE = stringPreferencesKey("queue")   // "index\tbaşlık\tsanatçı" satırları
}

val IndexKey = ActionParameters.Key<Int>("index")

enum class WidgetStyle { COMPACT, LARGE, CLEAR }

/** Küçük widget (koyu kart). */
class NovayWidget : BaseNovayWidget(WidgetStyle.COMPACT)
/** Büyük widget: çalan şarkı + sıradakiler + favoriler. */
class NovayWidgetLarge : BaseNovayWidget(WidgetStyle.LARGE)
/** Şeffaf widget: duvar kâğıdıyla bütünleşir. */
class NovayWidgetClear : BaseNovayWidget(WidgetStyle.CLEAR)

open class BaseNovayWidget(private val style: WidgetStyle) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { if (style == WidgetStyle.LARGE) LargeContent() else Content() }
    }

    @Composable
    private fun Content() {
        val prefs = currentState<Preferences>()
        val title = prefs[WidgetKeys.TITLE] ?: "Novay Sound"
        val artist = prefs[WidgetKeys.ARTIST] ?: "Çalmak için dokun"
        val playing = prefs[WidgetKeys.PLAYING] ?: false
        val art = prefs[WidgetKeys.ART]?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        val white = ColorProvider(Color.White)
        val grey = ColorProvider(Color(0xFFB4B6BE))

        val bg = if (style == WidgetStyle.CLEAR) GlanceModifier else GlanceModifier.background(Color(0xFF141418))
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .then(bg)
                .cornerRadius(24.dp)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.logo_mark),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp)
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(title, maxLines = 1, style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Text(artist, maxLines = 1, style = TextStyle(color = grey, fontSize = 12.sp))
            }
            WidgetButton(R.drawable.ic_w_prev, "Önceki", actionRunCallback<PrevAction>())
            WidgetButton(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, "Oynat/Duraklat",
                actionRunCallback<PlayPauseAction>())
            WidgetButton(R.drawable.ic_w_next, "Sonraki", actionRunCallback<NextAction>())
        }
    }

    @Composable
    private fun LargeContent() {
        val prefs = currentState<Preferences>()
        val queue = prefs[WidgetKeys.QUEUE].orEmpty().lines().mapNotNull { l ->
            l.split('\t').takeIf { it.size >= 3 }?.let { Triple(it[0].toIntOrNull() ?: return@let null, it[1], it[2]) }
        }
        val white = ColorProvider(Color.White)
        val grey = ColorProvider(Color(0xFFB4B6BE))
        Column(
            modifier = GlanceModifier.fillMaxSize().background(Color(0xFF141418)).cornerRadius(24.dp).padding(10.dp)
        ) {
            Content()
            Text("Sıradakiler", style = TextStyle(color = grey, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp))
            if (queue.isEmpty()) Text("Sıra boş", style = TextStyle(color = grey, fontSize = 12.sp), modifier = GlanceModifier.padding(6.dp))
            androidx.glance.appwidget.lazy.LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(queue.size) { i ->
                    val (index, title, artist) = queue[i]
                    Column(
                        GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                            .clickable(actionRunCallback<SkipToAction>(androidx.glance.action.actionParametersOf(IndexKey to index)))
                    ) {
                        Text(title, maxLines = 1, style = TextStyle(color = white, fontSize = 13.sp))
                        Text(artist, maxLines = 1, style = TextStyle(color = grey, fontSize = 11.sp))
                    }
                }
            }
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♥  Favorileri karıştır", style = TextStyle(color = ColorProvider(Color(0xFFFF6A8A)), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(6.dp).clickable(actionRunCallback<ShuffleFavoritesAction>()))
            }
        }
    }

    @Composable
    private fun WidgetButton(icon: Int, desc: String, action: androidx.glance.action.Action) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = desc,
            modifier = GlanceModifier.size(40.dp).padding(8.dp).clickable(action)
        )
    }
}

class NovayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NovayWidget()
}
class NovayWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NovayWidgetLarge()
}
class NovayWidgetClearReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NovayWidgetClear()
}

// ---- Widget butonları: PlayerConnection üzerinden servise komut ----

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDeps {
    fun player(): PlayerConnection
    fun library(): com.novay.sound.data.repository.LibraryRepository
}

private suspend fun withPlayer(context: Context, block: (PlayerConnection) -> Unit) =
    withContext(Dispatchers.Main) {
        block(EntryPointAccessors.fromApplication(context.applicationContext, WidgetDeps::class.java).player())
    }

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { it.togglePlay() }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { it.next() }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { it.previous() }
}

class SkipToAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[IndexKey] ?: return
        withPlayer(context) { it.skipTo(index) }
    }
}

class ShuffleFavoritesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, WidgetDeps::class.java)
        val favs = deps.library().getFavorites().shuffled()
        if (favs.isNotEmpty()) withPlayer(context) { it.play(favs) }
    }
}
