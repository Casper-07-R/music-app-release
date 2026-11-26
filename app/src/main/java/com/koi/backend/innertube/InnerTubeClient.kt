package com.koi.backend.innertube

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.koi.backend.lyrics.LyricLine
import com.koi.backend.lyrics.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class InnerTubeClient(
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    val streamingOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for streaming media to prevent stalls when buffer is full
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(10, 15, TimeUnit.MINUTES))
        .build()

    companion object {
        private const val TAG = "InnerTubeClient"
        const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
        const val SW_JS_DATA_URL = "https://music.youtube.com/sw.js_data"
        const val FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val FILTER_ARTIST = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
        const val FILTER_PLAYLIST = "EgeKAQQoAEAB"
        const val FILTER_FEATURED_PLAYLIST = "EgeKAQQoADgB"
        const val FILTER_ALBUM = "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // Web client for search / next / browse
        const val WEB_CLIENT_NAME = "WEB_REMIX"
        const val WEB_CLIENT_VERSION = "1.20260213.01.00"
        const val WEB_CLIENT_ID = "67"
        const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        // Android Music client for official timed lyrics (cueRange timestamps)
        const val ANDROID_MUSIC_CLIENT_NAME = "ANDROID_MUSIC"
        const val ANDROID_MUSIC_CLIENT_VERSION = "7.29.52"
        const val ANDROID_MUSIC_CLIENT_ID = "21"
        const val ANDROID_MUSIC_USER_AGENT = "com.google.android.apps.youtube.music/7.29.52 (Linux; U; Android 14) gzip"

        // VisionOS direct un-ciphered stream client (#1 in Ryotunes STREAM_FALLBACK_ORDER)
        const val VISIONOS_CLIENT_NAME = "VISIONOS"
        const val VISIONOS_CLIENT_VERSION = "0.1"
        const val VISIONOS_CLIENT_ID = "101"
        const val VISIONOS_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"

        // Android VR 1.65 direct audio stream client (#2 in Ryotunes STREAM_FALLBACK_ORDER)
        const val VR_CLIENT_NAME = "ANDROID_VR"
        const val VR_CLIENT_VERSION = "1.65.10"
        const val VR_CLIENT_ID = "28"
        const val VR_USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        // Stream clients fallback chain (matching Ryotunes crates/innertube/src/clients.rs)
        data class StreamClient(
            val key: String,
            val clientName: String,
            val clientVersion: String,
            val clientId: String,
            val userAgent: String,
            val osName: String? = null,
            val osVersion: String? = null,
            val deviceMake: String? = null,
            val deviceModel: String? = null,
            val androidSdkVersion: String? = null
        )

        val VISIONOS = StreamClient(
            key = "VISIONOS",
            clientName = VISIONOS_CLIENT_NAME,
            clientVersion = VISIONOS_CLIENT_VERSION,
            clientId = VISIONOS_CLIENT_ID,
            userAgent = VISIONOS_USER_AGENT,
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1"
        )

        val ANDROID_VR_1_65_10 = StreamClient(
            key = "ANDROID_VR_1_65_10",
            clientName = VR_CLIENT_NAME,
            clientVersion = VR_CLIENT_VERSION,
            clientId = VR_CLIENT_ID,
            userAgent = VR_USER_AGENT,
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32"
        )

        val ANDROID_VR_1_43_32 = StreamClient(
            key = "ANDROID_VR_1_43_32",
            clientName = VR_CLIENT_NAME,
            clientVersion = "1.43.32",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32"
        )

        val STREAM_FALLBACK_CLIENTS = listOf(
            VISIONOS,
            ANDROID_VR_1_65_10,
            ANDROID_VR_1_43_32
        )

        /**
         * Upgrades low-resolution YouTube Music thumbnails to crisp 800x800 high definition.
         * Mirrors Ryotunes thumbnail rewriting and Google user content URL scaling rules.
         */
        fun getHighResThumbnailUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            var u = url
            // Google user content (YouTube Music album covers and artist images)
            if (u.contains("googleusercontent.com") || u.contains("ggpht.com")) {
                u = u.replace(Regex("""=w\d+-h\d+.*"""), "=w800-h800-l90-rj")
                u = u.replace(Regex("""=s\d+.*"""), "=s800")
                return u
            }
            // YouTube video thumbnails (i.ytimg.com)
            if (u.contains("i.ytimg.com")) {
                u = u.replace("/default.jpg", "/hqdefault.jpg")
                     .replace("/mqdefault.jpg", "/hqdefault.jpg")
                return u
            }
            return u
        }

        private val VIDEO_TITLE_REGEX = Regex(
            """(?i)\b(""" +
                """lyric(\s+video)?|""" +
                """lyrical(\s+video)?|""" +
                """video\s+clip|""" +
                """music\s+video|""" +
                """official\s+video|""" +
                """official\s+music\s+video|""" +
                """full\s+video(\s+song)?|""" +
                """video\s+song|""" +
                """dance\s+video|""" +
                """performance\s+video|""" +
                """visualizer|""" +
                """ringtone|""" +
                """shorts?""" +
            """)\b|""" +
            """(\[|\()(?:\w+\s+)?video(\]|\))|""" +
            """\|\s*(?:\w+\s+)?video\s*(\||\Z)|""" +
            """(\[|\()(lyric|visualizer|ringtone)"""
        )

        fun isVideoOrClip(title: String, subtitle: String): Boolean {
            val sub = subtitle.trim()
            val subLower = sub.lowercase()

            // 1. Stated as video or podcast episode in subtitle
            if (subLower.startsWith("video") || subLower.contains("• video") || subLower.contains("video •")) {
                return true
            }
            if (subLower.startsWith("episode") || subLower.contains("• episode") || subLower.contains("episode •")) {
                return true
            }
            if (subLower.contains("views") && !subLower.startsWith("song")) {
                return true
            }

            // 2. Title patterns
            if (VIDEO_TITLE_REGEX.containsMatchIn(title)) {
                return true
            }

            return false
        }

        fun isMissingArtist(artist: String?): Boolean {
            val trimmed = artist?.trim().orEmpty()
            if (trimmed.isEmpty()) return true
            val lower = trimmed.lowercase()
            if (lower == "unknown artist" || lower == "various artists" || lower == "unknown") return true
            if (lower.matches(Regex("""^\d+(:\d+){1,2}$"""))) return true
            if (lower.matches(Regex("""^[\d.,]+[kmb]?\s+(views|plays|likes)$"""))) return true
            if (lower.matches(Regex("""^\d+\s+(hour|hours|minute|minutes|second|seconds).*$"""))) return true
            return false
        }
    }

    @Volatile
    private var visitorData: String? = null

    /**
     * Bootstrap visitorData from sw.js_data (exactly as Ryotunes does)
     */
    suspend fun getVisitorData(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh && !visitorData.isNullOrBlank()) {
            return@withContext visitorData
        }
        try {
            val req = Request.Builder()
                .url(SW_JS_DATA_URL)
                .header("User-Agent", WEB_USER_AGENT)
                .build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val match = Regex("""\"(Cg[ts][^\"]{20,})\"""").find(body)
                val vd = match?.groupValues?.get(1)
                if (!vd.isNullOrEmpty()) {
                    visitorData = vd
                    Log.d(TAG, "Bootstrapped visitorData: ${vd.take(20)}...")
                    return@withContext vd
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bootstrap visitorData", e)
        }
        return@withContext visitorData
    }

    private fun updateVisitorDataFromResponse(root: JsonObject) {
        val vd = root.getAsJsonObject("responseContext")?.get("visitorData")?.asString
        if (!vd.isNullOrEmpty()) {
            visitorData = vd
        }
    }

    private fun buildWebContext(vd: String?): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", WEB_CLIENT_NAME)
            addProperty("clientVersion", WEB_CLIENT_VERSION)
            addProperty("gl", "US")
            addProperty("hl", "en")
            if (!vd.isNullOrEmpty()) {
                addProperty("visitorData", vd)
            }
        }
        val request = JsonObject().apply {
            addProperty("useSsl", true)
        }
        return JsonObject().apply {
            add("client", client)
            add("request", request)
        }
    }

    private fun buildStreamContext(client: StreamClient, vd: String?): JsonObject {
        val clientObj = JsonObject().apply {
            addProperty("clientName", client.clientName)
            addProperty("clientVersion", client.clientVersion)
            client.osName?.let { addProperty("osName", it) }
            client.osVersion?.let { addProperty("osVersion", it) }
            client.deviceMake?.let { addProperty("deviceMake", it) }
            client.deviceModel?.let { addProperty("deviceModel", it) }
            client.androidSdkVersion?.let { addProperty("androidSdkVersion", it) }
            addProperty("gl", "US")
            addProperty("hl", "en")
            if (!vd.isNullOrEmpty()) {
                addProperty("visitorData", vd)
            }
        }
        val request = JsonObject().apply {
            addProperty("useSsl", true)
        }
        return JsonObject().apply {
            add("client", clientObj)
            add("request", request)
        }
    }

    /**
     * Search songs using InnerTube WEB_REMIX client
     */
    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("query", query)
            addProperty("params", FILTER_SONG)
        }

        val request = Request.Builder()
            .url(BASE_URL + "search")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Search songs network failure", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Search songs error: ${response.code}")
            return@withContext emptyList()
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = JsonParser.parseString(jsonStr).asJsonObject
        updateVisitorDataFromResponse(root)
        parseSongsFromSearch(root)
    }

    /**
     * Comprehensive search returning Top Result, Songs, Artists, and Playlists/Albums
     */
    suspend fun searchComprehensive(query: String): ComprehensiveSearchResult = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("query", query)
        }

        val request = Request.Builder()
            .url(BASE_URL + "search")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Search comprehensive network failure", e)
            return@withContext ComprehensiveSearchResult()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Search comprehensive error: ${response.code}")
            return@withContext ComprehensiveSearchResult()
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return@withContext ComprehensiveSearchResult()
        }
        updateVisitorDataFromResponse(root)
        parseComprehensiveSearchResult(query, root)
    }

    /**
     * Search artists specifically using InnerTube FILTER_ARTIST
     */
    suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("query", query)
            addProperty("params", FILTER_ARTIST)
        }

        val request = Request.Builder()
            .url(BASE_URL + "search")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Search artists network failure", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Search artists error: ${response.code}")
            return@withContext emptyList()
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        updateVisitorDataFromResponse(root)
        parseArtistsFromSearch(root)
    }

    /**
     * Search playlists across both featured and community InnerTube playlist filters.
     */
    suspend fun searchPlaylists(query: String): List<PlaylistItem> = coroutineScope {
        val vd = withContext(Dispatchers.IO) { getVisitorData() }
        val communityDeferred = async(Dispatchers.IO) {
            executePlaylistSearch(query, FILTER_PLAYLIST, vd)
        }
        val featuredDeferred = async(Dispatchers.IO) {
            executePlaylistSearch(query, FILTER_FEATURED_PLAYLIST, vd)
        }
        val featured = featuredDeferred.await()
        val community = communityDeferred.await()

        val merged = mutableListOf<PlaylistItem>()
        val seenIds = mutableSetOf<String>()
        val maxLen = maxOf(featured.size, community.size)
        for (i in 0 until maxLen) {
            if (i < featured.size && seenIds.add(featured[i].id)) {
                merged.add(featured[i])
            }
            if (i < community.size && seenIds.add(community[i].id)) {
                merged.add(community[i])
            }
        }
        merged
    }

    private fun executePlaylistSearch(query: String, params: String, visitorData: String?): List<PlaylistItem> {
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(visitorData))
            addProperty("query", query)
            addProperty("params", params)
        }

        val request = Request.Builder()
            .url(BASE_URL + "search")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!visitorData.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", visitorData)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Search playlists network failure", e)
            return emptyList()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Search playlists error: ${response.code}")
            return emptyList()
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        updateVisitorDataFromResponse(root)
        return parsePlaylistsFromSearch(root)
    }

    /**
     * Fetches complete artist profile from InnerTube browse endpoint
     */
    suspend fun getArtistProfile(browseId: String): ArtistProfile? = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("browseId", browseId)
        }

        val request = Request.Builder()
            .url(BASE_URL + "browse")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "getArtistProfile network failure", e)
            return@withContext null
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "getArtistProfile error: ${response.code}")
            return@withContext null
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return@withContext null
        }
        updateVisitorDataFromResponse(root)
        parseArtistProfileResult(browseId, root)
    }

    /**
     * Up next queue and recommendations using InnerTube next endpoint
     */
    suspend fun getNext(videoId: String, playlistId: String? = null): NextResult = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("videoId", videoId)
            if (!playlistId.isNullOrEmpty()) {
                addProperty("playlistId", playlistId)
            }
        }

        val request = Request.Builder()
            .url(BASE_URL + "next")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "getNext network failure", e)
            return@withContext NextResult(emptyList())
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "getNext error: ${response.code}")
            return@withContext NextResult(emptyList())
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = JsonParser.parseString(jsonStr).asJsonObject
        updateVisitorDataFromResponse(root)
        parseNextResult(root)
    }

    /**
     * Browse Home (shelves, recommendations)
     */
    suspend fun getHome(): HomeResult = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("browseId", "FEmusic_home")
        }

        val request = Request.Builder()
            .url(BASE_URL + "browse")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "getHome network failure", e)
            return@withContext HomeResult(emptyList(), emptyList())
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "getHome error: ${response.code}")
            return@withContext HomeResult(emptyList(), emptyList())
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = JsonParser.parseString(jsonStr).asJsonObject
        updateVisitorDataFromResponse(root)
        parseHomeResult(root)
    }

    /**
     * Browse Playlist or Album by browseId (e.g. "VL...", "MPRE...")
     */
    suspend fun getPlaylist(browseId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val trimmed = browseId.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("UC")) {
            return@withContext PlaylistResult(browseId, "Unknown Playlist")
        }
        val actualBrowseId = when {
            trimmed.startsWith("VL") || trimmed.startsWith("MPRE") || trimmed.startsWith("FEmusic") -> trimmed
            else -> "VL$trimmed"
        }
        val vd = getVisitorData()
        val bodyObj = JsonObject().apply {
            add("context", buildWebContext(vd))
            addProperty("browseId", actualBrowseId)
        }

        val request = Request.Builder()
            .url(BASE_URL + "browse")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "getPlaylist network failure", e)
            return@withContext PlaylistResult(browseId, "Unknown Playlist")
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "getPlaylist error: ${response.code}")
            return@withContext PlaylistResult(browseId, "Unknown Playlist")
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = JsonParser.parseString(jsonStr).asJsonObject
        parsePlaylistResult(browseId, root)
    }

    /**
     * Fetches raw lyrics JSON from YouTube Music via browse endpoint using lyricsBrowseId.
     */
    suspend fun getLyricsRaw(browseId: String, useAndroidMusic: Boolean = false): String? = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        val clientObj = JsonObject().apply {
            if (useAndroidMusic) {
                addProperty("clientName", ANDROID_MUSIC_CLIENT_NAME)
                addProperty("clientVersion", ANDROID_MUSIC_CLIENT_VERSION)
            } else {
                addProperty("clientName", WEB_CLIENT_NAME)
                addProperty("clientVersion", WEB_CLIENT_VERSION)
            }
            addProperty("gl", "US")
            addProperty("hl", "en")
            if (!vd.isNullOrEmpty()) {
                addProperty("visitorData", vd)
            }
        }
        val contextObj = JsonObject().apply {
            add("client", clientObj)
            add("request", JsonObject().apply { addProperty("useSsl", true) })
        }
        val bodyObj = JsonObject().apply {
            add("context", contextObj)
            addProperty("browseId", browseId)
        }

        val request = Request.Builder()
            .url(BASE_URL + "browse")
            .header("Content-Type", "application/json")
            .header("User-Agent", if (useAndroidMusic) ANDROID_MUSIC_USER_AGENT else WEB_USER_AGENT)
            .header("X-YouTube-Client-Name", if (useAndroidMusic) ANDROID_MUSIC_CLIENT_ID else WEB_CLIENT_ID)
            .header("X-YouTube-Client-Version", if (useAndroidMusic) ANDROID_MUSIC_CLIENT_VERSION else WEB_CLIENT_VERSION)
            .apply {
                if (!useAndroidMusic) {
                    header("Origin", "https://music.youtube.com")
                    header("Referer", "https://music.youtube.com/")
                }
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "getLyricsRaw network failure (android=$useAndroidMusic)", e)
            return@withContext null
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "getLyricsRaw error: ${response.code} (android=$useAndroidMusic)")
            return@withContext null
        }

        response.body?.string()
    }

    /**
     * Fetches official lyrics from YouTube Music for a given videoId.
     * Queries ANDROID_MUSIC first (which returns official cueRange timestamps when available),
     * falling back to WEB_REMIX description shelf lyrics.
     */
    suspend fun getLyrics(videoId: String): Lyrics? = withContext(Dispatchers.IO) {
        val next = getNext(videoId)
        val browseId = next.lyricsBrowseId ?: return@withContext null
        getLyricsByBrowseId(browseId, videoId)
    }

    suspend fun getLyricsByBrowseId(browseId: String, videoId: String = ""): Lyrics? = withContext(Dispatchers.IO) {
        val androidRaw = getLyricsRaw(browseId, useAndroidMusic = true)
        val androidParsed = androidRaw?.let { parseLyricsFromBrowse(it, videoId) }
        if (androidParsed != null && androidParsed.synced) {
            return@withContext androidParsed
        }

        val webRaw = getLyricsRaw(browseId, useAndroidMusic = false)
        val webParsed = webRaw?.let { parseLyricsFromBrowse(it, videoId) }
        if (webParsed != null && webParsed.synced) {
            return@withContext webParsed
        }

        androidParsed ?: webParsed
    }

    fun parseLyricsFromBrowse(rawJson: String, videoId: String = ""): Lyrics? {
        return try {
            val root = JsonParser.parseString(rawJson).asJsonObject

            // 1. Check ANDROID_MUSIC timedLyricsModel (contains cueRange synced timestamps when licensed)
            val lyricsDataNodes = findAllNodes(root, "lyricsData")
            for (lyricsData in lyricsDataNodes) {
                val timedArray = lyricsData.getAsJsonArray("timedLyricsData")
                if (timedArray != null && timedArray.size() > 0) {
                    val sourceMsg = lyricsData.get("sourceMessage")?.asString.orEmpty()
                        .removePrefix("Source:")
                        .trim()
                    val sourceName = if (sourceMsg.isNotBlank()) "YouTube Music ($sourceMsg)" else "YouTube Music"

                    var hasTiming = false
                    val timedLines = mutableListOf<LyricLine>()
                    for (el in timedArray) {
                        if (!el.isJsonObject) continue
                        val obj = el.asJsonObject
                        val text = obj.get("lyricLine")?.asString?.trim().orEmpty()
                        val startMs = obj.getAsJsonObject("cueRange")
                            ?.get("startTimeMilliseconds")?.asString?.toLongOrNull()
                        if (startMs != null) {
                            hasTiming = true
                        }
                        if (text.isNotEmpty() && (text != "♪" || startMs != null)) {
                            timedLines.add(LyricLine(timeMs = startMs, text = text))
                        }
                    }

                    // Drop trailing/leading plain musical note placeholders if unsynced
                    val filteredLines = if (hasTiming) {
                        timedLines.filter { it.timeMs != null }
                    } else {
                        timedLines.filter { it.text != "♪" }
                    }

                    if (filteredLines.isNotEmpty()) {
                        return Lyrics(
                            videoId = videoId,
                            source = sourceName,
                            synced = hasTiming,
                            lines = filteredLines
                        )
                    }
                }
            }

            // 2. Fallback: WEB_REMIX musicDescriptionShelfRenderer
            val shelves = findAllNodes(root, "musicDescriptionShelfRenderer")
            val shelf = shelves.firstOrNull() ?: return null
            val descObj = shelf.getAsJsonObject("description") ?: return null
            val rawText = extractRunsText(descObj)
            if (rawText.isBlank()) return null

            val footerObj = shelf.getAsJsonObject("footer")
            val footerText = extractRunsText(footerObj)
                .removePrefix("Source:")
                .trim()
            val sourceName = if (footerText.isNotBlank()) "YouTube Music ($footerText)" else "YouTube Music"

            val lines = rawText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }

            if (lines.isEmpty()) return null

            Lyrics(
                videoId = videoId,
                source = sourceName,
                synced = false,
                lines = lines
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse YouTube Music lyrics", e)
            null
        }
    }

    /**
     * Fetches authoritative track metadata (clean artist name, album, duration) for a videoId
     * using InnerTube's next endpoint with player videoDetails fallback.
     */
    suspend fun getSongDetails(videoId: String): SongItem? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        val next = getNext(videoId)
        val exactMatch = next.items.firstOrNull { it.videoId == videoId }
            ?: next.items.firstOrNull()
        if (exactMatch != null && !isMissingArtist(exactMatch.artists)) {
            return@withContext exactMatch
        }

        // Fallback to player endpoint videoDetails
        val vd = getVisitorData()
        for (client in STREAM_FALLBACK_CLIENTS.take(2)) {
            try {
                val bodyObj = JsonObject().apply {
                    add("context", buildStreamContext(client, vd))
                    addProperty("videoId", videoId)
                    addProperty("contentCheckOk", true)
                    addProperty("racyCheckOk", true)
                }
                val req = Request.Builder()
                    .url(BASE_URL + "player")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", client.userAgent)
                    .header("X-YouTube-Client-Name", client.clientId)
                    .header("X-YouTube-Client-Version", client.clientVersion)
                    .apply {
                        if (!vd.isNullOrEmpty()) header("X-Goog-Visitor-Id", vd)
                    }
                    .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
                    .build()

                val resp = okHttpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    continue
                }
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                resp.close()
                val details = root.getAsJsonObject("videoDetails") ?: continue
                val title = details.get("title")?.asString?.trim().orEmpty()
                val rawAuthor = details.get("author")?.asString?.trim().orEmpty()
                val cleanAuthor = cleanTopicArtist(rawAuthor)
                val durSec = details.get("lengthSeconds")?.asString?.toIntOrNull() ?: 0
                if (!isMissingArtist(cleanAuthor)) {
                    val mins = durSec / 60
                    val secs = durSec % 60
                    val durStr = if (durSec > 0) String.format("%d:%02d", mins, secs) else exactMatch?.duration
                    return@withContext SongItem(
                        videoId = videoId,
                        title = title.ifEmpty { exactMatch?.title.orEmpty() },
                        artists = cleanAuthor,
                        album = exactMatch?.album,
                        duration = durStr,
                        durationSeconds = if (durSec > 0) durSec else (exactMatch?.durationSeconds ?: 0),
                        thumbnailUrl = exactMatch?.thumbnailUrl ?: extractLastThumbnail(details)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "getSongDetails player fallback failed for $videoId", e)
            }
        }
        exactMatch
    }

    private fun cleanTopicArtist(raw: String): String {
        return raw.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Resolves a videoId to a playable direct stream using the Ryotunes STREAM_FALLBACK_ORDER:
     * 1. VISIONOS (direct un-ciphered stream, accepts open-ended Range requests)
     * 2. ANDROID_VR 1.65.10
     * 3. ANDROID_VR 1.43.32
     * Resolves a videoId to the highest possible acoustic quality stream across available clients.
     * Evaluates formats using perceptual audio quality scoring and the Ryotunes HIGH two-pass strategy.
     * Includes stream probing (validateStream) and anti-bot gate auto-recovery with fresh visitorData.
     */
    suspend fun resolveStream(
        videoId: String,
        preferredQuality: StreamQuality = StreamQuality.AUTO
    ): PlaybackStream? = withContext(Dispatchers.IO) {
        var vd = getVisitorData()
        var bestCandidate: PlaybackStream? = null
        var bestScore = -1L

        for (client in STREAM_FALLBACK_CLIENTS) {
            val res = tryResolveBestStreamWithClient(client, videoId, vd)
            if (res != null) {
                val (stream, score) = res
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = stream
                }
                // Optimal tier threshold: Opus 160kbps+ (itag 251) or AAC 256kbps+ (itag 141) has score >= 22,200,000L.
                // When reached, this is the maximum possible audio quality YouTube encodes.
                if (bestScore >= 22_200_000L) {
                    Log.d(TAG, "Locked in optimal high-fidelity stream for $videoId with ${stream.clientName} (itag=${stream.itag}, ${stream.bitrate / 1000}kbps, mime=${stream.mimeType}, fallbacks=${stream.fallbackStreams.size})")
                    return@withContext applyPreferredQuality(bestCandidate, preferredQuality)
                }
            }
        }

        if (bestCandidate != null) {
            Log.d(TAG, "Resolved best available stream for $videoId with ${bestCandidate.clientName} (itag=${bestCandidate.itag}, ${bestCandidate.bitrate / 1000}kbps, fallbacks=${bestCandidate.fallbackStreams.size})")
            return@withContext applyPreferredQuality(bestCandidate, preferredQuality)
        }

        // Anti-bot gate auto-recovery: if all clients failed, refresh visitorData and retry chain
        Log.d(TAG, "No playable stream found on first pass for $videoId. Refreshing visitorData and retrying...")
        vd = getVisitorData(forceRefresh = true)
        for (client in STREAM_FALLBACK_CLIENTS) {
            val res = tryResolveBestStreamWithClient(client, videoId, vd)
            if (res != null) {
                val (stream, score) = res
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = stream
                }
                if (bestScore >= 22_200_000L) {
                    return@withContext applyPreferredQuality(bestCandidate, preferredQuality)
                }
            }
        }

        if (bestCandidate != null) {
            Log.d(TAG, "Resolved best available stream after refresh for $videoId with ${bestCandidate.clientName} (fallbacks=${bestCandidate.fallbackStreams.size})")
        } else {
            Log.e(TAG, "Failed to resolve playable stream with any client for $videoId")
        }
        applyPreferredQuality(bestCandidate, preferredQuality)
    }

    private fun applyPreferredQuality(
        candidate: PlaybackStream?,
        preferredQuality: StreamQuality
    ): PlaybackStream? {
        if (candidate == null) return null
        if (preferredQuality == StreamQuality.AUTO || preferredQuality == StreamQuality.HIGH) {
            return candidate
        }
        val targetTier = when (preferredQuality) {
            StreamQuality.MEDIUM -> AudioQualityTier.MEDIUM
            StreamQuality.LOW -> AudioQualityTier.LOW
            StreamQuality.VERY_LOW -> AudioQualityTier.VERY_LOW
            else -> AudioQualityTier.HIGH
        }
        val selected = candidate.getStreamForTier(targetTier)
        val remaining = candidate.getAllStreams().filter { it.itag != selected.itag }
        return selected.copy(fallbackStreams = remaining)
    }

    /**
     * Scores an audio format based on acoustic fidelity and psychoacoustic transparency:
     * 1. Audio Quality Tier: HIGH (e.g. 256k AAC) > MEDIUM (160k Opus, 128k AAC) > LOW (70k Opus, 50k AAC)
     * 2. Channels: Stereo (>= 2 channels) preferred over mono
     * 3. Codec: Opus (48kHz fullband) has higher perceptual efficiency than AAC at comparable bitrates
     * 4. Bitrate: Higher bitrates within the tier strictly prioritized
     * 5. Sample rate: 48,000 Hz > 44,100 Hz > 22,050 Hz
     */
    private fun computeAudioQualityScore(format: JsonObject): Long {
        val mimeType = format.get("mimeType")?.asString ?: ""
        val itag = format.get("itag")?.asInt ?: 0
        val audioQuality = format.get("audioQuality")?.asString ?: ""
        val bitrate = format.get("bitrate")?.asLong ?: 0L
        val channels = format.get("audioChannels")?.asInt ?: 2
        val sampleRate = format.get("audioSampleRate")?.asString?.toLongOrNull() ?: 44100L

        // 1. Tier score (HIGH: 30M, MEDIUM: 20M, LOW: 10M)
        val tierScore = when (audioQuality) {
            "AUDIO_QUALITY_HIGH" -> 30_000_000L
            "AUDIO_QUALITY_MEDIUM" -> 20_000_000L
            "AUDIO_QUALITY_LOW" -> 10_000_000L
            else -> if (bitrate >= 140000L) 20_000_000L else 10_000_000L
        }

        // 2. Channel score (Stereo preferred)
        val channelScore = channels.coerceIn(1, 2) * 1_000_000L

        // 3. Codec score (Opus preferred for transparency)
        val isOpus = mimeType.contains("opus") || itag == 251 || itag == 250 || itag == 249
        val codecScore = if (isOpus) 200_000L else 100_000L

        // 4. Effective perceptual bitrate (Opus gets 1.25x perceptual efficiency multiplier)
        val effectiveBitrate = if (isOpus) (bitrate * 1.25).toLong() else bitrate
        val bitrateScore = (effectiveBitrate / 100L).coerceAtLeast(0L)

        // 5. Sample rate bonus (48000 Hz > 44100 Hz)
        val sampleRateScore = sampleRate / 1000L

        return tierScore + channelScore + codecScore + bitrateScore + sampleRateScore
    }

    private fun tryResolveBestStreamWithClient(
        client: StreamClient,
        videoId: String,
        vd: String?
    ): Pair<PlaybackStream, Long>? {
        val bodyObj = JsonObject().apply {
            add("context", buildStreamContext(client, vd))
            addProperty("videoId", videoId)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        }

        val reqBuilder = Request.Builder()
            .url(BASE_URL + "player")
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .apply {
                if (!vd.isNullOrEmpty()) {
                    header("X-Goog-Visitor-Id", vd)
                }
            }
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA))

        val response = try {
            okHttpClient.newCall(reqBuilder.build()).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Network error on resolveStream for $videoId with ${client.key}", e)
            return null
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Player endpoint responded with ${response.code} for ${client.key}")
            return null
        }

        val jsonStr = response.body?.string().orEmpty()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return null
        }

        val status = root.getAsJsonObject("playabilityStatus")?.get("status")?.asString
        if (status != "OK") {
            Log.w(TAG, "Playability status for $videoId with ${client.key} is $status")
            return null
        }

        val streamingData = root.getAsJsonObject("streamingData") ?: return null
        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats") ?: return null
        val expiresInSeconds = streamingData.get("expiresInSeconds")?.asLong ?: 21600L
        val loudnessDb = root.getAsJsonObject("playerConfig")
            ?.getAsJsonObject("audioConfig")
            ?.get("loudnessDb")?.asDouble

        // Collect and rank all playable audio formats
        val audioCandidates = mutableListOf<Pair<Long, JsonObject>>()

        for (el in adaptiveFormats) {
            val format = el.asJsonObject
            val mimeType = format.get("mimeType")?.asString ?: ""
            val url = format.get("url")?.asString

            if (!mimeType.startsWith("audio/") || url.isNullOrEmpty()) {
                continue
            }

            // Exclude auto-dubbed foreign audio tracks
            val audioTrack = format.getAsJsonObject("audioTrack")
            if (audioTrack?.get("isAutoDubbed")?.asBoolean == true) {
                continue
            }

            val score = computeAudioQualityScore(format)
            audioCandidates.add(score to format)
        }

        // Sort descending by acoustic quality score
        audioCandidates.sortByDescending { it.first }

        // Probe starting from the best candidate format down
        for (i in audioCandidates.indices) {
            val (score, format) = audioCandidates[i]
            val url = format.get("url")?.asString ?: continue
            val itag = format.get("itag")?.asInt ?: 0
            val mimeType = format.get("mimeType")?.asString ?: "audio/webm"
            val bitrate = format.get("bitrate")?.asLong ?: 160000L

            // Validate stream via lightweight probe matching Ryotunes validate_head
            if (validateStream(url, client.userAgent)) {
                Log.d(TAG, "Validated stream: itag=$itag, ${bitrate / 1000}kbps, mime=$mimeType (score=$score) on ${client.key}")

                // Collect remaining valid candidates as fallback streams
                val fallbacks = mutableListOf<PlaybackStream>()
                for (j in audioCandidates.indices) {
                    if (j == i) continue
                    val (_, f) = audioCandidates[j]
                    val fUrl = f.get("url")?.asString ?: continue
                    val fItag = f.get("itag")?.asInt ?: 0
                    val fMime = f.get("mimeType")?.asString ?: "audio/webm"
                    val fBitrate = f.get("bitrate")?.asLong ?: 128000L
                    fallbacks.add(
                        PlaybackStream(
                            videoId = videoId,
                            streamUrl = fUrl,
                            itag = fItag,
                            mimeType = fMime,
                            bitrate = fBitrate,
                            expiresInSeconds = expiresInSeconds,
                            loudnessDb = loudnessDb,
                            userAgent = client.userAgent,
                            clientName = client.key,
                            contentLength = f.get("contentLength")?.asString?.toLongOrNull()
                        )
                    )
                }

                val stream = PlaybackStream(
                    videoId = videoId,
                    streamUrl = url,
                    itag = itag,
                    mimeType = mimeType,
                    bitrate = bitrate,
                    expiresInSeconds = expiresInSeconds,
                    loudnessDb = loudnessDb,
                    userAgent = client.userAgent,
                    clientName = client.key,
                    contentLength = format.get("contentLength")?.asString?.toLongOrNull(),
                    fallbackStreams = fallbacks.distinctBy { it.itag }.sortedByDescending { it.bitrate }
                )
                return stream to score
            } else {
                Log.w(TAG, "Format itag $itag (${bitrate / 1000}kbps) failed probe with ${client.key}, trying next format...")
            }
        }

        return null
    }

    /**
     * Lightweight probe to confirm stream is playable (returns 200..299 or 206 for Range).
     * Mirrors Ryotunes orchestrator validate_head.
     */
    private fun validateStream(url: String, userAgent: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1024")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val valid = resp.isSuccessful || resp.code == 200 || resp.code == 206
            resp.close()
            valid
        } catch (e: Exception) {
            Log.w(TAG, "validateStream probe failed", e)
            false
        }
    }

    // --- JSON PARSING HELPERS (Replicating Ryotunes' tolerant discovery) ---

    private fun parseSongsFromSearch(root: JsonObject): List<SongItem> {
        val items = mutableListOf<SongItem>()
        val listItems = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (node in listItems) {
            val flexColumns = node.getAsJsonArray("flexColumns") ?: continue
            val col0 = flexColumns.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            val title = extractRunsText(col0?.getAsJsonObject("text"))
            val col1 = if (flexColumns.size() > 1) {
                flexColumns.get(1)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            } else null
            val subtitle = extractRunsText(col1?.getAsJsonObject("text"))
            val subLower = subtitle.lowercase()
            val isNonSong = subLower.startsWith("artist") ||
                subLower.contains("artist •") ||
                subLower.startsWith("album") ||
                subLower.startsWith("single") ||
                subLower.startsWith("ep") ||
                subLower.startsWith("playlist") ||
                subLower.startsWith("podcast") ||
                subLower.startsWith("episode") ||
                subLower.startsWith("profile")

            if (!isNonSong && !isVideoOrClip(title, subtitle)) {
                val song = parseSongFromListItem(node)
                if (song != null && items.none { it.videoId == song.videoId }) {
                    items.add(song)
                }
            }
        }
        return items
    }

    internal fun parseNextResult(root: JsonObject): NextResult {
        val items = mutableListOf<SongItem>()
        val panelVideos = findAllNodes(root, "playlistPanelVideoRenderer")
        for (node in panelVideos) {
            val videoId = node.get("videoId")?.asString.orEmpty()
            val title = extractRunsText(node.getAsJsonObject("title"))
            val shortText = extractRunsText(node.getAsJsonObject("shortBylineText"))
            val longText = extractRunsText(node.getAsJsonObject("longBylineText"))
            val (parsedArtist, parsedAlbum) = extractArtistAndAlbumFromByline(shortText, longText)
            val artist = if (!isMissingArtist(parsedArtist)) parsedArtist else "Unknown Artist"
            val lengthText = extractRunsText(node.getAsJsonObject("lengthText"))
            val thumb = extractLastThumbnail(node)

            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                items.add(
                    SongItem(
                        videoId = videoId,
                        title = title,
                        artists = artist,
                        album = parsedAlbum,
                        duration = lengthText,
                        durationSeconds = parseDurationToSeconds(lengthText),
                        thumbnailUrl = thumb
                    )
                )
            }
        }

        val lyricsBrowseId = findLyricsBrowseId(root)
        val automix = findAllNodes(root, "automixPreviewVideoRenderer")
            .firstOrNull()?.get("playlistId")?.asString

        return NextResult(
            items = items,
            lyricsBrowseId = lyricsBrowseId,
            automixPlaylistId = automix
        )
    }

    private fun extractArtistAndAlbumFromByline(shortText: String, longText: String): Pair<String, String?> {
        val cleanShort = cleanTopicArtist(shortText)
        val longParts = longText.split("•").map { it.trim() }.filter { part ->
            part.isNotEmpty() &&
                !isDuration(part) &&
                !isMetadataMetricPart(part) &&
                !part.matches(Regex("""^(Song|Video|Single|Episode)$""", RegexOption.IGNORE_CASE))
        }

        val artist = when {
            cleanShort.isNotBlank() && !isMissingArtist(cleanShort) -> cleanShort
            longParts.isNotEmpty() -> cleanTopicArtist(longParts[0])
            else -> ""
        }

        val album = longParts.firstOrNull { part ->
            !part.equals(artist, ignoreCase = true) && !part.matches(Regex("""^\d{4}$"""))
        }
        return artist to album
    }

    private fun parseHomeResult(root: JsonObject): HomeResult {
        val chips = mutableListOf<String>()
        val chipNodes = findAllNodes(root, "chipCloudChipRenderer")
        for (node in chipNodes) {
            val text = extractRunsText(node.getAsJsonObject("text"))
            if (text.isNotEmpty()) {
                chips.add(text)
            }
        }

        val sections = mutableListOf<HomeSection>()
        val shelfNodes = findAllNodes(root, "musicCarouselShelfRenderer")
        for (shelf in shelfNodes) {
            val header = findAllNodes(shelf, "musicCarouselShelfBasicHeaderRenderer").firstOrNull()
            val title = extractRunsText(header?.getAsJsonObject("title"))

            val shelfItems = mutableListOf<SongItem>()
            val twoRowItems = findAllNodes(shelf, "musicTwoRowItemRenderer")
            for (item in twoRowItems) {
                val song = parseSongFromTwoRowItem(item)
                if (song != null) {
                    shelfItems.add(song)
                }
            }

            if (shelfItems.isEmpty()) {
                val listRows = findAllNodes(shelf, "musicResponsiveListItemRenderer")
                for (item in listRows) {
                    val song = parseSongFromListItem(item)
                    if (song != null) {
                        shelfItems.add(song)
                    }
                }
            }

            if (shelfItems.isNotEmpty()) {
                sections.add(HomeSection(title = title.ifEmpty { "Recommended" }, items = shelfItems))
            }
        }

        return HomeResult(chips = chips, sections = sections)
    }

    internal fun parsePlaylistResult(browseId: String, root: JsonObject): PlaylistResult {
        val header = findAllNodes(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicEditablePlaylistDetailHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicDetailHeaderRenderer").firstOrNull()

        val title = extractRunsText(header?.getAsJsonObject("title")).ifEmpty { "Playlist" }
        val subtitle = extractRunsText(header?.getAsJsonObject("subtitle"))
        val strapline = extractRunsText(header?.getAsJsonObject("straplineTextOne"))
        val description = extractRunsText(header?.getAsJsonObject("description"))
        val thumbnail = extractLastThumbnail(header ?: root)

        val isAlbum = browseId.startsWith("MPRE") ||
            subtitle.contains("Album", ignoreCase = true) ||
            subtitle.contains("Single", ignoreCase = true) ||
            subtitle.contains("EP", ignoreCase = true)

        val albumArtist: String? = when {
            strapline.isNotBlank() && !isMissingArtist(strapline) -> cleanTopicArtist(strapline)
            isAlbum && subtitle.isNotBlank() -> {
                subtitle.split("•")
                    .map { it.trim() }
                    .firstOrNull { part ->
                        part.isNotBlank() &&
                            !part.matches(Regex("""^(Upcoming\s+)?(Album|Single|EP|Playlist)$""", RegexOption.IGNORE_CASE)) &&
                            !part.matches(Regex("""^\d{4}$""")) &&
                            !isMetadataMetricPart(part) &&
                            !isDuration(part)
                    }?.let { cleanTopicArtist(it) }
            }
            else -> null
        }

        val tracks = mutableListOf<SongItem>()
        val rows = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (node in rows) {
            val song = parseSongFromListItem(
                node = node,
                fallbackArtist = albumArtist,
                fallbackAlbum = if (isAlbum) title else null
            )
            if (song != null) {
                val songThumb = song.thumbnailUrl?.ifBlank { null }
                    ?: thumbnail?.ifBlank { null }
                    ?: "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg"
                val songWithThumb = song.copy(
                    thumbnailUrl = songThumb,
                    album = song.album ?: if (isAlbum) title else null
                )
                tracks.add(songWithThumb)
            }
        }

        return PlaylistResult(
            id = browseId,
            title = title,
            subtitle = subtitle,
            description = description,
            thumbnailUrl = thumbnail,
            tracks = tracks,
            isAlbum = isAlbum
        )
    }

    internal fun parseComprehensiveSearchResult(query: String, root: JsonObject): ComprehensiveSearchResult {
        var topResult: SearchTopResult? = null
        val songs = mutableListOf<SongItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        val cardChildArtists = mutableMapOf<String, String>()

        // 1. Check musicCardShelfRenderer (Top Result)
        val cardShelves = findAllNodes(root, "musicCardShelfRenderer")
        if (cardShelves.isNotEmpty()) {
            val card = cardShelves.first()
            val title = extractRunsText(card.getAsJsonObject("title"))
            val subtitle = extractRunsText(card.getAsJsonObject("subtitle"))
            val thumbnail = extractLastThumbnail(card)

            if (!isVideoOrClip(title, subtitle)) {
                val onTap = card.getAsJsonObject("onTap")
                val titleRuns = card.getAsJsonObject("title")?.getAsJsonArray("runs")
                val titleNav = if (titleRuns != null && titleRuns.size() > 0) {
                    titleRuns.get(0).asJsonObject.getAsJsonObject("navigationEndpoint")
                } else null
                val mainNav = onTap ?: titleNav

                val cardBrowse = mainNav?.getAsJsonObject("browseEndpoint")
                val browseId = cardBrowse?.get("browseId")?.asString.orEmpty()
                val pageType = cardBrowse?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                    ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()

                val cardWatch = mainNav?.getAsJsonObject("watchEndpoint")
                    ?: card.getAsJsonObject("playlistItemData")
                val videoId = cardWatch?.get("videoId")?.asString.orEmpty()

                val subLower = subtitle.lowercase()
                val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                    subLower.startsWith("artist") ||
                    subLower.contains("artist •") ||
                    (browseId.startsWith("UC") && (subLower.contains("subscribers") || subLower.contains("monthly") || subLower.contains("audience")))

                val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
                    browseId.startsWith("MPRE") ||
                    subLower.startsWith("album") ||
                    subLower.startsWith("single") ||
                    subLower.startsWith("ep")

                val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                    browseId.startsWith("VL") ||
                    browseId.startsWith("PL") ||
                    subLower.startsWith("playlist")

                if (isArtist) {
                    val actualBrowseId = if (browseId.isNotEmpty()) browseId else findAllNodes(card, "browseEndpoint").firstOrNull()?.get("browseId")?.asString.orEmpty()
                    val cleanedSub = subtitle.replace(Regex("""^Artist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                    val artist = ArtistItem(
                        id = actualBrowseId,
                        name = title,
                        subscribersOrAudience = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail
                    )
                    topResult = SearchTopResult(
                        type = "ARTIST",
                        id = actualBrowseId,
                        title = title,
                        subtitle = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        artistItem = artist
                    )
                    if (actualBrowseId.isNotEmpty() && artists.none { it.id == actualBrowseId || it.name.equals(title, ignoreCase = true) }) {
                        artists.add(0, artist)
                    }
                    // Map child rows inside the artist card to fallback to this artist name
                    if (title.isNotBlank() && !isMissingArtist(title)) {
                        for (childRow in findAllNodes(card, "musicResponsiveListItemRenderer")) {
                            val childVid = extractVideoIdFromListItem(childRow)
                            if (!childVid.isNullOrBlank()) {
                                cardChildArtists[childVid] = title
                            }
                        }
                    }
                } else if (isAlbum) {
                    val actualId = if (browseId.isNotEmpty() && !browseId.startsWith("UC")) {
                        browseId
                    } else {
                        extractPlaylistOrAlbumBrowseId(card, mainNav, preferAlbum = true)
                    }
                    val cleanedSub = subtitle.replace(Regex("""^(Album|Single|EP)\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                    val pl = PlaylistItem(
                        id = actualId,
                        title = title,
                        author = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        isAlbum = true
                    )
                    topResult = SearchTopResult(
                        type = "ALBUM",
                        id = actualId,
                        title = title,
                        subtitle = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        playlistItem = pl
                    )
                    if (actualId.isNotEmpty() && playlists.none { it.id == actualId }) {
                        playlists.add(0, pl)
                    }
                } else if (isPlaylist) {
                    val actualId = if (browseId.isNotEmpty() && !browseId.startsWith("UC")) {
                        browseId
                    } else {
                        extractPlaylistOrAlbumBrowseId(card, mainNav, preferAlbum = false)
                    }
                    val cleanedSub = subtitle.replace(Regex("""^Playlist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                    val pl = PlaylistItem(
                        id = actualId,
                        title = title,
                        author = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        isAlbum = false
                    )
                    topResult = SearchTopResult(
                        type = "PLAYLIST",
                        id = actualId,
                        title = title,
                        subtitle = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        playlistItem = pl
                    )
                    if (actualId.isNotEmpty() && playlists.none { it.id == actualId }) {
                        playlists.add(0, pl)
                    }
                } else if (!subLower.startsWith("podcast") && !subLower.startsWith("episode") && !subLower.startsWith("profile")) {
                    // Song or Video
                    val songVideoId = videoId.ifEmpty {
                        card.getAsJsonObject("buttons")?.let {
                            findAllNodes(it, "watchEndpoint").firstOrNull()?.get("videoId")?.asString.orEmpty()
                        }.orEmpty()
                    }

                    if (songVideoId.isNotEmpty()) {
                        val (parsedArtist, parsedAlbum, parsedDuration) = parseSubtitleFields(subtitle)
                        val artistFromRuns = extractArtistFromRuns(card.getAsJsonObject("subtitle"))
                        val artistName = when {
                            !isMissingArtist(parsedArtist) -> parsedArtist
                            !artistFromRuns.isNullOrBlank() -> artistFromRuns
                            else -> "Unknown Artist"
                        }

                        val song = SongItem(
                            videoId = songVideoId,
                            title = title,
                            artists = artistName,
                            album = parsedAlbum,
                            duration = parsedDuration,
                            durationSeconds = parseDurationToSeconds(parsedDuration),
                            thumbnailUrl = thumbnail
                        )
                        topResult = SearchTopResult(
                            type = "SONG",
                            id = songVideoId,
                            title = title,
                            subtitle = artistName,
                            thumbnailUrl = thumbnail,
                            songItem = song
                        )
                        if (songs.none { it.videoId == song.videoId }) {
                            songs.add(0, song)
                        }
                    }
                }
            }
        }

        // 2. Parse all musicResponsiveListItemRenderer
        val listItems = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (item in listItems) {
            val flexColumns = item.getAsJsonArray("flexColumns") ?: continue
            if (flexColumns.size() == 0) continue

            val col0 = flexColumns.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            val title = extractRunsText(col0?.getAsJsonObject("text"))
            if (title.isEmpty()) continue

            val col1 = if (flexColumns.size() > 1) {
                flexColumns.get(1)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            } else null
            val subtitle = extractRunsText(col1?.getAsJsonObject("text"))
            val thumbnail = extractLastThumbnail(item)

            val nav = item.getAsJsonObject("navigationEndpoint")
                ?: col0?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")
            val browse = nav?.getAsJsonObject("browseEndpoint")
            val browseId = browse?.get("browseId")?.asString.orEmpty()
            val pageType = browse?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()

            val subLower = subtitle.lowercase()
            val isItemArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                subLower.startsWith("artist") ||
                subLower.contains("artist •") ||
                (browseId.startsWith("UC") && (subLower.contains("subscribers") || subLower.contains("monthly") || subLower.contains("audience")))

            val isItemAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
                browseId.startsWith("MPRE") ||
                subLower.startsWith("album") ||
                subLower.startsWith("single") ||
                subLower.startsWith("ep")

            val isItemPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                browseId.startsWith("VL") ||
                browseId.startsWith("PL") ||
                browseId.startsWith("RD") ||
                subLower.startsWith("playlist")

            val isItemOther = subLower.startsWith("podcast") ||
                subLower.startsWith("episode") ||
                subLower.startsWith("profile") ||
                pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"

            if (isItemArtist) {
                val actualId = if (browseId.isNotEmpty()) browseId else findAllNodes(item, "browseEndpoint").firstOrNull()?.get("browseId")?.asString.orEmpty()
                val cleanedSub = subtitle.replace(Regex("""^Artist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                if (actualId.isNotEmpty() && artists.none { it.id == actualId || it.name.equals(title, ignoreCase = true) }) {
                    artists.add(
                        ArtistItem(
                            id = actualId,
                            name = title,
                            subscribersOrAudience = cleanedSub.ifEmpty { subtitle },
                            thumbnailUrl = thumbnail
                        )
                    )
                }
            } else if (isItemAlbum) {
                val actualId = extractPlaylistOrAlbumBrowseId(item, nav, preferAlbum = true)
                val cleanedSub = subtitle.replace(Regex("""^(Album|Single|EP)\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                if (actualId.isNotEmpty() && playlists.none { it.id == actualId }) {
                    playlists.add(
                        PlaylistItem(
                            id = actualId,
                            title = title,
                            author = cleanedSub.ifEmpty { subtitle },
                            thumbnailUrl = thumbnail,
                            isAlbum = true
                        )
                    )
                }
            } else if (isItemPlaylist) {
                val actualId = extractPlaylistOrAlbumBrowseId(item, nav, preferAlbum = false)
                val cleanedSub = subtitle.replace(Regex("""^Playlist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                if (actualId.isNotEmpty() && playlists.none { it.id == actualId }) {
                    playlists.add(
                        PlaylistItem(
                            id = actualId,
                            title = title,
                            author = cleanedSub.ifEmpty { subtitle },
                            thumbnailUrl = thumbnail,
                            isAlbum = false
                        )
                    )
                }
            } else if (isItemOther) {
                continue
            } else {
                if (!isVideoOrClip(title, subtitle)) {
                    val itemVid = extractVideoIdFromListItem(item)
                    val song = parseSongFromListItem(
                        node = item,
                        fallbackArtist = itemVid?.let { cardChildArtists[it] }
                    )
                    if (song != null && songs.none { it.videoId == song.videoId }) {
                        songs.add(song)
                    }
                }
            }
        }

        // 3. Post-process topResult: if null or was a video, pick best matching result
        if (topResult == null || (topResult.type == "SONG" && isVideoOrClip(topResult.title, topResult.subtitle ?: ""))) {
            val trimmedQuery = query.trim()

            // 1) Does an artist's name match the query (exact or close)?
            val exactArtist = artists.firstOrNull { it.name.equals(trimmedQuery, ignoreCase = true) }
                ?: artists.firstOrNull { it.name.startsWith(trimmedQuery, ignoreCase = true) || trimmedQuery.startsWith(it.name, ignoreCase = true) }
                ?: artists.firstOrNull { it.name.contains(trimmedQuery, ignoreCase = true) || trimmedQuery.contains(it.name, ignoreCase = true) }

            // 2) Does a song title match the query (exact, prefix, or contains)?
            val exactSong = songs.firstOrNull { it.title.equals(trimmedQuery, ignoreCase = true) }
            val matchingSong = exactSong
                ?: songs.firstOrNull { it.title.startsWith(trimmedQuery, ignoreCase = true) || it.title.contains(trimmedQuery, ignoreCase = true) }
                ?: songs.firstOrNull()

            // 3) Does an album match the query?
            val matchingAlbum = playlists.firstOrNull { it.isAlbum && it.title.equals(trimmedQuery, ignoreCase = true) }

            if (exactArtist != null && exactSong == null) {
                topResult = SearchTopResult(
                    type = "ARTIST",
                    id = exactArtist.id,
                    title = exactArtist.name,
                    subtitle = exactArtist.subscribersOrAudience,
                    thumbnailUrl = exactArtist.thumbnailUrl,
                    artistItem = exactArtist
                )
            } else if (matchingSong != null) {
                topResult = SearchTopResult(
                    type = "SONG",
                    id = matchingSong.videoId,
                    title = matchingSong.title,
                    subtitle = matchingSong.artists,
                    thumbnailUrl = matchingSong.thumbnailUrl,
                    songItem = matchingSong
                )
            } else if (exactArtist != null) {
                topResult = SearchTopResult(
                    type = "ARTIST",
                    id = exactArtist.id,
                    title = exactArtist.name,
                    subtitle = exactArtist.subscribersOrAudience,
                    thumbnailUrl = exactArtist.thumbnailUrl,
                    artistItem = exactArtist
                )
            } else if (matchingAlbum != null) {
                topResult = SearchTopResult(
                    type = "ALBUM",
                    id = matchingAlbum.id,
                    title = matchingAlbum.title,
                    subtitle = matchingAlbum.author,
                    thumbnailUrl = matchingAlbum.thumbnailUrl,
                    playlistItem = matchingAlbum
                )
            } else if (artists.isNotEmpty()) {
                val firstArtist = artists.first()
                topResult = SearchTopResult(
                    type = "ARTIST",
                    id = firstArtist.id,
                    title = firstArtist.name,
                    subtitle = firstArtist.subscribersOrAudience,
                    thumbnailUrl = firstArtist.thumbnailUrl,
                    artistItem = firstArtist
                )
            } else if (playlists.isNotEmpty()) {
                val firstPl = playlists.first()
                topResult = SearchTopResult(
                    type = if (firstPl.isAlbum) "ALBUM" else "PLAYLIST",
                    id = firstPl.id,
                    title = firstPl.title,
                    subtitle = firstPl.author,
                    thumbnailUrl = firstPl.thumbnailUrl,
                    playlistItem = firstPl
                )
            } else {
                topResult = null
            }
        }

        return ComprehensiveSearchResult(
            topResult = topResult,
            songs = songs,
            artists = artists,
            playlists = playlists
        )
    }

    private fun parseArtistsFromSearch(root: JsonObject): List<ArtistItem> {
        val list = mutableListOf<ArtistItem>()

        // Check if there is a musicCardShelfRenderer (card shelf)
        val cardShelves = findAllNodes(root, "musicCardShelfRenderer")
        if (cardShelves.isNotEmpty()) {
            val card = cardShelves.first()
            val title = extractRunsText(card.getAsJsonObject("title"))
            val subtitle = extractRunsText(card.getAsJsonObject("subtitle"))
            val thumbnail = extractLastThumbnail(card)
            val cardBrowse = card.getAsJsonObject("onTap")?.getAsJsonObject("browseEndpoint")
                ?: card.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")
            val browseId = cardBrowse?.get("browseId")?.asString.orEmpty()
            val pageType = cardBrowse?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()
            val subLower = subtitle.lowercase()
            val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                subLower.startsWith("artist") ||
                subLower.contains("artist •") ||
                browseId.startsWith("UC")

            if (isArtist && browseId.isNotEmpty()) {
                val cleanedSub = subtitle.replace(Regex("""^Artist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                list.add(
                    ArtistItem(
                        id = browseId,
                        name = title,
                        subscribersOrAudience = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail
                    )
                )
            }
        }

        val rows = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (item in rows) {
            val flexColumns = item.getAsJsonArray("flexColumns") ?: continue
            if (flexColumns.size() == 0) continue
            val col0 = flexColumns.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            val title = extractRunsText(col0?.getAsJsonObject("text"))
            if (title.isEmpty()) continue
            val col1 = if (flexColumns.size() > 1) {
                flexColumns.get(1)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            } else null
            val subtitle = extractRunsText(col1?.getAsJsonObject("text"))

            val browseEndpoints = findAllNodes(item, "browseEndpoint")
            val browseId = browseEndpoints.firstOrNull()?.get("browseId")?.asString.orEmpty()
            val pageType = browseEndpoints.firstOrNull()?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()
            val subLower = subtitle.lowercase()
            val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                subLower.startsWith("artist") ||
                subLower.contains("artist •") ||
                (browseId.startsWith("UC") && (subLower.contains("subscribers") || subLower.contains("monthly") || subLower.contains("audience")))

            if (isArtist && browseId.isNotEmpty() && list.none { it.id == browseId || it.name.equals(title, ignoreCase = true) }) {
                val thumbnail = extractLastThumbnail(item)
                val cleanedSub = subtitle.replace(Regex("""^Artist\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                list.add(
                    ArtistItem(
                        id = browseId,
                        name = title,
                        subscribersOrAudience = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail
                    )
                )
            }
        }
        return list
    }

    internal fun parsePlaylistsFromSearch(root: JsonObject): List<PlaylistItem> {
        val list = mutableListOf<PlaylistItem>()
        val rows = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (item in rows) {
            val flexColumns = item.getAsJsonArray("flexColumns") ?: continue
            if (flexColumns.size() == 0) continue
            val col0 = flexColumns.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            val title = extractRunsText(col0?.getAsJsonObject("text"))
            if (title.isEmpty()) continue
            val col1 = if (flexColumns.size() > 1) {
                flexColumns.get(1)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            } else null
            val subtitle = extractRunsText(col1?.getAsJsonObject("text"))

            val isAlbum = subtitle.contains("Album", ignoreCase = true) ||
                subtitle.contains("Single", ignoreCase = true) ||
                subtitle.contains("EP", ignoreCase = true)
            val nav = item.getAsJsonObject("navigationEndpoint")
                ?: col0?.getAsJsonObject("text")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.getAsJsonObject("navigationEndpoint")
            val browseId = extractPlaylistOrAlbumBrowseId(item, nav, preferAlbum = isAlbum)

            if (browseId.isNotEmpty() && list.none { it.id == browseId }) {
                val thumbnail = extractLastThumbnail(item)
                val cleanedSub = subtitle.replace(Regex("""^(?:Playlist|Album|Single|EP)\s*•\s*""", RegexOption.IGNORE_CASE), "").trim()
                list.add(
                    PlaylistItem(
                        id = browseId,
                        title = title,
                        author = cleanedSub.ifEmpty { subtitle },
                        thumbnailUrl = thumbnail,
                        isAlbum = isAlbum || browseId.startsWith("MPRE")
                    )
                )
            }
        }
        return list
    }

    /**
     * Resolves the playlist or album browseId from a search/shelf node without accidentally picking
     * a creator channel `UC...` link inside subtitle flexColumns.
     */
    private fun extractPlaylistOrAlbumBrowseId(
        node: JsonObject,
        directNav: JsonObject? = null,
        preferAlbum: Boolean = false
    ): String {
        fun isChannelPageType(pageType: String): Boolean =
            pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || pageType == "MUSIC_PAGE_TYPE_ARTIST"

        fun isValidId(id: String): Boolean {
            return id.isNotEmpty() && !id.startsWith("UC") && !id.startsWith("FE")
        }

        val candidates = mutableListOf<JsonObject>()
        directNav?.getAsJsonObject("browseEndpoint")?.let { candidates.add(it) }
        node.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")?.let { candidates.add(it) }
        node.getAsJsonObject("onTap")?.getAsJsonObject("browseEndpoint")?.let { candidates.add(it) }
        candidates.addAll(findAllNodes(node, "browseEndpoint"))

        val targetPageType = if (preferAlbum) "MUSIC_PAGE_TYPE_ALBUM" else "MUSIC_PAGE_TYPE_PLAYLIST"
        for (pass in 0..1) {
            for (ep in candidates) {
                val id = ep.get("browseId")?.asString.orEmpty().trim()
                if (!isValidId(id)) continue
                val pageType = ep.getAsJsonObject("browseEndpointContextSupportedConfigs")
                    ?.getAsJsonObject("browseEndpointContextMusicConfig")
                    ?.get("pageType")?.asString.orEmpty()
                if (isChannelPageType(pageType)) continue

                val hasKnownPrefix = id.startsWith("VL") || id.startsWith("PL") ||
                    id.startsWith("MPRE") || id.startsWith("RD") || id.startsWith("OLAK")
                if (pass == 0) {
                    if (pageType == targetPageType || pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                        return id
                    }
                } else if (hasKnownPrefix) {
                    return id
                }
            }
        }

        // Fallback to watchEndpoint playlistId (excluding RDAMVM radio auto-mixes if a real PL/VL/OLAK exists)
        val directWatch = directNav?.getAsJsonObject("watchEndpoint")
            ?: node.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")
        val watchList = buildList {
            directWatch?.let { add(it) }
            addAll(findAllNodes(node, "watchEndpoint"))
        }
        val playlistId = watchList
            .mapNotNull { it.get("playlistId")?.asString?.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("UC") }
            ?: return ""

        return if (playlistId.startsWith("VL") || playlistId.startsWith("MPRE")) {
            playlistId
        } else {
            "VL$playlistId"
        }
    }

    private fun parseArtistProfileResult(browseId: String, root: JsonObject): ArtistProfile {
        val header = findAllNodes(root, "musicImmersiveHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicVisualHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicResponsiveHeaderRenderer").firstOrNull()

        val name = extractRunsText(header?.getAsJsonObject("title")).ifEmpty { "Artist" }
        val description = extractRunsText(header?.getAsJsonObject("description"))
        val subBtn = header?.getAsJsonObject("subscriptionButton")?.getAsJsonObject("subscribeButtonRenderer")
        val subscriberCount = extractRunsText(subBtn?.getAsJsonObject("subscriberCountText"))
        val headerThumb = extractLastThumbnail(header ?: root)

        val topSongs = mutableListOf<SongItem>()
        val albums = mutableListOf<ArtistAlbumItem>()
        val singles = mutableListOf<ArtistAlbumItem>()
        val featuredIn = mutableListOf<PlaylistItem>()
        val similarArtists = mutableListOf<ArtistItem>()
        var bio: String? = description.ifEmpty { null }

        // Find Shelves for top songs
        val shelfNodes = findAllNodes(root, "musicShelfRenderer")
        val profileArtistFallback = if (name != "Artist" && !isMissingArtist(name)) name else null
        for (shelf in shelfNodes) {
            val title = extractRunsText(shelf.getAsJsonObject("title"))
            if (title.contains("Songs", ignoreCase = true) || title.contains("Popular", ignoreCase = true) || title.contains("Top", ignoreCase = true)) {
                val listRows = findAllNodes(shelf, "musicResponsiveListItemRenderer")
                for (row in listRows) {
                    val song = parseSongFromListItem(row, fallbackArtist = profileArtistFallback)
                    if (song != null && topSongs.none { it.videoId == song.videoId }) {
                        topSongs.add(song)
                    }
                }
            }
        }

        // Find Carousel Shelves
        val carouselNodes = findAllNodes(root, "musicCarouselShelfRenderer")
        for (shelf in carouselNodes) {
            val basicHeader = findAllNodes(shelf, "musicCarouselShelfBasicHeaderRenderer").firstOrNull()
            val title = extractRunsText(basicHeader?.getAsJsonObject("title"))
            val twoRowItems = findAllNodes(shelf, "musicTwoRowItemRenderer")

            when {
                title.contains("Albums", ignoreCase = true) -> {
                    for (item in twoRowItems) {
                        val albumTitle = extractRunsText(item.getAsJsonObject("title"))
                        if (albumTitle.isEmpty()) continue
                        val albumSub = extractRunsText(item.getAsJsonObject("subtitle"))
                        val nav = item.getAsJsonObject("navigationEndpoint")
                        val bId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString.orEmpty()
                        val thumb = extractLastThumbnail(item)
                        albums.add(
                            ArtistAlbumItem(
                                id = bId,
                                title = albumTitle,
                                year = albumSub,
                                type = "Album",
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
                title.contains("Singles", ignoreCase = true) || title.contains("EP", ignoreCase = true) -> {
                    for (item in twoRowItems) {
                        val singleTitle = extractRunsText(item.getAsJsonObject("title"))
                        if (singleTitle.isEmpty()) continue
                        val singleSub = extractRunsText(item.getAsJsonObject("subtitle"))
                        val nav = item.getAsJsonObject("navigationEndpoint")
                        val bId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString.orEmpty()
                        val thumb = extractLastThumbnail(item)
                        singles.add(
                            ArtistAlbumItem(
                                id = bId,
                                title = singleTitle,
                                year = singleSub,
                                type = "Single",
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
                title.contains("Featured", ignoreCase = true) || title.contains("Playlists", ignoreCase = true) -> {
                    for (item in twoRowItems) {
                        val plTitle = extractRunsText(item.getAsJsonObject("title"))
                        if (plTitle.isEmpty()) continue
                        val plSub = extractRunsText(item.getAsJsonObject("subtitle"))
                        val nav = item.getAsJsonObject("navigationEndpoint")
                        val bId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString.orEmpty()
                        val thumb = extractLastThumbnail(item)
                        featuredIn.add(
                            PlaylistItem(
                                id = bId,
                                title = plTitle,
                                author = plSub,
                                thumbnailUrl = thumb,
                                isAlbum = false
                            )
                        )
                    }
                }
                title.contains("Fans", ignoreCase = true) || title.contains("Similar", ignoreCase = true) || title.contains("Like", ignoreCase = true) -> {
                    for (item in twoRowItems) {
                        val artistName = extractRunsText(item.getAsJsonObject("title"))
                        if (artistName.isEmpty()) continue
                        val artistSub = extractRunsText(item.getAsJsonObject("subtitle"))
                        val nav = item.getAsJsonObject("navigationEndpoint")
                        val bId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString.orEmpty()
                        val thumb = extractLastThumbnail(item)
                        similarArtists.add(
                            ArtistItem(
                                id = bId,
                                name = artistName,
                                subscribersOrAudience = artistSub,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
            }
        }

        // Bio / Description shelf
        val descShelves = findAllNodes(root, "musicDescriptionShelfRenderer")
        if (descShelves.isNotEmpty()) {
            val detailedBio = extractRunsText(descShelves.first().getAsJsonObject("description"))
            if (detailedBio.isNotEmpty()) {
                bio = detailedBio
            }
        }

        return ArtistProfile(
            id = browseId,
            name = name,
            description = description.ifEmpty { null },
            subscriberCount = subscriberCount.ifEmpty { null },
            headerThumbnailUrl = headerThumb,
            avatarThumbnailUrl = headerThumb,
            topSongs = topSongs,
            albums = albums,
            singles = singles,
            featuredIn = featuredIn,
            similarArtists = similarArtists,
            bio = bio
        )
    }

    internal fun parseSongFromListItem(
        node: JsonObject,
        fallbackArtist: String? = null,
        fallbackAlbum: String? = null
    ): SongItem? {
        val flexColumns = node.getAsJsonArray("flexColumns") ?: return null
        val titleCol = flexColumns.get(0)?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
        val title = extractRunsText(titleCol?.getAsJsonObject("text"))
        if (title.isEmpty()) return null

        val subtitleCol = if (flexColumns.size() > 1) {
            flexColumns.get(1)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
        } else null
        val rawSubtitle = extractRunsText(subtitleCol?.getAsJsonObject("text"))

        // Reject if item is an artist, album, playlist, podcast, or profile
        val subLower = rawSubtitle.lowercase().trim()
        if (Regex("""^(?:artist|album|single|ep|playlist|podcast|episode|profile)(?:\s*•|$)""").containsMatchIn(subLower) ||
            subLower.contains("artist •")
        ) {
            return null
        }

        val nav = node.getAsJsonObject("navigationEndpoint")
            ?: titleCol?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")
        val pageType = nav?.getAsJsonObject("browseEndpoint")?.getAsJsonObject("browseEndpointContextSupportedConfigs")
            ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()
        if (pageType in listOf(
                "MUSIC_PAGE_TYPE_ARTIST",
                "MUSIC_PAGE_TYPE_ALBUM",
                "MUSIC_PAGE_TYPE_PLAYLIST",
                "MUSIC_PAGE_TYPE_USER_CHANNEL",
                "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE",
                "MUSIC_PAGE_TYPE_PODCAST_EPISODE_DETAIL_PAGE"
            )
        ) {
            return null
        }

        val videoId = extractVideoIdFromListItem(node, nav) ?: return null

        val (subArtist, subAlbum, subDuration) = parseSubtitleFields(rawSubtitle)
        var artist = subArtist
        var album = subAlbum
        var duration = subDuration

        // Check fixedColumns for track duration (Albums & Playlists put duration in fixedColumns)
        if (duration.isNullOrBlank()) {
            duration = extractDurationFromFixedColumns(node)
        }

        // Check flexColumns 1..N for explicit artist / album navigation endpoints
        if (isMissingArtist(artist)) {
            for (i in 1 until flexColumns.size()) {
                val colText = flexColumns.get(i)?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                val runArtist = extractArtistFromRuns(colText)
                if (!runArtist.isNullOrBlank()) {
                    artist = runArtist
                    break
                }
            }
        }
        if (album == null) {
            for (i in 1 until flexColumns.size()) {
                val colText = flexColumns.get(i)?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                val runAlbum = extractAlbumFromRuns(colText)
                if (!runAlbum.isNullOrBlank()) {
                    album = runAlbum
                    break
                }
            }
        }

        // Fallback to additional flexColumns if col 1 was empty (e.g. table layout)
        if (isMissingArtist(artist) && flexColumns.size() > 2) {
            for (i in 2 until flexColumns.size()) {
                val colText = flexColumns.get(i)?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                val candidate = extractRunsText(colText)
                if (candidate.isNotBlank() &&
                    !isDuration(candidate) &&
                    !isMetadataMetricPart(candidate) &&
                    !candidate.equals(album, ignoreCase = true) &&
                    !candidate.equals(fallbackAlbum, ignoreCase = true) &&
                    !isMissingArtist(candidate)
                ) {
                    artist = cleanTopicArtist(candidate)
                    break
                }
            }
        }

        // Fallback to accessibility label on the play button overlay (strips duration suffixes cleanly)
        if (isMissingArtist(artist)) {
            val a11yArtist = extractArtistFromAccessibility(node, title)
            if (!a11yArtist.isNullOrBlank() && !isMissingArtist(a11yArtist)) {
                artist = a11yArtist
            }
        }

        // Fallback to enclosing context artist (e.g. Album straplineTextOne, ArtistProfile name, Artist card title)
        if (isMissingArtist(artist) && !isMissingArtist(fallbackArtist)) {
            artist = cleanTopicArtist(fallbackArtist!!)
        }

        if (isMissingArtist(artist)) {
            artist = "Unknown Artist"
        }

        val thumbnail = extractLastThumbnail(node)

        return SongItem(
            videoId = videoId,
            title = title,
            artists = artist,
            album = album ?: fallbackAlbum,
            duration = duration,
            durationSeconds = parseDurationToSeconds(duration),
            thumbnailUrl = thumbnail
        )
    }

    internal fun parseSongFromTwoRowItem(node: JsonObject): SongItem? {
        val subtitle = extractRunsText(node.getAsJsonObject("subtitle"))
        val subLower = subtitle.lowercase()
        if (subLower.startsWith("artist") ||
            subLower.contains("artist •") ||
            subLower.startsWith("album") ||
            subLower.startsWith("single") ||
            subLower.startsWith("ep") ||
            subLower.startsWith("playlist") ||
            subLower.startsWith("podcast") ||
            subLower.startsWith("episode") ||
            subLower.startsWith("profile")
        ) {
            return null
        }

        val nav = node.getAsJsonObject("navigationEndpoint")
        val pageType = nav?.getAsJsonObject("browseEndpoint")?.getAsJsonObject("browseEndpointContextSupportedConfigs")
            ?.getAsJsonObject("browseEndpointContextMusicConfig")?.get("pageType")?.asString.orEmpty()
        if (pageType in listOf(
                "MUSIC_PAGE_TYPE_ARTIST",
                "MUSIC_PAGE_TYPE_ALBUM",
                "MUSIC_PAGE_TYPE_PLAYLIST",
                "MUSIC_PAGE_TYPE_USER_CHANNEL",
                "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE",
                "MUSIC_PAGE_TYPE_PODCAST_EPISODE_DETAIL_PAGE"
            )
        ) {
            return null
        }

        val watch = nav?.getAsJsonObject("watchEndpoint")
        val videoId = watch?.get("videoId")?.asString
            ?: findAllStrings(node, "videoId").firstOrNull()
            ?: return null

        val title = extractRunsText(node.getAsJsonObject("title"))
        if (title.isEmpty()) return null

        val (parsedArtist, parsedAlbum, parsedDuration) = parseSubtitleFields(subtitle)
        val artistFromRuns = extractArtistFromRuns(node.getAsJsonObject("subtitle"))
        val a11yArtist = extractArtistFromAccessibility(node, title)
        val artist = when {
            !isMissingArtist(parsedArtist) -> parsedArtist
            !artistFromRuns.isNullOrBlank() -> artistFromRuns
            !a11yArtist.isNullOrBlank() -> a11yArtist
            else -> "Unknown Artist"
        }

        val thumbnail = extractLastThumbnail(node)

        return SongItem(
            videoId = videoId,
            title = title,
            artists = artist,
            album = parsedAlbum,
            duration = parsedDuration,
            durationSeconds = parseDurationToSeconds(parsedDuration),
            thumbnailUrl = thumbnail
        )
    }

    private data class SubtitleFields(
        val artist: String,
        val album: String?,
        val duration: String?
    )

    private fun parseSubtitleFields(rawSubtitle: String): SubtitleFields {
        val cleanedSubtitle = rawSubtitle
            .replace(Regex("""^(Song|Video|Single|Episode)\s*•\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        val parts = cleanedSubtitle.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val nonMetricParts = parts.filter { !isDuration(it) && !isMetadataMetricPart(it) }
        val artist = if (nonMetricParts.isNotEmpty()) cleanTopicArtist(nonMetricParts[0]) else ""
        val album = if (nonMetricParts.size > 1 && nonMetricParts[1].isNotBlank()) nonMetricParts[1] else null
        val duration = parts.find { isDuration(it) }
        return SubtitleFields(artist = artist, album = album, duration = duration)
    }

    private fun isMetadataMetricPart(part: String): Boolean {
        val lower = part.trim().lowercase()
        return lower.contains("views") ||
            lower.contains("plays") ||
            lower.contains("likes") ||
            lower.contains("subscribers") ||
            lower.contains("monthly") ||
            lower.contains("audience") ||
            lower.matches(Regex("""^\d+\s+(songs?|tracks?)$"""))
    }

    private fun extractVideoIdFromListItem(node: JsonObject, nav: JsonObject? = null): String? {
        return node.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
            ?: nav?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
            ?: node.getAsJsonObject("overlay")
                ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("musicPlayButtonRenderer")
                ?.getAsJsonObject("playNavigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
            ?: findAllStrings(node, "videoId").firstOrNull()
    }

    private fun extractDurationFromFixedColumns(node: JsonObject): String? {
        val fixedCols = node.getAsJsonArray("fixedColumns") ?: return null
        for (el in fixedCols) {
            val textObj = el.asJsonObject
                ?.getAsJsonObject("musicResponsiveListItemFixedColumnRenderer")
                ?.getAsJsonObject("text")
            val candidate = extractRunsText(textObj)
            if (isDuration(candidate)) return candidate
        }
        return null
    }

    private fun extractArtistFromRuns(textObj: JsonObject?): String? {
        val runs = textObj?.getAsJsonArray("runs") ?: return null
        val artistNames = mutableListOf<String>()
        for (el in runs) {
            val run = el.asJsonObject ?: continue
            val text = run.get("text")?.asString?.trim().orEmpty()
            if (text.isEmpty() || text == "•" || text == "," || text == "&") continue
            val browse = run.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint") ?: continue
            val bid = browse.get("browseId")?.asString.orEmpty()
            val pt = browse.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")
                ?.get("pageType")?.asString.orEmpty()
            if (pt == "MUSIC_PAGE_TYPE_ARTIST" || bid.startsWith("UC")) {
                artistNames.add(cleanTopicArtist(text))
            }
        }
        return artistNames.distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun extractAlbumFromRuns(textObj: JsonObject?): String? {
        val runs = textObj?.getAsJsonArray("runs") ?: return null
        for (el in runs) {
            val run = el.asJsonObject ?: continue
            val text = run.get("text")?.asString?.trim().orEmpty()
            if (text.isEmpty()) continue
            val browse = run.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint") ?: continue
            val bid = browse.get("browseId")?.asString.orEmpty()
            val pt = browse.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")
                ?.get("pageType")?.asString.orEmpty()
            if (pt == "MUSIC_PAGE_TYPE_ALBUM" || bid.startsWith("MPRE")) {
                return text
            }
        }
        return null
    }

    internal fun extractArtistFromAccessibility(node: JsonObject, title: String): String? {
        val playA11y = node.getAsJsonObject("overlay")
            ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("musicPlayButtonRenderer")
            ?.getAsJsonObject("accessibilityPlayData")
            ?.getAsJsonObject("accessibilityData")
            ?.get("label")?.asString
            ?: return null

        var clean = playA11y.removePrefix("Play ").removePrefix("Pause ").trim()
        // Strip trailing spoken duration such as " - 3 minutes, 42 seconds" or " - 1 hour, 33 minutes, 29 seconds"
        clean = clean.replace(
            Regex("""\s+-\s+\d+\s+(?:hour|hours|minute|minutes|second|seconds)(?:,\s*\d+\s+(?:hour|hours|minute|minutes|second|seconds))*$""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        if (clean.equals(title, ignoreCase = true) ||
            title.contains(clean, ignoreCase = true) ||
            clean.isEmpty()
        ) {
            return null
        }

        // Prefer exact "Title - Artist" prefix match so dashes inside the title don't confuse extraction
        val titlePrefix = "$title - "
        if (clean.startsWith(titlePrefix, ignoreCase = true)) {
            val extracted = clean.substring(titlePrefix.length).trim()
            if (extracted.isNotEmpty() && !isMissingArtist(extracted)) {
                return cleanTopicArtist(extracted)
            }
        }

        // Only fall back to last " - " if the title itself does not already end with that suffix
        val lastDash = clean.lastIndexOf(" - ")
        if (lastDash != -1) {
            val rightPart = clean.substring(lastDash + 3).trim()
            if (rightPart.isNotEmpty() &&
                !title.endsWith(" - $rightPart", ignoreCase = true) &&
                !title.contains(" - $rightPart", ignoreCase = true) &&
                !isMissingArtist(rightPart)
            ) {
                return cleanTopicArtist(rightPart)
            }
        }
        return null
    }

    private fun findLyricsBrowseId(root: JsonObject): String? {
        val endpoints = findAllNodes(root, "browseEndpoint")
        for (ep in endpoints) {
            val bid = ep.get("browseId")?.asString
            if (bid?.startsWith("MPLY") == true) return bid

            val config = ep.getAsJsonObject("browseEndpointContextSupportedConfigs")
                ?.getAsJsonObject("browseEndpointContextMusicConfig")
            val pageType = config?.get("pageType")?.asString ?: ep.get("pageType")?.asString
            if (pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS") {
                if (!bid.isNullOrEmpty()) return bid
            }
        }
        return null
    }

    private fun extractRunsText(obj: JsonObject?): String {
        if (obj == null) return ""
        val runs = obj.getAsJsonArray("runs")
        if (runs != null) {
            val sb = StringBuilder()
            for (r in runs) {
                sb.append(r.asJsonObject.get("text")?.asString.orEmpty())
            }
            return sb.toString().trim()
        }
        return obj.get("simpleText")?.asString.orEmpty()
    }

    private fun extractLastThumbnail(node: JsonObject): String? {
        val thumbArrays = findAllArrays(node, "thumbnails")
        for (arr in thumbArrays) {
            if (arr.size() > 0) {
                var bestUrl: String? = null
                var maxWidth = -1
                for (el in arr) {
                    if (el.isJsonObject) {
                        val obj = el.asJsonObject
                        val w = obj.get("width")?.asInt ?: 0
                        val u = obj.get("url")?.asString
                        if (!u.isNullOrEmpty() && w >= maxWidth) {
                            maxWidth = w
                            bestUrl = u
                        }
                    }
                }
                if (!bestUrl.isNullOrEmpty()) {
                    return getHighResThumbnailUrl(bestUrl)
                }
            }
        }
        val thumbs = findAllNodes(node, "thumbnails")
        for (t in thumbs) {
            val url = t.get("url")?.asString
            if (!url.isNullOrEmpty()) return getHighResThumbnailUrl(url)
        }
        return null
    }

    private fun isDuration(s: String): Boolean {
        val trimmed = s.trim()
        val parts = trimmed.split(":")
        return parts.size in 2..3 && parts.all { it.all { c -> c.isDigit() } }
    }

    private fun parseDurationToSeconds(duration: String?): Int {
        if (duration.isNullOrEmpty()) return 0
        val parts = duration.trim().split(":")
        var total = 0
        for (p in parts) {
            total = total * 60 + (p.toIntOrNull() ?: 0)
        }
        return total
    }

    private fun findAllNodes(root: JsonObject, key: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun walk(obj: JsonObject) {
            for ((k, v) in obj.entrySet()) {
                if (k == key && v.isJsonObject) {
                    result.add(v.asJsonObject)
                }
                if (v.isJsonObject) {
                    walk(v.asJsonObject)
                } else if (v.isJsonArray) {
                    for (el in v.asJsonArray) {
                        if (el.isJsonObject) walk(el.asJsonObject)
                    }
                }
            }
        }
        walk(root)
        return result
    }

    private fun findAllArrays(root: JsonObject, key: String): List<JsonArray> {
        val result = mutableListOf<JsonArray>()
        fun walk(obj: JsonObject) {
            for ((k, v) in obj.entrySet()) {
                if (k == key && v.isJsonArray) {
                    result.add(v.asJsonArray)
                }
                if (v.isJsonObject) {
                    walk(v.asJsonObject)
                } else if (v.isJsonArray) {
                    for (el in v.asJsonArray) {
                        if (el.isJsonObject) walk(el.asJsonObject)
                    }
                }
            }
        }
        walk(root)
        return result
    }

    private fun findAllStrings(root: JsonObject, key: String): List<String> {
        val result = mutableListOf<String>()
        fun walk(obj: JsonObject) {
            for ((k, v) in obj.entrySet()) {
                if (k == key && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                    val s = v.asString
                    if (s.isNotEmpty()) result.add(s)
                }
                if (v.isJsonObject) {
                    walk(v.asJsonObject)
                } else if (v.isJsonArray) {
                    for (el in v.asJsonArray) {
                        if (el.isJsonObject) walk(el.asJsonObject)
                    }
                }
            }
        }
        walk(root)
        return result
    }
}
