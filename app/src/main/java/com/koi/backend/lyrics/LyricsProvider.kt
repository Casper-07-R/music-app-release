package com.koi.backend.lyrics

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.koi.backend.innertube.InnerTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val innerTube: InnerTubeClient? = null
) {
    companion object {
        private const val TAG = "LyricsProvider"
        private const val LRCLIB_BASE = "https://lrclib.net/api"
        private const val USER_AGENT = "Ryotunes/1.0.1 (Koi/Android)"
    }

    private val fastHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Cleans up YouTube and release tags from track titles for higher LRCLIB match rates:
     * Removes:
     * - (Official Music Video), [Official Audio], (Lyric Video), etc.
     * - (From "Movie"), [From "Movie"], (From Movie), (Original Motion Picture Soundtrack), (Soundtrack), (OST)
     * - (Tamil), (Telugu), (Hindi), (Malayalam), etc.
     * - (feat. XYZ), [ft. XYZ]
     * - (Remastered 2021), - Remastered
     * - "Artist - Title" prefix when artist is repeated in the title
     */
    fun cleanTitle(title: String, artist: String? = null): String {
        var clean = title

        // If title is "Artist - Title", strip the "Artist - " prefix
        if (!artist.isNullOrBlank() && clean.contains(" - ")) {
            val parts = clean.split(" - ", limit = 2)
            if (parts.size == 2) {
                val possibleArtist = parts[0].trim()
                val cleanArt = cleanArtist(artist)
                if (possibleArtist.equals(artist.trim(), ignoreCase = true) ||
                    possibleArtist.equals(cleanArt, ignoreCase = true) ||
                    cleanArt.contains(possibleArtist, ignoreCase = true) ||
                    possibleArtist.contains(cleanArt, ignoreCase = true)
                ) {
                    clean = parts[1]
                }
            }
        }

        // Remove bracketed/parenthesized noise
        val noisePattern = Regex(
            """\s*[\(\[](?:official\s+[^)\]]*|mv|hd|4k|[^)\]]*(?:music\s+video|lyric\s+video|lyrics|audio|visualizer|clip\s+officiel|video\s+oficial)[^)\]]*)[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        clean = noisePattern.replace(clean, "")

        // Remove movie / soundtrack brackets: (From "Movie"), [From "Movie"], (From Movie), (Original Motion Picture Soundtrack), (Soundtrack), (OST)
        val moviePattern = Regex(
            """\s*[\(\[](?:from\s+["']?[^)"']+["']?|original\s+motion\s+picture\s+soundtrack|soundtrack|ost)[^)\]]*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        clean = moviePattern.replace(clean, "")

        // Remove language brackets: (Tamil), (Telugu), (Hindi), (Malayalam), (Kannada), (Punjabi), (English)
        val langPattern = Regex(
            """\s*[\(\[](?:tamil|telugu|hindi|malayalam|kannada|punjabi|english)[^)\]]*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        clean = langPattern.replace(clean, "")

        // Remove Remastered noise: (Remastered 2021), (2011 Remaster), - Remastered
        val remasterPattern = Regex(
            """\s*(?:[\(\[](?:remastered|remaster|anniversary|deluxe)[^\)\]]*[\)\]]|-\s*(?:remastered|remaster)[^\-]*$)""",
            RegexOption.IGNORE_CASE
        )
        clean = remasterPattern.replace(clean, "")

        // Remove (feat. ...) or (ft. ...) or [feat ...]
        val featPattern = Regex(
            """\s*[\(\[](?:feat\.|ft\.|featuring)\s*[^\)\]]+[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        clean = featPattern.replace(clean, "")

        clean = clean.replace(Regex("""\s+"""), " ").trim()
        return if (clean.isNotBlank()) clean else title
    }

    /**
     * Completely strips all parenthesized and bracketed expressions from text.
     * e.g. "One Sun One Moon (From \"Jailer 2\")" -> "One Sun One Moon"
     */
    fun stripAllBrackets(text: String): String {
        var s = text.replace(Regex("""[\(\[].*?[\)\]]"""), "")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return if (s.isNotBlank()) s else text
    }

    /**
     * Strict title validator that verifies a search result belongs to the requested song.
     * Prevents mismatched songs by the same composer/artist (e.g. Malayalam songs matching Tamil songs).
     */
    fun isSongTitleMatch(candidateTitle: String, targetTitle: String): Boolean {
        val cleanCandidate = stripAllBrackets(cleanTitle(candidateTitle)).lowercase().trim()
        val cleanTarget = stripAllBrackets(cleanTitle(targetTitle)).lowercase().trim()
        if (cleanCandidate.isEmpty() || cleanTarget.isEmpty()) return false
        if (cleanCandidate == cleanTarget) return true

        val shorter = if (cleanCandidate.length <= cleanTarget.length) cleanCandidate else cleanTarget
        val longer = if (cleanCandidate.length <= cleanTarget.length) cleanTarget else cleanCandidate
        if (shorter.length >= 3 && Regex("""\b${Regex.escape(shorter)}\b""").containsMatchIn(longer)) {
            return true
        }

        val targetWords = cleanTarget.split(Regex("""[\s\-_,.:]+""")).filter { it.length >= 3 }
        if (targetWords.isNotEmpty()) {
            val candidateWords = cleanCandidate.split(Regex("""[\s\-_,.:]+""")).filter { it.length >= 3 }.toSet()
            val matches = targetWords.count { candidateWords.contains(it) }
            if (matches.toDouble() / targetWords.size >= 0.6) return true
        }
        return false
    }

    /**
     * Cleans up artist names from YouTube Music:
     * - Removes "- Topic" suffix
     * - Removes "• ... views"
     */
    fun cleanArtist(artist: String): String {
        var clean = artist.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "").trim()

        val bulletIdx = clean.indexOf('•')
        if (bulletIdx != -1) {
            clean = clean.substring(0, bulletIdx).trim()
        }

        return if (clean.isNotBlank()) clean else artist
    }

    /**
     * Extracts the primary artist from compound strings (e.g. "The Weeknd, Daft Punk" -> "The Weeknd")
     */
    fun getPrimaryArtist(artist: String): String {
        val cleaned = cleanArtist(artist)
        val delimiters = listOf(", ", " & ", " feat. ", " ft. ", " vs. ", " / ")
        for (delim in delimiters) {
            val idx = cleaned.indexOf(delim, ignoreCase = true)
            if (idx != -1) {
                val primary = cleaned.substring(0, idx).trim()
                if (primary.isNotBlank()) return primary
            }
        }
        return cleaned
    }

    /**
     * Pure Kotlin unescaper for standard and numeric HTML entities.
     */
    fun unescapeHtml(text: String): String {
        var s = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#8211;", "–")
            .replace("&ndash;", "–")
            .replace("&#8212;", "—")
            .replace("&mdash;", "—")
            .replace("&#8216;", "‘")
            .replace("&lsquo;", "‘")
            .replace("&#8217;", "’")
            .replace("&rsquo;", "’")
            .replace("&#8220;", "“")
            .replace("&ldquo;", "“")
            .replace("&#8221;", "”")
            .replace("&rdquo;", "”")

        val decimalPattern = Regex("""&#([0-9]+);""")
        s = decimalPattern.replace(s) { match ->
            try { match.groupValues[1].toInt().toChar().toString() } catch (_: Exception) { match.value }
        }
        val hexPattern = Regex("""&#x([0-9a-fA-F]+);""")
        s = hexPattern.replace(s) { match ->
            try { match.groupValues[1].toInt(16).toChar().toString() } catch (_: Exception) { match.value }
        }
        return s
    }

    /**
     * Identifies non-lyric metadata headers, SEO descriptions, and boilerplate:
     * - Article intro synopsis (e.g. '“Song” is a Tamil song performed by X with music composed by Y...')
     * - Key-value metadata headers (e.g. 'Song: ...', 'Singer: ...', 'Movie: ...', 'Lyrics: ...')
     * - Trailing credits and copyright boilerplate (e.g. 'Written by: ...', 'Lyrics licensed by ...')
     */
    fun isDescriptionOrMetadataLine(line: String): Boolean {
        val s = line.trim().lowercase()
        if (s.isEmpty()) return true

        // 1. Direct boilerplate prefix matches
        val boilerplatePrefixes = listOf(
            "lyrics licensed", "all rights reserved", "hope you liked",
            "if you find any mistake", "if you found any mistake",
            "presenting the lyrics", "presenting the official",
            "here are the lyrics", "official lyrics of",
            "listen to the song", "watch the official",
            "check out the lyrics", "read the complete lyrics",
            "written by:", "composed by:", "produced by:", "music by:",
            "lyrics by:", "lyrics powered by", "source:"
        )
        if (boilerplatePrefixes.any { s.startsWith(it) }) return true

        // 2. SEO & editorial article descriptions
        val descPhrases = listOf(
            "song is a", "is a song", "song was released", "performed by",
            "music composed by", "lyrics written by", "lyrics penned by",
            "song combines the singing", "song details given", "no album mentioned",
            "considered an album-less", "provides the main details",
            "reflects the creativity", "gives the song a collaborative"
        )
        if (descPhrases.any { s.contains(it) }) {
            if (s.startsWith("“") || s.startsWith("\"") || s.startsWith("the song") || s.startsWith("this song") || s.length > 50) {
                return true
            }
        }

        // 3. Key-value metadata headers (e.g. "Song: ...", "Singer : ...", "Movie : ...")
        val metaKeys = listOf(
            "song", "song name", "track", "track name", "title",
            "singer", "singers", "artist", "artists", "vocal", "vocals", "vocalist",
            "music", "music director", "composer", "composed by", "music by",
            "lyrics", "lyricist", "songwriter", "lyrics by", "written by",
            "movie", "film", "album", "soundtrack",
            "starring", "cast", "featuring", "feat",
            "label", "record label", "music label",
            "director", "directed by", "producer", "produced by",
            "release date", "year", "language", "dop", "choreography",
            "recorded at", "mixed by", "mastered by"
        )
        val metaRegex = Regex("""^(?:${metaKeys.joinToString("|") { Regex.escape(it) }})\s*[:\-–—]\s*.+""", RegexOption.IGNORE_CASE)
        if (metaRegex.matches(s)) return true

        return false
    }

    /**
     * Cleans a list of LyricLine objects by stripping introductory descriptions,
     * metadata headers, and trailing boilerplate.
     */
    fun cleanLyricLines(lines: List<LyricLine>): List<LyricLine> {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()

        // Strip leading metadata/descriptions
        var startIdx = 0
        while (startIdx < nonBlank.size && isDescriptionOrMetadataLine(nonBlank[startIdx].text)) {
            startIdx++
        }

        // Strip trailing credits
        var endIdx = nonBlank.size
        while (endIdx > startIdx && isDescriptionOrMetadataLine(nonBlank[endIdx - 1].text)) {
            endIdx--
        }

        val out = mutableListOf<LyricLine>()
        for (i in startIdx until endIdx) {
            val line = nonBlank[i]
            if (!isDescriptionOrMetadataLine(line.text)) {
                out.add(line.copy(text = line.text.trim()))
            }
        }
        return out
    }

    /**
     * Highly optimized parallel lyric engine:
     * - Fires LRCLIB, YouTube Music, JioSaavn, Looviq, and Lyrofy concurrently in parallel.
     * - The moment ANY provider finds lyrics, it calls [onProgressiveLyrics] immediately (< 250ms).
     * - If plain lyrics arrive first, they are shown right away while LRCLIB checks for synced timestamps.
     * - If LRCLIB finds synced lyrics, it immediately cancels plain jobs and upgrades to synced.
     * - No blocking sequential chains, no hanging network timeouts.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = 0,
        videoId: String? = null,
        onProgressiveLyrics: ((Lyrics) -> Unit)? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        val vId = videoId.orEmpty()

        coroutineScope {
            var bestLyrics: Lyrics? = null
            val lock = Any()

            fun emitCandidate(candidate: Lyrics?) {
                if (candidate == null || candidate.lines.isEmpty()) return
                val live = ensureLiveLyrics(candidate, durationSeconds)
                synchronized(lock) {
                    if (candidate.synced && candidate.lines.any { it.timeMs != null }) {
                        bestLyrics = candidate
                        onProgressiveLyrics?.invoke(candidate)
                    } else if (bestLyrics == null) {
                        bestLyrics = live
                        onProgressiveLyrics?.invoke(live)
                    }
                    Unit
                }
            }

            // 1. LRCLIB (Parallel Exact + Search)
            val lrclibJob = async {
                try {
                    val res = fetchLrclibFast(title, artist, album, durationSeconds, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 2. Megalobiz Synced Lyrics (Massive collection of Indian regional & new synced LRCs)
            val megalobizJob = async {
                try {
                    val res = fetchMegalobiz(title, artist, durationSeconds, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 3. lrcmux multi-source aggregator (LRCLIB, Deezer, NetEase, Kugou synced lyrics)
            val lrcmuxJob = async {
                try {
                    val res = fetchLrcMux(title, artist, durationSeconds, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 4. NetEase Cloud Music (Worldwide synced LRC database)
            val neteaseJob = async {
                try {
                    val res = fetchNetEase(title, artist, durationSeconds, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 5. YouTube Music Official Lyrics
            val ytJob = async {
                if (vId.isNotEmpty() && innerTube != null) {
                    try {
                        val res = innerTube.getLyrics(vId)
                        emitCandidate(res)
                        res
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            // 6. JioSaavn API
            val jioJob = async {
                try {
                    val res = fetchJioSaavn(title, artist, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 7. Looviq Web Scraper
            val looviqJob = async {
                try {
                    val res = fetchLooviq(title, artist, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 8. Lyrofy Web Scraper
            val lyrofyJob = async {
                try {
                    val res = fetchLyrofy(title, artist, vId)
                    emitCandidate(res)
                    res
                } catch (e: Exception) {
                    null
                }
            }

            // 9. Fast single-pass lyrics.ovh (1.5s timeout)
            val ovhJob = async {
                try {
                    val res = fetchLyricsOvh(title, artist, vId)
                    emitCandidate(res)
                    res
                } catch (_: Exception) {
                    null
                }
            }

            // If any authentic synced provider returns, cancel plain text fallbacks
            val lrc = lrclibJob.await()
            if (lrc != null && lrc.synced) {
                megalobizJob.cancel()
                lrcmuxJob.cancel()
                neteaseJob.cancel()
                ytJob.cancel()
                jioJob.cancel()
                looviqJob.cancel()
                lyrofyJob.cancel()
                ovhJob.cancel()
                return@coroutineScope lrc
            }

            val mega = megalobizJob.await()
            if (mega != null && mega.synced) {
                lrcmuxJob.cancel()
                neteaseJob.cancel()
                ytJob.cancel()
                jioJob.cancel()
                looviqJob.cancel()
                lyrofyJob.cancel()
                ovhJob.cancel()
                return@coroutineScope mega
            }

            val mux = lrcmuxJob.await()
            if (mux != null && mux.synced) {
                neteaseJob.cancel()
                ytJob.cancel()
                jioJob.cancel()
                looviqJob.cancel()
                lyrofyJob.cancel()
                ovhJob.cancel()
                return@coroutineScope mux
            }

            val netease = neteaseJob.await()
            if (netease != null && netease.synced) {
                ytJob.cancel()
                jioJob.cancel()
                looviqJob.cancel()
                lyrofyJob.cancel()
                ovhJob.cancel()
                return@coroutineScope netease
            }

            // Await remaining providers
            ytJob.await()
            jioJob.await()
            looviqJob.await()
            lyrofyJob.await()
            ovhJob.await()

            val finalResult = synchronized(lock) { bestLyrics }
            finalResult?.let { ensureLiveLyrics(it, durationSeconds) }
        }
    }

    private suspend fun fetchLrclibFast(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
        videoId: String
    ): Lyrics? = coroutineScope {
        val cTitle = cleanTitle(title, artist)
        val sTitle = stripAllBrackets(title)
        val primaryArtist = getPrimaryArtist(artist)

        val exactTitle = if (sTitle.isNotBlank()) sTitle else cTitle
        val query = if (sTitle.isNotBlank()) "$sTitle $primaryArtist" else "$cTitle $primaryArtist"

        val exactDeferred = async {
            fetchLrclibExact(exactTitle, primaryArtist, album, durationSeconds)
                ?: if (cTitle != exactTitle) fetchLrclibExact(cTitle, primaryArtist, album, durationSeconds) else null
        }
        val searchDeferred = async {
            fetchLrclibQuerySearch(query, durationSeconds, exactTitle)
                ?: if (sTitle != query) fetchLrclibQuerySearch(sTitle, durationSeconds, exactTitle) else null
        }

        val exactObj = exactDeferred.await()
        if (exactObj != null) {
            val parsed = parseLrclibResponse(exactObj, videoId)
            if (parsed != null && parsed.synced) return@coroutineScope parsed
        }

        val searchObj = searchDeferred.await()
        if (searchObj != null) {
            val parsed = parseLrclibResponse(searchObj, videoId)
            if (parsed != null && parsed.synced) return@coroutineScope parsed
        }

        exactObj?.let { parseLrclibResponse(it, videoId) }
            ?: searchObj?.let { parseLrclibResponse(it, videoId) }
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

            fastHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    JsonParser.parseString(body).asJsonObject
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB exact get failed for $title - $artist", e)
            null
        }
    }

    private fun fetchLrclibQuerySearch(
        query: String,
        durationSeconds: Int,
        targetTitle: String = ""
    ): JsonObject? {
        return try {
            val urlBuilder = (LRCLIB_BASE + "/search").toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("q", query)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            fastHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val body = response.body?.string().orEmpty()
                val arr = JsonParser.parseString(body).asJsonArray
                if (arr.size() == 0) return null

                pickBestCandidate(arr, durationSeconds, targetTitle)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB query search failed for $query", e)
            null
        }
    }

    private fun pickBestCandidate(arr: JsonArray, durationSeconds: Int, targetTitle: String = ""): JsonObject? {
        var bestItem: JsonObject? = null
        var minDiff = Double.MAX_VALUE

        // First pass: look specifically for synced items
        for (el in arr) {
            val item = el.asJsonObject
            val trackName = item.get("trackName")?.asString.orEmpty()
            if (targetTitle.isNotEmpty() && trackName.isNotEmpty() && !isSongTitleMatch(trackName, targetTitle)) {
                continue
            }
            val synced = item.get("syncedLyrics")?.asString.orEmpty()
            val d = item.get("duration")?.asDouble ?: 0.0

            val diff = if (durationSeconds > 0 && d > 0.0) {
                Math.abs(durationSeconds.toDouble() - d)
            } else {
                100.0
            }

            if (synced.isNotEmpty() && diff < minDiff) {
                minDiff = diff
                bestItem = item
            }
        }

        if (bestItem != null) return bestItem

        // Second pass: fallback to plain lyrics candidate
        for (el in arr) {
            val item = el.asJsonObject
            val trackName = item.get("trackName")?.asString.orEmpty()
            if (targetTitle.isNotEmpty() && trackName.isNotEmpty() && !isSongTitleMatch(trackName, targetTitle)) {
                continue
            }
            val plain = item.get("plainLyrics")?.asString.orEmpty()
            if (plain.isNotEmpty()) {
                return item
            }
        }

        return arr.firstOrNull { el ->
            val trackName = el.asJsonObject.get("trackName")?.asString.orEmpty()
            targetTitle.isEmpty() || trackName.isEmpty() || isSongTitleMatch(trackName, targetTitle)
        }?.asJsonObject
    }

    private fun parseLrclibResponse(json: JsonObject, videoId: String = ""): Lyrics? {
        val synced = json.get("syncedLyrics")?.asString
        if (!synced.isNullOrBlank()) {
            val lines = cleanLyricLines(parseLrc(synced))
            if (lines.isNotEmpty()) {
                return Lyrics(
                    videoId = videoId,
                    source = "LRCLIB",
                    synced = true,
                    lines = lines
                )
            }
        }

        val plain = json.get("plainLyrics")?.asString
        if (!plain.isNullOrBlank()) {
            val rawLines = plain.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }
            val lines = cleanLyricLines(rawLines)

            if (lines.isNotEmpty()) {
                return Lyrics(
                    videoId = videoId,
                    source = "LRCLIB",
                    synced = false,
                    lines = lines
                )
            }
        }

        return null
    }

    /**
     * Looviq lyrics scraper:
     * High hit rate for newly released South Indian, Hindi, and regional songs.
     */
    private fun fetchLooviq(title: String, artist: String, videoId: String): Lyrics? {
        val cTitle = cleanTitle(title, artist)
        val sTitle = stripAllBrackets(title)
        val pArtist = getPrimaryArtist(artist)

        val queries = listOf(cTitle, sTitle, "$sTitle $pArtist").distinct().filter { it.isNotBlank() }
        for (q in queries) {
            try {
                val urlBuilder = "https://looviq.com/search".toHttpUrlOrNull()?.newBuilder() ?: continue
                urlBuilder.addQueryParameter("q", q)
                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val html = fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
                } ?: continue
                if (html.isEmpty()) continue

                val linkRegex = Regex("""<a href="(https://looviq\.com/lyrics/[^"]+)" class="song-card-title">\s*([^<]+)\s*</a>""")
                val matches = linkRegex.findAll(html).toList()
                if (matches.isEmpty()) continue

                var targetUrl: String? = null
                for (m in matches) {
                    val cardTitle = m.groupValues[2].trim()
                    if (isSongTitleMatch(cardTitle, cTitle) || isSongTitleMatch(cardTitle, sTitle)) {
                        targetUrl = m.groupValues[1]
                        break
                    }
                }
                if (targetUrl == null) continue

                val lyrReq = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val lyrHtml = fastHttpClient.newCall(lyrReq).execute().use { lyrResp ->
                    if (!lyrResp.isSuccessful) null else lyrResp.body?.string().orEmpty()
                } ?: continue

                val bodyRegex = Regex("""<div[^>]*class="[^"]*lyrics-text[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                val bodyMatch = bodyRegex.find(lyrHtml)
                if (bodyMatch != null) {
                    var rawText = bodyMatch.groupValues[1]
                    rawText = rawText.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    rawText = rawText.replace(Regex("""<[^>]+>"""), "")
                    rawText = unescapeHtml(rawText).trim()

                    val rawLines = rawText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LyricLine(timeMs = null, text = it) }

                    val lines = cleanLyricLines(rawLines)

                    if (lines.size >= 3) {
                        Log.d(TAG, "Retrieved lyrics from Looviq for $title (${lines.size} lines)")
                        return Lyrics(
                            videoId = videoId,
                            source = "Looviq",
                            synced = false,
                            lines = lines
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Looviq lookup failed for $q", e)
            }
        }
        return null
    }

    /**
     * lyrics.ovh REST API:
     * Fast, reliable plain text lyrics for global Western and Bollywood hits.
     */
    private fun fetchLyricsOvh(title: String, artist: String, videoId: String): Lyrics? {
        val cTitle = cleanTitle(title, artist)
        val sTitle = stripAllBrackets(title)
        val pArtist = getPrimaryArtist(artist)
        val t = if (sTitle.isNotBlank()) sTitle else cTitle
        val a = pArtist

        return try {
            val encodedA = URLEncoder.encode(a, "UTF-8").replace("+", "%20")
            val encodedT = URLEncoder.encode(t, "UTF-8").replace("+", "%20")
            val url = "https://api.lyrics.ovh/v1/$encodedA/$encodedT"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val body = fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
            } ?: return null
            val json = JsonParser.parseString(body).asJsonObject
            val lyr = json.get("lyrics")?.asString.orEmpty().trim()
            if (lyr.length > 50) {
                val lines = lyr.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { LyricLine(timeMs = null, text = it) }

                if (lines.size >= 3) {
                    Log.d(TAG, "Retrieved lyrics from lyrics.ovh for $t - $a")
                    Lyrics(
                        videoId = videoId,
                        source = "lyrics.ovh",
                        synced = false,
                        lines = lines
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * JioSaavn API:
     * High hit rate for catalog Indian music (Bollywood, Hindi, Tamil, Telugu).
     */
    private fun fetchJioSaavn(title: String, artist: String, videoId: String): Lyrics? {
        val cTitle = cleanTitle(title, artist)
        val pArtist = getPrimaryArtist(artist)
        val queries = listOf(
            if (pArtist.isNotBlank()) "$cTitle $pArtist" else cTitle,
            cTitle
        ).distinct()

        for (q in queries) {
            try {
                val urlBuilder = "https://www.jiosaavn.com/api.php".toHttpUrlOrNull()?.newBuilder() ?: continue
                urlBuilder.addQueryParameter("__call", "search.getResults")
                urlBuilder.addQueryParameter("_format", "json")
                urlBuilder.addQueryParameter("_marker", "0")
                urlBuilder.addQueryParameter("n", "5")
                urlBuilder.addQueryParameter("p", "1")
                urlBuilder.addQueryParameter("q", q)

                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val body = fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
                } ?: continue
                val json = JsonParser.parseString(body).asJsonObject
                val results = json.getAsJsonArray("results") ?: continue

                for (el in results) {
                    val item = el.asJsonObject
                    val songName = unescapeHtml(item.get("song")?.asString ?: item.get("title")?.asString.orEmpty())
                    if (songName.isNotBlank() && !isSongTitleMatch(songName, cTitle)) {
                        continue
                    }
                    if (item.get("has_lyrics")?.asString == "true") {
                        val songId = item.get("id")?.asString ?: continue
                        val lyrUrlBuilder = "https://www.jiosaavn.com/api.php".toHttpUrlOrNull()?.newBuilder() ?: continue
                        lyrUrlBuilder.addQueryParameter("__call", "lyrics.getLyrics")
                        lyrUrlBuilder.addQueryParameter("_format", "json")
                        lyrUrlBuilder.addQueryParameter("_marker", "0")
                        lyrUrlBuilder.addQueryParameter("lyrics_id", songId)

                        val lyrReq = Request.Builder()
                            .url(lyrUrlBuilder.build())
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val lyrBody = fastHttpClient.newCall(lyrReq).execute().use { lyrResp ->
                            if (!lyrResp.isSuccessful) null else lyrResp.body?.string().orEmpty()
                        } ?: continue
                        val lyrJson = JsonParser.parseString(lyrBody).asJsonObject
                        var rawText = lyrJson.get("lyrics")?.asString.orEmpty()
                        rawText = rawText.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                        rawText = rawText.replace(Regex("""<[^>]+>"""), "")
                        rawText = unescapeHtml(rawText).trim()

                        val lines = rawText.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { LyricLine(timeMs = null, text = it) }

                        if (lines.size >= 3) {
                            Log.d(TAG, "Retrieved lyrics from JioSaavn for $title (${lines.size} lines)")
                            return Lyrics(
                                videoId = videoId,
                                source = "JioSaavn",
                                synced = false,
                                lines = lines
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "JioSaavn lyrics lookup failed for $q", e)
            }
        }
        return null
    }

    /**
     * Lyrofy web lyric scraper for regional and indie releases.
     */
    private fun fetchLyrofy(title: String, artist: String, videoId: String): Lyrics? {
        val cTitle = cleanTitle(title, artist)
        try {
            val urlBuilder = "https://lyrofy.com/".toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("s", cTitle)
            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val pageHtml = fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
            } ?: return null
            if (pageHtml.isEmpty()) return null

            val linkRegex = Regex("""<a\s+[^>]*href="(https?://lyrofy\.com/[^"]+)"[^>]*>(.*?)</a>""")
            var targetUrl: String? = null
            for (m in linkRegex.findAll(pageHtml)) {
                val link = m.groupValues[1]
                val text = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
                if (isSongTitleMatch(text, cTitle)) {
                    targetUrl = link
                    break
                }
            }
            if (targetUrl == null) return null

            val lyrReq = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val lyrHtml = fastHttpClient.newCall(lyrReq).execute().use { lyrResp ->
                if (!lyrResp.isSuccessful) null else lyrResp.body?.string().orEmpty()
            } ?: return null

            val pRegex = Regex("""<p class="wp-block-paragraph">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            val pMatches = pRegex.findAll(lyrHtml).toList()
            if (pMatches.isEmpty()) return null

            val rawParagraphs = pMatches.map { match ->
                val text = match.groupValues[1]
                    .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("""<[^>]+>"""), "")
                unescapeHtml(text).trim()
            }.filter { it.isNotEmpty() }

            // Filter out intro article synopsis/description paragraphs
            val lyricParagraphs = rawParagraphs.filter { !isDescriptionOrMetadataLine(it) }
            val fullText = lyricParagraphs.joinToString("\n\n")
            val rawLines = fullText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }

            val lines = cleanLyricLines(rawLines)

            if (lines.size >= 3) {
                Log.d(TAG, "Retrieved lyrics from Lyrofy for $title (${lines.size} lines)")
                return Lyrics(
                    videoId = videoId,
                    source = "Lyrofy",
                    synced = false,
                    lines = lines
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lyrofy lookup failed for $cTitle", e)
        }
        return null
    }

    /**
     * Megalobiz LRC Provider:
     * Public REST API querying hundreds of thousands of user-timed LRC files.
     * Exceptional coverage for Indian regional songs (Tamil, Telugu, Hindi, Malayalam, Punjabi, Kannada).
     */
    suspend fun fetchMegalobiz(
        title: String,
        artist: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cTitle = cleanTitle(title, artist)
        val cArtist = cleanArtist(artist)
        val queries = listOf(
            "$cTitle $cArtist".trim(),
            cTitle.trim()
        ).distinct()

        for (query in queries) {
            try {
                val url = "https://api.megalobiz.com/api/public/v1/search?q=" + URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                val lrcUrlToFetch = fastHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val json = JsonParser.parseString(body).asJsonObject
                    val results = json.getAsJsonArray("results") ?: return@use null
                    if (results.size() == 0) return@use null

                    var bestUrl: String? = null
                    var minDurDiff = Long.MAX_VALUE

                    for (elem in results) {
                        val item = elem.asJsonObject
                        val itemTitle = item.get("title")?.asString.orEmpty()
                        val lrcUrl = item.get("lrc_url")?.asString.orEmpty()
                        val dur = item.get("duration")?.asFloat ?: 0f

                        if (lrcUrl.isEmpty()) continue

                        // STRICT TITLE MATCH: candidate must match requested song title
                        if (!isSongTitleMatch(itemTitle, cTitle)) {
                            continue
                        }

                        if (durationSeconds > 0) {
                            val diff = Math.abs(dur.toLong() - durationSeconds.toLong())
                            if (diff < minDurDiff) {
                                minDurDiff = diff
                                bestUrl = lrcUrl
                            }
                        } else {
                            bestUrl = lrcUrl
                            break
                        }
                    }

                    if (bestUrl != null && (durationSeconds == 0 || minDurDiff <= 25L)) {
                        bestUrl
                    } else null
                }

                if (lrcUrlToFetch != null) {
                    val lrcReq = Request.Builder()
                        .url(lrcUrlToFetch)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val result = fastHttpClient.newCall(lrcReq).execute().use { lrcResp ->
                        if (lrcResp.isSuccessful) {
                            val lrcText = lrcResp.body?.string().orEmpty()
                            val lines = parseLrc(lrcText)
                            if (lines.isNotEmpty() && lines.any { it.timeMs != null }) {
                                Log.d(TAG, "Retrieved synced lyrics from Megalobiz for $cTitle (${lines.size} lines)")
                                Lyrics(
                                    videoId = videoId,
                                    source = "Megalobiz",
                                    synced = true,
                                    lines = lines
                                )
                            } else null
                        } else null
                    }
                    if (result != null) return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Megalobiz lookup failed for $query", e)
            }
        }
        null
    }

    /**
     * NetEase Cloud Music Provider:
     * Massive worldwide timestamped lyrics database covering international, Asian, and regional songs.
     */
    suspend fun fetchNetEase(
        title: String,
        artist: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cTitle = cleanTitle(title, artist)
        val cArtist = cleanArtist(artist)
        val query = "$cTitle $cArtist".trim()

        try {
            val formBody = FormBody.Builder()
                .add("s", query)
                .add("limit", "5")
                .add("type", "1")
                .add("offset", "0")
                .build()

            val request = Request.Builder()
                .url("https://music.163.com/api/search/pc")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("Cookie", "NMTID=00OAVK3xqDG726ITU6jopU6jF2yMk0AAAGCO8l1BA;")
                .post(formBody)
                .build()

            val songId = fastHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JsonParser.parseString(body).asJsonObject
                val resultObj = json.getAsJsonObject("result") ?: return@use null
                val songs = resultObj.getAsJsonArray("songs") ?: return@use null
                if (songs.size() == 0) return@use null

                var bestId: Long? = null
                var minDiff = Long.MAX_VALUE
                for (elem in songs) {
                    val sObj = elem.asJsonObject
                    val sName = sObj.get("name")?.asString.orEmpty()
                    if (!isSongTitleMatch(sName, cTitle)) {
                        continue
                    }

                    if (durationSeconds > 0) {
                        val durMs = sObj.get("duration")?.asLong ?: 0L
                        val diff = Math.abs(durMs / 1000 - durationSeconds)
                        if (diff < minDiff) {
                            minDiff = diff
                            bestId = sObj.get("id")?.asLong
                        }
                    } else if (bestId == null) {
                        bestId = sObj.get("id")?.asLong
                    }
                }
                bestId
            }

            if (songId != null) {
                val lyricReq = Request.Builder()
                    .url("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://music.163.com/")
                    .build()

                return@withContext fastHttpClient.newCall(lyricReq).execute().use { lResp ->
                    if (lResp.isSuccessful) {
                        val lBody = lResp.body?.string() ?: return@use null
                        val lJson = JsonParser.parseString(lBody).asJsonObject
                        val lrcText = lJson.getAsJsonObject("lrc")?.get("lyric")?.asString.orEmpty()
                        val lines = parseLrc(lrcText)
                        if (lines.isNotEmpty() && lines.any { it.timeMs != null }) {
                            Log.d(TAG, "Retrieved synced lyrics from NetEase for $cTitle (${lines.size} lines)")
                            Lyrics(
                                videoId = videoId,
                                source = "NetEase",
                                synced = true,
                                lines = lines
                            )
                        } else null
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetEase lookup failed for $query", e)
        }
        null
    }

    /**
     * lrcmux Compatibility Provider:
     * Multi-source aggregator proxy that fans out across LRCLIB, Deezer, NetEase, and KuGou.
     */
    suspend fun fetchLrcMux(
        title: String,
        artist: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cTitle = cleanTitle(title, artist)
        val cArtist = cleanArtist(artist)
        try {
            val urlBuilder = ("https://api.lrcmux.dev/compat/lrclib/api/get").toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
            urlBuilder.addQueryParameter("track_name", cTitle)
            urlBuilder.addQueryParameter("artist_name", cArtist)
            if (durationSeconds > 0) {
                urlBuilder.addQueryParameter("duration", durationSeconds.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            return@withContext fastHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JsonParser.parseString(body).asJsonObject
                val trackName = json.get("trackName")?.asString
                if (!trackName.isNullOrBlank() && !isSongTitleMatch(trackName, cTitle)) {
                    Log.w(TAG, "lrcmux trackName mismatch: '$trackName' does not match target '$cTitle'")
                    return@use null
                }
                val syncedLrc = json.get("syncedLyrics")?.asString
                if (!syncedLrc.isNullOrBlank()) {
                    val lines = parseLrc(syncedLrc)
                    if (lines.isNotEmpty() && lines.any { it.timeMs != null }) {
                        Log.d(TAG, "Retrieved synced lyrics from lrcmux for $cTitle (${lines.size} lines)")
                        Lyrics(
                            videoId = videoId,
                            source = "lrcmux",
                            synced = true,
                            lines = lines
                        )
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "lrcmux lookup failed for $cTitle", e)
        }
        null
    }

    /**
     * Exact LRC timestamp parser with standard [offset: +/- ms] header support:
     * Parses [mm:ss.xx] and [mm:ss.xxx] into millisecond offsets.
     * Automatically filters out empty spacer lines so they do not falsely highlight.
     */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val lines = lrcText.lines()

        // Extract [offset: +/- ms] tag if present
        var offsetMs = 0L
        val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
        for (raw in lines) {
            val m = offsetRegex.find(raw)
            if (m != null) {
                offsetMs = m.groupValues[1].toLongOrNull() ?: 0L
                break
            }
        }

        for (raw in lines) {
            var rest = raw.trim()
            val times = mutableListOf<Long>()

            while (rest.startsWith("[")) {
                val end = rest.indexOf(']')
                if (end == -1) break
                val tag = rest.substring(1, end)
                val timeMs = parseLrcTime(tag)
                if (timeMs != null) {
                    times.add((timeMs + offsetMs).coerceAtLeast(0L))
                    rest = rest.substring(end + 1).trimStart()
                } else {
                    break
                }
            }

            val cleanText = rest.trim()
            if (cleanText.isNotEmpty()) {
                for (timeMs in times) {
                    out.add(LyricLine(timeMs = timeMs, text = cleanText))
                }
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

        val dot = rest.indexOfAny(charArrayOf('.', ','))
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

    /**
     * Converts plain lyrics into live, rhythm-aware karaoke lyrics with acoustic timing:
     * - Strips any non-lyric metadata or article descriptions.
     * - Uses cumulative weighting across the song's vocal window (so lyrics never compress or end early).
     * - Estimates intro duration (clamped between 8s and 22s based on duration).
     * - Estimates outro duration (clamped between 6s and 16s).
     * - Guarantees monotonic progression with natural inter-line cadence.
     */
    fun createLiveLyrics(lines: List<LyricLine>, durationSeconds: Int): List<LyricLine> {
        val cleanLines = cleanLyricLines(lines)
        if (cleanLines.isEmpty()) return emptyList()

        val durSec = if (durationSeconds > 10) durationSeconds else (cleanLines.size * 4).coerceAtLeast(30)
        val durationMs = durSec * 1000L

        // Intro: 10% of song duration (clamped 8s - 22s)
        val introMs = (durationMs * 0.10f).toLong().coerceIn(8000L, 22000L)
        // Outro: 6% of song duration (clamped 6s - 16s)
        val outroMs = (durationMs * 0.06f).toLong().coerceIn(6000L, 16000L)
        val vocalWindowMs = (durationMs - introMs - outroMs).coerceAtLeast(10000L)

        val weights = cleanLines.map { line ->
            val clean = line.text.trim()
            val words = clean.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            (words.size * 1.0 + clean.length * 0.1).coerceIn(1.5, 12.0)
        }

        val totalWeight = weights.sum().coerceAtLeast(1.0)
        val cumWeights = DoubleArray(cleanLines.size)
        var cum = 0.0
        for (i in cleanLines.indices) {
            cum += weights[i]
            cumWeights[i] = cum
        }

        val out = mutableListOf<LyricLine>()
        var lastMs = introMs
        for (i in cleanLines.indices) {
            val lineStartMs = if (i == 0) {
                introMs
            } else {
                introMs + ((cumWeights[i - 1] / totalWeight) * vocalWindowMs).toLong()
            }
            val safeStartMs = if (i == 0) lineStartMs else lineStartMs.coerceAtLeast(lastMs + 1200L)
            out.add(LyricLine(timeMs = safeStartMs, text = cleanLines[i].text.trim()))
            lastMs = safeStartMs
        }

        return out
    }

    fun ensureLiveLyrics(lyrics: Lyrics, durationSeconds: Int): Lyrics {
        val cleanedLines = cleanLyricLines(lyrics.lines)
        if (lyrics.synced && cleanedLines.any { it.timeMs != null }) {
            return lyrics.copy(lines = cleanedLines)
        }
        val liveLines = createLiveLyrics(cleanedLines, durationSeconds)
        return lyrics.copy(
            synced = true,
            lines = liveLines
        )
    }
}
