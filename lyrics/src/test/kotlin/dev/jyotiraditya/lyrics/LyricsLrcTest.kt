package dev.jyotiraditya.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(
            "[00:01.00]<00:01.00>Hello <00:01.50>world<00:02.00>",
            lyrics.toLrc(),
        )
    }

    @Test
    fun `backing words become a standard bg line`() {
        val line =
            LyricLine(
                startMs = 1_000,
                endMs = 3_000,
                text = "Hello (ooh)",
                words =
                    listOf(
                        LyricWord(1_000, 1_500, 0, 5, background = false),
                        LyricWord(1_500, 3_000, 7, 10, background = true),
                    ),
            )
        val lrc = Lyrics(listOf(line), synced = true).toLrc()
        assertTrue(lrc.contains("[bg: [00:01.00]"))
        assertTrue(lrc.contains("<00:01.50>ooh<00:03.00>"))

        val background =
            LyricsParser.parse(lrc)!!.lines.flatMap { it.words }.filter { it.background }
        assertEquals(1, background.size)
        assertEquals(3_000, background.single().endMs)
    }

    @Test
    fun `bg line keeps the main line start after an offset`() {
        val lrc =
            "[00:01.00]<00:01.00>Hello<00:02.00>\n" +
                "[bg: [00:01.00]<00:01.50>ooh<00:02.00>]"
        val shifted = LyricsParser.parse(lrc)!!.shiftBy(500)
        val out = shifted.toLrc()
        assertTrue(out.contains("[bg: [00:01.50]"))
    }

    @Test
    fun `duet sides survive the round trip`() {
        val raw = javaClass.getResource("/lyrics/ttml_multivoice.ttml")!!.readText()
        val lyrics = LyricsParser.parse(raw)!!
        val lrc = lyrics.toLrc()
        assertTrue(lrc.contains("v1:"))
        val parsed = LyricsParser.parse(lrc)!!
        // Backing vocals become their own [bg: ...] lines, so compare the main lines only.
        val expectedMain =
            lyrics.lines.filter { line -> line.words.isEmpty() || line.words.any { !it.background } }
        val actualMain =
            parsed.lines.filter { line -> line.words.isEmpty() || line.words.any { !it.background } }
        assertEquals(expectedMain.size, actualMain.size)
        expectedMain.zip(actualMain).forEach { (expected, actual) ->
            if (expected.voice != Voice.GROUP) {
                assertEquals(expected.voice, actual.voice)
            }
        }
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
            "[00:01.00]<00:01.00>こんにちは<00:02.00>\n" +
                "[bg: [00:01.00]<00:01.00>Konnichiwa<00:02.00>]\n" +
                "[tr: [00:01.00]<00:01.00>Hello<00:02.00>]",
            lrc,
        )

        val parsed = LyricsParser.parse(lrc)!!
        val parsedLine = parsed.lines.single()
        assertEquals("Konnichiwa", parsedLine.transliteration?.text)
        assertEquals("Hello", parsedLine.translation.single().text)
    }
}
