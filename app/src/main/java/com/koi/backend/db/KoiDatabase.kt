package com.koi.backend.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.koi.backend.innertube.PlaybackStream
import com.koi.backend.innertube.SongItem
import com.koi.backend.lyrics.Lyrics
import java.util.UUID

data class LocalPlaylistInfo(
    val id: String,
    val title: String,
    val trackCount: Int,
    val coverUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class SearchHistoryRecord(
    val id: String,
    val type: String,
    val queryText: String? = null,
    val songItem: SongItem? = null,
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
    }

    // --- PLAYLIST MANAGEMENT (matching Ryotunes local_playlists) ---

    fun createPlaylist(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", id)
            put("title", title)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertWithOnConflict("local_playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return id
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
            SELECT p.id, p.title, p.created_at, p.updated_at, COUNT(t.video_id) AS track_count
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

            // Get cover of first track
            var coverUrl: String? = null
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

            list.add(
                LocalPlaylistInfo(
                    id = id,
                    title = title,
                    trackCount = trackCount,
                    coverUrl = coverUrl,
                    createdAt = createdAt,
                    updatedAt = updatedAt
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

    // --- DOWNLOADS MANAGEMENT ---

    fun isDownloaded(videoId: String): Boolean {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val cursor = db.rawQuery("SELECT 1 FROM downloaded_songs WHERE video_id = ?", arrayOf(videoId))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun getDownloadedFilePath(videoId: String): String? {
        val db = readableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_songs (video_id TEXT PRIMARY KEY, title TEXT, artist TEXT, file_path TEXT, downloaded_at INTEGER, song_json TEXT);")
        val cursor = db.rawQuery("SELECT file_path FROM downloaded_songs WHERE video_id = ?", arrayOf(videoId))
        var path: String? = null
        if (cursor.moveToFirst()) {
            path = cursor.getString(0)
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
        val cursor = db.rawQuery("SELECT song_json FROM downloaded_songs ORDER BY downloaded_at DESC", null)
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

            list.add(
                SearchHistoryRecord(
                    id = id,
                    type = type,
                    queryText = queryText,
                    songItem = songItem,
                    timestamp = timestamp
                )
            )
        }
        cursor.close()
        return list
    }
}
