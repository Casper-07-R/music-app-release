package com.koi.backend.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import com.koi.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.SoftReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Koi-themed visual palettes & paired high-detail Koi Pond artworks for Version 40 QR rendering.
 * Module dark/finder stops preserve strong luminance contrast against light water regions so
 * dense 177x177 Version 40 QR codes remain fast and reliable to scan even after social compression.
 */
enum class KoiQrTheme(
    val label: String,
    val artAssetPath: String,
    val cardTopColor: Int,
    val cardMidColor: Int,
    val cardBottomColor: Int,
    val accentPrimary: Int,
    val accentSecondary: Int,
    val rippleColor: Int,
    val qrBg: Int,
    val moduleGradStart: Int,
    val moduleGradMid: Int,
    val moduleGradEnd: Int,
    val finderOuterColor: Int,
    val finderInnerPrimary: Int,
    val finderInnerSecondary: Int
) {
    KOI_POND(
        label = "Koi Pond",
        artAssetPath = "qr_art/koi_pond.jpg",
        cardTopColor = 0xFF031015.toInt(),
        cardMidColor = 0xFF0A2732.toInt(),
        cardBottomColor = 0xFF061B23.toInt(),
        accentPrimary = 0xFFFF6B35.toInt(),
        accentSecondary = 0xFF00B4D8.toInt(),
        rippleColor = 0xFF154B59.toInt(),
        qrBg = 0xFFFAFEFF.toInt(),
        moduleGradStart = 0xFFB03009.toInt(),
        moduleGradMid = 0xFF0A3440.toInt(),
        moduleGradEnd = 0xFF00557A.toInt(),
        finderOuterColor = 0xFF04222E.toInt(),
        finderInnerPrimary = 0xFF8C1E04.toInt(),
        finderInnerSecondary = 0xFF003B59.toInt()
    ),
    KOHAKU_SUNSET(
        label = "Kohaku Gold",
        artAssetPath = "qr_art/kohaku_gold.jpg",
        cardTopColor = 0xFF150806.toInt(),
        cardMidColor = 0xFF2B120C.toInt(),
        cardBottomColor = 0xFF120705.toInt(),
        accentPrimary = 0xFFFF6B35.toInt(),
        accentSecondary = 0xFFFFB703.toInt(),
        rippleColor = 0xFF4D2114.toInt(),
        qrBg = 0xFFFFFCF7.toInt(),
        moduleGradStart = 0xFF991B14.toInt(),
        moduleGradMid = 0xFFB03608.toInt(),
        moduleGradEnd = 0xFF471810.toInt(),
        finderOuterColor = 0xFF2B0C05.toInt(),
        finderInnerPrimary = 0xFF8C1D04.toInt(),
        finderInnerSecondary = 0xFF5E2F00.toInt()
    ),
    LOTUS_CYAN(
        label = "Lotus Cyan",
        artAssetPath = "qr_art/lotus_cyan.jpg",
        cardTopColor = 0xFF04101A.toInt(),
        cardMidColor = 0xFF0A283A.toInt(),
        cardBottomColor = 0xFF151229.toInt(),
        accentPrimary = 0xFF00B4D8.toInt(),
        accentSecondary = 0xFFFF70A6.toInt(),
        rippleColor = 0xFF174B66.toInt(),
        qrBg = 0xFFF7FCFE.toInt(),
        moduleGradStart = 0xFF04384D.toInt(),
        moduleGradMid = 0xFF00567B.toInt(),
        moduleGradEnd = 0xFF681C54.toInt(),
        finderOuterColor = 0xFF041D2B.toInt(),
        finderInnerPrimary = 0xFF003E5E.toInt(),
        finderInnerSecondary = 0xFF6B0E3B.toInt()
    )
}

/**
 * Pure QR matrix helpers (JVM-testable) plus styled Koi QR Bitmap rendering,
 * social sharing, gallery saving, and multi-engine QR decoding.
 */
object QrCodeHelper {

    const val QR_VERSION = 40
    private const val QUIET_ZONE_MODULES = 4
    private const val QR_CODE_MODULES = 177
    private const val FULL_GRID_MODULES = QR_CODE_MODULES + QUIET_ZONE_MODULES * 2
    private const val INV_16 = 1f / 16f
    private const val MAX_CACHED_THEMES = 3

    private val TYPE_INFO_COORDS = arrayOf(
        intArrayOf(8, 0),
        intArrayOf(8, 1),
        intArrayOf(8, 2),
        intArrayOf(8, 3),
        intArrayOf(8, 4),
        intArrayOf(8, 5),
        intArrayOf(8, 7),
        intArrayOf(8, 8),
        intArrayOf(7, 8),
        intArrayOf(5, 8),
        intArrayOf(4, 8),
        intArrayOf(3, 8),
        intArrayOf(2, 8),
        intArrayOf(1, 8),
        intArrayOf(0, 8)
    )

    // Marks modules inside the 177x177 Version 40 grid that are XORed with the QR data mask pattern.
    private val V40_IS_DATA_MODULE: BooleanArray = buildV40DataModuleMask()

    // Marks the 30 format-info modules inside the 177x177 grid so mask scoring matches ZXing exactly.
    private val V40_IS_FORMAT_MODULE: BooleanArray = buildV40FormatModuleMask()

    private class PreparedThemeArt(
        val artPixels: IntArray,
        val lumsQuant: ShortArray,
        val detailQuant: ShortArray,
        val artLumGrid: FloatArray,
        val modArtNorm: FloatArray
    )

    private class ByteLuminanceSource(
        width: Int,
        height: Int,
        private val luminances: ByteArray
    ) : LuminanceSource(width, height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val w = width
            val out = if (row == null || row.size < w) ByteArray(w) else row
            System.arraycopy(luminances, y * w, out, 0, w)
            return out
        }

