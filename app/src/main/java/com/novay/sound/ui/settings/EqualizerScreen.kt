package com.novay.sound.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.playback.AudioEffects
import com.novay.sound.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val repo: SettingsRepository,
    effects: AudioEffects
) : ViewModel() {
    val info = effects.info
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    fun setEnabled(on: Boolean) = viewModelScope.launch { repo.setEqEnabled(on) }
    fun setPreset(index: Int, levels: List<Int>) = viewModelScope.launch { repo.setEq(index, levels) }
    fun setBands(levels: List<Int>) = viewModelScope.launch { repo.setEq(-1, levels) }
    fun setNormalize(on: Boolean) = viewModelScope.launch { repo.setNormalize(on) }
}

@Composable
fun EqualizerScreen(onBack: () -> Unit, vm: EqualizerViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Text("Ekolayzer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.eqEnabled, onCheckedChange = vm::setEnabled, modifier = Modifier.padding(end = 16.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ses dengeleme", style = MaterialTheme.typography.bodyLarge)
                Text("Sessiz ve gürültülü şarkılar arasındaki farkı azaltır (Android 9+)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.normalize, onCheckedChange = vm::setNormalize)
        }

        val eq = info
        if (eq == null || eq.maxLevel <= eq.minLevel || eq.bandFreqsHz.isEmpty()) {
            EmptyState(Icons.Default.GraphicEq, "Bir şarkı çalmaya başlayınca ekolayzer ayarları görünür.")
            return@Column
        }

        val levels = remember(eq) { mutableStateListOf<Int>() }
        LaunchedEffect(eq, s.eqBands, s.eqPreset) {
            val target = when {
                s.eqBands.size == eq.bandFreqsHz.size -> s.eqBands
                s.eqPreset in eq.presetLevels.indices -> eq.presetLevels[s.eqPreset]
                else -> List(eq.bandFreqsHz.size) { 0 }
            }
            levels.clear(); levels.addAll(target)
        }

        Text("Hazır ayarlar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(eq.presets) { i, name ->
                FilterChip(
                    selected = s.eqPreset == i && s.eqBands == eq.presetLevels[i],
                    enabled = s.eqEnabled,
                    onClick = { vm.setPreset(i, eq.presetLevels[i]) },
                    label = { Text(name) }
                )
            }
        }

        Text("Bantlar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
        levels.forEachIndexed { i, level ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                val hz = eq.bandFreqsHz[i]
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { levels[i] = it.toInt() },
                    onValueChangeFinished = { vm.setBands(levels.toList()) },
                    valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                    enabled = s.eqEnabled,
                    modifier = Modifier.weight(1f)
                )
                Text("%+.0f dB".format(level / 100f), modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(
            onClick = { vm.setBands(List(eq.bandFreqsHz.size) { 0 }) }, enabled = s.eqEnabled,
            modifier = Modifier.padding(start = 12.dp)
        ) { Text("Sıfırla") }
    }
}
