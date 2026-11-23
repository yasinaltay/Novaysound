package com.novay.sound.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.novay.sound.R
import com.novay.sound.data.model.Song
import com.novay.sound.data.repository.LibraryRepository
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.youtube.SponsorBlockRepository
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.util.CrashLogger
import com.novay.sound.util.FeatureFlags
import com.novay.sound.data.settings.Toggle
import com.novay.sound.data.lyrics.LyricsRepository
import com.novay.sound.data.smart.SmartList
import com.novay.sound.data.smart.SmartPlaylists
import com.novay.sound.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CMD_LIKE = "novay.LIKE"
        const val CMD_CLOSE = "novay.CLOSE"
        @Volatile var instance: java.lang.ref.WeakReference<PlaybackService>? = null
        const val ACTION_ALARM = "com.novay.sound.ALARM"
        const val ACTION_AUTOPLAY = "com.novay.sound.AUTOPLAY"
        private const val ALARM_NOTIFICATION = 77
    }

    @Inject lateinit var dataSourceFactory: AudioDataSourceFactory
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var youtube: YouTubeRepository
    @Inject lateinit var tree: LibraryTree
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var effects: AudioEffects
    @Inject lateinit var sponsorBlock: SponsorBlockRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var widget: WidgetUpdater
    @Inject lateinit var queueStore: QueueStore
    @Inject lateinit var flags: FeatureFlags
    @Inject lateinit var lyricsRepo: LyricsRepository
    @Inject lateinit var smart: SmartPlaylists
    @Inject lateinit var radio: com.novay.sound.data.radio.RadioRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    private var radioJob: Job? = null
    private var lyricJob: Job? = null
    private var replacingForLyrics = false
    private var autoSleepNight: String? = null
    private var alarmPending = false
    private var restoring = false
    private var skipJob: Job? = null
    private var fadeJob: Job? = null
    private val retryCount = mutableMapOf<String, Int>()
    private val currentId = MutableStateFlow<String?>(null)

    // Sonsuz radyo + SponsorBlock ayarı (DataStore'dan)
    private var autoRadio = true
    private var sponsorEnabled = true
    private var segments: List<LongRange> = emptyList()

    // Dinleme istatistiği
    private var trackedSong: Song? = null
    private var listenedMs = 0L
    private var playingSince: Long? = null

    private val likeCommand = SessionCommand(CMD_LIKE, Bundle.EMPTY)
    private val closeCommand = SessionCommand(CMD_CLOSE, Bundle.EMPTY)
    private var closing = false
    private var currentLiked = false
    private val dismissedCommand = SessionCommand(androidx.media3.session.MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY, Bundle.EMPTY)

    /** Kart kaydırılıp silindiyse (müzik sürerken) bildirimi yeniden göster. */
    private fun repostNotification() {
        if (closing) return
        scope.launch {
            delay(100)
            if (closing || player.mediaItemCount == 0) return@launch
            // YouTube Music gibi: kaydırılıp silinen kartı hemen yeniden göster
            session?.let { onUpdateNotification(it, true) }
        }
    }

    /** Bazı telefonlar silme olayını bildirmez: çalarken bildirim yoksa geri getir. */
    private fun watchNotification() = scope.launch {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        while (true) {
            delay(700)
            if (closing || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) continue
            val shown = runCatching {
                nm.activeNotifications.any {
                    it.id == androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID ||
                        it.notification.channelId == androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
                }
            }.getOrDefault(true)
            if (!shown) repostNotification()
        }
    }
    private lateinit var crossfader: Crossfader
    private var cast: CastController? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory.create()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true   // arama/navigasyon gelince duraklat/kıs
            )
            .setHandleAudioBecomingNoisy(true)  // Bluetooth kopunca / kulaklık çıkınca duraklat
            .setWakeMode(C.WAKE_MODE_NETWORK)   // ekran kilitliyken CPU + Wi-Fi uyanık
            .build()
            .apply { addListener(PlayerListener()) }

        crossfader = Crossfader(this, dataSourceFactory, scope, player).also { it.start() }
        effects.attach(player.audioSessionId)

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .apply { openApp?.let { setSessionActivity(it) } }
            .setCustomLayout(ImmutableList.of(likeButton(false), closeButton))
            .build()

        observeLikeState()
        instance = java.lang.ref.WeakReference(this)
        watchNotification()
        scope.launch {
            settings.settings.collect {
                sponsorEnabled = it.sponsorBlock
                crossfader.seconds = it.crossfadeSec
                autoRadio = it.on(Toggle.AUTO_RADIO)
                player.skipSilenceEnabled = it.on(Toggle.SKIP_SILENCE)
            }
        }
        // Konumu çalarken 15 sn'de bir kaydet
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.isPlaying) saveQueue()
            }
        }
        // Chromecast: oturum açılınca MediaSession'ın oynatıcısı CastPlayer olur
        cast = CastController(this, scope, youtube, player) { p -> session?.player = p }.also { it.init() }

        // Kaldığın yerden devam: her şey hazır olduktan sonra (aksi halde açılışta çökme)
        restoreQueue()
        scope.launch { sleepTimer.fire.collect { fadeOutAndPause() } }
        scope.launch {
            sleepTimer.state.collect { player.pauseAtEndOfMediaItems = it.endOfSong }
        }
    }

    /** Müzikli alarm: AlarmReceiver servisi bu eylemle başlatır. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ALARM) {
            startAlarm()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTOPLAY) {
            startTempForeground("Novay Sound", "Bluetooth bağlandı, müzik başlıyor…")
            if (player.mediaItemCount == 0) restoreQueue()
            scope.launch {
                if (player.mediaItemCount == 0) {
                    val songs = library.getRecent().take(50)
                    if (songs.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); return@launch }
                    player.setMediaItems(songs.map { it.toMediaItem() })
                }
                player.prepare()
                player.play()
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Arka plandan başlatılınca (alarm, Bluetooth) 5 sn içinde ön plana geçmek zorunlu. */
    private fun startTempForeground(title: String, text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(
            android.app.NotificationChannel("alarm", "Alarm ve otomatik çalma", android.app.NotificationManager.IMPORTANCE_HIGH)
        )
        val n = androidx.core.app.NotificationCompat.Builder(this, "alarm")
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
        androidx.core.app.ServiceCompat.startForeground(
            this, ALARM_NOTIFICATION, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        alarmPending = true // çalma başlayınca bu bildirim kaldırılır
    }

    private fun startAlarm() {
        startTempForeground("Günaydın ☀️", "Novay Sound alarmı başlıyor…")
        scope.launch {
            val s = flags.current
            val songs = (if (s.alarmSource == "DISCOVER") runCatching { smart.songs(SmartList.DISCOVER) }.getOrDefault(emptyList())
            else library.getFavorites().shuffled())
                .ifEmpty { library.getRecent() }
                .ifEmpty { runCatching { youtube.trending() }.getOrDefault(emptyList()) }
            if (songs.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); return@launch }
            player.volume = 0.1f
            player.setMediaItems(songs.take(60).map { it.toMediaItem() })
            player.prepare()
            player.play()
            // Sesi 60 sn'de yavaşça aç
            for (i in 1..30) { delay(2000); player.volume = 0.1f + 0.9f * i / 30f }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /**
     * Bildirim/medya kartı duraklatılınca da sabit kalsın: kaydırarak kapanmaz, sadece ✕ ile kapanır.
     * (Servis ön planda kaldıkça Android kartı kaydırmaya izin vermez.)
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val active = !closing && session.player.mediaItemCount > 0 && session.player.playbackState != Player.STATE_IDLE
        super.onUpdateNotification(session, startInForegroundRequired || active)
    }

    /**
     * Uygulama son kullanılanlardan kaydırılınca: varsayılan olarak müzik durur ve servis kapanır.
     * "Uygulama kapatılınca çalmaya devam et" açıksa ve çalıyorsa devam eder.
     */
    override fun onTaskRemoved(rootIntent: Intent?) = onAppTaskRemoved("service")

    fun onAppTaskRemoved(from: String) {
        val keepSetting = flags.on(Toggle.KEEP_PLAYING)
        val keep = keepSetting && player.playWhenReady && player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED
        CrashLogger.log("Uygulama kapatıldı ($from): çalmaya devam ayarı=${if (keepSetting) "AÇIK" else "kapalı"}",
            Exception(if (keep) "müzik devam ediyor" else "müzik durduruldu"))
        if (keep || closing) return
        closePlayback()
    }

    override fun onDestroy() {
        instance = null
        saveQueue(sync = true)
        flushListen()
        crossfader.release()
        cast?.release()
        effects.release()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ======================= Kaldığın yerden devam =======================

    private fun restoreQueue() {
        if (player.mediaItemCount > 0) return
        val saved = queueStore.load() ?: return
        restoring = true
        runCatching {
            player.setMediaItems(
                saved.songs.map { it.toMediaItem() },
                saved.index.coerceIn(0, saved.songs.lastIndex),
                saved.positionMs.coerceAtLeast(0)
            )
            player.prepare()
        }.onFailure {
            CrashLogger.log("Sıra geri yüklenemedi", it)
            queueStore.clear()
        }
        restoring = false
    }

    private fun saveQueue(sync: Boolean = false) {
        if (player.mediaItemCount == 0) return
        val q = SavedQueue(
            songs = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toSongOrNull() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition
        )
        if (sync) queueStore.save(q) else scope.launch(Dispatchers.IO) { queueStore.save(q) }
    }

    // ======================= Bildirim ❤️ butonu =======================

    private fun likeButton(liked: Boolean): CommandButton =
        CommandButton.Builder()
            .setDisplayName(if (liked) "Favorilerden çıkar" else "Favorilere ekle")
            .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            .setSessionCommand(likeCommand)
            .build()

    private val closeButton: CommandButton by lazy {
        CommandButton.Builder()
            .setDisplayName("Kapat")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(closeCommand)
            .build()
    }

    /** Müziği durdur, bildirimi ve medya kartını kaldır, servisi kapat (sıra kaydedilir, sonra kaldığın yerden açılır). */
    private fun closePlayback() {
        closing = true
        saveQueue(sync = true)
        flushListen()
        crossfader.cancel()
        session?.player?.let { if (it !== player) { it.pause(); it.stop() } }
        player.pause()
        player.stop()
        player.playWhenReady = false
        stopSelf()
    }

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLikeState() = scope.launch {
        currentId.flatMapLatest { id -> if (id == null) flowOf(false) else library.isLiked(id) }
            .collect { liked -> currentLiked = liked; session?.setCustomLayout(ImmutableList.of(likeButton(liked), closeButton)) }
    }

    private fun toggleLikeCurrent() {
        val song = (session?.player ?: player).currentMediaItem?.toSongOrNull() ?: return
        if (song.isRadio) { radio.toggleFavorite(song); return }
        scope.launch { library.toggleLike(song) }
    }

    // ======================= Uyku zamanlayıcısı =======================

    private fun fadeOutAndPause() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val start = player.volume
            for (i in 20 downTo 0) {
                player.volume = start * i / 20f
                delay(400)
            }
            player.pause()
            player.volume = start
        }
    }

    /** Büyük widget için sıradaki şarkılar (oynatıcıdaki sıra numarasıyla). */
    private fun upcoming(): List<Pair<Int, Song>> {
        val start = player.currentMediaItemIndex + 1
        return (start until minOf(player.mediaItemCount, start + 6))
            .mapNotNull { i -> player.getMediaItemAt(i).toSongOrNull()?.let { i to it } }
    }

    // ======================= Otomatik uyku =======================

    /** Gece (ayarlanan saatten sabah 5'e kadar) çalma başlarsa, o gece bir kez zamanlayıcı kur. */
    private fun maybeAutoSleep() {
        val s = flags.current
        if (!s.on(Toggle.AUTO_SLEEP) || sleepTimer.state.value.active) return
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        if (!(h >= s.autoSleepHour || h < 5)) return
        if (h < 5) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val night = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        if (night == autoSleepNight) return
        autoSleepNight = night
        sleepTimer.start(s.autoSleepMinutes)
    }

    // ======================= Kilit ekranı sözleri (deneysel) =======================

    private fun startLockLyrics(song: Song) {
        lyricJob?.cancel()
        if (!flags.on(Toggle.LOCK_LYRICS)) return
        lyricJob = scope.launch {
            val lyrics = runCatching { lyricsRepo.get(song) }.getOrNull()?.takeIf { it.isSynced } ?: return@launch
            var last = -1
            while (isActive && player.currentMediaItem?.videoId == song.id && flags.on(Toggle.LOCK_LYRICS)) {
                val idx = lyrics.synced.indexOfLast { it.timeMs <= player.currentPosition + 300 }
                if (idx >= 0 && idx != last) {
                    last = idx
                    showLyricLine(lyrics.synced[idx].text.ifBlank { "♪" })
                }
                delay(400)
            }
        }
    }

    private fun showLyricLine(line: String) {
        val item = player.currentMediaItem ?: return
        replacingForLyrics = true
        try {
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtist("♪ $line").build()).build()
            )
        } finally {
            replacingForLyrics = false
        }
    }

    // ======================= SponsorBlock =======================

    private fun loadSegments(id: String) {
        segments = emptyList()
        if (!sponsorEnabled || id.startsWith("local:")) return
        scope.launch {
            val list = sponsorBlock.segments(id)
            if (player.currentMediaItem?.videoId == id) {
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                // Neredeyse tüm şarkıyı kaplayan işaretlemeleri yok say
                segments = list.filter { (it.last - it.first) < duration * 0.8 }
                startSkipLoop()
            }
        }
    }

    private fun startSkipLoop() {
        skipJob?.cancel()
        if (segments.isEmpty()) return
        skipJob = scope.launch {
            while (isActive) {
                if (player.isPlaying && sponsorEnabled) {
                    val pos = player.currentPosition
                    segments.firstOrNull { pos >= it.first && pos < it.last - 500 }?.let { player.seekTo(it.last) }
                }
                delay(400)
            }
        }
    }

    // ======================= İstatistik =======================

    private fun pauseListenClock() {
        playingSince?.let { listenedMs += SystemClock.elapsedRealtime() - it }
        playingSince = null
    }

    /** 30 sn veya şarkının yarısı dinlendiyse istatistiğe yaz. */
    private fun flushListen() {
        pauseListenClock()
        val song = trackedSong
        val ms = listenedMs
        if (song != null && !song.isRadio && flags.on(Toggle.RECORD_STATS) && (ms >= 30_000 || (song.durationSec > 0 && ms >= song.durationSec * 500))) {
            scope.launch { runCatching { library.recordPlay(song.id, ms) } }
        }
        listenedMs = 0
    }

    // ======================= Oynatıcı olayları =======================

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Kilit ekranı sözleri için yapılan meta güncellemesi: şarkı değişmedi
            if (replacingForLyrics || (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                    mediaItem != null && mediaItem.videoId == currentId.value)) return
            // Açılışta kayıtlı sıranın geri yüklenmesi "dinleme" sayılmaz
            if (restoring) {
                trackedSong = mediaItem?.toSongOrNull()
                currentId.value = trackedSong?.id
                widget.update(trackedSong, false)
                return
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) crossfader.onAutoTransition() else crossfader.cancel()
            flushListen()
            val song = mediaItem?.toSongOrNull()
            trackedSong = song
            if (player.isPlaying) playingSince = SystemClock.elapsedRealtime()
            currentId.value = song?.id
            song?.takeIf { !it.isRadio }?.let { s ->
                scope.launch { library.onPlayed(s) }
                loadSegments(s.id)
                startLockLyrics(s)
            }
            maybeExtendRadio()
            widget.update(song, player.isPlaying, upcoming())
            saveQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !replacingForLyrics && !restoring) saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) closing = false
            if (isPlaying) {
                maybeAutoSleep()
                if (alarmPending) {
                    alarmPending = false
                    scope.launch { delay(3000); getSystemService(android.app.NotificationManager::class.java).cancel(ALARM_NOTIFICATION) }
                }
            }
            if (isPlaying) crossfader.onMainPlaying()
            else if (!crossfader.isHandoff && player.playbackState != Player.STATE_BUFFERING) crossfader.cancel()
            if (isPlaying) playingSince = SystemClock.elapsedRealtime() else pauseListenClock()

            // "Şarkı bitince dur" tetiklendiyse zamanlayıcıyı kapat
            if (!isPlaying && sleepTimer.state.value.endOfSong && player.playbackState == Player.STATE_READY &&
                !player.playWhenReady
            ) sleepTimer.cancel()
            widget.update(player.currentMediaItem?.toSongOrNull(), isPlaying, upcoming())
            if (!isPlaying) saveQueue()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = effects.attach(audioSessionId)

        override fun onPlayerError(error: PlaybackException) {
            CrashLogger.log("Oynatma hatası", error)
            val id = player.currentMediaItem?.videoId ?: return
            val tries = retryCount.getOrDefault(id, 0)
            val http = generateSequence(error.cause) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()

            when {
                // Süresi dolmuş/engellenmiş URL -> yeniden çöz ve kaldığı yerden devam
                tries < 2 && (http?.responseCode == 403 || http?.responseCode == 410) -> {
                    retryCount[id] = tries + 1
                    youtube.invalidate(id)
                    player.prepare()
                    player.play()
                }
                // Çözülemeyen şarkıyı atla
                player.hasNextMediaItem() -> {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun maybeExtendRadio() {
        if (!autoRadio || radioJob?.isActive == true) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount >= 300) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > 2) return
        val seed = player.currentMediaItem?.videoId?.takeIf { !it.startsWith("local:") && !it.startsWith("radio:") } ?: return

        radioJob = scope.launch {
            // Sırada olanlar ve son dinlediklerin tekrar eklenmesin
            val existing = (0 until player.mediaItemCount)
                .map { player.getMediaItemAt(it).videoId }.toSet() +
                runCatching { library.getRecent().take(25).map { it.id } }.getOrDefault(emptyList())
            val more = runCatching { youtube.radio(seed) }.getOrDefault(emptyList())
                .filter { it.id !in existing }
                .take(25)
            if (more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    // ======================= Android Auto / Controller =======================

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /** ❤️ komutunu tüm denetleyicilere (bildirim, Auto, kilit ekranı) aç. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon().add(likeCommand).add(closeCommand).add(dismissedCommand).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_LIKE -> toggleLikeCurrent()
                CMD_CLOSE -> closePlayback()
                androidx.media3.session.MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY -> repostNotification()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Kök + Android Auto ipuçları (arama desteği, liste/ızgara görünümü). */
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // liste
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)  // liste
            }
            return Futures.immediateFuture(
                LibraryResult.ofItem(tree.root, LibraryParams.Builder().setExtras(extras).build())
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = tree.children(parentId)
            val paged = if (pageSize in 1 until Int.MAX_VALUE) {
                all.drop(page * pageSize).take(pageSize)
            } else all
            LibraryResult.ofItemList(paged, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        // ---- Android Auto arama çubuğu ----
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            session.notifySearchResultChanged(browser, query, 20, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(searchSongs(query).map { it.toMediaItem() }, params)
        }

        // ---- Kuyruğa ekleme (telefon arayüzü, Auto, sesli komut) ----
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            if (query != null) playFromSearch(query).mediaItems.toMutableList()
            else resolve(mediaItems).toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            if (single != null) {
                // Sesli komut: "Ok Google, Novay Sound'da Sezen Aksu çal"
                val query = single.requestMetadata.searchQuery
                if (query != null || single.mediaId.isEmpty()) {
                    return@future playFromSearch(query.orEmpty())
                }
                // Android Auto'da listeden şarkı seçildi -> tüm listeyi kuyruğa al
                val parent = single.mediaId.substringBeforeLast('/', "")
                if (parent.isNotEmpty()) {
                    val songs = tree.songsOf(parent)
                    val index = songs.indexOfFirst { it.id == single.videoId }
                    if (index >= 0) {
                        return@future MediaItemsWithStartPosition(
                            songs.map { it.toMediaItem() }, index, C.TIME_UNSET
                        )
                    }
                }
            }
            MediaItemsWithStartPosition(resolve(mediaItems), startIndex, startPositionMs)
        }

        /** Bluetooth/araçta "oynat" tuşu, uygulama kapalıyken: son çalınanlardan devam. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            queueStore.load()?.let { q ->
                return@future MediaItemsWithStartPosition(
                    q.songs.map { it.toMediaItem() }, q.index.coerceIn(0, q.songs.lastIndex), q.positionMs
                )
            }
            val recent = library.getRecent().take(50)
            MediaItemsWithStartPosition(recent.map { it.toMediaItem() }, 0, C.TIME_UNSET)
        }
    }

    // ======================= Yardımcılar =======================

    /**
     * Controller'dan gelen MediaItem'lar URI'siz gelir (güvenlik gereği Media3 siler).
     * Başlık varsa ondan, yoksa DB'den, o da yoksa YouTube'dan yeniden kurulur.
     */
    private suspend fun resolve(items: List<MediaItem>): List<MediaItem> = items.mapNotNull { item ->
        val song = item.toSongOrNull()
            ?: library.getSong(item.videoId)
            ?: runCatching { youtube.getSong(item.videoId) }.getOrNull()
        song?.toMediaItem()
    }

    private suspend fun searchSongs(query: String): List<Song> =
        runCatching { youtube.search(query).songs }
            .getOrElse { library.searchLocal(query) } // internet yoksa kitaplıkta ara
            .ifEmpty { library.searchLocal(query) }

    /** Boş komut ("müzik çal") -> karışık favoriler, yoksa son çalınanlar. */
    private suspend fun playFromSearch(query: String): MediaItemsWithStartPosition {
        val songs = if (query.isBlank()) {
            library.getFavorites().shuffled().ifEmpty { library.getRecent() }
        } else searchSongs(query)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
    }
}
