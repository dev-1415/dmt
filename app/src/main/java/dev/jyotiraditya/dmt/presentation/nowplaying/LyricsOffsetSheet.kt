package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.presentation.main.BackBar
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.Block
import dev.jyotiraditya.dmt.ui.components.FlatSlider
import dev.jyotiraditya.dmt.ui.components.Stepper
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.theme.Ground
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.LocalCoverColors
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.lyrics.shiftBy
import dev.jyotiraditya.lyrics.toLrc
import java.util.Locale

private val OFFSET_RANGE = -10_000f..10_000f
private const val OFFSET_STEP_MS = 50

/** Shifts the lyrics against the audio, or edits their text or timing. */
@Composable
internal fun LyricsOffsetSheet(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    player: Player?,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ground) {
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                BackBar(
                    label = stringResource(R.string.lyrics_edit_title),
                    field = Ground,
                    onBack = onDismiss,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp),
                ) {
                    TabLabel(stringResource(R.string.lyrics_offset_tab), tab == 0) { tab = 0 }
                    TabLabel(stringResource(R.string.lyrics_timing_tab), tab == 1) { tab = 1 }
                    TabLabel(stringResource(R.string.lyrics_text_tab), tab == 2) { tab = 2 }
                }
                when (tab) {
                    0 -> OffsetTab(state, dispatch)
                    1 ->
                        LyricsTimingEditor(
                            state = state,
                            dispatch = dispatch,
                            player = player,
                            onDismiss = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    else -> TextTab(state, dispatch, onDismiss, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = LocalCoverColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) scheme.primary else Muted,
        modifier = Modifier.pressable(onClick = onClick, label = text).padding(vertical = 8.dp),
    )
}

@Composable
private fun OffsetTab(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val offset = state.settings.lyricsOffsetMs
    Stepper(
        value = offsetLabel(offset),
        onStep = { step ->
            val next =
                (offset + step * OFFSET_STEP_MS)
                    .coerceIn(OFFSET_RANGE.start.toInt(), OFFSET_RANGE.endInclusive.toInt())
            dispatch(DmtAction.Config(state.settings.copy(lyricsOffsetMs = next)))
        },
        lessLabel = stringResource(R.string.lyrics_offset_earlier),
        moreLabel = stringResource(R.string.lyrics_offset_later),
        canLess = offset > OFFSET_RANGE.start,
        canMore = offset < OFFSET_RANGE.endInclusive,
        valueLabel = stringResource(R.string.lyrics_offset_reset),
        onValueClick = { dispatch(DmtAction.Config(state.settings.copy(lyricsOffsetMs = 0))) },
        modifier = Modifier.padding(horizontal = Margin, vertical = 8.dp),
    )
    FlatSlider(
        value = offset.toFloat(),
        onValueChange = {
            val next = (it / OFFSET_STEP_MS).toInt() * OFFSET_STEP_MS
            dispatch(DmtAction.Config(state.settings.copy(lyricsOffsetMs = next)))
        },
        range = OFFSET_RANGE,
        label = stringResource(R.string.lyrics_offset_title),
        modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
    )
    val lyrics = state.lyrics
    Block(
        label = stringResource(R.string.lyrics_offset_apply),
        onClick = {
            val shifted = lyrics?.shiftBy(offset.toLong())
            if (shifted != null) {
                dispatch(DmtAction.SaveLyrics(shifted.toLrc(), raw = true))
                dispatch(DmtAction.Config(state.settings.copy(lyricsOffsetMs = 0)))
            }
        },
        enabled = lyrics != null && offset != 0,
        modifier = Modifier.padding(horizontal = Margin, vertical = 8.dp),
    )
}

/** The whole lyrics as plain text, with copy and paste, for a file that has none yet. */
@Composable
private fun TextTab(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val original = state.lyrics
    val scheme = LocalCoverColors.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val initial =
        original?.raw?.takeIf { it.isNotBlank() }
            ?: original?.toLrc().orEmpty()
    var text by rememberSaveable(original) { mutableStateOf(initial) }
    var value by remember { mutableStateOf(TextFieldValue(text, selection = TextRange(text.length))) }
    val format =
        when {
            text.trimStart().startsWith("<") -> "TTML"
            text.lineSequence().any { it.trimStart().startsWith("[") } -> "LRC"
            else -> "Text"
        }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Margin)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            TextLink(
                label = stringResource(R.string.lyrics_text_copy),
                onClick = { clipboard.setText(AnnotatedString(text)) },
            )
            TextLink(
                label = stringResource(R.string.lyrics_text_paste),
                onClick = {
                    clipboard.getText()?.text?.let {
                        text = it
                        value = TextFieldValue(it, selection = TextRange(it.length))
                    }
                },
            )
            TextLink(
                label = stringResource(R.string.lyrics_text_clear),
                onClick = {
                    text = ""
                    value = TextFieldValue("")
                },
            )
        }
        Text(
            text = format,
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                text = it.text
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(scheme.primary),
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .background(scheme.surfaceContainer)
                    .padding(16.dp)
                    .focusRequester(focusRequester),
        )
        Block(
            label = stringResource(R.string.lyrics_edit_save),
            onClick = {
                dispatch(DmtAction.SaveLyrics(text, raw = true))
                onDismiss()
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

/** `+0.25 s`, `-0.25 s` or `0.00 s`. */
private fun offsetLabel(ms: Int): String {
    val sign = if (ms > 0) "+" else ""
    return String.format(Locale.ROOT, "%s%.2f s", sign, ms / 1000f)
}
