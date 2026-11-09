package com.koi.ui.model

import androidx.compose.ui.graphics.Color
import com.koi.backend.innertube.SongItem
import com.koi.backend.lyrics.LyricLine

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val coverGradientStart: Color = Color(0xFF133642),
    val coverGradientEnd: Color = Color(0xFF07181E),
    val thumbnailUrl: String? = null,
    val isLiked: Boolean = false,
    val lyrics: List<String> = emptyList(),
    val syncedLyrics: List<LyricLine> = emptyList()
) {
    fun toSongItem(): SongItem {
        return SongItem(
            videoId = id,
            title = title,
            artists = artist,
            album = album.ifEmpty { null },
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl
        )
    }

    companion object {
        fun fromSongItem(item: SongItem, isLiked: Boolean = false, syncedLyrics: List<LyricLine> = emptyList()): Track {
            return Track(
                id = item.videoId,
                title = item.title,
                artist = item.artists,
                album = item.album ?: "",
                durationSeconds = item.durationSeconds,
                thumbnailUrl = com.koi.backend.innertube.InnerTubeClient.getHighResThumbnailUrl(item.thumbnailUrl) ?: item.thumbnailUrl,
                isLiked = isLiked,
                lyrics = syncedLyrics.map { it.text },
                syncedLyrics = syncedLyrics
            )
        }
    }
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int = 2024,
    val trackCount: Int = 0,
    val thumbnailUrl: String? = null,
    val coverGradientStart: Color = Color(0xFF133642),
    val coverGradientEnd: Color = Color(0xFF07181E)
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String = "",
    val trackCount: Int = 0,
    val isPinned: Boolean = false,
    val isLikedSongs: Boolean = false,
    val thumbnailUrl: String? = null,
    val coverGradientStart: Color = Color(0xFF133642),
    val coverGradientEnd: Color = Color(0xFF07181E),
    val creator: String = "Koi",
    val tracks: List<Track> = emptyList()
)

data class GenreCategory(
    val id: String,
    val title: String,
    val backgroundColor: Color
)

data class QuickAccessItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val gradientStart: Color = Color(0xFF1E3264),
    val gradientEnd: Color = Color(0xFF121212),
    val isLikedSongs: Boolean = false
)

data class SoundStation(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String,
    val searchQuery: String
)

enum class NavigationTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    YOUR_LIBRARY("Your Library"),
    PROFILE("Profile")
}

enum class SearchHistoryType {
    TRACK,
    QUERY
}

data class SearchHistoryEntry(
    val id: String,
    val type: SearchHistoryType,
    val track: Track? = null,
    val query: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

