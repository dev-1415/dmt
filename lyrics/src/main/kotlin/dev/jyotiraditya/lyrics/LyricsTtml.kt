package dev.jyotiraditya.lyrics

import java.util.Locale

/**
 * These lyrics as standard TTML for TTML-aware players.
 *
 * Each line gets its own `<div>` with the absolute line time, and its `<p>` and `<span>` times are
 * relative to that div. Players that add parent times, such as Gramophone, then land on the right
 * moment, and DMT's parser detects the relative form.
 */
fun Lyrics.toTtml(): String? {
    if (!synced) return null
    val body =
        lines.mapIndexed { index, line ->
            val start = line.startMs.coerceAtLeast(0L)
            val end =
                line.endMs.takeIf { it > start }
                    ?: lines.getOrNull(index + 1)?.startMs?.takeIf { it > start }
                    ?: (start + 1L)
            val duration = end - start
            val content =
                if (line.words.isEmpty()) {
                    escapeXml(line.text)
                } else {
                    timedWords(line)
                }
            """
            <div begin="${ttmlTime(start)}">
              <p begin="00:00:00.000" end="${ttmlTime(duration)}">$content</p>
            </div>
            """.trimIndent()
        }.joinToString("\n")
    return """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xml:lang="en">
  <body>
$body
  </body>
</tt>
"""
}

private fun timedWords(line: LyricLine): String = buildString {
    var cursor = 0
    line.words.forEach { word ->
        val start = word.start.coerceIn(0, line.text.length)
        val end = word.end.coerceIn(start, line.text.length)
        if (start > cursor) append(escapeXml(line.text.substring(cursor, start)))
        val wordStart = (word.startMs - line.startMs).coerceAtLeast(0L)
        val wordEnd = (word.endMs - line.startMs).coerceAtLeast(wordStart)
        append("<span begin=\"")
        append(ttmlTime(wordStart))
        append("\" end=\"")
        append(ttmlTime(wordEnd))
        append("\">")
        append(escapeXml(line.text.substring(start, end)))
        append("</span>")
        cursor = end
    }
    if (cursor < line.text.length) append(escapeXml(line.text.substring(cursor)))
}

private fun ttmlTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
}

private fun escapeXml(text: String): String = buildString(text.length) {
    text.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(char)
        }
    }
}
