package dev.jyotiraditya.dmt.presentation.nowplaying.lyrics

import dev.jyotiraditya.lyrics.LyricLine
import dev.jyotiraditya.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimingTest {

    @Test
    fun `the line playing at a position is the last one that started`() {
        val lines =
            listOf(
                LyricLine(1_000, 2_000, "One"),
                LyricLine(2_000, 3_000, "Two"),
                LyricLine(3_000, 4_000, "Three"),
            )
        assertEquals(0, lines.indexAt(1_500))
        assertEquals(1, lines.indexAt(2_500))
        assertEquals(2, lines.indexAt(3_500))
        assertEquals(-1, lines.indexAt(500))
    }

    @Test
    fun `a background line starting inside the main line does not hide it`() {
        val main =
            LyricLine(
                startMs = 1_000,
                endMs = 5_000,
                text = "Hello",
                words = listOf(LyricWord(1_000, 5_000, 0, 5, background = false)),
            )
        val background =
            LyricLine(
                startMs = 2_000,
                endMs = 3_000,
                text = "ooh",
                words = listOf(LyricWord(2_000, 3_000, 0, 3, background = true)),
            )
        val lines = listOf(main, background)
        assertEquals(0, lines.indexAt(2_500))
        assertEquals(0, lines.indexAt(4_000))
    }

    @Test
    fun `a background-only line at the same start does not hide the main line`() {
        val main =
            LyricLine(
                startMs = 1_000,
                endMs = 3_000,
                text = "Hello",
                words = listOf(LyricWord(1_000, 3_000, 0, 5, background = false)),
            )
        val background =
            LyricLine(
                startMs = 1_000,
                endMs = 3_000,
                text = "ooh",
                words = listOf(LyricWord(1_000, 3_000, 0, 3, background = true)),
            )
        // The parser keeps the main line before its backing line, but a binary search over
        // equal starts may land on either one.
        val lines = listOf(main, background)
        assertEquals(0, lines.indexAt(1_500))
        assertEquals(0, lines.indexAt(2_500))
    }
}
