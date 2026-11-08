package com.koi.backend.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderTest {

    private val lyricsProvider = LyricsProvider()

    @Test
    fun testParseLrcStandardTimestamps() {
        val lrc = """
            [00:15.71]I'm tryna put you in the worst mood, ah
            [00:18.36]P1 cleaner than your church shoes, ah
            [00:20.98]Milli point two just to hurt you, ah
        """.trimIndent()

        val lines = lyricsProvider.parseLrc(lrc)
        assertEquals(3, lines.size)

        assertEquals(15710L, lines[0].timeMs)
        assertEquals("I'm tryna put you in the worst mood, ah", lines[0].text)

        assertEquals(18360L, lines[1].timeMs)
        assertEquals("P1 cleaner than your church shoes, ah", lines[1].text)

        assertEquals(20980L, lines[2].timeMs)
        assertEquals("Milli point two just to hurt you, ah", lines[2].text)
    }

    @Test
    fun testParseLrcThreeDigitFractionAndMetadataTags() {
        val lrc = """
            [ar:The Weeknd]
            [ti:Starboy]
            [01:05.123]Look what you've done
            [01:08.500]I'm a motherfuckin' starboy
        """.trimIndent()

        val lines = lyricsProvider.parseLrc(lrc)
        assertEquals(2, lines.size)

        assertEquals(65123L, lines[0].timeMs)
        assertEquals("Look what you've done", lines[0].text)

        assertEquals(68500L, lines[1].timeMs)
        assertEquals("I'm a motherfuckin' starboy", lines[1].text)
    }

    @Test
    fun testFetchLrclibLive() = runBlocking {
        val lyrics = lyricsProvider.getLyrics(
            title = "Starboy",
            artist = "The Weeknd"
        )
        // If third-party LRCLIB is available (not 502/rate-limited)
        if (lyrics != null) {
            assertTrue("Lyrics should be synced", lyrics.synced)
            assertTrue("Lyrics lines should not be empty", lyrics.lines.isNotEmpty())
            assertTrue("Should have first timestamp line", lyrics.lines[0].timeMs != null)
        }
    }
}
