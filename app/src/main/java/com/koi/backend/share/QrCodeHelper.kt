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
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
     * selected EC level and picks the mask whose modules align best with dark/light artwork regions.
     * Note: We intentionally omit `QR_COMPACT` because ZXing's `MinimalEncoder` ignores
     * `QR_VERSION`; since `KOI://P/<BASE41>` is 100% QR Alphanumeric, standard `Encoder`
     * automatically selects dense Alphanumeric mode AND strictly honors `QR_VERSION = 40`.
     */
    fun encodeVersion40BitMatrix(
        content: String,
        artLuminanceGrid: FloatArray? = null,
        gridSize: Int = 0
    ): Version40MatrixResult {
        val writer = QRCodeWriter()
        val ecLevels = listOf(
            ErrorCorrectionLevel.H,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.L
        )
        var lastException: Exception? = null
        for (ec in ecLevels) {
            val baseHints = mapOf(
                EncodeHintType.QR_VERSION to QR_VERSION,
                EncodeHintType.ERROR_CORRECTION to ec,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES
            )
            try {
                val defaultMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, baseHints)
                if (artLuminanceGrid != null && gridSize == defaultMatrix.width) {
                    var bestMatrix = defaultMatrix
                    var bestScore = Double.NEGATIVE_INFINITY
                    for (maskId in 0..7) {
                        val maskHints = baseHints + (EncodeHintType.QR_MASK_PATTERN to maskId)
                        val candidate = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, maskHints)
                        val score = scoreMatrixArtAlignment(candidate, artLuminanceGrid, gridSize)
                        if (score > bestScore) {
                            bestScore = score
                            bestMatrix = candidate
                        }
                    }
                    return Version40MatrixResult(bestMatrix, ec)
                }
                return Version40MatrixResult(defaultMatrix, ec)
            } catch (e: WriterException) {
                lastException = e
            }
        }
        throw lastException ?: WriterException("Payload exceeds QR Version 40 capacity")
    }

    private fun scoreMatrixArtAlignment(
        matrix: BitMatrix,
        artLumGrid: FloatArray,
        gridSize: Int
    ): Double {
        val margin = QUIET_ZONE_MODULES
        val dataEnd = gridSize - margin
        var score = 0.0
        for (my in margin until dataEnd) {
            val rowOff = my * gridSize
            for (mx in margin until dataEnd) {
                val delta = 140.0 - artLumGrid[rowOff + mx]
                score += if (matrix.get(mx, my)) delta else -delta
            }
        }
        return score
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
        context: Context? = null
    ): QrGenerationResult = withContext(Dispatchers.Default) {
        if (songs.isEmpty()) {
            return@withContext QrGenerationResult.Error("Add songs to this playlist before sharing")
        }

        val payload = try {
            PlaylistShareCodec.encodePlaylist(playlistTitle, playlistDescription, coverUrl, songs)
        } catch (e: Exception) {
            return@withContext QrGenerationResult.Error(e.message ?: "Failed to encode playlist")
        }

        val modulePixels = 8
        val fullSizePx = (QR_CODE_MODULES + QUIET_ZONE_MODULES * 2) * modulePixels // 185 * 8 = 1480
        val artPixels = context?.let { loadScaledArtPixels(it, theme.artAssetPath, fullSizePx) }
        val artLumGrid = artPixels?.let {
            computeModuleLuminanceGrid(it, fullSizePx, QR_CODE_MODULES + QUIET_ZONE_MODULES * 2, modulePixels)
        }

        val matrixResult = try {
            encodeVersion40BitMatrix(
                content = payload.uri,
                artLuminanceGrid = artLumGrid,
                gridSize = QR_CODE_MODULES + QUIET_ZONE_MODULES * 2
            )
        } catch (_: WriterException) {
            return@withContext QrGenerationResult.TooLarge(
                songCount = songs.size,
                encodedChars = payload.uri.length,
                maxChars = PlaylistShareCodec.MAX_QR_V40_TOTAL_CHARS
            )
        }

        var styledQrBitmap = if (artPixels != null) {
            renderArtQrBitmap(
                matrix = matrixResult.bitMatrix,
                artPixels = artPixels,
                theme = theme,
                modulePixels = modulePixels,
                coreHalfRatio = 0.60f,
                outerShiftWeight = 0.30f,
                darkCoreMaxLum = 24f,
                lightCoreMinLum = 216f
            )
        } else {
            renderStyledQrBitmap(
                matrix = matrixResult.bitMatrix,
                theme = theme,
                modulePixels = modulePixels
            )
        }

        // Self-verify scannability; if a tough EC-L pattern needs stronger dot definition,
        // automatically retry once with firmer core dots, then fallback cleanly if still needed.
        if (!verifyBitmapDecodable(styledQrBitmap, payload.uri)) {
            if (artPixels != null) {
                styledQrBitmap.recycle()
                styledQrBitmap = renderArtQrBitmap(
                    matrix = matrixResult.bitMatrix,
                    artPixels = artPixels,
                    theme = theme,
                    modulePixels = modulePixels,
                    coreHalfRatio = 0.70f,
                    outerShiftWeight = 0.56f,
                    darkCoreMaxLum = 20f,
                    lightCoreMinLum = 222f
                )
            }
            if (!verifyBitmapDecodable(styledQrBitmap, payload.uri)) {
                styledQrBitmap.recycle()
                styledQrBitmap = renderStyledQrBitmap(
                    matrix = matrixResult.bitMatrix,
                    theme = theme,
                    modulePixels = modulePixels
                )
            }
        }

        val shareCardBitmap = renderShareCardBitmap(
            styledQrBitmap = styledQrBitmap,
            theme = theme,
            title = playlistTitle,
            songCount = songs.size
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

    private fun verifyBitmapDecodable(bitmap: Bitmap, expectedText: String): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            decodePixels(pixels, w, h) == expectedText
        } catch (_: Exception) {
            false
        }
    }

    private fun loadScaledArtPixels(context: Context, assetPath: String, targetSize: Int): IntArray? {
        return runCatching {
            val decoded = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
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
        for (my in 0 until gridSize) {
            val py = (my * modulePixels + modulePixels / 2).coerceAtMost(imageSize - 1)
            val rowOff = my * gridSize
            for (mx in 0 until gridSize) {
                val px = (mx * modulePixels + modulePixels / 2).coerceAtMost(imageSize - 1)
                grid[rowOff + mx] = rgbLuminance(artPixels[py * imageSize + px])
            }
        }
        return grid
    }

    private fun rgbLuminance(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun shiftToTargetLum(argb: Int, targetLum: Float, satBoost: Float): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        val l = 0.299f * r + 0.587f * g + 0.114f * b
        if (l < 1f) {
            val v = targetLum.roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
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

        var ri = outR.roundToInt().coerceIn(0, 255)
        var gi = outG.roundToInt().coerceIn(0, 255)
        var bi = outB.roundToInt().coerceIn(0, 255)
        val actualL = 0.299f * ri + 0.587f * gi + 0.114f * bi
        val diff = (targetLum - actualL).roundToInt()
        ri = (ri + diff).coerceIn(0, 255)
        gi = (gi + diff).coerceIn(0, 255)
        bi = (bi + diff).coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun lerpArgb(c1: Int, c2: Int, t: Float): Int {
        val inv = 1f - t
        val r = (((c1 ushr 16) and 0xFF) * inv + ((c2 ushr 16) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val g = (((c1 ushr 8) and 0xFF) * inv + ((c2 ushr 8) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) * inv + (c2 and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun computeHighFrequencyDetail(artPixels: IntArray, size: Int): FloatArray {
        val total = size * size
        val lums = FloatArray(total)
        for (i in 0 until total) {
            lums[i] = rgbLuminance(artPixels[i])
        }

        // Fast 2-pass separable box blur (radius = 3 -> 7-pixel span) for crisp scale/fin detail retention
        val horiz = FloatArray(total)
        val rad = 3
        val span = (rad * 2 + 1).toFloat()
        for (y in 0 until size) {
            val rowOff = y * size
            for (x in 0 until size) {
                var sum = 0f
                for (dx in -rad..rad) {
                    val xx = (x + dx).coerceIn(0, size - 1)
                    sum += lums[rowOff + xx]
                }
                horiz[rowOff + x] = sum / span
            }
        }

        val detail = FloatArray(total)
        for (y in 0 until size) {
            val rowOff = y * size
            for (x in 0 until size) {
                var sum = 0f
                for (dy in -rad..rad) {
                    val yy = (y + dy).coerceIn(0, size - 1)
                    sum += horiz[yy * size + x]
                }
                detail[rowOff + x] = lums[rowOff + x] - (sum / span)
            }
        }
        return detail
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

        val highFreqDetail = computeHighFrequencyDetail(artPixels, size)
        val outPixels = IntArray(size * size)
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        val edgeSoft = 0.16f
        val coreLowEdge = coreHalfRatio - edgeSoft * 0.5f
        val coreHighEdge = coreHalfRatio + edgeSoft * 0.5f

        for (my in 0 until mw) {
            for (mx in 0 until mw) {
                val isQuiet = mx < margin || mx >= dataEnd || my < margin || my >= dataEnd
                val inTL = mx in margin until (margin + 7) && my in margin until (margin + 7)
                val inTR = mx in (dataEnd - 7) until dataEnd && my in margin until (margin + 7)
                val inBL = mx in margin until (margin + 7) && my in (dataEnd - 7) until dataEnd
                val isFinder = inTL || inTR || inBL
                val inTLSep = !inTL && mx in margin..(margin + 7) && my in margin..(margin + 7)
                val inTRSep = !inTR && mx in (dataEnd - 8) until dataEnd && my in margin..(margin + 7)
                val inBLSep = !inBL && mx in margin..(margin + 7) && my in (dataEnd - 8) until dataEnd
                val isFinderSep = inTLSep || inTRSep || inBLSep

                val bit = !isQuiet && matrix.get(mx, my)

                var modArtLumSum = 0f
                for (py in 0 until modulePixels) {
                    val y = my * modulePixels + py
                    val rowOff = y * size + mx * modulePixels
                    for (px in 0 until modulePixels) {
                        modArtLumSum += rgbLuminance(artPixels[rowOff + px])
                    }
                }
                val modArtNorm = (modArtLumSum / (modulePixels * modulePixels)) / 255f

                for (py in 0 until modulePixels) {
                    val y = my * modulePixels + py
                    val ny = ((py + 0.5f) / modulePixels - 0.5f) * 2f
                    val any = abs(ny)
                    val ny2 = ny * ny
                    val rowOff = y * size

                    for (px in 0 until modulePixels) {
                        val x = mx * modulePixels + px
                        val idx = rowOff + x
                        val baseRgb = artPixels[idx]
                        val baseL = rgbLuminance(baseRgb)
                        val detail = highFreqDetail[idx]

                        if (isQuiet || isFinder || isFinderSep) {
                            val minLight = if (isFinder || isFinderSep) 198f else 170f
                            val targetL = (max(baseL, minLight + modArtNorm * 18f) + detail * 0.24f).coerceIn(0f, 244f)
                            val blend = if (isFinderSep) 0.88f else 0.78f
                            val shifted = shiftToTargetLum(baseRgb, targetL, 1.15f)
                            var pxOut = lerpArgb(baseRgb, shifted, blend)

                            // When rendering pure pixels (unit tests without Canvas overlay), ensure finder patterns remain readable
                            if (isFinder && bit) {
                                pxOut = shiftToTargetLum(baseRgb, 18f, 1.4f)
                            }
                            outPixels[idx] = pxOut
                            continue
                        }

                        val nx = ((px + 0.5f) / modulePixels - 0.5f) * 2f
                        val anx = abs(nx)
                        val sqDist = max(anx, any) * 0.68f + sqrt(nx * nx + ny2) * 0.32f
                        val coreMask = when {
                            sqDist <= coreLowEdge -> 1f
                            sqDist >= coreHighEdge -> 0f
                            else -> 1f - (sqDist - coreLowEdge) / edgeSoft
                        }

                        if (bit) {
                            val targetDarkL = (12f + modArtNorm * (darkCoreMaxLum - 12f) + detail * 0.25f)
                                .coerceIn(6f, darkCoreMaxLum + 8f)
                            val coreRgb = shiftToTargetLum(baseRgb, targetDarkL, 1.50f)

                            val outerDarkL = min(baseL, max(targetDarkL + 38f, baseL * (1f - 0.65f * outerShiftWeight)))
                            val outerRgb = lerpArgb(baseRgb, shiftToTargetLum(baseRgb, outerDarkL, 1.25f), outerShiftWeight)
                            outPixels[idx] = lerpArgb(outerRgb, coreRgb, coreMask)
                        } else {
                            val targetLightL = (max(lightCoreMinLum, baseL) + modArtNorm * 12f + detail * 0.25f)
                                .coerceAtMost(250f)
                            val coreRgb = shiftToTargetLum(baseRgb, targetLightL, 1.12f)

                            val outerLightL = max(baseL, targetLightL - (1f - outerShiftWeight) * 65f)
                            val outerRgb = lerpArgb(baseRgb, shiftToTargetLum(baseRgb, outerLightL, 1.10f), outerShiftWeight)
                            outPixels[idx] = lerpArgb(outerRgb, coreRgb, coreMask)
                        }
                    }
                }
            }
        }
        return outPixels
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

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(outPixels, 0, sizePx, 0, 0, sizePx, sizePx)

        val canvas = Canvas(bitmap)
        val modF = modulePixels.toFloat()
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES

        // Clean high-contrast rounded Koi Finder Eyes over luminous colored water
        drawFinderEye(canvas, margin * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        drawFinderEye(canvas, (dataEnd - 7) * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerSecondary)
        drawFinderEye(canvas, margin * modF, (dataEnd - 7) * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)

        return bitmap
    }

    /**
     * Fallback renderer producing a vibrant Koi-themed Version 40 gradient QR code with rounded
     * squircle modules and crisp finder eyes (no center medallion cutout).
     */
    fun renderStyledQrBitmap(
        matrix: BitMatrix,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND,
        modulePixels: Int = 7
    ): Bitmap {
        val mw = matrix.width
        val sizePx = mw * modulePixels
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(theme.qrBg)

        val modF = modulePixels.toFloat()
        val margin = ((mw - QR_CODE_MODULES) / 2).coerceAtLeast(0)
        val dataEnd = margin + QR_CODE_MODULES
        val finderTL = margin..(margin + 6)
        val finderTR = (dataEnd - 7) until dataEnd
        val finderBL = (dataEnd - 7) until dataEnd

        val dataPath = Path()
        val inset = modF * 0.045f
        val cornerR = modF * 0.34f

        for (my in margin until dataEnd) {
            for (mx in margin until dataEnd) {
                if (!matrix.get(mx, my)) continue
                val inTL = mx in finderTL && my in finderTL
                val inTR = mx in finderTR && my in finderTL
                val inBL = mx in finderTL && my in finderBL
                if (inTL || inTR || inBL) continue

                val left = mx * modF + inset
                val top = my * modF + inset
                val right = (mx + 1) * modF - inset
                val bottom = (my + 1) * modF - inset
                dataPath.addRoundRect(left, top, right, bottom, cornerR, cornerR, Path.Direction.CW)
            }
        }

        val qrBoundsStart = margin * modF
        val qrBoundsEnd = dataEnd * modF
        val modulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                qrBoundsStart,
                qrBoundsStart,
                qrBoundsEnd,
                qrBoundsEnd,
                intArrayOf(theme.moduleGradStart, theme.moduleGradMid, theme.moduleGradEnd),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(dataPath, modulePaint)

        drawFinderEye(canvas, margin * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        drawFinderEye(canvas, (dataEnd - 7) * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerSecondary)
        drawFinderEye(canvas, margin * modF, (dataEnd - 7) * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)

        return bitmap
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

    /**
     * Renders a vibrant `1620 x 1800` (or `1440 x 1600` for 7px fallback) Koi Pond Share Card
     * containing the full-color Version 40 QR code drawn 1:1 pixel-exact for crisp WhatsApp & social sharing.
     */
    private fun renderShareCardBitmap(
        styledQrBitmap: Bitmap,
        theme: KoiQrTheme,
        title: String,
        songCount: Int
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

        // Header Koi Medallion + Playlist Info
        val logoRadius = 46f * scale
        val logoCx = sidePad + logoRadius
        val logoCy = 104f * scale
        drawKoiPondMedallion(canvas, logoCx, logoCy, logoRadius, theme)

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
                putExtra(Intent.EXTRA_SUBJECT, "Koi Playlist: $playlistTitle")
                putExtra(Intent.EXTRA_TEXT, "Scan this QR code in Koi to import \"$playlistTitle\"")
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
            GlobalHistogramBinarizer(source),
            HybridBinarizer(source),
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
