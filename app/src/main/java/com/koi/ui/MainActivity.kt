package com.koi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import com.koi.backend.player.BatteryOptimizationHelper
import com.koi.backend.audio.AudioProfile
import com.koi.backend.audio.SpatialMode
import com.koi.ui.components.EightDAudioStudioSheet
import com.koi.ui.components.EightDProfileSelectorSheet
import com.koi.ui.components.EqualizerProfilesSheet
import com.koi.ui.components.NowPlayingFullScreenSheet
import com.koi.ui.components.NowPlayingMiniBar
import com.koi.ui.components.SleepTimerSheet
import com.koi.ui.components.KoiBottomNavBar
import com.koi.ui.model.NavigationTab
import com.koi.ui.screens.HomeScreen
import com.koi.ui.screens.LibraryScreen
import com.koi.ui.screens.PlaylistDetailScreen
import com.koi.ui.screens.ProfileScreen
import com.koi.ui.screens.SearchScreen
import com.koi.ui.screens.SettingsScreen
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.PlaybackViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime notification permission for background media playback controls on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Initialize background media playback service (MediaSessionService will promote to foreground when playback begins)
        try {
            val serviceIntent = Intent(this, com.koi.backend.player.PlaybackService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to start PlaybackService", e)
        }

        setContent {
            KoiTheme {
                KoiApp()
            }
        }
    }
}

