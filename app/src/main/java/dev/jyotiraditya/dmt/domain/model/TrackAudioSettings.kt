package dev.jyotiraditya.dmt.domain.model

import kotlinx.serialization.Serializable

/** Speed and pitch saved for one file, applied whenever that file plays. */
@Serializable
data class TrackAudioSettings(
    val speed: Float = 1f,
    val pitchSemitones: Int = 0,
)
