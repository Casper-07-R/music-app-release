package com.koi.backend.download

enum class DownloadStatus {
    IDLE,
    RESOLVING,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgress(
    val videoId: String,
    val title: String,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val statusMessage: String = "",
    val error: Throwable? = null
)

enum class AudioFormat(
    val id: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    M4A("m4a", "M4A", "m4a", "audio/mp4", "Apple & Android optimized, AAC"),
    MP3("mp3", "MP3", "mp3", "audio/mpeg", "Universal compatibility for all players"),
    OPUS("opus", "OPUS", "opus", "audio/opus", "High-efficiency modern codec"),
    FLAC("flac", "FLAC", "flac", "audio/flac", "Lossless studio fidelity, high-res")
}

enum class AudioQuality(
    val id: String,
    val displayName: String,
    val bitrateKbps: Int,
    val badge: String,
    val description: String
) {
    ULTRA("ultra", "320 kbps", 320, "STUDIO", "Pristine audio clarity for high-end audio"),
    HIGH("high", "256 kbps", 256, "RECOMMENDED", "Studio quality, optimal acoustic balance"),
    MEDIUM("medium", "192 kbps", 192, "BALANCED", "Great sound with smaller file size"),
    LOW("low", "128 kbps", 128, "COMPACT", "Smallest storage, fast download")
}

fun calculateEstimatedDownloadBytes(durationSeconds: Int, format: AudioFormat, quality: AudioQuality): Long {
    val durationSec = if (durationSeconds > 0) durationSeconds else 210
    val effectiveBitrateKbps = if (format == AudioFormat.FLAC) 850 else quality.bitrateKbps
    return ((effectiveBitrateKbps.toLong() * 1000L * durationSec) / 8L) + 32_768L
}

fun formatDownloadSize(bytes: Long): String {
    val mb = bytes.toFloat() / (1024f * 1024f)
    return if (mb < 0.1f) {
        "${(bytes / 1024).coerceAtLeast(1)} KB"
    } else {
        String.format("%.1f MB", mb)
    }
}
