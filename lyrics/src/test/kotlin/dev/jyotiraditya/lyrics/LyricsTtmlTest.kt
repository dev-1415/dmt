package dev.jyotiraditya.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsTtmlTest {

    @Test
    fun generatedTtmlRoundTripsLineAndWordTimes() {
        val lyrics =
            Lyrics(
                lines =
                    listOf(
                        LyricLine(
                            startMs = 31_358,
                            endMs = 33_830,
                            text = "Maybe we should switch careers",
                            words =
                                listOf(
                                    LyricWord(31_358, 31_902, 0, 5, false),
                                    LyricWord(31_902, 32_045, 6, 8, false),
                                    LyricWord(32_045, 32_337, 9, 15, false),
                                    LyricWord(32_337, 32_583, 16, 22, false),
                                    LyricWord(32_583, 33_830, 23, 30, false),
                                ),
                        ),
                    ),
                synced = true,
            )

        val ttml = lyrics.toTtml()
        assertNotNull(ttml)
        val parsed = LyricsParser.parse(ttml!!, relativeTtml = true)
        assertNotNull(parsed)

        // The leading silence becomes an interlude line.
        val sung = parsed!!.lines.filter { !it.interlude }
        assertEquals(1, sung.size)
        val line = sung.first()
        assertEquals(31_358, line.startMs)
        assertEquals(33_830, line.endMs)
        assertEquals(5, line.words.size)
        assertEquals(31_358, line.words.first().startMs)
        assertEquals(33_830, line.words.last().endMs)
    }
}
