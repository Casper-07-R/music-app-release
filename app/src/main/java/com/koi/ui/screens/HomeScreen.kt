package com.koi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.koi.ui.components.TutorialTarget
import coil.compose.AsyncImage
import com.koi.backend.audio.AudioDspConfig
import com.koi.backend.audio.SpatialMode
import com.koi.ui.R
import com.koi.ui.model.*
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.PlaybackUiState

@Composable
fun HomeScreen(
    uiState: PlaybackUiState,
    dspConfig: AudioDspConfig,
    spectrumBands: FloatArray = FloatArray(10) { 0f },
    rotationAngleDegrees: Float = 0f,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onSelectSpatialMode: (SpatialMode) -> Unit,
    onConfigChange: (AudioDspConfig) -> Unit,
    onOpenProfileSelector: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenSleepTimer: () -> Unit,
    onSelectStation: (SoundStation) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit = {},
    onOpenDownloadManager: () -> Unit = {},
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit = onProfileClick,
    onOpenFullScreen: () -> Unit,
    onTargetPositioned: (TutorialTarget, Rect) -> Unit = { _, _ -> },
    tutorialStep: Int? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Smoothly scroll to the target feature when the active tutorial step changes
    LaunchedEffect(tutorialStep) {
        if (tutorialStep == 0 || tutorialStep == 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // --- 1. MINIMAL HEADER BAR ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand + Live Spatial Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onProfileClick() }
                    ) {
                        Text(
                            text = "Koi",
                            fontSize = 22.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 1.sp
                        )
                        if (dspConfig.spatialMode != SpatialMode.OFF) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RogGoldDim)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (dspConfig.spatialMode == SpatialMode.MODE_16D) "16D" else "8D",
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextGold
                                )
                            }
                        }
                    }

                    // Action Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sleep Timer Action
                        if (uiState.sleepTimerRemainingSeconds > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenSleepTimer() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        tint = RogTextGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${(uiState.sleepTimerRemainingSeconds + 59) / 60}m",
                                        fontSize = 11.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextGold
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenSleepTimer() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = RogTextSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Download Manager Shortcut (No button background)
                        val isDownloading = uiState.activeDownload.isDownloading
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onOpenDownloadManager() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Download Manager",
                                tint = if (isDownloading) RogGoldPrimary else RogTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Settings Button (No button background)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onSettingsClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = "Settings",
                                tint = RogTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 2. LIVE AUDIO DECK (HERO FEATURE CARD) ---
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords -> onTargetPositioned(TutorialTarget.HERO_AUDIO_STUDIO, coords.boundsInRoot()) }
                    .clip(RoundedCornerShape(28.dp))
            ) {
                // Background Image: The vibrant Koi pond with water ripples starting from bottom-left corner
                Image(
                    painter = painterResource(id = R.drawable.koi_notification_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.matchParentSize()
                )

                // Aquatic Scrim Overlay for optimal contrast and deep water vibrance
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x9905141C),
                                    Color(0x4005141C),
                                    Color(0xB005141C)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Top Row: Album Cover + Track Meta + Like
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords -> onTargetPositioned(TutorialTarget.LYRICS_AND_FIXES, coords.boundsInRoot()) }
                            .clickable { onOpenFullScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uiState.currentTrack.thumbnailUrl,
                            contentDescription = uiState.currentTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(RogSurfaceMedium)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentTrack.title,
                                fontSize = 16.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.currentTrack.artist,
                                fontSize = 12.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = RogTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleLike(uiState.currentTrack) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (uiState.currentTrack.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                                ),
                                contentDescription = "Like",
                                tint = if (uiState.currentTrack.isLiked) RogTextGold else RogTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtle Immersive Live Spectrum Strip on Hero Card
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    ) {
                        val barCount = 10
                        val gap = 5.dp.toPx()
                        val barW = (size.width - gap * (barCount - 1)) / barCount
                        val active = uiState.isPlaying && dspConfig.masterEnabled && !dspConfig.abBypass
                        for (i in 0 until barCount) {
                            val amp = if (active && i < spectrumBands.size) spectrumBands[i].coerceIn(0.12f, 1f) else 0.14f
                            val barH = size.height * amp
                            drawRoundRect(
                                color = if (active) RogTextGold.copy(alpha = 0.55f) else RogTextMuted.copy(alpha = 0.25f),
                                topLeft = androidx.compose.ui.geometry.Offset(i * (barW + gap), size.height - barH),
                                size = androidx.compose.ui.geometry.Size(barW, barH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Middle: Inline 360° Spatial Audio Segmented Switcher (translucent background)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RogSurfaceMedium.copy(alpha = 0.52f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val spatialModes = listOf(
                            SpatialMode.OFF to "STEREO",
                            SpatialMode.MODE_8D to "8D",
                            SpatialMode.MODE_16D to "16D"
                        )

                        spatialModes.forEach { (mode, label) ->
                            val isSelected = dspConfig.spatialMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) RogSurfaceHighlight.copy(alpha = 0.65f) else Color.Transparent)
                                    .clickable { onSelectSpatialMode(mode) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) RogTextGold else RogTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Line
                    val duration = uiState.currentTrack.durationSeconds.toFloat().coerceAtLeast(1f)
                    val progress = (uiState.currentPositionSeconds / duration).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(RogSurfaceMedium.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(RogGoldPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: Profile Tag & Playback Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Visual Profile & EQ Quick Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RogSurfaceMedium.copy(alpha = 0.6f))
                                    .clickable { onOpenProfileSelector() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Profile",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = dspConfig.activeProfile.label,
                                    fontSize = 10.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (dspConfig.eqEnabled && !dspConfig.abBypass) RogGoldDim
                                        else RogSurfaceMedium.copy(alpha = 0.6f)
                                    )
                                    .clickable { onOpenEqualizer() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Equalizer",
                                    tint = if (dspConfig.eqEnabled && !dspConfig.abBypass) RogTextGold else RogTextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (dspConfig.eqEnabled && !dspConfig.abBypass) dspConfig.eqPreset else "EQ",
                                    fontSize = 10.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dspConfig.eqEnabled && !dspConfig.abBypass) RogTextGold else RogTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Playback Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onSkipPrevious,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_back),
                                    contentDescription = "Previous",
                                    tint = RogTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RogGoldPrimary)
                                    .clickable { onTogglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                    ),
                                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                    tint = RogObsidian,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onSkipNext,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_next),
                                    contentDescription = "Next",
                                    tint = RogTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. STATIONS ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "Stations",
                    fontSize = 15.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.soundStations.size) { idx ->
                        val station = uiState.soundStations[idx]
                        SoundStationCard(
                            station = station,
                            onClick = { onSelectStation(station) }
                        )
                    }
                }
            }
        }

        // --- 4. PLAYLISTS ---
        if (uiState.libraryPlaylists.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Playlists",
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.libraryPlaylists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RogSurfaceDark)
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (playlist.isLikedSongs) R.drawable.ic_heart_filled else R.drawable.ic_library
                                    ),
                                    contentDescription = playlist.title,
                                    tint = RogTextGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = playlist.title,
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. DISCOVER ---
        item {
            Text(
                text = "Discover",
                fontSize = 15.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        val feedTracks = if (uiState.discoveryFeed.isNotEmpty()) {
            uiState.discoveryFeed
        } else {
            uiState.madeForYou.flatMap { it.tracks }.distinctBy { it.id }
        }

        itemsIndexed(feedTracks.take(20)) { _, track ->
            DiscoveryFeedItem(
                track = track,
                isCurrentTrack = track.id == uiState.currentTrack.id,
                onClick = { onTrackClick(track, feedTracks) },
                onLikeClick = { onToggleLike(track) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SoundStationCard(
    station: SoundStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(158.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RogSurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = station.tag,
                fontSize = 9.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextGold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = station.title,
                fontSize = 13.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(RogSurfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = "Play",
                tint = RogTextGold,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

@Composable
private fun DiscoveryFeedItem(
    track: Track,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) RogSurfaceDark else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RogSurfaceHighlight)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontFamily = CircularFontFamily,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentTrack) RogTextGold else RogTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Normal,
                color = RogTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onLikeClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(
                    id = if (track.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                ),
                contentDescription = "Like",
                tint = if (track.isLiked) RogTextGold else RogTextSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
