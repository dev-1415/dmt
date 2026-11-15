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
        assertTrue(lrc.contains("v2:"))
        val parsed = LyricsParser.parse(lrc)!!
        // Backing vocals become their own [bg: ...] lines, and a third singer is written as a
        // group, so compare the first two singers' main lines by their start time.
        val actualByStart = parsed.lines.associateBy { it.startMs }
        val expectedMain =
            lyrics.lines.filter { line ->
                (line.words.isEmpty() || line.words.any { !it.background }) &&
                    line.singer in 0..1
            }
        assertTrue(expectedMain.isNotEmpty())
        expectedMain.forEach { expected ->
            val actual = actualByStart[expected.startMs] ?: return@forEach
            assertEquals(expected.voice, actual.voice)
        }
    }

    @Test
    fun `a background line repeating the main text stays separate`() {
        val main =
            LyricLine(
                1_000,
                2_000,
                "Take me away",
                words = listOf(LyricWord(1_000, 2_000, 0, 12, background = false)),
            )
        val background =
            LyricLine(
                1_000,
                2_000,
                "Take me away",
                words = listOf(LyricWord(1_000, 2_000, 0, 12, background = true)),
            )
        val merged = listOf(main, background).mergeSimultaneousDuplicates()
        assertEquals(2, merged.size)
        assertEquals(Voice.PRIMARY, merged.first().voice)
        assertTrue(merged.last().words.all { it.background })
    }

    @Test
    fun `a group is written as v3 and survives the round trip`() {
        val lyrics =
            Lyrics(
                listOf(
                    LyricLine(1_000, 2_000, "One", singer = 0),
                    LyricLine(2_000, 3_000, "Two", singer = 1, voice = Voice.SECONDARY),
                    LyricLine(
                        3_000,
                        4_000,
                        "All",
                        voice = Voice.GROUP,
                        singer = LyricLine.NO_SINGER,
                    ),
                ),
                synced = true,
            )
        val lrc = lyrics.toLrc()
        assertTrue(lrc.contains("[00:02.00]v2:Two"))
        assertTrue(lrc.contains("[00:03.00]v3:All"))

        val parsed = LyricsParser.parse(lrc)!!
        assertEquals(Voice.GROUP, parsed.lines.last().voice)
        assertEquals(LyricLine.NO_SINGER, parsed.lines.last().singer)
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
