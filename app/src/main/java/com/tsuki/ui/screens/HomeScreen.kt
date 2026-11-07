package com.tsuki.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tsuki.backend.audio.AudioDspConfig
import com.tsuki.backend.audio.SpatialMode
import com.tsuki.ui.R
import com.tsuki.ui.model.*
import com.tsuki.ui.theme.*
import com.tsuki.ui.viewmodel.PlaybackUiState
import kotlin.math.cos
import kotlin.math.sin

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
    onOpenEqualizer: () -> Unit,
    onOpenSurroundStudio: () -> Unit,
    onOpenProfileSelector: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectStation: (SoundStation) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
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
                            text = "TSUKI",
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

                        // Surround Studio Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onOpenSurroundStudio() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_soundwave),
                                contentDescription = "DSP Studio",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
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

                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceHighlight)
                                .clickable { onProfileClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "C",
                                fontSize = 13.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextGold
                            )
                        }
                    }
                }
            }
        }

        // --- 2. LIVE AUDIO DECK (HERO FEATURE CARD) ---
        item {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RogSurfaceDark)
                        .padding(18.dp)
                ) {
                    // Top Row: Track Artwork, Meta & Fullscreen Tap
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFullScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uiState.currentTrack.thumbnailUrl,
                            contentDescription = uiState.currentTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(RogSurfaceMedium)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentTrack.title,
                                fontSize = 15.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.currentTrack.artist,
                                fontSize = 12.sp,
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

                    // Middle: Inline 360° Spatial Audio Segmented Switcher + Tune Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RogSurfaceMedium)
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
                                        .background(if (isSelected) RogSurfaceHighlight else Color.Transparent)
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

                        // Tune Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RogSurfaceHighlight)
                                .clickable { onOpenSurroundStudio() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_soundwave),
                                    contentDescription = "Tune",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "TUNE",
                                    fontSize = 10.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextGold,
                                    letterSpacing = 0.5.sp
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
                            .background(RogSurfaceMedium)
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

        // --- 4. 10-BAND ACOUSTIC EQUALIZER ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                EqualizerPlaceholderCard(
                    bands = spectrumBands,
                    isPlaying = uiState.isPlaying,
                    profileName = uiState.selectedEqPreset,
                    onOpenEqualizer = onOpenEqualizer
                )
            }
        }

        // --- 4. CURATED SOUND STATIONS ---
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

        // --- 5. YOUR VAULT (LIBRARY QUICK LAUNCH) ---
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

        // --- 6. LIVE DISCOVERY STREAM (VERTICAL FEED) ---
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
private fun EqualizerPlaceholderCard(
    bands: FloatArray,
    isPlaying: Boolean,
    profileName: String,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RogSurfaceDark)
            .clickable { onOpenEqualizer() }
            .padding(18.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Equalizer",
                    tint = RogTextGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ACOUSTIC EQUALIZER",
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "10-Band Realtime Spectrum",
                        fontSize = 12.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextPrimary
                    )
                }
            }

            // Profile Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RogSurfaceHighlight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = profileName,
                    fontSize = 10.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextGold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spectrum / Equalizer Visualization (Canvas)
        val bandLabels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val count = 10
            val totalWidth = size.width
            val barWidth = (totalWidth / count) * 0.52f
            val spacing = (totalWidth - (barWidth * count)) / (count - 1).coerceAtLeast(1)

            val defaultContour = floatArrayOf(0.40f, 0.52f, 0.44f, 0.32f, 0.28f, 0.36f, 0.45f, 0.54f, 0.46f, 0.35f)

            for (i in 0 until count) {
                val rawFraction = if (i < bands.size && isPlaying) {
                    bands[i].coerceIn(0.08f, 1.0f)
                } else {
                    defaultContour[i]
                }

                val barHeight = (size.height * rawFraction).coerceAtLeast(4.dp.toPx())
                val left = i * (barWidth + spacing)
                val top = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            RogGoldPrimary,
                            RogGoldDark
                        ),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frequency Labels Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bandLabels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = RogTextMuted,
                    modifier = Modifier.width(26.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action Hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap to open Equalizer Profiles & Faders",
                fontSize = 11.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Normal,
                color = RogTextSecondary
            )

            Text(
                text = "TUNE >",
                fontSize = 11.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextGold,
                letterSpacing = 0.5.sp
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
