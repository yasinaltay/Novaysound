package com.novay.sound.ui.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.radio.RadioCountry
import com.novay.sound.data.radio.RadioRepository
import com.novay.sound.ui.components.Artwork
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RadioUi {
    data object Loading : RadioUi
    data class Ready(val stations: List<Song>) : RadioUi
    data class Error(val message: String) : RadioUi
}

@HiltViewModel
class RadioViewModel @Inject constructor(private val repo: RadioRepository) : ViewModel() {
    val country = MutableStateFlow(RadioCountry.TR)
    val favorites = repo.favorites
    private val _ui = MutableStateFlow<RadioUi>(RadioUi.Loading)
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun setCountry(c: RadioCountry) { country.value = c; load() }

    fun load(query: String = "") {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = RadioUi.Loading
            _ui.value = runCatching {
                RadioUi.Ready(if (query.isBlank()) repo.top(country.value) else repo.search(country.value, query))
            }.getOrElse { RadioUi.Error(it.message ?: "Radyo listesi alınamadı") }
        }
    }

    fun toggleFavorite(s: Song) = repo.toggleFavorite(s)
}

@Composable
fun RadioScreen(
    currentId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    vm: RadioViewModel = hiltViewModel()
) {
    val country by vm.country.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val favIds = favorites.map { it.id }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text("Radyo", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                RadioCountry.entries.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = c == country,
                        onClick = { query = ""; vm.setCountry(c) },
                        shape = SegmentedButtonDefaults.itemShape(i, RadioCountry.entries.size),
                        label = { Text("${c.flag} ${c.label}", maxLines = 1) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("İstasyon ara (ör. Kral, Power, 1LIVE)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.load() }) { Icon(Icons.Default.Close, "Temizle") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.load(query) }),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }

        if (favorites.isNotEmpty() && query.isBlank()) {
            item { SectionTitle("Favori istasyonlar") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(favorites, key = { _, s -> "f" + s.id }) { i, s ->
                        Column(
                            Modifier.width(96.dp).clickable { onPlay(favorites, i) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StationLogo(s, 88)
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        item { SectionTitle(if (query.isBlank()) "En çok dinlenenler" else "Arama sonuçları") }
        when (val s = ui) {
            RadioUi.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is RadioUi.Error -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Default.ErrorOutline, s.message)
                    Button(onClick = { vm.load(query) }) { Text("Tekrar dene") }
                }
            }
            is RadioUi.Ready -> {
                if (s.stations.isEmpty()) item { EmptyState(Icons.Default.Radio, "İstasyon bulunamadı") }
                itemsIndexed(s.stations, key = { _, st -> st.id }) { i, st ->
                    StationRow(st, st.id == currentId, st.id in favIds,
                        onClick = { onPlay(s.stations, i) }, onFavorite = { vm.toggleFavorite(st) })
                }
            }
        }
    }
}

@Composable
private fun StationLogo(s: Song, size: Int) {
    Box(contentAlignment = Alignment.Center) {
        Artwork(s.thumbnailUrl, Modifier.size(size.dp), corner = 14.dp)
        if (s.thumbnailUrl == null) Icon(Icons.Default.Radio, null, Modifier.size((size / 2).dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StationRow(s: Song, isCurrent: Boolean, isFavorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(s, 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favori istasyon",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
