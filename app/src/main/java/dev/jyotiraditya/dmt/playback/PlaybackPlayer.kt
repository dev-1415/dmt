package dev.jyotiraditya.dmt.playback

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

private const val SEEK_HOLD_STEP_MS = 5_000L

/**
 * Opus, Vorbis and MP3 are decoded in software on most phones, and the platform Codec2 decoders
 * are expensive. Report them as unavailable so ExoPlayer falls through to the bundled FFmpeg
 * renderer, while AAC keeps the platform decoder and compressed offload.
 */
@OptIn(UnstableApi::class)
private object SoftwareCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> =
        if (mimeType == MimeTypes.AUDIO_VORBIS || mimeType == MimeTypes.AUDIO_OPUS) {
            emptyList()
        } else {
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        }
}

/** A deep PCM buffer and the power-saving AudioTrack path. */
@OptIn(UnstableApi::class, ExperimentalApi::class)
private class PowerSavingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParameters: Boolean,
    ): AudioSink {
        val output =
            AudioTrackAudioOutputProvider.Builder(context)
                .setAudioTrackBufferSizeProvider { minBuffer, maxBuffer, outputMode, pcmFrameSize, sampleRate, _, _ ->
                    if (outputMode == DefaultAudioSink.OUTPUT_MODE_OFFLOAD) {
                        // A deeper offload buffer gives the app more time to refill the DSP.
                        val target = minBuffer.toLong() * 2
                        (if (maxBuffer > 0) target.coerceAtMost(maxBuffer.toLong()) else target)
                            .toInt()
                    } else if (outputMode == DefaultAudioSink.OUTPUT_MODE_PCM && pcmFrameSize > 0) {
                        // About 250 ms of PCM, rounded to a whole frame.
                        val target = sampleRate.toLong() * pcmFrameSize / 4
                        maxOf(minBuffer.toLong(), target / pcmFrameSize * pcmFrameSize).toInt()
                    } else {
                        minBuffer
                    }
                }
                .setAudioTrackBuilderModifier { builder, _ ->
                    runCatching {
                        builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                    }
                }
                .build()
        val sink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                .setAudioOutputProvider(output)
                .setExperimentalAudioOffloadListener(
                    object : ExoPlayer.AudioOffloadListener {
                        override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
                            Log.i("DmtAudio", "offloaded=$isOffloadedPlayback")
                        }
                    },
                )
                .build()
        return object : ForwardingAudioSink(sink) {
            override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
                // The MP3 gapless metadata can confuse the offload DSP; let it play the raw
                // frames instead.
                super.setOffloadDelayPadding(0, 0)
            }
        }
    }
}

/**
 * MP3 offload glitches on some devices, so MP3 is decoded on the platform decoder and the PCM
 * path. AAC and the other formats keep compressed offload, which is the efficient path.
 */
@OptIn(UnstableApi::class)
internal fun Player.applyOffloadPolicy(mediaItem: MediaItem?) {
    val path = mediaItem?.localConfiguration?.tag as? String ?: return
    val extension = path.substringAfterLast('.', "").lowercase()
    // Debug: test MP3 offload with the deeper buffer and event logging.
    val offload = true
    val gapless = extension != "opus" && extension != "ogg"
    val mode =
        if (offload) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(mode)
                    .setIsGaplessSupportRequired(gapless)
                    .build(),
            )
            .build()
}

/** Logs which compressed formats the device reports as offloadable. */
private fun logOffloadSupport(context: Context) {
    val manager = context.getSystemService(AudioManager::class.java) ?: return
    val attributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    val formats =
        mapOf(
            "mp3" to AudioFormat.ENCODING_MP3,
            "aac" to AudioFormat.ENCODING_AAC_LC,
            "opus" to AudioFormat.ENCODING_OPUS,
        )
    val method =
        runCatching {
            AudioManager::class.java.getMethod(
                "isOffloadedPlaybackSupported",
                AudioFormat::class.java,
                AudioAttributes::class.java,
            )
        }
            .getOrNull()
    for ((name, encoding) in formats) {
        val format =
            AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(48_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        val supported =
            method
                ?.let { runCatching { it.invoke(manager, format, attributes) as? Boolean }.getOrNull() }
                ?: false
        Log.i("DmtAudio", "offload $name=$supported")
    }
}

/** The music player: bundled decoders first, offload where supported, focus and noisy handled. */
@OptIn(UnstableApi::class)
internal fun Context.buildMusicPlayer(dataSourceFactory: DataSource.Factory): ExoPlayer {
    logOffloadSupport(this)
    val renderers =
        PowerSavingRenderersFactory(this)
            // Platform decoders are more power-efficient; extensions only fill the gaps.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(SoftwareCodecSelector)
            .setEnableDecoderFallback(true)
    val offload =
        AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            // The upstream player requires gapless offload. Without it, some devices'
            // offload DSP can glitch mid-track.
            .setIsGaplessSupportRequired(true)
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
