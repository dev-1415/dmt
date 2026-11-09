package dev.jyotiraditya.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLrcTest {

    @Test
    fun `plain lyrics become plain text`() {
        val lyrics =
            Lyrics(
                listOf(LyricLine(LyricLine.UNTIMED, LyricLine.UNTIMED, "Hello")),
                synced = false,
            )
        assertEquals("Hello", lyrics.toLrc())
    }

    @Test
    fun `synced lyrics become line-timed LRC`() {
        val lyrics =
            Lyrics(
                listOf(
                    LyricLine(1_000, 2_000, "Hello"),
                    LyricLine(2_500, 3_500, "World"),
                ),
                synced = true,
            )
        assertEquals("[00:01.00]Hello\n[00:02.50]World", lyrics.toLrc())
    }

    @Test
    fun `word times become enhanced LRC`() {
        val line =
            LyricLine(
                startMs = 1_000,
                endMs = 2_000,
                text = "Hello world",
                words =
                    listOf(
                        LyricWord(1_000, 1_500, 0, 5, background = false),
                        LyricWord(1_500, 2_000, 6, 11, background = false),
                    ),
            )
        val lyrics = Lyrics(listOf(line), synced = true)
        assertEquals("[00:01.00]<00:01.00>Hello <00:01.50>world", lyrics.toLrc())
    }

    @Test
    fun `translations and transliteration survive a round trip`() {
        val line =
            LyricLine(
                startMs = 1_000,
                endMs = 2_000,
                text = "こんにちは",
                words = listOf(LyricWord(1_000, 2_000, 0, 5, background = false)),
                transliteration =
                    TimedText(
                        "Konnichiwa",
                        listOf(LyricWord(1_000, 2_000, 0, 10, background = false)),
                    ),
                translation =
                    listOf(
                        TimedText(
                            "Hello",
                            listOf(LyricWord(1_000, 2_000, 0, 5, background = false)),
                        ),
                    ),
            )
        val lrc = Lyrics(listOf(line), synced = true).toLrc()
        assertEquals(
            "[00:01.00]<00:01.00>こんにちは\n" +
                "[bg: [00:01.00]<00:01.00>Konnichiwa]\n" +
                "[tr: [00:01.00]<00:01.00>Hello]",
            lrc,
        )

        val parsed = LyricsParser.parse(lrc)!!
        val parsedLine = parsed.lines.single()
        assertEquals("Konnichiwa", parsedLine.transliteration?.text)
        assertEquals("Hello", parsedLine.translation.single().text)
    }
}
