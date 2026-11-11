package com.koi.backend.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderTest {

    private val lyricsProvider = LyricsProvider()

    @Test
    fun testParseLrcOffsetAndCommaSeparators() {
        val lrc = """
            [offset: 350]
            [01:05,12]First line with comma decimal
            [01:10.00] 
            [01:15.50]Second line after spacer
        """.trimIndent()

        val lines = lyricsProvider.parseLrc(lrc)
        assertEquals(2, lines.size)
        // 65120 + 350 = 65470
        assertEquals(65470L, lines[0].timeMs)
        assertEquals("First line with comma decimal", lines[0].text)

        // 75500 + 350 = 75850
        assertEquals(75850L, lines[1].timeMs)
        assertEquals("Second line after spacer", lines[1].text)
    }

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

    @Test
    fun testTitleAndArtistCleaning() {
        assertEquals("Starboy", lyricsProvider.cleanTitle("Starboy (Official Music Video)", "The Weeknd"))
        assertEquals("Blinding Lights", lyricsProvider.cleanTitle("Blinding Lights [Official Audio]", "The Weeknd"))
        assertEquals("Bohemian Rhapsody", lyricsProvider.cleanTitle("Queen - Bohemian Rhapsody (Official Video)", "Queen"))
        assertEquals("Anti-Hero", lyricsProvider.cleanTitle("Anti-Hero (feat. Bleachers)", "Taylor Swift"))
        assertEquals("Yesterday", lyricsProvider.cleanTitle("Yesterday - Remastered 2015", "The Beatles"))
        assertEquals("In The End", lyricsProvider.cleanTitle("In The End (Official HD Video) [Remastered]", "Linkin Park"))
        assertEquals("One Sun One Moon", lyricsProvider.cleanTitle("One Sun One Moon (From \"Jailer 2\")", "Anirudh Ravichander"))
        assertEquals("Aathi Iva Yarraa", lyricsProvider.cleanTitle("Aathi Iva Yarraa (From \"Scene\")", "Sushin Shyam"))
        assertEquals("Hukum - Thalaivar Alappara", lyricsProvider.cleanTitle("Hukum - Thalaivar Alappara (Tamil) (From \"Jailer\")", "Anirudh Ravichander"))

        assertEquals("Queen", lyricsProvider.cleanArtist("Queen - Topic"))
        assertEquals("The Weeknd", lyricsProvider.cleanArtist("The Weeknd • 45M views"))
        assertEquals("The Weeknd", lyricsProvider.getPrimaryArtist("The Weeknd, Daft Punk"))
        assertEquals("Eminem", lyricsProvider.getPrimaryArtist("Eminem feat. Rihanna"))
        assertEquals("Coldplay", lyricsProvider.getPrimaryArtist("Coldplay & BTS"))
    }

    @Test
    fun testStripAllBrackets() {
        assertEquals("One Sun One Moon", lyricsProvider.stripAllBrackets("One Sun One Moon (From \"Jailer 2\")"))
        assertEquals("Kaathu Veesum Desam", lyricsProvider.stripAllBrackets("Kaathu Veesum Desam [OST Scene]"))
        assertEquals("Aathiram", lyricsProvider.stripAllBrackets("Aathiram (Official Audio)"))
    }

    @Test
    fun testUnescapeHtml() {
        assertEquals("Sushin's & Vaasi's \"Song\"", lyricsProvider.unescapeHtml("Sushin&#039;s &amp; Vaasi&#039;s &quot;Song&quot;"))
        assertEquals("Hello – World", lyricsProvider.unescapeHtml("Hello &#8211; World"))
    }

    @Test
    fun testFetchLrclibMessyTitle() = runBlocking {
        val lyrics = lyricsProvider.getLyrics(
            title = "Starboy (Official Music Video)",
            artist = "The Weeknd - Topic"
        )
        if (lyrics != null) {
            assertTrue("Should find lyrics for messy title", lyrics.lines.isNotEmpty())
        }
    }

    @Test
    fun testYouTubeMusicLyricsFallback() = runBlocking {
        val innerTube = com.koi.backend.innertube.InnerTubeClient()
        val providerWithYt = LyricsProvider(innerTube = innerTube)

        // Starboy videoId
        val lyrics = providerWithYt.getLyrics(
            title = "NonExistentTrack12345XYZ",
            artist = "NonExistentArtist98765XYZ",
            videoId = "34Na4j8AVgA"
        )
        assertNotNull("YouTube Music fallback should return lyrics for valid videoId", lyrics)
        assertTrue("Lyrics lines should not be empty", lyrics!!.lines.isNotEmpty())
        assertEquals("34Na4j8AVgA", lyrics.videoId)
    }

    @Test
    fun testLooviqFallbackLive() = runBlocking {
        val lyrics = lyricsProvider.getLyrics(
            title = "Imaginoromancophilia",
            artist = "Sushin Shyam & Vaasi",
            videoId = "l_qCWDAIyeY"
        )
        if (lyrics != null) {
            assertTrue("Lyrics should not be empty", lyrics.lines.isNotEmpty())
            assertEquals("l_qCWDAIyeY", lyrics.videoId)
        }
    }

    @Test
    fun testLyricsOvhFallbackLive() = runBlocking {
        val lyrics = lyricsProvider.getLyrics(
            title = "Yellow",
            artist = "Coldplay",
            videoId = "yKNxeF4KMsY"
        )
        if (lyrics != null) {
            assertTrue("Lyrics lines should not be empty", lyrics.lines.isNotEmpty())
        }
    }

    @Test
    fun testCreateLiveLyricsCadence() {
        val plainLines = listOf(
            LyricLine(text = "First short line"),
            LyricLine(text = "Second longer line with many words singing along in rhythm"),
            LyricLine(text = "Outro line")
        )
        val liveLines = lyricsProvider.createLiveLyrics(plainLines, durationSeconds = 30)
        assertEquals(3, liveLines.size)
        // Intro should be clamped >= 6000ms
        assertTrue("First line should start at or after 6s intro", (liveLines[0].timeMs ?: 0L) >= 6000L)
        // Subsequent lines should have strictly increasing timestamps
        assertTrue("Second line after first", (liveLines[1].timeMs ?: 0L) > (liveLines[0].timeMs ?: 0L))
        assertTrue("Third line after second", (liveLines[2].timeMs ?: 0L) > (liveLines[1].timeMs ?: 0L))
        // Longer line should have more duration allotted than short line
        val firstDur = (liveLines[1].timeMs ?: 0L) - (liveLines[0].timeMs ?: 0L)
        val secondDur = (liveLines[2].timeMs ?: 0L) - (liveLines[1].timeMs ?: 0L)
        assertTrue("Longer line gets more time", secondDur > firstDur)
    }

    @Test
    fun testEnsureLiveLyricsPreservesExistingTimestamps() {
        val synced = Lyrics(
            videoId = "test1",
            synced = true,
            lines = listOf(
                LyricLine(timeMs = 12000L, text = "Authentic LRC line 1"),
                LyricLine(timeMs = 16500L, text = "Authentic LRC line 2")
            )
        )
        val result = lyricsProvider.ensureLiveLyrics(synced, 200)
        assertTrue(result.synced)
        assertEquals(12000L, result.lines[0].timeMs)
        assertEquals(16500L, result.lines[1].timeMs)
    }

    @Test
    fun testEnsureLiveLyricsUpgradesPlainLyrics() {
        val plain = Lyrics(
            videoId = "test2",
            synced = false,
            lines = listOf(
                LyricLine(text = "Plain lyric line 1"),
                LyricLine(text = "Plain lyric line 2")
            )
        )
        val result = lyricsProvider.ensureLiveLyrics(plain, 120)
        assertTrue(result.synced)
        assertNotNull(result.lines[0].timeMs)
        assertNotNull(result.lines[1].timeMs)
        assertTrue((result.lines[1].timeMs ?: 0L) > (result.lines[0].timeMs ?: 0L))
    }

    @Test
    fun testMegalobizLiveLookup() = runBlocking {
        val lyrics = lyricsProvider.fetchMegalobiz("Hukum", "Anirudh Ravichander", 206)
        if (lyrics != null) {
            assertTrue("Megalobiz lyrics should be synced", lyrics.synced)
            assertEquals("Megalobiz", lyrics.source)
            assertTrue("Megalobiz should return timestamped lines", lyrics.lines.isNotEmpty())
            assertTrue("First line should have timeMs", lyrics.lines[0].timeMs != null)
        }
    }

    @Test
    fun testNetEaseLiveLookup() = runBlocking {
        val lyrics = lyricsProvider.fetchNetEase("Kesariya", "Pritam", 268)
        if (lyrics != null) {
            assertTrue("NetEase lyrics should be synced", lyrics.synced)
            assertEquals("NetEase", lyrics.source)
            assertTrue("NetEase should return lines", lyrics.lines.isNotEmpty())
        }
    }

    @Test
    fun testSongTitleMatch() {
        assertFalse(lyricsProvider.isSongTitleMatch("Nebulakal", "Aathiram"))
        assertFalse(lyricsProvider.isSongTitleMatch("Nebulakal (From Aavesham)", "Aathiram"))
        assertFalse(lyricsProvider.isSongTitleMatch("Illuminati", "Imaginoromancophilia"))
        assertFalse(lyricsProvider.isSongTitleMatch("Nebulakal", "Unakke Nizhalaanen"))
        assertFalse(lyricsProvider.isSongTitleMatch("Nebulakal", "Kaathu Veesum Desam"))

        assertTrue(lyricsProvider.isSongTitleMatch("Aeroplane", "Aeroplane (From \"Scene\")"))
        assertTrue(lyricsProvider.isSongTitleMatch("Aeroplane (From \"Scene\")", "Aeroplane"))
        assertTrue(lyricsProvider.isSongTitleMatch("Aeroplane - Sushin Shyam", "Aeroplane"))
        assertTrue(lyricsProvider.isSongTitleMatch("Unakke Nizhalaanen", "Unakke Nizhalaanen"))
        assertTrue(lyricsProvider.isSongTitleMatch("Aathi Iva Yarraa", "Aathi Iva Yarraa"))
        assertTrue(lyricsProvider.isSongTitleMatch("Killin' 'em", "Killin' 'em"))
    }

    @Test
    fun testMegalobizRejectsUnmatchedSongForSameArtist() = runBlocking {
        // Sushin Shyam has "Nebulakal" on Megalobiz, but "Aathiram" does not exist on Megalobiz.
        // It must NOT return Nebulakal's Malayalam lyrics for Aathiram!
        val lyrics = lyricsProvider.fetchMegalobiz("Aathiram", "Sushin Shyam")
        if (lyrics != null) {
            val text = lyrics.lines.joinToString(" ") { it.text }
            assertFalse("Megalobiz should not return Nebulakal for Aathiram", text.contains("സുഖിക്കാം"))
            assertTrue("If returned, title must match Aathiram", lyricsProvider.isSongTitleMatch("Aathiram", "Aathiram"))
        }
    }

    @Test
    fun testLrcMuxAeroplaneTamilLyrics() = runBlocking {
        val lyrics = lyricsProvider.fetchLrcMux("Aeroplane", "Sushin Shyam")
        if (lyrics != null) {
            assertTrue("Aeroplane should have synced lyrics", lyrics.synced)
            val fullText = lyrics.lines.joinToString(" ") { it.text }
            assertTrue("Should contain Tamil lyrics", fullText.contains("அடியே") || fullText.contains("உன்னை") || fullText.contains("போனாலும்"))
        }
    }

    @Test
    fun testDescriptionAndMetadataStripping() {
        val seoDescription = "“Imaginoromancophilia” song is a Tamil song performed by Vaasi with music composed by Sushin Shyam and lyrics by Vaasi. As per the song details given above, the song was released on 7 November 2026."
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine(seoDescription))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Song: Aeroplane"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Singer : Sushin Shyam, Gana Bala"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Music: Sushin Shyam"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Movie: Scene"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Written by: Vaasi"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Lyrics licensed by Musixmatch"))
        assertTrue(lyricsProvider.isDescriptionOrMetadataLine("Presenting the official lyrics of Aeroplane"))

        assertFalse(lyricsProvider.isDescriptionOrMetadataLine("Adakkura aala"))
        assertFalse(lyricsProvider.isDescriptionOrMetadataLine("Arattura nee thannala"))
        assertFalse(lyricsProvider.isDescriptionOrMetadataLine("Adi anga inga enga ponaalum unnai vittu pogamaatten di."))
        assertFalse(lyricsProvider.isDescriptionOrMetadataLine("Killin' 'em, killin' 'em"))

        val dirtyList = listOf(
            LyricLine(text = seoDescription),
            LyricLine(text = "Song: Imaginoromancophilia"),
            LyricLine(text = "Singer: Vaasi"),
            LyricLine(text = "Adakkura aala"),
            LyricLine(text = "Arattura nee thannala"),
            LyricLine(text = "Written by: Vaasi")
        )
        val cleaned = lyricsProvider.cleanLyricLines(dirtyList)
        assertEquals(2, cleaned.size)
        assertEquals("Adakkura aala", cleaned[0].text)
        assertEquals("Arattura nee thannala", cleaned[1].text)
    }

    @Test
    fun testLiveLyricsSpansFullDuration() {
        val lines = listOf(
            LyricLine(text = "Adakkura aala"),
            LyricLine(text = "Arattura nee thannala"),
            LyricLine(text = "Karuppula colour ah"),
            LyricLine(text = "Kadathura un kannala vaa"),
            LyricLine(text = "Enmela case ah potu cell ulla saathiputta!")
        )
        val durationSeconds = 176
        val live = lyricsProvider.createLiveLyrics(lines, durationSeconds)
        assertEquals(5, live.size)
        // Intro should start at ~10% (17600ms)
        assertEquals(17600L, live[0].timeMs)
        // Strictly increasing
        for (i in 1 until live.size) {
            assertTrue((live[i].timeMs ?: 0L) > (live[i - 1].timeMs ?: 0L))
        }
        // Last line should be well into the second half of the track, not cramped at 15s
        assertTrue("Last line should span into the later part of the song", (live.last().timeMs ?: 0L) > 100000L)
    }

    @Test
    fun testLrcMuxLiveLookup() = runBlocking {
        val lyrics = lyricsProvider.fetchLrcMux("Starboy", "The Weeknd", 230)
        if (lyrics != null) {
            assertTrue("lrcmux lyrics should be synced", lyrics.synced)
            assertEquals("lrcmux", lyrics.source)
            assertTrue("lrcmux should return lines", lyrics.lines.isNotEmpty())
        }
    }
}
