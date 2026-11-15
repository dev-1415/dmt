package dev.jyotiraditya.dmt.presentation.player

import android.os.Bundle
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dev.jyotiraditya.dmt.core.base.StateStore
import dev.jyotiraditya.dmt.data.repository.PreferencesRepository
import dev.jyotiraditya.dmt.domain.model.DmtSettings
import dev.jyotiraditya.dmt.domain.model.TrackAudioSettings
import dev.jyotiraditya.dmt.playback.PlaybackCommands
import dev.jyotiraditya.dmt.util.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal val SPEED_RANGE = 0.5f..2f

internal const val SPEED_STEP = 0.05f

internal val SLEEP_RANGE = 5..180

internal const val SLEEP_STEP = 5

internal val PITCH_RANGE = -12..12

internal const val PITCH_STEP = 1

/** [speed] snapped to a [SPEED_STEP] and moved [steps] steps, within [SPEED_RANGE]. */
internal fun steppedSpeed(speed: Float, steps: Int = 0): Float {
    // Counts in hundredths so repeated steps don't drift.
    val hundredths = ((speed / SPEED_STEP).roundToInt() + steps) * (SPEED_STEP * 100).roundToInt()
    return (hundredths / 100f).coerceIn(SPEED_RANGE)
}

internal fun steppedSleepMinutes(minutes: Int, steps: Int): Int =
    (minutes + steps * SLEEP_STEP).coerceIn(SLEEP_RANGE)

internal fun steppedPitch(semitones: Int, steps: Int = 0): Int =
    (semitones + steps * PITCH_STEP).coerceIn(PITCH_RANGE)

/** These milliseconds in whole minutes, rounded up. */
internal fun Long.ceilMinutes(): Int = ceil(milliseconds / 1.minutes).toInt()

/** The sleep timer and playback speed, shown through [store] and kept by the service. */
internal class SleepAndSpeed(
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
    private val store: StateStore<DmtState>,
) {

    private var appliedTrack: String? = null

    /** Reads back the service's sleep timer and applies the saved speed to [controller]. */
    suspend fun restore(controller: MediaController) {
        controller.addListener(
            object : Player.Listener {
                // The service pauses playback when the timer ends.
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && store.current.sleepOn) scope.launch { sync(controller) }
                }
            },
        )
        sync(controller)
        val keepPitch = preferences.settings.first().keepPitch
        controller.playbackParameters =
            parameters(preferences.savedSpeed(), keepPitch, store.current.pitchSemitones)
    }

    fun setSpeed(controller: MediaController, speed: Float) {
        val stepped = steppedSpeed(speed)
        if (stepped == steppedSpeed(controller.playbackParameters.speed)) return
        controller.playbackParameters =
            parameters(stepped, store.current.settings.keepPitch, store.current.pitchSemitones)
        scope.launch { preferences.saveSpeed(stepped) }
    }

    fun setPitch(controller: MediaController, semitones: Int) {
        val stepped = steppedPitch(semitones)
        if (stepped == store.current.pitchSemitones) return
        controller.playbackParameters =
            parameters(
                controller.playbackParameters.speed,
                store.current.settings.keepPitch,
                stepped,
            )
        store.reduce { it.copy(pitchSemitones = stepped) }
    }

    /** Applies the file's saved speed and pitch when it starts, if it has any. */
    suspend fun applyForTrack(controller: MediaController, trackId: String?) {
        if (trackId == null || trackId == appliedTrack) return
        appliedTrack = trackId
        val saved = preferences.trackAudioSettings(trackId)
        val speed = saved?.speed ?: preferences.savedSpeed()
        val semitones = saved?.pitchSemitones ?: 0
        controller.playbackParameters =
            parameters(speed, store.current.settings.keepPitch, semitones)
        store.reduce { it.copy(pitchSemitones = semitones) }
    }

    /** Saves the current speed and pitch for [trackId] only. */
    suspend fun saveForTrack(controller: MediaController, trackId: String?) {
        if (trackId == null) return
        preferences.saveTrackAudioSettings(
            trackId,
            TrackAudioSettings(
                speed = steppedSpeed(controller.playbackParameters.speed),
                pitchSemitones = store.current.pitchSemitones,
            ),
        )
    }

    /** Drops [trackId]'s saved speed and pitch, returning it to the global speed. */
    suspend fun clearForTrack(trackId: String?) {
        if (trackId == null) return
        preferences.clearTrackAudioSettings(trackId)
        appliedTrack = null
    }

    fun setSleep(controller: MediaController, minutes: Int) {
        val endAt =
            if (minutes == 0) {
                0L
            } else {
                System.currentTimeMillis() + minutes.minutes.inWholeMilliseconds
            }
        send(controller, endAt, store.current.settings.sleepFinishSong)
    }

    fun sleepAtSongEnd(controller: MediaController) {
        send(controller, System.currentTimeMillis(), finishSong = true)
    }

    /** Applies a changed pitch or finish-the-song setting to what is running. */
    fun onSettings(controller: MediaController, old: DmtSettings, settings: DmtSettings) {
        if (old.keepPitch != settings.keepPitch) {
            controller.playbackParameters =
                parameters(
                    controller.playbackParameters.speed,
                    settings.keepPitch,
                    store.current.pitchSemitones,
                )
        }
        val endAt = store.current.sleepEndAt
        if (old.sleepFinishSong != settings.sleepFinishSong && endAt > System.currentTimeMillis()) {
            send(controller, endAt, settings.sleepFinishSong)
        }
    }

    /** Reads the sleep timer from the service into [store]. */
    suspend fun sync(controller: MediaController) {
        suspendRunCatching {
            val extras =
                controller
                    .sendCustomCommand(PlaybackCommands.SLEEP_GET, Bundle.EMPTY)
                    .await()
                    .extras
            store.reduce {
                it.copy(
                    sleepEndAt = extras.getLong(PlaybackCommands.KEY_END_AT),
                    sleepFinishSong = extras.getBoolean(PlaybackCommands.KEY_FINISH_SONG),
                )
            }
        }
    }

    private fun send(controller: MediaController, endAt: Long, finishSong: Boolean) {
        controller.sendCustomCommand(
            PlaybackCommands.SLEEP_SET,
            Bundle().apply {
                putLong(PlaybackCommands.KEY_END_AT, endAt)
                putBoolean(PlaybackCommands.KEY_FINISH_SONG, finishSong)
            },
        )
        store.reduce { it.copy(sleepEndAt = endAt, sleepFinishSong = finishSong) }
    }

    private fun parameters(speed: Float, keepPitch: Boolean, semitones: Int): PlaybackParameters {
        // A chosen semitone is absolute: changing speed must not shift it further.
        val pitch =
            when {
                semitones != 0 -> 2f.pow(semitones / 12f)
                keepPitch -> 1f
                else -> speed
            }
        return PlaybackParameters(speed, pitch.coerceIn(0.1f, 8f))
    }
}
