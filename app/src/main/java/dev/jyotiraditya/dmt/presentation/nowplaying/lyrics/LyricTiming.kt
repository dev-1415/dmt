package dev.jyotiraditya.dmt.presentation.nowplaying.lyrics

import dev.jyotiraditya.lyrics.LyricLine

internal enum class LineState {
    UPCOMING,
    ACTIVE,
    PASSED,
}

/** The index of the line playing at [positionMs], or -1 before the first; lines sort by start. */
internal fun List<LyricLine>.indexAt(positionMs: Long): Int {
    val found = binarySearch { it.startMs.compareTo(positionMs) }
    val index = if (found >= 0) found else -found - 2
    if (index < 0) return index
    // A backing line can start with the main line or inside it; the live line wants the main
    // line that is still running, not the background-only line.
    if (this[index].isBackgroundOnly()) {
        for (i in index downTo 0) {
            val line = this[i]
            if (!line.isBackgroundOnly() && line.endMs > positionMs) return i
        }
    }
    return index
}

private fun LyricLine.isBackgroundOnly(): Boolean =
    words.isNotEmpty() && words.all { it.background }

internal fun LyricLine.stateAt(positionMs: Long, synced: Boolean): LineState = when {
    !synced || startMs < 0 -> LineState.UPCOMING
    positionMs >= endMs -> LineState.PASSED
    positionMs >= startMs -> LineState.ACTIVE
    else -> LineState.UPCOMING
}
