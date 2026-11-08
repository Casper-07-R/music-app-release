package com.koi.backend.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.koi.KoiApplication
import com.koi.backend.audio.AudioDspConfig
import com.koi.backend.audio.EightDAudioProcessor
import com.koi.backend.db.KoiDatabase
import com.koi.backend.innertube.InnerTubeClient
import com.koi.backend.innertube.SongItem
import com.koi.backend.lyrics.Lyrics
import com.koi.backend.lyrics.LyricsProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

@OptIn(UnstableApi::class)
class PlayerEngine(
    private val context: Context,
    private val innerTube: InnerTubeClient,
    private val lyricsProvider: LyricsProvider,
    private val database: KoiDatabase
) {
    companion object {
        private const val TAG = "PlayerEngine"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.e(TAG, "Uncaught coroutine exception in PlayerEngine", throwable)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + coroutineExceptionHandler)

    val eightDAudioProcessor = EightDAudioProcessor()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val wakeLock: PowerManager.WakeLock? = powerManager?.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "Koi:PlayerWakeLock"
    )?.apply {
        setReferenceCounted(false)
    }

    private val wifiLock: WifiManager.WifiLock? = wifiManager?.createWifiLock(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        },
        "Koi:PlayerWifiLock"
    )?.apply {
        setReferenceCounted(false)
    }

    private val playbackPrefs = context.getSharedPreferences("koi_playback_state", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun savePlaybackState(song: SongItem?, positionMs: Long, queue: List<SongItem>, queueIndex: Int) {
        try {
            playbackPrefs.edit().apply {
                if (song != null) {
                    putString("last_song_json", gson.toJson(song))
                    putLong("last_position_ms", positionMs)
                    putInt("last_queue_index", queueIndex)
                    putString("last_queue_json", gson.toJson(queue))
                }
                apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save playback state", e)
        }
    }

    fun restoreLastPlaybackState(): Boolean {
        try {
            val songJson = playbackPrefs.getString("last_song_json", null) ?: return false
            val song = gson.fromJson(songJson, SongItem::class.java) ?: return false
            val posMs = playbackPrefs.getLong("last_position_ms", 0L)
            val queueIndex = playbackPrefs.getInt("last_queue_index", 0)
            val queueJson = playbackPrefs.getString("last_queue_json", null)
            val queueList: List<SongItem> = if (queueJson != null) {
                try {
                    val type = object : TypeToken<List<SongItem>>() {}.type
                    gson.fromJson(queueJson, type) ?: listOf(song)
                } catch (e: Exception) {
                    listOf(song)
                }
            } else {
                listOf(song)
            }

            _currentTrack.value = song
            _currentPositionMs.value = posMs
            _queue.value = queueList
            _queueIndex.value = queueIndex
            Log.d(TAG, "Restored last playback state: ${song.title} at ${posMs}ms (queue size ${queueList.size})")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore playback state", e)
            return false
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock != null && !wakeLock.isHeld) {
                wakeLock.acquire()
            }
            if (wifiLock != null && !wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake/wifi lock", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (exoPlayer.playWhenReady || _isBuffering.value || resolveJob?.isActive == true) {
                return
            }
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
            if (wifiLock?.isHeld == true) {
                wifiLock.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake/wifi lock", e)
        }
    }

    private fun buildDataSourceFactory(userAgent: String): DataSource.Factory {
        val upstreamFactory = OkHttpDataSource.Factory(innerTube.streamingOkHttpClient)
            .setUserAgent(userAgent)
        val app = context.applicationContext as? KoiApplication
        val cache = app?.mediaCache
        return if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
    }

    val exoPlayer: ExoPlayer = run {
        // Force the highest supported bitrate across Opus & AAC audio streams
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setPreferredAudioMimeTypes(MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_AAC)
                .build()
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(eightDAudioProcessor))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // Robust buffer management to eliminate background throttling and ensure continuous playback
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60_000,
                /* maxBufferMs = */ 300_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val defaultDataSourceFactory = buildDataSourceFactory(InnerTubeClient.VISIONOS_USER_AGENT)
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun ensurePlaybackServiceStarted() {
        if (PlaybackService.isRunning) return
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start PlaybackService via startService, trying startForegroundService", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, PlaybackService::class.java))
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to start PlaybackService", e2)
            }
        }
    }

    // State flows
    private val _currentTrack = MutableStateFlow<SongItem?>(null)
    val currentTrack: StateFlow<SongItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private var progressJob: Job? = null
    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var recommendationsJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var lastSkipTimestamp = 0L

    private fun startBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = scope.launch {
            delay(8000)
            if (isActive && exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.playWhenReady) {
                val currentPos = if (_currentPositionMs.value > 0) _currentPositionMs.value else exoPlayer.currentPosition.coerceAtLeast(0L)
                Log.w(TAG, "Playback stuck in buffering for 8s, auto-nudge recovery at ${currentPos}ms...")
                if (currentPos > 0) {
                    exoPlayer.seekTo(currentPos)
                } else {
                    exoPlayer.prepare()
                }
                exoPlayer.play()
            }
        }
    }

    private fun stopBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    init {
        // Clean any expired stream caches and restore last playback state
        scope.launch(Dispatchers.IO) {
            database.cleanExpiredStreams()
        }
        restoreLastPlaybackState()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    acquireLocks()
                    startProgressPolling()
                    schedulePrefetchNextTrack()
                } else {
                    if (!exoPlayer.playWhenReady && !_isBuffering.value && resolveJob?.isActive != true) {
                        releaseLocks()
                    }
                    stopProgressPolling()
                    _currentTrack.value?.let {
                        savePlaybackState(it, _currentPositionMs.value, _queue.value, _queueIndex.value)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        acquireLocks()
                        startBufferingWatchdog()
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        stopBufferingWatchdog()
                        _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                            acquireLocks()
                        }
                    }
                    Player.STATE_ENDED -> {
                        // Do not auto-skip if a new track is already resolving or repeat-one is on
                        if (resolveJob?.isActive == true || _isRepeat.value) return
                        _isBuffering.value = true
                        stopBufferingWatchdog()
                        acquireLocks()
                        skipNext()
                    }
                    Player.STATE_IDLE -> {
                        stopBufferingWatchdog()
                        if (!exoPlayer.playWhenReady && resolveJob?.isActive != true) {
                            _isBuffering.value = false
                            releaseLocks()
                        }
                    }
                }
            }

            private var errorRetryCount = 0
            private var lastFailedVideoId: String? = null

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error occurred: ${error.errorCodeName} (${error.errorCode})", error)
                _isBuffering.value = false

                // Ignore stale errors from previous track if a new track is already resolving
                if (resolveJob?.isActive == true) {
                    Log.d(TAG, "Ignoring ExoPlayer error because a new track is already resolving")
                    return
                }

                val current = _currentTrack.value ?: return

                if (lastFailedVideoId == current.videoId && errorRetryCount >= 2) {
                    Log.w(TAG, "Max auto-recovery retries reached for ${current.title}, advancing to next track")
                    errorRetryCount = 0
                    skipNext()
                    return
                }

                if (lastFailedVideoId != current.videoId) {
                    lastFailedVideoId = current.videoId
                    errorRetryCount = 0
                }
                errorRetryCount++

                val resumePosMs = if (_currentPositionMs.value > 0) _currentPositionMs.value else exoPlayer.currentPosition.coerceAtLeast(0L)
                Log.d(TAG, "Auto-recovering (attempt $errorRetryCount): invalidating stream cache and retrying for ${current.title} at ${resumePosMs}ms...")
                acquireLocks()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.invalidateStream(current.videoId)
                        }
                        if (!isActive || resolveJob?.isActive == true) return@launch

                        val freshStream = innerTube.resolveStream(current.videoId)
                        if (!isActive || resolveJob?.isActive == true) return@launch

                        if (freshStream != null) {
                            withContext(Dispatchers.IO) {
                                database.cacheStream(freshStream)
                            }
                            Log.d(TAG, "Successfully refreshed stream for ${current.title}, replaying...")
                            val highResThumb = InnerTubeClient.getHighResThumbnailUrl(current.thumbnailUrl)
                            val metadata = MediaMetadata.Builder()
                                .setTitle(current.title)
                                .setArtist(current.artists)
                                .setAlbumTitle(current.album ?: "")
                                .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: current.thumbnailUrl?.let { Uri.parse(it) })
                                .build()

                            val mediaItem = MediaItem.Builder()
                                .setUri(freshStream.streamUrl)
                                .setMimeType(freshStream.mimeType)
                                .setMediaId(current.videoId)
                                .setCustomCacheKey(current.videoId)
                                .setMediaMetadata(metadata)
                                .build()

                            val dataSourceFactory = buildDataSourceFactory(freshStream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT)
                            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem)

                            exoPlayer.setMediaSource(mediaSource)
                            if (resumePosMs > 1000L) {
                                exoPlayer.seekTo(resumePosMs)
                            }
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } else {
                            Log.e(TAG, "Auto-recovery failed to resolve fresh stream for ${current.title}")
                            if (!exoPlayer.playWhenReady) {
                                releaseLocks()
                            }
                            skipNext()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during auto-recovery", e)
                    }
                }
            }
        })
    }

    private fun schedulePrefetchNextTrack() {
        prefetchJob?.cancel()
        val q = _queue.value
        if (q.size <= 1) return

        val nextIndex = if (_isShuffle.value) {
            -1
        } else {
            (_queueIndex.value + 1) % q.size
        }
        if (nextIndex < 0 || nextIndex >= q.size) return
        val nextSong = q[nextIndex]

        prefetchJob = scope.launch(Dispatchers.IO) {
            delay(5000)
            if (!isActive) return@launch

            val localPath = database.getDownloadedFilePath(nextSong.videoId)
            if (localPath != null && java.io.File(localPath).exists()) return@launch

            val cached = database.getCachedStream(nextSong.videoId)
            if (cached != null) return@launch

            Log.d(TAG, "Pre-fetching stream for next track: ${nextSong.title} (${nextSong.videoId})")
            val stream = innerTube.resolveStream(nextSong.videoId)
            if (stream != null) {
                database.cacheStream(stream)
                Log.d(TAG, "Pre-fetch complete for ${nextSong.title}")
            }
        }
    }

    fun playTrack(song: SongItem, newQueue: List<SongItem> = emptyList(), startPositionMs: Long = 0L) {
        resolveJob?.cancel()
        recommendationsJob?.cancel()

        // Immediately pause previous audio without clearing timeline so the notification remains persistent
        try {
            exoPlayer.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pause exoPlayer in playTrack", e)
        }

        val highResThumb = InnerTubeClient.getHighResThumbnailUrl(song.thumbnailUrl)
        val immediateMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artists)
            .setAlbumTitle(song.album ?: "")
            .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: song.thumbnailUrl?.let { Uri.parse(it) })
            .build()
        try {
            exoPlayer.setPlaylistMetadata(immediateMetadata)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set playlist metadata", e)
        }

        acquireLocks()

        // Update queue
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
            val idx = newQueue.indexOfFirst { it.videoId == song.videoId }
            _queueIndex.value = if (idx != -1) idx else 0
        } else if (_queue.value.isEmpty()) {
            _queue.value = listOf(song)
            _queueIndex.value = 0
        } else {
            val idx = _queue.value.indexOfFirst { it.videoId == song.videoId }
            if (idx != -1) {
                _queueIndex.value = idx
            } else {
                val updated = _queue.value.toMutableList().apply { add(song) }
                _queue.value = updated
                _queueIndex.value = updated.size - 1
            }
        }

        _currentTrack.value = song
        _currentLyrics.value = null
        _currentPositionMs.value = startPositionMs
        _isBuffering.value = true

        savePlaybackState(song, startPositionMs, _queue.value, _queueIndex.value)

        // Hold wake/wifi locks immediately so background execution & network aren't throttled
        acquireLocks()

        // Ensure background playback foreground service is running
        ensurePlaybackServiceStarted()

        // Record play in history
        scope.launch(Dispatchers.IO) {
            try {
                database.recordPlay(song)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record play", e)
            }
        }

        // Fetch stream and start playback
        resolveJob = scope.launch {
            try {
                // 0. Check local downloaded file first
                val localPath = withContext(Dispatchers.IO) { database.getDownloadedFilePath(song.videoId) }
                if (localPath != null && java.io.File(localPath).exists()) {
                    Log.d(TAG, "Playing ${song.title} from local download: $localPath at ${startPositionMs}ms")
                    val highResThumb = InnerTubeClient.getHighResThumbnailUrl(song.thumbnailUrl)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artists)
                        .setAlbumTitle(song.album ?: "")
                        .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: song.thumbnailUrl?.let { Uri.parse(it) })
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(java.io.File(localPath)))
                        .setMediaId(song.videoId)
                        .setCustomCacheKey(song.videoId)
                        .setMediaMetadata(metadata)
                        .build()

                    exoPlayer.setMediaItem(mediaItem)
                    if (startPositionMs > 1000L) {
                        exoPlayer.seekTo(startPositionMs)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()

                    if (isActive) {
                        launch(Dispatchers.IO) { fetchLyrics(song) }
                        recommendationsJob = launch(Dispatchers.IO) { fetchQueueRecommendations(song.videoId) }
                        schedulePrefetchNextTrack()
                    }
                    return@launch
                }

                // 1. Check stream cache first
                var stream = withContext(Dispatchers.IO) {
                    database.getCachedStream(song.videoId)
                }

                // 2. Resolve via InnerTube if not cached
                if (stream == null) {
                    stream = innerTube.resolveStream(song.videoId)
                    if (stream != null) {
                        withContext(Dispatchers.IO) {
                            database.cacheStream(stream)
                        }
                    }
                }

                if (!isActive) return@launch

                if (stream != null) {
                    Log.d(TAG, "Playing ${song.title} from ${stream.streamUrl.take(60)}... (resuming at ${startPositionMs}ms)")

                    val highResThumb = InnerTubeClient.getHighResThumbnailUrl(song.thumbnailUrl)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artists)
                        .setAlbumTitle(song.album ?: "")
                        .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: song.thumbnailUrl?.let { Uri.parse(it) })
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(stream.streamUrl)
                        .setMimeType(stream.mimeType)
                        .setMediaId(song.videoId)
                        .setCustomCacheKey(song.videoId)
                        .setMediaMetadata(metadata)
                        .build()

                    val dataSourceFactory = buildDataSourceFactory(stream.userAgent ?: InnerTubeClient.VISIONOS_USER_AGENT)
                    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)

                    exoPlayer.setMediaSource(mediaSource)
                    if (startPositionMs > 1000L) {
                        exoPlayer.seekTo(startPositionMs)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    Log.e(TAG, "Failed to resolve stream for ${song.title} (${song.videoId})")
                    _isBuffering.value = false
                    if (!exoPlayer.playWhenReady) {
                        releaseLocks()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving/playing stream", e)
                _isBuffering.value = false
                if (!exoPlayer.playWhenReady) {
                    releaseLocks()
                }
            }

            if (!isActive) return@launch

            // Concurrently fetch lyrics & queue recommendations
            launch(Dispatchers.IO) {
                fetchLyrics(song)
            }
            recommendationsJob = launch(Dispatchers.IO) {
                fetchQueueRecommendations(song.videoId)
            }
            schedulePrefetchNextTrack()
        }
    }

    private suspend fun fetchLyrics(song: SongItem) {
        try {
            // 1. Check local cache
            val cached = database.getCachedLyrics(song.videoId)
            if (cached != null) {
                _currentLyrics.value = cached
                return
            }

            // 2. Fetch from LRCLIB
            val lyrics = lyricsProvider.getLyrics(
                title = song.title,
                artist = song.artists,
                album = song.album,
                durationSeconds = song.durationSeconds
            )

            if (lyrics != null) {
                _currentLyrics.value = lyrics
                database.cacheLyrics(song.videoId, lyrics)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch lyrics for ${song.title}", e)
        }
    }

    private suspend fun fetchQueueRecommendations(videoId: String) {
        try {
            // If current queue only has this single track, populate with InnerTube next queue
            if (_queue.value.size <= 1) {
                val nextResult = innerTube.getNext(videoId)
                if (nextResult.items.isNotEmpty()) {
                    val combined = mutableListOf<SongItem>()
                    _currentTrack.value?.let { combined.add(it) }
                    for (item in nextResult.items) {
                        if (item.videoId != videoId && combined.none { it.videoId == item.videoId }) {
                            combined.add(item)
                        }
                    }
                    _queue.value = combined
                    schedulePrefetchNextTrack()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch recommendations for $videoId", e)
        }
    }

    fun togglePlayPause() {
        ensurePlaybackServiceStarted()
        val track = _currentTrack.value ?: _queue.value.firstOrNull()
        val savedPos = _currentPositionMs.value

        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
            if (track != null) {
                playTrack(track, _queue.value, startPositionMs = savedPos)
                return
            }
        }

        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            if (track != null) {
                val dur = _durationMs.value
                val resumePos = if (dur > 0 && savedPos >= dur - 3000L) 0L else savedPos
                playTrack(track, _queue.value, startPositionMs = resumePos)
                return
            }
        }

        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
            stopBufferingWatchdog()
            releaseLocks()
            track?.let { savePlaybackState(it, _currentPositionMs.value, _queue.value, _queueIndex.value) }
        } else {
            acquireLocks()
            // If was stuck in buffering, immediately un-stall the pipeline at current position
            if (exoPlayer.playbackState == Player.STATE_BUFFERING) {
                val pos = if (savedPos > 0) savedPos else exoPlayer.currentPosition
                if (pos > 0) {
                    exoPlayer.seekTo(pos)
                }
            }
            exoPlayer.play()
        }
    }

    fun seekTo(seconds: Float) {
        val posMs = (seconds * 1000).toLong()
        exoPlayer.seekTo(posMs)
        _currentPositionMs.value = posMs
        _currentTrack.value?.let { savePlaybackState(it, posMs, _queue.value, _queueIndex.value) }
    }

    fun skipNext() {
        val now = System.currentTimeMillis()
        if (now - lastSkipTimestamp < 200L) return
        lastSkipTimestamp = now

        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = if (_isShuffle.value) {
            if (q.size > 1) {
                var rand = (q.indices).random()
                while (rand == _queueIndex.value) {
                    rand = (q.indices).random()
                }
                rand
            } else 0
        } else {
            val cur = _queueIndex.value.coerceIn(0, q.size - 1)
            (cur + 1) % q.size
        }

        if (nextIndex in q.indices) {
            playTrack(q[nextIndex], q)
        }
    }

    fun skipPrevious() {
        val now = System.currentTimeMillis()
        if (now - lastSkipTimestamp < 200L) return
        lastSkipTimestamp = now

        val q = _queue.value
        if (q.isEmpty()) return

        // If played more than 3 seconds, replay current song
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            _currentPositionMs.value = 0L
            return
        }

        val cur = _queueIndex.value.coerceIn(0, q.size - 1)
        val prevIndex = if (cur > 0) cur - 1 else q.size - 1
        if (prevIndex in q.indices) {
            playTrack(q[prevIndex], q)
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
        exoPlayer.repeatMode = if (_isRepeat.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tick = 0
            while (isActive) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                _currentPositionMs.value = pos
                if (exoPlayer.duration > 0) {
                    _durationMs.value = exoPlayer.duration
                }
                tick++
                if (tick % 15 == 0) { // Every ~4.5s persist playback state
                    _currentTrack.value?.let {
                        savePlaybackState(it, pos, _queue.value, _queueIndex.value)
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun applyDspConfig(config: AudioDspConfig) {
        eightDAudioProcessor.config = config
        val pitchMultiplier = 2.0.pow(config.pitchSemitones.toDouble() / 12.0).toFloat()
        exoPlayer.playbackParameters = PlaybackParameters(
            config.playbackSpeed,
            if (config.lockPitchWithSpeed) 1.0f else pitchMultiplier
        )
    }

    fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    fun release() {
        stopBufferingWatchdog()
        prefetchJob?.cancel()
        releaseLocks()
        scope.cancel()
        exoPlayer.release()
    }
}
