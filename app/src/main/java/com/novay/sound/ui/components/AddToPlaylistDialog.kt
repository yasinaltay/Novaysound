package com.novay.sound.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novay.sound.data.db.entity.PlaylistWithCount

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çalma listesine ekle") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("Yeni liste adı") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (playlists.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(playlists, key = { it.playlist.id }) { p ->
                            Text(
                                "${p.playlist.name}  (${p.songCount})",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelect(p.playlist.id) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName) }) { Text("Oluştur ve ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    confirm: String = "Kaydet",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
