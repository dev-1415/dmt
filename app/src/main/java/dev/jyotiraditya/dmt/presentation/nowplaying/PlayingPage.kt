package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.core.common.asString
import dev.jyotiraditya.dmt.domain.model.AudioQuality
import dev.jyotiraditya.dmt.domain.model.asCredit
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.nowPlayingTrack
import dev.jyotiraditya.dmt.presentation.nowplaying.lyrics.CurrentLyricLine
import dev.jyotiraditya.dmt.ui.components.Artwork
import dev.jyotiraditya.dmt.ui.components.QualityBadge
import dev.jyotiraditya.dmt.ui.components.SeekBar
import dev.jyotiraditya.dmt.ui.components.Tag
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.motion.FADE_MS
import dev.jyotiraditya.dmt.ui.motion.SurfaceSpring
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.LocalCoverColors
import dev.jyotiraditya.dmt.ui.theme.Night
import dev.jyotiraditya.dmt.ui.theme.NightInk
import dev.jyotiraditya.dmt.ui.theme.Numerals
import dev.jyotiraditya.dmt.ui.theme.inkOn
import dev.jyotiraditya.dmt.ui.theme.secondary
import dev.jyotiraditya.dmt.util.asTime

/** Scale of the cover while paused. */
private const val PAUSED_COVER_SCALE = 0.92f

/**
 * Cover, title, seek bar, transport and extras; in a wide window the cover sits beside it instead.
 */
@Composable
internal fun PlayingPage(
    state: DmtState,
    player: Player,
    steps: SeekSteps,
    field: Color,
    dispatch: (DmtAction) -> Unit,
    cover: (@Composable () -> Unit)?,
    lyricsOpen: Boolean,
    active: Boolean,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenDetails: () -> Unit,
    onToggleLyrics: () -> Unit,
) {
    val ink = field.inkOn()
    val dim = ink.secondary(field)
    var volumeAnchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        PlayingLayout(
            cover = cover,
            title = {
                Column {
                    PlayerActions(
                        state = state,
                        player = player,
                        field = field,
                        ink = ink,
                        dispatch = dispatch,
                        onOpenAlbum = onOpenAlbum,
                        onOpenDetails = onOpenDetails,
                        lyricsOpen = lyricsOpen,
                        onToggleLyrics = onToggleLyrics,
                        modifier = Modifier.padding(bottom = 0.dp),
                    )
                    AnimatedVisibility(
                        visible = !lyricsOpen && state.lyrics != null,
                        enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                        exit = shrinkVertically(tween(240)) + fadeOut(tween(240)),
                    ) {
                        CurrentLyricLine(
                            state = state,
                            player = player,
                            background = field,
                            active = active,
                            onClick = onToggleLyrics,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TrackTitle(state, ink, dim, onOpenArtist)
                }
            },
            seek = { SeekRow(state, player, field, ink, dim) },
            transport = {
                Transport(
                    player = player,
                    steps = steps,
                    field = field,
                    ink = ink,
                    onVolume = { volumeAnchor = it },
                )
            },
            extras = { Spacer(modifier = Modifier.height(0.dp)) },
            maxCover = PlayerDimens.MaxCover,
            modifier = Modifier.fillMaxSize().padding(horizontal = Margin),
        )
        val anchor = volumeAnchor
        if (anchor != null) {
            VolumePopup(anchor = anchor, onDismiss = { volumeAnchor = null })
        }
    }
}

/**
 * The largest square cover that fits, shrinking while paused; [onPlaced] reports where it lands.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerCover(
    state: DmtState,
    player: Player,
    steps: SeekSteps,
    hidden: Boolean,
    active: Boolean,
    onPlaced: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = rememberPlayPauseButtonState(player).showPlay
    val scale by
        animateFloatAsState(
            targetValue = if (paused) PAUSED_COVER_SCALE else 1f,
            animationSpec = SurfaceSpring,
            label = "coverScale",
        )
    val coverAlpha by
        animateFloatAsState(
            targetValue = if (hidden) 0f else 1f,
            animationSpec = tween(240),
            label = "coverAlpha",
        )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier =
                Modifier.sizeIn(maxWidth = PlayerDimens.MaxCover, maxHeight = PlayerDimens.MaxCover)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        alpha = coverAlpha
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    }
                    .onGloballyPositioned(onPlaced),
        ) {
            Artwork(
                cover = state.cover,
                modifier = Modifier.fillMaxSize(),
            )
            state.animatedArtUrl?.let { url ->
                AnimatedCover(
                    url = url,
                    playing = rememberIsPlaying(player) && !hidden && active,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            steps.stepMs?.let { step ->
                SeekReadout(player, step, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Where a hold-to-seek of [stepMs] each repeat has reached. */
@OptIn(UnstableApi::class)
@Composable
private fun SeekReadout(player: Player, stepMs: Long, modifier: Modifier = Modifier) {
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000L)
    Text(
        text = (if (stepMs > 0) "+ " else "− ") + progress.currentPositionMs.asTime(),
        style = MaterialTheme.typography.titleLarge,
        color = NightInk,
        modifier = modifier.background(Night).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Title and credit on one line each; a long title is ellipsized instead of scrolling. */
@Composable
private fun TrackTitle(
    state: DmtState,
    ink: Color,
    dim: Color,
    onOpenArtist: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = state.title,
            transitionSpec = {
                fadeIn(
                    tween(
                        FADE_MS,
                        delayMillis = FADE_MS / 3,
                    ),
                ) togetherWith fadeOut(tween(FADE_MS / 2))
            },
            label = "title",
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = ink,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = 28.sp,
                        stepSize = 1.sp,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text =
                state.fault?.asString()
                    ?: listOf(state.artist.asCredit(), state.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(top = PlayerDimens.LineGap)
                    .pressable(onClick = onOpenArtist, label = state.artist),
        )
    }
}

/** The seek bar with elapsed and remaining time, and the track's tags between them. */
@OptIn(UnstableApi::class)
@Composable
private fun SeekRow(state: DmtState, player: Player, field: Color, ink: Color, dim: Color) {
    var width by remember { mutableIntStateOf(0) }
    val hold = rememberSeekHold(player, width)
    val clock = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000L)
    val cue = state.nowPlayingTrack?.cue == true
    val shown = hold.shownMs(clock.currentPositionMs)
    val primary = LocalCoverColors.current.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        SeekBar(
            fraction = { hold.fraction },
            scrubbing = hold.scrubbing,
            ink = primary,
            onScrub = hold::onScrub,
            onSeek = hold::onSeek,
            modifier = Modifier.onSizeChanged { width = it.width },
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = PlayerDimens.LineGap)) {
            Text(
                text = shown.asTime(),
                style = Numerals,
                color = dim,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            TrackTags(
                quality = state.quality,
                cue = cue,
                ink = ink,
                field = field,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = "−" + (state.durationMs - shown).coerceAtLeast(0).asTime(),
                style = Numerals,
                color = dim,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Opacity of the ink tint behind the cue tag. */
private const val CUE_TAG_TINT = 0.18f

/** The quality badge and, for a track cut from a cue sheet, a cue tag beside it. */
@Composable
private fun TrackTags(
    quality: AudioQuality?,
    cue: Boolean,
    ink: Color,
    field: Color,
    modifier: Modifier = Modifier,
) {
    if (quality == null && !cue) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        quality?.let { QualityBadge(it, ink = ink, field = field) }
        if (cue) {
            Tag(
                text = stringResource(R.string.library_cue_tag),
                color = ink,
                field = ink.copy(alpha = CUE_TAG_TINT).compositeOver(field),
            )
        }
    }
}
