package com.koi.backend.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.koi.ui.R
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Custom BitmapLoader for MediaSession & Notifications.
 *
 * Transforms the media notification card into a tranquil Koi Pond aesthetic:
 * - Base background: Deep turquoise/teal aquatic pond water with concentric ripples and 2 colorful koi fish.
 * - Center disc: Floating circular album cover framed by glowing water ripples.
 * - Fallback / Offline: Pure high-resolution Koi Pond artwork with two swimming koi fish.
 */
@OptIn(UnstableApi::class)
class KoiNotificationBitmapLoader(
    private val context: Context
) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("koi_notif_prefs", Context.MODE_PRIVATE)

    // Cache recently composited bitmaps to prevent re-decoding
    private val bitmapCache = LruCache<String, Bitmap>(10)
    private var cachedPondBase: Bitmap? = null

    companion object {
        private const val TAG = "KoiNotifBitmap"
        const val PREF_NOTIF_STYLE = "koi_notif_style"
        const val STYLE_POND_WITH_DISC = 0
        const val STYLE_PURE_POND = 1
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return Util.isBitmapFactorySupportedMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (decoded != null) {
                    future.set(compositeWithKoiPond(decoded))
                } else {
                    future.set(getPondBaseBitmap())
                }
            } catch (e: Exception) {
                Log.w(TAG, "decodeBitmap fallback to pond", e)
                future.set(getPondBaseBitmap())
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val cacheKey = uri.toString()
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return Futures.immediateFuture(cached)
        }

        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val rawBitmap = loadBitmapFromUri(uri)
                if (rawBitmap != null) {
                    val composited = compositeWithKoiPond(rawBitmap)
                    bitmapCache.put(cacheKey, composited)
                    future.set(composited)
                } else {
                    future.set(getPondBaseBitmap())
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadBitmap error for $uri, fallback to pond", e)
                future.set(getPondBaseBitmap())
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val style = prefs.getInt(PREF_NOTIF_STYLE, STYLE_POND_WITH_DISC)
        if (style == STYLE_PURE_POND) {
            return Futures.immediateFuture(getPondBaseBitmap())
        }

        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }

        if (metadata.artworkUri != null) {
            return loadBitmap(metadata.artworkUri!!)
        }

        return Futures.immediateFuture(getPondBaseBitmap())
    }

    /**
     * Loads the base Koi Pond graphic from application resources.
     */
    private fun getPondBaseBitmap(): Bitmap {
        cachedPondBase?.let { if (!it.isRecycled) return it }
        val loaded = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.koi_notification_bg_square
        ) ?: Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#07181E"))
        }
        cachedPondBase = loaded
        return loaded
    }

    /**
     * Composites track album artwork onto the tranquil Koi Pond canvas.
     * The artwork floats in the center surrounded by concentric water ripples,
     * while the two colorful koi fish swim peacefully around it.
     */
    private fun compositeWithKoiPond(albumBitmap: Bitmap): Bitmap {
        val style = prefs.getInt(PREF_NOTIF_STYLE, STYLE_POND_WITH_DISC)
        val pond = getPondBaseBitmap()
        if (style == STYLE_PURE_POND) {
            return pond
        }

        try {
            val size = pond.width.coerceAtLeast(800)
            val result = pond.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            val discSize = (size * 0.46f).toInt()
            val radius = discSize / 2f
            val cx = size / 2f
            val cy = size / 2f

            // Concentric water ripples radiating around the floating album art
            val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
            }

            // Ripple 1
            ripplePaint.color = Color.argb(130, 72, 202, 228)
            ripplePaint.strokeWidth = 3.5f
            canvas.drawCircle(cx, cy, radius + 14f, ripplePaint)

            // Ripple 2
            ripplePaint.color = Color.argb(75, 72, 202, 228)
            ripplePaint.strokeWidth = 2.5f
            canvas.drawCircle(cx, cy, radius + 28f, ripplePaint)

            // Ripple 3
            ripplePaint.color = Color.argb(40, 72, 202, 228)
            ripplePaint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, radius + 44f, ripplePaint)

            // Circular album disc
            val circularDisc = Bitmap.createBitmap(discSize, discSize, Bitmap.Config.ARGB_8888)
            val discCanvas = Canvas(circularDisc)
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            discCanvas.drawCircle(radius, radius, radius, discPaint)
            discPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            val srcRect = Rect(0, 0, albumBitmap.width, albumBitmap.height)
            val dstRect = Rect(0, 0, discSize, discSize)
            discCanvas.drawBitmap(albumBitmap, srcRect, dstRect, discPaint)

            // Draw circular disc on top of pond center
            val left = (size - discSize) / 2f
            val top = (size - discSize) / 2f
            canvas.drawBitmap(circularDisc, left, top, null)

            // Subtle luminous aquatic rim around disc perimeter
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.color = Color.argb(190, 72, 202, 228)
                this.strokeWidth = 3f
            }
            canvas.drawCircle(cx, cy, radius, rimPaint)

            circularDisc.recycle()
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to composite with pond, using base pond", e)
            return pond
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        val scheme = uri.scheme ?: ""
        if (scheme == "file") {
            return uri.path?.let { BitmapFactory.decodeFile(it) }
        }
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            val url = URL(uri.toString())
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                return BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP fetch failed for $uri", e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
        return null
    }
}
