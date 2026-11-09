package dev.jyotiraditya.dmt.presentation.nowplaying.lyrics

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.LyricsSource
import dev.jyotiraditya.dmt.presentation.nowplaying.rememberIsPlaying
import dev.jyotiraditya.dmt.presentation.nowplaying.rememberPlaybackPosition
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.theme.LyricType
import dev.jyotiraditya.dmt.ui.theme.NightInk

/** How far ahead of the reported position words are lit, to cover display latency. */
private const val LEAD_MS = 80L

/** The lyrics in the cover's square, drawn over the player tint. */
@OptIn(UnstableApi::class)
@Composable
internal fun LyricsPanel(
    state: DmtState,
    player: Player,
    dispatch: (DmtAction) -> Unit,
    background: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val lyrics = state.lyrics
    val bySinger = state.settings.singerColors
    val colors =
        remember(bySinger, background) {
            LyricColors.on(ink = NightInk, background = background, bySinger)
        }
    val playPause = rememberPlayPauseButtonState(player)

    BoxWithConstraints(modifier = modifier) {
        val bottom = maxHeight * 0.5f
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (lyrics != null) {
                LyricsView(
                    lyrics = lyrics,
                    trackId = state.nowPlayingId,
                    position =
                        rememberPlaybackPosition(
                            player,
                            moving = rememberIsPlaying(player) && lyrics.synced && active,
                            leadMs = LEAD_MS,
                        ),
                    durationMs = state.durationMs,
                    romanized = state.settings.romanizedLyrics,
                    colors = colors,
                    onSeek = {
                        dispatch(DmtAction.Seek(it))
                        if (playPause.showPlay) playPause.onClick()
                    },
                    contentPadding = PaddingValues(top = 24.dp, bottom = bottom),
                    modifier = Modifier.weight(1f),
                )
            } else {
                EmptyState(
                    title =
                        stringResource(
                            if (state.lyricsFetching) {
                                R.string.lyrics_fetching
                            } else {
                                R.string.lyrics_none_title
                            },
                        ),
                    body = stringResource(R.string.lyrics_none_body),
                    ink = NightInk,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The line being sung, shown below the cover and action buttons. */
@OptIn(UnstableApi::class)
@Composable
internal fun CurrentLyricLine(
    state: DmtState,
    player: Player,
    background: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val lyrics = state.lyrics
    val tap =
        if (onClick != null && lyrics != null) {
            Modifier.pressable(
                onClick = onClick,
                sink = 0.99f,
                label = stringResource(R.string.player_lyrics),
            )
        } else {
            Modifier
        }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.height(48.dp).then(tap),
    ) {
        if (lyrics != null) {
            val bySinger = state.settings.singerColors
            val colors = remember(bySinger, background) {
                LyricColors.on(ink = NightInk, background = background, bySinger)
            }
            val position =
                rememberPlaybackPosition(
                    player,
                    moving = rememberIsPlaying(player) && lyrics.synced && active,
                    leadMs = LEAD_MS,
                )
            // Read the position through a derived state so a new frame only redraws the sweep
            // instead of recomposing this line.
            val lineIndex by
                remember(lyrics) {
                    derivedStateOf {
                        if (lyrics.synced) lyrics.lines.indexAt(position.value) else -1
                    }
                }
            val line =
                if (lyrics.synced) {
                    lyrics.lines.getOrNull(lineIndex)
                } else {
                    lyrics.lines.firstOrNull()
                }
            val text = line?.text?.takeIf { it.isNotBlank() }
            if (text != null) {
                val style =
                    LyricType.copy(
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Start,
                    )
                if (lyrics.synced && line.words.isNotEmpty()) {
                    SweepText(
                        text = text,
                        words = line.words,
                        position = position,
                        style = style,
                        sung = colors.singer(line),
                        unsung = colors.unsung,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    BasicText(
                        text = text,
                        style = style.copy(color = colors.lead),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@get:StringRes
internal val LyricsSource.label: Int
    get() =
        when (this) {
            LyricsSource.EMBEDDED -> R.string.lyrics_source_embedded
            LyricsSource.LOCAL -> R.string.lyrics_source_local
            LyricsSource.LRCLIB -> R.string.lyrics_source_lrclib
            LyricsSource.LRC_RED -> R.string.lyrics_source_lrc_red
        }
