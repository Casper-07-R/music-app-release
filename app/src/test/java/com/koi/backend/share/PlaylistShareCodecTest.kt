package com.koi.backend.share

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.koi.backend.innertube.SongItem
import org.junit.Assert.*
import org.junit.Test

class PlaylistShareCodecTest {

    private val qrAlphanumericAllowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    private fun sampleSongs(count: Int): List<SongItem> {
        return (1..count).map { idx ->
            val vid = String.format("vid%08d", idx)
            val thumb = when (idx % 4) {
                0 -> "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                1 -> "https://lh3.googleusercontent.com/artToken_$idx=w800-h800-l90-rj"
                2 -> "https://lh3.googleusercontent.com/otherToken_$idx=w544-h544-l90-rj"
                else -> "https://i.ytimg.com/vi/$vid/maxresdefault.jpg"
            }
            SongItem(
                videoId = vid,
                title = "Track Title #$idx",
                artists = "Artist ${((idx - 1) % 7) + 1}, Feat. Guest",
                album = if (idx % 3 == 0) null else "Album ${((idx - 1) % 5) + 1}",
                durationSeconds = 180 + idx * 3,
                thumbnailUrl = thumb,
                explicit = idx % 5 == 0
            )
        }
    }

    @Test
    fun testBase41EncodeDecode_allEdgeCasesAndQrAlphanumericCompatibility() {
        for (len in listOf(1, 2, 3, 4, 5, 17, 64, 255)) {
            val raw = ByteArray(len) { ((it * 73 + 19) and 0xFF).toByte() }
            val encoded = PlaylistShareCodec.base41Encode(raw)
            assertTrue(encoded.all { it in qrAlphanumericAllowed })
            assertFalse(encoded.contains(' '))
            assertFalse(encoded.contains('%'))
            assertFalse(encoded.contains('/'))
            assertFalse(encoded.contains(':'))

            val decoded = PlaylistShareCodec.base41Decode(encoded)
            assertArrayEquals("Mismatch for len=$len", raw, decoded)
        }
    }

    @Test
    fun testEncodeAndDecode_preservesExactSongsAndLocalCoverFiltering() {
        val songs = sampleSongs(12)
        val payload = PlaylistShareCodec.encodePlaylist(
            title = "Midnight Koi Vibes",
            description = "Curated late night tracks",
            coverUrl = "/data/user/0/com.koi.ui/files/covers/local_cover.jpg",
            songs = songs
        )

        assertTrue(payload.uri.startsWith("KOI://P/"))
        assertTrue(payload.compressedBytesCount < payload.jsonBytesCount)

        val decoded = PlaylistShareCodec.decode(payload.uri).getOrThrow()
        assertEquals("Midnight Koi Vibes", decoded.title)
        assertEquals("Curated late night tracks", decoded.description)
        // Local file path should not be leaked; falls back to first song's resolved thumbnail
        assertEquals(songs.first().thumbnailUrl, decoded.coverUrl)
        assertEquals(songs.size, decoded.songs.size)

        songs.zip(decoded.songs).forEach { (expected, actual) ->
            assertEquals(expected.videoId, actual.videoId)
            assertEquals(expected.title, actual.title)
            assertEquals(expected.artists, actual.artists)
            assertEquals(expected.album, actual.album)
            assertEquals(expected.durationSeconds, actual.durationSeconds)
            assertEquals(expected.thumbnailUrl, actual.thumbnailUrl)
            assertEquals(expected.explicit, actual.explicit)
        }
    }

    @Test
    fun testDecode_acceptsUriVariantsAndRawJson() {
        val songs = sampleSongs(4)
        val payload = PlaylistShareCodec.encodePlaylist(
            title = "Variant Test",
            description = "",
            coverUrl = "https://example.com/custom_cover.png",
            songs = songs
        )

        val variants = listOf(
            payload.uri,
            "koi://p/${payload.body}",
            "KOI://P/${payload.body}",
            "koi://playlist?d=${payload.body}",
            "https://koi.music/p/${payload.body}",
            "https://koi.app/p?d=${payload.body}",
            PlaylistShareCodec.toCompactJson("Variant Test", "", "https://example.com/custom_cover.png", songs)
        )

        for (variant in variants) {
            val decoded = PlaylistShareCodec.decode(variant).getOrThrow()
            assertEquals("Variant Test", decoded.title)
            assertEquals("https://example.com/custom_cover.png", decoded.coverUrl)
            assertEquals(songs.map { it.videoId }, decoded.songs.map { it.videoId })
        }
    }

    @Test
    fun testDecode_rejectsInvalidAndCorruptPayloads() {
        val invalidInputs = listOf(
            "",
            "   ",
            "https://example.com/not-koi",
            "koi://p/",
            "koi://p/A", // invalid Base41 length (len % 3 == 1)
            "koi://p/INVALID_CHAR?",
            "koi://p/0000", // unknown header byte / invalid data
            "{}" // missing song list
        )
        for (input in invalidInputs) {
            assertTrue("Expected failure for: $input", PlaylistShareCodec.decode(input).isFailure)
        }
    }

