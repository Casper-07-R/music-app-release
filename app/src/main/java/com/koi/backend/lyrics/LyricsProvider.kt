package com.koi.backend.lyrics

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LyricsProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "LyricsProvider"
        private const val LRCLIB_BASE = "https://lrclib.net/api"
        private const val USER_AGENT = "Ryotunes/1.0.1 (Koi/Android)"
    }

    /**
     * Fetches lyrics following Ryotunes provider chain:
     * 1. LRCLIB exact match (/api/get)
     * 2. LRCLIB search fallback (/api/search)
     * 3. LRC parser for millisecond timestamps
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = 0
    ): Lyrics? = withContext(Dispatchers.IO) {
        // 1. Try LRCLIB exact get
        val exact = fetchLrclibExact(title, artist, album, durationSeconds)
        if (exact != null) {
            val parsed = parseLrclibResponse(exact)
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return@withContext parsed
            }
        }

        // 2. Try LRCLIB search
        val searched = fetchLrclibSearch(title, artist, durationSeconds)
        if (searched != null) {
            val parsed = parseLrclibResponse(searched)
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return@withContext parsed
            }
        }

        return@withContext null
    }

    private fun fetchLrclibExact(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int
    ): JsonObject? {
        return try {
            val urlBuilder = (LRCLIB_BASE + "/get").toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("track_name", title)
            urlBuilder.addQueryParameter("artist_name", artist)
            if (!album.isNullOrBlank()) {
                urlBuilder.addQueryParameter("album_name", album)
            }
            if (durationSeconds > 0) {
                urlBuilder.addQueryParameter("duration", durationSeconds.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                JsonParser.parseString(body).asJsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB exact get failed for $title - $artist", e)
            null
        }
    }

    private fun fetchLrclibSearch(
        title: String,
        artist: String,
        durationSeconds: Int
    ): JsonObject? {
        return try {
            val urlBuilder = (LRCLIB_BASE + "/search").toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("track_name", title)
            urlBuilder.addQueryParameter("artist_name", artist)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string().orEmpty()
            val arr = JsonParser.parseString(body).asJsonArray
            if (arr.size() == 0) return null

            // Find closest candidate by duration
            var bestItem: JsonObject? = null
            var minDiff = Double.MAX_VALUE

            for (el in arr) {
                val item = el.asJsonObject
                val synced = item.get("syncedLyrics")?.asString.orEmpty()
                val d = item.get("duration")?.asDouble ?: 0.0

                val diff = if (durationSeconds > 0 && d > 0.0) {
                    Math.abs(durationSeconds.toDouble() - d)
                } else {
                    100.0
                }

                // Prefer synced lyrics within 5s
                if (synced.isNotEmpty() && diff < minDiff) {
                    minDiff = diff
                    bestItem = item
                } else if (bestItem == null) {
                    bestItem = item
                }
            }

            bestItem
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB search failed for $title - $artist", e)
            null
        }
    }

    private fun parseLrclibResponse(json: JsonObject): Lyrics? {
        val synced = json.get("syncedLyrics")?.asString
        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return Lyrics(
                    source = "LRCLIB",
                    synced = true,
                    lines = lines
                )
            }
        }

        val plain = json.get("plainLyrics")?.asString
        if (!plain.isNullOrBlank()) {
            val lines = plain.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }

            if (lines.isNotEmpty()) {
                return Lyrics(
                    source = "LRCLIB",
                    synced = false,
                    lines = lines
                )
            }
        }

        return null
    }

    /**
     * Exact LRC timestamp parser as implemented in Ryotunes (lyrics.rs parse_lrc):
     * Parses [mm:ss.xx] and [mm:ss.xxx] into millisecond offsets.
     */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val lines = lrcText.lines()

        for (raw in lines) {
            var rest = raw.trim()
            val times = mutableListOf<Long>()

            while (rest.startsWith("[")) {
                val end = rest.indexOf(']')
                if (end == -1) break
                val tag = rest.substring(1, end)
                val timeMs = parseLrcTime(tag)
                if (timeMs != null) {
                    times.add(timeMs)
                    rest = rest.substring(end + 1).trimStart()
                } else {
                    // Not a timestamp (metadata like [ar: ...] or bracketed lyric [Chorus])
                    break
                }
            }

            for (timeMs in times) {
                out.add(LyricLine(timeMs = timeMs, text = rest))
            }
        }

        out.sortBy { it.timeMs ?: 0L }
        return out
    }

    private fun parseLrcTime(tag: String): Long? {
        val colon = tag.indexOf(':')
        if (colon == -1) return null
        val minuteStr = tag.substring(0, colon).trim()
        val rest = tag.substring(colon + 1).trim()
        val m = minuteStr.toLongOrNull() ?: return null

        val dot = rest.indexOf('.')
        val secondStr = if (dot != -1) rest.substring(0, dot) else rest
        val fracStr = if (dot != -1) rest.substring(dot + 1) else null
        val s = secondStr.toLongOrNull() ?: return null

        val ms = if (fracStr != null) {
            val digits = fracStr.filter { it.isDigit() }.take(3)
            val v = digits.toLongOrNull() ?: 0L
            when (digits.length) {
                1 -> v * 100
                2 -> v * 10
                else -> v
            }
        } else {
            0L
        }

        return (m * 60 + s) * 1000 + ms
    }
}