        override fun getMatrix(): ByteArray = luminances
    }

    private val artCacheLock = Any()
    private val preparedArtCache = object : LinkedHashMap<String, SoftReference<PreparedThemeArt>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SoftReference<PreparedThemeArt>>?): Boolean {
            return size > MAX_CACHED_THEMES
        }
    }
    @Volatile
    private var cachedAppLogo: SoftReference<Bitmap>? = null

    private fun buildV40DataModuleMask(): BooleanArray {
        val data = BooleanArray(QR_CODE_MODULES * QR_CODE_MODULES) { true }
        // Finders + separators + format info areas
        for (y in 0..8) {
            val rowOff = y * QR_CODE_MODULES
            for (x in 0..8) data[rowOff + x] = false
            for (x in (QR_CODE_MODULES - 8) until QR_CODE_MODULES) data[rowOff + x] = false
        }
        for (y in (QR_CODE_MODULES - 8) until QR_CODE_MODULES) {
            val rowOff = y * QR_CODE_MODULES
            for (x in 0..8) data[rowOff + x] = false
        }
        // Timing patterns
        for (i in 0 until QR_CODE_MODULES) {
            data[6 * QR_CODE_MODULES + i] = false
            data[i * QR_CODE_MODULES + 6] = false
        }
        // Version info (6x3 adjacent to top-right and bottom-left finders)
        for (i in 0..5) {
            for (j in 0..2) {
                data[i * QR_CODE_MODULES + (QR_CODE_MODULES - 11 + j)] = false
                data[(QR_CODE_MODULES - 11 + j) * QR_CODE_MODULES + i] = false
            }
        }
        // Alignment patterns
        val centers = Version.getVersionForNumber(QR_VERSION).alignmentPatternCenters
        for (cy in centers) {
            for (cx in centers) {
                if ((cx == 6 && cy == 6) || (cx == 6 && cy == 170) || (cx == 170 && cy == 6)) continue
                for (dy in -2..2) {
                    val rowOff = (cy + dy) * QR_CODE_MODULES
                    for (dx in -2..2) {
                        data[rowOff + (cx + dx)] = false
                    }
                }
            }
        }
        return data
    }

    private fun buildV40FormatModuleMask(): BooleanArray {
        val fmt = BooleanArray(QR_CODE_MODULES * QR_CODE_MODULES)
        for (i in TYPE_INFO_COORDS.indices) {
            val c = TYPE_INFO_COORDS[i]
            fmt[c[1] * QR_CODE_MODULES + c[0]] = true
            if (i < 8) {
                fmt[8 * QR_CODE_MODULES + (QR_CODE_MODULES - 1 - i)] = true
            } else {
                fmt[(QR_CODE_MODULES - 7 + (i - 8)) * QR_CODE_MODULES + 8] = true
            }
        }
        return fmt
    }

    private fun getDataMaskBit(maskPattern: Int, x: Int, y: Int): Boolean {
        return when (maskPattern) {
            0 -> ((y + x) and 1) == 0
            1 -> (y and 1) == 0
            2 -> x % 3 == 0
            3 -> (y + x) % 3 == 0
            4 -> (((y ushr 1) + (x / 3)) and 1) == 0
            5 -> {
                val t = y * x
                (t and 1) + (t % 3) == 0
            }
            6 -> {
                val t = y * x
                (((t and 1) + (t % 3)) and 1) == 0
            }
            7 -> (((y * x) % 3 + ((y + x) and 1)) and 1) == 0
            else -> false
        }
    }

    private fun makeFormatInfoBits(ec: ErrorCorrectionLevel, maskPattern: Int): Int {
        val raw = (ec.bits shl 3) or maskPattern
        var rem = raw shl 10
        val poly = 0x537
        for (bit in 4 downTo 0) {
            if ((rem and (1 shl (bit + 10))) != 0) {
                rem = rem xor (poly shl bit)
            }
        }
        return ((raw shl 10) or rem) xor 0x5412
    }

    data class Version40MatrixResult(
        val bitMatrix: BitMatrix,
        val errorCorrectionLevel: ErrorCorrectionLevel
    )

    sealed class QrGenerationResult {
        data class Success(
            val payload: PlaylistShareCodec.EncodedPlaylistPayload,
            val errorCorrectionLevel: ErrorCorrectionLevel,
            val matrixSize: Int,
            val theme: KoiQrTheme,
            val qrBitmap: Bitmap,
            val shareCardBitmap: Bitmap
        ) : QrGenerationResult()

        data class TooLarge(
            val songCount: Int,
            val encodedChars: Int,
            val maxChars: Int
        ) : QrGenerationResult()

        data class Error(val message: String) : QrGenerationResult()
    }

    /**
     * Generates a QR Code Version 40 [BitMatrix] using the highest error correction level
     * (`H` -> `Q` -> `M` -> `L`) that fits the compressed payload.
     * When [artLuminanceGrid] is provided, evaluates all 8 QR mask patterns (`0..7`) at the
     * selected EC level in a single pass over the encoded matrix and picks the mask whose
     * modules align best with dark/light artwork regions.
     * Note: We intentionally omit `QR_COMPACT` because ZXing's `MinimalEncoder` ignores
     * `QR_VERSION`; since `KOI://P/<BASE41>` is 100% QR Alphanumeric, standard `Encoder`
     * automatically selects dense Alphanumeric mode AND strictly honors `QR_VERSION = 40`.
     */
    fun encodeVersion40BitMatrix(
        content: String,
        artLuminanceGrid: FloatArray? = null,
        gridSize: Int = 0
    ): Version40MatrixResult {
        val ecLevels = listOf(
            ErrorCorrectionLevel.H,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.L
        )
        val hasArtGrid = artLuminanceGrid != null && gridSize == FULL_GRID_MODULES
        val hints = if (hasArtGrid) {
            mapOf(
                EncodeHintType.QR_VERSION to QR_VERSION,
                EncodeHintType.QR_MASK_PATTERN to 0
            )
        } else {
            mapOf(EncodeHintType.QR_VERSION to QR_VERSION)
        }

        var lastException: Exception? = null
        for (ec in ecLevels) {
            try {
                val qrCode = Encoder.encode(content, ec, hints)
                if (!hasArtGrid) {
                    val bitMatrix = byteMatrixToBitMatrix(qrCode, maskFromZero = 0, ec = ec, applyMaskFlip = false)
                    return Version40MatrixResult(bitMatrix, ec)
                }
                val bestMask = selectBestArtMask(qrCode, ec, artLuminanceGrid!!)
                val bitMatrix = byteMatrixToBitMatrix(qrCode, maskFromZero = bestMask, ec = ec, applyMaskFlip = bestMask != 0)
                return Version40MatrixResult(bitMatrix, ec)
            } catch (e: WriterException) {
                lastException = e
            }
        }
        throw lastException ?: WriterException("Payload exceeds QR Version 40 capacity")
    }

    private fun selectBestArtMask(
        qr0: QRCode,
        ec: ErrorCorrectionLevel,
        artLumGrid: FloatArray
    ): Int {
        val bytes = qr0.matrix.array
        val scores = DoubleArray(8)
        for (y in 0 until QR_CODE_MODULES) {
            val rowBytes = bytes[y]
            val gridRow = (y + QUIET_ZONE_MODULES) * FULL_GRID_MODULES + QUIET_ZONE_MODULES
            val dataRow = y * QR_CODE_MODULES
            for (x in 0 until QR_CODE_MODULES) {
                val cellIdx = dataRow + x
                if (V40_IS_FORMAT_MODULE[cellIdx]) continue
                val delta = 140.0 - artLumGrid[gridRow + x]
                val b0 = rowBytes[x].toInt() == 1
                if (!V40_IS_DATA_MODULE[cellIdx]) {
                    val d = if (b0) delta else -delta
                    for (m in 0..7) scores[m] += d
                } else {
                    val rawBit = b0 xor (((y + x) and 1) == 0)
                    scores[0] += if (b0) delta else -delta
                    for (m in 1..7) {
                        val bm = rawBit xor getDataMaskBit(m, x, y)
                        scores[m] += if (bm) delta else -delta
                    }
                }
            }
        }

        // Include the 30 format-info modules so scores match full-matrix evaluation identically
        for (m in 0..7) {
            val fmtBits = makeFormatInfoBits(ec, m)
            var fmtScore = 0.0
            for (i in TYPE_INFO_COORDS.indices) {
                val bit = ((fmtBits ushr i) and 1) == 1
                val c = TYPE_INFO_COORDS[i]
                val d1 = 140.0 - artLumGrid[(c[1] + QUIET_ZONE_MODULES) * FULL_GRID_MODULES + (c[0] + QUIET_ZONE_MODULES)]
                fmtScore += if (bit) d1 else -d1

                val mx: Int
                val my: Int
                if (i < 8) {
                    mx = QR_CODE_MODULES - 1 - i
                    my = 8
                } else {
                    mx = 8
                    my = QR_CODE_MODULES - 7 + (i - 8)
                }
                val d2 = 140.0 - artLumGrid[(my + QUIET_ZONE_MODULES) * FULL_GRID_MODULES + (mx + QUIET_ZONE_MODULES)]
                fmtScore += if (bit) d2 else -d2
            }
            scores[m] += fmtScore
        }

        var bestMask = 0
        for (m in 1..7) {
            if (scores[m] > scores[bestMask]) bestMask = m
        }
        return bestMask
    }

    private fun byteMatrixToBitMatrix(
        qrCode: QRCode,
        maskFromZero: Int,
        ec: ErrorCorrectionLevel,
        applyMaskFlip: Boolean
    ): BitMatrix {
        val bytes = qrCode.matrix.array
        val out = BitMatrix(FULL_GRID_MODULES, FULL_GRID_MODULES)
        if (!applyMaskFlip) {
            for (y in 0 until QR_CODE_MODULES) {
                val row = bytes[y]
                val oy = y + QUIET_ZONE_MODULES
                for (x in 0 until QR_CODE_MODULES) {
                    if (row[x].toInt() == 1) {
                        out.set(x + QUIET_ZONE_MODULES, oy)
                    }
                }
            }
            return out
        }

        for (y in 0 until QR_CODE_MODULES) {
            val row = bytes[y]
            val oy = y + QUIET_ZONE_MODULES
            val dataRow = y * QR_CODE_MODULES
            for (x in 0 until QR_CODE_MODULES) {
                val cellIdx = dataRow + x
                val b0 = row[x].toInt() == 1
                val bit = if (V40_IS_DATA_MODULE[cellIdx]) {
                    b0 xor (((y + x) and 1) == 0) xor getDataMaskBit(maskFromZero, x, y)
                } else {
                    b0
                }
                if (bit) {
                    out.set(x + QUIET_ZONE_MODULES, oy)
                }
            }
        }

        // Overwrite the 30 format-info modules for (ec, maskFromZero)
        val fmtBits = makeFormatInfoBits(ec, maskFromZero)
        for (i in TYPE_INFO_COORDS.indices) {
            val bit = ((fmtBits ushr i) and 1) == 1
            val c = TYPE_INFO_COORDS[i]
            setOrClearBit(out, c[0] + QUIET_ZONE_MODULES, c[1] + QUIET_ZONE_MODULES, bit)
            if (i < 8) {
                setOrClearBit(out, QR_CODE_MODULES - 1 - i + QUIET_ZONE_MODULES, 8 + QUIET_ZONE_MODULES, bit)
            } else {
                setOrClearBit(out, 8 + QUIET_ZONE_MODULES, QR_CODE_MODULES - 7 + (i - 8) + QUIET_ZONE_MODULES, bit)
            }
        }
        return out
    }

    private fun setOrClearBit(matrix: BitMatrix, x: Int, y: Int, value: Boolean) {
        if (value) {
            matrix.set(x, y)
        } else if (matrix.get(x, y)) {
            matrix.flip(x, y)
        }
    }

    /**
     * Decodes a [BitMatrix] through ZXing's real binarizer + detector pipeline
     * at crisp module scaling (also usable directly in pure JVM unit tests).
     */
    fun decodeBitMatrix(matrix: BitMatrix, modulePixels: Int = 6): String {
        val bw = matrix.width * modulePixels
        val bh = matrix.height * modulePixels
        val pixels = renderPixels(matrix, modulePixels)
        return decodePixels(pixels, bw, bh)
            ?: throw com.google.zxing.NotFoundException.getNotFoundInstance()
    }

    /**
     * Encodes the playlist and renders a full-color artistic Koi Version 40 QR bitmap
     * (without any obstructing center symbol) plus a `1620 x 1800` social share card bitmap,
     * self-verifying scannability automatically.
     */
    suspend fun generatePlaylistQr(
        playlistTitle: String,
        playlistDescription: String?,
        coverUrl: String?,
        songs: List<com.koi.backend.innertube.SongItem>,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND,
        context: Context? = null,
        preEncodedPayload: PlaylistShareCodec.EncodedPlaylistPayload? = null
    ): QrGenerationResult = withContext(Dispatchers.Default) {
        if (songs.isEmpty()) {
            return@withContext QrGenerationResult.Error("Add songs to this playlist before sharing")
        }

        val modulePixels = 8
        val fullSizePx = FULL_GRID_MODULES * modulePixels // 185 * 8 = 1480

        val payload: PlaylistShareCodec.EncodedPlaylistPayload
        val preparedArt: PreparedThemeArt?
        try {
            if (preEncodedPayload != null) {
                payload = preEncodedPayload
                preparedArt = context?.let { getOrPrepareThemeArt(it, theme.artAssetPath, fullSizePx, modulePixels) }
            } else if (context != null) {
                val (encoded, art) = coroutineScope {
                    val payloadJob = async(Dispatchers.Default) {
                        PlaylistShareCodec.encodePlaylist(playlistTitle, playlistDescription, coverUrl, songs)
                    }
                    val artJob = async(Dispatchers.IO) {
                        getOrPrepareThemeArt(context, theme.artAssetPath, fullSizePx, modulePixels)
                    }
                    payloadJob.await() to artJob.await()
                }
                payload = encoded
                preparedArt = art
            } else {
                payload = PlaylistShareCodec.encodePlaylist(playlistTitle, playlistDescription, coverUrl, songs)
                preparedArt = null
            }
        } catch (e: Exception) {
            return@withContext QrGenerationResult.Error(e.message ?: "Failed to encode playlist")
        }

        val matrixResult = try {
            encodeVersion40BitMatrix(
                content = payload.uri,
                artLuminanceGrid = preparedArt?.artLumGrid,
                gridSize = FULL_GRID_MODULES
            )
        } catch (_: WriterException) {
            return@withContext QrGenerationResult.TooLarge(
                songCount = songs.size,
                encodedChars = payload.uri.length,
                maxChars = PlaylistShareCodec.MAX_QR_V40_TOTAL_CHARS
            )
        }

        var styledQrBitmap: Bitmap
        var verifyPixels: IntArray
        if (preparedArt != null) {
            val rendered = renderPreparedArtQrBitmapParallel(
                matrix = matrixResult.bitMatrix,
                preparedArt = preparedArt,
                theme = theme,
                modulePixels = modulePixels,
                coreHalfRatio = 0.60f,
                outerShiftWeight = 0.30f,
                darkCoreMaxLum = 24f,
                lightCoreMinLum = 216f
            )
            styledQrBitmap = rendered.first
            verifyPixels = rendered.second
        } else {
            val rendered = renderStyledQrBitmapWithPixels(
                matrix = matrixResult.bitMatrix,
                theme = theme,
                modulePixels = modulePixels
            )
            styledQrBitmap = rendered.first
            verifyPixels = rendered.second
        }

        // Self-verify scannability; if a tough EC-L pattern needs stronger dot definition,
        // automatically retry once with firmer core dots, then fallback cleanly if still needed.
        if (!verifyPixelsDecodable(verifyPixels, fullSizePx, payload.uri)) {
            if (preparedArt != null) {
                styledQrBitmap.recycle()
                val retried = renderPreparedArtQrBitmapParallel(
                    matrix = matrixResult.bitMatrix,
                    preparedArt = preparedArt,
                    theme = theme,
                    modulePixels = modulePixels,
                    coreHalfRatio = 0.70f,
                    outerShiftWeight = 0.56f,
                    darkCoreMaxLum = 20f,
                    lightCoreMinLum = 222f
                )
                styledQrBitmap = retried.first
                verifyPixels = retried.second
                if (!verifyPixelsDecodable(verifyPixels, fullSizePx, payload.uri)) {
                    styledQrBitmap.recycle()
                    val fallback = renderStyledQrBitmapWithPixels(
                        matrix = matrixResult.bitMatrix,
                        theme = theme,
                        modulePixels = modulePixels
                    )
                    styledQrBitmap = fallback.first
                }
            }
        }

        val shareCardBitmap = renderShareCardBitmap(
            styledQrBitmap = styledQrBitmap,
            theme = theme,
            title = playlistTitle,
            songCount = songs.size,
            context = context
        )

        QrGenerationResult.Success(
            payload = payload,
            errorCorrectionLevel = matrixResult.errorCorrectionLevel,
            matrixSize = matrixResult.bitMatrix.width,
            theme = theme,
            qrBitmap = styledQrBitmap,
            shareCardBitmap = shareCardBitmap
        )
    }

    private fun verifyPixelsDecodable(pixels: IntArray, sizePx: Int, expectedText: String): Boolean {
        if (verifySubsampledHybrid(pixels, sizePx, expectedText)) {
            return true
        }
        return try {
            decodePixels(pixels, sizePx, sizePx) == expectedText
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySubsampledHybrid(pixels: IntArray, sizePx: Int, expectedText: String): Boolean {
        if (sizePx < 370) return false
        return try {
            val subSize = sizePx / 2
            val subLum = ByteArray(subSize * subSize)
            for (sy in 0 until subSize) {
                val srcRow = (sy * 2 + 1) * sizePx
                val dstRow = sy * subSize
                for (sx in 0 until subSize) {
                    val argb = pixels[srcRow + sx * 2 + 1]
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    subLum[dstRow + sx] = ((r + (g shl 1) + b) ushr 2).toByte()
                }
            }
            val reader = QRCodeReader()
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
            val decoded = reader.decode(
                BinaryBitmap(HybridBinarizer(ByteLuminanceSource(subSize, subSize, subLum))),
                hints
            ).text
            decoded == expectedText
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrPrepareThemeArt(
        context: Context,
        assetPath: String,
        targetSize: Int,
        modulePixels: Int
    ): PreparedThemeArt? {
        val cacheKey = "$assetPath@$targetSize"
        synchronized(artCacheLock) {
            preparedArtCache[cacheKey]?.get()?.let { return it }
        }

        val artPixels = loadScaledArtPixels(context, assetPath, targetSize) ?: return null
        val total = targetSize * targetSize
        val lumsQuant = ShortArray(total)
        val detailQuant = ShortArray(total)
        computeLumsAndDetailQuant(artPixels, targetSize, lumsQuant, detailQuant)
        val artLumGrid = computeModuleLuminanceGridFromQuant(lumsQuant, targetSize, FULL_GRID_MODULES, modulePixels)
        val modArtNorm = computeModArtNorm(lumsQuant, targetSize, FULL_GRID_MODULES, modulePixels)

        val prepared = PreparedThemeArt(
            artPixels = artPixels,
            lumsQuant = lumsQuant,
            detailQuant = detailQuant,
            artLumGrid = artLumGrid,
            modArtNorm = modArtNorm
        )
        synchronized(artCacheLock) {
            preparedArtCache[cacheKey] = SoftReference(prepared)
        }
        return prepared
    }

    private fun loadScaledArtPixels(context: Context, assetPath: String, targetSize: Int): IntArray? {
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return null
            val scaled = if (decoded.width == targetSize && decoded.height == targetSize) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, targetSize, targetSize, true).also {
                    decoded.recycle()
                }
            }
            val pixels = IntArray(targetSize * targetSize)
            scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
            scaled.recycle()
            pixels
        }.getOrNull()
    }

    internal fun computeModuleLuminanceGrid(
        artPixels: IntArray,
        imageSize: Int,
        gridSize: Int,
        modulePixels: Int
    ): FloatArray {
        val grid = FloatArray(gridSize * gridSize)
        val halfMod = modulePixels / 2
        for (my in 0 until gridSize) {
            val py = (my * modulePixels + halfMod).coerceAtMost(imageSize - 1)
            val rowOff = my * gridSize
            val imgRow = py * imageSize
            for (mx in 0 until gridSize) {
                val px = (mx * modulePixels + halfMod).coerceAtMost(imageSize - 1)
                grid[rowOff + mx] = rgbLuminance(artPixels[imgRow + px])
            }
        }
        return grid
    }

    private fun computeModuleLuminanceGridFromQuant(
        lumsQuant: ShortArray,
        imageSize: Int,
        gridSize: Int,
        modulePixels: Int
    ): FloatArray {
        val grid = FloatArray(gridSize * gridSize)
        val halfMod = modulePixels / 2
        for (my in 0 until gridSize) {
            val py = (my * modulePixels + halfMod).coerceAtMost(imageSize - 1)
            val rowOff = my * gridSize
            val imgRow = py * imageSize
            for (mx in 0 until gridSize) {
                val px = (mx * modulePixels + halfMod).coerceAtMost(imageSize - 1)
                grid[rowOff + mx] = lumsQuant[imgRow + px].toInt() * INV_16
            }
        }
        return grid
    }

    private fun computeModArtNorm(
        lumsQuant: ShortArray,
        imageSize: Int,
        gridSize: Int,
        modulePixels: Int
    ): FloatArray {
        val out = FloatArray(gridSize * gridSize)
        val normScale = INV_16 / (modulePixels * modulePixels * 255f)
        for (my in 0 until gridSize) {
            val baseY = my * modulePixels
            val rowOff = my * gridSize
            for (mx in 0 until gridSize) {
                var sum = 0
                val baseX = mx * modulePixels
                for (py in 0 until modulePixels) {
                    val imgRow = (baseY + py) * imageSize + baseX
                    for (px in 0 until modulePixels) {
                        sum += lumsQuant[imgRow + px].toInt()
                    }
                }
                out[rowOff + mx] = sum * normScale
            }
        }
        return out
    }

    private fun rgbLuminance(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun shiftLuminance(
        r: Int,
        g: Int,
        b: Int,
        l: Float,
        targetLum: Float,
        satBoost: Float,
        weight: Float = 1f
    ): Int {
        if (l < 1f) {
            if (weight >= 1f) {
                val v = (targetLum + 0.5f).toInt().coerceIn(0, 255)
                return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            val inv = 1f - weight
            val ri = (r * inv + targetLum * weight + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * inv + targetLum * weight + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * inv + targetLum * weight + 0.5f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val nr = l + (r - l) * satBoost
        val ng = l + (g - l) * satBoost
        val nb = l + (b - l) * satBoost

        val outR: Float
        val outG: Float
        val outB: Float
        if (targetLum <= l) {
            val scale = max(0f, targetLum / l)
            outR = nr * scale
            outG = ng * scale
            outB = nb * scale
        } else {
            val t = (targetLum - l) / max(1f, 255f - l)
            outR = nr + (255f - nr) * t
            outG = ng + (255f - ng) * t
            outB = nb + (255f - nb) * t
        }

        if (weight < 1f) {
            val inv = 1f - weight
            val ri = (r * inv + outR * weight + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * inv + outG * weight + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * inv + outB * weight + 0.5f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        var ri = (outR + 0.5f).toInt().coerceIn(0, 255)
        var gi = (outG + 0.5f).toInt().coerceIn(0, 255)
        var bi = (outB + 0.5f).toInt().coerceIn(0, 255)
        val actualL = 0.299f * ri + 0.587f * gi + 0.114f * bi
        val diff = (targetLum - actualL).roundToInt()
        ri = (ri + diff).coerceIn(0, 255)
        gi = (gi + diff).coerceIn(0, 255)
        bi = (bi + diff).coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun lerpArgb(c1: Int, c2: Int, t: Float): Int {
        val inv = 1f - t
        val r = (((c1 ushr 16) and 0xFF) * inv + ((c2 ushr 16) and 0xFF) * t + 0.5f).toInt().coerceIn(0, 255)
        val g = (((c1 ushr 8) and 0xFF) * inv + ((c2 ushr 8) and 0xFF) * t + 0.5f).toInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) * inv + (c2 and 0xFF) * t + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun fillHorizRowSum(lums: FloatArray, rowOff: Int, size: Int, outRow: FloatArray) {
        var s = lums[rowOff] * 4f + lums[rowOff + 1] + lums[rowOff + 2] + lums[rowOff + 3]
        outRow[0] = s
        s += lums[rowOff + 4] - lums[rowOff]
        outRow[1] = s
        s += lums[rowOff + 5] - lums[rowOff]
        outRow[2] = s
        s += lums[rowOff + 6] - lums[rowOff]
        outRow[3] = s
        for (x in 4 until size - 3) {
            s += lums[rowOff + x + 3] - lums[rowOff + x - 4]
            outRow[x] = s
        }
        val last = lums[rowOff + size - 1]
        for (x in size - 3 until size) {
            s += last - lums[rowOff + x - 4]
            outRow[x] = s
        }
    }

    internal fun computeHighFrequencyDetail(artPixels: IntArray, size: Int): FloatArray {
        val total = size * size
        val lums = FloatArray(total)
        for (i in 0 until total) {
            lums[i] = rgbLuminance(artPixels[i])
        }
        val detail = FloatArray(total)
        computeHighFrequencyDetailFromLums(lums, size, detail)
        return detail
    }

    private fun computeHighFrequencyDetailFromLums(lums: FloatArray, size: Int, detailOut: FloatArray) {
        if (size < 7) {
            val rad = 3
            val span = 7f
            val horiz = FloatArray(size * size)
            for (y in 0 until size) {
                val rowOff = y * size
                for (x in 0 until size) {
                    var sum = 0f
                    for (dx in -rad..rad) sum += lums[rowOff + (x + dx).coerceIn(0, size - 1)]
                    horiz[rowOff + x] = sum / span
                }
            }
            for (y in 0 until size) {
                val rowOff = y * size
                for (x in 0 until size) {
                    var sum = 0f
                    for (dy in -rad..rad) sum += horiz[(y + dy).coerceIn(0, size - 1) * size + x]
                    detailOut[rowOff + x] = lums[rowOff + x] - (sum / span)
                }
            }
            return
        }

        // Cache-local O(1)-per-pixel separable 7x7 box blur using an 8-row ring buffer
        val ring = Array(8) { FloatArray(size) }
        val colSum = FloatArray(size)
        fillHorizRowSum(lums, 0, size, ring[0])
        val r0 = ring[0]
        for (x in 0 until size) colSum[x] = r0[x] * 4f
        for (y in 1..3) {
            val ry = ring[y]
            fillHorizRowSum(lums, y * size, size, ry)
            for (x in 0 until size) colSum[x] += ry[x]
        }
        val inv49 = 1f / 49f
        for (x in 0 until size) {
            detailOut[x] = lums[x] - colSum[x] * inv49
        }
        for (outY in 1 until size) {
            val subY = (outY - 4).coerceAtLeast(0)
            val addY = (outY + 3).coerceAtMost(size - 1)
            val subRow = ring[subY and 7]
            val addRow = if (outY + 3 < size) {
                val r = ring[addY and 7]
                fillHorizRowSum(lums, addY * size, size, r)
                r
            } else {
                ring[(size - 1) and 7]
            }
            val rowOff = outY * size
            for (x in 0 until size) {
                val cs = colSum[x] + addRow[x] - subRow[x]
                colSum[x] = cs
                detailOut[rowOff + x] = lums[rowOff + x] - cs * inv49
            }
        }
    }

    private fun computeLumsAndDetailQuant(
        artPixels: IntArray,
        size: Int,
        lumsQuantOut: ShortArray,
        detailQuantOut: ShortArray
    ) {
        val total = size * size
        val lums = FloatArray(total)
        for (i in 0 until total) {
            val lum = rgbLuminance(artPixels[i])
            lums[i] = lum
            lumsQuantOut[i] = (lum * 16f + 0.5f).toInt().coerceIn(0, 4080).toShort()
        }
        val detail = FloatArray(total)
        computeHighFrequencyDetailFromLums(lums, size, detail)
        for (i in 0 until total) {
            detailQuantOut[i] = (detail[i] * 16f).roundToInt().coerceIn(-4080, 4080).toShort()
        }
    }

    private fun buildCoreMaskTable(modulePixels: Int, coreHalfRatio: Float): FloatArray {
        val edgeSoft = 0.16f
        val coreLowEdge = coreHalfRatio - edgeSoft * 0.5f
        val coreHighEdge = coreHalfRatio + edgeSoft * 0.5f
        val table = FloatArray(modulePixels * modulePixels)
        for (py in 0 until modulePixels) {
            val ny = ((py + 0.5f) / modulePixels - 0.5f) * 2f
            val any = abs(ny)
            val ny2 = ny * ny
            val rowOff = py * modulePixels
            for (px in 0 until modulePixels) {
                val nx = ((px + 0.5f) / modulePixels - 0.5f) * 2f
                val anx = abs(nx)
                val sqDist = max(anx, any) * 0.68f + sqrt(nx * nx + ny2) * 0.32f
                table[rowOff + px] = when {
                    sqDist <= coreLowEdge -> 1f
                    sqDist >= coreHighEdge -> 0f
                    else -> 1f - (sqDist - coreLowEdge) / edgeSoft
                }
            }
        }
        return table
    }

    private fun fuseArtRows(
        startMy: Int,
        endMy: Int,
        mw: Int,
        modulePixels: Int,
        size: Int,
        margin: Int,
        dataEnd: Int,
        matrix: BitMatrix,
        artPixels: IntArray,
        lumsQuant: ShortArray,
        detailQuant: ShortArray,
        modArtNormArr: FloatArray,
        coreMaskTable: FloatArray,
        outPixels: IntArray,
        outerShiftWeight: Float,
        darkCoreMaxLum: Float,
        lightCoreMinLum: Float
    ) {
        val outerDarkFactor = 1f - 0.65f * outerShiftWeight
        val outerLightDrop = (1f - outerShiftWeight) * 65f

        for (my in startMy until endMy) {
            val myQuiet = my < margin || my >= dataEnd
            val myTopF = my in margin until (margin + 7)
            val myBotF = my in (dataEnd - 7) until dataEnd
            val myTopS = my in margin..(margin + 7)
            val myBotS = my in (dataEnd - 8) until dataEnd
            val baseY = my * modulePixels
            val modRow = my * mw

            for (mx in 0 until mw) {
                val isQuiet = myQuiet || mx < margin || mx >= dataEnd
                val inTL = myTopF && mx in margin until (margin + 7)
                val inTR = myTopF && mx in (dataEnd - 7) until dataEnd
                val inBL = myBotF && mx in margin until (margin + 7)
                val isFinder = inTL || inTR || inBL
                val isFinderSep = !isFinder && (
                    (myTopS && mx in margin..(margin + 7)) ||
                        (myTopS && mx in (dataEnd - 8) until dataEnd) ||
                        (myBotS && mx in margin..(margin + 7))
                    )

                val modArtNorm = modArtNormArr[modRow + mx]
                val baseX = mx * modulePixels

                if (isQuiet || isFinder || isFinderSep) {
                    val finderBit = isFinder && matrix.get(mx, my)
                    val minLight = if (isFinder || isFinderSep) 198f else 170f
                    val lightFloor = minLight + modArtNorm * 18f
                    val blend = if (isFinderSep) 0.88f else 0.78f
                    for (py in 0 until modulePixels) {
                        val rowOff = (baseY + py) * size + baseX
                        for (px in 0 until modulePixels) {
                            val idx = rowOff + px
                            val baseRgb = artPixels[idx]
                            val r = (baseRgb ushr 16) and 0xFF
                            val g = (baseRgb ushr 8) and 0xFF
                            val b = baseRgb and 0xFF
                            val baseL = lumsQuant[idx].toInt() * INV_16
                            if (finderBit) {
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, 18f, 1.4f)
                            } else {
                                val detail = detailQuant[idx].toInt() * INV_16
                                val targetL = (max(baseL, lightFloor) + detail * 0.24f).coerceIn(0f, 244f)
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, targetL, 1.15f, blend)
                            }
                        }
                    }
                } else if (matrix.get(mx, my)) {
                    val darkBase = 12f + modArtNorm * (darkCoreMaxLum - 12f)
                    val darkMax = darkCoreMaxLum + 8f
                    for (py in 0 until modulePixels) {
                        val rowOff = (baseY + py) * size + baseX
                        val maskOff = py * modulePixels
                        for (px in 0 until modulePixels) {
                            val coreMask = coreMaskTable[maskOff + px]
                            val idx = rowOff + px
                            val baseRgb = artPixels[idx]
                            val r = (baseRgb ushr 16) and 0xFF
                            val g = (baseRgb ushr 8) and 0xFF
                            val b = baseRgb and 0xFF
                            val baseL = lumsQuant[idx].toInt() * INV_16
                            val detail = detailQuant[idx].toInt() * INV_16
                            val targetDarkL = (darkBase + detail * 0.25f).coerceIn(6f, darkMax)
                            if (coreMask >= 1f) {
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, targetDarkL, 1.50f)
                            } else if (coreMask <= 0f) {
                                val outerDarkL = min(baseL, max(targetDarkL + 38f, baseL * outerDarkFactor))
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, outerDarkL, 1.25f, outerShiftWeight)
                            } else {
                                val coreRgb = shiftLuminance(r, g, b, baseL, targetDarkL, 1.50f)
                                val outerDarkL = min(baseL, max(targetDarkL + 38f, baseL * outerDarkFactor))
                                val outerRgb = shiftLuminance(r, g, b, baseL, outerDarkL, 1.25f, outerShiftWeight)
                                outPixels[idx] = lerpArgb(outerRgb, coreRgb, coreMask)
                            }
                        }
                    }
                } else {
                    val lightOffset = modArtNorm * 12f
                    for (py in 0 until modulePixels) {
                        val rowOff = (baseY + py) * size + baseX
                        val maskOff = py * modulePixels
                        for (px in 0 until modulePixels) {
                            val coreMask = coreMaskTable[maskOff + px]
                            val idx = rowOff + px
                            val baseRgb = artPixels[idx]
                            val r = (baseRgb ushr 16) and 0xFF
                            val g = (baseRgb ushr 8) and 0xFF
                            val b = baseRgb and 0xFF
                            val baseL = lumsQuant[idx].toInt() * INV_16
                            val detail = detailQuant[idx].toInt() * INV_16
                            val targetLightL = (max(lightCoreMinLum, baseL) + lightOffset + detail * 0.25f)
                                .coerceAtMost(250f)
                            if (coreMask >= 1f) {
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, targetLightL, 1.12f)
                            } else if (coreMask <= 0f) {
                                val outerLightL = max(baseL, targetLightL - outerLightDrop)
                                outPixels[idx] = shiftLuminance(r, g, b, baseL, outerLightL, 1.10f, outerShiftWeight)
                            } else {
                                val coreRgb = shiftLuminance(r, g, b, baseL, targetLightL, 1.12f)
                                val outerLightL = max(baseL, targetLightL - outerLightDrop)
                                val outerRgb = shiftLuminance(r, g, b, baseL, outerLightL, 1.10f, outerShiftWeight)
                                outPixels[idx] = lerpArgb(outerRgb, coreRgb, coreMask)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Pure JVM-testable full-color Art-QR pixel synthesis: preserves full-resolution Koi artwork
     * hues and sharp scale/fin details across the entire square canvas (including a fully colored
     * quiet zone), while encoding QR module bits via high-contrast luminance-preserving cores.
     */
    internal fun fuseArtIntoQrPixels(
        matrix: BitMatrix,
        artPixels: IntArray,
        modulePixels: Int = 8,
        coreHalfRatio: Float = 0.60f,
        outerShiftWeight: Float = 0.30f,
        darkCoreMaxLum: Float = 24f,
        lightCoreMinLum: Float = 216f
    ): IntArray {
        val mw = matrix.width
        val size = mw * modulePixels
        require(artPixels.size == size * size) { "Expected ${size * size} art pixels, got ${artPixels.size}" }

        val total = size * size
        val lumsQuant = ShortArray(total)
        val detailQuant = ShortArray(total)
        computeLumsAndDetailQuant(artPixels, size, lumsQuant, detailQuant)
        val modArtNorm = computeModArtNorm(lumsQuant, size, mw, modulePixels)
        val coreMaskTable = buildCoreMaskTable(modulePixels, coreHalfRatio)
        val outPixels = IntArray(total)
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES

        fuseArtRows(
            startMy = 0,
            endMy = mw,
            mw = mw,
            modulePixels = modulePixels,
            size = size,
            margin = margin,
            dataEnd = dataEnd,
            matrix = matrix,
            artPixels = artPixels,
            lumsQuant = lumsQuant,
            detailQuant = detailQuant,
            modArtNormArr = modArtNorm,
            coreMaskTable = coreMaskTable,
            outPixels = outPixels,
            outerShiftWeight = outerShiftWeight,
            darkCoreMaxLum = darkCoreMaxLum,
            lightCoreMinLum = lightCoreMinLum
        )
        return outPixels
    }

    private suspend fun renderPreparedArtQrBitmapParallel(
        matrix: BitMatrix,
        preparedArt: PreparedThemeArt,
        theme: KoiQrTheme,
        modulePixels: Int,
        coreHalfRatio: Float,
        outerShiftWeight: Float,
        darkCoreMaxLum: Float,
        lightCoreMinLum: Float
    ): Pair<Bitmap, IntArray> = coroutineScope {
        val mw = matrix.width
        val sizePx = mw * modulePixels
        val outPixels = IntArray(sizePx * sizePx)
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        val coreMaskTable = buildCoreMaskTable(modulePixels, coreHalfRatio)
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        (0 until workers).map { chunk ->
            val startMy = (mw * chunk) / workers
            val endMy = (mw * (chunk + 1)) / workers
            async(Dispatchers.Default) {
                fuseArtRows(
                    startMy = startMy,
                    endMy = endMy,
                    mw = mw,
                    modulePixels = modulePixels,
                    size = sizePx,
                    margin = margin,
                    dataEnd = dataEnd,
                    matrix = matrix,
                    artPixels = preparedArt.artPixels,
                    lumsQuant = preparedArt.lumsQuant,
                    detailQuant = preparedArt.detailQuant,
                    modArtNormArr = preparedArt.modArtNorm,
                    coreMaskTable = coreMaskTable,
                    outPixels = outPixels,
                    outerShiftWeight = outerShiftWeight,
                    darkCoreMaxLum = darkCoreMaxLum,
                    lightCoreMinLum = lightCoreMinLum
                )
            }
        }.awaitAll()

        val bitmap = finalizeArtQrBitmap(outPixels, sizePx, margin, dataEnd, modulePixels, theme)
        bitmap to outPixels
    }

    private fun finalizeArtQrBitmap(
        outPixels: IntArray,
        sizePx: Int,
        margin: Int,
        dataEnd: Int,
        modulePixels: Int,
        theme: KoiQrTheme
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(outPixels, 0, sizePx, 0, 0, sizePx, sizePx)

        val canvas = Canvas(bitmap)
        val modF = modulePixels.toFloat()

        // Clean high-contrast rounded Koi Finder Eyes over luminous colored water
        drawFinderEye(canvas, margin * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        drawFinderEye(canvas, (dataEnd - 7) * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerSecondary)
        drawFinderEye(canvas, margin * modF, (dataEnd - 7) * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)

        // Sync only the 3 small finder eye regions back into outPixels so verification stays exact without copying 2.2M pixels
        copyFinderPatchesToPixels(bitmap, outPixels, sizePx, margin, dataEnd, modulePixels)
        return bitmap
    }

    private fun copyFinderPatchesToPixels(
        bitmap: Bitmap,
        pixels: IntArray,
        sizePx: Int,
        margin: Int,
        dataEnd: Int,
        modulePixels: Int
    ) {
        val fSize = 7 * modulePixels
        val coords = arrayOf(
            intArrayOf(margin * modulePixels, margin * modulePixels),
            intArrayOf((dataEnd - 7) * modulePixels, margin * modulePixels),
            intArrayOf(margin * modulePixels, (dataEnd - 7) * modulePixels)
        )
        for (c in coords) {
            val x = (c[0] - 1).coerceAtLeast(0)
            val y = (c[1] - 1).coerceAtLeast(0)
            val w = (fSize + 2).coerceAtMost(sizePx - x)
            val h = (fSize + 2).coerceAtMost(sizePx - y)
            if (w > 0 && h > 0) {
                bitmap.getPixels(pixels, y * sizePx + x, sizePx, x, y, w, h)
            }
        }
    }

    /**
     * Renders an Android Bitmap fusing full-bleed colorful Koi artwork into the Version 40 QR grid
     * with zero center emblem obstruction and crisp Koi Finder Eyes.
     */
    fun renderArtQrBitmap(
        matrix: BitMatrix,
        artPixels: IntArray,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND,
        modulePixels: Int = 8,
        coreHalfRatio: Float = 0.60f,
        outerShiftWeight: Float = 0.30f,
        darkCoreMaxLum: Float = 24f,
        lightCoreMinLum: Float = 216f
    ): Bitmap {
        val mw = matrix.width
        val sizePx = mw * modulePixels
        val outPixels = fuseArtIntoQrPixels(
            matrix = matrix,
            artPixels = artPixels,
            modulePixels = modulePixels,
            coreHalfRatio = coreHalfRatio,
            outerShiftWeight = outerShiftWeight,
            darkCoreMaxLum = darkCoreMaxLum,
            lightCoreMinLum = lightCoreMinLum
        )
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        return finalizeArtQrBitmap(outPixels, sizePx, margin, dataEnd, modulePixels, theme)
    }

    /**
     * Fallback renderer producing a vibrant Koi-themed Version 40 gradient QR code with rounded
     * squircle modules and crisp finder eyes (no center medallion cutout).
     */
    fun renderStyledQrBitmap(
        matrix: BitMatrix,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND,
        modulePixels: Int = 7
    ): Bitmap = renderStyledQrBitmapWithPixels(matrix, theme, modulePixels).first

    private fun renderStyledQrBitmapWithPixels(
        matrix: BitMatrix,
        theme: KoiQrTheme,
        modulePixels: Int
    ): Pair<Bitmap, IntArray> {
        val mw = matrix.width
        val sizePx = mw * modulePixels
        val pixels = IntArray(sizePx * sizePx) { theme.qrBg }

        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        val finderTL = margin..(margin + 6)
        val finderTR = (dataEnd - 7) until dataEnd
        val finderBL = (dataEnd - 7) until dataEnd

        val modF = modulePixels.toFloat()
        val inset = modF * 0.045f
        val cornerR = modF * 0.34f
        val squircleAlpha = buildSquircleAlphaMask(modulePixels, inset, cornerR)

        val qrStartPx = margin * modulePixels
        val qrSpanPx = (dataEnd - margin) * modulePixels
        val gradLut = buildDiagonalGradientLut(
            spanPx = qrSpanPx,
            startColor = theme.moduleGradStart,
            midColor = theme.moduleGradMid,
            endColor = theme.moduleGradEnd
        )
        val bgR = (theme.qrBg ushr 16) and 0xFF
        val bgG = (theme.qrBg ushr 8) and 0xFF
        val bgB = theme.qrBg and 0xFF

        for (my in margin until dataEnd) {
            val baseY = my * modulePixels
            for (mx in margin until dataEnd) {
                if (!matrix.get(mx, my)) continue
                val inTL = mx in finderTL && my in finderTL
                val inTR = mx in finderTR && my in finderTL
                val inBL = mx in finderTL && my in finderBL
                if (inTL || inTR || inBL) continue

                val baseX = mx * modulePixels
                val gradBase = (baseX - qrStartPx) + (baseY - qrStartPx)
                for (py in 0 until modulePixels) {
                    val rowOff = (baseY + py) * sizePx + baseX
                    val maskOff = py * modulePixels
                    val gradY = gradBase + py
                    for (px in 0 until modulePixels) {
                        val alpha = squircleAlpha[maskOff + px]
                        if (alpha == 0) continue
                        val fg = gradLut[(gradY + px).coerceIn(0, gradLut.lastIndex)]
                        if (alpha >= 256) {
                            pixels[rowOff + px] = fg
                        } else {
                            val inv = 256 - alpha
                            val r = (bgR * inv + ((fg ushr 16) and 0xFF) * alpha + 128) ushr 8
                            val g = (bgG * inv + ((fg ushr 8) and 0xFF) * alpha + 128) ushr 8
                            val b = (bgB * inv + (fg and 0xFF) * alpha + 128) ushr 8
                            pixels[rowOff + px] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
            }
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        val canvas = Canvas(bitmap)
        drawFinderEye(canvas, margin * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        drawFinderEye(canvas, (dataEnd - 7) * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerSecondary)
        drawFinderEye(canvas, margin * modF, (dataEnd - 7) * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        copyFinderPatchesToPixels(bitmap, pixels, sizePx, margin, dataEnd, modulePixels)
        return bitmap to pixels
    }

    private fun buildSquircleAlphaMask(modulePixels: Int, inset: Float, cornerR: Float): IntArray {
        val mask = IntArray(modulePixels * modulePixels)
        val modF = modulePixels.toFloat()
        val left = inset
        val top = inset
        val right = modF - inset
        val bottom = modF - inset
        val innerLeft = left + cornerR
        val innerRight = right - cornerR
        val innerTop = top + cornerR
        val innerBottom = bottom - cornerR
        val rSq = cornerR * cornerR
        val subOffsets = floatArrayOf(0.125f, 0.375f, 0.625f, 0.875f)

        for (py in 0 until modulePixels) {
            for (px in 0 until modulePixels) {
                var hits = 0
                for (sy in subOffsets) {
                    val v = py + sy
                    if (v < top || v > bottom) continue
                    for (sx in subOffsets) {
                        val u = px + sx
                        if (u < left || u > right) continue
                        val cx = when {
                            u < innerLeft -> innerLeft - u
                            u > innerRight -> u - innerRight
                            else -> 0f
                        }
                        val cy = when {
                            v < innerTop -> innerTop - v
                            v > innerBottom -> v - innerBottom
                            else -> 0f
                        }
                        if (cx == 0f || cy == 0f || cx * cx + cy * cy <= rSq) {
                            hits++
                        }
                    }
                }
                mask[py * modulePixels + px] = (hits * 256) / 16
            }
        }
        return mask
    }

    private fun buildDiagonalGradientLut(
        spanPx: Int,
        startColor: Int,
        midColor: Int,
        endColor: Int
    ): IntArray {
        val len = (spanPx * 2).coerceAtLeast(1)
        val denom = (len - 1).coerceAtLeast(1).toFloat()
        return IntArray(len) { d ->
            val t = d / denom
            if (t <= 0.5f) {
                lerpArgb(startColor, midColor, t * 2f)
            } else {
                lerpArgb(midColor, endColor, (t - 0.5f) * 2f)
            }
        }
    }

    private fun drawFinderEye(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        modPx: Float,
        outerColor: Int,
        innerColor: Int
    ) {
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outerColor
            style = Paint.Style.STROKE
            strokeWidth = modPx
        }
        val ringRect = RectF(
            startX + modPx * 0.5f,
            startY + modPx * 0.5f,
            startX + modPx * 6.5f,
            startY + modPx * 6.5f
        )
        canvas.drawRoundRect(ringRect, modPx * 0.55f, modPx * 0.55f, outerPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = innerColor
            style = Paint.Style.FILL
        }
        val innerRect = RectF(
            startX + modPx * 2.0f,
            startY + modPx * 2.0f,
            startX + modPx * 5.0f,
            startY + modPx * 5.0f
        )
        canvas.drawRoundRect(innerRect, modPx * 0.34f, modPx * 0.34f, innerPaint)
    }

    private fun drawKoiPondMedallion(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        theme: KoiQrTheme
    ) {
        // Clean halo matching QR canvas
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.qrBg
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 1.12f, haloPaint)

        // Accent gradient rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius,
                theme.accentPrimary,
                theme.accentSecondary,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, rimPaint)

        // Pond water core
        val innerR = radius * 0.89f
        val pondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A2E38.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, innerR, pondPaint)

        // Concentric pond ripples
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A5E6E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.045f
        }
        canvas.drawCircle(cx, cy, innerR * 0.68f, ripplePaint)
        ripplePaint.color = 0xFF287F92.toInt()
        canvas.drawCircle(cx, cy, innerR * 0.36f, ripplePaint)

        // Draw stylized dual Koi fish (Orange Kohaku + Cyan Koi) inside circle
        drawKoiPairIcon(canvas, cx, cy, innerR)
    }

    private fun drawKoiPairIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val scale = radius / 20f
        fun sx(v: Float) = cx + (v - 24f) * scale
        fun sy(v: Float) = cy + (v - 24f) * scale

        // Lily pad & lotus blossom
        val lilyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B5E3F.toInt() }
        canvas.drawCircle(sx(13f), sy(13.5f), 3.4f * scale, lilyPaint)
        val lotusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF70A6.toInt() }
        canvas.drawCircle(sx(13.8f), sy(13.2f), 1.4f * scale, lotusPaint)

        // Orange Kohaku Koi (right side, swimming clockwise)
        val orangeFish = Path().apply {
            moveTo(sx(24f), sy(8f))
            cubicTo(sx(30.5f), sy(8f), sx(37f), sy(13f), sx(37f), sy(21f))
            cubicTo(sx(37f), sy(26f), sx(33f), sy(31f), sx(29f), sy(33f))
            cubicTo(sx(27.5f), sy(33.8f), sx(26.5f), sy(32f), sx(28f), sy(30f))
            cubicTo(sx(31f), sy(26f), sx(32f), sy(22f), sx(31f), sy(18f))
            cubicTo(sx(29f), sy(14f), sx(26f), sy(12f), sx(24f), sy(11f))
            cubicTo(sx(23f), sy(10.5f), sx(23f), sy(8f), sx(24f), sy(8f))
            close()
        }
        val orangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6B35.toInt() }
        canvas.drawPath(orangeFish, orangePaint)

        // Orange Koi tail
        val orangeTail = Path().apply {
            moveTo(sx(29f), sy(33f))
            cubicTo(sx(32f), sy(36f), sx(34f), sy(40f), sx(31f), sy(41f))
            cubicTo(sx(29f), sy(39f), sx(28f), sy(36f), sx(28.5f), sy(33f))
            close()
        }
        canvas.drawPath(orangeTail, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF8C42.toInt() })

        // White Kohaku dorsal marking
        val whiteSpot = Path().apply {
            moveTo(sx(28f), sy(10.5f))
            cubicTo(sx(32f), sy(12.5f), sx(35f), sy(16f), sx(34.5f), sy(20f))
            cubicTo(sx(33f), sy(18f), sx(31f), sy(15f), sx(28f), sy(13f))
            close()
        }
        canvas.drawPath(whiteSpot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        // Cyan Koi (left side, swimming clockwise)
        val cyanFish = Path().apply {
            moveTo(sx(24f), sy(40f))
            cubicTo(sx(17.5f), sy(40f), sx(11f), sy(35f), sx(11f), sy(27f))
            cubicTo(sx(11f), sy(22f), sx(15f), sy(17f), sx(19f), sy(15f))
            cubicTo(sx(20.5f), sy(14.2f), sx(21.5f), sy(16f), sx(20f), sy(18f))
            cubicTo(sx(17f), sy(22f), sx(16f), sy(26f), sx(17f), sy(30f))
            cubicTo(sx(19f), sy(34f), sx(22f), sy(36f), sx(24f), sy(37f))
            cubicTo(sx(25f), sy(37.5f), sx(25f), sy(40f), sx(24f), sy(40f))
            close()
        }
        canvas.drawPath(cyanFish, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00B4D8.toInt() })

        // Cyan Koi tail
        val cyanTail = Path().apply {
            moveTo(sx(19f), sy(15f))
            cubicTo(sx(16f), sy(12f), sx(14f), sy(8f), sx(17f), sy(7f))
            cubicTo(sx(19f), sy(9f), sx(20f), sy(12f), sx(19.5f), sy(15f))
            close()
        }
        canvas.drawPath(cyanTail, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF48CAE4.toInt() })

        // Aqua dorsal marking
        val aquaSpot = Path().apply {
            moveTo(sx(20f), sy(37.5f))
            cubicTo(sx(16f), sy(35.5f), sx(13f), sy(32f), sx(13.5f), sy(28f))
            cubicTo(sx(15f), sy(30f), sx(17f), sy(33f), sx(20f), sy(35f))
            close()
        }
        canvas.drawPath(aquaSpot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCAF0F8.toInt() })
    }

    /**
     * Renders a pure JVM-compatible flat pixel array (used in unit tests & verification).
     */
    internal fun renderPixels(
        matrix: BitMatrix,
        modulePixels: Int = 6,
        darkColor: Int = 0xFF000000.toInt(),
        lightColor: Int = 0xFFFFFFFF.toInt(),
        centerExclusionRadiusModules: Float = 0f
    ): IntArray {
        val mw = matrix.width
        val mh = matrix.height
        val bw = mw * modulePixels
        val bh = mh * modulePixels
        val pixels = IntArray(bw * bh) { lightColor }
        val centerMod = mw / 2f
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        val finderTL = margin..(margin + 6)
        val finderTR = (dataEnd - 7) until dataEnd
        val finderBL = (dataEnd - 7) until dataEnd
        val exclSq = centerExclusionRadiusModules * centerExclusionRadiusModules

        for (my in 0 until mh) {
            val pyStart = my * modulePixels
            for (mx in 0 until mw) {
                if (!matrix.get(mx, my)) continue
                val inFinder = (mx in finderTL && my in finderTL) ||
                    (mx in finderTR && my in finderTL) ||
                    (mx in finderTL && my in finderBL)
                if (!inFinder && centerExclusionRadiusModules > 0f) {
                    val dx = (mx + 0.5f) - centerMod
                    val dy = (my + 0.5f) - centerMod
                    if (dx * dx + dy * dy <= exclSq) continue
                }

                val pxStart = mx * modulePixels
                for (dy in 0 until modulePixels) {
                    val rowOffset = (pyStart + dy) * bw + pxStart
                    for (dx in 0 until modulePixels) {
                        pixels[rowOffset + dx] = darkColor
                    }
                }
            }
        }
        return pixels
    }

    private fun getAppLogoBitmap(context: Context): Bitmap? {
        cachedAppLogo?.get()?.takeUnless { it.isRecycled }?.let { return it }
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_round, opts)?.also { bmp ->
                cachedAppLogo = SoftReference(bmp)
            }
        }.getOrNull()
    }

    /**
     * Renders a vibrant `1620 x 1800` (or `1440 x 1600` for 7px fallback) Koi Pond Share Card
     * containing the full-color Version 40 QR code drawn 1:1 pixel-exact for crisp WhatsApp & social sharing.
     */
    private fun renderShareCardBitmap(
        styledQrBitmap: Bitmap,
        theme: KoiQrTheme,
        title: String,
        songCount: Int,
        context: Context? = null
    ): Bitmap {
        val qrSize = styledQrBitmap.width // 185 * 8 = 1480px (or 1295px fallback)
        val width = max(1440, qrSize + 140)
        val height = (width * 10) / 9
        val scale = width / 1440f
        val sidePad = (width - qrSize) / 2f
        val headerHeight = 192f * scale

        val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)

        // Deep Pond vertical gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(theme.cardTopColor, theme.cardMidColor, theme.cardBottomColor),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle ambient Koi glows
        val topGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.16f,
                height * 0.10f,
                520f * scale,
                Color.argb(62, Color.red(theme.accentPrimary), Color.green(theme.accentPrimary), Color.blue(theme.accentPrimary)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.16f, height * 0.10f, 520f * scale, topGlowPaint)

        val bottomGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.86f,
                height * 0.90f,
                540f * scale,
                Color.argb(56, Color.red(theme.accentSecondary), Color.green(theme.accentSecondary), Color.blue(theme.accentSecondary)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.86f, height * 0.90f, 540f * scale, bottomGlowPaint)

        // Subtle concentric pond ripples behind QR
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.rippleColor
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            alpha = 110
        }
        val qrCenterX = width / 2f
        val qrCenterY = headerHeight + qrSize / 2f
        canvas.drawCircle(qrCenterX, qrCenterY, 760f * scale, ripplePaint)
        canvas.drawCircle(qrCenterX, qrCenterY, 880f * scale, ripplePaint)

        // Header App Logo + Playlist Info
        val logoRadius = 46f * scale
        val logoCx = sidePad + logoRadius
        val logoCy = 104f * scale
        val appLogoBitmap = context?.let { getAppLogoBitmap(it) }
        if (appLogoBitmap != null) {
            val logoRect = RectF(
                logoCx - logoRadius,
                logoCy - logoRadius,
                logoCx + logoRadius,
                logoCy + logoRadius
            )
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(appLogoBitmap, null, logoRect, logoPaint)
        } else {
            drawKoiPondMedallion(canvas, logoCx, logoCy, logoRadius, theme)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF0FDFD.toInt()
            textSize = 46f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentSecondary
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textStartX = logoCx + logoRadius + 28f * scale
        val maxTextWidth = width - textStartX - sidePad
        val clippedTitle = ellipsizeText(title.ifBlank { "Shared Playlist" }, titlePaint, maxTextWidth)
        canvas.drawText(clippedTitle, textStartX, 96f * scale, titlePaint)
        canvas.drawText(
            "KOI PLAYLIST • $songCount ${if (songCount == 1) "SONG" else "SONGS"}",
            textStartX,
            138f * scale,
            subtitlePaint
        )

        // Glowing gradient frame around QR card
        val framePad = 12f * scale
        val qrRect = RectF(
            sidePad,
            headerHeight,
            sidePad + qrSize,
            headerHeight + qrSize
        )
        val frameRect = RectF(
            qrRect.left - framePad,
            qrRect.top - framePad,
            qrRect.right + framePad,
            qrRect.bottom + framePad
        )
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f * scale
            shader = LinearGradient(
                frameRect.left,
                frameRect.top,
                frameRect.right,
                frameRect.bottom,
                intArrayOf(theme.accentPrimary, theme.accentSecondary, theme.accentPrimary),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(frameRect, 36f * scale, 36f * scale, framePaint)

        // Keep corners softly rounded without clipping into the 4-module quiet zone
        canvas.save()
        val qrClip = Path().apply {
            addRoundRect(qrRect, 24f * scale, 24f * scale, Path.Direction.CW)
        }
        canvas.clipPath(qrClip)
        val qrPaint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        canvas.drawBitmap(styledQrBitmap, qrRect.left, qrRect.top, qrPaint)
        canvas.restore()

        // Footer caption
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB8D4DC.toInt()
            textSize = 26f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(
            "SCAN IN KOI OR IMPORT IMAGE TO ADD PLAYLIST",
            width / 2f,
            headerHeight + qrSize + 66f * scale,
            footerPaint
        )

        return card
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "..."
    }

    /**
     * Writes the share card bitmap to cache and launches Android's Share Sheet (`ACTION_SEND`).
     */
    suspend fun shareQrImage(
        context: Context,
        shareCardBitmap: Bitmap,
        playlistTitle: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val shareDir = File(context.cacheDir, "shared_qr").apply { mkdirs() }
            shareDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                    runCatching { file.delete() }
                }
            }
            val safeName = playlistTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(32).ifEmpty { "playlist" }
            val outFile = File(shareDir, "koi_qr_${safeName}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                shareCardBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Playlist QR").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    /**
     * Saves the QR share card PNG to the device's Pictures/Koi album via MediaStore.
     */
    suspend fun saveQrToGallery(
        context: Context,
        shareCardBitmap: Bitmap,
        playlistTitle: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = playlistTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(32).ifEmpty { "playlist" }
            val fileName = "Koi_${safeName}_${System.currentTimeMillis()}.png"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Koi")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore image entry")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!shareCardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Failed to encode PNG")
                    }
                } ?: error("Failed to open output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            fileName
        }
    }

    /**
     * Decodes a QR code string from an image [Uri] (gallery, WhatsApp image, screenshot)
     * using MLKit Barcode Scanning first, followed by multi-strategy ZXing fallbacks.
     */
    suspend fun decodeQrFromImageUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadSoftwareBitmap(context, uri)
                ?: error("Could not open selected image")
            try {
                decodeQrFromBitmap(bitmap)
                    ?: error("No QR code found in this image")
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        // 1. Try Google MLKit Barcode Scanner (handles perspective, screenshots, blur)
        val mlkitResults = scanWithMlKit(bitmap)
        mlkitResults.firstOrNull { PlaylistShareCodec.isKoiPlaylistPayload(it) }?.let { return it }

        // 2. Try ZXing with multiple binarizers & crops (very strong on crisp/JPEG-recompressed Version 40)
        val zxingCandidates = buildList {
            add(bitmap)
            // If tall share card or portrait screenshot, also try square bottom/center crop
            if (bitmap.height > bitmap.width) {
                val size = bitmap.width
                val yBottom = (bitmap.height - size).coerceAtLeast(0)
                add(Bitmap.createBitmap(bitmap, 0, yBottom, size, size))
                val yCenter = ((bitmap.height - size) / 2).coerceAtLeast(0)
                if (yCenter != yBottom) {
                    add(Bitmap.createBitmap(bitmap, 0, yCenter, size, size))
                }
                // Share card crop matching 1440x1600 layout (header ~12% from top)
                val qrCropSize = min(bitmap.width, (bitmap.width * 0.92f).toInt()).coerceAtLeast(1)
                val cropX = ((bitmap.width - qrCropSize) / 2).coerceAtLeast(0)
                val cropY = (bitmap.height * 0.11f).toInt().coerceIn(0, (bitmap.height - qrCropSize).coerceAtLeast(0))
                add(Bitmap.createBitmap(bitmap, cropX, cropY, qrCropSize, qrCropSize))
            }
            if (bitmap.width < 900 || bitmap.height < 900) {
                add(Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, false))
            }
        }

        var firstNonKoiText: String? = mlkitResults.firstOrNull()

        try {
            for (candidate in zxingCandidates) {
                val w = candidate.width
                val h = candidate.height
                val pixels = IntArray(w * h)
                candidate.getPixels(pixels, 0, w, 0, 0, w, h)
                val decoded = decodePixels(pixels, w, h)
                if (!decoded.isNullOrBlank()) {
                    if (PlaylistShareCodec.isKoiPlaylistPayload(decoded)) {
                        return decoded
                    }
                    if (firstNonKoiText == null) firstNonKoiText = decoded
                }
            }
        } finally {
            for (candidate in zxingCandidates) {
                if (candidate !== bitmap && !candidate.isRecycled) {
                    candidate.recycle()
                }
            }
        }
        return firstNonKoiText
    }

    internal fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        val reader = QRCodeReader()
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        val source = RGBLuminanceSource(width, height, pixels)
        val binarizers = listOf(
            HybridBinarizer(source),
            GlobalHistogramBinarizer(source),
            GlobalHistogramBinarizer(source.invert())
        )
        var firstText: String? = null
        for (binarizer in binarizers) {
            try {
                val decoded = reader.decode(BinaryBitmap(binarizer), hints).text
                if (!decoded.isNullOrBlank()) {
                    if (PlaylistShareCodec.isKoiPlaylistPayload(decoded)) {
                        return decoded
                    }
                    if (firstText == null) firstText = decoded
                }
            } catch (_: Exception) {
                reader.reset()
            }
        }
        return firstText
    }

    private suspend fun scanWithMlKit(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val values = barcodes.mapNotNull { it.rawValue?.takeIf { raw -> raw.isNotBlank() } }
                scanner.close()
                if (cont.isActive) cont.resume(values)
            }
            .addOnFailureListener {
                scanner.close()
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    private fun loadSoftwareBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxDim = max(info.size.width, info.size.height)
                    if (maxDim > 2400) {
                        val scale = 2400f / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                var sampleSize = 1
                val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (maxDim / sampleSize > 2400) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
