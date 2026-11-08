package com.koi.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koi.KoiApplication
import com.koi.backend.audio.*
import com.koi.backend.innertube.SongItem
import com.koi.backend.update.AppUpdateManager
import com.koi.backend.update.GitHubAsset
import com.koi.backend.update.GitHubRelease
import com.koi.backend.update.UpdateUiState
import com.koi.ui.model.*
import com.koi.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UsageMetrics(
    val totalPlays: Int = 0,
    val totalLiked: Int = 0,
    val totalPlaylists: Int = 0,
    val cachedStreams: Int = 0,
    val cachedLyrics: Int = 0,
    val estimatedCacheSizeMb: Float = 0f,
    val listeningTimeSeconds: Long = 0L,
    val memoryUsedMb: Long = 0L,
    val memoryMaxMb: Long = 0L,
    val audioSessionId: Int = 0
)

data class PlaybackUiState(
    val currentTrack: Track,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionSeconds: Float = 0f,
    val isShuffleActive: Boolean = false,
    val isRepeatActive: Boolean = false,
    val isFullScreenPlayerExpanded: Boolean = false,
    val selectedTab: NavigationTab = NavigationTab.HOME,
    val selectedFilter: String = "All",
    val selectedPlaylist: Playlist? = null,
    val quickAccessItems: List<QuickAccessItem> = emptyList(),
    val recentlyPlayed: List<Album> = emptyList(),
    val madeForYou: List<Playlist> = emptyList(),
    val categories: List<GenreCategory> = emptyList(),
    val libraryPlaylists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val trackForPlaylistMenu: Track? = null,
    val isDownloaded: Boolean = false,
    val showEightDStudioSheet: Boolean = false,
    val showProfileSelectorSheet: Boolean = false,
    val sleepTimerRemainingSeconds: Int = 0,
    val soundStations: List<SoundStation> = emptyList(),
    val discoveryFeed: List<Track> = emptyList(),
    val showSleepTimerDialog: Boolean = false,
    val showEqualizerSheet: Boolean = false,
    val selectedEqPreset: String = "Flat",
    val showSettingsScreen: Boolean = false
)

class PlaybackViewModel : ViewModel() {

    private val app = KoiApplication.instance
    private val playerEngine = app.playerEngine
    private val innerTube = app.innerTube
    private val database = app.database

    private val defaultTrack = Track(
        id = "34Na4j8AVgA",
        title = "Starboy",
        artist = "The Weeknd, Daft Punk",
        album = "Starboy",
        durationSeconds = 230,
        thumbnailUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg",
        coverGradientStart = Color(0xFF1DB954),
        coverGradientEnd = Color(0xFF070709)
    )

    private val defaultCategories = listOf(
        GenreCategory("cat1", "Music", GenrePop),
        GenreCategory("cat2", "Podcasts", GenreHipHop),
        GenreCategory("cat3", "Live Events", GenreRock),
        GenreCategory("cat4", "Made For You", GenreIndie),
        GenreCategory("cat5", "New Releases", GenreDance),
        GenreCategory("cat6", "Pop", GenrePop),
        GenreCategory("cat7", "Hip-Hop", GenreHipHop),
        GenreCategory("cat8", "Rock", GenreRock),
        GenreCategory("cat9", "Dance / Electronic", GenreDance),
        GenreCategory("cat10", "Indie", GenreIndie),
        GenreCategory("cat11", "Workout", GenreWorkout),
        GenreCategory("cat12", "Chill", GenreChill),
        GenreCategory("cat13", "R&B", GenreRAndB),
        GenreCategory("cat14", "K-Pop", GenreKPop),
        GenreCategory("cat15", "Anime", GenreAnime),
        GenreCategory("cat16", "Jazz", GenreJazz)
    )

    private val defaultSoundStations = listOf(
        SoundStation("st_lofi", "Midnight Lo-Fi", "Chill 8D beats & rain ambiance", "LO-FI", "Lofi hip hop beats to relax"),
        SoundStation("st_synth", "Cyber Synthwave", "Retro analog overdrive & spatial depth", "SYNTH", "Synthwave retrowave electronic"),
        SoundStation("st_focus", "Deep Focus 16D", "Binaural frequencies & alpha waves", "FOCUS", "Binaural beats deep focus study"),
        SoundStation("st_radar", "Global Radar", "Hottest charting tracks right now", "TOP HITS", "Today's top hits global trending"),
        SoundStation("st_vocal", "Acoustic & Vocal", "Pristine vocal clarity & acoustic resonance", "VOCAL", "Acoustic vocal masterpieces unplugged"),
        SoundStation("st_nightcore", "Nightcore & Slowed", "Sped-up club edits and slowed reverb", "REMIX", "Nightcore slowed reverb trending")
    )

