package com.novay.sound.ui

import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novay.sound.data.settings.ThemeMode
import com.novay.sound.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var player: PlayerConnection
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startService(android.content.Intent(this, com.novay.sound.playback.TaskWatcherService::class.java)) }
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Durum/gezinme çubuğu ikon renkleri seçilen temaya uysun
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose {}
            }
            NovayRoot(vm, darkTheme = dark)
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) player.disconnect()
    }

    /** YouTube'dan paylaşım, link açma ve "Ok Google ... çal" komutları. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                if (!vm.handleText(it)) Toast.makeText(this, "YouTube linki bulunamadı", Toast.LENGTH_SHORT).show()
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { vm.handleText(it) }
            "com.novay.sound.action.OPEN_ARTIST" -> intent.getStringExtra("url")?.let(vm::openArtistUrl)
            // Uygulama kısayolları
            "com.novay.sound.action.SHUFFLE_FAVORITES" -> vm.playFromSearch("")
            "com.novay.sound.action.DISCOVER" -> vm.playSmart(com.novay.sound.data.smart.SmartList.DISCOVER)
            "com.novay.sound.action.CAR_MODE" -> vm.navigateTo("car")
            "com.novay.sound.action.SEARCH" -> vm.navigateTo("search")
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ->
                vm.playFromSearch(intent.getStringExtra(SearchManager.QUERY).orEmpty())
        }
    }
}
