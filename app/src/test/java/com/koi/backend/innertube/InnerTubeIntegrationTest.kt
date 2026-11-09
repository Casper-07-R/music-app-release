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
}
