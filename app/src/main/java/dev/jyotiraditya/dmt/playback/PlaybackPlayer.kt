package dev.jyotiraditya.dmt.playback

import android.content.Context
import android.media.AudioTrack
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

private const val SEEK_HOLD_STEP_MS = 5_000L

/**
 * Opus and Vorbis are decoded in software on most phones, and the platform Codec2 decoders are
 * expensive. Report them as unavailable so ExoPlayer falls through to the bundled FFmpeg
 * renderer, while MP3/AAC keep the platform decoder and audio offload.
 */
private val preferFfmpegForOpus =
    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        if (mimeType == MimeTypes.AUDIO_OPUS || mimeType == MimeTypes.AUDIO_VORBIS) {
            emptyList()
        } else {
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        }
    }

/**
 * A deeper PCM buffer and the power-saving AudioTrack path. Offload and passthrough keep their
 * default buffers so the DSP path is unchanged.
 */
@OptIn(UnstableApi::class)
private class PowerSavingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParameters: Boolean,
    ): AudioSink {
        val output =
            AudioTrackAudioOutputProvider.Builder(context)
                .setAudioTrackBufferSizeProvider { minBuffer, _, outputMode, pcmFrameSize, sampleRate, _, _ ->
                    if (outputMode == DefaultAudioSink.OUTPUT_MODE_PCM && pcmFrameSize > 0) {
                        // About 250 ms of PCM, rounded to a whole frame.
                        val target = sampleRate.toLong() * pcmFrameSize / 4
                        maxOf(minBuffer.toLong(), target / pcmFrameSize * pcmFrameSize).toInt()
                    } else {
                        minBuffer
                    }
                }
                .setAudioTrackBuilderModifier { builder, config ->
                    if (!config.isOffload) {
                        runCatching {
                            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                        }
                    }
                }
                .build()
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
            .setAudioOutputProvider(output)
            .build()
    }
}

/** The music player: bundled decoders first, offload where supported, focus and noisy handled. */
@OptIn(UnstableApi::class)
internal fun Context.buildMusicPlayer(dataSourceFactory: DataSource.Factory): ExoPlayer {
    val renderers =
        PowerSavingRenderersFactory(this)
            // Platform decoders are more power-efficient; extensions only fill the gaps.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(preferFfmpegForOpus)
            .setEnableDecoderFallback(true)
    val offload =
        AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            // Opus files carry pre-skip, and many devices do not report gapless offload for
            // them. Requiring gapless would force those tracks back onto the CPU decoder.
            .setIsGaplessSupportRequired(false)
            .build()
    val trackSelector =
        DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setAudioOffloadPreferences(offload).build()
        }
    val attributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    val handleAudioFocus = true
    return ExoPlayer.Builder(this, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(attributes, handleAudioFocus)
        .setHandleAudioBecomingNoisy(true)
        // Local files only need a partial wake lock; remote tracks switch this to network below.
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setTrackSelector(trackSelector)
        .setSeekBackIncrementMs(SEEK_HOLD_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_HOLD_STEP_MS)
        .build()
}
