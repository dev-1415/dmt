package dev.jyotiraditya.lyrics

import dev.jyotiraditya.lyrics.lrc.LrcParser
import dev.jyotiraditya.lyrics.ttml.TtmlParser

object LyricsParser {

    /** The lyrics in [raw], read as TTML, LRC or plain text, or null if it holds none. */
    fun parse(raw: String, relativeTtml: Boolean = false): Lyrics? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parsed =
            when (LyricsFormat.of(trimmed)) {
                LyricsFormat.TTML -> TtmlParser.parse(trimmed, relativeTtml)

                LyricsFormat.ENRICHED_LRC,
                LyricsFormat.WORD_LRC,
                LyricsFormat.LINE_LRC,
                -> LrcParser.parse(trimmed)

                LyricsFormat.PLAIN -> parsePlain(trimmed)
            }
        return parsed?.copy(raw = trimmed)
    }

    private fun parsePlain(text: String): Lyrics = Lyrics(
        lines =
            text
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map {
                    LyricLine(startMs = LyricLine.UNTIMED, endMs = LyricLine.UNTIMED, text = it)
                }
                .toList(),
        synced = false,
    )
}
