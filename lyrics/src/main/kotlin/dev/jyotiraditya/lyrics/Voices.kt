package dev.jyotiraditya.lyrics

/**
 * These lines with a line written once per singer folded into one group line. Expects them sorted.
 */
internal fun List<LyricLine>.mergeSimultaneousDuplicates(): List<LyricLine> {
    val result = ArrayList<LyricLine>(size)
    for (line in this) {
        val last = result.lastOrNull()
        // A background or transliteration line can repeat the main line's text; merging it
        // would turn the main line into a group line and drop the secondary line.
        val lineIsBackground = line.words.isNotEmpty() && line.words.all { it.background }
        val lastIsBackground =
            last != null && last.words.isNotEmpty() && last.words.all { it.background }
        val duplicate =
            last != null &&
                !last.interlude &&
                last.text == line.text &&
                line.startMs < last.endMs &&
                !lineIsBackground &&
                !lastIsBackground
        if (!duplicate) {
            result += line
            continue
        }
        val singer =
            when {
                last.voice == Voice.GROUP -> last.singer
                line.voice == Voice.GROUP -> line.singer
                else -> LyricLine.NO_SINGER
            }
        result[result.lastIndex] =
            last.copy(
                endMs = maxOf(last.endMs, line.endMs),
                voice = Voice.GROUP,
                singer = singer,
            )
    }
    return result
}

/** These lines with even singers on the leading side and odd ones on the trailing side. */
internal fun List<LyricLine>.assignSides(): List<LyricLine> = map { line ->
    if (line.voice == Voice.GROUP || line.interlude || line.singer < 0) {
        line
    } else {
        line.copy(voice = if (line.singer % 2 == 0) Voice.PRIMARY else Voice.SECONDARY)
    }
}
