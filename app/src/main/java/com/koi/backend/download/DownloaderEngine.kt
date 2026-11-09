package com.koi.backend.download

import android.content.Context
import android.util.Log
import com.koi.backend.db.KoiDatabase
import com.koi.backend.innertube.InnerTubeClient
import com.koi.backend.innertube.PlaybackStream
import com.koi.ui.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class DownloaderEngine(
    private val context: Context,
    private val innerTube: InnerTubeClient,
    private val database: KoiDatabase
) {
    companion object {
        private const val TAG = "DownloaderEngine"
        private const val CHUNK_SIZE = 512 * 1024 // 512 KB per chunk for high throughput & anti-throttling
        private const val MAX_RETRIES_PER_CHUNK = 3
    }

    private val audioConverter = AudioConverter(context)

    /**
     * Resolves, downloads, extracts, and converts an audio track to high-quality offline storage.
     */
    suspend fun downloadTrack(
        track: Track,
        format: AudioFormat = AudioFormat.M4A,
        quality: AudioQuality = AudioQuality.HIGH,
        onProgress: (DownloadProgress) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val videoId = track.id
        Log.d(TAG, "Starting download pipeline for ${track.title} ($videoId) with format=$format, quality=$quality")

        onProgress(
            DownloadProgress(
                videoId = videoId,
                title = track.title,
                status = DownloadStatus.RESOLVING,
                statusMessage = "Resolving high-quality stream..."
            )
        )

        // 1. Resolve direct un-ciphered stream URL with optimal acoustic score
        var stream: PlaybackStream? = database.getCachedStream(videoId)
        if (stream == null) {
            stream = innerTube.resolveStream(videoId)
        }

        if (stream == null) {
            // Anti-bot retry with fresh visitorData
            Log.d(TAG, "First resolve attempt yielded null for $videoId, refreshing visitorData...")
            innerTube.getVisitorData(forceRefresh = true)
            stream = innerTube.resolveStream(videoId)
        }

        if (stream == null) {
            Log.e(TAG, "Failed to resolve stream for ${track.title} ($videoId)")
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.FAILED,
                    statusMessage = "Could not resolve stream from server"
                )
            )
            return@withContext null
        }

        Log.d(TAG, "Resolved stream for ${track.title}: itag=${stream.itag}, mime=${stream.mimeType}, client=${stream.clientName}")

        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val tempDir = File(context.filesDir, "downloads/temp").apply { mkdirs() }
        val rawFile = File(tempDir, "${videoId}.raw")
        val finalFile = File(downloadsDir, "${videoId}.${format.extension}")

        if (rawFile.exists()) rawFile.delete()
        val convertingTmp = File(downloadsDir, "${videoId}_converting.tmp")
        if (convertingTmp.exists()) convertingTmp.delete()

        // 2. Download the audio stream with chunked range requests (resilient against CDN throttling)
        onProgress(
            DownloadProgress(
                videoId = videoId,
                title = track.title,
                status = DownloadStatus.DOWNLOADING,
                progressFraction = 0.05f,
                statusMessage = "Downloading audio stream..."
            )
        )

        val downloadSuccess = downloadStreamToRawFile(track, stream, rawFile) { bytesDownloaded, totalBytes, frac ->
            val downloadFraction = 0.05f + (frac * 0.75f) // 5% -> 80%
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.DOWNLOADING,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    progressFraction = downloadFraction,
                    statusMessage = "Downloading: ${(frac * 100).toInt()}%"
                )
            )
        }

        if (!downloadSuccess || !rawFile.exists() || rawFile.length() < 1024) {
            Log.e(TAG, "Raw stream download failed for ${track.title}")
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.FAILED,
                    statusMessage = "Network download failed"
                )
            )
            return@withContext null
        }

        Log.d(TAG, "Downloaded ${rawFile.length()} bytes raw stream for ${track.title}. Beginning audio extraction & conversion...")

        // 3. Audio Extraction & Conversion to High-Quality offline file
        onProgress(
            DownloadProgress(
                videoId = videoId,
                title = track.title,
                status = DownloadStatus.CONVERTING,
                progressFraction = 0.82f,
                statusMessage = "Extracting & converting audio..."
            )
        )

        val convertSuccess = audioConverter.convertAudio(rawFile, finalFile, track, format, quality.bitrateKbps) { convertProgress ->
            val overallFraction = 0.82f + (convertProgress * 0.16f) // 82% -> 98%
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.CONVERTING,
                    progressFraction = overallFraction,
                    statusMessage = "Converting audio: ${(convertProgress * 100).toInt()}%"
                )
            )
        }

        if (convertSuccess && finalFile.exists() && finalFile.length() > 0) {
            database.saveDownload(track.toSongItem(), finalFile.absolutePath)
            Log.d(TAG, "Download and conversion completed for ${track.title}: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.COMPLETED,
                    bytesDownloaded = finalFile.length(),
                    totalBytes = finalFile.length(),
                    progressFraction = 1.0f,
                    statusMessage = "Download complete"
                )
            )
            finalFile
        } else {
            Log.e(TAG, "Conversion failed for ${track.title}")
            onProgress(
                DownloadProgress(
                    videoId = videoId,
                    title = track.title,
                    status = DownloadStatus.FAILED,
                    statusMessage = "Audio conversion failed"
                )
            )
            null
        }
    }

    /**
     * Downloads raw stream bytes using chunked Range requests or buffered streaming fallback.
     */
    private suspend fun downloadStreamToRawFile(
        track: Track,
        initialStream: PlaybackStream,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, fraction: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var currentStream = initialStream
        var totalLength = currentStream.contentLength?.takeIf { it > 0 } ?: probeContentLength(currentStream)

        Log.d(TAG, "Content length for ${track.title}: $totalLength bytes (fromStream=${currentStream.contentLength})")

        val raf = RandomAccessFile(targetFile, "rw")
        var downloadedBytes = 0L

        try {
            if (totalLength > 0L) {
                // Chunked Range download strategy: full speed, bypasses Google CDN throttling
                while (downloadedBytes < totalLength && isActive) {
                    val chunkEnd = (downloadedBytes + CHUNK_SIZE - 1).coerceAtMost(totalLength - 1)
                    var chunkSuccess = false
                    var retryCount = 0

                    while (!chunkSuccess && retryCount < MAX_RETRIES_PER_CHUNK && isActive) {
                        try {
                            val userAgent = currentStream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT
                            val request = Request.Builder()
                                .url(currentStream.streamUrl)
                                .header("User-Agent", userAgent)
                                .header("Range", "bytes=$downloadedBytes-$chunkEnd")
                                .header("Accept", "*/*")
                                .header("Accept-Encoding", "identity")
                                .header("Referer", "https://music.youtube.com/")
                                .build()

                            val response = innerTube.streamingOkHttpClient.newCall(request).execute()

                            if (response.code in 200..206) {
                                val body = response.body
                                if (body != null) {
                                    val bytes = body.bytes()
                                    raf.seek(downloadedBytes)
                                    raf.write(bytes)
                                    downloadedBytes += bytes.size
                                    chunkSuccess = true
                                    val frac = (downloadedBytes.toFloat() / totalLength).coerceIn(0f, 1f)
                                    onProgress(downloadedBytes, totalLength, frac)
                                }
                            } else if (response.code == 403 || response.code == 410) {
                                // URL expired, re-resolve stream URL
                                Log.w(TAG, "Stream expired with HTTP ${response.code}, refreshing URL...")
                                database.invalidateStream(track.id)
                                val refreshed = innerTube.resolveStream(track.id)
                                if (refreshed != null) {
                                    currentStream = refreshed
                                }
                            }
                            response.close()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Chunk download error at byte $downloadedBytes, retry $retryCount", e)
                        }

                        if (!chunkSuccess) {
                            retryCount++
                            delay(500L * retryCount)
                        }
                    }

                    if (!chunkSuccess) {
                        Log.e(TAG, "Max retries exceeded for chunk at byte $downloadedBytes")
                        break
                    }
                }
            }

            // Fallback: If total length was 0 or chunked download was partial, run continuous streaming GET
            if (downloadedBytes == 0L || (totalLength > 0L && downloadedBytes < totalLength)) {
                Log.d(TAG, "Attempting streaming fallback for ${track.title} from offset $downloadedBytes...")
                val userAgent = currentStream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT
                val requestBuilder = Request.Builder()
                    .url(currentStream.streamUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .header("Referer", "https://music.youtube.com/")

                if (downloadedBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                }

                val response = innerTube.streamingOkHttpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val estimatedTotal = if (totalLength > 0L) totalLength else (downloadedBytes + body.contentLength()).coerceAtLeast(3_000_000L)
                        val streamInput = body.byteStream()
                        val buffer = ByteArray(32 * 1024)
                        raf.seek(downloadedBytes)

                        while (isActive) {
                            val read = streamInput.read(buffer)
                            if (read < 0) break
                            raf.write(buffer, 0, read)
                            downloadedBytes += read
                            val frac = (downloadedBytes.toFloat() / estimatedTotal).coerceIn(0f, 1f)
                            onProgress(downloadedBytes, estimatedTotal, frac)
                        }
                    }
                }
                response.close()
            }
        } finally {
            try {
                raf.close()
            } catch (_: Exception) {}
        }

        downloadedBytes > 1024
    }

    /**
     * Probes the stream URL to obtain the exact content length via Range probe or Content-Range.
     */
    private fun probeContentLength(stream: PlaybackStream): Long {
        return try {
            val userAgent = stream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT
            val request = Request.Builder()
                .url(stream.streamUrl)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
                .header("Accept", "*/*")
                .header("Referer", "https://music.youtube.com/")
                .build()

            val response = innerTube.streamingOkHttpClient.newCall(request).execute()
            var length = -1L

            val contentRange = response.header("Content-Range")
            if (contentRange != null && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                length = totalStr.toLongOrNull() ?: -1L
            }

            if (length <= 0L) {
                val cl = response.header("Content-Length")?.toLongOrNull()
                if (cl != null && cl > 1L) {
                    length = cl
                }
            }

            response.close()
            length
        } catch (e: Exception) {
            Log.w(TAG, "Failed to probe content length: ${e.message}")
            -1L
        }
    }
}
