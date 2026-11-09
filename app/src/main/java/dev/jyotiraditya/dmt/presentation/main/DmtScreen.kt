package dev.jyotiraditya.dmt.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.LyricsSource
import dev.jyotiraditya.dmt.domain.model.TrackSource
import dev.jyotiraditya.dmt.domain.repository.ArtworkRepository
import dev.jyotiraditya.dmt.presentation.home.ListenPane
import dev.jyotiraditya.dmt.presentation.library.BackLink
import dev.jyotiraditya.dmt.presentation.library.LibraryPage
import dev.jyotiraditya.dmt.presentation.library.libraryBack
import dev.jyotiraditya.dmt.presentation.nowplaying.LocalPlayer
import dev.jyotiraditya.dmt.presentation.nowplaying.LyricsSourceSheet
import dev.jyotiraditya.dmt.presentation.nowplaying.NowPlayingPage
import dev.jyotiraditya.dmt.presentation.nowplaying.QueuePage
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.presentation.player.nowPlayingTrack
import dev.jyotiraditya.dmt.presentation.settings.SettingsPage
import dev.jyotiraditya.dmt.presentation.settings.settingsBack
import dev.jyotiraditya.dmt.ui.adaptive.windowShape
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.LocalArtwork
import dev.jyotiraditya.dmt.ui.components.Notice
import dev.jyotiraditya.dmt.ui.motion.FADE_MS
import dev.jyotiraditya.dmt.ui.theme.Ground
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.darkenedForPlayer
import kotlinx.coroutines.launch

/** Bottom padding that keeps the last row of a list clear of what sits below it. */
val LocalContentBottom = compositionLocalOf { 0.dp }

/**
 * The app's main screen: the player, queue and library sections as swipeable tabs, with Settings
 * behind the gear.
 */
