package com.koi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onOpenSleepTimer: () -> Unit,
    onSelectStation: (SoundStation) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit = onProfileClick,
    onOpenFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
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
                    // Logo & Brand
                    Column(
                        modifier = Modifier.clickable { onProfileClick() }
                    ) {
                        Text(
                            text = "KOI",
                            fontSize = 19.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 2.sp
                        )
                        val statusSubtitle = when (dspConfig.spatialMode) {
                            SpatialMode.MODE_8D -> "8D SPATIAL ACTIVE"
                            SpatialMode.MODE_16D -> "16D DUAL RING ACTIVE"
                            SpatialMode.OFF -> "STUDIO AUDIO ENGINE"
                        }
                        Text(
                            text = statusSubtitle,
                            fontSize = 9.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dspConfig.spatialMode != SpatialMode.OFF) RogTextGold else RogTextSecondary,
                            letterSpacing = 1.sp
                        )
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
                                    .background(RogSurfaceHighlight)
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

                        // Search Shortcut
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onSearchClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = "Search",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Settings Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceHighlight)
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
                    // Top Row: Track Meta & Fullscreen Tap (No pond image in icon; controls float above background)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFullScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Middle: Inline 360° Spatial Audio Segmented Switcher (slightly opaque background)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RogSurfaceMedium.copy(alpha = 0.52f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
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
                                    fontSize = 11.sp,
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
                        // Current Profile indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenProfileSelector() }
                        ) {
                            Text(
                                text = "PROFILE: ",
                                fontSize = 10.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = RogTextSecondary
                            )
                            Text(
                                text = dspConfig.activeProfile.name,
                                fontSize = 10.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextGold
                            )
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

        // --- 3. CURATED SOUND STATIONS ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "CURATED SOUND STATIONS",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Atmospheric acoustic spaces tuned for immersion",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // --- 4. YOUR VAULT (LIBRARY QUICK LAUNCH) ---
        if (uiState.libraryPlaylists.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "YOUR VAULT",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (playlist.isLikedSongs) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_heart_filled),
                                        contentDescription = "Liked",
                                        tint = RogTextGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_library),
                                        contentDescription = "Playlist",
                                        tint = RogTextGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = playlist.title,
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextPrimary
                                    )
                                    Text(
                                        text = "${playlist.trackCount} tracks",
                                        fontSize = 11.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        color = RogTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. LIVE DISCOVERY STREAM (VERTICAL FEED) ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "DISCOVERY STREAM",
                    fontSize = 11.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextGold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Trending and curated tracks ready for instant playback",
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = RogTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Items of the Discovery Feed
        val feedTracks = if (uiState.discoveryFeed.isNotEmpty()) {
            uiState.discoveryFeed
        } else {
            uiState.madeForYou.flatMap { it.tracks }.distinctBy { it.id }
        }

        itemsIndexed(feedTracks.take(20)) { index, track ->
            DiscoveryFeedItem(
                index = index + 1,
                track = track,
                isCurrentTrack = track.id == uiState.currentTrack.id,
                isPlaying = track.id == uiState.currentTrack.id && uiState.isPlaying,
                onClick = { onTrackClick(track, feedTracks) },
                onLikeClick = { onToggleLike(track) },
                onPlayClick = { onTrackClick(track, feedTracks) },
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
    Box(
        modifier = modifier
            .width(230.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(RogSurfaceDark)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RogSurfaceHighlight)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = station.tag,
                        fontSize = 9.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = station.title,
                    fontSize = 15.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.subtitle,
                    fontSize = 11.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = RogTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "Play Station",
                        tint = RogTextGold,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFeedItem(
    index: Int,
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onPlayClick: () -> Unit,
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
        // Track Index
        Text(
            text = "%02d".format(index),
            fontSize = 12.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = if (isCurrentTrack) RogTextGold else RogTextSecondary,
            modifier = Modifier.width(28.dp)
        )

        // Artwork
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

        // Title & Artist
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

        // Like Button
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

        // Play/Status Action
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(
                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                ),
                contentDescription = "Play",
                tint = if (isCurrentTrack) RogTextGold else RogTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
