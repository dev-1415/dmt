package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.Block
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.IconTap
import dev.jyotiraditya.dmt.ui.components.SeekBar
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ground
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.LocalCoverColors
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface
import dev.jyotiraditya.dmt.ui.theme.Surface2
import dev.jyotiraditya.dmt.util.asTime
import dev.jyotiraditya.lyrics.LyricLine
import dev.jyotiraditya.lyrics.LyricWord
import dev.jyotiraditya.lyrics.Lyrics
import java.util.Locale

/**
 * Times and edits lyrics against the audio: a clock beside each line, word start and end clocks
 * for word-synced lyrics, inline text editing behind an explicit pencil, and a player bar at the
 * bottom for listening while timing.
 */
@Composable
internal fun LyricsTimingEditor(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    player: Player?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val original = state.lyrics
    // Keep the edits local to this editor; a background lyrics reload must not discard them.
    var lyrics by remember { mutableStateOf(original) }
    val current = lyrics
    if (current == null) {
        EmptyState(
            title = stringResource(R.string.lyrics_none_title),
            body = stringResource(R.string.lyrics_none_body),
            modifier = modifier.padding(horizontal = Margin),
        )
        return
    }
    val isPlaying = if (player != null) rememberIsPlaying(player) else false
    val position = if (player != null) rememberPlaybackPosition(player, moving = isPlaying) else null
    val hold = if (player != null) rememberSeekHold(player, moving = isPlaying) else null
    val playPause = if (player != null) rememberPlayPauseButtonState(player) else null

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            itemsIndexed(current.lines, key = { index, _ -> index }) { index, line ->
                TimingLineRow(
                    line = line,
                    position = position,
                    onSetStart = { at -> lyrics = lyrics?.updateLine(index) { it.withStart(at) } },
                    onSetWordStart = { word, at ->
                        lyrics = lyrics?.updateLine(index) { it.withWordStart(word, at) }
                    },
                    onSetWordEnd = { word, at ->
                        lyrics = lyrics?.updateLine(index) { it.withWordEnd(word, at) }
                    },
                    onTextChange = { text ->
                        lyrics = lyrics?.updateLine(index) { it.copy(text = text, words = emptyList()) }
                    },
                    onWordTextChange = { word, text ->
                        lyrics = lyrics?.updateLine(index) { it.replaceWord(word, text) }
                    },
                    onAddWord = { text, at, after ->
                        lyrics = lyrics?.updateLine(index) { it.addWord(text, at, after) }
                    },
                    onPlayFrom = { at ->
                        player?.let {
                            it.seekTo(at.coerceAtLeast(0L))
                            it.play()
                        }
                    },
                )
            }
        }
        Block(
            label = stringResource(R.string.lyrics_edit_save),
            onClick = {
                // Read the state at click time, so a word added just before the tap is saved.
                lyrics?.let { dispatch(DmtAction.SaveTimedLyrics(it)) }
                onDismiss()
            },
            modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
        )
        if (player != null && hold != null && playPause != null) {
            TimingPlayerBar(
                player = player,
                hold = hold,
                showPlay = playPause.showPlay,
                onPlayPause = playPause::onClick,
            )
        }
    }
}

