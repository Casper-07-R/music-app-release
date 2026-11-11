package com.koi.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koi.KoiApplication
import com.koi.backend.audio.*
import com.koi.backend.download.*
import com.koi.backend.innertube.*
import com.koi.backend.profile.UserProfileManager
import com.koi.backend.update.AppUpdateManager
import com.koi.backend.update.GitHubAsset
import com.koi.backend.update.GitHubRelease
import com.koi.backend.update.UpdateUiState
import com.koi.ui.model.*
import com.koi.ui.theme.*
import kotlinx.coroutines.CancellationException
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

data class ActiveDownloadState(
    val isDownloading: Boolean = false,
    val currentItemTitle: String = "",
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val progressFraction: Float = 0f
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
    val searchFilter: SearchFilterType = SearchFilterType.ALL,
    val comprehensiveSearchResult: ComprehensiveSearchResult = ComprehensiveSearchResult(),
    val artistFilterResults: List<ArtistItem> = emptyList(),
    val playlistFilterResults: List<PlaylistItem> = emptyList(),
    val currentArtistProfile: ArtistProfile? = null,
    val isLoadingArtistProfile: Boolean = false,
    val artistStack: List<String> = emptyList(),
    val followedArtistIds: Set<String> = emptySet(),
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val isSearching: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val trackForPlaylistMenu: Track? = null,
    val isDownloaded: Boolean = false,
    val showEightDStudioSheet: Boolean = false,
    val showProfileSelectorSheet: Boolean = false,
    val isFindingLyrics: Boolean = false,
    val sleepTimerRemainingSeconds: Int = 0,
    val soundStations: List<SoundStation> = emptyList(),
    val discoveryFeed: List<Track> = emptyList(),
    val showSleepTimerDialog: Boolean = false,
    val showEqualizerSheet: Boolean = false,
    val selectedEqPreset: String = "Flat",
    val showSettingsScreen: Boolean = false,
    val showDownloadManager: Boolean = false,
    val downloadedSongs: List<Track> = emptyList(),
    val downloadedPlaylists: List<Playlist> = emptyList(),
    val downloadedSongIds: Set<String> = emptySet(),
    val downloadedPlaylistIds: Set<String> = emptySet(),
    val activeDownload: ActiveDownloadState = ActiveDownloadState(),
    val totalDownloadSizeBytes: Long = 0L,
    val showAddSongsSheet: Boolean = false,
    val playlistSearchQuery: String = "",
    val playlistSearchResults: List<Track> = emptyList(),
    val isSearchingForPlaylist: Boolean = false,
    val showEditPlaylistDialog: Boolean = false,
    val playlistToEdit: Playlist? = null,
    val showDownloadOptionsSheet: Boolean = false,
    val downloadOptionsTargetTrack: Track? = null,
    val downloadOptionsTargetPlaylist: Playlist? = null,
    val preferredDownloadFormat: AudioFormat = AudioFormat.M4A,
    val preferredDownloadQuality: AudioQuality = AudioQuality.HIGH,
    val username: String = UserProfileManager.DEFAULT_USERNAME,
    val showInitialUsernameDialog: Boolean = false,
    val isUpdateDialogDismissed: Boolean = false
)

class PlaybackViewModel : ViewModel() {

    private val app = KoiApplication.instance
    private val playerEngine = app.playerEngine
    private val innerTube = app.innerTube
    private val database = app.database
    private val lyricsProvider = app.lyricsProvider
    private val downloaderEngine = DownloaderEngine(app, innerTube, database)

