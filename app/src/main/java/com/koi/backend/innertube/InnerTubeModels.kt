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

data class PlaybackStream(
    val videoId: String,
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Long,
    val expiresInSeconds: Long,
    val loudnessDb: Double? = null,
    val userAgent: String? = null,
    val clientName: String = "VISIONOS"
)

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
    val tracks: List<SongItem> = emptyList()
)
