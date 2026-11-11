package com.koi.backend.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.koi.backend.innertube.ArtistItem
import com.koi.backend.innertube.PlaybackStream
import com.koi.backend.innertube.PlaylistItem
import com.koi.backend.innertube.SongItem
import com.koi.backend.lyrics.Lyrics
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import java.io.File
import java.util.UUID

data class LocalPlaylistInfo(
    val id: String,
    val title: String,
    val trackCount: Int,
    val coverUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val description: String? = null
)

data class SearchHistoryRecord(
    val id: String,
    val type: String,
    val queryText: String? = null,
    val songItem: SongItem? = null,
    val artistItem: ArtistItem? = null,
    val playlistItem: PlaylistItem? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class KoiDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "koi_music.db"
        const val DATABASE_VERSION = 1
        private val gson = Gson()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_playlists (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                cover_url TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_playlist_tracks (
                playlist_id TEXT NOT NULL,
                video_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                song_json TEXT NOT NULL,
                PRIMARY KEY (playlist_id, video_id)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS liked_songs (
                video_id TEXT PRIMARY KEY,
                song_json TEXT NOT NULL,
                liked_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stream_url_cache (
                video_id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                itag INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                loudness_db REAL,
                user_agent TEXT,
                client_name TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                video_id TEXT PRIMARY KEY,
                lyrics TEXT,
                fetched_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                played_at INTEGER NOT NULL,
                song_json TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_songs (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                file_path TEXT NOT NULL,
                downloaded_at INTEGER NOT NULL,
                song_json TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_playlists (
                playlist_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                creator TEXT,
                thumbnail_url TEXT,
                downloaded_at INTEGER NOT NULL,
                track_count INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_playlist_tracks (
                playlist_id TEXT NOT NULL,
                video_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                song_json TEXT NOT NULL,
                PRIMARY KEY (playlist_id, video_id)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_audio_settings (
                video_id TEXT PRIMARY KEY,
                settings_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_presets (
                name TEXT PRIMARY KEY,
                config_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                query_text TEXT,
                song_json TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic if needed
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE stream_url_cache ADD COLUMN user_agent TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE stream_url_cache ADD COLUMN client_name TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE local_playlists ADD COLUMN description TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE local_playlists ADD COLUMN cover_url TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS search_history (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    query_text TEXT,
                    song_json TEXT,
                    timestamp INTEGER NOT NULL
                );
                """.trimIndent()
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS downloaded_playlists (
                    playlist_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    creator TEXT,
                    thumbnail_url TEXT,
                    downloaded_at INTEGER NOT NULL,
                    track_count INTEGER NOT NULL
                );
                """.trimIndent()
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS downloaded_playlist_tracks (
                    playlist_id TEXT NOT NULL,
                    video_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    song_json TEXT NOT NULL,
                    PRIMARY KEY (playlist_id, video_id)
                );
                """.trimIndent()
            )
        } catch (_: Exception) {}
    }

    // --- PLAYLIST MANAGEMENT (matching Ryotunes local_playlists) ---

    fun createPlaylist(title: String, description: String? = null, coverUrl: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", id)
            put("title", title)
            if (description != null) put("description", description)
            if (coverUrl != null) put("cover_url", coverUrl)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertWithOnConflict("local_playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return id
    }

    fun updatePlaylist(playlistId: String, newTitle: String, newDescription: String? = null, newCoverUrl: String? = null) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", newTitle)
            if (newDescription != null) put("description", newDescription)
            if (newCoverUrl != null) put("cover_url", newCoverUrl)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        db.update("local_playlists", cv, "id = ?", arrayOf(playlistId))

        // Also update downloaded_playlists if this playlist is downloaded
        val plCv = ContentValues().apply {
            put("title", newTitle)
            if (newDescription != null) put("description", newDescription)
            if (newCoverUrl != null) put("thumbnail_url", newCoverUrl)
        }
        db.update("downloaded_playlists", plCv, "playlist_id = ?", arrayOf(playlistId))
    }

    fun renamePlaylist(playlistId: String, newTitle: String) {
        updatePlaylist(playlistId, newTitle)
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("cover_url", coverUri)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        db.update("local_playlists", cv, "id = ?", arrayOf(playlistId))
        val plCv = ContentValues().apply {
            put("thumbnail_url", coverUri)
        }
        db.update("downloaded_playlists", plCv, "playlist_id = ?", arrayOf(playlistId))
    }

    fun getPlaylistInfo(playlistId: String): LocalPlaylistInfo? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT p.id, p.title, p.created_at, p.updated_at, COUNT(t.video_id) AS track_count, p.description, p.cover_url
            FROM local_playlists p
            LEFT JOIN local_playlist_tracks t ON p.id = t.playlist_id
            WHERE p.id = ?
            GROUP BY p.id
            """.trimIndent(),
            arrayOf(playlistId)
        )
        var info: LocalPlaylistInfo? = null
        if (cursor.moveToFirst()) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val createdAt = cursor.getLong(2)
            val updatedAt = cursor.getLong(3)
            val trackCount = cursor.getInt(4)
            val description = if (cursor.isNull(5)) null else cursor.getString(5)
            val customCover = if (cursor.isNull(6)) null else cursor.getString(6)

            var coverUrl = customCover
            if (coverUrl.isNullOrEmpty()) {
                val coverCursor = db.rawQuery(
                    "SELECT song_json FROM local_playlist_tracks WHERE playlist_id = ? ORDER BY position ASC LIMIT 1",
                    arrayOf(id)
                )
                if (coverCursor.moveToFirst()) {
                    val json = coverCursor.getString(0)
                    try {
                        val s = gson.fromJson(json, SongItem::class.java)
                        coverUrl = s.thumbnailUrl
                    } catch (_: Exception) {}
                }
                coverCursor.close()
            }
            info = LocalPlaylistInfo(id, title, trackCount, coverUrl, createdAt, updatedAt, description)
        }
        cursor.close()
        return info
    }

    fun deletePlaylist(playlistId: String) {
        val db = writableDatabase
        db.delete("local_playlists", "id = ?", arrayOf(playlistId))
        db.delete("local_playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
    }

    fun addTrackToPlaylist(playlistId: String, song: SongItem) {
        val db = writableDatabase
        val countCursor = db.rawQuery(
            "SELECT COUNT(*) FROM local_playlist_tracks WHERE playlist_id = ?",
            arrayOf(playlistId)
        )
        val pos = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
        countCursor.close()

        val cv = ContentValues().apply {
            put("playlist_id", playlistId)
            put("video_id", song.videoId)
            put("position", pos)
            put("song_json", gson.toJson(song))
        }
        db.insertWithOnConflict("local_playlist_tracks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        // Update playlist updated_at
        val updateCv = ContentValues().apply {
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        db.update("local_playlists", updateCv, "id = ?", arrayOf(playlistId))
    }

    fun removeTrackFromPlaylist(playlistId: String, videoId: String) {
        val db = writableDatabase
        db.delete(
            "local_playlist_tracks",
            "playlist_id = ? AND video_id = ?",
            arrayOf(playlistId, videoId)
        )
        val updateCv = ContentValues().apply {
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        db.update("local_playlists", updateCv, "id = ?", arrayOf(playlistId))
    }

    fun removeTracksFromPlaylist(playlistId: String, videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (videoId in videoIds) {
                db.delete(
                    "local_playlist_tracks",
                    "playlist_id = ? AND video_id = ?",
                    arrayOf(playlistId, videoId)
                )
            }
            val updateCv = ContentValues().apply {
                put("updated_at", System.currentTimeMillis() / 1000)
            }
            db.update("local_playlists", updateCv, "id = ?", arrayOf(playlistId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun removeLikedSongs(videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (videoId in videoIds) {
                db.delete("liked_songs", "video_id = ?", arrayOf(videoId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPlaylistTracks(playlistId: String): List<SongItem> {
        val db = readableDatabase
        val list = mutableListOf<SongItem>()
        val cursor = db.rawQuery(
            "SELECT song_json FROM local_playlist_tracks WHERE playlist_id = ? ORDER BY position ASC",
            arrayOf(playlistId)
        )
        while (cursor.moveToNext()) {
            val json = cursor.getString(0)
            try {
                val song = gson.fromJson(json, SongItem::class.java)
                if (song != null) list.add(song)
            } catch (e: Exception) {
                // ignore malformed
            }
        }
        cursor.close()
        return list
    }

    fun getAllPlaylists(): List<LocalPlaylistInfo> {
        val db = readableDatabase
        val list = mutableListOf<LocalPlaylistInfo>()
        val cursor = db.rawQuery(
            """
            SELECT p.id, p.title, p.created_at, p.updated_at, COUNT(t.video_id) AS track_count, p.description, p.cover_url
            FROM local_playlists p
            LEFT JOIN local_playlist_tracks t ON p.id = t.playlist_id
            GROUP BY p.id
            ORDER BY p.updated_at DESC
            """.trimIndent(),
            null
        )
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val createdAt = cursor.getLong(2)
            val updatedAt = cursor.getLong(3)
            val trackCount = cursor.getInt(4)
            val description = if (cursor.isNull(5)) null else cursor.getString(5)
            val customCover = if (cursor.isNull(6)) null else cursor.getString(6)

            // Get cover of first track if customCover is null
            var coverUrl: String? = customCover
            if (coverUrl.isNullOrEmpty()) {
                val coverCursor = db.rawQuery(
                    "SELECT song_json FROM local_playlist_tracks WHERE playlist_id = ? ORDER BY position ASC LIMIT 1",
                    arrayOf(id)
                )
                if (coverCursor.moveToFirst()) {
                    val json = coverCursor.getString(0)
                    try {
                        val s = gson.fromJson(json, SongItem::class.java)
                        coverUrl = s.thumbnailUrl
                    } catch (e: Exception) { }
                }
                coverCursor.close()
            }

            list.add(
                LocalPlaylistInfo(
                    id = id,
                    title = title,
                    trackCount = trackCount,
                    coverUrl = coverUrl,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    description = description
                )
            )
        }
        cursor.close()
        return list
    }

    // --- LIKED SONGS ---

    fun toggleLike(song: SongItem): Boolean {
        val db = writableDatabase
        val isCurrentlyLiked = isLiked(song.videoId)
        return if (isCurrentlyLiked) {
            db.delete("liked_songs", "video_id = ?", arrayOf(song.videoId))
            false
        } else {
            val cv = ContentValues().apply {
                put("video_id", song.videoId)
                put("song_json", gson.toJson(song))
                put("liked_at", System.currentTimeMillis() / 1000)
            }
            db.insertWithOnConflict("liked_songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    fun isLiked(videoId: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM liked_songs WHERE video_id = ?", arrayOf(videoId))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getLikedSongs(): List<SongItem> {
        val db = readableDatabase
        val list = mutableListOf<SongItem>()
        val cursor = db.rawQuery("SELECT song_json FROM liked_songs ORDER BY liked_at DESC", null)
        while (cursor.moveToNext()) {
            val json = cursor.getString(0)
            try {
                val song = gson.fromJson(json, SongItem::class.java)
                if (song != null) list.add(song)
            } catch (e: Exception) { }
        }
        cursor.close()
        return list
    }

    // --- STREAM URL CACHING ---

    fun getCachedStream(videoId: String): PlaybackStream? {
        val now = System.currentTimeMillis() / 1000
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT url, itag, expires_at, loudness_db, user_agent, client_name FROM stream_url_cache WHERE video_id = ? AND expires_at > ?",
            arrayOf(videoId, now.toString())
        )
        var stream: PlaybackStream? = null
        if (cursor.moveToFirst()) {
            val url = cursor.getString(0)
            val itag = cursor.getInt(1)
            val expiresAt = cursor.getLong(2)
            val loudnessDb = if (cursor.isNull(3)) null else cursor.getDouble(3)
            val userAgent = if (cursor.columnCount > 4 && !cursor.isNull(4)) cursor.getString(4) else null
            val clientName = if (cursor.columnCount > 5 && !cursor.isNull(5)) cursor.getString(5) else null
            // Quality gate: Only serve cached streams if they are top-tier formats (itag 251 Opus 160k+ or itag 141 AAC 256k)
            // Any lower-bitrate format forces a fresh online resolution to guarantee the best quality
            if (itag == 251 || itag == 141) {
                stream = PlaybackStream(
                    videoId = videoId,
                    streamUrl = url,
                    itag = itag,
                    mimeType = if (itag == 251) "audio/webm" else "audio/mp4",
                    bitrate = if (itag == 251) 160000L else 256000L,
                    expiresInSeconds = expiresAt - now,
                    loudnessDb = loudnessDb,
                    userAgent = userAgent,
                    clientName = clientName ?: "VISIONOS"
                )
            }
        }
        cursor.close()
        return stream
    }

    fun cacheStream(stream: PlaybackStream) {
        val now = System.currentTimeMillis() / 1000
        val expiresAt = now + stream.expiresInSeconds
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("video_id", stream.videoId)
            put("url", stream.streamUrl)
            put("itag", stream.itag)
            put("expires_at", expiresAt)
            put("loudness_db", stream.loudnessDb)
            put("user_agent", stream.userAgent)
            put("client_name", stream.clientName)
        }
        db.insertWithOnConflict("stream_url_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun invalidateStream(videoId: String) {
        val db = writableDatabase
        db.delete("stream_url_cache", "video_id = ?", arrayOf(videoId))
    }

    fun cleanExpiredStreams() {
        val now = System.currentTimeMillis() / 1000
        val db = writableDatabase
        db.delete("stream_url_cache", "expires_at <= ?", arrayOf(now.toString()))
    }

    fun clearStreamCache() {
        val db = writableDatabase
        db.delete("stream_url_cache", null, null)
    }

    // --- LYRICS CACHING ---

    fun getCachedLyrics(videoId: String): Lyrics? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT lyrics FROM lyrics_cache WHERE video_id = ?",
            arrayOf(videoId)
        )
        var lyrics: Lyrics? = null
        if (cursor.moveToFirst()) {
            val json = cursor.getString(0)
            try {
                lyrics = gson.fromJson(json, Lyrics::class.java)
            } catch (e: Exception) { }
        }
        cursor.close()
        return lyrics
    }

    fun cacheLyrics(videoId: String, lyrics: Lyrics) {
        val now = System.currentTimeMillis() / 1000
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("video_id", videoId)
            put("lyrics", gson.toJson(lyrics))
            put("fetched_at", now)
        }
        db.insertWithOnConflict("lyrics_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- PLAY HISTORY ---

    fun recordPlay(song: SongItem) {
        val now = System.currentTimeMillis() / 1000
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("video_id", song.videoId)
            put("played_at", now)
            put("song_json", gson.toJson(song))
        }
        db.insert("plays", null, cv)
    }

    fun getRecentPlays(limit: Int = 20): List<SongItem> {
        val db = readableDatabase
        val list = mutableListOf<SongItem>()
        val seen = mutableSetOf<String>()
        val cursor = db.rawQuery(
            "SELECT video_id, song_json FROM plays ORDER BY played_at DESC LIMIT ?",
            arrayOf((limit * 2).toString())
        )
        while (cursor.moveToNext() && list.size < limit) {
            val videoId = cursor.getString(0)
            if (seen.add(videoId)) {
                val json = cursor.getString(1)
                try {
                    val s = gson.fromJson(json, SongItem::class.java)
                    if (s != null) list.add(s)
                } catch (e: Exception) { }
            }
        }
        cursor.close()
        return list
    }

    // --- MONITORING STATS ---

    fun getTotalPlaysCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM plays", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getLikedSongsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM liked_songs", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getPlaylistsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM local_playlists", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getCachedStreamsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM stream_url_cache", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getCachedLyricsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM lyrics_cache", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun clearAllCache() {
        val db = writableDatabase
        db.delete("stream_url_cache", null, null)
        db.delete("lyrics_cache", null, null)
    }

    fun clearLyricsCache() {
        val db = writableDatabase
        db.delete("lyrics_cache", null, null)
    }

    // --- DOWNLOADS MANAGEMENT ---

    fun isDownloaded(videoId: String): Boolean {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val cursor = db.rawQuery("SELECT file_path FROM downloaded_songs WHERE video_id = ?", arrayOf(videoId))
        var exists = false
        if (cursor.moveToFirst()) {
            val path = cursor.getString(0)
            if (path != null && File(path).exists() && File(path).length() > 0) {
                exists = true
            }
        }
        cursor.close()
        return exists
    }

    fun getDownloadedFilePath(videoId: String): String? {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val cursor = db.rawQuery("SELECT file_path FROM downloaded_songs WHERE video_id = ?", arrayOf(videoId))
        var path: String? = null
        if (cursor.moveToFirst()) {
            val p = cursor.getString(0)
            if (p != null && File(p).exists() && File(p).length() > 0) {
                path = p
            }
        }
        cursor.close()
        return path
    }

    fun saveDownload(song: SongItem, filePath: String) {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val now = System.currentTimeMillis() / 1000
        val cv = ContentValues().apply {
            put("video_id", song.videoId)
            put("title", song.title)
            put("artist", song.artists)
            put("file_path", filePath)
            put("downloaded_at", now)
            put("song_json", gson.toJson(song))
        }
        db.insertWithOnConflict("downloaded_songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeDownload(videoId: String) {
        val db = writableDatabase
        db.delete("downloaded_songs", "video_id = ?", arrayOf(videoId))
    }

    fun getDownloadedSongs(): List<SongItem> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val list = mutableListOf<SongItem>()
        val cursor = db.rawQuery("SELECT song_json, file_path FROM downloaded_songs ORDER BY downloaded_at DESC", null)
        while (cursor.moveToNext()) {
            val json = cursor.getString(0)
            val path = cursor.getString(1)
            if (path != null && File(path).exists() && File(path).length() > 0) {
                try {
                    val song = gson.fromJson(json, SongItem::class.java)
                    if (song != null) list.add(song)
                } catch (e: Exception) { }
            }
        }
        cursor.close()
        return list
    }

    // --- DOWNLOADED PLAYLISTS MANAGEMENT ---

    fun saveDownloadedPlaylist(playlist: Playlist, songs: List<SongItem>) {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlists (playlist_id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, creator TEXT, thumbnail_url TEXT, downloaded_at INTEGER NOT NULL, track_count INTEGER NOT NULL);")
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlist_tracks (playlist_id TEXT NOT NULL, video_id TEXT NOT NULL, position INTEGER NOT NULL, song_json TEXT NOT NULL, PRIMARY KEY (playlist_id, video_id));")
        val now = System.currentTimeMillis() / 1000
        val cv = ContentValues().apply {
            put("playlist_id", playlist.id)
            put("title", playlist.title)
            put("description", playlist.description)
            put("creator", playlist.creator)
            put("thumbnail_url", playlist.thumbnailUrl)
            put("downloaded_at", now)
            put("track_count", songs.size)
        }
        db.insertWithOnConflict("downloaded_playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        db.delete("downloaded_playlist_tracks", "playlist_id = ?", arrayOf(playlist.id))
        songs.forEachIndexed { index, song ->
            val trackCv = ContentValues().apply {
                put("playlist_id", playlist.id)
                put("video_id", song.videoId)
                put("position", index)
                put("song_json", gson.toJson(song))
            }
            db.insertWithOnConflict("downloaded_playlist_tracks", null, trackCv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun isPlaylistDownloaded(playlistId: String): Boolean {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlists (playlist_id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, creator TEXT, thumbnail_url TEXT, downloaded_at INTEGER NOT NULL, track_count INTEGER NOT NULL);")
        val cursor = db.rawQuery("SELECT 1 FROM downloaded_playlists WHERE playlist_id = ?", arrayOf(playlistId))
        val inTable = cursor.count > 0
        cursor.close()
        if (!inTable) return false
        val tracks = getDownloadedPlaylistTracks(playlistId)
        if (tracks.isEmpty()) return false
        return tracks.all { isDownloaded(it.videoId) }
    }

    fun getDownloadedPlaylists(): List<Playlist> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlists (playlist_id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, creator TEXT, thumbnail_url TEXT, downloaded_at INTEGER NOT NULL, track_count INTEGER NOT NULL);")
        val list = mutableListOf<Playlist>()
        val cursor = db.rawQuery(
            "SELECT playlist_id, title, description, creator, thumbnail_url, track_count FROM downloaded_playlists ORDER BY downloaded_at DESC",
            null
        )
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val description = cursor.getString(2) ?: ""
            val creator = cursor.getString(3) ?: "Koi"
            val thumbnailUrl = cursor.getString(4)
            val trackCount = cursor.getInt(5)

            val songs = getDownloadedPlaylistTracks(id)
            val tracks = songs.map { song ->
                Track.fromSongItem(song, isLiked = isLiked(song.videoId), isDownloaded = true)
            }

            list.add(
                Playlist(
                    id = id,
                    title = title,
                    description = description,
                    creator = creator,
                    thumbnailUrl = thumbnailUrl,
                    trackCount = if (trackCount > 0) trackCount else tracks.size,
                    tracks = tracks
                )
            )
        }
        cursor.close()
        return list
    }

    fun getDownloadedPlaylistTracks(playlistId: String): List<SongItem> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlist_tracks (playlist_id TEXT NOT NULL, video_id TEXT NOT NULL, position INTEGER NOT NULL, song_json TEXT NOT NULL, PRIMARY KEY (playlist_id, video_id));")
        val list = mutableListOf<SongItem>()
        val cursor = db.rawQuery(
            "SELECT song_json FROM downloaded_playlist_tracks WHERE playlist_id = ? ORDER BY position ASC",
            arrayOf(playlistId)
        )
        while (cursor.moveToNext()) {
            val json = cursor.getString(0)
            try {
                val song = gson.fromJson(json, SongItem::class.java)
                if (song != null) list.add(song)
            } catch (_: Exception) {}
        }
        cursor.close()
        return list
    }

    fun removeDownloadedPlaylist(playlistId: String, deleteAudioFiles: Boolean = true, context: Context? = null) {
        val db = writableDatabase
        if (deleteAudioFiles && context != null) {
            val tracks = getDownloadedPlaylistTracks(playlistId)
            tracks.forEach { track ->
                val checkCursor = db.rawQuery(
                    "SELECT COUNT(*) FROM downloaded_playlist_tracks WHERE video_id = ? AND playlist_id != ?",
                    arrayOf(track.videoId, playlistId)
                )
                val count = if (checkCursor.moveToFirst()) checkCursor.getInt(0) else 0
                checkCursor.close()
                if (count == 0) {
                    val path = getDownloadedFilePath(track.videoId)
                    removeDownload(track.videoId)
                    try {
                        if (path != null) File(path).delete()
                        File(context.filesDir, "downloads/${track.videoId}.m4a").delete()
                        File(context.filesDir, "downloads/${track.videoId}.mp3").delete()
                        File(context.filesDir, "downloads/temp/${track.videoId}.raw").delete()
                    } catch (_: Exception) {}
                }
            }
        }
        db.delete("downloaded_playlists", "playlist_id = ?", arrayOf(playlistId))
        db.delete("downloaded_playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
    }

    fun getAllDownloadedVideoIds(): Set<String> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val set = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT video_id, file_path FROM downloaded_songs", null)
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val path = cursor.getString(1)
            if (path != null && File(path).exists() && File(path).length() > 0) {
                set.add(id)
            }
        }
        cursor.close()
        return set
    }

    fun getAllDownloadedPlaylistIds(): Set<String> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlists (playlist_id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, creator TEXT, thumbnail_url TEXT, downloaded_at INTEGER NOT NULL, track_count INTEGER NOT NULL);")
        val set = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT playlist_id FROM downloaded_playlists", null)
        val allIds = mutableListOf<String>()
        while (cursor.moveToNext()) {
            allIds.add(cursor.getString(0))
        }
        cursor.close()
        for (id in allIds) {
            if (isPlaylistDownloaded(id)) {
                set.add(id)
            }
        }
        return set
    }

    fun getDownloadedSongsCount(): Int {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val cursor = db.rawQuery("SELECT COUNT(*) FROM downloaded_songs", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getDownloadedPlaylistsCount(): Int {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_playlists (playlist_id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, creator TEXT, thumbnail_url TEXT, downloaded_at INTEGER NOT NULL, track_count INTEGER NOT NULL);")
        val cursor = db.rawQuery("SELECT COUNT(*) FROM downloaded_playlists", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getTotalDownloadedSizeBytes(context: Context): Long {
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) return 0L
        var totalBytes = 0L
        try {
            downloadsDir.walkTopDown().forEach { file ->
                if (file.isFile) totalBytes += file.length()
            }
        } catch (_: Exception) {}
        return totalBytes
    }

    fun clearAllDownloads(context: Context) {
        val db = writableDatabase
        db.delete("downloaded_songs", null, null)
        db.delete("downloaded_playlists", null, null)
        db.delete("downloaded_playlist_tracks", null, null)
        try {
            val downloadsDir = File(context.filesDir, "downloads")
            downloadsDir.deleteRecursively()
            downloadsDir.mkdirs()
        } catch (_: Exception) {}
    }

    // --- PER-TRACK AUDIO SETTINGS ---

    fun saveTrackSettings(videoId: String, configJson: String) {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS track_audio_settings (video_id TEXT PRIMARY KEY, settings_json TEXT NOT NULL, updated_at INTEGER NOT NULL);")
        val now = System.currentTimeMillis() / 1000
        val cv = ContentValues().apply {
            put("video_id", videoId)
            put("settings_json", configJson)
            put("updated_at", now)
        }
        db.insertWithOnConflict("track_audio_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getTrackSettings(videoId: String): String? {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS track_audio_settings (video_id TEXT PRIMARY KEY, settings_json TEXT NOT NULL, updated_at INTEGER NOT NULL);")
        val cursor = db.rawQuery("SELECT settings_json FROM track_audio_settings WHERE video_id = ?", arrayOf(videoId))
        var result: String? = null
        if (cursor.moveToFirst()) {
            result = cursor.getString(0)
        }
        cursor.close()
        return result
    }

    // --- CUSTOM PRESETS ---

    fun clearCustomPresets() {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_presets (name TEXT PRIMARY KEY, config_json TEXT NOT NULL, created_at INTEGER NOT NULL);")
        db.execSQL("DELETE FROM custom_presets")
    }

    fun saveCustomPreset(name: String, configJson: String) {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_presets (name TEXT PRIMARY KEY, config_json TEXT NOT NULL, created_at INTEGER NOT NULL);")
        val now = System.currentTimeMillis() / 1000
        val cv = ContentValues().apply {
            put("name", name)
            put("config_json", configJson)
            put("created_at", now)
        }
        db.insertWithOnConflict("custom_presets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCustomPresets(): Map<String, String> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_presets (name TEXT PRIMARY KEY, config_json TEXT NOT NULL, created_at INTEGER NOT NULL);")
        val map = mutableMapOf<String, String>()
        val cursor = db.rawQuery("SELECT name, config_json FROM custom_presets ORDER BY created_at ASC", null)
        while (cursor.moveToNext()) {
            map[cursor.getString(0)] = cursor.getString(1)
        }
        cursor.close()
        return map
    }

    fun deleteCustomPreset(name: String) {
        val db = writableDatabase
        db.delete("custom_presets", "name = ?", arrayOf(name))
    }

    // --- SEARCH HISTORY ---

    fun saveSearchHistoryTrack(song: SongItem) {
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                query_text TEXT,
                song_json TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", "track_${song.videoId}")
            put("type", "TRACK")
            putNull("query_text")
            put("song_json", gson.toJson(song))
            put("timestamp", now)
        }
        db.insertWithOnConflict("search_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        try {
            db.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 50);")
        } catch (_: Exception) {}
    }

    fun saveSearchHistoryQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                query_text TEXT,
                song_json TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", "query_${trimmed.lowercase()}")
            put("type", "QUERY")
            put("query_text", trimmed)
            putNull("song_json")
            put("timestamp", now)
        }
        db.insertWithOnConflict("search_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        try {
            db.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 50);")
        } catch (_: Exception) {}
    }

    fun saveSearchHistoryArtist(artist: ArtistItem) {
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                query_text TEXT,
                song_json TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", "artist_${artist.id}")
            put("type", "ARTIST")
            putNull("query_text")
            put("song_json", gson.toJson(artist))
            put("timestamp", now)
        }
        db.insertWithOnConflict("search_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        try {
            db.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 50);")
        } catch (_: Exception) {}
    }

    fun saveSearchHistoryPlaylist(playlist: PlaylistItem) {
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                query_text TEXT,
                song_json TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", "playlist_${playlist.id}")
            put("type", "PLAYLIST")
            putNull("query_text")
            put("song_json", gson.toJson(playlist))
            put("timestamp", now)
        }
        db.insertWithOnConflict("search_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        try {
            db.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 50);")
        } catch (_: Exception) {}
    }

    fun removeSearchHistoryItem(id: String) {
        val db = writableDatabase
        db.delete("search_history", "id = ?", arrayOf(id))
    }

    fun clearSearchHistory() {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS search_history (id TEXT PRIMARY KEY, type TEXT NOT NULL, query_text TEXT, song_json TEXT, timestamp INTEGER NOT NULL);")
        db.delete("search_history", null, null)
    }

    fun getSearchHistory(limit: Int = 20): List<SearchHistoryRecord> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS search_history (id TEXT PRIMARY KEY, type TEXT NOT NULL, query_text TEXT, song_json TEXT, timestamp INTEGER NOT NULL);")
        val list = mutableListOf<SearchHistoryRecord>()
        val cursor = db.rawQuery(
            "SELECT id, type, query_text, song_json, timestamp FROM search_history ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val type = cursor.getString(1)
            val queryText = cursor.getString(2)
            val songJson = cursor.getString(3)
            val timestamp = cursor.getLong(4)

            val songItem = if (type == "TRACK" && !songJson.isNullOrEmpty()) {
                try {
                    gson.fromJson(songJson, SongItem::class.java)
                } catch (_: Exception) {
                    null
                }
            } else null

            val artistItem = if (type == "ARTIST" && !songJson.isNullOrEmpty()) {
                try {
                    gson.fromJson(songJson, ArtistItem::class.java)
                } catch (_: Exception) {
                    null
                }
            } else null

            val playlistItem = if (type == "PLAYLIST" && !songJson.isNullOrEmpty()) {
                try {
                    gson.fromJson(songJson, PlaylistItem::class.java)
                } catch (_: Exception) {
                    null
                }
            } else null

            list.add(
                SearchHistoryRecord(
                    id = id,
                    type = type,
                    queryText = queryText,
                    songItem = songItem,
                    artistItem = artistItem,
                    playlistItem = playlistItem,
                    timestamp = timestamp
                )
            )
        }
        cursor.close()
        return list
    }

    // --- FOLLOWED ARTISTS ---

    fun toggleFollowArtist(artistId: String, name: String = "", thumbnailUrl: String? = null): Boolean {
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS followed_artists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                thumbnail_url TEXT,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        val cursor = db.rawQuery("SELECT 1 FROM followed_artists WHERE id = ?", arrayOf(artistId))
        val isFollowed = cursor.moveToFirst()
        cursor.close()

        return if (isFollowed) {
            db.delete("followed_artists", "id = ?", arrayOf(artistId))
            false
        } else {
            val cv = ContentValues().apply {
                put("id", artistId)
                put("name", name)
                put("thumbnail_url", thumbnailUrl)
                put("timestamp", System.currentTimeMillis())
            }
            db.insertWithOnConflict("followed_artists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    fun isArtistFollowed(artistId: String): Boolean {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS followed_artists (id TEXT PRIMARY KEY, name TEXT NOT NULL, thumbnail_url TEXT, timestamp INTEGER NOT NULL);")
        val cursor = db.rawQuery("SELECT 1 FROM followed_artists WHERE id = ?", arrayOf(artistId))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getFollowedArtistIds(): Set<String> {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS followed_artists (id TEXT PRIMARY KEY, name TEXT NOT NULL, thumbnail_url TEXT, timestamp INTEGER NOT NULL);")
        val set = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT id FROM followed_artists", null)
        while (cursor.moveToNext()) {
            set.add(cursor.getString(0))
        }
        cursor.close()
        return set
    }
}
