package com.koi.backend.share

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.koi.backend.innertube.SongItem
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2InputStream
import org.tukaani.xz.LZMA2Options
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Codec for serializing a playlist's exact song list to compact JSON,
 * choosing the smallest lossless compression (LZMA2 vs raw DEFLATE vs raw UTF-8),
 * and encoding/decoding it into a URL-safe QR Alphanumeric payload (`koi://p/<BODY>`).
 */
object PlaylistShareCodec {

    const val SCHEME = "KOI"
    const val HOST = "P"
    const val URI_PREFIX = "$SCHEME://$HOST/"

    private const val FORMAT_VERSION = 1
    private const val HEADER_RAW_JSON: Byte = 0x10
    private const val HEADER_DEFLATE: Byte = 0x11
    private const val HEADER_LZMA2: Byte = 0x12

    private const val LZMA2_DICT_SIZE = 65_536 // 64 KiB fixed dict (covers max QR payload)
    private const val MAX_DECOMPRESSED_BYTES = 262_144 // 256 KiB safety cap

    /**
     * 41-character subset of the ISO/IEC 18004 QR Alphanumeric character set (45 chars total),
     * excluding space, '%', '/', ':' so the encoded body is a single clean RFC 3986 path segment.
     * Since 41^3 = 68,921 >= 65,536 and 41^2 = 1,681 >= 256, 2 bytes encode into 3 chars.
     */
    const val BASE41_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-+$*."
    private val BASE41_REVERSE = IntArray(128) { -1 }.apply {
        BASE41_ALPHABET.forEachIndexed { idx, ch -> this[ch.code] = idx }
    }

    /**
     * Maximum Alphanumeric characters for QR Version 40 Error Correction Level L (4,296 chars).
     * Using uppercase `KOI://P/<BASE41>` keeps the entire URI inside pure QR Alphanumeric mode.
     */
    const val MAX_QR_V40_TOTAL_CHARS = 4296

    private const val GOOGLE_USER_CONTENT_PREFIX = "https://lh3.googleusercontent.com/"
    private const val GOOGLE_SUFFIX_800 = "=w800-h800-l90-rj"
    private const val GOOGLE_SUFFIX_544 = "=w544-h544-l90-rj"

    data class SharedPlaylistData(
        val title: String,
        val description: String = "",
        val coverUrl: String? = null,
        val songs: List<SongItem>
    )

    data class EncodedPlaylistPayload(
        val uri: String,
        val body: String,
        val jsonBytesCount: Int,
        val compressedBytesCount: Int,
        val compressionMethod: String
    ) {
        val savedPercent: Int
            get() = if (jsonBytesCount <= 0) 0
            else (((jsonBytesCount - compressedBytesCount).toFloat() / jsonBytesCount) * 100f)
                .toInt()
                .coerceIn(0, 99)
    }

    /**
     * Converts a [Playlist] and its exact [SongItem] list into a compact JSON string.
     */
    fun toCompactJson(
        title: String,
        description: String?,
        coverUrl: String?,
        songs: List<SongItem>
    ): String {
        val root = JsonObject()
        root.addProperty("v", FORMAT_VERSION)
        root.addProperty("t", title.trim().ifEmpty { "Shared Playlist" })

        val cleanDesc = description?.trim().orEmpty()
        val isAutoDesc = cleanDesc.isEmpty() ||
            cleanDesc == "${songs.size} songs" ||
            cleanDesc == "${songs.size} song" ||
            cleanDesc == "${songs.size} songs you love"
        if (!isAutoDesc) {
            root.addProperty("d", cleanDesc)
        }

        val firstResolvedThumb = songs.firstOrNull()?.let { resolveDefaultOrCustomThumb(it) }
        val compactCover = compressThumbnailToken(coverUrl, videoId = null)
        if (!compactCover.isNullOrEmpty() && expandThumbnailToken(compactCover, null) != firstResolvedThumb) {
            root.addProperty("c", compactCover)
        }

        val songsArray = JsonArray()
        for (song in songs) {
            if (song.videoId.isBlank() && song.title.isBlank()) continue
            val item = JsonArray()
            item.add(song.videoId)
            item.add(song.title)
            item.add(song.artists)
            item.add(song.durationSeconds)

            val album = song.album?.trim().orEmpty()
            val thumbToken = compressThumbnailToken(song.thumbnailUrl, song.videoId).orEmpty()
            val explicitInt = if (song.explicit) 1 else 0

            if (explicitInt != 0 || thumbToken.isNotEmpty() || album.isNotEmpty()) {
                item.add(album)
            }
            if (explicitInt != 0 || thumbToken.isNotEmpty()) {
                item.add(thumbToken)
            }
            if (explicitInt != 0) {
                item.add(explicitInt)
            }
            songsArray.add(item)
        }
        root.add("s", songsArray)
        return root.toString()
    }

