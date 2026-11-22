package com.novay.sound.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.lyrics.Lyrics
import com.novay.sound.data.lyrics.LyricsRepository
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.settings.AppSettings
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.spotify.SpotifyImporter
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.Mood
import com.novay.sound.data.radio.RadioRepository
import com.novay.sound.data.follow.FollowRepository
import com.novay.sound.data.follow.FollowedArtist
import com.novay.sound.data.follow.NewReleaseWorker
import com.novay.sound.data.share.PlaylistCodec
import com.novay.sound.data.smart.SleepSound
import com.novay.sound.data.smart.SmartPlaylists
import com.novay.sound.data.spotify.SpotifyLink
import com.novay.sound.data.youtube.YouTubeLink
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.download.DownloadTracker
import com.novay.sound.playback.PlayerConnection
import com.novay.sound.playback.SleepTimer
import com.novay.sound.ui.components.SongActions
import com.novay.sound.update.UpdateInfo
import com.novay.sound.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ImportProgress(val name: String, val done: Int, val total: Int)

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUi
}

@HiltViewModel
class MainViewModel @Inject constructor(
    val player: PlayerConnection,
    val sleepTimer: SleepTimer,
    private val library: LibraryRepository,
    private val youtube: YouTubeRepository,
    private val downloads: DownloadTracker,
    private val lyricsRepo: LyricsRepository,
    private val updates: UpdateRepository,
    private val spotify: SpotifyImporter,
    private val smart: SmartPlaylists,
    private val radio: RadioRepository,
    private val follows: FollowRepository,
    settingsRepo: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playerState = player.state
    val playlists = library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloadItems = downloads.items

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** Ekran geçişi istekleri (ör. sanatçı sayfası). */
    private val _navigation = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val navigation = _navigation.asSharedFlow()

    /** Çalma listesine eklenmek üzere seçilen şarkı (dialog açık). */
    val playlistTarget = MutableStateFlow<Song?>(null)

    private val _import = MutableStateFlow<ImportProgress?>(null)
    val import = _import.asStateFlow()

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val update = _update.asStateFlow()

    val actions = SongActions(
        play = player::play,
        playNext = { player.playNext(it); msg("Sıradaki: ${it.title}") },
        enqueue = { player.enqueue(it); msg("Sıraya eklendi") },
        radio = player::startRadio,
        toggleLike = { s -> viewModelScope.launch { library.toggleLike(s) } },
        addToPlaylist = { playlistTarget.value = it },
        download = { downloads.download(it); msg("İndiriliyor: ${it.title}") },
        removeDownload = { downloads.remove(it); msg("İndirme silindi") },
        downloadAll = { songs -> downloads.downloadAll(songs); msg("${songs.size} şarkı indirilecek") },
        openArtist = ::openArtist
    )

    init {
        viewModelScope.launch {
            if (settingsRepo.current().autoUpdateCheck) checkUpdate(manual = false)
        }
    }

    fun isLiked(id: String): Flow<Boolean> = library.isLiked(id)
    suspend fun lyrics(song: Song): Lyrics? = lyricsRepo.get(song)

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch {
        val added = library.addToPlaylist(playlistId, song)
        msg(if (added) "Listeye eklendi" else "Bu şarkı zaten listede")
        playlistTarget.value = null
    }

    fun createPlaylistAndAdd(name: String, song: Song) = viewModelScope.launch {
        val id = library.createPlaylist(name)
        library.addToPlaylist(id, song)
        msg("\"$name\" oluşturuldu")
        playlistTarget.value = null
    }

    fun openArtist(song: Song) = viewModelScope.launch {
        val url = song.artistUrl ?: youtube.findArtistUrl(song.artist)
        if (url == null) { msg("Sanatçı bulunamadı"); return@launch }
        openArtistUrl(url)
    }

    /** Ruh hali listesi: YouTube Music'te ara, karıştır, çal (sonsuz radyo devam ettirir). */
    fun playMood(mood: Mood) = viewModelScope.launch {
        msg("${mood.emoji} ${mood.label} hazırlanıyor…")
        val songs = runCatching { youtube.search(mood.query).songs }.getOrDefault(emptyList())
        if (songs.isEmpty()) msg("Liste yüklenemedi") else player.play(songs.shuffled())
    }

    // ---- Uyku sesleri ----
    fun playSleepSound(sound: SleepSound) = viewModelScope.launch {
        msg("${sound.emoji} ${sound.label} yükleniyor…")
        val song = runCatching { youtube.search(sound.query, com.novay.sound.data.youtube.SearchFilter.VIDEOS).songs }
            .getOrDefault(emptyList()).maxByOrNull { it.durationSec }
        if (song == null) { msg("Ses bulunamadı"); return@launch }
        player.play(listOf(song))
        player.repeatOne()
        if (!sleepTimer.state.value.active) {
            sleepTimer.start(60)
            msg("Uyku zamanlayıcısı 60 dakikaya kuruldu")
        }
    }

    // ---- Sanatçı takibi ----
    val followedArtists = follows.artists
    fun toggleFollow(name: String, url: String, avatar: String?) {
        if (follows.isFollowing(url)) { follows.unfollow(url); msg("$name takipten çıkarıldı") }
        else {
            follows.follow(FollowedArtist(name, url, avatar))
            NewReleaseWorker.schedule(appContext)
            msg("$name takip ediliyor: yeni şarkı çıkınca haber vereceğim")
        }
    }

    // ---- Liste paylaşma ----
    fun shareLink(name: String, songs: List<Song>): String = PlaylistCodec.encode(name, songs)

    private fun importShared(text: String): Boolean {
        val (name, songs) = PlaylistCodec.decode(text) ?: return false
        viewModelScope.launch {
            if (songs.isEmpty()) { msg("Paylaşılan liste boş"); return@launch }
            val id = library.createPlaylist(name)
            songs.forEach { library.addToPlaylist(id, it) }
            player.play(songs)
            msg("\"$name\" kitaplığa eklendi (${songs.size} şarkı)")
        }
        return true
    }

    val radioFavorites = radio.favorites
    fun toggleRadioFavorite(station: Song) = radio.toggleFavorite(station)

    fun navigateTo(route: String) { _navigation.tryEmit(route) }

    fun playSmart(type: SmartList) = viewModelScope.launch {
        val songs = runCatching { smart.songs(type) }.getOrDefault(emptyList())
        if (songs.isEmpty()) msg("${type.title} şu an boş") else player.play(songs)
    }

    fun openArtistUrl(url: String) { _navigation.tryEmit("artist?url=${Uri.encode(url)}") }

    // ---------------- Güncelleme ----------------

    fun checkUpdate(manual: Boolean) = viewModelScope.launch {
        runCatching { updates.check() }
            .onSuccess { info ->
                if (info != null) _update.value = UpdateUi.Available(info)
                else if (manual) msg("Uygulama güncel")
            }
            .onFailure { if (manual) msg("Güncelleme denetlenemedi: ${it.message}") }
    }

    fun downloadUpdate() {
        val info = (_update.value as? UpdateUi.Available)?.info ?: return
        viewModelScope.launch {
            _update.value = UpdateUi.Downloading(info, 0f)
            runCatching { updates.download(info) { p -> _update.value = UpdateUi.Downloading(info, p) } }
                .onSuccess { file -> _update.value = UpdateUi.Ready(info, file); installUpdate() }
                .onFailure { _update.value = UpdateUi.Available(info); msg("İndirme başarısız: ${it.message}") }
        }
    }

    fun installUpdate() {
        val ready = _update.value as? UpdateUi.Ready ?: return
        if (!updates.canInstall()) {
            msg("\"Bilinmeyen uygulamaları yükle\" iznini verip tekrar Kur'a dokun")
            updates.openInstallPermissionSettings()
            return
        }
        updates.install(ready.file)
    }

    fun dismissUpdate() { _update.value = UpdateUi.None }

    // ---------------- Link / sesli komut ----------------

    /** Paylaşılan / yapıştırılan metin YouTube linkiyse işle. Link değilse false. */
    fun handleText(text: String): Boolean {
        if (text.contains(PlaylistCodec.PREFIX)) return importShared(text)
        spotify.parse(text)?.let { importSpotify(it); return true }
        val link = youtube.parseLink(text) ?: return false
        viewModelScope.launch {
            when (link) {
                is YouTubeLink.Video -> {
                    val song = runCatching { youtube.getSong(link.id) }.getOrElse {
                        msg("Video açılamadı: ${it.message}"); return@launch
                    }
                    player.startRadio(song)
                }
                is YouTubeLink.Playlist -> {
                    msg("Çalma listesi alınıyor…")
                    val (name, songs) = runCatching { youtube.getPlaylist(link.url) }.getOrElse {
                        msg("Liste alınamadı: ${it.message}"); return@launch
                    }
                    if (songs.isEmpty()) { msg("Liste boş"); return@launch }
                    val id = library.createPlaylist(name)
                    songs.forEach { library.addToPlaylist(id, it) }
                    player.play(songs)
                    msg("\"$name\" kitaplığa kaydedildi (${songs.size} şarkı)")
                }
            }
        }
        return true
    }

    /** Spotify listesi/albümü -> YouTube'da eşleştir -> yeni çalma listesi. */
    private fun importSpotify(link: SpotifyLink) = viewModelScope.launch {
        if (_import.value != null) { msg("Bir aktarım zaten sürüyor"); return@launch }
        _import.value = ImportProgress("Spotify", 0, 0)
        try {
            val (name, tracks) = runCatching { spotify.fetch(link) }.getOrElse {
                msg("Spotify okunamadı: ${it.message}"); return@launch
            }
            if (tracks.isEmpty()) { msg("Liste boş"); return@launch }
            _import.value = ImportProgress(name, 0, tracks.size)
            val songs = spotify.match(tracks) { done, total -> _import.value = ImportProgress(name, done, total) }
            if (songs.isEmpty()) { msg("Hiçbir şarkı eşleşmedi"); return@launch }
            if (link.type == "track") player.startRadio(songs.first())
            else {
                val id = library.createPlaylist(name)
                songs.forEach { library.addToPlaylist(id, it) }
                player.play(songs)
                msg("\"$name\": ${songs.size}/${tracks.size} şarkı bulundu ve kaydedildi")
            }
        } finally {
            _import.value = null
        }
    }

    /** Sesli komut (MEDIA_PLAY_FROM_SEARCH) uygulama ekranına gelirse. */
    fun playFromSearch(query: String) = viewModelScope.launch {
        if (query.isBlank()) {
            val favs = library.getFavorites().shuffled().ifEmpty { library.getRecent() }
            if (favs.isNotEmpty()) player.play(favs) else msg("Kitaplık boş")
            return@launch
        }
        val songs = runCatching { youtube.search(query).songs }.getOrElse { library.searchLocal(query) }
        songs.firstOrNull()?.let(player::startRadio) ?: msg("\"$query\" bulunamadı")
    }

    fun msg(text: String) { _messages.tryEmit(text) }
}
