package com.koi.backend.player

import com.koi.backend.innertube.AudioQualityTier
import com.koi.backend.innertube.PlaybackStream
import com.koi.backend.innertube.StreamQuality
import org.junit.Assert.*
import org.junit.Test

class StreamingFallbackQualityTest {

    private fun createSampleStreamBundle(videoId: String = "test_video_123"): PlaybackStream {
        val veryLowStream = PlaybackStream(
            videoId = videoId,
            streamUrl = "https://stream.youtube.com/249",
            itag = 249,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 49_496L,
            expiresInSeconds = 21600L
        )

        val lowStream = PlaybackStream(
            videoId = videoId,
            streamUrl = "https://stream.youtube.com/250",
            itag = 250,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 65_508L,
            expiresInSeconds = 21600L
        )

        val mediumStream = PlaybackStream(
            videoId = videoId,
            streamUrl = "https://stream.youtube.com/140",
            itag = 140,
            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
            bitrate = 130_677L,
            expiresInSeconds = 21600L
        )

        val highStream = PlaybackStream(
            videoId = videoId,
            streamUrl = "https://stream.youtube.com/251",
            itag = 251,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 160_000L,
            expiresInSeconds = 21600L,
            fallbackStreams = listOf(mediumStream, lowStream, veryLowStream)
        )

        return highStream
    }

    @Test
    fun testQualityTierCategorization() {
        val streamHigh = PlaybackStream(
            videoId = "v1",
            streamUrl = "url1",
            itag = 251,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 160000L,
            expiresInSeconds = 21600L
        )
        assertEquals(AudioQualityTier.HIGH, streamHigh.qualityTier)

        val streamHighAac = PlaybackStream(
            videoId = "v1",
            streamUrl = "url2",
            itag = 141,
            mimeType = "audio/mp4",
            bitrate = 256000L,
            expiresInSeconds = 21600L
        )
        assertEquals(AudioQualityTier.HIGH, streamHighAac.qualityTier)

        val streamMedium = PlaybackStream(
            videoId = "v1",
            streamUrl = "url3",
            itag = 140,
            mimeType = "audio/mp4",
            bitrate = 128000L,
            expiresInSeconds = 21600L
        )
        assertEquals(AudioQualityTier.MEDIUM, streamMedium.qualityTier)

        val streamLow = PlaybackStream(
            videoId = "v1",
            streamUrl = "url4",
            itag = 250,
            mimeType = "audio/webm",
            bitrate = 70000L,
            expiresInSeconds = 21600L
        )
        assertEquals(AudioQualityTier.LOW, streamLow.qualityTier)

        val streamVeryLow = PlaybackStream(
            videoId = "v1",
            streamUrl = "url5",
            itag = 249,
            mimeType = "audio/webm",
            bitrate = 49000L,
            expiresInSeconds = 21600L
        )
        assertEquals(AudioQualityTier.VERY_LOW, streamVeryLow.qualityTier)
    }

    @Test
    fun testGetAllStreamsReturnsSortedDescending() {
        val root = createSampleStreamBundle()
        val all = root.getAllStreams()

        assertEquals(4, all.size)
        // Highest bitrate first
        assertEquals(251, all[0].itag)
        assertEquals(140, all[1].itag)
        assertEquals(250, all[2].itag)
        assertEquals(249, all[3].itag)
        assertTrue(all[0].bitrate > all[1].bitrate)
        assertTrue(all[1].bitrate > all[2].bitrate)
        assertTrue(all[2].bitrate > all[3].bitrate)
    }

    @Test
    fun testGetStreamForTier() {
        val root = createSampleStreamBundle()

        val high = root.getStreamForTier(AudioQualityTier.HIGH)
        assertEquals(251, high.itag)

        val medium = root.getStreamForTier(AudioQualityTier.MEDIUM)
        assertEquals(140, medium.itag)

        val low = root.getStreamForTier(AudioQualityTier.LOW)
        assertEquals(250, low.itag)

        val veryLow = root.getStreamForTier(AudioQualityTier.VERY_LOW)
        assertEquals(249, veryLow.itag)
    }

    @Test
    fun testGetFallbackStreamProgressiveDegradation() {
        val root = createSampleStreamBundle()

        // Starting at High (160k)
        val fallback1 = root.getFallbackStream()
        assertNotNull(fallback1)
        assertEquals(140, fallback1!!.itag) // drops to Medium (130k)

        // From Medium (130k)
        val fallback2 = root.copy(bitrate = fallback1.bitrate).getFallbackStream()
        assertNotNull(fallback2)
        assertEquals(250, fallback2!!.itag) // drops to Low (65k)

        // From Low (65k)
        val fallback3 = root.copy(bitrate = fallback2.bitrate).getFallbackStream()
        assertNotNull(fallback3)
        assertEquals(249, fallback3!!.itag) // drops to Very Low (49k)

        // At lowest tier (49k), no further fallback exists
        val fallback4 = root.copy(bitrate = fallback3.bitrate).getFallbackStream()
        assertNull(fallback4)
    }

    @Test
    fun testStreamQualityEnumProperties() {
        assertEquals("auto", StreamQuality.AUTO.id)
        assertNotNull(StreamQuality.AUTO.badge)
        assertEquals("high", StreamQuality.HIGH.id)
        assertEquals("medium", StreamQuality.MEDIUM.id)
        assertEquals("low", StreamQuality.LOW.id)
        assertEquals("very_low", StreamQuality.VERY_LOW.id)
    }

    @Test
    fun testFallbackStreamsJsonSerializationRoundTrip() {
        val original = createSampleStreamBundle()
        val gson = com.google.gson.Gson()
        val json = gson.toJson(original.fallbackStreams)

        assertNotNull(json)
        assertTrue(json.contains("\"itag\":140"))
        assertTrue(json.contains("\"itag\":250"))
        assertTrue(json.contains("\"itag\":249"))

        val type = object : com.google.gson.reflect.TypeToken<List<PlaybackStream>>() {}.type
        val restored: List<PlaybackStream> = gson.fromJson(json, type)

        assertEquals(3, restored.size)
        assertEquals(140, restored[0].itag)
        assertEquals(250, restored[1].itag)
        assertEquals(249, restored[2].itag)
        assertEquals(AudioQualityTier.MEDIUM, restored[0].qualityTier)
        assertEquals(AudioQualityTier.LOW, restored[1].qualityTier)
        assertEquals(AudioQualityTier.VERY_LOW, restored[2].qualityTier)
    }

    @Test
    fun testNetworkCoverageLevelRecommendation() {
        assertEquals("High Coverage (WiFi / 5G)", NetworkCoverageLevel.EXCELLENT.displayName)
        assertEquals("Good Coverage (4G LTE)", NetworkCoverageLevel.GOOD.displayName)
        assertEquals("Moderate Coverage (3G / Weak Signal)", NetworkCoverageLevel.MODERATE.displayName)
        assertEquals("Low Coverage (2G / Fringe Signal)", NetworkCoverageLevel.LOW.displayName)
    }
}
