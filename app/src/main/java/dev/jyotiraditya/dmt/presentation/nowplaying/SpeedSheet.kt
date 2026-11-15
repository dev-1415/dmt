package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.PITCH_RANGE
import dev.jyotiraditya.dmt.presentation.player.SPEED_RANGE
import dev.jyotiraditya.dmt.presentation.player.steppedPitch
import dev.jyotiraditya.dmt.presentation.player.steppedSpeed
import dev.jyotiraditya.dmt.presentation.settings.ToggleRow
import dev.jyotiraditya.dmt.ui.components.Block
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.FlatSlider
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.Stepper
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.util.asTime
import kotlin.math.roundToInt

private val SPEED_MARKS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Sets the playback speed by step or slider, showing the song's length at that speed. */
@Composable
internal fun SpeedSheet(
    state: DmtState,
    playbackSpeed: Float,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val speed = steppedSpeed(playbackSpeed)
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.speed_and_pitch_title),
            meta =
                stringResource(
                    when {
                        speed < 1f -> R.string.speed_slower
                        speed > 1f -> R.string.speed_faster
                        else -> R.string.speed_normal
                    },
                ),
        )
        Stepper(
            value = stringResource(R.string.speed_value, speed.speedLabel()),
            onStep = { dispatch(DmtAction.SetSpeed(steppedSpeed(speed, it))) },
            lessLabel = stringResource(R.string.speed_slower),
            moreLabel = stringResource(R.string.speed_faster),
            canLess = speed > SPEED_RANGE.start,
            canMore = speed < SPEED_RANGE.endInclusive,
            valueLabel = stringResource(R.string.speed_reset),
            onValueClick = { dispatch(DmtAction.SetSpeed(1f)) },
            modifier = Modifier.padding(horizontal = Margin, vertical = 8.dp),
        )
        if (state.durationMs > 0L) {
            Text(
                text =
                    stringResource(
                        R.string.speed_song_length,
                        state.durationMs.asTime(),
                        (state.durationMs / speed).toLong().asTime(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Margin),
            )
        }
        FlatSlider(
            value = speed,
            onValueChange = { dispatch(DmtAction.SetSpeed(it)) },
            range = SPEED_RANGE,
            label = stringResource(R.string.speed_title),
            marks =
                SPEED_MARKS.map { mark ->
                    mark to stringResource(R.string.speed_value, mark.speedLabel())
                },
            modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
        )
        val pitch = state.pitchSemitones
        Stepper(
            value = pitchLabel(pitch),
            onStep = { dispatch(DmtAction.SetPitch(steppedPitch(pitch, it))) },
            lessLabel = stringResource(R.string.speed_pitch_lower),
            moreLabel = stringResource(R.string.speed_pitch_higher),
            canLess = pitch > PITCH_RANGE.start,
            canMore = pitch < PITCH_RANGE.endInclusive,
            valueLabel = stringResource(R.string.speed_pitch_reset),
            onValueClick = { dispatch(DmtAction.SetPitch(0)) },
            modifier = Modifier.padding(horizontal = Margin, vertical = 8.dp),
        )
        FlatSlider(
            value = pitch.toFloat(),
            onValueChange = { dispatch(DmtAction.SetPitch(it.roundToInt())) },
            range = PITCH_RANGE.start.toFloat()..PITCH_RANGE.endInclusive.toFloat(),
            label = stringResource(R.string.speed_pitch_title),
            marks = listOf(-12f, -6f, 0f, 6f, 12f).map { it to pitchLabel(it.toInt()) },
            modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp),
        ) {
            Block(
                label = stringResource(R.string.speed_save_track),
                onClick = { dispatch(DmtAction.SaveTrackAudio) },
                modifier = Modifier.weight(1f),
            )
            TextLink(
                label = stringResource(R.string.speed_clear_track),
                onClick = { dispatch(DmtAction.ClearTrackAudio) },
            )
        }
        val keepPitch = state.settings.keepPitch
        ToggleRow(
            label = stringResource(R.string.speed_keep_pitch),
            hint = stringResource(R.string.speed_keep_pitch_hint),
            checked = keepPitch,
            onToggle = {
                dispatch(DmtAction.Config(state.settings.copy(keepPitch = !keepPitch)))
            },
        )
    }
}

/** This speed without trailing zeros, such as `1.25` or `2`. */
internal fun Float.speedLabel(): String =
    steppedSpeed(this).toBigDecimal().stripTrailingZeros().toPlainString()

/** `+3 st`, `0 st` or `-2 st`. */
private fun pitchLabel(semitones: Int): String =
    if (semitones > 0) "+$semitones st" else "$semitones st"
