package com.koi.backend.db

import com.google.gson.Gson
import com.koi.backend.innertube.SongItem
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import com.koi.ui.viewmodel.ActiveDownloadState
import org.junit.Assert.*
import org.junit.Test

class DownloadManagerTest {

    private val gson = Gson()

    @Test
    fun testActiveDownloadState_initialAndProgress() {
        val initial = ActiveDownloadState()
        assertFalse(initial.isDownloading)
        assertEquals(0, initial.completedCount)
        assertEquals(0, initial.totalCount)
        assertEquals(0f, initial.progressFraction, 0.001f)

        val inProgress = ActiveDownloadState(
            isDownloading = true,
            playlistId = "pl_synth",
            playlistTitle = "Synthwave",
            currentItemTitle = "Nightcall",
            completedCount = 3,
            totalCount = 10,
            progressFraction = 0.3f
        )

        assertTrue(inProgress.isDownloading)
        assertEquals("pl_synth", inProgress.playlistId)
        assertEquals("Synthwave", inProgress.playlistTitle)
        assertEquals("Nightcall", inProgress.currentItemTitle)
        assertEquals(3, inProgress.completedCount)
        assertEquals(10, inProgress.totalCount)
        assertEquals(0.3f, inProgress.progressFraction, 0.001f)
    }

    @Test
    fun testTrackIsDownloadedFlag_defaultAndExplicit() {
        val defaultTrack = Track(
            id = "t1",
            title = "Starboy",
            artist = "The Weeknd"
        )
        assertFalse(defaultTrack.isDownloaded)

        val downloadedTrack = defaultTrack.copy(isDownloaded = true)
        assertTrue(downloadedTrack.isDownloaded)

        val songItem = SongItem(
            videoId = "t2",
            title = "Blinding Lights",
            artists = "The Weeknd"
        )
        val trackFromSong = Track.fromSongItem(songItem, isLiked = true, isDownloaded = true)
        assertTrue(trackFromSong.isLiked)
        assertTrue(trackFromSong.isDownloaded)
    }

    @Test
    fun testDownloadedPlaylistSerializationRoundTrip() {
        val songs = listOf(
            SongItem("s1", "Song One", "Artist A", durationSeconds = 180),
            SongItem("s2", "Song Two", "Artist B", durationSeconds = 210),
            SongItem("s3", "Song Three", "Artist C", durationSeconds = 240)
        )

        val playlist = Playlist(
            id = "pl_offline_1",
            title = "Offline Hits",
            description = "Downloaded for flights",
            trackCount = songs.size,
            creator = "Koi User",
            tracks = songs.map { Track.fromSongItem(it, isDownloaded = true) }
        )

        assertEquals("pl_offline_1", playlist.id)
        assertEquals(3, playlist.tracks.size)
        assertTrue(playlist.tracks.all { it.isDownloaded })

        // Validate JSON serialization of tracks in playlist
        val songsJson = songs.map { gson.toJson(it) }
        assertEquals(3, songsJson.size)

        val restoredSongs = songsJson.map { gson.fromJson(it, SongItem::class.java) }
        assertEquals(songs.map { it.videoId }, restoredSongs.map { it.videoId })
        assertEquals(songs.map { it.title }, restoredSongs.map { it.title })
    }

    @Test
    fun testDownloadManagerSimulation_addAndRemovePlaylists() {
        val downloadedPlaylists = mutableMapOf<String, Playlist>()
        val downloadedTracks = mutableMapOf<String, MutableList<SongItem>>()
        val downloadedSongs = mutableMapOf<String, SongItem>()

        fun saveDownloadedPlaylist(pl: Playlist, tracks: List<SongItem>) {
            downloadedPlaylists[pl.id] = pl
            downloadedTracks[pl.id] = tracks.toMutableList()
            tracks.forEach { song ->
                downloadedSongs[song.videoId] = song
            }
        }

        fun removeDownloadedPlaylist(playlistId: String, removeSongs: Boolean) {
            downloadedPlaylists.remove(playlistId)
            val tracks = downloadedTracks.remove(playlistId) ?: emptyList()
            if (removeSongs) {
                tracks.forEach { track ->
                    val inOtherPlaylist = downloadedTracks.values.any { list ->
                        list.any { it.videoId == track.videoId }
                    }
                    if (!inOtherPlaylist) {
                        downloadedSongs.remove(track.videoId)
                    }
                }
            }
        }

        val s1 = SongItem("v1", "Song 1", "Artist 1")
        val s2 = SongItem("v2", "Song 2", "Artist 2")
        val s3 = SongItem("v3", "Song 3", "Artist 3")

        val p1 = Playlist(id = "p1", title = "Playlist 1")
        val p2 = Playlist(id = "p2", title = "Playlist 2")

        saveDownloadedPlaylist(p1, listOf(s1, s2))
        saveDownloadedPlaylist(p2, listOf(s2, s3))

        assertEquals(2, downloadedPlaylists.size)
        assertEquals(3, downloadedSongs.size) // v1, v2, v3

        // Removing p1 should remove v1, but keep v2 because v2 is also in p2
        removeDownloadedPlaylist("p1", removeSongs = true)
        assertEquals(1, downloadedPlaylists.size)
        assertTrue(downloadedPlaylists.containsKey("p2"))
        assertFalse(downloadedSongs.containsKey("v1"))
        assertTrue(downloadedSongs.containsKey("v2"))
        assertTrue(downloadedSongs.containsKey("v3"))

        // Removing p2 removes remaining
        removeDownloadedPlaylist("p2", removeSongs = true)
        assertEquals(0, downloadedPlaylists.size)
        assertEquals(0, downloadedSongs.size)
    }
}
