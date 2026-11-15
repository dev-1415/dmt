package dev.jyotiraditya.dmt.presentation.nowplaying

import dev.jyotiraditya.lyrics.LyricLine
import dev.jyotiraditya.lyrics.LyricWord
import dev.jyotiraditya.lyrics.Lyrics
import dev.jyotiraditya.lyrics.toLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAddWordTest {

    private fun line() =
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

    @Test
    fun `a word added at the end is written into the LRC`() {
        val added = line().addWord("again", 2_000, null)
        assertEquals("Hello world again", added.text)
        assertEquals(3, added.words.size)
        assertEquals(12, added.words.last().start)
        assertEquals(17, added.words.last().end)

        val lrc = Lyrics(listOf(added), synced = true).toLrc()
        assertTrue(lrc.contains("<00:02.00>again"))

        val parsed = dev.jyotiraditya.lyrics.LyricsParser.parse(lrc)!!.lines.single()
        assertTrue(parsed.words.any { parsed.text.substring(it.start, it.end) == "again" })
    }

    @Test
    fun `a word added after the first word shifts the later words`() {
        val added = line().addWord("big", 1_500, 0)
        assertEquals("Hello big world", added.text)
        assertEquals(3, added.words.size)
        assertEquals(6, added.words[1].start)
        assertEquals(9, added.words[1].end)
        assertEquals(10, added.words[2].start)
        assertEquals(15, added.words[2].end)

        val lrc = Lyrics(listOf(added), synced = true).toLrc()
        assertTrue(lrc.contains("<00:01.50>big"))
    }
}
