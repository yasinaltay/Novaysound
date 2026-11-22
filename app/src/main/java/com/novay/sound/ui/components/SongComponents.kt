package com.novay.sound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.compositionLocalOf
import com.novay.sound.download.DlState
import com.novay.sound.download.DownloadItem
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.novay.sound.data.model.Song

/** Tüm ekranlarda ortak şarkı işlemleri (MainViewModel sağlar). */
@Immutable
data class SongActions(
    val play: (List<Song>, Int) -> Unit,
    val playNext: (Song) -> Unit,
    val enqueue: (Song) -> Unit,
    val radio: (Song) -> Unit,
    val toggleLike: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val download: (Song) -> Unit = {},
    val removeDownload: (String) -> Unit = {},
    val downloadAll: (List<Song>) -> Unit = {},
    val openArtist: (Song) -> Unit = {},
    val remove: ((Song) -> Unit)? = null,
    val removeLabel: String = "Listeden kaldır"
)

/** İndirme durumları tüm ekranlara buradan dağıtılır. */
val LocalDownloads = compositionLocalOf<Map<String, DownloadItem>> { emptyMap() }

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    actions: SongActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.thumbnailUrl, Modifier.size(52.dp), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadBadge(song.id)
                Text(
                    listOf(song.artist, formatDuration(song.durationSec * 1000)).filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, "Seçenekler", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SongMenu(song, actions, menu) { menu = false }
        }
    }
}

@Composable
fun SongMenu(song: Song, actions: SongActions, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun item(text: String, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
            text = { Text(text) },
            leadingIcon = { Icon(icon, null) },
            onClick = { onDismiss(); action() }
        )
        item("Radyoyu başlat", Icons.Default.Radio) { actions.radio(song) }
        item("Sıradaki olarak çal", Icons.Default.SkipNext) { actions.playNext(song) }
        item("Sıraya ekle", Icons.AutoMirrored.Filled.QueueMusic) { actions.enqueue(song) }
        item("Favori ekle/kaldır", Icons.Default.Favorite) { actions.toggleLike(song) }
        item("Çalma listesine ekle", Icons.AutoMirrored.Filled.PlaylistAdd) { actions.addToPlaylist(song) }
        when (LocalDownloads.current[song.id]?.state) {
            null, DlState.FAILED -> item("İndir", Icons.Default.Download) { actions.download(song) }
            else -> item("İndirmeyi sil", Icons.Default.DeleteOutline) { actions.removeDownload(song.id) }
        }
        item("Sanatçıya git", Icons.Default.Person) { actions.openArtist(song) }
        actions.remove?.let { remove -> item(actions.removeLabel, Icons.Default.Delete) { remove(song) } }
    }
}

@Composable
fun DownloadBadge(id: String) {
    val item = LocalDownloads.current[id] ?: return
    when (item.state) {
        DlState.DONE -> Icon(Icons.Default.DownloadDone, "İndirildi", Modifier.size(16.dp).padding(end = 4.dp),
            tint = MaterialTheme.colorScheme.primary)
        DlState.DOWNLOADING, DlState.QUEUED -> CircularProgressIndicator(
            progress = { item.percent / 100f }, modifier = Modifier.size(14.dp).padding(end = 4.dp), strokeWidth = 2.dp
        )
        DlState.FAILED -> Icon(Icons.Default.ErrorOutline, "İndirilemedi", Modifier.size(16.dp).padding(end = 4.dp),
            tint = MaterialTheme.colorScheme.error)
    }
}

/** Yatay listelerde kare kart. */
@Composable
fun SongCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(140.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Artwork(song.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 10.dp)
        Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artist, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
