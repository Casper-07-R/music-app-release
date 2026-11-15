package com.koi.backend.innertube

data class SongItem(
    val videoId: String,
    val title: String,
    val artists: String,
    val album: String? = null,
    val duration: String? = null,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false
)

enum class StreamQuality(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String? = null
) {
    AUTO("auto", "Auto (Adaptive with Fallback)", "High quality on strong network, auto-fallbacks on low coverage to prevent tearing", "RECOMMENDED"),
    HIGH("high", "High Fidelity (160–256 kbps)", "Highest studio quality (Opus 160k / AAC 256k)", null),
    MEDIUM("medium", "Medium (128 kbps)", "Balanced sound quality & data usage (AAC 128k)", null),
    LOW("low", "Low / Resilient (65–70 kbps)", "Resilient streaming for weak signal (Opus 70k)", null),
    VERY_LOW("very_low", "Data Saver (48–50 kbps)", "Ultra-low bandwidth stream for poor network & 2G coverage", null)
}

enum class AudioQualityTier(val priority: Int, val label: String) {
    HIGH(4, "High (160–256 kbps)"),
    MEDIUM(3, "Medium (128 kbps)"),
    LOW(2, "Low (70 kbps)"),
    VERY_LOW(1, "Data Saver (50 kbps)")
}

data class PlaybackStream(
    val videoId: String,
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Long,
    val expiresInSeconds: Long,
    val loudnessDb: Double? = null,
    val userAgent: String? = null,
    val clientName: String = "VISIONOS",
    val contentLength: Long? = null,
    val fallbackStreams: List<PlaybackStream> = emptyList()
) {
    val qualityTier: AudioQualityTier
        get() = when {
            itag == 141 || (mimeType.contains("opus") && bitrate >= 130_000L) || bitrate >= 160_000L -> AudioQualityTier.HIGH
            itag == 140 || (bitrate in 90_000L..159_999L) -> AudioQualityTier.MEDIUM
            itag == 250 || (bitrate in 60_000L..89_999L) -> AudioQualityTier.LOW
            else -> AudioQualityTier.VERY_LOW
        }

    fun getAllStreams(): List<PlaybackStream> {
        val list = mutableListOf(this.copy(fallbackStreams = emptyList()))
        list.addAll(fallbackStreams.map { it.copy(fallbackStreams = emptyList()) })
        return list.distinctBy { it.itag }.sortedByDescending { it.bitrate }
    }

    fun getStreamForTier(tier: AudioQualityTier): PlaybackStream {
        val all = getAllStreams()
        return all.firstOrNull { it.qualityTier == tier }
            ?: all.minByOrNull { kotlin.math.abs(it.qualityTier.priority - tier.priority) }
            ?: this
    }

    fun getFallbackStream(): PlaybackStream? {
        val all = getAllStreams()
        val currentBitrate = this.bitrate
        return all.filter { it.bitrate < currentBitrate }.maxByOrNull { it.bitrate }
    }
}

data class NextResult(
    val items: List<SongItem>,
    val continuation: String? = null,
    val lyricsBrowseId: String? = null,
    val automixPlaylistId: String? = null
)

data class HomeSection(
    val title: String,
    val items: List<SongItem>
)

data class HomeResult(
    val chips: List<String>,
    val sections: List<HomeSection>
)

data class PlaylistResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val tracks: List<SongItem> = emptyList(),
    val isAlbum: Boolean = false
)

data class ArtistItem(
    val id: String,
    val name: String,
    val subscribersOrAudience: String? = null,
    val thumbnailUrl: String? = null
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val author: String? = null,
    val songCount: String? = null,
    val thumbnailUrl: String? = null,
    val isAlbum: Boolean = false
)

data class ArtistAlbumItem(
    val id: String,
    val title: String,
    val year: String? = null,
    val type: String = "Album",
    val thumbnailUrl: String? = null
)

data class ArtistProfile(
    val id: String,
    val name: String,
    val description: String? = null,
    val subscriberCount: String? = null,
    val monthlyAudience: String? = null,
    val headerThumbnailUrl: String? = null,
    val avatarThumbnailUrl: String? = null,
    val topSongs: List<SongItem> = emptyList(),
    val albums: List<ArtistAlbumItem> = emptyList(),
    val singles: List<ArtistAlbumItem> = emptyList(),
    val featuredIn: List<PlaylistItem> = emptyList(),
    val similarArtists: List<ArtistItem> = emptyList(),
    val bio: String? = null
)

enum class SearchFilterType(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

data class SearchTopResult(
    val type: String, // "ARTIST", "SONG", "PLAYLIST", "ALBUM"
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val songItem: SongItem? = null,
    val artistItem: ArtistItem? = null,
    val playlistItem: PlaylistItem? = null
)

data class ComprehensiveSearchResult(
    val topResult: SearchTopResult? = null,
    val songs: List<SongItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList()
)
