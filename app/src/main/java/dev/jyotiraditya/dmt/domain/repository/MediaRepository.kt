package dev.jyotiraditya.dmt.domain.repository

import dev.jyotiraditya.dmt.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MediaRepository {
    /** The tracks as of the last scan, read without scanning; empty when nothing is stored. */
    suspend fun stored(): List<Track> = emptyList()

    suspend fun scan(): List<Track>

    /** Whether the source changed since the stored library was read, when cheap to check. */
    suspend fun changed(): Boolean = false

    fun invalidate() = Unit

    /** Emits whenever the tracks have changed and are worth reading again. */
    fun changes(): Flow<Unit> = emptyFlow()
}
