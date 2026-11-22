package com.novay.sound.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.novay.sound.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var player: PlayerConnection
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NovayRoot(vm) }
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
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ->
                vm.playFromSearch(intent.getStringExtra(SearchManager.QUERY).orEmpty())
        }
    }
}