    fun toCompactJson(playlist: Playlist): String {
        return toCompactJson(
            title = playlist.title,
            description = playlist.description,
            coverUrl = playlist.thumbnailUrl,
            songs = playlist.tracks.map { it.toSongItem() }
        )
    }

    /**
     * Compresses the playlist JSON using the best available lossless codec
     * (LZMA2 vs raw DEFLATE vs raw UTF-8) and encodes it as `koi://p/<BASE41>`.
     */
    fun encodePlaylist(playlist: Playlist): EncodedPlaylistPayload {
        return encodePlaylist(
            title = playlist.title,
            description = playlist.description,
            coverUrl = playlist.thumbnailUrl,
            songs = playlist.tracks.map { it.toSongItem() }
        )
    }

    fun encodePlaylist(
        title: String,
        description: String?,
        coverUrl: String?,
        songs: List<SongItem>
    ): EncodedPlaylistPayload {
        require(songs.isNotEmpty()) { "Cannot share an empty playlist" }

        val json = toCompactJson(title, description, coverUrl, songs)
        val rawBytes = json.toByteArray(StandardCharsets.UTF_8)

        val deflateBytes = compressDeflate(rawBytes)
        val lzmaBytes = compressLzma2(rawBytes)

        val candidates = listOf(
            Triple(HEADER_RAW_JSON, rawBytes, "None"),
            Triple(HEADER_DEFLATE, deflateBytes, "DEFLATE"),
            Triple(HEADER_LZMA2, lzmaBytes, "LZMA2")
        )
        val best = candidates.minBy { it.second.size }

        val framed = ByteArray(1 + best.second.size)
        framed[0] = best.first
        System.arraycopy(best.second, 0, framed, 1, best.second.size)

        val body = base41Encode(framed)
        val uri = "$URI_PREFIX$body"
        return EncodedPlaylistPayload(
            uri = uri,
            body = body,
            jsonBytesCount = rawBytes.size,
            compressedBytesCount = framed.size,
            compressionMethod = best.third
        )
    }

