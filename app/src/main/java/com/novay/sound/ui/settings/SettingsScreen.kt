package com.novay.sound.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.novay.sound.BuildConfig
import com.novay.sound.R
import com.novay.sound.backup.AutoBackupWorker
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.ThemeMode
import com.novay.sound.data.settings.AudioQuality
import com.novay.sound.data.settings.Toggle
import com.novay.sound.alarm.AlarmScheduler
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import com.novay.sound.di.CacheModule
import com.novay.sound.di.DownloadCache
import com.novay.sound.di.PlaybackCache
import com.novay.sound.ui.theme.BrandTitleStyle
import com.novay.sound.util.CrashLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    @PlaybackCache private val cache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val cacheBytes: Long get() = runCatching { cache.cacheSpace }.getOrDefault(0L)
    val downloadBytes: Long get() = runCatching { downloadCache.cacheSpace }.getOrDefault(0L)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setPureBlack(on: Boolean) = viewModelScope.launch { repo.setPureBlack(on) }
    fun setArtworkColors(on: Boolean) = viewModelScope.launch { repo.setArtworkColors(on) }
    fun setSponsorBlock(on: Boolean) = viewModelScope.launch { repo.setSponsorBlock(on) }
    fun setQuality(q: AudioQuality) = viewModelScope.launch { repo.setAudioQuality(q) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { repo.setWifiOnlyDownloads(on) }
    fun setCrossfade(sec: Int) = viewModelScope.launch { repo.setCrossfade(sec) }
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { repo.setAutoUpdateCheck(on) }

    fun enableAutoBackup(folder: Uri) = viewModelScope.launch {
        context.contentResolver.takePersistableUriPermission(
            folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repo.setAutoBackup(true, folder.toString())
        AutoBackupWorker.schedule(context, true)
        AutoBackupWorker.runNow(context)
    }

    fun disableAutoBackup() = viewModelScope.launch {
        repo.setAutoBackup(false, null)
        AutoBackupWorker.schedule(context, false)
    }

    fun backupNow() = AutoBackupWorker.runNow(context)

    fun setToggle(t: Toggle, on: Boolean) = viewModelScope.launch {
        repo.setToggle(t, on)
        if (t == Toggle.ALARM) AlarmScheduler.reschedule(context)
    }

    fun setInt(name: String, v: Int) = viewModelScope.launch {
        repo.setInt(name, v)
        if (name.startsWith("alarm_")) AlarmScheduler.reschedule(context)
    }

    fun setAlarmSource(src: String) = viewModelScope.launch { repo.setAlarmSource(src) }

    fun clearCache() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStats: () -> Unit,
    onCheckUpdate: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var showLog by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var cleared by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::enableAutoBackup)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge)
        }

        SettingsCard("Görünüm") {
            Text("Tema", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            val icons = mapOf(
                ThemeMode.SYSTEM to Icons.Default.BrightnessAuto,
                ThemeMode.LIGHT to Icons.Default.LightMode,
                ThemeMode.DARK to Icons.Default.DarkMode
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        icon = { if (s.themeMode != mode) Icon(icons.getValue(mode), null, Modifier.size(18.dp)) },
                        label = { Text(mode.label, maxLines = 1, softWrap = false) }
                    )
                }
            }
            SwitchRow("Saf siyah (AMOLED)", "Karanlık temada tam siyah zemin, pil tasarrufu", s.pureBlack, vm::setPureBlack)
            SwitchRow("Kapak rengini kullan", "Vurgu rengi çalan şarkının kapağına göre değişir", s.artworkColors, vm::setArtworkColors)
        }

        SettingsCard("Özellikler") {
            listOf(
                Toggle.AUTO_RADIO, Toggle.CLEAN_TITLES, Toggle.DEDUPE, Toggle.SHOW_SMART, Toggle.SHOW_MOODS,
                Toggle.SHOW_QUICK, Toggle.SHOW_TRENDING, Toggle.RECORD_STATS, Toggle.SEARCH_HISTORY, Toggle.LOCK_LYRICS
            ).forEach { t -> SwitchRow(t.title, t.subtitle, s.on(t)) { vm.setToggle(t, it) } }
        }

        SettingsCard("Uyku ve alarm") {
            SwitchRow(Toggle.AUTO_SLEEP.title, Toggle.AUTO_SLEEP.subtitle, s.on(Toggle.AUTO_SLEEP)) { vm.setToggle(Toggle.AUTO_SLEEP, it) }
            if (s.on(Toggle.AUTO_SLEEP)) {
                ChipRow("Şu saatten sonra", listOf(21, 22, 23, 0), s.autoSleepHour, { "%02d:00".format(it) }) { vm.setInt("auto_sleep_hour", it) }
                ChipRow("Kaç dakika sonra dursun", listOf(15, 30, 45, 60, 90), s.autoSleepMinutes, { "$it dk" }) { vm.setInt("auto_sleep_minutes", it) }
            }
            SwitchRow(Toggle.ALARM.title, Toggle.ALARM.subtitle, s.on(Toggle.ALARM)) { vm.setToggle(Toggle.ALARM, it) }
            if (s.on(Toggle.ALARM)) {
                LinkRow("Alarm saati", "%02d:%02d • her gün".format(s.alarmHour, s.alarmMinute)) { showTime = true }
                ChipRow("Ne çalsın", listOf("FAVORITES", "DISCOVER"), s.alarmSource,
                    { if (it == "FAVORITES") "Favoriler (karışık)" else "Haftalık keşif" }) { vm.setAlarmSource(it) }
            }
        }

        SettingsCard("Ses") {
            LinkRow("Ekolayzer ve ses dengeleme", if (s.eqEnabled) "Açık" else "Kapalı", onOpenEqualizer)
            Text("Ses kalitesi", style = MaterialTheme.typography.bodyLarge)
            Text(
                when (s.audioQuality) {
                    AudioQuality.AUTO -> "Wi-Fi'da yüksek, mobil veride düşük (veri tasarrufu)"
                    AudioQuality.HIGH -> "Her zaman en yüksek (~130-160 kbps)"
                    AudioQuality.LOW -> "Her zaman düşük (~50-70 kbps, az veri)"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEachIndexed { i, q ->
                    SegmentedButton(
                        selected = s.audioQuality == q, onClick = { vm.setQuality(q) },
                        shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                        label = { Text(q.label, maxLines = 1, softWrap = false) }
                    )
                }
            }
            SwitchRow("İndirmeler sadece Wi-Fi'da", "Mobil veride indirmeler bekler", s.wifiOnlyDownloads, vm::setWifiOnly)
            Text("Şarkılar arası geçiş (crossfade)", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp))
            Text(if (s.crossfadeSec == 0) "Kapalı" else "${s.crossfadeSec} saniye",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var fade by remember(s.crossfadeSec) { mutableFloatStateOf(s.crossfadeSec.toFloat()) }
            Slider(
                value = fade, onValueChange = { fade = it }, valueRange = 0f..12f, steps = 11,
                onValueChangeFinished = { vm.setCrossfade(fade.toInt()) }
            )
            Text("Ses yükseltme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Text(if (s.boostDb == 0) "Kapalı" else "+${s.boostDb} dB (sesi zayıf şarkılar/kulaklıklar için)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var boost by remember(s.boostDb) { mutableFloatStateOf(s.boostDb.toFloat()) }
            Slider(value = boost, onValueChange = { boost = it }, valueRange = 0f..6f, steps = 5,
                onValueChangeFinished = { vm.setInt("boost_db", boost.toInt()) })
            SwitchRow("SponsorBlock", "Klip videolarındaki konuşma, sponsor ve müzik dışı kısımları atla",
                s.sponsorBlock, vm::setSponsorBlock)
        }

        SettingsCard("Kitaplık") {
            LinkRow("İstatistikler", "En çok dinlediklerin, dinleme süresi", onOpenStats)
            SwitchRow(
                "Haftalık otomatik yedek",
                if (s.autoBackup) "Klasör: ${Uri.parse(s.backupFolder.orEmpty()).lastPathSegment?.substringAfterLast(':').orEmpty()}"
                else "Seçtiğin klasöre her hafta JSON yedek",
                s.autoBackup
            ) { on -> if (on) folderPicker.launch(null) else vm.disableAutoBackup() }
            if (s.autoBackup) TextButton(onClick = vm::backupNow) { Text("Şimdi yedekle") }
        }

        SettingsCard("Depolama") {
            Text("İndirilenler: %.0f MB".format(vm.downloadBytes / 1_048_576.0), style = MaterialTheme.typography.bodyLarge)
            Text(
                "Otomatik önbellek: %.0f MB / %d MB".format(vm.cacheBytes / 1_048_576.0, s.cacheSizeMb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChipRow("Önbellek boyutu (yeniden başlatınca geçerli)", listOf(512, 1024, 2048, 5120, 10240), s.cacheSizeMb,
                { if (it < 1024) "$it MB" else "${it / 1024} GB" }) { vm.setInt("cache_size_mb", it) }
            TextButton(onClick = { vm.clearCache(); cleared = true }) {
                Text(if (cleared) "Önbellek temizlendi ✓" else "Otomatik önbelleği temizle (indirilenler kalır)")
            }
        }

        SettingsCard("Uygulama") {
            SwitchRow("Açılışta güncelleme denetle", "GitHub'da yeni sürüm varsa haber ver", s.autoUpdateCheck, vm::setAutoUpdate)
            LinkRow("Şimdi güncelleme denetle", "Sürüm ${BuildConfig.VERSION_NAME}", onCheckUpdate)
            LinkRow("Hata kaydı", "Çökme ve hata ayrıntıları", { showLog = true })
        }

        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(painterResource(R.drawable.logo_mark), null, Modifier.size(72.dp))
            Text("Novay Sound", style = BrandTitleStyle)
            Text("Sürüm ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showLog) CrashLogDialog(onDismiss = { showLog = false })
    if (showTime) {
        val state = rememberTimePickerState(s.alarmHour, s.alarmMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Alarm saati") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setInt("alarm_hour", state.hour); vm.setInt("alarm_minute", state.minute); showTime = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun <T> ChipRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            val o = options[i]
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o), maxLines = 1) })
        }
    }
}

@Composable
private fun CrashLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var log by rememberSaveable { mutableStateOf(CrashLogger.read()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hata kaydı") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(log.ifBlank { "Kayıtlı hata yok 🎉" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Row {
                if (log.isNotBlank()) {
                    TextButton(onClick = {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log), "Hata kaydını paylaş"
                        ))
                    }) { Text("Paylaş") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(log)) }) { Text("Kopyala") }
                    TextButton(onClick = { CrashLogger.clear(); log = "" }) { Text("Temizle") }
                }
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        }
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
