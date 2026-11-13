package dev.jyotiraditya.lyrics

import java.util.Locale

/** This lyrics as LRC text, with word times when the words are timed. */
fun Lyrics.toLrc(): String {
    if (!synced) return lines.joinToString("\n") { it.text }
    return lines.joinToString("\n") { line ->
        buildString {
            append(line.lrcLine())
            line.transliteration?.let { appendSecondary("bg", line.startMs, it) }
            line.translation.forEach { appendSecondary("tr", line.startMs, it) }
        }
    }
}

/**
 * This lyrics as plain line-level LRC that any player can read. The rich version, with word
 * timing, translations and backing vocals, goes in a DMT custom tag instead.
 */
fun Lyrics.toStandardLrc(): String {
    if (!synced) return lines.joinToString("\n") { it.text }
    return lines.joinToString("\n") { line -> "[${line.startMs.lrcTime()}]${line.text}" }
}

private fun LyricLine.lrcLine(): String {
    val stamp = "[${startMs.lrcTime()}]"
    // A secondary line keeps its side after the round trip; primary is the default.
    val voice = if (this.voice == Voice.SECONDARY) "v1:" else ""
    if (words.isEmpty()) return stamp + voice + text
    return buildString {
        append(stamp)
        append(voice)
        appendTimedText(text, words)
    }
}

/** Appends a `[bg: ...]` transliteration or `[tr: ...]` translation line. */
private fun StringBuilder.appendSecondary(marker: String, lineStartMs: Long, text: TimedText) {
    append('\n')
    append('[').append(marker).append(": [").append(lineStartMs.lrcTime()).append(']')
    if (text.words.isEmpty()) {
        append(text.text)
    } else {
        appendTimedText(text.text, text.words)
    }
    append(']')
}

/**
 * Appends [text] with its word start tags, a `bg` flag on backing words, and a closing tag for
 * the last word so its real end time survives the round trip.
 */
private fun StringBuilder.appendTimedText(text: String, words: List<LyricWord>) {
    var cursor = 0
    words.forEach { word ->
        if (word.start > cursor) append(text, cursor, word.start)
        append('<').append(word.startMs.lrcTime())
        if (word.background) append(" bg")
        append('>')
        append(text, word.start, word.end)
        cursor = word.end
    }
    if (cursor < text.length) append(text, cursor, text.length)
    val last = words.lastOrNull()
    if (last != null && last.endMs > last.startMs) {
        append('<').append(last.endMs.lrcTime()).append('>')
    }
}

private fun Long.lrcTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (this % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}
