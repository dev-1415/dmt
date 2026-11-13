package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.data.lyrics.LyricsEmbedder
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.Block
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.FlatSlider
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.Stepper
import dev.jyotiraditya.dmt.ui.components.pressable
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
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    DmtSheet(onDismiss = onDismiss) {
        val scheme = LocalCoverColors.current
        SheetHeader(
            title = stringResource(R.string.lyrics_edit_title),
            meta = stringResource(R.string.lyrics_edit_hint),
            titleColor = scheme.primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp),
        ) {
            TabLabel(stringResource(R.string.lyrics_offset_tab), tab == 0) { tab = 0 }
            TabLabel(stringResource(R.string.lyrics_edit_tab), tab == 1) { tab = 1 }
            TabLabel(stringResource(R.string.lyrics_timing_tab), tab == 2) { tab = 2 }
        }
        when (tab) {
            0 -> OffsetTab(state, dispatch)
            1 -> EditTab(state, dispatch, onDismiss)
            else -> TimingTab(state, dispatch, onDismiss)
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
                val shiftedTtml =
                    lyrics.raw
                        ?.takeIf { it.trimStart().startsWith("<") }
                        ?.let { LyricsEmbedder.shiftTtmlTimes(it, offset.toLong()) }
                dispatch(
                    DmtAction.SaveLyrics(
                        shifted.toLrc(),
                        raw = true,
                        ttml = shiftedTtml,
                    )
                )
                dispatch(DmtAction.Config(state.settings.copy(lyricsOffsetMs = 0)))
            }
        },
        enabled = lyrics != null && offset != 0,
        modifier = Modifier.padding(horizontal = Margin, vertical = 8.dp),
    )
}

@Composable
private fun EditTab(state: DmtState, dispatch: (DmtAction) -> Unit, onDismiss: () -> Unit) {
    val original = state.lyrics
    var text by
        remember(original) {
            mutableStateOf(original?.lines?.joinToString("\n") { it.text }.orEmpty())
        }
    LyricsEditor(
        text = text,
        onTextChange = { text = it },
        onSave = {
            dispatch(DmtAction.SaveLyrics(text))
            onDismiss()
        },
    )
}

@Composable
private fun TimingTab(state: DmtState, dispatch: (DmtAction) -> Unit, onDismiss: () -> Unit) {
    val original = state.lyrics
    val isTtml = original?.raw?.trimStart()?.startsWith("<") == true
    var text by
        remember(original) {
            mutableStateOf(if (isTtml) original?.raw.orEmpty() else original?.toLrc().orEmpty())
        }
    LyricsEditor(
        text = text,
        format = if (isTtml) "TTML" else "LRC",
        onTextChange = { text = it },
        onSave = {
            dispatch(DmtAction.SaveLyrics(text, raw = true))
            onDismiss()
        },
    )
}

@Composable
private fun LyricsEditor(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    format: String? = null,
) {
    val scheme = LocalCoverColors.current
    val focusRequester = remember { FocusRequester() }
    var value by
        remember {
            mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
        }
    // Put the cursor in the field as soon as the tab opens, so it is visible without a tap.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Margin)) {
        if (format != null) {
            Text(
                text = format,
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onTextChange(it.text)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(scheme.primary),
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp)
                    .background(scheme.surfaceContainer)
                    .padding(16.dp)
                    .focusRequester(focusRequester),
        )
        Block(
            label = stringResource(R.string.lyrics_edit_save),
            onClick = onSave,
            enabled = value.text.isNotBlank(),
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

/** `+0.25 s`, `-0.25 s` or `0.00 s`. */
private fun offsetLabel(ms: Int): String {
    val sign = if (ms > 0) "+" else ""
    return String.format(Locale.ROOT, "%s%.2f s", sign, ms / 1000f)
}
