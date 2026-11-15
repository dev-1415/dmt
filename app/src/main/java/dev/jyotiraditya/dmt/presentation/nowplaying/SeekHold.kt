package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

/** How long the hold-to-seek readout stays after the last step. */
private const val STEP_LINGER_MS = 700L

/** The step of the hold-to-seek in progress, kept for [STEP_LINGER_MS] after the hold ends. */
@Stable
internal class SeekSteps {
    var stepMs by mutableStateOf<Long?>(null)
        private set

    private val steps =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun onStep(stepMs: Long) {
        this.stepMs = stepMs
        steps.tryEmit(Unit)
    }

    suspend fun clearAfterSteps() {
        steps.collectLatest {
            delay(STEP_LINGER_MS)
            stepMs = null
        }
    }
}

@Composable
internal fun rememberSeekSteps(): SeekSteps {
    val steps = remember { SeekSteps() }
    LaunchedEffect(steps) { steps.clearAfterSteps() }
    return steps
}

/** A seek bar's position: the dragged one while scrubbing, the player's otherwise. */
@Stable
internal class SeekHold(
    private val player: Player,
    private val position: State<Long>,
) {
    var scrub by mutableStateOf<Float?>(null)
        private set

    val scrubbing: Boolean
        get() = scrub != null

    val fraction: Float
        get() {
            // Read the position even before the duration is known, so the bar's snapshot
            // flow keeps tracking it and starts moving as soon as the duration arrives.
            val position = position.value
            val duration = durationMs()
            if (duration <= 0) return scrub ?: 0f
            return scrub ?: (position.toFloat() / duration).coerceIn(0f, 1f)
        }

    /** The position now, or the time the drag would seek to while scrubbing. */
    fun shownMs(): Long = scrub?.let(::progressToPosition) ?: position.value

    fun onScrub(fraction: Float?) {
        scrub = fraction
    }

    fun onSeek(fraction: Float) {
        val duration = durationMs()
        if (duration > 0 && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo((fraction * duration).toLong())
        }
        scrub = null
    }

    private fun progressToPosition(progress: Float): Long {
        val duration = durationMs()
        return if (duration > 0) (progress * duration).toLong() else 0L
    }

    private fun durationMs(): Long = player.duration.takeIf { it > 0 } ?: 0L
}

/** A [SeekHold] that polls only while [moving], so an off-screen bar costs nothing. */
@Composable
internal fun rememberSeekHold(player: Player, moving: Boolean): SeekHold {
    val position = rememberPlaybackPosition(player, moving)
    return remember(player, position) { SeekHold(player, position) }
}