    private val defaultTrack = Track(
        id = "34Na4j8AVgA",
        title = "Starboy",
        artist = "The Weeknd, Daft Punk",
        album = "Starboy",
        durationSeconds = 230,
        thumbnailUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg",
        coverGradientStart = Color(0xFFFF6B35),
        coverGradientEnd = Color(0xFF07181E)
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
            soundStations = defaultSoundStations,
            username = UserProfileManager.getUsername(app),
            showInitialUsernameDialog = UserProfileManager.shouldPromptInitialUsername(app)
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
    private var playlistSearchJob: Job? = null

    init {
        database.clearCustomPresets()
        playerEngine.applyDspConfig(_dspConfig.value)
        prefs.edit().putString("last_dsp_config", _dspConfig.value.toJson()).apply()
        loadSearchHistory()
        viewModelScope.launch(Dispatchers.IO) {
            val followed = database.getFollowedArtistIds()
            _uiState.update { it.copy(followedArtistIds = followed) }
        }

        // Collect PlayerEngine states
        viewModelScope.launch {
            playerEngine.currentTrack.collect { song ->
                if (song != null) {
                    val isLiked = database.isLiked(song.videoId)
                    val isDownloaded = database.isDownloaded(song.videoId)
                    val cached = database.getCachedLyrics(song.videoId)
                    val currentLyrics = if (cached != null) {
                        cached.copy(videoId = song.videoId)
                    } else {
                        playerEngine.currentLyrics.value
                    }
                    val matchingLyrics = if (currentLyrics?.videoId == song.videoId) {
                        lyricsProvider.ensureLiveLyrics(currentLyrics, song.durationSeconds)
                    } else null
                    _uiState.update { state ->
                        state.copy(
                            currentTrack = Track.fromSongItem(
                                item = song,
                                isLiked = isLiked,
                                isDownloaded = isDownloaded,
                                syncedLyrics = matchingLyrics?.lines ?: emptyList()
                            ).copy(
                                lyrics = matchingLyrics?.lines?.map { it.text } ?: emptyList()
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
                _uiState.update { state ->
                    val curTrack = state.currentTrack
                    if (lyrics != null && (lyrics.videoId.isEmpty() || lyrics.videoId == curTrack.id)) {
                        val liveLyrics = lyricsProvider.ensureLiveLyrics(lyrics, curTrack.durationSeconds)
                        state.copy(
                            currentTrack = curTrack.copy(
                                lyrics = liveLyrics.lines.map { line -> line.text },
                                syncedLyrics = liveLyrics.lines
                            )
                        )
                    } else if (lyrics == null) {
                        state.copy(
                            currentTrack = curTrack.copy(
                                lyrics = emptyList(),
                                syncedLyrics = emptyList()
                            )
                        )
                    } else {
                        state
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

        viewModelScope.launch {
            playerEngine.isFindingLyrics.collect { finding ->
                _uiState.update { it.copy(isFindingLyrics = finding) }
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
        refreshDownloads()
        loadHomeData()
        refreshUsageMetrics()

        // Background update check on app launch
        checkForUpdate()
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
                    description = info.description ?: "${tracks.size} songs",
                    trackCount = tracks.size,
                    thumbnailUrl = info.coverUrl,
                    coverGradientStart = Color(0xFFFF6B35),
                    coverGradientEnd = Color(0xFF07181E),
                    creator = "You",
                    tracks = tracks.map { it.toTrack(database.isLiked(it.videoId)) }
                )
            }

            val all = mutableListOf<Playlist>().apply {
                add(likedPlaylist)
                addAll(customPlaylists)
            }

            _uiState.update { state ->
                val updatedSelected = if (state.selectedPlaylist != null) {
                    all.find { it.id == state.selectedPlaylist.id } ?: state.selectedPlaylist
                } else null

                state.copy(
                    libraryPlaylists = all,
                    selectedPlaylist = updatedSelected
                )
            }
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
        _uiState.update { state ->
            val updatedCurrent = if (targetTrack == null || targetTrack.id == state.currentTrack.id) {
                state.currentTrack.copy(isLiked = isNowLiked)
            } else {
                state.currentTrack
            }
            val updatedHistory = state.searchHistory.map { entry ->
                if (entry.track != null && entry.track.id == track.id) {
                    entry.copy(track = entry.track.copy(isLiked = isNowLiked))
                } else {
                    entry
                }
            }
            val updatedSelected = if (state.selectedPlaylist?.isLikedSongs == true) {
                val updatedLikedTracks = database.getLikedSongs().map { it.toTrack(isLiked = true) }
                state.selectedPlaylist.copy(
                    tracks = updatedLikedTracks,
                    trackCount = updatedLikedTracks.size,
                    thumbnailUrl = updatedLikedTracks.firstOrNull()?.thumbnailUrl
                )
            } else if (state.selectedPlaylist != null) {
                val updatedPlaylistTracks = state.selectedPlaylist.tracks.map {
                    if (it.id == track.id) it.copy(isLiked = isNowLiked) else it
                }
                state.selectedPlaylist.copy(tracks = updatedPlaylistTracks)
            } else {
                state.selectedPlaylist
            }
            state.copy(
                currentTrack = updatedCurrent,
                searchHistory = updatedHistory,
                selectedPlaylist = updatedSelected
            )
        }
        refreshLibrary()
    }

    fun toggleShuffle() {
        playerEngine.toggleShuffle()
    }

    fun toggleRepeat() {
        playerEngine.toggleRepeat()
    }

    // --- SEARCH & SEARCH HISTORY ---

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().isEmpty()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    comprehensiveSearchResult = ComprehensiveSearchResult(),
                    artistFilterResults = emptyList(),
                    playlistFilterResults = emptyList(),
                    isSearching = false
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _uiState.update { it.copy(isSearching = true) }
            when (_uiState.value.searchFilter) {
                SearchFilterType.ALL -> {
                    val comp = innerTube.searchComprehensive(query)
                    val tracks = comp.songs.map { it.toTrack(database.isLiked(it.videoId)) }
                    _uiState.update {
                        it.copy(
                            comprehensiveSearchResult = comp,
                            searchResults = tracks,
                            isSearching = false
                        )
                    }
                }
                SearchFilterType.SONGS -> {
                    val songs = innerTube.searchSongs(query)
                    val tracks = songs.map { it.toTrack(database.isLiked(it.videoId)) }
                    _uiState.update {
                        it.copy(
                            searchResults = tracks,
                            isSearching = false
                        )
                    }
                }
                SearchFilterType.ARTISTS -> {
                    val artists = innerTube.searchArtists(query)
                    _uiState.update {
                        it.copy(
                            artistFilterResults = artists,
                            isSearching = false
                        )
                    }
                }
                SearchFilterType.PLAYLISTS -> {
                    val playlists = innerTube.searchPlaylists(query)
                    _uiState.update {
                        it.copy(
                            playlistFilterResults = playlists,
                            isSearching = false
                        )
                    }
                }
            }
        }
    }

    fun setSearchFilter(filter: SearchFilterType) {
        _uiState.update { it.copy(searchFilter = filter) }
        val query = _uiState.value.searchQuery
        if (query.trim().isNotEmpty()) {
            onSearchQueryChanged(query)
        }
    }

    fun openArtistProfile(artistId: String) {
        val currentStack = _uiState.value.artistStack
        _uiState.update {
            it.copy(
                artistStack = currentStack + artistId,
                isLoadingArtistProfile = true,
                showSettingsScreen = false
            )
        }
        viewModelScope.launch {
            val profile = innerTube.getArtistProfile(artistId)
            _uiState.update {
                it.copy(
                    currentArtistProfile = profile,
                    isLoadingArtistProfile = false
                )
            }
        }
    }

    fun popArtistProfile() {
        val currentStack = _uiState.value.artistStack
        if (currentStack.size > 1) {
            val newStack = currentStack.dropLast(1)
            val prevId = newStack.last()
            _uiState.update {
                it.copy(
                    artistStack = newStack,
                    isLoadingArtistProfile = true
                )
            }
            viewModelScope.launch {
                val profile = innerTube.getArtistProfile(prevId)
                _uiState.update {
                    it.copy(
                        currentArtistProfile = profile,
                        isLoadingArtistProfile = false
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    artistStack = emptyList(),
                    currentArtistProfile = null,
                    isLoadingArtistProfile = false
                )
            }
        }
    }

    fun toggleFollowArtist(artistId: String, name: String = "", thumbnailUrl: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            database.toggleFollowArtist(artistId, name, thumbnailUrl)
            val updated = database.getFollowedArtistIds()
            _uiState.update { it.copy(followedArtistIds = updated) }
        }
    }

    fun playArtistTopSongs(songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val tracks = songs.map { it.toTrack(database.isLiked(it.videoId)) }
        val startTrack = tracks.getOrElse(startIndex) { tracks.first() }
        playTrack(startTrack, tracks)
    }

    fun openSearchPlaylist(item: PlaylistItem) {
        addPlaylistToSearchHistory(item)
        val playlist = Playlist(
            id = item.id,
            title = item.title,
            description = item.author ?: "",
            creator = item.author ?: "YouTube Music",
            thumbnailUrl = item.thumbnailUrl
        )
        openPlaylist(playlist)
    }

    fun openAlbum(item: ArtistAlbumItem, artistName: String) {
        val playlist = Playlist(
            id = item.id,
            title = item.title,
            description = item.year ?: "",
            creator = artistName,
            thumbnailUrl = item.thumbnailUrl
        )
        openPlaylist(playlist)
    }

    fun addArtistToSearchHistory(artist: ArtistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            database.saveSearchHistoryArtist(artist)
            loadSearchHistory()
        }
    }

    fun addPlaylistToSearchHistory(playlist: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            database.saveSearchHistoryPlaylist(playlist)
            loadSearchHistory()
        }
    }

    fun loadSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = database.getSearchHistory()
            val entries = records.mapNotNull { record ->
                when (record.type) {
                    "TRACK" -> {
                        val songItem = record.songItem ?: return@mapNotNull null
                        SearchHistoryEntry(
                            id = record.id,
                            type = SearchHistoryType.TRACK,
                            track = songItem.toTrack(database.isLiked(songItem.videoId)),
                            timestamp = record.timestamp
                        )
                    }
                    "QUERY" -> {
                        val q = record.queryText ?: return@mapNotNull null
                        SearchHistoryEntry(
                            id = record.id,
                            type = SearchHistoryType.QUERY,
                            query = q,
                            timestamp = record.timestamp
                        )
                    }
                    "ARTIST" -> {
                        val artist = record.artistItem ?: return@mapNotNull null
                        SearchHistoryEntry(
                            id = record.id,
                            type = SearchHistoryType.ARTIST,
                            artist = artist,
                            timestamp = record.timestamp
                        )
                    }
                    "PLAYLIST" -> {
                        val playlist = record.playlistItem ?: return@mapNotNull null
                        SearchHistoryEntry(
                            id = record.id,
                            type = SearchHistoryType.PLAYLIST,
                            playlist = playlist,
                            timestamp = record.timestamp
                        )
                    }
                    else -> null
                }
            }
            _uiState.update { it.copy(searchHistory = entries) }
        }
    }

    fun addTrackToSearchHistory(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            database.saveSearchHistoryTrack(track.toSongItem())
            loadSearchHistory()
        }
    }

    fun addQueryToSearchHistory(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.saveSearchHistoryQuery(query)
            loadSearchHistory()
        }
    }

    fun removeSearchHistoryItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.removeSearchHistoryItem(id)
            loadSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearSearchHistory()
            _uiState.update { it.copy(searchHistory = emptyList()) }
        }
    }

    // --- USER PROFILE & ONBOARDING ---

    fun submitInitialUsername(username: String) {
        val cleanName = username.trim()
        val finalName = if (cleanName.isNotBlank()) cleanName else UserProfileManager.DEFAULT_USERNAME
        UserProfileManager.saveUsername(app, finalName)
        _uiState.update {
            it.copy(
                username = finalName,
                showInitialUsernameDialog = false
            )
        }
    }

    fun skipInitialUsername() {
        UserProfileManager.markPromptCompleted(app)
        _uiState.update {
            it.copy(showInitialUsernameDialog = false)
        }
    }

    fun updateUsername(newUsername: String) {
        val cleanName = newUsername.trim()
        if (cleanName.isNotBlank()) {
            UserProfileManager.saveUsername(app, cleanName)
            _uiState.update {
                it.copy(username = cleanName)
            }
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
        viewModelScope.launch(Dispatchers.IO) {
            database.addTrackToPlaylist(playlistId, track.toSongItem())
            withContext(Dispatchers.Main) {
                closeAddToPlaylistDialog()
                Toast.makeText(app, "Added \"${track.title}\" to playlist", Toast.LENGTH_SHORT).show()
            }
            refreshLibrary()
            val currentSelected = _uiState.value.selectedPlaylist
            if (currentSelected?.id == playlistId) {
                val updatedTracks = database.getPlaylistTracks(playlistId).map { it.toTrack(database.isLiked(it.videoId)) }
                val localInfo = database.getPlaylistInfo(playlistId)
                _uiState.update {
                    it.copy(
                        selectedPlaylist = it.selectedPlaylist?.let { pl ->
                            pl.copy(
                                title = localInfo?.title ?: pl.title,
                                description = localInfo?.description ?: pl.description,
                                thumbnailUrl = localInfo?.coverUrl ?: pl.thumbnailUrl ?: track.thumbnailUrl,
                                tracks = updatedTracks,
                                trackCount = updatedTracks.size
                            )
                        }
                    )
                }
            }
        }
    }

    fun createPlaylistAndAddTrack(title: String, track: Track) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = database.createPlaylist(trimmed)
            database.addTrackToPlaylist(playlistId, track.toSongItem())
            withContext(Dispatchers.Main) {
                closeAddToPlaylistDialog()
                Toast.makeText(app, "Created playlist \"$trimmed\" and added song", Toast.LENGTH_SHORT).show()
            }
            refreshLibrary()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val isLiked = playlistId == "liked_songs" || _uiState.value.selectedPlaylist?.isLikedSongs == true
            if (isLiked) {
                database.removeLikedSongs(setOf(track.id))
            } else {
                database.removeTrackFromPlaylist(playlistId, track.id)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Removed from playlist", Toast.LENGTH_SHORT).show()
            }
            refreshLibrary()
            val currentSelected = _uiState.value.selectedPlaylist
            if (currentSelected?.id == playlistId) {
                val updatedTracks = if (isLiked) {
                    database.getLikedSongs().map { it.toTrack(isLiked = true) }
                } else {
                    database.getPlaylistTracks(playlistId).map { it.toTrack(database.isLiked(it.videoId)) }
                }
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
    }

    fun removeTracksFromPlaylist(playlistId: String, trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val isLiked = playlistId == "liked_songs" || _uiState.value.selectedPlaylist?.isLikedSongs == true
            if (isLiked) {
                database.removeLikedSongs(trackIds)
            } else {
                database.removeTracksFromPlaylist(playlistId, trackIds)
            }
            refreshLibrary()
            val currentSelected = _uiState.value.selectedPlaylist
            if (currentSelected?.id == playlistId) {
                val updatedTracks = if (isLiked) {
                    database.getLikedSongs().map { it.toTrack(isLiked = true) }
                } else {
                    database.getPlaylistTracks(playlistId).map { it.toTrack(database.isLiked(it.videoId)) }
                }
                _uiState.update {
                    it.copy(
                        selectedPlaylist = it.selectedPlaylist?.copy(
                            tracks = updatedTracks,
                            trackCount = updatedTracks.size
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                val count = trackIds.size
                Toast.makeText(app, "Removed $count song${if (count > 1) "s" else ""} from playlist", Toast.LENGTH_SHORT).show()
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
        _uiState.update { it.copy(selectedTab = tab, selectedPlaylist = null, showSettingsScreen = false, showDownloadManager = false) }
        if (tab == NavigationTab.PROFILE) {
            refreshUsageMetrics()
        } else if (tab == NavigationTab.SEARCH) {
            loadSearchHistory()
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
            val localInfo = database.getPlaylistInfo(playlist.id)
            val currentTitle = localInfo?.title ?: playlist.title
            val currentDesc = localInfo?.description ?: playlist.description
            val currentCover = localInfo?.coverUrl ?: playlist.thumbnailUrl

            val tracks = if (playlist.isLikedSongs) {
                database.getLikedSongs().map { it.toTrack(isLiked = true) }
            } else if (playlist.id.startsWith("shelf_")) {
                val downloaded = database.getDownloadedPlaylistTracks(playlist.id)
                if (downloaded.isNotEmpty()) {
                    downloaded.map { it.toTrack(database.isLiked(it.videoId)) }
                } else {
                    playlist.tracks
                }
            } else {
                val localTracks = database.getPlaylistTracks(playlist.id)
                if (localTracks.isNotEmpty()) {
                    localTracks.map { it.toTrack(database.isLiked(it.videoId)) }
                } else {
                    val downloaded = database.getDownloadedPlaylistTracks(playlist.id)
                    if (downloaded.isNotEmpty()) {
                        downloaded.map { it.toTrack(database.isLiked(it.videoId)) }
                    } else {
                        playlist.tracks
                    }
                }
            }

            _uiState.update {
                it.copy(
                    selectedPlaylist = playlist.copy(
                        title = currentTitle,
                        description = currentDesc,
                        thumbnailUrl = currentCover,
                        tracks = tracks,
                        trackCount = tracks.size
                    ),
                    showSettingsScreen = false
                )
            }

            if (tracks.isEmpty() && (playlist.id.startsWith("VL") || playlist.id.startsWith("MPRE") || playlist.id.startsWith("PL") || playlist.id.startsWith("RD") || playlist.id.startsWith("OLAK"))) {
                viewModelScope.launch {
                    val result = innerTube.getPlaylist(playlist.id)
                    val onlineTracks = result.tracks.map { it.toTrack(database.isLiked(it.videoId)) }
                    if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                        _uiState.update {
                            it.copy(
                                selectedPlaylist = it.selectedPlaylist?.let { cur ->
                                    cur.copy(
                                        title = if (cur.title.isNotBlank()) cur.title else result.title,
                                        description = if (result.description.isNullOrBlank()) cur.description else result.description,
                                        creator = result.subtitle ?: cur.creator,
                                        thumbnailUrl = cur.thumbnailUrl ?: result.thumbnailUrl,
                                        tracks = onlineTracks,
                                        trackCount = onlineTracks.size
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun openEditPlaylistDialog(playlist: Playlist) {
        _uiState.update { it.copy(showEditPlaylistDialog = true, playlistToEdit = playlist) }
    }

    fun closeEditPlaylistDialog() {
        _uiState.update { it.copy(showEditPlaylistDialog = false, playlistToEdit = null) }
    }

    fun renamePlaylist(playlistId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.renamePlaylist(playlistId, trimmed)
            refreshLibrary()
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                _uiState.update {
                    it.copy(selectedPlaylist = it.selectedPlaylist?.copy(title = trimmed))
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Playlist renamed to \"$trimmed\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updatePlaylistCover(playlistId: String, imageUriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedCoverPath = saveCoverImageToInternalStorage(imageUriString, playlistId)
            database.updatePlaylistCover(playlistId, savedCoverPath)
            refreshLibrary()
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                _uiState.update {
                    it.copy(selectedPlaylist = it.selectedPlaylist?.copy(thumbnailUrl = savedCoverPath))
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Playlist cover updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updatePlaylistDetails(playlistId: String, newTitle: String, newDescription: String?, newCoverUriString: String?) {
        val trimmedTitle = newTitle.trim().ifEmpty { "My Playlist" }
        viewModelScope.launch(Dispatchers.IO) {
            val savedCoverPath = if (!newCoverUriString.isNullOrEmpty() && (newCoverUriString.startsWith("content://") || newCoverUriString.startsWith("file://"))) {
                saveCoverImageToInternalStorage(newCoverUriString, playlistId)
            } else {
                newCoverUriString
            }

            database.updatePlaylist(playlistId, trimmedTitle, newDescription, savedCoverPath)
            refreshLibrary()
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                _uiState.update {
                    it.copy(
                        selectedPlaylist = it.selectedPlaylist?.let { pl ->
                            pl.copy(
                                title = trimmedTitle,
                                description = newDescription ?: pl.description,
                                thumbnailUrl = savedCoverPath ?: pl.thumbnailUrl
                            )
                        }
                    )
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Playlist details updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCoverImageToInternalStorage(uriString: String, playlistId: String): String {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val coversDir = java.io.File(app.filesDir, "covers").apply { mkdirs() }
            // Clean up any existing cover files for this playlist to prevent storage bloat and stale cache
            coversDir.listFiles()?.filter { it.name.startsWith("playlist_${playlistId}") }?.forEach {
                try { it.delete() } catch (e: Exception) {}
            }
            val destFile = java.io.File(coversDir, "playlist_${playlistId}_${System.currentTimeMillis()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.w("PlaybackViewModel", "Failed to save cover image", e)
            uriString
        }
    }

    fun openAddSongsSheet() {
        _uiState.update {
            it.copy(
                showAddSongsSheet = true,
                playlistSearchQuery = "",
                playlistSearchResults = emptyList(),
                isSearchingForPlaylist = false
            )
        }
    }

    fun closeAddSongsSheet() {
        playlistSearchJob?.cancel()
        _uiState.update {
            it.copy(
                showAddSongsSheet = false,
                playlistSearchQuery = "",
                playlistSearchResults = emptyList(),
                isSearchingForPlaylist = false
            )
        }
    }

    fun onPlaylistSearchQueryChanged(query: String) {
        _uiState.update { it.copy(playlistSearchQuery = query) }
        playlistSearchJob?.cancel()

        if (query.trim().isEmpty()) {
            _uiState.update { it.copy(playlistSearchResults = emptyList(), isSearchingForPlaylist = false) }
            return
        }

        playlistSearchJob = viewModelScope.launch {
            delay(350)
            _uiState.update { it.copy(isSearchingForPlaylist = true) }
            val songs = innerTube.searchSongs(query)
            val tracks = songs.map { it.toTrack(database.isLiked(it.videoId)) }
            _uiState.update { it.copy(playlistSearchResults = tracks, isSearchingForPlaylist = false) }
        }
    }

    fun closePlaylist() {
        closeAddSongsSheet()
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

    // --- DOWNLOAD MANAGEMENT & OFFLINE STREAMING ---

    private var playlistDownloadJob: Job? = null

    fun openDownloadManager() {
        refreshDownloads()
        _uiState.update { it.copy(showDownloadManager = true, showSettingsScreen = false) }
    }

    fun closeDownloadManager() {
        _uiState.update { it.copy(showDownloadManager = false) }
    }

    fun refreshDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = database.getDownloadedSongs().map {
                it.toTrack(isLiked = database.isLiked(it.videoId), isDownloaded = true)
            }
            val playlists = database.getDownloadedPlaylists()
            val videoIds = database.getAllDownloadedVideoIds()
            val playlistIds = database.getAllDownloadedPlaylistIds()
            val currentDownloaded = database.isDownloaded(_uiState.value.currentTrack.id)
            val sizeBytes = database.getTotalDownloadedSizeBytes(app)

            _uiState.update {
                it.copy(
                    downloadedSongs = songs,
                    downloadedPlaylists = playlists,
                    downloadedSongIds = videoIds,
                    downloadedPlaylistIds = playlistIds,
                    isDownloaded = currentDownloaded,
                    totalDownloadSizeBytes = sizeBytes
                )
            }
        }
    }

    private val downloadPrefs = app.getSharedPreferences("koi_download_prefs", Context.MODE_PRIVATE)

    fun getSavedDownloadFormat(): AudioFormat {
        val name = downloadPrefs.getString("preferred_format", AudioFormat.M4A.name)
        return try { AudioFormat.valueOf(name!!) } catch (e: Exception) { AudioFormat.M4A }
    }

    fun getSavedDownloadQuality(): AudioQuality {
        val name = downloadPrefs.getString("preferred_quality", AudioQuality.HIGH.name)
        return try { AudioQuality.valueOf(name!!) } catch (e: Exception) { AudioQuality.HIGH }
    }

    fun saveDownloadPreferences(format: AudioFormat, quality: AudioQuality) {
        downloadPrefs.edit()
            .putString("preferred_format", format.name)
            .putString("preferred_quality", quality.name)
            .apply()
        _uiState.update {
            it.copy(
                preferredDownloadFormat = format,
                preferredDownloadQuality = quality
            )
        }
    }

    fun requestDownloadTrack(track: Track) {
        if (_uiState.value.downloadedSongIds.contains(track.id) && database.isDownloaded(track.id)) {
            deleteDownloadedSong(track)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Removed \"${track.title}\" from downloads", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (_uiState.value.activeDownload.isDownloading && _uiState.value.activeDownload.currentItemTitle == track.title) {
            cancelPlaylistDownload()
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Download cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        _uiState.update {
            it.copy(
                showDownloadOptionsSheet = true,
                downloadOptionsTargetTrack = track,
                downloadOptionsTargetPlaylist = null,
                preferredDownloadFormat = getSavedDownloadFormat(),
                preferredDownloadQuality = getSavedDownloadQuality()
            )
        }
    }

    fun requestDownloadPlaylist(playlist: Playlist) {
        if (_uiState.value.downloadedPlaylistIds.contains(playlist.id)) {
            removeDownloadedPlaylist(playlist.id)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Removed playlist from downloads", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (_uiState.value.activeDownload.isDownloading && _uiState.value.activeDownload.playlistId == playlist.id) {
            cancelPlaylistDownload()
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Download cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        _uiState.update {
            it.copy(
                showDownloadOptionsSheet = true,
                downloadOptionsTargetTrack = null,
                downloadOptionsTargetPlaylist = playlist,
                preferredDownloadFormat = getSavedDownloadFormat(),
                preferredDownloadQuality = getSavedDownloadQuality()
            )
        }
    }

    fun closeDownloadOptionsSheet() {
        _uiState.update {
            it.copy(
                showDownloadOptionsSheet = false,
                downloadOptionsTargetTrack = null,
                downloadOptionsTargetPlaylist = null
            )
        }
    }

    fun confirmDownload(format: AudioFormat, quality: AudioQuality) {
        val targetTrack = _uiState.value.downloadOptionsTargetTrack
        val targetPlaylist = _uiState.value.downloadOptionsTargetPlaylist
        closeDownloadOptionsSheet()
        saveDownloadPreferences(format, quality)

        if (targetTrack != null) {
            downloadTrack(targetTrack, format, quality)
        } else if (targetPlaylist != null) {
            downloadPlaylist(targetPlaylist, format, quality)
        }
    }

    fun downloadTrack(
        track: Track,
        format: AudioFormat = getSavedDownloadFormat(),
        quality: AudioQuality = getSavedDownloadQuality()
    ) {
        if (_uiState.value.downloadedSongIds.contains(track.id) && database.isDownloaded(track.id)) {
            deleteDownloadedSong(track)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Removed \"${track.title}\" from downloads", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (_uiState.value.activeDownload.isDownloading && _uiState.value.activeDownload.currentItemTitle == track.title) {
            cancelPlaylistDownload()
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Download cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(app, "Downloading \"${track.title}\" (${format.displayName} • ${quality.displayName})...", Toast.LENGTH_SHORT).show()
        }

        playlistDownloadJob?.cancel()
        playlistDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    activeDownload = ActiveDownloadState(
                        isDownloading = true,
                        currentItemTitle = track.title,
                        totalCount = 1,
                        completedCount = 0,
                        progressFraction = 0f
                    )
                )
            }

            val file = downloaderEngine.downloadTrack(track, format, quality) { progress ->
                _uiState.update {
                    it.copy(
                        activeDownload = it.activeDownload.copy(
                            progressFraction = progress.progressFraction,
                            currentItemTitle = track.title
                        )
                    )
                }
            }

            _uiState.update {
                it.copy(activeDownload = ActiveDownloadState(isDownloading = false))
            }
            refreshDownloads()

            withContext(Dispatchers.Main) {
                if (file != null && file.exists()) {
                    Toast.makeText(app, "Downloaded \"${track.title}\" (${format.displayName})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(app, "Failed to download \"${track.title}\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleDownload(track: Track) {
        requestDownloadTrack(track)
    }

    fun downloadPlaylist(
        playlist: Playlist,
        format: AudioFormat = getSavedDownloadFormat(),
        quality: AudioQuality = getSavedDownloadQuality()
    ) {
        if (_uiState.value.activeDownload.isDownloading && _uiState.value.activeDownload.playlistId == playlist.id) {
            cancelPlaylistDownload()
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Download cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (_uiState.value.downloadedPlaylistIds.contains(playlist.id)) {
            removeDownloadedPlaylist(playlist.id)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Removed playlist from downloads", Toast.LENGTH_SHORT).show()
            }
            return
        }

        viewModelScope.launch {
            val tracksToDownload = if (playlist.tracks.isNotEmpty()) {
                playlist.tracks
            } else if (playlist.isLikedSongs) {
                withContext(Dispatchers.IO) { database.getLikedSongs().map { it.toTrack(true) } }
            } else {
                val localTracks = withContext(Dispatchers.IO) { database.getPlaylistTracks(playlist.id) }
                if (localTracks.isNotEmpty()) localTracks.map { it.toTrack(database.isLiked(it.videoId)) }
                else withContext(Dispatchers.IO) { database.getDownloadedPlaylistTracks(playlist.id).map { it.toTrack(database.isLiked(it.videoId)) } }
            }

            if (tracksToDownload.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Playlist is empty", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Downloading playlist \"${playlist.title}\" (${tracksToDownload.size} songs • ${format.displayName})...", Toast.LENGTH_SHORT).show()
            }

            playlistDownloadJob?.cancel()
            playlistDownloadJob = viewModelScope.launch(Dispatchers.IO) {
                val total = tracksToDownload.size
                _uiState.update {
                    it.copy(
                        activeDownload = ActiveDownloadState(
                            isDownloading = true,
                            playlistId = playlist.id,
                            playlistTitle = playlist.title,
                            totalCount = total,
                            completedCount = 0,
                            progressFraction = 0f
                        )
                    )
                }

                val songItems = mutableListOf<SongItem>()

                for ((index, track) in tracksToDownload.withIndex()) {
                    if (!isActive) break

                    val songItem = track.toSongItem()
                    songItems.add(songItem)

                    val existingPath = database.getDownloadedFilePath(track.id)
                    if (existingPath != null && File(existingPath).exists() && File(existingPath).length() > 0) {
                        _uiState.update {
                            it.copy(
                                activeDownload = it.activeDownload.copy(
                                    currentItemTitle = track.title,
                                    completedCount = index + 1,
                                    progressFraction = (index + 1).toFloat() / total
                                )
                            )
                        }
                        continue
                    }

                    _uiState.update {
                        it.copy(
                            activeDownload = it.activeDownload.copy(
                                currentItemTitle = track.title,
                                completedCount = index,
                                progressFraction = index.toFloat() / total
                            )
                        )
                    }

                    downloaderEngine.downloadTrack(track, format, quality) { progress ->
                        val trackContribution = progress.progressFraction / total
                        val currentBase = index.toFloat() / total
                        _uiState.update {
                            it.copy(
                                activeDownload = it.activeDownload.copy(
                                    progressFraction = (currentBase + trackContribution).coerceIn(0f, 1f),
                                    currentItemTitle = track.title
                                )
                            )
                        }
                    }

                    _uiState.update {
                        it.copy(
                            activeDownload = it.activeDownload.copy(
                                completedCount = index + 1,
                                progressFraction = (index + 1).toFloat() / total
                            )
                        )
                    }
                }

                if (isActive) {
                    database.saveDownloadedPlaylist(playlist, songItems)
                    _uiState.update {
                        it.copy(activeDownload = ActiveDownloadState(isDownloading = false))
                    }
                    refreshDownloads()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Downloaded playlist \"${playlist.title}\"", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun cancelPlaylistDownload() {
        playlistDownloadJob?.cancel()
        playlistDownloadJob = null
        _uiState.update {
            it.copy(activeDownload = ActiveDownloadState(isDownloading = false))
        }
        refreshDownloads()
    }

    fun deleteDownloadedSong(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = database.getDownloadedFilePath(track.id)
            database.removeDownload(track.id)
            try {
                if (path != null) {
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
                File(app.filesDir, "downloads/${track.id}.m4a").delete()
                File(app.filesDir, "downloads/${track.id}.mp3").delete()
                File(app.filesDir, "downloads/temp/${track.id}.raw").delete()
            } catch (_: Exception) {}
            refreshDownloads()
            if (_uiState.value.currentTrack.id == track.id) {
                _uiState.update { it.copy(isDownloaded = false) }
            }
        }
    }

    fun removeDownloadedPlaylist(playlistId: String, deleteAudioFiles: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            database.removeDownloadedPlaylist(playlistId, deleteAudioFiles = deleteAudioFiles, context = app)
            refreshDownloads()
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            cancelPlaylistDownload()
            database.clearAllDownloads(app)
            refreshDownloads()
            _uiState.update { it.copy(isDownloaded = false) }
        }
    }

    private fun SongItem.toTrack(isLiked: Boolean, isDownloaded: Boolean = false): Track {
        val downloaded = database.isDownloaded(this.videoId)
        return Track.fromSongItem(this, isLiked = isLiked, isDownloaded = downloaded)
    }

    // --- APP UPDATE MANAGEMENT ---

    val updateState: StateFlow<UpdateUiState> = app.updateManager.state

    fun checkForUpdate(isManual: Boolean = false) {
        if (isManual) {
            _uiState.update { it.copy(isUpdateDialogDismissed = false) }
        }
        app.updateManager.checkForUpdate()
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(isUpdateDialogDismissed = true) }
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
        _uiState.update { it.copy(showSettingsScreen = true, selectedPlaylist = null, showDownloadManager = false) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsScreen = false) }
    }

    fun toggleMasterDsp() {
        val current = _dspConfig.value
        updateDspConfig(current.copy(masterEnabled = !current.masterEnabled))
    }
}
