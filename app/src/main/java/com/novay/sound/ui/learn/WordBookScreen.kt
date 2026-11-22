package com.novay.sound.ui.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novay.sound.data.learn.SavedWord
import com.novay.sound.ui.MainViewModel
import com.novay.sound.ui.components.EmptyState
import java.util.Locale

/** Şarkı sözlerinden kaydedilen kelimeler. Kart modunda anlam dokununca açılır (kendini sınama). */
@Composable
fun WordBookScreen(vm: MainViewModel, onBack: () -> Unit) {
    val words by vm.savedWords.collectAsStateWithLifecycle()
    var cardMode by rememberSaveable { mutableStateOf(false) }
    var langFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val langs = words.map { it.lang }.distinct()
    val shown = words.filter { langFilter == null || it.lang == langFilter }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Column(Modifier.weight(1f)) {
                    Text("Kelime defterim", style = MaterialTheme.typography.titleLarge)
                    Text("${words.size} kelime", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (words.isNotEmpty()) item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = cardMode, onClick = { cardMode = !cardMode }, label = { Text("🃏 Kart modu") }) }
                item { FilterChip(selected = langFilter == null, onClick = { langFilter = null }, label = { Text("Tümü") }) }
                items(langs) { l ->
                    FilterChip(selected = langFilter == l, onClick = { langFilter = l },
                        label = { Text(Locale.forLanguageTag(l).getDisplayLanguage(Locale.forLanguageTag("tr"))) })
                }
            }
        }
        if (words.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Filled.MenuBook,
                "Defterin boş.\nSözlerde 📖 Öğren'i aç, bir kelimeye dokun ve \"Defterime ekle\"ye bas.")
        }
        items(shown, key = { it.lang + it.word }) { w -> WordCard(w, cardMode, onDelete = { vm.removeWord(w) }) }
    }
}

@Composable
private fun WordCard(w: SavedWord, cardMode: Boolean, onDelete: () -> Unit) {
    var revealed by remember(cardMode) { mutableStateOf(!cardMode) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { revealed = !revealed || !cardMode },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AnimatedVisibility(revealed) {
                    Text(w.meaning, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (!revealed) Text("Anlamı görmek için dokun", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“${w.line}”", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Text("🎵 ${w.song}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Sil") }
        }
    }
}
