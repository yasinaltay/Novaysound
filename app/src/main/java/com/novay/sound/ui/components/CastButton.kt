package com.novay.sound.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent

/** Chromecast / Google TV / Nest hoparlör seçici (AppCompat gerektirmeyen Compose sürümü). */
@Composable
fun CastButton(tint: Color = LocalContentColor.current, activeTint: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    val router = remember { runCatching { MediaRouter.getInstance(context) }.getOrNull() } ?: return
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    var selected by remember { mutableStateOf(router.selectedRoute) }
    var dialog by remember { mutableStateOf(false) }

    DisposableEffect(dialog) {
        fun refresh() {
            routes = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            selected = router.selectedRoute
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        router.addCallback(selector, callback, if (dialog) MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN else 0)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    val connected = !selected.isDefault && selected.matchesSelector(selector)
    IconButton(onClick = { dialog = true }) {
        Icon(if (connected) Icons.Default.CastConnected else Icons.Default.Cast, "Yayınla",
            tint = if (connected) activeTint else tint)
    }

    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text(if (connected) "Bağlı: ${selected.name}" else "Cihaza yayınla") },
        text = {
            Column {
                if (routes.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Aynı Wi-Fi'daki cihazlar aranıyor…")
                }
                routes.forEach { route ->
                    Row(
                        Modifier.fillMaxWidth().clickable { router.selectRoute(route); dialog = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV) Icons.Default.Tv else Icons.Default.Speaker, null,
                            tint = if (route.id == selected.id) activeTint else LocalContentColor.current
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(route.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            if (connected) TextButton(onClick = { router.unselect(MediaRouter.UNSELECT_REASON_STOPPED); dialog = false }) {
                Text("Bağlantıyı kes")
            }
        },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text("Kapat") } }
    )
}
