package com.novay.sound.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Radio
import com.novay.sound.ui.radio.RadioScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novay.sound.ui.components.AddToPlaylistDialog
import com.novay.sound.ui.components.LocalDownloads
import com.novay.sound.ui.artist.ArtistScreen
import com.novay.sound.ui.smart.SmartScreen
import com.novay.sound.ui.car.CarModeScreen
import com.novay.sound.ui.stats.StatsScreen
import com.novay.sound.ui.settings.EqualizerScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import com.novay.sound.ui.home.HomeScreen
import com.novay.sound.ui.library.LibraryScreen
import com.novay.sound.ui.library.PlaylistScreen
import com.novay.sound.ui.player.FullPlayer
import com.novay.sound.ui.player.MiniPlayer
import com.novay.sound.ui.search.SearchScreen
import com.novay.sound.ui.settings.SettingsScreen
import androidx.compose.ui.unit.dp
import com.novay.sound.ui.theme.NovayTheme
import com.novay.sound.ui.theme.rememberArtworkAccent

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Ana sayfa", Icons.Default.Home),
    Tab("search", "Ara", Icons.Default.Search),
    Tab("radio", "Radyo", Icons.Default.Radio),
    Tab("library", "Kitaplık", Icons.Default.LibraryMusic)
)

@Composable
fun NovayRoot(vm: MainViewModel, darkTheme: Boolean) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accent by rememberArtworkAccent(state.song?.thumbnailUrl, settings.artworkColors, darkTheme)

    NovayTheme(darkTheme = darkTheme, pureBlack = settings.pureBlack, accent = accent) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        var showPlayer by rememberSaveable { mutableStateOf(false) }
        val actions = vm.actions
        val currentId = state.song?.id

        val downloads by vm.downloadItems.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) { vm.navigation.collect { route -> showPlayer = false; nav.navigate(route) } }
        NotificationPermission()

        CompositionLocalProvider(LocalDownloads provides downloads) {

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (nav.currentBackStackEntryAsState().value?.destination?.route != "car") Column {
                        MiniPlayer(state, onOpen = { showPlayer = true },
                            onToggle = vm.player::togglePlay, onNext = vm.player::next)
                        BottomBar(nav)
                    }
                }
            ) { padding ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") {
                        HomeScreen(actions, currentId, onOpenSearch = { nav.navigateTab("search") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenSmart = { nav.navigate("smart/${it.name}") },
                            onOpenCar = { nav.navigate("car") },
                            onMood = vm::playMood)
                    }
                    composable("search") {
                        SearchScreen(actions, currentId, onLink = vm::handleText, onOpenArtist = vm::openArtistUrl)
                    }
                    composable("radio") { RadioScreen(currentId, onPlay = actions.play) }
                    composable("library") {
                        LibraryScreen(actions, currentId, onOpenPlaylist = { nav.navigate("playlist/$it") },
                            onOpenStats = { nav.navigate("stats") },
                            onOpenSmart = { nav.navigate("smart/${it.name}") })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenEqualizer = { nav.navigate("equalizer") },
                            onOpenStats = { nav.navigate("stats") },
                            onCheckUpdate = { vm.checkUpdate(manual = true) }
                        )
                    }
                    composable("car") { CarModeScreen(vm, onClose = { nav.popBackStack() }) }
                    composable("smart/{type}", arguments = listOf(navArgument("type") { type = NavType.StringType })) {
                        SmartScreen(actions, currentId, onBack = { nav.popBackStack() })
                    }
                    composable("equalizer") { EqualizerScreen(onBack = { nav.popBackStack() }) }
                    composable("stats") {
                        StatsScreen(actions, onBack = { nav.popBackStack() }, onOpenArtist = vm::openArtistUrl)
                    }
                    composable("artist?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) {
                        ArtistScreen(actions, currentId, onBack = { nav.popBackStack() })
                    }
                    composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        PlaylistScreen(actions, currentId, onBack = { nav.popBackStack() })
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlayer && state.song != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                val liked by remember(currentId) { vm.isLiked(currentId.orEmpty()) }
                    .collectAsStateWithLifecycle(false)
                FullPlayer(
                    state = state, vm = vm, accent = accent, isLiked = liked,
                    onClose = { showPlayer = false },
                    onOpenEqualizer = { showPlayer = false; nav.navigate("equalizer") }
                )
            }
        }
        }

        BackHandler(enabled = showPlayer) { showPlayer = false }

        val import by vm.import.collectAsStateWithLifecycle()
        import?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Spotify'dan aktarılıyor") },
                text = {
                    Column {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        if (p.total > 0) {
                            LinearProgressIndicator(progress = { p.done / p.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text("${p.done} / ${p.total} şarkı YouTube'da aranıyor", style = MaterialTheme.typography.bodySmall)
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {}
            )
        }

        val update by vm.update.collectAsStateWithLifecycle()
        UpdateDialog(update, onUpdate = vm::downloadUpdate, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)

        val target by vm.playlistTarget.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        target?.let { song ->
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { vm.playlistTarget.value = null },
                onSelect = { vm.addToPlaylist(it, song) },
                onCreate = { vm.createPlaylistAndAdd(it, song) }
            )
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        TABS.forEach { tab ->
            val owner = when {
                route == null -> "home"
                route == "search" -> "search"
                route == "radio" -> "radio"
                route == "library" || route.startsWith("playlist") -> "library"
                else -> "home" // ayarlar, istatistik, sanatçı, akıllı liste… ana sayfa sekmesine ait
            }
            val selected = owner == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label) }
            )
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    // Ana sayfa: üstte ne açıksa (Ayarlar, İstatistik, Sanatçı…) kapat, ana sayfaya dön.
    // Ana sayfanın kayıtlı durumu geri yüklenmez; yoksa Ayarlar tekrar açılırdı.
    if (route == "home") {
        if (!popBackStack("home", inclusive = false)) navigate("home") { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun UpdateDialog(update: UpdateUi, onUpdate: () -> Unit, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val info = when (update) {
        UpdateUi.None -> return
        is UpdateUi.Available -> update.info
        is UpdateUi.Downloading -> update.info
        is UpdateUi.Ready -> update.info
    }
    AlertDialog(
        onDismissRequest = { if (update !is UpdateUi.Downloading) onDismiss() },
        title = { Text("Yeni sürüm: v${info.version}") },
        text = {
            Column {
                if (info.notes.isNotBlank()) Text(info.notes.take(600), style = MaterialTheme.typography.bodyMedium)
                if (update is UpdateUi.Downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
                    Text("%${(update.progress * 100).toInt()} indirildi", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when (update) {
                is UpdateUi.Available -> TextButton(onClick = onUpdate) { Text("Güncelle") }
                is UpdateUi.Ready -> TextButton(onClick = onInstall) { Text("Kur") }
                else -> {}
            }
        },
        dismissButton = {
            if (update !is UpdateUi.Downloading) TextButton(onClick = onDismiss) { Text("Sonra") }
        }
    )
}

/** Android 13+: bildirim panelindeki oynatıcı kontrolleri için izin iste. */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
