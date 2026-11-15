package dev.jyotiraditya.dmt.presentation.player

import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import dev.jyotiraditya.dmt.core.base.StateStore
import dev.jyotiraditya.dmt.data.repository.AnimatedArtworkRepository
import dev.jyotiraditya.dmt.domain.model.Cover
import dev.jyotiraditya.dmt.domain.model.TrackTech
import dev.jyotiraditya.dmt.domain.repository.ArtworkRepository
import dev.jyotiraditya.dmt.domain.usecase.GetTrackTechUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COVER_PX = 1024

/** Loads the cover, animated cover and format details of the track being played into [store]. */
internal class NowPlayingDetailsLoader(
    private val artwork: ArtworkRepository,
    private val animatedArtwork: AnimatedArtworkRepository,
    private val getTrackTech: GetTrackTechUseCase,
    private val scope: CoroutineScope,
    private val store: StateStore<DmtState>,
) {
    private var coverJob: Job? = null
    private var animatedArtJob: Job? = null
    private var techJob: Job? = null
    private var techFor: String? = null
    private var techPlayed: Format? = null

    /** Loads the cover and animated art of [mediaItem]; the format comes with its tracks. */
    fun load(mediaItem: MediaItem?) {
        loadCover(mediaItem)
        loadAnimatedArt(mediaItem)
    }

    /** Shows the cached cover of [mediaItem] at once, then the loaded one. */
    fun loadCover(mediaItem: MediaItem?) {
        val forId = mediaItem?.mediaId
        val track = trackFor(forId)
        coverJob?.cancel()
        val ready = track?.let { artwork.cached(it, COVER_PX) }?.let(Cover::Ready)
        store.reduce {
            if (it.nowPlayingId != forId) it else it.copy(cover = ready ?: Cover.Loading)
        }
        if (track == null) return
        coverJob = scope.launch {
            val cover = artwork.load(track, COVER_PX)?.let(Cover::Ready) ?: ready ?: Cover.Missing
            store.reduce { if (it.nowPlayingId != forId) it else it.copy(cover = cover) }
        }
    }

    fun loadAnimatedArt(mediaItem: MediaItem?) {
        val forId = mediaItem?.mediaId
        animatedArtJob?.cancel()
        store.reduce { it.copy(animatedArtUrl = null) }
        if (!store.current.settings.animatedArt) return
        val track = trackFor(forId) ?: return
        animatedArtJob = scope.launch {
            val url = withContext(Dispatchers.IO) { animatedArtwork.streamUrlFor(track) }
            store.reduce { if (it.nowPlayingId != forId) it else it.copy(animatedArtUrl = url) }
        }
    }

    fun loadTech(mediaItem: MediaItem?, played: Format?) {
        val uri = mediaItem?.localConfiguration?.uri
        val forId = mediaItem?.mediaId
        val track = trackFor(forId)
        // The transition and the tracks callback both ask for this; read the file only once.
        if (track != null && forId == techFor && (played == null || played == techPlayed)) return
        if (track != null) {
            techFor = forId
            techPlayed = played
        }
        techJob?.cancel()
        techJob = scope.launch {
            val tech = uri?.let { getTrackTech(it, track, played) } ?: TrackTech.None
            store.reduce {
                if (it.nowPlayingId != forId) return@reduce it
                it.copy(tech = tech.specs, quality = tech.quality)
            }
        }
    }

    private fun trackFor(mediaId: String?) = store.current.library.track(mediaId)
}
