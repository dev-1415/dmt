package dev.jyotiraditya.lyrics

import java.util.Locale

/** This lyrics as LRC text, with word times when the words are timed. */
fun Lyrics.toLrc(): String {
    if (!synced) return lines.joinToString("\n") { it.text }
    return lines.joinToString("\n") { line ->
        val stamp = "[${line.startMs.lrcTime()}]"
        if (line.words.isEmpty()) {
            stamp + line.text
        } else {
            buildString {
                append(stamp)
                var cursor = 0
                line.words.forEach { word ->
                    if (word.start > cursor) append(line.text, cursor, word.start)
                    append('<').append(word.startMs.lrcTime()).append('>')
                    append(line.text, word.start, word.end)
                    cursor = word.end
                }
                if (cursor < line.text.length) append(line.text, cursor, line.text.length)
            }
        }
    }
}

private fun Long.lrcTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (this % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}