    private val initialTrack: Track = run {
        val restoredSong = playerEngine.currentTrack.value
        if (restoredSong != null) {
            Track.fromSongItem(
                item = restoredSong,
                isLiked = database.isLiked(restoredSong.videoId)
            )
        } else {
            defaultTrack
        }
    }

    private val _uiState = MutableStateFlow(
        PlaybackUiState(
            currentTrack = initialTrack,
            currentPositionSeconds = playerEngine.currentPositionMs.value / 1000f,
            categories = defaultCategories,
            soundStations = defaultSoundStations
        )
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var sessionListeningSeconds: Long = 0L
    private val _usageMetrics = MutableStateFlow(UsageMetrics())
    val usageMetrics: StateFlow<UsageMetrics> = _usageMetrics.asStateFlow()

    private val prefs = app.getSharedPreferences("koi_dsp_prefs", Context.MODE_PRIVATE)

    private val _dspConfig = MutableStateFlow(
        prefs.getString("last_dsp_config", null)?.let { json ->
            val loaded = AudioDspConfig.fromJson(json)
            if (loaded != null && loaded.activeProfile == AudioProfile.BALANCED) {
                val updatedSpeed = when (loaded.spatialMode) {
                    SpatialMode.MODE_16D -> 7.5f
                    else -> 11.0f
                }
                val updatedDepth = when (loaded.spatialMode) {
                    SpatialMode.MODE_16D -> 0.88f
                    else -> 0.80f
                }
                loaded.copy(
                    rotationSpeedSeconds = updatedSpeed,
                    panDepth = updatedDepth,
                    preGainDb = 2.0f,
                    trebleClarityDb = 1.1f,
                    bassBoostGainDb = 2.8f,
                    roomSize = 0.38f,
                    reverbLevel = if (loaded.is8dEnabled) 0.05f else 0.0f,
                    stereoWidth = if (loaded.is8dEnabled) 1.14f else 1.0f,
                    crossfeed = false,
                    crossfeedAmount = 0.0f,
                    distanceFrontBackCue = false
                )
            } else {
                loaded
            }
        } ?: AudioDspConfig.createConfig(SpatialMode.OFF, AudioProfile.BALANCED)
    )
    val dspConfig: StateFlow<AudioDspConfig> = _dspConfig.asStateFlow()

    val rotationAngleDegrees: StateFlow<Float> = playerEngine.eightDAudioProcessor.rotationAngleDegrees
    val spectrumBands: StateFlow<FloatArray> = playerEngine.eightDAudioProcessor.spectrumBands

    private val _customPresets = MutableStateFlow<Map<String, AudioDspConfig>>(emptyMap())
    val customPresets: StateFlow<Map<String, AudioDspConfig>> = _customPresets.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var searchJob: Job? = null

    init {
        database.clearCustomPresets()
        playerEngine.applyDspConfig(_dspConfig.value)
        prefs.edit().putString("last_dsp_config", _dspConfig.value.toJson()).apply()

        // Collect PlayerEngine states
        viewModelScope.launch {
            playerEngine.currentTrack.collect { song ->
                if (song != null) {
                    val isLiked = database.isLiked(song.videoId)
                    val isDownloaded = database.isDownloaded(song.videoId)
                    val lyrics = playerEngine.currentLyrics.value
                    _uiState.update {
                        it.copy(
                            currentTrack = Track.fromSongItem(
                                item = song,
                                isLiked = isLiked,
                                syncedLyrics = lyrics?.lines ?: emptyList()
                            ),
                            isDownloaded = isDownloaded
                        )
                    }
                    if (_dspConfig.value.perTrackMemoryEnabled) {
                        val saved = database.getTrackSettings(song.videoId)
                        if (saved != null) {
                            AudioDspConfig.fromJson(saved)?.let { updateDspConfig(it) }
                        }
                    }
                    refreshUsageMetrics()
                }
            }
        }

        viewModelScope.launch {
            playerEngine.currentLyrics.collect { lyrics ->
                val track = _uiState.value.currentTrack
                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            currentTrack = track.copy(
                                lyrics = lyrics.lines.map { line -> line.text },
                                syncedLyrics = lyrics.lines
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            playerEngine.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }

        viewModelScope.launch {
            playerEngine.isBuffering.collect { buffering ->
                _uiState.update { it.copy(isBuffering = buffering) }
            }
        }

        viewModelScope.launch {
            playerEngine.currentPositionMs.collect { posMs ->
                _uiState.update { it.copy(currentPositionSeconds = posMs / 1000f) }
            }
        }

        viewModelScope.launch {
            playerEngine.durationMs.collect { durMs ->
                if (durMs > 0) {
                    val track = _uiState.value.currentTrack
                    _uiState.update {
                        it.copy(currentTrack = track.copy(durationSeconds = (durMs / 1000).toInt()))
                    }
                }
            }
        }

        viewModelScope.launch {
            playerEngine.isShuffle.collect { shuffle ->
                _uiState.update { it.copy(isShuffleActive = shuffle) }
            }
        }

        viewModelScope.launch {
            playerEngine.isRepeat.collect { repeat ->
                _uiState.update { it.copy(isRepeatActive = repeat) }
            }
        }

        // Realtime session listening timer
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isPlaying) {
                    sessionListeningSeconds++
                    if (sessionListeningSeconds % 5 == 0L) {
                        _usageMetrics.update { it.copy(listeningTimeSeconds = sessionListeningSeconds) }
                    }
                }
                delay(1000)
            }
        }

        // Load data from InnerTube & Database
        refreshLibrary()
        loadHomeData()
        refreshUsageMetrics()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // InnerTube Home
            val homeResult = innerTube.getHome()
            if (homeResult.sections.isNotEmpty()) {
                val shelves = homeResult.sections.map { section ->
                    Playlist(
                        id = "shelf_${section.title.hashCode()}",
                        title = section.title,
                        description = "Recommended for you",
                        trackCount = section.items.size,
                        thumbnailUrl = section.items.firstOrNull()?.thumbnailUrl,
                        creator = "YouTube Music",
                        tracks = section.items.map { it.toTrack(database.isLiked(it.videoId)) }
                    )
                }

                val allFeedTracks = homeResult.sections
                    .flatMap { it.items }
                    .distinctBy { it.videoId }
                    .take(25)
                    .map { it.toTrack(database.isLiked(it.videoId)) }

                val quickAccess = homeResult.sections.firstOrNull()?.items?.take(6)?.mapIndexed { idx, s ->
                    QuickAccessItem(
                        id = s.videoId,
                        title = s.title,
                        thumbnailUrl = s.thumbnailUrl,
                        isLikedSongs = idx == 0
                    )
                } ?: emptyList()

                _uiState.update {
                    it.copy(
                        madeForYou = shelves,
                        quickAccessItems = if (quickAccess.isNotEmpty()) quickAccess else it.quickAccessItems,
                        discoveryFeed = allFeedTracks,
                        soundStations = defaultSoundStations
                    )
                }
            } else {
                // Fallback default search to populate Home
                val defaultSongs = innerTube.searchSongs("Top Hits 2024")
                if (defaultSongs.isNotEmpty()) {
                    val tracks = defaultSongs.map { it.toTrack(database.isLiked(it.videoId)) }
                    val topShelf = Playlist(
                        id = "shelf_tophits",
                        title = "Today's Top Hits",
                        description = "The hottest tracks right now",
                        trackCount = tracks.size,
                        thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
                        creator = "Koi",
                        tracks = tracks
                    )
                    _uiState.update {
                        it.copy(
                            madeForYou = listOf(topShelf),
                            discoveryFeed = tracks,
                            soundStations = defaultSoundStations
                        )
                    }
                }
            }
        }
    }

    fun playStation(station: SoundStation) {
        viewModelScope.launch {
            val songs = innerTube.searchSongs(station.searchQuery)
            if (songs.isNotEmpty()) {
                val tracks = songs.map { it.toTrack(database.isLiked(it.videoId)) }
                val stationPlaylist = Playlist(
                    id = "station_${station.id}",
                    title = station.title,
                    description = station.subtitle,
                    trackCount = tracks.size,
                    thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
                    creator = "Koi Station",
                    tracks = tracks
                )
                openPlaylist(stationPlaylist)
                playTrack(tracks.first(), tracks)
            }
        }
    }

    fun openSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = true) }
    }

    fun closeSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = false) }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val localPlaylists = database.getAllPlaylists()
            val likedSongs = database.getLikedSongs()

            val likedPlaylist = Playlist(
                id = "liked_songs",
                title = "Liked Songs",
                description = "${likedSongs.size} songs you love",
                trackCount = likedSongs.size,
                isPinned = true,
                isLikedSongs = true,
                thumbnailUrl = likedSongs.firstOrNull()?.thumbnailUrl,
                coverGradientStart = LikedSongsGradientStart,
                coverGradientEnd = LikedSongsGradientEnd,
                creator = "You",
                tracks = likedSongs.map { it.toTrack(isLiked = true) }
            )

            val customPlaylists = localPlaylists.map { info ->
                val tracks = database.getPlaylistTracks(info.id)
                Playlist(
                    id = info.id,
                    title = info.title,
                    description = "${tracks.size} songs",
                    trackCount = tracks.size,
                    thumbnailUrl = info.coverUrl,
                    coverGradientStart = Color(0xFF1DB954),
                    coverGradientEnd = Color(0xFF070709),
                    creator = "You",
                    tracks = tracks.map { it.toTrack(database.isLiked(it.videoId)) }
                )
            }

            val all = mutableListOf<Playlist>().apply {
                add(likedPlaylist)
                addAll(customPlaylists)
            }

            _uiState.update { it.copy(libraryPlaylists = all) }
        }
    }

    // --- PLAYBACK CONTROLS ---

    fun playTrack(track: Track, queue: List<Track> = emptyList(), startPositionMs: Long = 0L) {
        val songItem = track.toSongItem()
        val queueItems = if (queue.isNotEmpty()) {
            queue.map { it.toSongItem() }
        } else {
            listOf(songItem)
        }
        playerEngine.playTrack(songItem, queueItems, startPositionMs)
    }

    fun togglePlayPause() {
        if (playerEngine.currentTrack.value == null) {
            playTrack(_uiState.value.currentTrack)
        } else {
            playerEngine.togglePlayPause()
        }
    }

    fun skipNext() {
        playerEngine.skipNext()
    }

    fun skipPrevious() {
        playerEngine.skipPrevious()
    }

    fun seekTo(seconds: Float) {
        playerEngine.seekTo(seconds)
    }

    fun toggleLike(targetTrack: Track? = null) {
        val track = targetTrack ?: _uiState.value.currentTrack
        val isNowLiked = database.toggleLike(track.toSongItem())
        if (targetTrack == null || targetTrack.id == _uiState.value.currentTrack.id) {
            _uiState.update { it.copy(currentTrack = it.currentTrack.copy(isLiked = isNowLiked)) }
        }
        refreshLibrary()
    }

    fun toggleShuffle() {
        playerEngine.toggleShuffle()
    }

    fun toggleRepeat() {
        playerEngine.toggleRepeat()
    }

    // --- SEARCH ---

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _uiState.update { it.copy(isSearching = true) }
            val songs = innerTube.searchSongs(query)
            val tracks = songs.map { it.toTrack(database.isLiked(it.videoId)) }
            _uiState.update { it.copy(searchResults = tracks, isSearching = false) }
        }
    }

    // --- PLAYLIST MANAGEMENT ---

    fun openCreatePlaylistDialog() {
        _uiState.update { it.copy(showCreatePlaylistDialog = true) }
    }

    fun closeCreatePlaylistDialog() {
        _uiState.update { it.copy(showCreatePlaylistDialog = false) }
    }

    fun createPlaylist(title: String) {
        if (title.isBlank()) return
        database.createPlaylist(title.trim())
        closeCreatePlaylistDialog()
        refreshLibrary()
    }

    fun deletePlaylist(playlistId: String) {
        database.deletePlaylist(playlistId)
        if (_uiState.value.selectedPlaylist?.id == playlistId) {
            closePlaylist()
        }
        refreshLibrary()
    }

    fun openAddToPlaylistDialog(track: Track) {
        _uiState.update { it.copy(trackForPlaylistMenu = track) }
    }

    fun closeAddToPlaylistDialog() {
        _uiState.update { it.copy(trackForPlaylistMenu = null) }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        database.addTrackToPlaylist(playlistId, track.toSongItem())
        closeAddToPlaylistDialog()
        refreshLibrary()
        // If current playlist is open, refresh it
        if (_uiState.value.selectedPlaylist?.id == playlistId) {
            val updatedTracks = database.getPlaylistTracks(playlistId).map { it.toTrack(database.isLiked(it.videoId)) }
            _uiState.update {
                it.copy(
                    selectedPlaylist = it.selectedPlaylist?.copy(
                        tracks = updatedTracks,
                        trackCount = updatedTracks.size
                    )
                )
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, track: Track) {
        database.removeTrackFromPlaylist(playlistId, track.id)
        refreshLibrary()
        if (_uiState.value.selectedPlaylist?.id == playlistId) {
            val updatedTracks = database.getPlaylistTracks(playlistId).map { it.toTrack(database.isLiked(it.videoId)) }
            _uiState.update {
                it.copy(
                    selectedPlaylist = it.selectedPlaylist?.copy(
                        tracks = updatedTracks,
                        trackCount = updatedTracks.size
                    )
                )
            }
        }
    }

    // --- UI NAVIGATION ---

    fun openFullScreenPlayer() {
        _uiState.update { it.copy(isFullScreenPlayerExpanded = true) }
    }

    fun closeFullScreenPlayer() {
        _uiState.update { it.copy(isFullScreenPlayerExpanded = false) }
    }

    fun selectTab(tab: NavigationTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedPlaylist = null, showSettingsScreen = false) }
        if (tab == NavigationTab.PROFILE) {
            refreshUsageMetrics()
        }
    }

    fun refreshUsageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val totalPlays = database.getTotalPlaysCount()
            val totalLiked = database.getLikedSongsCount()
            val totalPlaylists = database.getPlaylistsCount()
            val cachedStreams = database.getCachedStreamsCount()
            val cachedLyrics = database.getCachedLyricsCount()

            var cacheBytes = 0L
            try {
                app.cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile) cacheBytes += file.length()
                }
            } catch (e: Exception) {}

            val estimatedTotalBytes = cacheBytes + (cachedStreams + cachedLyrics) * 16384L
            val cacheMb = (estimatedTotalBytes / (1024f * 1024f)).coerceAtLeast(0.1f)

            val rt = Runtime.getRuntime()
            val usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val maxMem = rt.maxMemory() / (1024 * 1024)

            val audioSessionId = withContext(Dispatchers.Main) {
                try {
                    playerEngine.exoPlayer.audioSessionId
                } catch (e: Exception) {
                    0
                }
            }

            _usageMetrics.update {
                it.copy(
                    totalPlays = totalPlays,
                    totalLiked = totalLiked,
                    totalPlaylists = totalPlaylists,
                    cachedStreams = cachedStreams,
                    cachedLyrics = cachedLyrics,
                    estimatedCacheSizeMb = cacheMb,
                    listeningTimeSeconds = sessionListeningSeconds,
                    memoryUsedMb = usedMem,
                    memoryMaxMb = maxMem,
                    audioSessionId = audioSessionId
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllCache()
            try {
                app.clearMediaCache()
                app.cacheDir.listFiles()?.forEach { file ->
                    if (file.name != "media_cache") {
                        file.deleteRecursively()
                    }
                }
            } catch (e: Exception) {}
            refreshUsageMetrics()
        }
    }

    fun selectFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = if (playlist.isLikedSongs) {
                database.getLikedSongs().map { it.toTrack(isLiked = true) }
            } else if (playlist.id.startsWith("shelf_")) {
                playlist.tracks
            } else {
                database.getPlaylistTracks(playlist.id).map { it.toTrack(database.isLiked(it.videoId)) }
            }

            _uiState.update {
                it.copy(
                    selectedPlaylist = playlist.copy(
                        tracks = tracks,
                        trackCount = tracks.size
                    ),
                    showSettingsScreen = false
                )
            }
        }
    }

    fun closePlaylist() {
        _uiState.update { it.copy(selectedPlaylist = null) }
    }

    // --- 8D AUDIO & DSP METHODS ---

    fun getProfileConfig(profile: AudioProfile, mode: SpatialMode = _dspConfig.value.spatialMode): AudioDspConfig {
        val targetMode = if (mode == SpatialMode.OFF) SpatialMode.MODE_8D else mode
        val key = "dsp_config_${targetMode.name}_${profile.name}"
        val savedJson = prefs.getString(key, null)
        if (savedJson != null) {
            val parsed = AudioDspConfig.fromJson(savedJson)
            if (parsed != null) return parsed
        }
        return AudioDspConfig.createConfig(targetMode, profile)
    }

    fun updateDspConfig(newConfig: AudioDspConfig) {
        _dspConfig.value = newConfig
        playerEngine.applyDspConfig(newConfig)
        prefs.edit().putString("last_dsp_config", newConfig.toJson()).apply()
        if (newConfig.spatialMode != SpatialMode.OFF) {
            val key = "dsp_config_${newConfig.spatialMode.name}_${newConfig.activeProfile.name}"
            prefs.edit().putString(key, newConfig.toJson()).apply()
        }
        if (newConfig.perTrackMemoryEnabled) {
            val trackId = _uiState.value.currentTrack.id
            if (trackId.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    database.saveTrackSettings(trackId, newConfig.toJson())
                }
            }
        }
    }

    fun cycleSpatialMode() {
        val nextMode = when (_dspConfig.value.spatialMode) {
            SpatialMode.OFF -> SpatialMode.MODE_8D
            SpatialMode.MODE_8D -> SpatialMode.MODE_16D
            SpatialMode.MODE_16D -> SpatialMode.OFF
        }
        setSpatialMode(nextMode)
    }

    fun setSpatialMode(mode: SpatialMode) {
        val currentProfile = _dspConfig.value.activeProfile
        val newConfig = if (mode == SpatialMode.OFF) {
            AudioDspConfig.createConfig(mode, currentProfile)
        } else {
            getProfileConfig(currentProfile, mode)
        }
        updateDspConfig(newConfig)
    }

    fun selectProfile(profile: AudioProfile) {
        val currentMode = _dspConfig.value.spatialMode
        val newConfig = getProfileConfig(profile, currentMode)
        updateDspConfig(newConfig)
    }

    fun resetProfile(profile: AudioProfile, mode: SpatialMode = _dspConfig.value.spatialMode) {
        val targetMode = if (mode == SpatialMode.OFF) SpatialMode.MODE_8D else mode
        val defaultConfig = AudioDspConfig.createConfig(targetMode, profile)
        prefs.edit()
            .remove("dsp_config_${targetMode.name}_${profile.name}")
            .apply()

        val toApply = if (_dspConfig.value.spatialMode == SpatialMode.OFF) {
            defaultConfig.copy(spatialMode = SpatialMode.OFF, is8dEnabled = false)
        } else {
            defaultConfig
        }
        updateDspConfig(toApply)
    }

    fun resetCurrentProfile() {
        resetProfile(_dspConfig.value.activeProfile, _dspConfig.value.spatialMode)
    }

    fun openProfileSelectorSheet() {
        _uiState.update { it.copy(showProfileSelectorSheet = true) }
    }

    fun closeProfileSelectorSheet() {
        _uiState.update { it.copy(showProfileSelectorSheet = false) }
    }

    fun toggle8d() {
        cycleSpatialMode()
    }

    fun toggleMasterEffects() {
        updateDspConfig(_dspConfig.value.copy(masterEnabled = !_dspConfig.value.masterEnabled))
    }

    fun toggleAbBypass() {
        updateDspConfig(_dspConfig.value.copy(abBypass = !_dspConfig.value.abBypass))
    }

    fun toggleSafeMode() {
        updateDspConfig(_dspConfig.value.copy(safeMode = !_dspConfig.value.safeMode))
    }

    fun resetToFlat() {
        updateDspConfig(AudioDspConfig.createConfig(SpatialMode.OFF, AudioProfile.BALANCED))
    }

    fun openEightDStudioSheet() {
        _uiState.update { it.copy(showEightDStudioSheet = true) }
    }

    fun closeEightDStudioSheet() {
        _uiState.update { it.copy(showEightDStudioSheet = false) }
    }

    fun openEqualizerSheet() {
        _uiState.update { it.copy(showEqualizerSheet = true) }
    }

    fun closeEqualizerSheet() {
        _uiState.update { it.copy(showEqualizerSheet = false) }
    }

    fun selectEqPreset(presetName: String) {
        _uiState.update { it.copy(selectedEqPreset = presetName) }
        when (presetName) {
            "Flat" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 0f, trebleClarityDb = 0f))
            "Bass Boost" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 6.0f, trebleClarityDb = 1.0f, bassBoostEnabled = true))
            "Bass Reducer" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 0f, trebleClarityDb = 2.0f, bassBoostEnabled = false))
            "Vocal Clarity" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 1.5f, trebleClarityDb = 4.0f))
            "Treble Boost" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 1.0f, trebleClarityDb = 5.5f))
            "Electronic" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 5.0f, trebleClarityDb = 3.5f))
            "Rock" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 4.5f, trebleClarityDb = 3.0f))
            "Hip-Hop" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 7.0f, trebleClarityDb = 2.0f))
            "Acoustic" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 2.0f, trebleClarityDb = 3.0f))
            "Jazz" -> updateDspConfig(_dspConfig.value.copy(bassBoostGainDb = 3.0f, trebleClarityDb = 2.5f))
        }
    }

    // --- SLEEP TIMER WITH VOLUME FADEOUT ---

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingSeconds = 0) }
            playerEngine.setVolume(1.0f)
            return
        }
        sleepTimerJob = viewModelScope.launch {
            var totalSecs = minutes * 60
            while (totalSecs > 0) {
                _uiState.update { it.copy(sleepTimerRemainingSeconds = totalSecs) }
                // Smooth volume fade during the last 60 seconds
                if (totalSecs <= 60) {
                    val fadeFraction = totalSecs / 60.0f
                    playerEngine.setVolume(fadeFraction)
                }
                delay(1000)
                totalSecs--
            }
            _uiState.update { it.copy(sleepTimerRemainingSeconds = 0) }
            withContext(Dispatchers.Main) {
                playerEngine.setVolume(0.0f)
                if (_uiState.value.isPlaying) {
                    playerEngine.togglePlayPause()
                }
                delay(500)
                playerEngine.setVolume(1.0f)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(0)
    }

    // --- DOWNLOAD MANAGEMENT ---

    fun toggleDownload(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            if (database.isDownloaded(track.id)) {
                database.removeDownload(track.id)
                try {
                    val file = File(app.filesDir, "downloads/${track.id}.mp3")
                    if (file.exists()) file.delete()
                } catch (e: Exception) {}
                _uiState.update { it.copy(isDownloaded = false) }
            } else {
                try {
                    val cachedStream = database.getCachedStream(track.id)
                    val url = cachedStream?.streamUrl ?: innerTube.resolveStream(track.id)?.streamUrl
                    if (url != null) {
                        val downloadsDir = File(app.filesDir, "downloads").apply { mkdirs() }
                        val file = File(downloadsDir, "${track.id}.mp3")
                        val request = okhttp3.Request.Builder().url(url).build()
                        val response = innerTube.okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            database.saveDownload(track.toSongItem(), file.absolutePath)
                            _uiState.update { it.copy(isDownloaded = true) }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlaybackViewModel", "Download failed for ${track.id}", e)
                }
            }
        }
    }

    private fun SongItem.toTrack(isLiked: Boolean): Track {
        return Track.fromSongItem(this, isLiked)
    }

    // --- APP UPDATE MANAGEMENT ---

    val updateState: StateFlow<UpdateUiState> = app.updateManager.state

    fun checkForUpdate() {
        app.updateManager.checkForUpdate()
    }

    fun downloadUpdate(release: GitHubRelease, apkAsset: GitHubAsset, context: Context) {
        app.updateManager.downloadUpdate(release, apkAsset, context)
    }

    fun cancelUpdateDownload() {
        app.updateManager.cancelDownload()
    }

    fun installUpdate(context: Context, apkFile: File) {
        app.updateManager.installApk(context, apkFile)
    }

    // --- SETTINGS NAVIGATION & CONTROLS ---

    fun openSettings() {
        _uiState.update { it.copy(showSettingsScreen = true, selectedPlaylist = null) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsScreen = false) }
    }

    fun toggleMasterDsp() {
        val current = _dspConfig.value
        updateDspConfig(current.copy(masterEnabled = !current.masterEnabled))
    }
}