@Composable
private fun TimingLineRow(
    line: LyricLine,
    position: State<Long>?,
    onSetStart: (Long) -> Unit,
    onSetWordStart: (Int, Long) -> Unit,
    onSetWordEnd: (Int, Long) -> Unit,
    onTextChange: (String) -> Unit,
    onWordTextChange: (Int, String) -> Unit,
    onAddWord: (String, Long, Int?) -> Unit,
    onPlayFrom: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Int?>(null) }
    var addingAfter by remember { mutableStateOf<Int?>(null) }
    var addingAtEnd by remember { mutableStateOf(false) }
    val hasWords = line.words.isNotEmpty()
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Margin, vertical = 6.dp)
                .background(Surface2, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editingLine) {
                InlineTextField(
                    initial = line.text,
                    onCommit = {
                        onTextChange(it)
                        editingLine = false
                    },
                    onCancel = { editingLine = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink,
                    )
                    Text(
                        text = line.startMs.asTimeOrDash(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                    )
                }
                if (!hasWords) {
                    IconTap(
                        icon = DmtIcons.Edit,
                        description = stringResource(R.string.lyrics_timing_edit_line),
                        onClick = { editingLine = true },
                        ink = Muted,
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                }
                if (hasWords) {
                    IconTap(
                        icon = DmtIcons.ChevronDown,
                        description = stringResource(R.string.lyrics_timing_words),
                        onClick = { expanded = !expanded },
                        ink = Muted,
                        size = 40.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
                IconTap(
                    icon = DmtIcons.Plus,
                    description = stringResource(R.string.lyrics_timing_add_word),
                    onClick = {
                        expanded = true
                        addingAtEnd = true
                    },
                    ink = Muted,
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                IconTap(
                    icon = DmtIcons.Play,
                    description = stringResource(R.string.lyrics_timing_play_line),
                    onClick = { onPlayFrom(line.startMs) },
                    ink = Muted,
                    size = 40.dp,
                    iconSize = 20.dp,
                    enabled = position != null && line.startMs != LyricLine.UNTIMED,
                )
                IconTap(
                    icon = DmtIcons.Clock,
                    description = stringResource(R.string.lyrics_timing_set_line),
                    onClick = { position?.let { onSetStart(it.value) } },
                    ink = LocalCoverColors.current.primary,
                    size = 40.dp,
                    iconSize = 20.dp,
                    enabled = position != null,
                )
            }
        }
        if (expanded) {
            line.words.forEachIndexed { index, word ->
                val start = word.start.coerceIn(0, line.text.length)
                val end = word.end.coerceIn(start, line.text.length)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp)
                            .background(Surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    if (editingWord == index) {
                        InlineTextField(
                            initial = line.text.substring(start, end),
                            onCommit = {
                                onWordTextChange(index, it)
                                editingWord = null
                            },
                            onCancel = { editingWord = null },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        IconTap(
                            icon = DmtIcons.Play,
                            description = stringResource(R.string.lyrics_timing_play_word),
                            onClick = { onPlayFrom(word.startMs) },
                            ink = Muted,
                            size = 32.dp,
                            iconSize = 16.dp,
                            enabled = position != null && word.startMs != LyricLine.UNTIMED,
                        )
                        Text(
                            text = line.text.substring(start, end),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.weight(1f)
                                    .pressable(
                                        onClick = { editingWord = index },
                                        label = stringResource(R.string.lyrics_timing_edit_word),
                                    )
                                    .padding(horizontal = 4.dp),
                        )
                        TimeChip(
                            time = word.startMs,
                            onClick = { position?.let { onSetWordStart(index, it.value) } },
                        )
                        TimeChip(
                            time = word.endMs,
                            onClick = { position?.let { onSetWordEnd(index, it.value) } },
                        )
                        IconTap(
                            icon = DmtIcons.Plus,
                            description = stringResource(R.string.lyrics_timing_add_word),
                            onClick = { addingAfter = index },
                            ink = Muted,
                            size = 32.dp,
                            iconSize = 16.dp,
                        )
                    }
                }
                if (addingAfter == index) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                    ) {
                        InlineTextField(
                            initial = "",
                            onCommit = {
                                onAddWord(it, position?.value ?: word.endMs, index)
                                addingAfter = null
                            },
                            onCancel = { addingAfter = null },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (addingAtEnd) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                ) {
                    InlineTextField(
                        initial = "",
                        onCommit = {
                            onAddWord(it, position?.value ?: line.endMs, null)
                            addingAtEnd = false
                        },
                        onCancel = { addingAtEnd = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                TextLink(
                    label = stringResource(R.string.lyrics_timing_add_word),
                    onClick = { addingAtEnd = true },
                    color = LocalCoverColors.current.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }
    }
}

/** A small tappable clock and time; tapping it sets the word's start or end to the player. */
@Composable
private fun TimeChip(time: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.pressable(onClick = onClick, label = time.asTimeOrDash())
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(DmtIcons.Clock),
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = time.asTimeOrDash(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Muted,
            maxLines = 1,
        )
    }
}

/** A one-line editor that only appears after an explicit pencil tap, with commit and cancel. */
@Composable
private fun InlineTextField(
    initial: String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalCoverColors.current
    val focusRequester = remember { FocusRequester() }
    var value by
        remember(initial) {
            mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
        }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (value.text.isNotBlank()) onCommit(value.text)
                    },
                ),
            modifier =
                Modifier.weight(1f)
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
        )
        IconTap(
            icon = DmtIcons.Check,
            description = stringResource(R.string.lyrics_timing_done),
            onClick = { if (value.text.isNotBlank()) onCommit(value.text) },
            ink = scheme.primary,
            size = 36.dp,
            iconSize = 18.dp,
            enabled = value.text.isNotBlank(),
        )
        IconTap(
            icon = DmtIcons.Close,
            description = stringResource(R.string.lyrics_timing_cancel),
            onClick = onCancel,
            ink = Muted,
            size = 36.dp,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun TimingPlayerBar(
    player: Player,
    hold: SeekHold,
    showPlay: Boolean,
    onPlayPause: () -> Unit,
) {
    val shownSeconds by remember(hold) { derivedStateOf { hold.shownMs() / 1000 } }
    val duration = player.duration.takeIf { it > 0 }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Ground)
                .padding(horizontal = Margin, vertical = 8.dp),
    ) {
        SeekBar(
            fraction = { hold.fraction },
            scrubbing = hold.scrubbing,
            ink = LocalCoverColors.current.primary,
            onScrub = hold::onScrub,
            onSeek = hold::onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTap(
                icon = if (showPlay) DmtIcons.Play else DmtIcons.Pause,
                description =
                    stringResource(if (showPlay) R.string.player_play else R.string.player_pause),
                onClick = onPlayPause,
                ink = Ink,
                size = 48.dp,
                iconSize = 28.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = (shownSeconds * 1000).asTime(),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = duration?.asTime() ?: "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
}

private fun Lyrics.updateLine(index: Int, block: (LyricLine) -> LyricLine): Lyrics {
    val updated = lines.mapIndexed { i, line -> if (i == index) block(line) else line }
    // Timing an unsynced line makes the whole lyric synced.
    return copy(
        lines = updated,
        synced = synced || updated.any { it.startMs != LyricLine.UNTIMED },
    )
}

/** Moves a line to [newStart], carrying its words and its length with it. */
private fun LyricLine.withStart(newStart: Long): LyricLine {
    val delta = if (startMs == LyricLine.UNTIMED) 0L else newStart - startMs
    return copy(
        startMs = newStart,
        endMs =
            if (endMs == LyricLine.UNTIMED) endMs else (endMs + delta).coerceAtLeast(newStart),
        words =
            words.map {
                it.copy(startMs = it.startMs + delta, endMs = it.endMs + delta)
            },
    )
}

private fun LyricLine.withWordStart(index: Int, at: Long): LyricLine {
    val words =
        words.mapIndexed { i, word ->
            if (i == index) word.copy(startMs = at, endMs = word.endMs.coerceAtLeast(at)) else word
        }
    return copy(words = words).withWordBounds()
}

private fun LyricLine.withWordEnd(index: Int, at: Long): LyricLine {
    val words =
        words.mapIndexed { i, word ->
            if (i == index) word.copy(endMs = at, startMs = word.startMs.coerceAtMost(at)) else word
        }
    return copy(words = words).withWordBounds()
}

private fun LyricLine.withWordBounds(): LyricLine {
    if (words.isEmpty()) return this
    return copy(
        startMs = words.minOf { it.startMs },
        endMs = words.maxOf { it.endMs },
    )
}

/** How long a newly added word lasts until its end is timed. */
private const val DEFAULT_WORD_MS = 500L

/**
 * Inserts a word after the word at [after], or at the end when [after] is null, timed at [atMs]
 * and lasting [DEFAULT_WORD_MS]. The words after the insertion move right with the text.
 */
internal fun LyricLine.addWord(text: String, atMs: Long, after: Int?): LyricLine {
    val word = text.trim()
    if (word.isEmpty()) return this
    val anchor = after?.let { words.getOrNull(it) }
    val insertAt = anchor?.end?.coerceIn(0, this.text.length) ?: this.text.length
    val needsSpace = insertAt > 0 && !this.text[insertAt - 1].isWhitespace()
    val prefix = if (needsSpace) " " else ""
    val newText = this.text.substring(0, insertAt) + prefix + word + this.text.substring(insertAt)
    val start = insertAt + prefix.length
    val end = start + word.length
    val delta = prefix.length + word.length
    val moved =
        words.map { current ->
            if (current.start >= insertAt) {
                current.copy(start = current.start + delta, end = current.end + delta)
            } else {
                current
            }
        }
    val anchorEnd = anchor?.endMs ?: words.lastOrNull()?.endMs ?: startMs
    val wordStart = maxOf(atMs, anchorEnd).coerceAtLeast(0L)
    val wordEnd = wordStart + DEFAULT_WORD_MS
    return copy(
        text = newText,
        words =
            (moved + LyricWord(wordStart, wordEnd, start, end, background = false))
                .sortedBy { it.start },
        endMs = maxOf(endMs, wordEnd),
    )
}

/** Replaces one word's text and shifts the offsets of the words after it. */
private fun LyricLine.replaceWord(index: Int, newText: String): LyricLine {
    val word = words.getOrNull(index) ?: return this
    val start = word.start.coerceIn(0, text.length)
    val end = word.end.coerceIn(start, text.length)
    val updatedText = text.substring(0, start) + newText + text.substring(end)
    val delta = newText.length - (end - start)
    val updatedWords =
        words.mapIndexed { i, current ->
            when {
                i == index -> current.copy(end = start + newText.length)
                current.start >= end ->
                    current.copy(start = current.start + delta, end = current.end + delta)
                else -> current
            }
        }
    return copy(text = updatedText, words = updatedWords)
}

private fun Long.asTimeOrDash(): String =
    if (this == LyricLine.UNTIMED) "--:--" else asTimeWithMillis()

/** `mm:ss.SSS`, so the editor shows the sub-second part the LRC keeps. */
private fun Long.asTimeWithMillis(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = this % 1000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}
