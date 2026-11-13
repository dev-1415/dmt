package dev.jyotiraditya.dmt.presentation.player

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.core.base.BaseViewModel
import dev.jyotiraditya.dmt.core.common.PermissionChecker
import dev.jyotiraditya.dmt.core.common.UiText
import dev.jyotiraditya.dmt.data.library.MediaIndexRepair
import dev.jyotiraditya.dmt.data.lyrics.LyricsEmbedder
import dev.jyotiraditya.dmt.data.repository.AnimatedArtworkRepository
import dev.jyotiraditya.dmt.data.repository.PlaylistRepository
import dev.jyotiraditya.dmt.data.repository.PreferencesRepository
import dev.jyotiraditya.dmt.data.repository.TrackMediaRepository
import dev.jyotiraditya.dmt.domain.model.DmtSettings
import dev.jyotiraditya.dmt.domain.model.LibraryScan
import dev.jyotiraditya.dmt.domain.model.LibrarySnapshot
import dev.jyotiraditya.dmt.domain.model.SourceDraft
import dev.jyotiraditya.dmt.domain.model.TrackSource
import dev.jyotiraditya.dmt.domain.repository.ArtworkRepository
import dev.jyotiraditya.dmt.domain.usecase.ConnectSourceUseCase
import dev.jyotiraditya.dmt.domain.usecase.GetLyricsUseCase
import dev.jyotiraditya.dmt.domain.usecase.GetTrackTechUseCase
import dev.jyotiraditya.dmt.domain.usecase.ScanLibraryUseCase
import dev.jyotiraditya.dmt.playback.PlaybackCommands
import dev.jyotiraditya.dmt.playback.PlaybackConnection
import dev.jyotiraditya.dmt.presentation.settings.loginMessage
import dev.jyotiraditya.dmt.util.audioPermission
import dev.jyotiraditya.dmt.util.clearUpcoming
import dev.jyotiraditya.dmt.util.enqueue
import dev.jyotiraditya.dmt.util.jumpTo
import dev.jyotiraditya.dmt.util.moveInQueue
import dev.jyotiraditya.dmt.util.playNext
import dev.jyotiraditya.dmt.util.playTracks
import dev.jyotiraditya.dmt.util.playedAudioFormat
import dev.jyotiraditya.dmt.util.removeFromQueue
import dev.jyotiraditya.dmt.util.resolveQueue
import dev.jyotiraditya.dmt.util.suspendRunCatching
import dev.jyotiraditya.dmt.util.toQueueRequest
import dev.jyotiraditya.lyrics.LyricsParser
import dev.jyotiraditya.lyrics.toLrc
import dev.jyotiraditya.lyrics.toStandardLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LIBRARY_SETTLE = 500.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackConnection: PlaybackConnection,
    permissionChecker: PermissionChecker,
    private val preferencesRepository: PreferencesRepository,
    private val scanLibrary: ScanLibraryUseCase,
    private val connectSource: ConnectSourceUseCase,
    private val getLyrics: GetLyricsUseCase,
    getTrackTech: GetTrackTechUseCase,
    private val trackMediaRepository: TrackMediaRepository,
    animatedArtworkRepository: AnimatedArtworkRepository,
    private val playlistRepository: PlaylistRepository,
    val artwork: ArtworkRepository,
) : BaseViewModel<DmtAction, DmtState>(
    DmtState(hasPermission = permissionChecker.isGranted(audioPermission)),
) {

    private val lyrics = LyricsLoader(getLyrics, preferencesRepository, viewModelScope, store)
    private val details =
        NowPlayingDetailsLoader(
            artwork = artwork,
            animatedArtwork = animatedArtworkRepository,
            getTrackTech = getTrackTech,
            scope = viewModelScope,
            store = store,
        )
    private val sleepAndSpeed = SleepAndSpeed(preferencesRepository, viewModelScope, store)

    /** The connected player, for the controls that read and drive it directly. */
    val player: StateFlow<Player?>
        field = MutableStateFlow<Player?>(null)

    private var controller: MediaController? = null
    private var noticeJob: Job? = null
    private var scanJob: Job? = null
    private var filterJob: Job? = null
    private var sessionRestored = false

    /** Whether the app is visible; lyrics and covers are only loaded while it is. */
    private var foreground = false

    /** Drives the observers that should only run while the app is visible. */
    private val foregroundFlow = MutableStateFlow(false)

    /** Whether the app has been in the background since it was last visible. */
    private var wasBackgrounded = false
    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            preferencesRepository.settings.collect { settings ->
                reduce { it.copy(settings = settings, settingsLoaded = true) }
            }
        }
        viewModelScope.launch {
            val stored = preferencesRepository.lastScan()
            reduce { it.copy(lastScan = it.lastScan ?: stored) }
        }
        viewModelScope.launch {
            preferencesRepository.stats.collect { stats ->
                reduce { it.copy(stats = stats).withHome() }
            }
        }
        viewModelScope.launch {
            foregroundFlow
                .flatMapLatest { active ->
                    if (active) {
                        trackMediaRepository
                            .routeSpecs()
                            .distinctUntilChanged()
                            .flowOn(Dispatchers.IO)
                    } else {
                        emptyFlow()
                    }
                }
                .collect { route -> reduce { it.copy(route = route) } }
        }
        viewModelScope.launch {
            queries
                .drop(1)
                .debounce { if (it.isEmpty()) Duration.ZERO else 150.milliseconds }
                .collect { refilter() }
        }
        viewModelScope.launch {
            foregroundFlow
                .flatMapLatest { active ->
                    if (active) scanLibrary.changes().debounce(LIBRARY_SETTLE) else emptyFlow()
                }
                .collect {
                    if (currentState.hasPermission) scan()
                }
        }
        if (currentState.hasPermission) scan()
        connect()
    }

    private fun mutatePlaylists(block: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            val playlists = playlistRepository.load(currentState.tracks)
            reduce { it.copy(playlists = playlists) }
        }
    }

    /** Re-indexes every audio file in the library, then reads the library again. */
    private fun repairIndex() {
        viewModelScope.launch {
            reduce { it.copy(scanning = true) }
            val roots = currentState.library.folderRoots.map { it.path }
            val repaired = MediaIndexRepair.repair(context, roots)
            reduce { it.copy(scanning = false) }
            notify(UiText(R.string.notice_index_repaired, repaired))
            scan(refresh = true)
        }
    }

    /** Saves the tracks currently queued as a new playlist. */
    private fun saveQueue() {
        val tracks = currentState.queue.mapNotNull { currentState.library.track(it.mediaId) }
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val name = playlistRepository.createFromTracks("Queue", tracks)
            if (name == null) {
                reduce { it.copy(error = UiText(R.string.error_playlist_export)) }
            } else {
                val playlists = playlistRepository.load(currentState.tracks)
                reduce { it.copy(playlists = playlists) }
                withContext(Dispatchers.Main) {
                    notify(UiText(R.string.notice_playlist_saved, name))
                }
            }
        }
    }

    override fun onIntent(intent: DmtAction) {
        val c = controller
        when (intent) {
            is DmtAction.Permission -> {
                reduce { it.copy(hasPermission = intent.granted) }
                if (intent.granted) scan()
            }

            DmtAction.Rescan -> scan(refresh = true)

            DmtAction.RepairIndex -> repairIndex()

            is DmtAction.Query -> {
                reduce { it.copy(query = intent.value) }
                queries.value = intent.value
            }

            is DmtAction.Show -> {
                reduce {
                    it.copy(
                        view = intent.view,
                        error = null,
                        scrollToTrackId = null,
                    )
                }
            }

            DmtAction.ResetFolder ->
                reduce { it.copy(openFolder = it.settings.startFolder) }

            is DmtAction.OpenAlbum -> reduce { it.copy(openAlbum = intent.id) }

            is DmtAction.OpenArtist -> reduce { it.copy(openArtist = intent.name) }

            is DmtAction.OpenFolder ->
                reduce { it.copy(openFolder = intent.path, scrollToTrackId = null) }

            is DmtAction.ScrollToTrack -> reduce { it.copy(scrollToTrackId = intent.id) }

            is DmtAction.OpenGenre -> reduce { it.copy(openGenre = intent.name) }

            is DmtAction.OpenPlaylist -> reduce { it.copy(openPlaylist = intent.name) }

            is DmtAction.CreatePlaylist ->
                mutatePlaylists {
                    playlistRepository.create(intent.name)
                }

            is DmtAction.DeletePlaylist -> {
                reduce { it.copy(openPlaylist = null) }
                mutatePlaylists { playlistRepository.delete(intent.name) }
            }

            is DmtAction.AddToPlaylist ->
                mutatePlaylists {
                    playlistRepository.addTrack(intent.name, intent.track)
                }

            is DmtAction.RemoveFromPlaylist ->
                mutatePlaylists {
                    playlistRepository.removeTrack(intent.name, intent.path)
                }

            is DmtAction.MoveInPlaylist ->
                mutatePlaylists {
                    playlistRepository.moveTrack(intent.name, intent.from, intent.to)
                }

            DmtAction.SaveQueue -> saveQueue()

            is DmtAction.ImportPlaylist ->
                viewModelScope.launch(Dispatchers.IO) {
                    val playlist = playlistRepository.import(intent.uri, currentState.tracks)
                    if (playlist == null) {
                        reduce { it.copy(error = UiText(R.string.error_playlist_import)) }
                    } else {
                        val playlists = playlistRepository.load(currentState.tracks)
                        reduce { it.copy(playlists = playlists) }
                        withContext(Dispatchers.Main) {
                            notify(UiText(R.string.notice_playlist_imported, playlist.name))
                        }
                    }
                }

            is DmtAction.ExportPlaylist ->
                viewModelScope.launch(Dispatchers.IO) {
                    val exported =
                        playlistRepository.export(intent.name, intent.uri, currentState.tracks)
                    if (exported) {
                        withContext(Dispatchers.Main) {
                            notify(UiText(R.string.notice_playlist_exported, intent.name))
                        }
                    } else {
                        reduce { it.copy(error = UiText(R.string.error_playlist_export)) }
                    }
                }

            is DmtAction.PlayAt ->
                c?.run {
                    reduce { it.copy(error = null) }
                    startForeground()
                    playTracks(intent.list, intent.index)
                }

            is DmtAction.ShufflePlay ->
                c?.takeIf { intent.list.isNotEmpty() }
                    ?.run {
                        reduce { it.copy(error = null) }
                        startForeground()
                        playTracks(intent.list, intent.list.indices.random())
                        shuffleModeEnabled = true
                    }

            is DmtAction.Enqueue ->
                c?.run {
                    enqueue(intent.list)
                    notify(UiText(R.string.notice_queued, intent.label))
                }

            is DmtAction.PlayNext ->
                c?.run {
                    playNext(intent.list)
                    notify(UiText(R.string.notice_playing_next, intent.label))
                }

            is DmtAction.MoveInQueue -> c?.moveInQueue(intent.from, intent.to)

            DmtAction.ClearUpcoming ->
                c?.clearUpcoming(currentState.queue, currentState.queuePosition)

            is DmtAction.Jump ->
                c?.run {
                    startForeground()
                    jumpTo(intent.index)
                }

            is DmtAction.Seek ->
                c?.run {
                    val duration = currentState.durationMs
                    if (duration > 0) seekTo((intent.fraction * duration).toLong())
                }

            is DmtAction.RemoveAt -> c?.removeFromQueue(intent.index)

            DmtAction.OpenLyricsSources -> lyrics.openSources()

            DmtAction.EmbedLyrics -> embedLyrics()

            is DmtAction.SaveLyrics -> saveLyrics(intent.text, intent.raw)

            DmtAction.CloseLyricsSources -> reduce { it.copy(lyricsSourcesOpen = false) }

            is DmtAction.SetLyricsSource -> lyrics.choose(intent.source, c?.currentMediaItem)

            is DmtAction.SetSleep -> c?.let { sleepAndSpeed.setSleep(it, intent.minutes) }

            DmtAction.SleepAtSongEnd -> c?.let(sleepAndSpeed::sleepAtSongEnd)

            is DmtAction.SetSpeed -> c?.let { sleepAndSpeed.setSpeed(it, intent.speed) }

            is DmtAction.Config -> {
                val old = currentState.settings
                reduce { it.copy(settings = intent.settings) }
                if (old.librarySort != intent.settings.librarySort) refilter()
                val switched = leaveSource(old, intent.settings)
                viewModelScope.launch {
                    preferencesRepository.save(intent.settings)
                    if (switched || old.hiddenFolders != intent.settings.hiddenFolders) scan()
                }
                if (old.animatedArt != intent.settings.animatedArt) {
                    details.loadAnimatedArt(c?.currentMediaItem)
                }
                if (old.lyricsSource != intent.settings.lyricsSource) {
                    lyrics.load(c?.currentMediaItem)
                }
                c?.let { sleepAndSpeed.onSettings(it, old, intent.settings) }
            }

            is DmtAction.OpenSourceForm ->
                reduce {
                    it.copy(
                        view = DmtView.SOURCE_FORM,
                        sourceForm = SourceForm(intent.kind, intent.sourceId),
                        sourceError = null,
                    )
                }

            is DmtAction.SaveSource -> saveSource(intent.draft)
        }
    }

    /** Stops playback if [next] uses a different source than [old], and returns whether it did. */
    private fun leaveSource(old: DmtSettings, next: DmtSettings): Boolean {
        if (old.server?.id == next.server?.id) return false
        controller?.run {
            stop()
            clearMediaItems()
        }
        return true
    }

    /** Saves [draft], leaves its form for Sources, and rescans if the source in use changed. */
    private fun saveSource(draft: SourceDraft) = viewModelScope.launch {
        reduce { it.copy(connecting = true, sourceError = null) }
        val old = currentState.settings
        val failure = connectSource(draft).exceptionOrNull()
        if (failure != null) {
            reduce {
                it.copy(connecting = false, sourceError = UiText(draft.kind.loginMessage(failure)))
            }
            return@launch
        }
        val next = preferencesRepository.settings.first()
        reduce {
            it.copy(
                settings = next,
                connecting = false,
                view = if (it.view == DmtView.SOURCE_FORM) DmtView.SOURCES else it.view,
            )
        }
        leaveSource(old, next)
        if (old.server != next.server) scan()
    }

    private fun connect() = viewModelScope.launch {
        val c = suspendRunCatching { playbackConnection.connect() }.getOrNull() ?: return@launch
        controller = c
        val mirror =
            PlayerStateMirror(
                player = c,
                store = store,
                onItemChanged = ::loadNowPlaying,
                onTracksChanged = { details.loadTech(c.currentMediaItem, it.playedAudioFormat()) },
            )
        c.addListener(mirror)
        mirror.syncAll()
        player.value = c
        sleepAndSpeed.restore(c)
        loadNowPlaying(c.currentMediaItem)
        restoreSession()
    }

    /** Promotes the service to the foreground before playback, for Android 17 while-in-use audio. */
    private fun MediaController.startForeground() {
        sendCustomCommand(PlaybackCommands.START_FOREGROUND, Bundle.EMPTY)
    }

    private fun scan(refresh: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            reduce { it.copy(scanning = true) }
            val before = currentState.library
            suspendRunCatching {
                scanLibrary(refresh).collect { library ->
                    showLibrary(library)
                    restoreSession()
                }
            }
                .onSuccess {
                    val after = currentState.library
                    recordScan(LibraryScan.between(before.tracks, after.tracks, compare = refresh))
                }
                .onFailure { failure ->
                    showScanFailure(failure, keepLibrary = currentState.library !== before)
                }
            reduce { it.copy(scanning = false) }
            mutatePlaylists()
        }
    }

    private suspend fun recordScan(scan: LibraryScan) {
        reduce { it.copy(lastScan = scan) }
        preferencesRepository.saveLastScan(scan)
    }

    /** Puts [library] on screen, filtered by the current search and sort. */
    private suspend fun showLibrary(library: LibrarySnapshot) {
        filterJob?.cancel()
        val filtered = filterLibrary(library, currentState.query)
        val playing = controller?.currentMediaItem
        val wasKnown = currentState.nowPlayingTrack != null
        reduce { it.withLibrary(library).withFiltered(filtered).withHome() }
        if (!wasKnown && currentState.nowPlayingTrack != null) loadNowPlaying(playing)
    }

    private suspend fun filterLibrary(library: LibrarySnapshot, query: String): FilteredLibrary {
        val sort = currentState.settings.librarySort
        return withContext(Dispatchers.Default) { library.filtered(query, sort) }
    }

    /**
     * Filters the library again for the current search and sort, dropping a filter still running.
     */
    private fun refilter() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val filtered = filterLibrary(currentState.library, currentState.query)
            reduce { it.withFiltered(filtered) }
        }
    }

    private fun showScanFailure(failure: Throwable, keepLibrary: Boolean) {
        val message = scanFailureMessage(currentState.settings, failure)
        reduce { state ->
            if (keepLibrary) {
                state.copy(error = message)
            } else {
                state.withoutLibrary().copy(error = message)
            }
        }
    }

    /** Queues the last session once the library is known, unless something is queued already. */
    private fun restoreSession() {
        if (sessionRestored) return
        val c = controller ?: return
        val tracks = currentState.tracks
        if (tracks.isEmpty()) return
        sessionRestored = true
        if (c.mediaItemCount > 0) return
        viewModelScope.launch {
            val session = preferencesRepository.lastSession() ?: return@launch
            val (existing, index, position) = session.resolveQueue(tracks) ?: return@launch
            existing.getOrNull(index)?.let { track ->
                reduce {
                    it.copy(
                        nowPlayingId = track.id.toString(),
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                    )
                }
            }
            c.shuffleModeEnabled = session.shuffle
            c.setMediaItems(
                existing.map { it.toQueueRequest() },
                index,
                position,
            )
            c.prepare()
        }
    }

    /** Tells the view model whether the app is visible, so background playback stays light. */
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        foregroundFlow.value = value
        if (!value) {
            wasBackgrounded = true
            return
        }
        loadNowPlaying(controller?.currentMediaItem)
        if (wasBackgrounded) {
            wasBackgrounded = false
            if (currentState.hasPermission) scan(refresh = true)
        }
    }

    private fun loadNowPlaying(mediaItem: MediaItem?) {
        if (!foreground) return
        details.load(mediaItem, controller?.currentTracks?.playedAudioFormat())
        lyrics.load(mediaItem)
    }

    /** Writes the lyrics on screen into the playing track's file, replacing any embedded ones. */
    private fun embedLyrics() {
        val track = currentState.nowPlayingTrack ?: return
        val currentLyrics = currentState.lyrics ?: return
        if (track.source != TrackSource.LOCAL || track.path.isBlank()) return
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        val wasPlaying = c.isPlaying
        // Stop playback so the player releases the file before the tag writer renames it.
        c.stop()
        viewModelScope.launch {
            delay(500)
            val standard = currentLyrics.toStandardLrc()
            val rawTtml = currentLyrics.raw?.takeIf { it.trimStart().startsWith("<") }
            val rich = rawTtml?.let { "DMT-TTML:$it" } ?: "DMT-LRC:${currentLyrics.toLrc()}"
            LyricsEmbedder.embed(context, track, standard, rich, rawTtml).fold(
                onSuccess = {
                    // The file may keep its old size and modified date, so drop the cached lyrics.
                    getLyrics.invalidateLocal(track.path)
                    notify(UiText(R.string.notice_lyrics_embedded))
                    // Re-read the file's tags so the library shows the same title and artist.
                    scan(refresh = true)
                    // Show the newly embedded lyrics at once, not the old ones.
                    lyrics.load(c.currentMediaItem)
                },
                onFailure = {
                    reduce { it.copy(error = UiText(R.string.error_lyrics_embed)) }
                    // A failed write may still have changed MediaStore, so read the tags again.
                    scan(refresh = true)
                },
            )
            c.setMediaItems(items, index, position)
            c.prepare()
            if (wasPlaying) c.play()
        }
    }

    /** Writes edited lyrics into the playing track's file, keeping line timing when it can. */
    private fun saveLyrics(text: String, raw: Boolean) {
        val track = currentState.nowPlayingTrack ?: return
        if (track.source != TrackSource.LOCAL || track.path.isBlank()) return
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        val wasPlaying = c.isPlaying
        val original = currentState.lyrics
        val edited = text.lines().map { it.trimEnd() }
        // Keep the original line and word timing when the edit has the same number of lines.
        // A line whose words changed loses its word timing, but the others keep theirs.
        val isTtml = raw && text.trimStart().startsWith("<")
        val lrc =
            if (!raw && original != null && edited.size == original.lines.size) {
                val lines =
                    original.lines.mapIndexed { i, line ->
                        if (edited[i] == line.text) {
                            line
                        } else {
                            line.copy(
                                text = edited[i],
                                words = emptyList(),
                                translation = emptyList(),
                                transliteration = null,
                            )
                        }
                    }
                original.copy(lines = lines).toLrc()
            } else {
                text
            }
        val parsed = LyricsParser.parse(lrc)
        val standard = parsed?.toStandardLrc() ?: lrc
        val rich = if (isTtml) "DMT-TTML:$lrc" else "DMT-LRC:$lrc"
        val ttml = if (isTtml) lrc else null
        c.stop()
        viewModelScope.launch {
            delay(500)
            LyricsEmbedder.embed(context, track, standard, rich, ttml).fold(
                onSuccess = {
                    getLyrics.invalidateLocal(track.path)
                    notify(UiText(R.string.notice_lyrics_embedded))
                    scan(refresh = true)
                    lyrics.load(c.currentMediaItem)
                },
                onFailure = {
                    reduce { it.copy(error = UiText(R.string.error_lyrics_embed)) }
                    scan(refresh = true)
                },
            )
            c.setMediaItems(items, index, position)
            c.prepare()
            if (wasPlaying) c.play()
        }
    }

    private fun notify(message: UiText) {
        noticeJob?.cancel()
        reduce { it.copy(notice = message) }
        noticeJob = viewModelScope.launch {
            delay(2.seconds)
            reduce { it.copy(notice = null) }
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
    }
}
