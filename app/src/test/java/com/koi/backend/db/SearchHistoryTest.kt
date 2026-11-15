package com.koi.backend.db

import com.google.gson.Gson
import com.koi.backend.innertube.SongItem
import com.koi.ui.model.SearchHistoryEntry
import com.koi.ui.model.SearchHistoryType
import com.koi.ui.model.Track
import org.junit.Assert.*
import org.junit.Test

class SearchHistoryTest {

    private val gson = Gson()

    @Test
    fun testSearchHistoryEntry_trackType() {
        val track = Track(
            id = "test_vid_123",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationSeconds = 230,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )
        val entry = SearchHistoryEntry(
            id = "track_${track.id}",
            type = SearchHistoryType.TRACK,
            track = track
        )

        assertEquals("track_test_vid_123", entry.id)
        assertEquals(SearchHistoryType.TRACK, entry.type)
        assertEquals("Starboy", entry.track?.title)
        assertEquals("The Weeknd", entry.track?.artist)
        assertNull(entry.query)
    }

    @Test
    fun testSearchHistoryEntry_queryType() {
        val query = "Lo-Fi Beats"
        val entry = SearchHistoryEntry(
            id = "query_${query.lowercase().trim()}",
            type = SearchHistoryType.QUERY,
            query = query
        )

        assertEquals("query_lo-fi beats", entry.id)
        assertEquals(SearchHistoryType.QUERY, entry.type)
        assertEquals("Lo-Fi Beats", entry.query)
        assertNull(entry.track)
    }

    @Test
    fun testSearchHistoryRecord_songJsonSerialization() {
        val song = SongItem(
            videoId = "vid_abc",
            title = "Blinding Lights",
            artists = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            thumbnailUrl = "https://img.youtube.com/vi/vid_abc/hqdefault.jpg"
        )

        val json = gson.toJson(song)
        val restored = gson.fromJson(json, SongItem::class.java)

        assertNotNull(restored)
        assertEquals("vid_abc", restored.videoId)
        assertEquals("Blinding Lights", restored.title)
        assertEquals("The Weeknd", restored.artists)
        assertEquals("After Hours", restored.album)
    }

    @Test
    fun testSearchHistoryDeduplication_logic() {
        val items = mutableListOf<SearchHistoryRecord>()

        fun addTrack(song: SongItem) {
            val id = "track_${song.videoId}"
            items.removeAll { it.id == id }
            items.add(0, SearchHistoryRecord(id = id, type = "TRACK", songItem = song))
        }

        fun addQuery(query: String) {
            val id = "query_${query.lowercase().trim()}"
            items.removeAll { it.id == id }
            items.add(0, SearchHistoryRecord(id = id, type = "QUERY", queryText = query.trim()))
        }

        val song1 = SongItem(videoId = "v1", title = "Song One", artists = "Artist A", durationSeconds = 180)
        val song2 = SongItem(videoId = "v2", title = "Song Two", artists = "Artist B", durationSeconds = 200)

        addTrack(song1)
        addQuery("synthwave")
        addTrack(song2)

        assertEquals(3, items.size)
        assertEquals("track_v2", items[0].id)
        assertEquals("query_synthwave", items[1].id)
        assertEquals("track_v1", items[2].id)

        // Re-adding song1 moves it to the top
        addTrack(song1)
        assertEquals(3, items.size)
        assertEquals("track_v1", items[0].id)
        assertEquals("track_v2", items[1].id)
        assertEquals("query_synthwave", items[2].id)
    }

    @Test
    fun testSearchHistoryEntry_artistAndPlaylistTypes() {
        val artist = com.koi.backend.innertube.ArtistItem(
            id = "UC_test_artist",
            name = "The Weeknd",
            subscribersOrAudience = "40M subscribers",
            thumbnailUrl = "https://example.com/theweeknd.jpg"
        )
        val artistEntry = SearchHistoryEntry(
            id = "artist_${artist.id}",
            type = SearchHistoryType.ARTIST,
            artist = artist
        )
        assertEquals(SearchHistoryType.ARTIST, artistEntry.type)
        assertEquals("The Weeknd", artistEntry.artist?.name)

        val playlist = com.koi.backend.innertube.PlaylistItem(
            id = "VLPL_test",
            title = "Top Hits",
            author = "Spotify",
            thumbnailUrl = "https://example.com/hits.jpg"
        )
        val playlistEntry = SearchHistoryEntry(
            id = "playlist_${playlist.id}",
            type = SearchHistoryType.PLAYLIST,
            playlist = playlist
        )
        assertEquals(SearchHistoryType.PLAYLIST, playlistEntry.type)
        assertEquals("Top Hits", playlistEntry.playlist?.title)
    }
}
