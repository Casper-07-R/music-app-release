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
    val clientName: String = "VISIONOS",
    val contentLength: Long? = null
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
