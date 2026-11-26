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
import kotlin.math.max
import kotlin.math.min

/**
 * Koi-themed visual palettes for Version 40 QR rendering.
 * Module gradient stops intentionally preserve strong luminance contrast against [qrBg]
 * so dense 177x177 Version 40 QR codes remain fast and reliable to scan.
 */
enum class KoiQrTheme(
    val label: String,
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
        finderOuterColor = 0xFF072833.toInt(),
        finderInnerPrimary = 0xFFC43408.toInt(),
        finderInnerSecondary = 0xFF006490.toInt()
    ),
    KOHAKU_SUNSET(
        label = "Kohaku Gold",
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
        finderOuterColor = 0xFF380F09.toInt(),
        finderInnerPrimary = 0xFFC83D0A.toInt(),
        finderInnerSecondary = 0xFF9E3A00.toInt()
    ),
    LOTUS_CYAN(
        label = "Lotus Cyan",
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
        finderOuterColor = 0xFF052434.toInt(),
        finderInnerPrimary = 0xFF006B98.toInt(),
        finderInnerSecondary = 0xFF8C2264.toInt()
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
     * Note: We intentionally omit `QR_COMPACT` because ZXing's `MinimalEncoder` ignores
     * `QR_VERSION`; since `KOI://P/<BASE41>` is 100% QR Alphanumeric, standard `Encoder`
     * automatically selects dense Alphanumeric mode AND strictly honors `QR_VERSION = 40`.
     */
    fun encodeVersion40BitMatrix(content: String): Version40MatrixResult {
        val writer = QRCodeWriter()
        val ecLevels = listOf(
            ErrorCorrectionLevel.H,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.L
        )
        var lastException: Exception? = null
        for (ec in ecLevels) {
            val hints = mapOf(
                EncodeHintType.QR_VERSION to QR_VERSION,
                EncodeHintType.ERROR_CORRECTION to ec,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES
            )
            try {
                val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
                return Version40MatrixResult(matrix, ec)
            } catch (e: WriterException) {
                lastException = e
            }
        }
        throw lastException ?: WriterException("Payload exceeds QR Version 40 capacity")
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
     * Encodes the playlist and renders a styled Koi Pond Version 40 QR bitmap and
     * a `1440 x 1600` social/WhatsApp share card bitmap, self-verifying center-emblem
     * scannability automatically.
     */
    suspend fun generatePlaylistQr(
        playlistTitle: String,
        playlistDescription: String?,
        coverUrl: String?,
        songs: List<com.koi.backend.innertube.SongItem>,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND
    ): QrGenerationResult = withContext(Dispatchers.Default) {
        if (songs.isEmpty()) {
            return@withContext QrGenerationResult.Error("Add songs to this playlist before sharing")
        }

        val payload = try {
            PlaylistShareCodec.encodePlaylist(playlistTitle, playlistDescription, coverUrl, songs)
        } catch (e: Exception) {
            return@withContext QrGenerationResult.Error(e.message ?: "Failed to encode playlist")
        }

        val matrixResult = try {
            encodeVersion40BitMatrix(payload.uri)
        } catch (_: WriterException) {
            return@withContext QrGenerationResult.TooLarge(
                songCount = songs.size,
                encodedChars = payload.uri.length,
                maxChars = PlaylistShareCodec.MAX_QR_V40_TOTAL_CHARS
            )
        }

        val preferredRadiusModules = when (matrixResult.errorCorrectionLevel) {
            ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q -> 12.5f
            ErrorCorrectionLevel.M -> 11.5f
            ErrorCorrectionLevel.L -> 9.5f
        }

        var styledQrBitmap = renderStyledQrBitmap(
            matrix = matrixResult.bitMatrix,
            theme = theme,
            modulePixels = 7,
            emblemRadiusModules = preferredRadiusModules
        )

        // Self-verify styled QR pixels with center emblem; if dense EC-L edge case fails,
        // fall back cleanly to styled QR without center cutout so every generated QR scans 100%.
        if (!verifyBitmapDecodable(styledQrBitmap, payload.uri)) {
            styledQrBitmap.recycle()
            styledQrBitmap = renderStyledQrBitmap(
                matrix = matrixResult.bitMatrix,
                theme = theme,
                modulePixels = 7,
                emblemRadiusModules = 0f
            )
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

    /**
     * Renders a colorful, Koi-themed Version 40 QR code with smooth rounded squircle
     * modules, a multi-stop diagonal Koi gradient, stylized finder eyes, and an optional
     * centered Koi Pond medallion.
     */
    fun renderStyledQrBitmap(
        matrix: BitMatrix,
        theme: KoiQrTheme = KoiQrTheme.KOI_POND,
        modulePixels: Int = 7,
        emblemRadiusModules: Float = 11.5f
    ): Bitmap {
        val mw = matrix.width
        val mh = matrix.height
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

        val centerMod = mw / 2f
        val emblemMaskRadiusMod = if (emblemRadiusModules > 0f) emblemRadiusModules + 1.2f else 0f
        val emblemMaskSq = emblemMaskRadiusMod * emblemMaskRadiusMod

        // Collect all regular data/alignment modules into one smooth Path for unified gradient fill
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

                if (emblemMaskRadiusMod > 0f) {
                    val dx = (mx + 0.5f) - centerMod
                    val dy = (my + 0.5f) - centerMod
                    if (dx * dx + dy * dy <= emblemMaskSq) continue
                }

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

        // Draw 3 stylized Koi Finder Eyes (7x7 outer rounded frame + 3x3 colorful inner jewel)
        drawFinderEye(canvas, margin * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)
        drawFinderEye(canvas, (dataEnd - 7) * modF, margin * modF, modF, theme.finderOuterColor, theme.finderInnerSecondary)
        drawFinderEye(canvas, margin * modF, (dataEnd - 7) * modF, modF, theme.finderOuterColor, theme.finderInnerPrimary)

        // Draw centered Koi Pond Medallion
        if (emblemRadiusModules > 0f) {
            val cx = centerMod * modF
            val cy = centerMod * modF
            drawKoiPondMedallion(
                canvas = canvas,
                cx = cx,
                cy = cy,
                radius = emblemRadiusModules * modF,
                theme = theme
            )
        }

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
        canvas.drawRoundRect(ringRect, modPx * 1.55f, modPx * 1.55f, outerPaint)

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
        canvas.drawRoundRect(innerRect, modPx * 0.9f, modPx * 0.9f, innerPaint)
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
     * Renders a vibrant `1440 x 1600` Koi Pond Share Card containing the styled Version 40 QR code
     * (`1295 x 1295` at 7px/module) with glowing aquatic accents for WhatsApp & social sharing.
     */
    private fun renderShareCardBitmap(
        styledQrBitmap: Bitmap,
        theme: KoiQrTheme,
        title: String,
        songCount: Int
    ): Bitmap {
        val qrSize = styledQrBitmap.width // 185 * 7 = 1295px
        val width = 1440
        val height = 1600
        val sidePad = (width - qrSize) / 2f // ~72.5px
        val headerHeight = 192f

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
                520f,
                Color.argb(62, Color.red(theme.accentPrimary), Color.green(theme.accentPrimary), Color.blue(theme.accentPrimary)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.16f, height * 0.10f, 520f, topGlowPaint)

        val bottomGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.86f,
                height * 0.90f,
                540f,
                Color.argb(56, Color.red(theme.accentSecondary), Color.green(theme.accentSecondary), Color.blue(theme.accentSecondary)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.86f, height * 0.90f, 540f, bottomGlowPaint)

        // Subtle concentric pond ripples behind QR
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.rippleColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 110
        }
        val qrCenterX = width / 2f
        val qrCenterY = headerHeight + qrSize / 2f
        canvas.drawCircle(qrCenterX, qrCenterY, 760f, ripplePaint)
        canvas.drawCircle(qrCenterX, qrCenterY, 880f, ripplePaint)

        // Header Koi Medallion + Playlist Info
        val logoRadius = 46f
        val logoCx = sidePad + logoRadius
        val logoCy = 104f
        drawKoiPondMedallion(canvas, logoCx, logoCy, logoRadius, theme)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF0FDFD.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentSecondary
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textStartX = logoCx + logoRadius + 28f
        val maxTextWidth = width - textStartX - sidePad
        val clippedTitle = ellipsizeText(title.ifBlank { "Shared Playlist" }, titlePaint, maxTextWidth)
        canvas.drawText(clippedTitle, textStartX, 96f, titlePaint)
        canvas.drawText(
            "KOI PLAYLIST • $songCount ${if (songCount == 1) "SONG" else "SONGS"}",
            textStartX,
            138f,
            subtitlePaint
        )

        // Glowing gradient frame around QR card
        val framePad = 14f
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
            strokeWidth = 8f
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
        canvas.drawRoundRect(frameRect, 44f, 44f, framePaint)

        // Clip rounded corners for QR pearl canvas & draw styled QR bitmap crisp
        canvas.save()
        val qrClip = Path().apply {
            addRoundRect(qrRect, 34f, 34f, Path.Direction.CW)
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
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(
            "SCAN IN KOI OR IMPORT IMAGE TO ADD PLAYLIST",
            width / 2f,
            headerHeight + qrSize + 68f,
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
