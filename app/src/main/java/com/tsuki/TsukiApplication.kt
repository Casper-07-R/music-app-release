package com.tsuki

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
import com.tsuki.backend.db.TsukiDatabase
import com.tsuki.backend.innertube.InnerTubeClient
import com.tsuki.backend.lyrics.LyricsProvider
import com.tsuki.backend.player.PlayerEngine
import java.io.File

@OptIn(UnstableApi::class)
class TsukiApplication : Application(), ImageLoaderFactory {

    lateinit var database: TsukiDatabase
        private set

    lateinit var innerTube: InnerTubeClient
        private set

    lateinit var lyricsProvider: LyricsProvider
        private set

    lateinit var playerEngine: PlayerEngine
        private set

    var mediaCache: SimpleCache? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        initMediaCache()
        database = TsukiDatabase(this)
        innerTube = InnerTubeClient()
        lyricsProvider = LyricsProvider()
        playerEngine = PlayerEngine(this, innerTube, lyricsProvider, database)
    }

    private fun initMediaCache() {
        try {
            val cacheFolder = File(cacheDir, "media_cache")
            val databaseProvider = StandaloneDatabaseProvider(this)
            val evictor = LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024) // 250 MB LRU disk cache
            mediaCache = SimpleCache(cacheFolder, evictor, databaseProvider)
        } catch (e: Exception) {
            Log.e("TsukiApplication", "Failed to initialize Media3 SimpleCache", e)
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
            Log.w("TsukiApplication", "Failed to clear mediaCache", e)
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
        lateinit var instance: TsukiApplication
            private set
    }
}
