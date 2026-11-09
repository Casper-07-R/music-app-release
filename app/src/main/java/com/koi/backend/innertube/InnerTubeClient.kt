package com.koi.backend.innertube

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
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
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // Web client for search / next / browse
        const val WEB_CLIENT_NAME = "WEB_REMIX"
        const val WEB_CLIENT_VERSION = "1.20260213.01.00"
        const val WEB_CLIENT_ID = "67"
        const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

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
        val actualBrowseId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
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
     * Resolves a videoId to a playable direct stream using the Ryotunes STREAM_FALLBACK_ORDER:
     * 1. VISIONOS (direct un-ciphered stream, accepts open-ended Range requests)
     * 2. ANDROID_VR 1.65.10
     * 3. ANDROID_VR 1.43.32
     * Resolves a videoId to the highest possible acoustic quality stream across available clients.
     * Evaluates formats using perceptual audio quality scoring and the Ryotunes HIGH two-pass strategy.
     * Includes stream probing (validateStream) and anti-bot gate auto-recovery with fresh visitorData.
     */
    suspend fun resolveStream(videoId: String): PlaybackStream? = withContext(Dispatchers.IO) {
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
                    Log.d(TAG, "Locked in optimal high-fidelity stream for $videoId with ${stream.clientName} (itag=${stream.itag}, ${stream.bitrate / 1000}kbps, mime=${stream.mimeType})")
                    return@withContext bestCandidate
                }
            }
        }

        if (bestCandidate != null) {
            Log.d(TAG, "Resolved best available stream for $videoId with ${bestCandidate.clientName} (itag=${bestCandidate.itag}, ${bestCandidate.bitrate / 1000}kbps)")
            return@withContext bestCandidate
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
                    return@withContext bestCandidate
                }
            }
        }

        if (bestCandidate != null) {
            Log.d(TAG, "Resolved best available stream after refresh for $videoId with ${bestCandidate.clientName}")
        } else {
            Log.e(TAG, "Failed to resolve playable stream with any client for $videoId")
        }
        bestCandidate
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
        for ((score, format) in audioCandidates) {
            val url = format.get("url")?.asString ?: continue
            val itag = format.get("itag")?.asInt ?: 0
            val mimeType = format.get("mimeType")?.asString ?: "audio/webm"
            val bitrate = format.get("bitrate")?.asLong ?: 160000L

            // Validate stream via lightweight probe matching Ryotunes validate_head
            if (validateStream(url, client.userAgent)) {
                Log.d(TAG, "Validated stream: itag=$itag, ${bitrate / 1000}kbps, mime=$mimeType (score=$score) on ${client.key}")
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
                    contentLength = format.get("contentLength")?.asString?.toLongOrNull()
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
            val song = parseSongFromListItem(node)
            if (song != null) {
                items.add(song)
            }
        }
        return items
    }

    private fun parseNextResult(root: JsonObject): NextResult {
        val items = mutableListOf<SongItem>()
        val panelVideos = findAllNodes(root, "playlistPanelVideoRenderer")
        for (node in panelVideos) {
            val videoId = node.get("videoId")?.asString.orEmpty()
            val title = extractRunsText(node.getAsJsonObject("title"))
            val byline = node.getAsJsonObject("longBylineText") ?: node.getAsJsonObject("shortBylineText")
            val artist = extractRunsText(byline)
            val lengthText = extractRunsText(node.getAsJsonObject("lengthText"))
            val thumb = extractLastThumbnail(node)

            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                items.add(
                    SongItem(
                        videoId = videoId,
                        title = title,
                        artists = artist,
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

    private fun parsePlaylistResult(browseId: String, root: JsonObject): PlaylistResult {
        val header = findAllNodes(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicEditablePlaylistDetailHeaderRenderer").firstOrNull()
            ?: findAllNodes(root, "musicDetailHeaderRenderer").firstOrNull()

        val title = extractRunsText(header?.getAsJsonObject("title")).ifEmpty { "Playlist" }
        val subtitle = extractRunsText(header?.getAsJsonObject("subtitle"))
        val description = extractRunsText(header?.getAsJsonObject("description"))
        val thumbnail = extractLastThumbnail(header ?: root)

        val tracks = mutableListOf<SongItem>()
        val rows = findAllNodes(root, "musicResponsiveListItemRenderer")
        for (node in rows) {
            val song = parseSongFromListItem(node)
            if (song != null) {
                tracks.add(song)
            }
        }

        return PlaylistResult(
            id = browseId,
            title = title,
            subtitle = subtitle,
            description = description,
            thumbnailUrl = thumbnail,
            tracks = tracks
        )
    }

    private fun parseSongFromListItem(node: JsonObject): SongItem? {
        val videoId = node.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
            ?: node.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
            ?: findAllStrings(node, "videoId").firstOrNull()
            ?: return null

        val flexColumns = node.getAsJsonArray("flexColumns") ?: return null
        val titleCol = flexColumns.get(0)?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
        val title = extractRunsText(titleCol?.getAsJsonObject("text"))
        if (title.isEmpty()) return null

        val subtitleCol = flexColumns.get(1)?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
        val subtitle = extractRunsText(subtitleCol?.getAsJsonObject("text"))

        // Split subtitle by bullet •
        val parts = subtitle.split("•").map { it.trim() }
        val artist = if (parts.isNotEmpty()) parts[0] else "Unknown Artist"
        val album = if (parts.size > 1 && !isDuration(parts[1])) parts[1] else null
        val duration = parts.find { isDuration(it) }

        val thumbnail = extractLastThumbnail(node)

        return SongItem(
            videoId = videoId,
            title = title,
            artists = artist,
            album = album,
            duration = duration,
            durationSeconds = parseDurationToSeconds(duration),
            thumbnailUrl = thumbnail
        )
    }

    private fun parseSongFromTwoRowItem(node: JsonObject): SongItem? {
        val nav = node.getAsJsonObject("navigationEndpoint")
        val watch = nav?.getAsJsonObject("watchEndpoint")
        val videoId = watch?.get("videoId")?.asString
            ?: findAllStrings(node, "videoId").firstOrNull()
            ?: return null

        val title = extractRunsText(node.getAsJsonObject("title"))
        if (title.isEmpty()) return null

        val subtitle = extractRunsText(node.getAsJsonObject("subtitle"))
        val thumbnail = extractLastThumbnail(node)

        return SongItem(
            videoId = videoId,
            title = title,
            artists = subtitle.ifEmpty { "Various Artists" },
            thumbnailUrl = thumbnail
        )
    }

    private fun findLyricsBrowseId(root: JsonObject): String? {
        val endpoints = findAllNodes(root, "browseEndpoint")
        for (ep in endpoints) {
            val pageType = ep.get("pageType")?.asString
            if (pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS") {
                val bid = ep.get("browseId")?.asString
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
