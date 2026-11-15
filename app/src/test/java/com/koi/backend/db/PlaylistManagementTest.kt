package com.koi.backend.db

import com.google.gson.Gson
import com.koi.backend.innertube.SongItem
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PlaylistManagementTest {

    private val gson = Gson()

    @Test
    fun testLocalPlaylistInfo_properties() {
        val now = System.currentTimeMillis() / 1000
        val info = LocalPlaylistInfo(
            id = "pl_123",
            title = "Chill Vibes",
            trackCount = 5,
            coverUrl = "https://example.com/cover.jpg",
            createdAt = now,
            updatedAt = now
        )

        assertEquals("pl_123", info.id)
        assertEquals("Chill Vibes", info.title)
        assertEquals(5, info.trackCount)
        assertEquals("https://example.com/cover.jpg", info.coverUrl)
    }

    @Test
    fun testTrackToSongItemConversion_andJsonRoundTrip() {
        val track = Track(
            id = "vid_999",
            title = "Nightcall",
            artist = "Kavinsky",
            album = "OutRun",
            durationSeconds = 259,
            thumbnailUrl = "https://img.youtube.com/vi/vid_999/hqdefault.jpg"
        )

        val songItem = SongItem(
            videoId = track.id,
            title = track.title,
            artists = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            thumbnailUrl = track.thumbnailUrl
        )

        val json = gson.toJson(songItem)
        val deserialized = gson.fromJson(json, SongItem::class.java)

        assertEquals(track.id, deserialized.videoId)
        assertEquals(track.title, deserialized.title)
        assertEquals(track.artist, deserialized.artists)
        assertEquals(track.album, deserialized.album)
        assertEquals(track.durationSeconds, deserialized.durationSeconds)
        assertEquals(track.thumbnailUrl, deserialized.thumbnailUrl)
    }

    @Test
    fun testCreatePlaylistAndAddTrack_simulation() {
        // Simulates the ViewModel flow: create playlist + add track
        val playlistMap = mutableMapOf<String, String>() // id to title
        val playlistTracks = mutableMapOf<String, MutableList<SongItem>>()

        fun createPlaylist(title: String): String {
            val id = UUID.randomUUID().toString()
            playlistMap[id] = title.trim()
            playlistTracks[id] = mutableListOf()
            return id
        }

        fun addTrackToPlaylist(playlistId: String, song: SongItem) {
            val list = playlistTracks.getOrPut(playlistId) { mutableListOf() }
            list.removeAll { it.videoId == song.videoId }
            list.add(song)
        }

        fun createPlaylistAndAddTrack(title: String, song: SongItem): String {
            val trimmed = title.trim()
            val id = createPlaylist(trimmed)
            addTrackToPlaylist(id, song)
            return id
        }

        val song = SongItem(
            videoId = "v100",
            title = "Midnight City",
            artists = "M83",
            durationSeconds = 243
        )

        val playlistId = createPlaylistAndAddTrack("Synthwave Favorites", song)

        assertEquals("Synthwave Favorites", playlistMap[playlistId])
        assertEquals(1, playlistTracks[playlistId]?.size)
        assertEquals("v100", playlistTracks[playlistId]?.first()?.videoId)
        assertEquals("Midnight City", playlistTracks[playlistId]?.first()?.title)
    }
}
