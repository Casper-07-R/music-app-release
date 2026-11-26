package com.koi.backend.lyrics

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.koi.backend.innertube.InnerTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

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
        private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val SYNCED_GRACE_WINDOW_MS = 1200L
        private const val MAX_DURATION_DIFF_SECONDS = 25L

        private val TITLE_STOPWORDS = setOf(
            "the", "and", "for", "from", "with", "feat", "featuring"
        )
        private val ARTIST_STOPWORDS = setOf(
            "the", "and", "music", "official", "records", "entertainment", "topic"
        )
        private val STRICT_VARIANT_MODIFIERS = setOf(
            "instrumental", "karaoke", "slowed", "reverb", "sped up", "speed up",
            "nightcore", "lofi", "lo-fi", "8d", "acappella", "acapella",
            "remix", "acoustic", "unplugged", "cover", "tribute", "mashup",
            "ringtone", "bgm", "reprise", "tv size", "live"
        )
        private val EXPLICIT_LANGUAGE_SCRIPT = mapOf(
            "tamil" to "TAMIL",
            "telugu" to "TELUGU",
            "malayalam" to "MALAYALAM",
            "kannada" to "KANNADA",
            "hindi" to "DEVANAGARI",
            "marathi" to "DEVANAGARI",
            "punjabi" to "GURMUKHI",
            "bengali" to "BENGALI",
            "english" to "LATIN",
            "spanish" to "LATIN",
            "french" to "LATIN",
            "japanese" to "JAPANESE",
            "korean" to "KOREAN",
            "arabic" to "ARABIC"
        )
    }

    private val fastHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(5000, TimeUnit.MILLISECONDS)
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
     * Prevents mismatched songs by the same composer/artist (e.g. Malayalam songs matching Tamil songs),
     * unsolicited covers/karaoke/instrumental/remixes, and short-word substring false positives.
     */
    fun isSongTitleMatch(candidateTitle: String, targetTitle: String): Boolean {
        if (hasConflictingVariantTag(candidateTitle, targetTitle)) return false
        if (hasConflictingLanguageTag(candidateTitle, targetTitle)) return false

        val cleanCandidate = normalizeTitleCore(stripAllBrackets(cleanTitle(candidateTitle)))
        val cleanTarget = normalizeTitleCore(stripAllBrackets(cleanTitle(targetTitle)))
        if (cleanCandidate.isEmpty() || cleanTarget.isEmpty()) return false
        if (cleanCandidate == cleanTarget) return true

        val punctNormCandidate = cleanCandidate.replace(Regex("""[,.:;!?]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        val punctNormTarget = cleanTarget.replace(Regex("""[,.:;!?]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (punctNormCandidate == punctNormTarget) return true

        if (isDelimiterTitleMatch(cleanCandidate, cleanTarget) || isDelimiterTitleMatch(cleanTarget, cleanCandidate)) {
            return true
        }

        val targetWords = extractTitleWords(punctNormTarget)
        val candidateWords = extractTitleWords(punctNormCandidate)
        if (targetWords.size >= 2 && candidateWords.size >= 2) {
            val candidateSet = candidateWords.toSet()
            val matches = targetWords.count { candidateSet.contains(it) }
            val targetRecall = matches.toDouble() / targetWords.size
            val candidatePrecision = matches.toDouble() / candidateWords.size
            if (matches >= 2 && targetRecall >= 0.7 && candidatePrecision >= 0.6) {
                return true
            }
        }
        return false
    }

    private fun normalizeTitleCore(text: String): String {
        return foldAccents(text.lowercase())
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isDelimiterTitleMatch(withDelimiter: String, plain: String): Boolean {
        val separators = listOf(" - ", " | ", ": ", " — ", " – ")
        for (sep in separators) {
            val idx = withDelimiter.indexOf(sep)
            if (idx > 0) {
                val head = withDelimiter.substring(0, idx).trim()
                val tail = withDelimiter.substring(idx + sep.length).trim()
                if (head == plain || tail == plain) return true
            }
        }
        return false
    }

    private fun extractTitleWords(normalizedTitle: String): List<String> {
        return normalizedTitle.split(Regex("""[\s\-_,.:;!?/]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && !TITLE_STOPWORDS.contains(it) }
    }

    private fun foldAccents(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    private fun extractVariantTags(text: String): Set<String> {
        val lower = text.lowercase()
        val tags = mutableSetOf<String>()

        val modifierSegments = mutableListOf<String>()
        Regex("""[\(\[]([^\)\]]+)[\)\]]""").findAll(lower).forEach {
            modifierSegments.add(it.groupValues[1])
        }
        val dashParts = lower.split(Regex("""\s+[-–—|]\s+"""))
        if (dashParts.size > 1) {
            modifierSegments.addAll(dashParts.drop(1))
        }

        for (seg in modifierSegments) {
            for (mod in STRICT_VARIANT_MODIFIERS) {
                if (Regex("""\b${Regex.escape(mod)}\b""").containsMatchIn(seg)) {
                    tags.add(mod)
                }
            }
        }

        val standaloneModifierPhrases = listOf(
            "karaoke version" to "karaoke",
            "instrumental version" to "instrumental",
            "slowed and reverb" to "slowed",
            "slowed + reverb" to "slowed",
            "sped up" to "sped up",
            "acoustic version" to "acoustic",
            "tribute to" to "tribute",
            "cover by" to "cover",
            "live version" to "live",
            "live at" to "live"
        )
        for ((phrase, canonical) in standaloneModifierPhrases) {
            if (lower.contains(phrase)) tags.add(canonical)
        }
        return tags
    }

    fun hasConflictingVariantTag(candidate: String, target: String): Boolean {
        val targetTags = extractVariantTags(target)
        val candidateTags = extractVariantTags(candidate)
        return candidateTags.any { !targetTags.contains(it) }
    }

    fun detectExplicitLanguageTags(text: String): Set<String> {
        val lower = text.lowercase()
        val found = mutableSetOf<String>()
        for (lang in EXPLICIT_LANGUAGE_SCRIPT.keys) {
            if (Regex("""\b${Regex.escape(lang)}\b""").containsMatchIn(lower)) {
                found.add(lang)
            }
        }
        return found
    }

    fun hasConflictingLanguageTag(candidateText: String, targetText: String): Boolean {
        val targetLangs = detectExplicitLanguageTags(targetText)
        val candidateLangs = detectExplicitLanguageTags(candidateText)
        return targetLangs.isNotEmpty() && candidateLangs.isNotEmpty() && candidateLangs.none { targetLangs.contains(it) }
    }

    /**
     * Validates that lyric script matches any explicitly requested language (e.g. rejecting Telugu lyrics
     * when a Tamil song is requested, or CJK lyrics when English is explicitly requested).
     * Romanized (Latin-script) lyrics remain allowed for Indic/Asian songs.
     */
    fun validateLyricScriptLanguage(
        lines: List<LyricLine>,
        targetTitle: String,
        targetAlbum: String? = null,
        candidateMetaText: String = ""
    ): Boolean {
        val targetMeta = "$targetTitle ${targetAlbum.orEmpty()}"
        if (candidateMetaText.isNotBlank()) {
            if (hasConflictingVariantTag(candidateMetaText, targetTitle)) return false
            if (hasConflictingLanguageTag(candidateMetaText, targetMeta)) return false
        }

        val requestedLangs = detectExplicitLanguageTags(targetMeta)
        val targetTitleScript = detectDominantScript(targetTitle)
        if (requestedLangs.isEmpty() && (targetTitleScript == null || targetTitleScript == "LATIN")) {
            return true
        }

        val text = lines.joinToString("\n") { it.text }
        val dominantScript = detectDominantScript(text) ?: return true
        if (dominantScript == "LATIN") {
            return true
        }

        if (requestedLangs.isNotEmpty()) {
            val allowedNonLatinScripts = requestedLangs.mapNotNull { EXPLICIT_LANGUAGE_SCRIPT[it] }.toSet()
            return allowedNonLatinScripts.contains(dominantScript)
        }

        return dominantScript == targetTitleScript
    }

    private fun detectDominantScript(text: String): String? {
        val counts = mutableMapOf<String, Int>()
        var latinCount = 0
        var totalLetters = 0

        for (ch in text) {
            val code = ch.code
            val script = when (code) {
                in 0x0B80..0x0BFF -> "TAMIL"
                in 0x0C00..0x0C7F -> "TELUGU"
                in 0x0D00..0x0D7F -> "MALAYALAM"
                in 0x0C80..0x0CFF -> "KANNADA"
                in 0x0900..0x097F -> "DEVANAGARI"
                in 0x0A00..0x0A7F -> "GURMUKHI"
                in 0x0980..0x09FF -> "BENGALI"
                in 0xAC00..0xD7AF, in 0x1100..0x11FF -> "KOREAN"
                in 0x3040..0x30FF -> "JAPANESE"
                in 0x4E00..0x9FFF -> "CJK"
                in 0x0600..0x06FF -> "ARABIC"
                else -> null
            }
            if (script != null) {
                counts[script] = (counts[script] ?: 0) + 1
                totalLetters++
            } else if ((ch in 'a'..'z') || (ch in 'A'..'Z') || code in 0x00C0..0x024F) {
                latinCount++
                totalLetters++
            }
        }

        if (totalLetters == 0) return null
        val topNonLatin = counts.maxByOrNull { it.value }
        if (topNonLatin != null && topNonLatin.value >= 10 && topNonLatin.value.toDouble() / totalLetters >= 0.25) {
            return topNonLatin.key
        }
        if (latinCount.toDouble() / totalLetters >= 0.7) {
            return "LATIN"
        }
        return null
    }

    private fun sanitizeArtistInput(artist: String?): String {
        val raw = artist.orEmpty().trim()
        if (InnerTubeClient.isMissingArtist(raw) || raw.equals("Various Artists", ignoreCase = true)) {
            return ""
        }
        return cleanArtist(raw)
    }

    private fun extractArtistTokens(artist: String): List<String> {
        val cleaned = cleanArtist(artist)
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(Regex("""\s*(?:,|&|feat\.?|ft\.?|featuring|vs\.?|/|×|\+)\s*|\s+with\s+|\s+x\s+""", RegexOption.IGNORE_CASE))
            .map { foldAccents(it.lowercase()).replace(".", "").replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() && !InnerTubeClient.isMissingArtist(it) && !it.equals("various artists", ignoreCase = true) }
    }

    fun isArtistMatch(candidateArtist: String, targetArtist: String): Boolean {
        val cleanTarget = sanitizeArtistInput(targetArtist)
        if (cleanTarget.isEmpty()) return true
        val cleanCand = sanitizeArtistInput(candidateArtist)
        if (cleanCand.isEmpty()) return true

        val targetTokens = extractArtistTokens(cleanTarget)
        val candTokens = extractArtistTokens(cleanCand)
        if (targetTokens.isEmpty() || candTokens.isEmpty()) return true

        for (t in targetTokens) {
            for (c in candTokens) {
                if (t == c) return true
                if (t.length >= 3 && !ARTIST_STOPWORDS.contains(t) && Regex("""\b${Regex.escape(t)}\b""").containsMatchIn(c)) {
                    return true
                }
                if (c.length >= 3 && !ARTIST_STOPWORDS.contains(c) && Regex("""\b${Regex.escape(c)}\b""").containsMatchIn(t)) {
                    return true
                }
                val tWords = t.split(" ").filter { it.length >= 3 && !ARTIST_STOPWORDS.contains(it) }
                val cWords = c.split(" ").filter { it.length >= 3 && !ARTIST_STOPWORDS.contains(it) }
                if (tWords.size >= 2 && cWords.size >= 2) {
                    val cSet = cWords.toSet()
                    val matches = tWords.count { cSet.contains(it) }
                    if (matches >= 2 && matches.toDouble() / minOf(tWords.size, cWords.size) >= 0.7) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun extractSearchTitleVariants(title: String, artist: String? = null): List<String> {
        val cleanArt = sanitizeArtistInput(artist).ifEmpty { null }
        val cTitle = cleanTitle(title, cleanArt)
        val sTitle = stripAllBrackets(cTitle)
        val pipeHead = sTitle.substringBefore(" | ").trim()
        val dashHead = if (pipeHead.contains(" - ")) pipeHead.substringBefore(" - ").trim() else ""
        return listOf(cTitle, sTitle, pipeHead, dashHead)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
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
            "lyrics by:", "lyrics powered by", "source:",
            "paroles de la chanson", "provided to youtube", "powered by", "[by:"
        )
        if (boilerplatePrefixes.any { s.startsWith(it) }) return true

        // 1b. NetEase / CJK production credit headers (e.g. "作词 : ...", "制作人：...")
        val cjkCreditKeys = listOf(
            "作词", "作曲", "编曲", "制作人", "混音", "母带", "录音",
            "监制", "出品", "和声", "策划", "统筹", "发行", "吉他",
            "贝斯", "鼓", "钢琴", "弦乐", "配唱", "人声编辑", "版权", "词曲"
        )
        val cjkCreditRegex = Regex("""^(?:${cjkCreditKeys.joinToString("|")})\s*[:：].*""")
        if (cjkCreditRegex.matches(s)) return true

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
            "arranger", "backing vocals", "mix & master",
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

    private data class RankedProviderResult(
        val key: String,
        val syncPriority: Int,
        val plainPriority: Int,
        val lyrics: Lyrics?
    )

    private fun normalizeAndVerifyCandidate(
        raw: Lyrics?,
        targetTitle: String,
        targetAlbum: String? = null,
        candidateMeta: String = ""
    ): Lyrics? {
        if (raw == null) return null
        val cleanedLines = cleanLyricLines(raw.lines)
        if (cleanedLines.isEmpty()) return null
        if (!validateLyricScriptLanguage(cleanedLines, targetTitle, targetAlbum, candidateMeta)) {
            return null
        }
        val hasAuthenticTimestamps = raw.synced && cleanedLines.any { it.timeMs != null }
        return raw.copy(synced = hasAuthenticTimestamps, lines = cleanedLines)
    }

    /**
     * Highly optimized parallel lyric engine:
     * - Races LRCLIB, YouTube Music (timed & plain), Megalobiz, lrcmux, NetEase, JioSaavn, Looviq, Lyrofy, lyrics.ovh.
     * - Validates every candidate against title, artist, duration, variant tags, and language/script.
     * - Streams verified progressive updates immediately without inventing fake timestamps for plain lyrics.
     * - Gives top-priority synced sources (LRCLIB & official YouTube Music) a short grace window if lower-priority synced arrives first.
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
        val effectiveArtist = sanitizeArtistInput(artist)
        val cleanAlbum = album?.takeIf { it.isNotBlank() }

        coroutineScope {
            val resultChannel = Channel<RankedProviderResult>(Channel.UNLIMITED)
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            val remainingTopSyncProviders = mutableSetOf("lrclib")
            if (vId.isNotEmpty() && innerTube != null) {
                remainingTopSyncProviders.add("yt")
            }

            fun launchProvider(
                key: String,
                syncPriority: Int,
                plainPriority: Int,
                block: suspend () -> Lyrics?
            ) {
                val job = async {
                    val result = try {
                        block()
                    } catch (_: Exception) {
                        null
                    }
                    resultChannel.trySend(RankedProviderResult(key, syncPriority, plainPriority, result))
                }
                jobs.add(job)
            }

            // 1. LRCLIB (Highest synced fidelity)
            launchProvider("lrclib", syncPriority = 1, plainPriority = 2) {
                fetchLrclibFast(title, effectiveArtist, cleanAlbum, durationSeconds, vId)
            }

            // 2. YouTube Music Official Lyrics (ANDROID_MUSIC cueRange timed + WEB_REMIX fallback)
            if (vId.isNotEmpty() && innerTube != null) {
                launchProvider("yt", syncPriority = 2, plainPriority = 1) {
                    normalizeAndVerifyCandidate(
                        raw = innerTube.getLyrics(vId),
                        targetTitle = title,
                        targetAlbum = cleanAlbum
                    )
                }
            }

            // 3. Megalobiz Synced Lyrics
            launchProvider("megalobiz", syncPriority = 3, plainPriority = 99) {
                fetchMegalobiz(title, effectiveArtist, durationSeconds, vId)
            }

            // 4. lrcmux multi-source aggregator
            if (effectiveArtist.isNotEmpty()) {
                launchProvider("lrcmux", syncPriority = 4, plainPriority = 99) {
                    fetchLrcMux(title, effectiveArtist, durationSeconds, vId)
                }
            }

            // 5. NetEase Cloud Music
            launchProvider("netease", syncPriority = 5, plainPriority = 99) {
                fetchNetEase(title, effectiveArtist, durationSeconds, vId)
            }

            // 6. Plain-text fallbacks (authentic plain lyrics only; never fake-synced)
            launchProvider("jiosaavn", syncPriority = 99, plainPriority = 3) {
                fetchJioSaavn(title, effectiveArtist, vId, cleanAlbum)
            }
            launchProvider("looviq", syncPriority = 99, plainPriority = 4) {
                fetchLooviq(title, effectiveArtist, vId, cleanAlbum)
            }
            launchProvider("lyrofy", syncPriority = 99, plainPriority = 5) {
                fetchLyrofy(title, effectiveArtist, vId, cleanAlbum)
            }
            if (effectiveArtist.isNotEmpty()) {
                launchProvider("ovh", syncPriority = 99, plainPriority = 6) {
                    fetchLyricsOvh(title, effectiveArtist, vId, cleanAlbum)
                }
            }

            var remainingJobs = jobs.size
            var bestSynced: Pair<Int, Lyrics>? = null
            var bestPlain: Pair<Int, Lyrics>? = null
            var firstSyncedAtMs: Long? = null

            try {
                while (remainingJobs > 0) {
                    val currentBestSynced = bestSynced
                    if (currentBestSynced != null && (currentBestSynced.first <= 1 || remainingTopSyncProviders.isEmpty())) {
                        break
                    }

                    val msg = if (firstSyncedAtMs != null) {
                        val elapsed = System.currentTimeMillis() - firstSyncedAtMs
                        val waitLeft = (SYNCED_GRACE_WINDOW_MS - elapsed).coerceAtLeast(1L)
                        withTimeoutOrNull(waitLeft) { resultChannel.receive() } ?: break
                    } else {
                        resultChannel.receive()
                    }

                    remainingJobs--
                    remainingTopSyncProviders.remove(msg.key)

                    val candidate = msg.lyrics ?: continue
                    if (candidate.synced && candidate.lines.any { it.timeMs != null }) {
                        val cur = bestSynced
                        if (cur == null || msg.syncPriority < cur.first) {
                            bestSynced = msg.syncPriority to candidate
                            onProgressiveLyrics?.invoke(candidate)
                        }
                        if (firstSyncedAtMs == null) {
                            firstSyncedAtMs = System.currentTimeMillis()
                        }
                    } else if (bestSynced == null) {
                        val curPlain = bestPlain
                        if (curPlain == null || msg.plainPriority < curPlain.first) {
                            bestPlain = msg.plainPriority to candidate
                            onProgressiveLyrics?.invoke(candidate)
                        }
                    }
                }
            } finally {
                jobs.forEach { it.cancel() }
                resultChannel.close()
            }

            bestSynced?.second ?: bestPlain?.second
        }
    }

    private fun JsonObject.optString(key: String): String {
        val el = get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString.orEmpty() else ""
    }

    private fun JsonObject.optStringOrNull(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive) el.asString else null
    }

    private fun JsonObject.optDouble(key: String): Double? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive) runCatching { el.asDouble }.getOrNull() else null
    }

    private fun JsonObject.optFloat(key: String): Float? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive) runCatching { el.asFloat }.getOrNull() else null
    }

    private fun JsonObject.optLong(key: String): Long? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive) runCatching { el.asLong }.getOrNull() else null
    }

    private fun JsonObject.optObject(key: String): JsonObject? {
        val el = get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private fun JsonObject.optArray(key: String): JsonArray? {
        val el = get(key) ?: return null
        return if (el.isJsonArray) el.asJsonArray else null
    }

    private suspend fun fetchLrclibFast(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
        videoId: String
    ): Lyrics? = coroutineScope {
        val effectiveArtist = sanitizeArtistInput(artist)
        val pArtist = if (effectiveArtist.isNotEmpty()) getPrimaryArtist(effectiveArtist) else ""
        val artistTokens = if (effectiveArtist.isNotEmpty()) {
            effectiveArtist.split(Regex("""\s*(?:,|&|feat\.?|ft\.?|featuring|/)\s*""", RegexOption.IGNORE_CASE))
                .map { cleanArtist(it).trim() }
                .filter { it.isNotBlank() && !InnerTubeClient.isMissingArtist(it) }
                .distinct()
        } else {
            emptyList()
        }
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(3)
        val primaryTitle = titleVariants.firstOrNull() ?: title

        val exactDeferred = async {
            if (pArtist.isEmpty()) return@async null
            val artistCandidates = (listOf(pArtist) + artistTokens).distinct().take(2)
            var fallbackPlain: Lyrics? = null
            for (tVar in titleVariants.take(2)) {
                for (aCand in artistCandidates) {
                    // Omit album_name on /get so playlist or YouTube soundtrack suffixes never trigger false 404s.
                    val obj = fetchLrclibExact(tVar, aCand, durationSeconds)
                    if (obj != null) {
                        val parsed = parseLrclibResponse(obj, videoId, title, album)
                        if (parsed != null) {
                            if (parsed.synced) return@async parsed
                            if (fallbackPlain == null) fallbackPlain = parsed
                        }
                    }
                }
            }
            fallbackPlain
        }

        val searchDeferred = async {
            val queries = mutableListOf<String>()
            if (pArtist.isNotEmpty()) {
                for (tVar in titleVariants.take(2)) {
                    queries.add("$tVar $pArtist")
                }
            } else {
                queries.addAll(titleVariants.take(2))
            }

            var fallbackPlain: Lyrics? = null
            for (q in queries.distinct()) {
                val parsed = fetchLrclibQuerySearch(
                    query = q,
                    durationSeconds = durationSeconds,
                    targetTitle = title,
                    targetArtist = effectiveArtist,
                    targetAlbum = album,
                    videoId = videoId
                )
                if (parsed != null) {
                    if (parsed.synced) return@async parsed
                    if (fallbackPlain == null) fallbackPlain = parsed
                }
            }

            // Secondary title-only search when artist+title failed (requires artist/album validation in rankLrclibCandidates)
            if (pArtist.isNotEmpty()) {
                for (tVar in titleVariants.take(2)) {
                    if (tVar != "$primaryTitle $pArtist") {
                        val parsed = fetchLrclibQuerySearch(
                            query = tVar,
                            durationSeconds = durationSeconds,
                            targetTitle = title,
                            targetArtist = effectiveArtist,
                            targetAlbum = album,
                            videoId = videoId
                        )
                        if (parsed != null) {
                            if (parsed.synced) return@async parsed
                            if (fallbackPlain == null) fallbackPlain = parsed
                        }
                    }
                }
            }
            fallbackPlain
        }

        val exactResult = exactDeferred.await()
        if (exactResult != null && exactResult.synced) return@coroutineScope exactResult

        val searchResult = searchDeferred.await()
        if (searchResult != null && searchResult.synced) return@coroutineScope searchResult

        exactResult ?: searchResult
    }

    private fun fetchLrclibExact(
        title: String,
        artist: String,
        durationSeconds: Int
    ): JsonObject? {
        return try {
            val urlBuilder = (LRCLIB_BASE + "/get").toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("track_name", title)
            urlBuilder.addQueryParameter("artist_name", artist)
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
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        videoId: String
    ): Lyrics? {
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

                val ranked = rankLrclibCandidates(arr, durationSeconds, targetTitle, targetArtist, targetAlbum)
                for (item in ranked) {
                    val parsed = parseLrclibResponse(item, videoId, targetTitle, targetAlbum)
                    if (parsed != null) return parsed
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB query search failed for $query", e)
            null
        }
    }

    internal fun rankLrclibCandidates(
        arr: JsonArray,
        durationSeconds: Int,
        targetTitle: String = "",
        targetArtist: String = "",
        targetAlbum: String? = null
    ): List<JsonObject> {
        val targetLangs = detectExplicitLanguageTags("$targetTitle ${targetAlbum.orEmpty()}")
        val targetMeta = "$targetTitle ${targetAlbum.orEmpty()}"
        val cleanTargetAlbum = targetAlbum?.let { stripAllBrackets(it).lowercase().trim() }.orEmpty()
        val effectiveArtist = sanitizeArtistInput(targetArtist)

        data class ScoredCandidate(
            val item: JsonObject,
            val synced: Boolean,
            val artistMatched: Boolean,
            val extraDubPenalty: Int,
            val diff: Double
        )

        val candidates = mutableListOf<ScoredCandidate>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val item = el.asJsonObject
            val trackName = item.optString("trackName")
            val artistName = item.optString("artistName")
            val albumName = item.optString("albumName")
            val synced = item.optString("syncedLyrics")
            val plain = item.optString("plainLyrics")
            if (synced.isBlank() && plain.isBlank()) continue

            if (targetTitle.isNotEmpty() && trackName.isNotEmpty() && !isSongTitleMatch(trackName, targetTitle)) {
                continue
            }

            val candMeta = "$trackName $albumName".trim()
            if (targetTitle.isNotEmpty()) {
                if (hasConflictingVariantTag(candMeta, targetTitle)) continue
                if (hasConflictingLanguageTag(candMeta, targetMeta)) continue
            }

            val d = item.optDouble("duration") ?: 0.0
            val diff = if (durationSeconds > 0 && d > 0.0) {
                abs(durationSeconds.toDouble() - d)
            } else {
                10.0
            }
            if (durationSeconds > 0 && d > 0.0 && diff > MAX_DURATION_DIFF_SECONDS) {
                continue
            }

            val artistMatched = effectiveArtist.isEmpty() || artistName.isBlank() || isArtistMatch(artistName, effectiveArtist)
            val albumMatches = cleanTargetAlbum.isNotEmpty() && albumName.isNotBlank() &&
                isSongTitleMatch(albumName, cleanTargetAlbum)
            if (!artistMatched && !(albumMatches && durationSeconds > 0 && diff <= 5.0)) {
                continue
            }

            val candLangs = detectExplicitLanguageTags(candMeta)
            val extraDubPenalty = if (targetLangs.isEmpty() && candLangs.isNotEmpty()) 1 else 0

            candidates.add(
                ScoredCandidate(
                    item = item,
                    synced = synced.isNotBlank(),
                    artistMatched = artistMatched,
                    extraDubPenalty = extraDubPenalty,
                    diff = diff
                )
            )
        }

        return candidates
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.synced }
                    .thenByDescending { it.artistMatched }
                    .thenBy { it.extraDubPenalty }
                    .thenBy { it.diff }
            )
            .map { it.item }
    }

    private fun parseLrclibResponse(
        json: JsonObject,
        videoId: String = "",
        targetTitle: String = "",
        targetAlbum: String? = null
    ): Lyrics? {
        val trackName = json.optString("trackName")
        val albumName = json.optString("albumName")
        val candMeta = "$trackName $albumName".trim()

        if (targetTitle.isNotEmpty()) {
            if (trackName.isNotEmpty() && !isSongTitleMatch(trackName, targetTitle)) return null
            if (hasConflictingVariantTag(candMeta, targetTitle)) return null
            if (hasConflictingLanguageTag(candMeta, "$targetTitle ${targetAlbum.orEmpty()}")) return null
        }

        val synced = json.optStringOrNull("syncedLyrics")
        if (!synced.isNullOrBlank()) {
            val lines = cleanLyricLines(parseLrc(synced))
            if (lines.isNotEmpty() &&
                (targetTitle.isEmpty() || validateLyricScriptLanguage(lines, targetTitle, targetAlbum, candMeta))
            ) {
                return Lyrics(
                    videoId = videoId,
                    source = "LRCLIB",
                    synced = lines.any { it.timeMs != null },
                    lines = lines
                )
            }
        }

        val plain = json.optStringOrNull("plainLyrics")
        if (!plain.isNullOrBlank()) {
            val rawLines = plain.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }
            val lines = cleanLyricLines(rawLines)

            if (lines.isNotEmpty() &&
                (targetTitle.isEmpty() || validateLyricScriptLanguage(lines, targetTitle, targetAlbum, candMeta))
            ) {
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
    private fun fetchLooviq(
        title: String,
        artist: String,
        videoId: String,
        album: String? = null
    ): Lyrics? {
        val effectiveArtist = sanitizeArtistInput(artist)
        val pArtist = if (effectiveArtist.isNotEmpty()) getPrimaryArtist(effectiveArtist) else ""
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(2)
        val queries = buildList {
            for (v in titleVariants) {
                if (pArtist.isNotEmpty()) add("$v $pArtist")
                add(v)
            }
        }.distinct().filter { it.isNotBlank() }

        for (q in queries) {
            try {
                val urlBuilder = "https://looviq.com/search".toHttpUrlOrNull()?.newBuilder() ?: continue
                urlBuilder.addQueryParameter("q", q)
                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                val html = fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
                } ?: continue
                if (html.isEmpty()) continue

                val linkRegex = Regex("""<a href="(https://looviq\.com/lyrics/[^"]+)" class="song-card-title">\s*([^<]+)\s*</a>""")
                val matches = linkRegex.findAll(html).toList()
                if (matches.isEmpty()) continue

                var targetUrl: String? = null
                var matchedCardTitle = ""
                for (m in matches) {
                    val cardTitle = unescapeHtml(m.groupValues[2].trim())
                    if (isSongTitleMatch(cardTitle, title) &&
                        !hasConflictingLanguageTag(cardTitle, "$title ${album.orEmpty()}")
                    ) {
                        targetUrl = m.groupValues[1]
                        matchedCardTitle = cardTitle
                        break
                    }
                }
                if (targetUrl == null) continue

                val lyrReq = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
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

                    if (lines.size >= 3 && validateLyricScriptLanguage(lines, title, album, matchedCardTitle)) {
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
    private fun fetchLyricsOvh(
        title: String,
        artist: String,
        videoId: String,
        album: String? = null
    ): Lyrics? {
        val effectiveArtist = sanitizeArtistInput(artist)
        if (effectiveArtist.isEmpty()) return null
        val t = extractSearchTitleVariants(title, effectiveArtist).firstOrNull() ?: return null
        val a = getPrimaryArtist(effectiveArtist)

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
            val lyr = json.optString("lyrics").trim()
            if (lyr.length > 50) {
                val lines = cleanLyricLines(
                    lyr.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LyricLine(timeMs = null, text = it) }
                )

                if (lines.size >= 3 && validateLyricScriptLanguage(lines, title, album)) {
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
    private fun fetchJioSaavn(
        title: String,
        artist: String,
        videoId: String,
        album: String? = null
    ): Lyrics? {
        val effectiveArtist = sanitizeArtistInput(artist)
        val pArtist = if (effectiveArtist.isNotEmpty()) getPrimaryArtist(effectiveArtist) else ""
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(2)
        val queries = buildList {
            for (v in titleVariants) {
                if (pArtist.isNotEmpty()) add("$v $pArtist")
            }
            addAll(titleVariants)
        }.distinct()

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
                val results = json.optArray("results") ?: continue

                for (el in results) {
                    if (!el.isJsonObject) continue
                    val item = el.asJsonObject
                    val songName = unescapeHtml(item.optString("song").ifEmpty { item.optString("title") })
                    val itemLang = item.optString("language")
                    val itemAlbum = unescapeHtml(item.optString("album"))
                    val candMeta = "$songName $itemAlbum $itemLang".trim()

                    if (songName.isNotBlank() && !isSongTitleMatch(songName, title)) continue
                    if (hasConflictingLanguageTag(candMeta, "$title ${album.orEmpty()}")) continue

                    val itemArtists = buildString {
                        item.optString("primary_artists").takeIf { it.isNotBlank() }?.let { append(it) }
                        item.optString("singers").takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }
                    }
                    if (effectiveArtist.isNotEmpty() && itemArtists.isNotBlank() && !isArtistMatch(itemArtists, effectiveArtist)) {
                        continue
                    }

                    if (item.optString("has_lyrics") == "true") {
                        val songId = item.optStringOrNull("id")?.takeIf { it.isNotBlank() } ?: continue
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
                        var rawText = lyrJson.optString("lyrics")
                        rawText = rawText.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                        rawText = rawText.replace(Regex("""<[^>]+>"""), "")
                        rawText = unescapeHtml(rawText).trim()

                        val lines = cleanLyricLines(
                            rawText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { LyricLine(timeMs = null, text = it) }
                        )

                        if (lines.size >= 3 && validateLyricScriptLanguage(lines, title, album, candMeta)) {
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
    private fun fetchLyrofy(
        title: String,
        artist: String,
        videoId: String,
        album: String? = null
    ): Lyrics? {
        val effectiveArtist = sanitizeArtistInput(artist)
        val cTitle = extractSearchTitleVariants(title, effectiveArtist).firstOrNull() ?: return null
        try {
            val urlBuilder = "https://lyrofy.com/".toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("s", cTitle)
            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val pageHtml = fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
            } ?: return null
            if (pageHtml.isEmpty()) return null

            val linkRegex = Regex("""<a\s+[^>]*href="(https?://lyrofy\.com/[^"]+)"[^>]*>(.*?)</a>""")
            var targetUrl: String? = null
            var matchedCardTitle = ""
            for (m in linkRegex.findAll(pageHtml)) {
                val link = m.groupValues[1]
                val text = unescapeHtml(m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim())
                if (isSongTitleMatch(text, title) &&
                    !hasConflictingLanguageTag(text, "$title ${album.orEmpty()}")
                ) {
                    targetUrl = link
                    matchedCardTitle = text
                    break
                }
            }
            if (targetUrl == null) return null

            val lyrReq = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
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

            val lyricParagraphs = rawParagraphs.filter { !isDescriptionOrMetadataLine(it) }
            val fullText = lyricParagraphs.joinToString("\n\n")
            val rawLines = fullText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }

            val lines = cleanLyricLines(rawLines)

            if (lines.size >= 3 && validateLyricScriptLanguage(lines, title, album, matchedCardTitle)) {
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
        val effectiveArtist = sanitizeArtistInput(artist)
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(2)
        val queries = buildList {
            for (v in titleVariants) {
                if (effectiveArtist.isNotEmpty()) add("$v $effectiveArtist")
            }
            if (effectiveArtist.isEmpty()) {
                addAll(titleVariants)
            } else {
                add(titleVariants.first())
            }
        }.distinct().filter { it.isNotBlank() }

        for (query in queries) {
            try {
                val url = "https://api.megalobiz.com/api/public/v1/search?q=" + URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                val candidates = fastHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<Pair<String, String>>()
                    val body = response.body?.string() ?: return@use emptyList<Pair<String, String>>()
                    val json = JsonParser.parseString(body).asJsonObject
                    val results = json.optArray("results") ?: return@use emptyList<Pair<String, String>>()
                    if (results.size() == 0) return@use emptyList<Pair<String, String>>()

                    data class MegaCand(val lrcUrl: String, val itemTitle: String, val diff: Long)
                    val valid = mutableListOf<MegaCand>()
                    for (elem in results) {
                        if (!elem.isJsonObject) continue
                        val item = elem.asJsonObject
                        val itemTitle = item.optString("title")
                        val itemArtist = item.optString("artist")
                        val lrcUrl = item.optString("lrc_url")
                        val dur = item.optFloat("duration") ?: 0f

                        if (lrcUrl.isEmpty()) continue
                        if (!isSongTitleMatch(itemTitle, title)) continue
                        if (hasConflictingVariantTag(itemTitle, title)) continue
                        if (hasConflictingLanguageTag(itemTitle, title)) continue
                        if (effectiveArtist.isNotEmpty() && itemArtist.isNotBlank() && !isArtistMatch(itemArtist, effectiveArtist)) {
                            continue
                        }

                        val diff = if (durationSeconds > 0 && dur > 0f) {
                            abs(dur.toLong() - durationSeconds.toLong())
                        } else if (durationSeconds > 0) {
                            12L
                        } else {
                            0L
                        }
                        if (durationSeconds > 0 && dur > 0f && diff > MAX_DURATION_DIFF_SECONDS) {
                            continue
                        }
                        valid.add(MegaCand(lrcUrl, "$itemTitle $itemArtist".trim(), diff))
                    }
                    valid.sortedBy { it.diff }.map { it.lrcUrl to it.itemTitle }.take(3)
                }

                for ((lrcUrlToFetch, itemMeta) in candidates) {
                    val lrcReq = Request.Builder()
                        .url(lrcUrlToFetch)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val result = fastHttpClient.newCall(lrcReq).execute().use { lrcResp ->
                        if (lrcResp.isSuccessful) {
                            val lrcText = lrcResp.body?.string().orEmpty()
                            val lines = cleanLyricLines(parseLrc(lrcText))
                            if (lines.isNotEmpty() &&
                                lines.any { it.timeMs != null } &&
                                validateLyricScriptLanguage(lines, title, null, itemMeta)
                            ) {
                                Log.d(TAG, "Retrieved synced lyrics from Megalobiz for $title (${lines.size} lines)")
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
        val effectiveArtist = sanitizeArtistInput(artist)
        val pArtist = if (effectiveArtist.isNotEmpty()) getPrimaryArtist(effectiveArtist) else ""
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(2)
        val queries = titleVariants.map { v ->
            if (pArtist.isNotEmpty()) "$v $pArtist" else v
        }.distinct()

        for (query in queries) {
            try {
                val formBody = FormBody.Builder()
                    .add("s", query)
                    .add("limit", "6")
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

                val candidateIds = fastHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<Pair<Long, String>>()
                    val body = response.body?.string() ?: return@use emptyList<Pair<Long, String>>()
                    val json = JsonParser.parseString(body).asJsonObject
                    val resultObj = json.optObject("result") ?: return@use emptyList<Pair<Long, String>>()
                    val songs = resultObj.optArray("songs") ?: return@use emptyList<Pair<Long, String>>()
                    if (songs.size() == 0) return@use emptyList<Pair<Long, String>>()

                    data class NetEaseCand(val id: Long, val meta: String, val diff: Long)
                    val matched = mutableListOf<NetEaseCand>()
                    for (elem in songs) {
                        if (!elem.isJsonObject) continue
                        val sObj = elem.asJsonObject
                        val sId = sObj.optLong("id") ?: continue
                        val sName = sObj.optString("name")
                        val aliases = sObj.optArray("alias")
                            ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                            ?.joinToString(" ")
                            .orEmpty()
                        val albumName = sObj.optObject("album")?.optString("name").orEmpty()
                        val artistsJoined = sObj.optArray("artists")
                            ?.mapNotNull { if (it.isJsonObject) it.asJsonObject.optStringOrNull("name") else null }
                            ?.joinToString(", ")
                            .orEmpty()
                        val candMeta = "$sName $aliases $albumName".trim()

                        if (!isSongTitleMatch(sName, title)) continue
                        if (hasConflictingVariantTag(candMeta, title)) continue
                        if (hasConflictingLanguageTag(candMeta, title)) continue
                        if (effectiveArtist.isNotEmpty() && artistsJoined.isNotBlank() && !isArtistMatch(artistsJoined, effectiveArtist)) {
                            continue
                        }

                        val durSec = (sObj.optLong("duration") ?: 0L) / 1000L
                        val diff = if (durationSeconds > 0 && durSec > 0L) {
                            abs(durSec - durationSeconds.toLong())
                        } else if (durationSeconds > 0) {
                            12L
                        } else {
                            0L
                        }
                        if (durationSeconds > 0 && durSec > 0L && diff > MAX_DURATION_DIFF_SECONDS) {
                            continue
                        }
                        matched.add(NetEaseCand(sId, candMeta, diff))
                    }
                    matched.sortedBy { it.diff }.map { it.id to it.meta }.take(2)
                }

                for ((songId, candMeta) in candidateIds) {
                    val lyricReq = Request.Builder()
                        .url("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://music.163.com/")
                        .build()

                    val parsed = fastHttpClient.newCall(lyricReq).execute().use { lResp ->
                        if (lResp.isSuccessful) {
                            val lBody = lResp.body?.string() ?: return@use null
                            val lJson = JsonParser.parseString(lBody).asJsonObject
                            val lrcText = lJson.optObject("lrc")?.optString("lyric").orEmpty()
                            val lines = cleanLyricLines(parseLrc(lrcText))
                            if (lines.isNotEmpty() &&
                                lines.any { it.timeMs != null } &&
                                validateLyricScriptLanguage(lines, title, null, candMeta)
                            ) {
                                Log.d(TAG, "Retrieved synced lyrics from NetEase for $title (${lines.size} lines)")
                                Lyrics(
                                    videoId = videoId,
                                    source = "NetEase",
                                    synced = true,
                                    lines = lines
                                )
                            } else null
                        } else null
                    }
                    if (parsed != null) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "NetEase lookup failed for $query", e)
            }
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
        val effectiveArtist = sanitizeArtistInput(artist)
        if (effectiveArtist.isEmpty()) return@withContext null
        val pArtist = getPrimaryArtist(effectiveArtist)
        val titleVariants = extractSearchTitleVariants(title, effectiveArtist).take(2)

        for (tVar in titleVariants) {
            try {
                val urlBuilder = ("https://api.lrcmux.dev/compat/lrclib/api/get").toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
                urlBuilder.addQueryParameter("track_name", tVar)
                urlBuilder.addQueryParameter("artist_name", pArtist)
                if (durationSeconds > 0) {
                    urlBuilder.addQueryParameter("duration", durationSeconds.toString())
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .build()

                val parsed = fastHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val json = JsonParser.parseString(body).asJsonObject
                    val trackName = json.optString("trackName")
                    val albumName = json.optString("albumName")
                    val candMeta = "$trackName $albumName".trim()

                    if (trackName.isNotBlank() && !isSongTitleMatch(trackName, title)) {
                        Log.w(TAG, "lrcmux trackName mismatch: '$trackName' does not match target '$title'")
                        return@use null
                    }
                    if (hasConflictingVariantTag(candMeta, title) || hasConflictingLanguageTag(candMeta, title)) {
                        return@use null
                    }
                    val dur = json.optDouble("duration") ?: 0.0
                    if (durationSeconds > 0 && dur > 0.0 && abs(durationSeconds.toDouble() - dur) > MAX_DURATION_DIFF_SECONDS) {
                        return@use null
                    }

                    val syncedLrc = json.optStringOrNull("syncedLyrics")
                    if (!syncedLrc.isNullOrBlank()) {
                        val lines = cleanLyricLines(parseLrc(syncedLrc))
                        if (lines.isNotEmpty() &&
                            lines.any { it.timeMs != null } &&
                            validateLyricScriptLanguage(lines, title, null, candMeta)
                        ) {
                            Log.d(TAG, "Retrieved synced lyrics from lrcmux for $title (${lines.size} lines)")
                            Lyrics(
                                videoId = videoId,
                                source = "lrcmux",
                                synced = true,
                                lines = lines
                            )
                        } else null
                    } else null
                }
                if (parsed != null) return@withContext parsed
            } catch (e: Exception) {
                Log.w(TAG, "lrcmux lookup failed for $tVar", e)
            }
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
