package dev.jyotiraditya.lyrics

import java.util.Locale

/** This lyrics as LRC text, with word times when the words are timed. */
fun Lyrics.toLrc(): String {
    if (!synced) return lines.joinToString("\n") { it.text }
    return lines.joinToString("\n") { line ->
        buildString {
            val main = line.lrcLine()
            if (main.isNotEmpty()) append(main)
            line.backgroundRuns().forEach { run ->
                if (isNotEmpty()) append('\n')
                append(line.backgroundLine(run))
            }
            line.transliteration?.let { appendSecondary("bg", line.startMs, it) }
            line.translation.forEach { appendSecondary("tr", line.startMs, it) }
        }
    }
}

/**
 * This lyrics as plain line-level LRC that any player can read. The rich version, with word
 * timing, translations and backing vocals, goes in the standard TTML sidecar instead.
 */
fun Lyrics.toStandardLrc(): String {
    if (!synced) return lines.joinToString("\n") { it.text }
    return lines.joinToString("\n") { line -> "[${line.startMs.lrcTime()}]${line.text}" }
}

private fun LyricLine.lrcLine(): String {
    val stamp = "[${startMs.lrcTime()}]"
    // A secondary line keeps its side after the round trip; primary is the default.
    val voice = if (this.voice == Voice.SECONDARY) "v1:" else ""
    val primary = words.filterNot { it.background }
    if (words.isEmpty()) return stamp + voice + text
    if (primary.isEmpty()) return ""
    return buildString {
        append(stamp)
        append(voice)
        appendTimedText(mainText(), primary)
    }
}

/** The line text with backing-vocal words blanked out, keeping every word offset valid. */
private fun LyricLine.mainText(): String {
    if (words.none { it.background }) return text
    val chars = text.toCharArray()
    words.filter { it.background }.forEach { word ->
        for (i in word.start.coerceAtLeast(0) until word.end.coerceAtMost(chars.size)) {
            chars[i] = ' '
        }
    }
    return String(chars)
}

/** Contiguous runs of backing-vocal words, in line order. */
private fun LyricLine.backgroundRuns(): List<List<LyricWord>> {
    val runs = mutableListOf<MutableList<LyricWord>>()
    var current: MutableList<LyricWord>? = null
    for (word in words.sortedBy { it.start }) {
        if (word.background) {
            if (current == null) {
                current = mutableListOf(word)
                runs += current
            } else {
                current.add(word)
            }
        } else {
            current = null
        }
    }
    return runs
}

/** A `[bg: ...]` line for one run of backing-vocal words, with its own word times. */
private fun LyricLine.backgroundLine(run: List<LyricWord>): String {
    val first = run.first()
    val last = run.last()
    val start = first.start.coerceIn(0, text.length)
    val end = last.end.coerceIn(start, text.length)
    val runText = text.substring(start, end)
    val runWords = run.map { it.copy(start = it.start - start, end = it.end - start) }
    return buildString {
        append("[bg: [").append(startMs.lrcTime()).append(']')
        appendTimedText(runText, runWords)
        append(']')
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
 * Appends [text] with its word start tags and a closing tag for the last word so its real end
 * time survives the round trip.
 */
private fun StringBuilder.appendTimedText(text: String, words: List<LyricWord>) {
    var cursor = 0
    words.forEach { word ->
        if (word.start > cursor) append(text, cursor, word.start)
        append('<').append(word.startMs.lrcTime()).append('>')
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
