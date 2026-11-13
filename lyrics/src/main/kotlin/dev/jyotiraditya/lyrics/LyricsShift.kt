package dev.jyotiraditya.lyrics

/** These lyrics with every line and word time moved by [offsetMs], clamped at zero. */
fun Lyrics.shiftBy(offsetMs: Long): Lyrics =
    copy(
        lines =
            lines.map { line ->
                line.copy(
                    startMs = line.startMs.shifted(offsetMs),
                    endMs = line.endMs.shifted(offsetMs),
                    words = line.words.map { it.shiftBy(offsetMs) },
                    translation = line.translation.map { it.shiftBy(offsetMs) },
                    transliteration = line.transliteration?.shiftBy(offsetMs),
                )
            },
    )

private fun Long.shifted(offsetMs: Long): Long =
    if (this == LyricLine.UNTIMED) this else (this + offsetMs).coerceAtLeast(0)

private fun LyricWord.shiftBy(offsetMs: Long): LyricWord =
    copy(startMs = startMs.shifted(offsetMs), endMs = endMs.shifted(offsetMs))

private fun TimedText.shiftBy(offsetMs: Long): TimedText =
    copy(words = words.map { it.shiftBy(offsetMs) })
