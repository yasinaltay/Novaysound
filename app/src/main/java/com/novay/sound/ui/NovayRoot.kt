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

        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        NotificationPermission()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        MiniPlayer(state, onOpen = { showPlayer = true },
                            onToggle = vm.player::togglePlay, onNext = vm.player::next)
                        BottomBar(nav)
                    }
                }
            ) { padding ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") {
                        HomeScreen(actions, currentId, onOpenSearch = { nav.navigateTab("search") },
                            onOpenSettings = { nav.navigate("settings") })
                    }
                    composable("search") {
                        SearchScreen(actions, currentId, onLink = vm::handleText)
                    }
                    composable("library") {
                        LibraryScreen(actions, currentId, onOpenPlaylist = { nav.navigate("playlist/$it") })
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
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
                    state = state, player = vm.player, accent = accent, isLiked = liked,
                    onLike = actions.toggleLike, onAddToPlaylist = actions.addToPlaylist,
                    onClose = { showPlayer = false }
                )
            }
        }

        BackHandler(enabled = showPlayer) { showPlayer = false }

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
            val selected = route == tab.route || (tab.route == "library" && route?.startsWith("playlist") == true)
            NavigationBarItem(
                selected = selected,
                onClick = { nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label) }
            )
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
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
