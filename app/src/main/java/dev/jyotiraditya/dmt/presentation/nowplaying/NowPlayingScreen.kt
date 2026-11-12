package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.presentation.nowplaying.details.DetailsPage
import dev.jyotiraditya.dmt.presentation.nowplaying.lyrics.LyricsPanel
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.adaptive.windowShape
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.animateFieldAsState
import dev.jyotiraditya.dmt.ui.theme.Margin

/** The Now Playing section: the player, with the lyrics in the cover's square on request. */
@Composable
internal fun NowPlayingPage(
    state: DmtState,
    player: Player,
    field: Color,
    dispatch: (DmtAction) -> Unit,
    active: Boolean,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var detailsAtTop by remember { mutableStateOf(true) }
    val steps = rememberSeekSteps()
    val wide = windowShape().wide
    val paint by animateFieldAsState(field)
    // Gramophone uses the content-based Material surface directly, without a second darkening.
    val playerBackground = paint
    val playerTop = paint

    if (showDetails) {
        DmtSheet(
            onDismiss = { showDetails = false },
            gesturesEnabled = detailsAtTop,
            showHandle = true,
        ) {
            SheetHeader(title = stringResource(R.string.player_tab_details))
            DetailsPage(
                state = state,
                player = player,
                onAtTopChanged = { detailsAtTop = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            )
        }
    }

    val cover: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            // Kept composed so its animated cover player is reused instead of rebuilt on each
            // lyric toggle; it fades out while the lyrics fade in over it.
            PlayerCover(
                state = state,
                player = player,
                steps = steps,
                hidden = showLyrics,
                active = active,
                onPlaced = {},
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(240)),
            ) {
                LyricsPanel(
                    state = state,
                    player = player,
                    dispatch = dispatch,
                    background = playerBackground,
                    active = active,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(playerTop, playerBackground))),
    ) {
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(0.46f).fillMaxHeight()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxWidth()
                                .padding(start = Margin, end = 8.dp, bottom = Margin),
                    ) {
                        cover()
                    }
                }
                Column(modifier = Modifier.weight(0.54f).fillMaxHeight()) {
                    PlayingPage(
                        state = state,
                        player = player,
                        steps = steps,
                        field = playerBackground,
                        dispatch = dispatch,
                        cover = null,
                        lyricsOpen = showLyrics,
                        active = active,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenDetails = {
                            detailsAtTop = true
                            showDetails = true
                        },
                        onToggleLyrics = { showLyrics = !showLyrics },
                    )
                }
            }
        } else {
            PlayingPage(
                state = state,
                player = player,
                steps = steps,
                field = playerBackground,
                dispatch = dispatch,
                cover = cover,
                lyricsOpen = showLyrics,
                active = active,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onOpenDetails = {
                    detailsAtTop = true
                    showDetails = true
                },
                onToggleLyrics = { showLyrics = !showLyrics },
            )
        }
    }
}
