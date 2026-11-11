package com.koi

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.koi.backend.db.KoiDatabase
import com.koi.backend.innertube.InnerTubeClient
import com.koi.backend.lyrics.LyricsProvider
import com.koi.backend.player.PlayerEngine
import com.koi.backend.update.AppUpdateManager
import java.io.File

@OptIn(UnstableApi::class)
class KoiApplication : Application(), ImageLoaderFactory {

    lateinit var database: KoiDatabase
        private set

    lateinit var innerTube: InnerTubeClient
        private set

    lateinit var lyricsProvider: LyricsProvider
        private set

    lateinit var playerEngine: PlayerEngine
        private set

    lateinit var updateManager: AppUpdateManager
        private set

    var mediaCache: SimpleCache? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        initMediaCache()
        database = KoiDatabase(this)
        try {
            val prefs = getSharedPreferences("koi_prefs", MODE_PRIVATE)
            if (prefs.getInt("lyrics_cache_v6", 0) < 6) {
                database.clearLyricsCache()
                prefs.edit().putInt("lyrics_cache_v6", 6).apply()
            }
        } catch (_: Exception) {}
        innerTube = InnerTubeClient()
        lyricsProvider = LyricsProvider(innerTube = innerTube)
        playerEngine = PlayerEngine(this, innerTube, lyricsProvider, database)
        updateManager = AppUpdateManager(this, innerTube.okHttpClient)
    }

    private fun initMediaCache() {
        try {
            val cacheFolder = File(cacheDir, "media_cache")
            val databaseProvider = StandaloneDatabaseProvider(this)
            val evictor = LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024) // 250 MB LRU disk cache
            mediaCache = SimpleCache(cacheFolder, evictor, databaseProvider)
        } catch (e: Exception) {
            Log.e("KoiApplication", "Failed to initialize Media3 SimpleCache", e)
        }
    }

    fun clearMediaCache() {
        try {
            val cache = mediaCache ?: return
            val keys = cache.keys.toList()
            for (key in keys) {
                cache.removeResource(key)
            }
        } catch (e: Exception) {
            Log.w("KoiApplication", "Failed to clear mediaCache", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .okHttpClient { innerTube.okHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: KoiApplication
            private set
    }
}