    @Test
    fun testVersion40QrRoundTrip_smallMediumAndLargePlaylists() {
        // Small playlist -> Version 40 (177x177 + 8 quiet zone = 185) with high EC level
        val smallSongs = sampleSongs(5)
        val smallPayload = PlaylistShareCodec.encodePlaylist("Quick Mix", null, null, smallSongs)
        val smallQr = QrCodeHelper.encodeVersion40BitMatrix(smallPayload.uri)
        assertEquals(185, smallQr.bitMatrix.width)
        assertEquals(185, smallQr.bitMatrix.height)
        assertEquals(ErrorCorrectionLevel.H, smallQr.errorCorrectionLevel)
        assertEquals(smallPayload.uri, QrCodeHelper.decodeBitMatrix(smallQr.bitMatrix))

        // Large playlist (80 exact songs) -> Version 40 QR code round-trip
        val largeSongs = sampleSongs(80)
        val largePayload = PlaylistShareCodec.encodePlaylist(
            title = "Mega Roadtrip 80",
            description = "Full lossless exact list",
            coverUrl = null,
            songs = largeSongs
        )
        val largeQr = QrCodeHelper.encodeVersion40BitMatrix(largePayload.uri)
        assertEquals(185, largeQr.bitMatrix.width)
        assertEquals(185, largeQr.bitMatrix.height)

        val scannedText = QrCodeHelper.decodeBitMatrix(largeQr.bitMatrix)
        assertEquals(largePayload.uri, scannedText)

        val decodedLarge = PlaylistShareCodec.decode(scannedText).getOrThrow()
        assertEquals("Mega Roadtrip 80", decodedLarge.title)
        assertEquals("Full lossless exact list", decodedLarge.description)
        assertEquals(80, decodedLarge.songs.size)
        largeSongs.zip(decodedLarge.songs).forEach { (expected, actual) ->
            assertEquals(expected.videoId, actual.videoId)
            assertEquals(expected.title, actual.title)
            assertEquals(expected.artists, actual.artists)
            assertEquals(expected.album, actual.album)
            assertEquals(expected.durationSeconds, actual.durationSeconds)
            assertEquals(expected.thumbnailUrl, actual.thumbnailUrl)
            assertEquals(expected.explicit, actual.explicit)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEncodeEmptyPlaylist_throws() {
        PlaylistShareCodec.encodePlaylist("Empty", null, null, emptyList())
    }

    @Test
    fun testKoiQrThemes_coloredLuminanceAndCenterEmblemCutoutRemainDecodable() {
        fun luminance(argb: Int): Double {
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            return 0.299 * r + 0.587 * g + 0.114 * b
        }

        val smallSongs = sampleSongs(10)
        val largeSongs = sampleSongs(80)
        val smallPayload = PlaylistShareCodec.encodePlaylist("Theme Small", null, null, smallSongs)
        val largePayload = PlaylistShareCodec.encodePlaylist("Theme Large", null, null, largeSongs)
        val smallQr = QrCodeHelper.encodeVersion40BitMatrix(smallPayload.uri)
        val largeQr = QrCodeHelper.encodeVersion40BitMatrix(largePayload.uri)

        for (theme in KoiQrTheme.entries) {
            val bgLum = luminance(theme.qrBg)
            assertTrue("${theme.label} background must be bright", bgLum >= 245.0)
            for (darkColor in listOf(
                theme.moduleGradStart,
                theme.moduleGradMid,
                theme.moduleGradEnd,
                theme.finderOuterColor,
                theme.finderInnerPrimary,
                theme.finderInnerSecondary
            )) {
                val darkLum = luminance(darkColor)
                assertTrue(
                    "${theme.label} dark stop too bright ($darkLum)",
                    bgLum - darkLum >= 150.0
                )
            }

            // Test colored modules + center emblem cutout on both EC-H (small) and EC-L (large)
            val smallPixels = QrCodeHelper.renderPixels(
                matrix = smallQr.bitMatrix,
                modulePixels = 7,
                darkColor = theme.moduleGradStart,
                lightColor = theme.qrBg,
                centerExclusionRadiusModules = 12.5f
            )
            val smallWidth = smallQr.bitMatrix.width * 7
            assertEquals(
                smallPayload.uri,
                QrCodeHelper.decodePixels(smallPixels, smallWidth, smallWidth)
            )

            val largePixels = QrCodeHelper.renderPixels(
                matrix = largeQr.bitMatrix,
                modulePixels = 7,
                darkColor = theme.moduleGradEnd,
                lightColor = theme.qrBg,
                centerExclusionRadiusModules = 9.5f
            )
            val largeWidth = largeQr.bitMatrix.width * 7
            assertEquals(
                largePayload.uri,
                QrCodeHelper.decodePixels(largePixels, largeWidth, largeWidth)
            )
        }
    }
}