@Composable
fun DmtScreen(
    state: DmtState,
    player: Player?,
    field: Color,
    dispatch: (DmtAction) -> Unit,
    artwork: ArtworkRepository,
) {
    val visiblePlaces =
        remember(state.settings.visibleLibraryTabs) {
            Place.entries.filter { place ->
                place.libraryTab == null || place.libraryTab in state.settings.visibleLibraryTabs
            }
        }
    val pager = rememberPagerState(pageCount = { visiblePlaces.size })
    val place = visiblePlaces.getOrNull(pager.currentPage) ?: visiblePlaces.first()
    val scope = rememberCoroutineScope()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsReturn by rememberSaveable { mutableStateOf(Place.QUEUE.ordinal) }
    val selected = if (settingsOpen) null else pager.currentPage + pager.currentPageOffsetFraction

    fun go(next: Place) {
        val target =
            if (next in visiblePlaces) {
                next
            } else {
                visiblePlaces.firstOrNull { it.libraryTab != null } ?: Place.QUEUE
            }
        target.view?.let { dispatch(DmtAction.Show(it)) }
        val index = visiblePlaces.indexOf(target)
        if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
    }

    fun openSettings(view: DmtView = DmtView.SETTINGS) {
        settingsReturn = place.ordinal
        settingsOpen = true
        dispatch(DmtAction.Show(view))
    }

    fun closeSettings() {
        settingsOpen = false
    }

    val returnPlace =
        Place.entries.getOrNull(settingsReturn)?.takeIf { it in visiblePlaces }
            ?: visiblePlaces.first()

    val back: BackLink? =
        when {
            settingsOpen ->
                settingsBack(state)
                    ?: BackLink(stringResource(returnPlace.label), emptyList())

            place.libraryTab != null -> libraryBack(state, place.view ?: DmtView.FOLDERS)

            else -> null
        }

    fun goBack() {
        when {
            settingsOpen && back != null && back.actions.isNotEmpty() ->
                back.actions.forEach(dispatch)

            settingsOpen -> closeSettings()

            back != null -> back.actions.forEach(dispatch)

            state.query.isNotEmpty() -> dispatch(DmtAction.Query(""))

            else -> go(Place.QUEUE)
        }
    }

    BackHandler(
        enabled = settingsOpen || back != null || state.query.isNotEmpty() || place != Place.QUEUE,
    ) {
        goBack()
    }

    // Keeps the library view in step with the section the pager settles on.
    LaunchedEffect(pager, visiblePlaces) {
        snapshotFlow { pager.settledPage }.collect { page ->
            visiblePlaces.getOrNull(page)?.view?.let { dispatch(DmtAction.Show(it)) }
        }
    }

    // Keeps the pager on the section the user came from when the visible tabs change.
    LaunchedEffect(visiblePlaces) {
        val target =
            Place.entries.getOrNull(settingsReturn)?.takeIf { it in visiblePlaces }
                ?: visiblePlaces.first()
        val index = visiblePlaces.indexOf(target)
        if (pager.currentPage != index) pager.scrollToPage(index)
    }

    // Follows library views opened from a track menu, such as Go to album.
    LaunchedEffect(state.view) {
        val target = Place.entries.find { it.view == state.view }
        if (target != null) {
            if (target in visiblePlaces) {
                val index = visiblePlaces.indexOf(target)
                if (pager.targetPage != index) pager.animateScrollToPage(index)
            } else {
                val currentView = place.view
                if (currentView != null && currentView != state.view) {
                    dispatch(DmtAction.Show(currentView))
                }
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val openArtist = {
        val track = state.nowPlayingTrack
        val artist =
            track?.let {
                state.library.artist(it.artist)
                    ?: state.library.artistOf(it)
                    ?: state.artists.find { artist -> it.artist.startsWith(artist.name) }
            }
        if (artist != null) {
            go(Place.ARTISTS)
            dispatch(DmtAction.OpenAlbum(null))
            dispatch(DmtAction.OpenArtist(artist.name))
        }
    }

    val openAlbum = {
        val track = state.nowPlayingTrack
        val album = track?.let { state.library.albumOf(it) }
        if (album != null) {
            go(Place.ALBUMS)
            dispatch(DmtAction.OpenAlbum(album.id))
        }
    }

    CompositionLocalProvider(LocalArtwork provides artwork, LocalPlayer provides player) {
        Column(modifier = Modifier.fillMaxSize()) {
            val rail = windowShape().rail
            val openPlace = { next: Place ->
                if (settingsOpen) closeSettings()
                go(next)
            }
            if (!rail) {
                if (back != null) {
                    BackBar(label = back.label, field = field, onBack = ::goBack)
                } else {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }
            }
            Row(modifier = Modifier.weight(1f)) {
                if (rail) {
                    SideRail(
                        selected = selected,
                        places = visiblePlaces,
                        settingsOpen = settingsOpen,
                        onPlace = openPlace,
                        onSettings = {
                            if (settingsOpen) closeSettings() else openSettings()
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (rail) {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        AnimatedContent(
                            targetState = back?.label,
                            transitionSpec = {
                                fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
                            },
                            label = "railBack",
                        ) { label ->
                            if (label != null) BackRow(label = label, onBack = ::goBack)
                        }
                    }
                    CompositionLocalProvider(LocalContentBottom provides 24.dp) {
                        if (settingsOpen) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SettingsPage(state = state, cover = field, dispatch = dispatch)
                            }
                        } else {
                            HorizontalPager(
                                state = pager,
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                val active = pager.currentPage == page
                                when (val shown = visiblePlaces[page]) {
                                    Place.QUEUE ->
                                        QueuePage(
                                            state = state,
                                            player = player,
                                            tint = field.darkenedForPlayer(),
                                            dispatch = dispatch,
                                        )

                                    Place.NOW_PLAYING ->
                                        if (player != null && state.nowPlayingId != null) {
                                            NowPlayingPage(
                                                state = state,
                                                player = player,
                                                field = field,
                                                dispatch = dispatch,
                                                active = active,
                                                onOpenArtist = openArtist,
                                                onOpenAlbum = openAlbum,
                                            )
                                        } else {
                                            EmptyState(
                                                title =
                                                    stringResource(
                                                        R.string.queue_nothing_playing_title,
                                                    ),
                                                body = stringResource(R.string.now_playing_empty_body),
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .padding(horizontal = Margin),
                                            )
                                        }

                                    Place.LISTEN ->
                                        ListenPane(
                                            state = state,
                                            dispatch = dispatch,
                                            onOpenAlbum = { name ->
                                                go(Place.ALBUMS)
                                                dispatch(DmtAction.OpenAlbum(name))
                                            },
                                            onOpenArtist = { name ->
                                                go(Place.ARTISTS)
                                                dispatch(DmtAction.OpenAlbum(null))
                                                dispatch(DmtAction.OpenArtist(name))
                                            },
                                            onOpenLibrary = { go(Place.FOLDERS) },
                                            onOpenSources = {
                                                openSettings(DmtView.SOURCES)
                                            },
                                        )

                                    else ->
                                        LibraryPage(
                                            state = state,
                                            dispatch = dispatch,
                                            view = shown.view ?: DmtView.FOLDERS,
                                            tint = field.darkenedForPlayer(),
                                        )
                                }
                            }
                        }
                    }
                }
            }
            Notice(error = state.error, notice = state.notice)
            if (rail) {
                Spacer(modifier = Modifier.height(navBottom))
            } else {
                BottomBar(
                    selected = selected,
                    places = visiblePlaces,
                    settingsOpen = settingsOpen,
                    field = field,
                    onPlace = openPlace,
                    onSettings = {
                        if (settingsOpen) closeSettings() else openSettings()
                    },
                )
            }
        }

        if (state.lyricsSourcesOpen) {
            val track = state.nowPlayingTrack
            LyricsSourceSheet(
                trackTitle = state.title,
                current = state.lyricsShowingFrom ?: state.lyricsSource,
                sources = state.lyricsSources,
                canEmbed =
                    state.lyrics != null &&
                        track?.source == TrackSource.LOCAL &&
                        state.lyricsShowingFrom != LyricsSource.EMBEDDED,
                onPick = { dispatch(DmtAction.SetLyricsSource(it)) },
                onEmbed = { dispatch(DmtAction.EmbedLyrics) },
                onDismiss = { dispatch(DmtAction.CloseLyricsSources) },
            )
        }
    }
}
