package com.koi.backend.download

import com.koi.ui.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DownloaderEngineTest {

    @Test
    fun testDownloadProgressTransitions() {
        val p1 = DownloadProgress(
            videoId = "v1",
            title = "Starboy",
            status = DownloadStatus.RESOLVING,
            statusMessage = "Resolving high-quality stream..."
        )
        assertEquals("v1", p1.videoId)
        assertEquals(DownloadStatus.RESOLVING, p1.status)
        assertEquals(0f, p1.progressFraction, 0.001f)

        val p2 = DownloadProgress(
            videoId = "v1",
            title = "Starboy",
            status = DownloadStatus.DOWNLOADING,
            bytesDownloaded = 1_500_000L,
            totalBytes = 3_000_000L,
            progressFraction = 0.5f,
            statusMessage = "Downloading: 50%"
        )
        assertEquals(DownloadStatus.DOWNLOADING, p2.status)
        assertEquals(0.5f, p2.progressFraction, 0.001f)
        assertEquals(1_500_000L, p2.bytesDownloaded)

        val p3 = DownloadProgress(
            videoId = "v1",
            title = "Starboy",
            status = DownloadStatus.CONVERTING,
            progressFraction = 0.9f,
            statusMessage = "Converting audio: 50%"
        )
        assertEquals(DownloadStatus.CONVERTING, p3.status)

        val p4 = DownloadProgress(
            videoId = "v1",
            title = "Starboy",
            status = DownloadStatus.COMPLETED,
            progressFraction = 1.0f,
            statusMessage = "Download complete"
        )
        assertEquals(DownloadStatus.COMPLETED, p4.status)
        assertEquals(1.0f, p4.progressFraction, 0.001f)
    }

    @Test
    fun testChunkedRangeCalculations() {
        val totalLength = 3_500_000L // 3.5 MB
        val chunkSize = 512 * 1024 // 512 KB

        val chunkRanges = mutableListOf<Pair<Long, Long>>()
        var offset = 0L
        while (offset < totalLength) {
            val chunkEnd = (offset + chunkSize - 1).coerceAtMost(totalLength - 1)
            chunkRanges.add(offset to chunkEnd)
            offset = chunkEnd + 1
        }

        assertEquals(7, chunkRanges.size)
        assertEquals(0L to 524287L, chunkRanges.first())
        assertEquals(3145728L to 3499999L, chunkRanges.last())

        // Ensure no gaps
        for (i in 0 until chunkRanges.size - 1) {
            assertEquals(chunkRanges[i].second + 1, chunkRanges[i + 1].first)
        }
    }

    @Test
    fun testRawStreamFallbackFileHandling() {
        val tempDir = File.createTempFile("test_koi_temp", "").parentFile!!
        val rawFile = File(tempDir, "test_track.raw")
        val finalFile = File(tempDir, "test_track.m4a")

        rawFile.writeBytes(ByteArray(4096) { 0x41 })
        assertTrue(rawFile.exists())
        assertEquals(4096L, rawFile.length())

        // Simulate fallback conversion (direct copy of stream container)
        rawFile.inputStream().use { input ->
            finalFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (finalFile.exists()) {
            rawFile.delete()
        }

        assertTrue(finalFile.exists())
        assertEquals(4096L, finalFile.length())
        assertFalse(rawFile.exists())

        finalFile.delete()
    }
}