    /**
     * Checks whether a scanned/received string looks like a Koi playlist share payload or URI.
     */
    fun isKoiPlaylistPayload(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("koi://") ||
            lower.startsWith("https://koi.music/") ||
            lower.startsWith("https://koi.app/")
        ) {
            return extractBodyToken(trimmed) != null
        }
        return false
    }

    /**
     * Decodes a QR string (`koi://p/...`, `KOI://P/...`, `koi://playlist?d=...`, or raw JSON)
     * back into [SharedPlaylistData] with the exact ordered list of songs.
     */
    fun decode(rawInput: String): Result<SharedPlaylistData> {
        return runCatching {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) error("Empty QR payload")

            val jsonText = if (trimmed.startsWith("{")) {
                trimmed
            } else {
                val token = extractBodyToken(trimmed)
                    ?: error("Not a valid Koi playlist link")
                val framed = base41Decode(token.uppercase())
                if (framed.isEmpty()) error("Empty compressed payload")

                val header = framed[0]
                val payloadOffset = 1
                val payloadLen = framed.size - payloadOffset
                if (payloadLen <= 0) error("Truncated payload")

                val decompressed = when (header) {
                    HEADER_RAW_JSON -> framed.copyOfRange(payloadOffset, framed.size)
                    HEADER_DEFLATE -> decompressDeflate(framed, payloadOffset, payloadLen)
                    HEADER_LZMA2 -> decompressLzma2(framed, payloadOffset, payloadLen)
                    else -> error("Unsupported compression format")
                }
                String(decompressed, StandardCharsets.UTF_8)
            }

            parsePlaylistJson(jsonText)
        }
    }

    private fun extractBodyToken(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("koi://p/", ignoreCase = true)) {
            return trimmed.substring(8).substringBefore('?').substringBefore('#').trim().takeIf { it.isNotEmpty() }
        }
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "koi" && scheme != "https" && scheme != "http") return null

            val query = uri.rawQuery
            if (!query.isNullOrBlank()) {
                for (pair in query.split('&')) {
                    val idx = pair.indexOf('=')
                    if (idx > 0) {
                        val key = pair.substring(0, idx)
                        val value = pair.substring(idx + 1).trim()
                        if ((key == "d" || key == "data" || key == "p") && value.isNotEmpty()) {
                            return value
                        }
                    }
                }
            }

            val path = uri.rawPath?.trim('/') ?: ""
            if (scheme == "koi") {
                val host = uri.host?.lowercase() ?: ""
                if ((host == "p" || host == "playlist") && path.isNotEmpty()) {
                    return path.substringAfterLast('/')
                }
                if (host.length > 8 && isAllBase41(host.uppercase())) {
                    return host
                }
            } else if (path.startsWith("p/") && path.length > 2) {
                return path.removePrefix("p/").substringAfterLast('/')
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun isAllBase41(value: String): Boolean {
        for (ch in value) {
            val code = ch.code
            if (code >= BASE41_REVERSE.size || BASE41_REVERSE[code] < 0) return false
        }
        return true
    }

    private fun parsePlaylistJson(jsonText: String): SharedPlaylistData {
        val root = JsonParser.parseString(jsonText).asJsonObject
        val title = when {
            root.has("t") && !root.get("t").isJsonNull -> root.get("t").asString
            root.has("title") && !root.get("title").isJsonNull -> root.get("title").asString
            root.has("n") && !root.get("n").isJsonNull -> root.get("n").asString
            else -> "Shared Playlist"
        }.trim().ifEmpty { "Shared Playlist" }

        val description = when {
            root.has("d") && !root.get("d").isJsonNull -> root.get("d").asString
            root.has("description") && !root.get("description").isJsonNull -> root.get("description").asString
            else -> ""
        }.trim()

        val rawCover = when {
            root.has("c") && !root.get("c").isJsonNull -> root.get("c").asString
            root.has("coverUrl") && !root.get("coverUrl").isJsonNull -> root.get("coverUrl").asString
            else -> null
        }

        val songsElement = root.get("s") ?: root.get("songs") ?: error("Playlist has no song list")
        if (!songsElement.isJsonArray) error("Invalid song list")

        val songs = mutableListOf<SongItem>()
        for (elem in songsElement.asJsonArray) {
            if (elem.isJsonArray) {
                val arr = elem.asJsonArray
                if (arr.size() < 2) continue
                val videoId = arr.get(0).safeString()
                val songTitle = arr.get(1).safeString()
                val artists = if (arr.size() > 2) arr.get(2).safeString() else ""
                val durationSeconds = if (arr.size() > 3) arr.get(3).safeInt() else 0
                val album = if (arr.size() > 4) arr.get(4).safeString().ifBlank { null } else null
                val rawThumb = if (arr.size() > 5) arr.get(5).safeString().ifBlank { null } else null
                val explicit = if (arr.size() > 6) arr.get(6).safeBoolean() else false

                val expandedThumb = expandThumbnailToken(rawThumb, videoId)
                songs.add(
                    SongItem(
                        videoId = videoId,
                        title = songTitle,
                        artists = artists,
                        album = album,
                        duration = formatDuration(durationSeconds),
                        durationSeconds = durationSeconds,
                        thumbnailUrl = expandedThumb,
                        explicit = explicit
                    )
                )
            } else if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val videoId = obj.firstString("i", "id", "videoId")
                val songTitle = obj.firstString("t", "title")
                val artists = obj.firstString("a", "artists", "artist")
                val durationSeconds = obj.firstInt("d", "durationSeconds")
                val album = obj.firstString("b", "album").ifBlank { null }
                val rawThumb = obj.firstString("u", "thumbnailUrl").ifBlank { null }
                val explicit = obj.firstBool("e", "explicit")
                val expandedThumb = expandThumbnailToken(rawThumb, videoId)
                songs.add(
                    SongItem(
                        videoId = videoId,
                        title = songTitle,
                        artists = artists,
                        album = album,
                        duration = formatDuration(durationSeconds),
                        durationSeconds = durationSeconds,
                        thumbnailUrl = expandedThumb,
                        explicit = explicit
                    )
                )
            }
        }

        if (songs.isEmpty()) error("Playlist contains no valid songs")

        val coverUrl = expandThumbnailToken(rawCover, null)
            ?: songs.firstOrNull()?.thumbnailUrl

        return SharedPlaylistData(
            title = title,
            description = description,
            coverUrl = coverUrl,
            songs = songs
        )
    }

    private fun resolveDefaultOrCustomThumb(song: SongItem): String? {
        val raw = song.thumbnailUrl?.ifBlank { null }
            ?: if (song.videoId.isNotBlank()) "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg" else null
        return raw
    }

    internal fun compressThumbnailToken(url: String?, videoId: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null // Do not leak local content:// or file:// paths across devices
        }
        if (!videoId.isNullOrBlank()) {
            val ytPrefix = "https://i.ytimg.com/vi/$videoId/"
            if (trimmed.startsWith(ytPrefix)) {
                val file = trimmed.removePrefix(ytPrefix)
                if (file == "hqdefault.jpg" || file == "default.jpg" || file == "mqdefault.jpg") {
                    return ""
                }
                return "@y:$file"
            }
        }
        if (trimmed.startsWith(GOOGLE_USER_CONTENT_PREFIX)) {
            val rest = trimmed.removePrefix(GOOGLE_USER_CONTENT_PREFIX)
            return when {
                rest.endsWith(GOOGLE_SUFFIX_800) -> "@g1:${rest.removeSuffix(GOOGLE_SUFFIX_800)}"
                rest.endsWith(GOOGLE_SUFFIX_544) -> "@g2:${rest.removeSuffix(GOOGLE_SUFFIX_544)}"
                else -> "@g0:$rest"
            }
        }
        return trimmed
    }

    internal fun expandThumbnailToken(token: String?, videoId: String?): String? {
        val t = token?.trim().orEmpty()
        if (t.isEmpty()) {
            return if (!videoId.isNullOrBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
        }
        return when {
            t.startsWith("@g1:") -> "$GOOGLE_USER_CONTENT_PREFIX${t.removePrefix("@g1:")}$GOOGLE_SUFFIX_800"
            t.startsWith("@g2:") -> "$GOOGLE_USER_CONTENT_PREFIX${t.removePrefix("@g2:")}$GOOGLE_SUFFIX_544"
            t.startsWith("@g0:") -> "$GOOGLE_USER_CONTENT_PREFIX${t.removePrefix("@g0:")}"
            t.startsWith("@y:") && !videoId.isNullOrBlank() -> "https://i.ytimg.com/vi/$videoId/${t.removePrefix("@y:")}"
            t.startsWith("http://") || t.startsWith("https://") -> t
            else -> if (!videoId.isNullOrBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
        }
    }

    fun base41Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size * 3 + 1) / 2)
        var i = 0
        while (i < data.size) {
            if (i + 1 < data.size) {
                val v = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val c0 = v % 41
                val r = v / 41
                val c1 = r % 41
                val c2 = r / 41
                sb.append(BASE41_ALPHABET[c0])
                sb.append(BASE41_ALPHABET[c1])
                sb.append(BASE41_ALPHABET[c2])
                i += 2
            } else {
                val v = data[i].toInt() and 0xFF
                val c0 = v % 41
                val c1 = v / 41
                sb.append(BASE41_ALPHABET[c0])
                sb.append(BASE41_ALPHABET[c1])
                i += 1
            }
        }
        return sb.toString()
    }

    fun base41Decode(encoded: String): ByteArray {
        require(encoded.isNotEmpty()) { "Empty encoded data" }
        require(encoded.length % 3 != 1) { "Invalid Base41 length" }

        val out = ByteArrayOutputStream((encoded.length * 2) / 3 + 1)
        var i = 0
        while (i < encoded.length) {
            val remaining = encoded.length - i
            if (remaining >= 3) {
                val c0 = decodeBase41Char(encoded[i])
                val c1 = decodeBase41Char(encoded[i + 1])
                val c2 = decodeBase41Char(encoded[i + 2])
                val v = c0 + c1 * 41 + c2 * 1681
                require(v <= 0xFFFF) { "Base41 triple overflow" }
                out.write((v ushr 8) and 0xFF)
                out.write(v and 0xFF)
                i += 3
            } else {
                val c0 = decodeBase41Char(encoded[i])
                val c1 = decodeBase41Char(encoded[i + 1])
                val v = c0 + c1 * 41
                require(v <= 0xFF) { "Base41 pair overflow" }
                out.write(v)
                i += 2
            }
        }
        return out.toByteArray()
    }

    private fun decodeBase41Char(ch: Char): Int {
        val code = ch.code
        if (code >= BASE41_REVERSE.size) error("Invalid Base41 character: $ch")
        val value = BASE41_REVERSE[code]
        if (value < 0) error("Invalid Base41 character: $ch")
        return value
    }

    private fun compressDeflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val baos = ByteArrayOutputStream()
        try {
            DeflaterOutputStream(baos, deflater).use { it.write(input) }
        } finally {
            deflater.end()
        }
        return baos.toByteArray()
    }

    private fun decompressDeflate(data: ByteArray, offset: Int, length: Int): ByteArray {
        val inflater = Inflater(true)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            inflater.setInput(data, offset, length)
            var total = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        error("Unexpected end of DEFLATE stream")
                    }
                } else {
                    total += count
                    if (total > MAX_DECOMPRESSED_BYTES) error("Playlist payload too large")
                    baos.write(buf, 0, count)
                }
            }
        } finally {
            inflater.end()
        }
        return baos.toByteArray()
    }

    private fun compressLzma2(input: ByteArray): ByteArray {
        val options = LZMA2Options(LZMA2Options.PRESET_MAX).apply {
            dictSize = LZMA2_DICT_SIZE
        }
        val baos = ByteArrayOutputStream()
        FinishableWrapperOutputStream(baos).use { wrapper ->
            options.getOutputStream(wrapper).use { lzmaOut ->
                lzmaOut.write(input)
            }
        }
        return baos.toByteArray()
    }

    private fun decompressLzma2(data: ByteArray, offset: Int, length: Int): ByteArray {
        val bais = ByteArrayInputStream(data, offset, length)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        LZMA2InputStream(bais, LZMA2_DICT_SIZE).use { lzmaIn ->
            var total = 0
            while (true) {
                val count = lzmaIn.read(buf)
                if (count < 0) break
                total += count
                if (total > MAX_DECOMPRESSED_BYTES) error("Playlist payload too large")
                baos.write(buf, 0, count)
            }
        }
        return baos.toByteArray()
    }

    private fun formatDuration(seconds: Int): String? {
        if (seconds <= 0) return null
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun com.google.gson.JsonElement?.safeString(): String =
        if (this == null || isJsonNull) "" else try { asString } catch (_: Exception) { "" }

    private fun com.google.gson.JsonElement?.safeInt(): Int =
        if (this == null || isJsonNull) 0 else try { asInt } catch (_: Exception) { 0 }

    private fun com.google.gson.JsonElement?.safeBoolean(): Boolean =
        if (this == null || isJsonNull) false else try {
            if (isJsonPrimitive && asJsonPrimitive.isNumber) asInt != 0 else asBoolean
        } catch (_: Exception) {
            false
        }

    private fun JsonObject.firstString(vararg keys: String): String {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                return get(k).safeString()
            }
        }
        return ""
    }

    private fun JsonObject.firstInt(vararg keys: String): Int {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                return get(k).safeInt()
            }
        }
        return 0
    }

    private fun JsonObject.firstBool(vararg keys: String): Boolean {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                return get(k).safeBoolean()
            }
        }
        return false
    }
}
