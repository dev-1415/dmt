package dev.jyotiraditya.lyrics

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** The source text these lines were parsed from, when it is worth keeping. */
    val raw: String? = null,
)
