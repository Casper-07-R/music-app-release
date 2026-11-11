package com.koi.backend.innertube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InnerTubeIntegrationTest {

    private val client = InnerTubeClient()

    @Test
    fun testBootstrapVisitorData() = runBlocking {
        val vd = client.getVisitorData()
        assertNotNull("VisitorData should be bootstrapped", vd)
        assertTrue("VisitorData should start with Cgt or Cgs", vd!!.startsWith("Cgt") || vd.startsWith("Cgs"))
    }

    @Test
    fun testSearchSongs() = runBlocking {
        val songs = client.searchSongs("Starboy The Weeknd")
        assertTrue("Search should return at least one song", songs.isNotEmpty())
        val first = songs.first()
        assertTrue("Song videoId should not be blank", first.videoId.isNotBlank())
        assertTrue("Song title should contain Starboy", first.title.contains("Starboy", ignoreCase = true))
    }

    @Test
    fun testResolveStream() = runBlocking {
        // Starboy videoId
        val stream = client.resolveStream("34Na4j8AVgA")
        assertNotNull("Direct stream should be resolved", stream)
        assertTrue("Stream URL should be googlevideo", stream!!.streamUrl.contains("googlevideo.com"))
        assertTrue("Format should be audio", stream.mimeType.startsWith("audio/"))

        // Test downloading the first chunk
        val req = okhttp3.Request.Builder()
            .url(stream.streamUrl)
            .header("User-Agent", stream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT)
            .header("Range", "bytes=0-65535")
            .build()
        val resp = client.streamingOkHttpClient.newCall(req).execute()
        println("Stream HTTP response: code=${resp.code}, msg=${resp.message}, len=${resp.body?.contentLength()}")
        assertTrue("Stream download must succeed (200 or 206)", resp.code in 200..206)
        val bytes = resp.body?.bytes()
        assertNotNull(bytes)
        assertTrue(bytes!!.isNotEmpty())
    }

    @Test
    fun testStreamingOkHttpClientConfiguration() {
        assertEquals("Streaming OkHttp client must have 0 read timeout for continuous buffering", 0, client.streamingOkHttpClient.readTimeoutMillis)
        assertTrue(client.streamingOkHttpClient.retryOnConnectionFailure)
    }

    @Test
    fun testSearchComprehensive() = runBlocking {
        val result = client.searchComprehensive("The Weeknd")
        assertNotNull("Search result should not be null", result)
        assertNotNull("Top result should be found", result.topResult)
        assertTrue("Artists should not be empty", result.artists.isNotEmpty())
        assertTrue("Songs should not be empty", result.songs.isNotEmpty())
        val firstArtist = result.artists.first()
        assertTrue("Artist id should start with UC", firstArtist.id.startsWith("UC"))
        assertTrue("Artist name should be non-empty", firstArtist.name.isNotBlank())
    }

    @Test
    fun testSearchArtists() = runBlocking {
        val artists = client.searchArtists("Taylor Swift")
        assertTrue("Should return artists", artists.isNotEmpty())
        val first = artists.first()
        assertTrue("Artist name should contain Taylor Swift", first.name.contains("Taylor Swift", ignoreCase = true))
        assertTrue("Artist id should start with UC", first.id.startsWith("UC"))
    }

    @Test
    fun testSearchPlaylists() = runBlocking {
        val playlists = client.searchPlaylists("Lofi hip hop")
        assertTrue("Should return playlists", playlists.isNotEmpty())
        val first = playlists.first()
        assertTrue("Playlist id should not be empty", first.id.isNotBlank())
        assertTrue("Playlist title should not be empty", first.title.isNotBlank())
    }

    @Test
    fun testGetArtistProfile() = runBlocking {
        val profile = client.getArtistProfile("UClYV6hHlupm_S_ObS1W-DYw")
        assertNotNull("Artist profile should be loaded", profile)
        assertEquals("The Weeknd", profile!!.name)
        assertTrue("Top songs should not be empty", profile.topSongs.isNotEmpty())
        assertTrue("Albums should not be empty", profile.albums.isNotEmpty())
        assertTrue("Similar artists should not be empty", profile.similarArtists.isNotEmpty())
        assertNotNull("Bio or description should be present", profile.bio ?: profile.description)
    }
}
