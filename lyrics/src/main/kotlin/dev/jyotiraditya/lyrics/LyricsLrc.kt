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

private fun LyricLine.lrcLine(): String {
    val stamp = "[${startMs.lrcTime()}]"
    if (words.isEmpty()) return stamp + text
    return buildString {
        append(stamp)
        var cursor = 0
        words.forEach { word ->
            if (word.start > cursor) append(text, cursor, word.start)
            append('<').append(word.startMs.lrcTime()).append('>')
            append(text, word.start, word.end)
            cursor = word.end
        }
        if (cursor < text.length) append(text, cursor, text.length)
    }
}

/** Appends a `[bg: ...]` transliteration or `[tr: ...]` translation line. */
private fun StringBuilder.appendSecondary(marker: String, lineStartMs: Long, text: TimedText) {
    append('\n')
    append('[').append(marker).append(": [").append(lineStartMs.lrcTime()).append(']')
    if (text.words.isEmpty()) {
        append(text.text)
    } else {
        var cursor = 0
        text.words.forEach { word ->
            if (word.start > cursor) append(text.text, cursor, word.start)
            append('<').append(word.startMs.lrcTime()).append('>')
            append(text.text, word.start, word.end)
            cursor = word.end
        }
        if (cursor < text.text.length) append(text.text, cursor, text.text.length)
    }
    append(']')
}

private fun Long.lrcTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (this % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}