@Composable
fun KoiApp(
    viewModel: PlaybackViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val usageMetrics by viewModel.usageMetrics.collectAsState()
    val dspConfig by viewModel.dspConfig.collectAsState()
    val rotationAngleDegrees by viewModel.rotationAngleDegrees.collectAsState()
    val spectrumBands by viewModel.spectrumBands.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val context = LocalContext.current
    val batteryPrefs = remember(context) { context.getSharedPreferences("koi_battery_prefs", android.content.Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        val hasPrompted = batteryPrefs.getBoolean("has_prompted_unrestricted_bg", false)
        if (!hasPrompted && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            batteryPrefs.edit().putBoolean("has_prompted_unrestricted_bg", true).apply()
            kotlinx.coroutines.delay(1200)
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                Toast.makeText(
                    context,
                    "Allow unrestricted background access for continuous playback",
                    Toast.LENGTH_LONG
                ).show()
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
        }
    }

    BackHandler(enabled = uiState.showEqualizerSheet) {
        viewModel.closeEqualizerSheet()
    }
    BackHandler(enabled = uiState.showSleepTimerDialog) {
        viewModel.closeSleepTimerDialog()
    }
    BackHandler(enabled = uiState.showProfileSelectorSheet) {
        viewModel.closeProfileSelectorSheet()
    }
    BackHandler(enabled = uiState.showEightDStudioSheet) {
        viewModel.closeEightDStudioSheet()
    }
    BackHandler(enabled = uiState.isFullScreenPlayerExpanded && !uiState.showEightDStudioSheet && !uiState.showEqualizerSheet) {
        viewModel.closeFullScreenPlayer()
    }
    BackHandler(enabled = uiState.selectedPlaylist != null && !uiState.isFullScreenPlayerExpanded && !uiState.showEightDStudioSheet && !uiState.showEqualizerSheet) {
        viewModel.closePlaylist()
    }
    BackHandler(enabled = uiState.showSettingsScreen && !uiState.isFullScreenPlayerExpanded && !uiState.showEightDStudioSheet && !uiState.showEqualizerSheet) {
        viewModel.closeSettings()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // Main Screen Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.showSettingsScreen) {
                val currentContext = LocalContext.current
                SettingsScreen(
                    updateState = updateState,
                    dspConfig = dspConfig,
                    usageMetrics = usageMetrics,
                    sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                    onBackClick = { viewModel.closeSettings() },
                    onSelectSpatialMode = { viewModel.setSpatialMode(it) },
                    onToggleMasterDsp = { viewModel.toggleMasterDsp() },
                    onOpenEqualizer = { viewModel.openEqualizerSheet() },
                    onOpenSurroundStudio = { viewModel.openEightDStudioSheet() },
                    onOpenProfileSelector = { viewModel.openProfileSelectorSheet() },
                    onResetProfile = { viewModel.resetProfile(it) },
                    onSetSleepTimer = { viewModel.setSleepTimer(it) },
                    onClearCacheClick = { viewModel.clearCache() },
                    onCheckForUpdate = { viewModel.checkForUpdate() },
                    onDownloadUpdate = { release, asset ->
                        viewModel.downloadUpdate(release, asset, currentContext)
                    },
                    onCancelDownload = { viewModel.cancelUpdateDownload() },
                    onInstallUpdate = { apkFile ->
                        viewModel.installUpdate(currentContext, apkFile)
                    }
                )
            } else if (uiState.selectedPlaylist != null) {
                PlaylistDetailScreen(
                    playlist = uiState.selectedPlaylist!!,
                    currentTrackId = uiState.currentTrack.id,
                    isPlaying = uiState.isPlaying,
                    onBackClick = { viewModel.closePlaylist() },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onPlayAllClick = {
                        val tracks = uiState.selectedPlaylist!!.tracks
                        if (tracks.isNotEmpty()) {
                            viewModel.playTrack(tracks.first(), tracks)
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    onLikeTrackClick = { track -> viewModel.toggleLike(track) },
                    onMoreTrackClick = { track -> viewModel.openAddToPlaylistDialog(track) }
                )
            } else {
                when (uiState.selectedTab) {
                    NavigationTab.HOME -> {
                        HomeScreen(
                            uiState = uiState,
                            dspConfig = dspConfig,
                            spectrumBands = spectrumBands,
                            rotationAngleDegrees = rotationAngleDegrees,
                            onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() },
                            onToggleLike = { track -> viewModel.toggleLike(track) },
                            onSelectSpatialMode = { mode -> viewModel.setSpatialMode(mode) },
                            onConfigChange = { viewModel.updateDspConfig(it) },
                            onOpenProfileSelector = { viewModel.openProfileSelectorSheet() },
                            onOpenSleepTimer = { viewModel.openSleepTimerDialog() },
                            onSelectStation = { station -> viewModel.playStation(station) },
                            onPlaylistClick = { playlist -> viewModel.openPlaylist(playlist) },
                            onSearchClick = { viewModel.selectTab(NavigationTab.SEARCH) },
                            onProfileClick = { viewModel.selectTab(NavigationTab.PROFILE) },
                            onSettingsClick = { viewModel.openSettings() },
                            onOpenFullScreen = { viewModel.openFullScreenPlayer() }
                        )
                    }
                    NavigationTab.SEARCH -> {
                        SearchScreen(
                            categories = uiState.categories,
                            searchQuery = uiState.searchQuery,
                            searchResults = uiState.searchResults,
                            isSearching = uiState.isSearching,
                            currentTrackId = uiState.currentTrack.id,
                            onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                            onLikeTrackClick = { track -> viewModel.toggleLike(track) },
                            onMoreTrackClick = { track -> viewModel.openAddToPlaylistDialog(track) },
                            onCategoryClick = { category ->
                                viewModel.onSearchQueryChanged(category.title)
                            }
                        )
                    }
                    NavigationTab.YOUR_LIBRARY -> {
                        LibraryScreen(
                            playlists = uiState.libraryPlaylists,
                            onPlaylistClick = { playlist ->
                                viewModel.openPlaylist(playlist)
                            },
                            onCreatePlaylistClick = {
                                viewModel.openCreatePlaylistDialog()
                            },
                            onDeletePlaylistClick = { playlist ->
                                viewModel.deletePlaylist(playlist.id)
                            },
                            onProfileClick = { viewModel.selectTab(NavigationTab.PROFILE) }
                        )
                    }
                    NavigationTab.PROFILE -> {
                        ProfileScreen(
                            usageMetrics = usageMetrics,
                            sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                            onClearCacheClick = { viewModel.clearCache() },
                            onRefreshMetricsClick = { viewModel.refreshUsageMetrics() },
                            onSetSleepTimer = { viewModel.setSleepTimer(it) }
                        )
                    }
                }
            }
        }

        // Floating Bottom Navigation & Floating Mini-Player Container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Floating Now Playing Mini-Bar (Hidden on Home screen since Home has the Hero Audio Deck)
            val isHomeScreen = uiState.selectedTab == NavigationTab.HOME && uiState.selectedPlaylist == null && !uiState.showSettingsScreen
            if (!isHomeScreen && !uiState.isFullScreenPlayerExpanded) {
                val durationSecs = uiState.currentTrack.durationSeconds.toFloat().coerceAtLeast(1f)
                val progressFraction = (uiState.currentPositionSeconds / durationSecs).coerceIn(0f, 1f)

                NowPlayingMiniBar(
                    track = uiState.currentTrack,
                    isPlaying = uiState.isPlaying,
                    isBuffering = uiState.isBuffering,
                    progressFraction = progressFraction,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onLikeClick = { viewModel.toggleLike() },
                    onBarClick = { viewModel.openFullScreenPlayer() },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Bottom Navigation Bar
            KoiBottomNavBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }

        // Expandable Full Screen Now Playing Sheet Modal
        val isEightDActive = dspConfig.spatialMode != SpatialMode.OFF && dspConfig.is8dEnabled && dspConfig.masterEnabled && !dspConfig.abBypass
        val eightDLabel = when (dspConfig.spatialMode) {
            SpatialMode.MODE_16D -> "16D"
            else -> "8D"
        }
        val activeProfileName = if (isEightDActive) dspConfig.activeProfile.label else ""

        AnimatedVisibility(
            visible = uiState.isFullScreenPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingFullScreenSheet(
                track = uiState.currentTrack,
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                currentPositionSeconds = uiState.currentPositionSeconds,
                isShuffleActive = uiState.isShuffleActive,
                isRepeatActive = uiState.isRepeatActive,
                onCloseClick = { viewModel.closeFullScreenPlayer() },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onSkipNextClick = { viewModel.skipNext() },
                onSkipPrevClick = { viewModel.skipPrevious() },
                onLikeClick = { viewModel.toggleLike() },
                onShuffleClick = { viewModel.toggleShuffle() },
                onRepeatClick = { viewModel.toggleRepeat() },
                onSeek = { viewModel.seekTo(it) },
                isDownloaded = uiState.isDownloaded,
                isEightDActive = isEightDActive,
                eightDLabel = eightDLabel,
                activeProfileName = activeProfileName,
                onDownloadClick = { viewModel.toggleDownload(uiState.currentTrack) },
                onEightDClick = { viewModel.cycleSpatialMode() },
                onEightDLongClick = { viewModel.openProfileSelectorSheet() }
            )
        }

        // 8D Audio Profile Selector Modal Bottom Sheet (Triggered by Long-Press on 8D button)
        if (uiState.showProfileSelectorSheet) {
            val context = LocalContext.current
            EightDProfileSelectorSheet(
                activeProfile = dspConfig.activeProfile,
                spatialMode = dspConfig.spatialMode,
                onProfileSelect = { profile ->
                    viewModel.selectProfile(profile)
                    Toast.makeText(context, "${profile.label} profile active", Toast.LENGTH_SHORT).show()
                    viewModel.closeProfileSelectorSheet()
                },
                onResetProfile = { profile ->
                    viewModel.resetProfile(profile)
                },
                onClose = { viewModel.closeProfileSelectorSheet() }
            )
        }

        // 8D Audio & DSP Studio Modal Bottom Sheet
        if (uiState.showEightDStudioSheet) {
            EightDAudioStudioSheet(
                dspConfig = dspConfig,
                rotationAngleDegrees = rotationAngleDegrees,
                spectrumBands = spectrumBands,
                sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                onConfigChange = { viewModel.updateDspConfig(it) },
                onSelectSpatialMode = { viewModel.setSpatialMode(it) },
                onProfileSelect = { viewModel.selectProfile(it) },
                onResetProfile = { viewModel.resetProfile(it) },
                onResetToFlat = { viewModel.resetToFlat() },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onClose = { viewModel.closeEightDStudioSheet() }
            )
        }

        // Sleep Timer Modal Bottom Sheet
        if (uiState.showSleepTimerDialog) {
            SleepTimerSheet(
                remainingSeconds = uiState.sleepTimerRemainingSeconds,
                onSetTimer = { mins -> viewModel.setSleepTimer(mins) },
                onDismiss = { viewModel.closeSleepTimerDialog() }
            )
        }

        // Equalizer Profiles Modal Bottom Sheet
        if (uiState.showEqualizerSheet) {
            EqualizerProfilesSheet(
                dspConfig = dspConfig,
                selectedPreset = uiState.selectedEqPreset,
                onSelectPreset = { viewModel.selectEqPreset(it) },
                onConfigChange = { viewModel.updateDspConfig(it) },
                onDismiss = { viewModel.closeEqualizerSheet() }
            )
        }

        // Create Playlist Dialog
        if (uiState.showCreatePlaylistDialog) {
            var playlistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.closeCreatePlaylistDialog() },
                title = {
                    Text(
                        text = "Give your playlist a name",
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary
                    )
                },
                text = {
                    TextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("My playlist #1", color = SpotifyTextSecondary) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = SpotifyTextPrimary,
                            unfocusedTextColor = SpotifyTextPrimary,
                            focusedContainerColor = SpotifySurface,
                            unfocusedContainerColor = SpotifySurface,
                            cursorColor = SpotifyGreen,
                            focusedIndicatorColor = SpotifyGreen,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.createPlaylist(playlistName) }
                    ) {
                        Text("Create", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeCreatePlaylistDialog() }) {
                        Text("Cancel", color = SpotifyTextSecondary)
                    }
                },
                containerColor = SpotifySurface
            )
        }

        // Add to Playlist Dialog
        uiState.trackForPlaylistMenu?.let { trackToAdd ->
            val customPlaylists = uiState.libraryPlaylists.filter { !it.isLikedSongs }
            AlertDialog(
                onDismissRequest = { viewModel.closeAddToPlaylistDialog() },
                title = {
                    Text(
                        text = "Add to playlist",
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary
                    )
                },
                text = {
                    if (customPlaylists.isEmpty()) {
                        Text(
                            text = "No playlists found. Create a playlist first in Your Library!",
                            fontFamily = CircularFontFamily,
                            color = SpotifyTextSecondary
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(customPlaylists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTrackToPlaylist(pl.id, trackToAdd)
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pl.title,
                                        fontSize = 15.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = SpotifyTextPrimary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${pl.trackCount} songs",
                                        fontSize = 12.sp,
                                        color = SpotifyTextSecondary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeAddToPlaylistDialog() }) {
                        Text("Close", color = SpotifyTextSecondary)
                    }
                },
                containerColor = SpotifySurface
            )
        }
    }
}
