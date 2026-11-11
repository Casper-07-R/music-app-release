package com.koi.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.Track
import com.koi.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingFullScreenSheet(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isFindingLyrics: Boolean = false,
    currentPositionSeconds: Float,
    isShuffleActive: Boolean,
    isRepeatActive: Boolean,
    onCloseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPrevClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Float) -> Unit,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgressFraction: Float = 0f,
    isEightDActive: Boolean = false,
    eightDLabel: String = "8D",
    activeProfileName: String = "",
    onDownloadClick: () -> Unit = {},
    onEightDClick: () -> Unit = {},
    onEightDLongClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val durationSeconds = track.durationSeconds.toFloat().coerceAtLeast(1f)
    var sliderValue by remember(currentPositionSeconds) { mutableFloatStateOf(currentPositionSeconds) }
    var isUserDragging by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }

    val displayPos = if (isUserDragging) sliderValue else currentPositionSeconds
    val elapsedMinutes = (displayPos / 60).toInt()
    val elapsedRemainderSeconds = (displayPos % 60).toInt()
    val elapsedString = String.format("%d:%02d", elapsedMinutes, elapsedRemainderSeconds)

    val remainingSeconds = (durationSeconds - displayPos).coerceAtLeast(0f)
    val remainingMinutes = (remainingSeconds / 60).toInt()
    val remainingRemainderSeconds = (remainingSeconds % 60).toInt()
    val remainingString = String.format("-%d:%02d", remainingMinutes, remainingRemainderSeconds)

    val context = LocalContext.current
    val lyricPrefs = remember(context) {
        context.getSharedPreferences("koi_lyric_offsets", Context.MODE_PRIVATE)
    }
    var userOffsetMs by remember(track.id) {
        mutableLongStateOf(lyricPrefs.getLong("offset_${track.id}", 0L))
    }

    val BASE_LEAD_IN_MS = 0L
    val currentPositionMs = (currentPositionSeconds * 1000).toLong()
    val effectivePositionMs = (currentPositionMs + userOffsetMs + BASE_LEAD_IN_MS).coerceAtLeast(0L)
    val activeLyricIndex = remember(effectivePositionMs, track.syncedLyrics) {
        if (track.syncedLyrics.isNotEmpty()) {
            track.syncedLyrics.indexOfLast { (it.timeMs ?: 0L) <= effectivePositionMs }
        } else {
            -1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (showFullLyrics) {
            BackHandler(enabled = true) {
                showFullLyrics = false
            }

            // Full Screen Lyrics View (Minimal)
            val listState = rememberLazyListState()

            LaunchedEffect(activeLyricIndex) {
                if (activeLyricIndex >= 0 && activeLyricIndex < track.syncedLyrics.size) {
                    listState.animateScrollToItem(
                        (activeLyricIndex - 2).coerceAtLeast(0)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showFullLyrics = false }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_down),
                            contentDescription = "Close Lyrics",
                            tint = RogTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (track.syncedLyrics.isNotEmpty()) "Lyrics (Live)" else "Lyrics",
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary
                        )
                        Text(
                            text = "${track.title} • ${track.artist}",
                            fontSize = 11.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = RogTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.size(24.dp))
                }

                if (track.syncedLyrics.isNotEmpty()) {
                    // Sync calibration controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = RogSurfaceDark,
                            border = BorderStroke(1.dp, RogBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Nudge earlier (-0.5s)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val newOffset = userOffsetMs - 500L
                                            userOffsetMs = newOffset
                                            lyricPrefs.edit().putLong("offset_${track.id}", newOffset).apply()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "-0.5s",
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextSecondary
                                    )
                                }

                                // Nudge earlier (-0.1s)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val newOffset = userOffsetMs - 100L
                                            userOffsetMs = newOffset
                                            lyricPrefs.edit().putLong("offset_${track.id}", newOffset).apply()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "-0.1s",
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextSecondary
                                    )
                                }

                                // Sync status / reset
                                val displayOffset = userOffsetMs / 1000f
                                val offsetLabel = if (userOffsetMs == 0L) "Sync" else String.format("Sync %+.1fs", displayOffset)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            userOffsetMs = 0L
                                            lyricPrefs.edit().remove("offset_${track.id}").apply()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = offsetLabel,
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (userOffsetMs != 0L) RogTextGold else RogTextPrimary
                                    )
                                }

                                // Nudge later (+0.1s)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val newOffset = userOffsetMs + 100L
                                            userOffsetMs = newOffset
                                            lyricPrefs.edit().putLong("offset_${track.id}", newOffset).apply()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+0.1s",
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextSecondary
                                    )
                                }

                                // Nudge later (+0.5s)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val newOffset = userOffsetMs + 500L
                                            userOffsetMs = newOffset
                                            lyricPrefs.edit().putLong("offset_${track.id}", newOffset).apply()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+0.5s",
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextSecondary
                                    )
                            }
                        }
                    }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(track.syncedLyrics) { index, line ->
                            val isCurrent = index == activeLyricIndex
                            val textColor by animateColorAsState(
                                targetValue = if (isCurrent) RogTextGold else RogTextMuted,
                                animationSpec = tween(durationMillis = 200),
                                label = "lyricColor"
                            )
                            val textAlpha by animateFloatAsState(
                                targetValue = if (isCurrent) 1f else 0.55f,
                                animationSpec = tween(durationMillis = 200),
                                label = "lyricAlpha"
                            )
                            val textScale by animateFloatAsState(
                                targetValue = if (isCurrent) 1.04f else 1f,
                                animationSpec = tween(durationMillis = 200),
                                label = "lyricScale"
                            )
                            Text(
                                text = line.text,
                                fontSize = if (isCurrent) 22.sp else 18.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                lineHeight = 30.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = textAlpha
                                        scaleX = textScale
                                        scaleY = textScale
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                    }
                                    .clickable {
                                        line.timeMs?.let { onSeek(it / 1000f) }
                                    }
                            )
                        }
                    }
                } else if (track.lyrics.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(track.lyrics) { _, lineText ->
                            Text(
                                text = lineText,
                                fontSize = 18.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = RogTextPrimary,
                                lineHeight = 28.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No lyrics available for this song",
                            fontSize = 15.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted
                        )
                    }
                }
            }
        } else {
            // Main Minimal Deck
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_down),
                            contentDescription = "Collapse",
                            tint = RogTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = track.album.ifEmpty { "Playing" },
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    var showOptionsMenu by remember { mutableStateOf(false) }

                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more),
                                contentDescription = "More Options",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(RogSurfaceDark)
                                .border(1.dp, RogDivider, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Add to playlist",
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextPrimary,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_plus),
                                        contentDescription = null,
                                        tint = RogGoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onAddToPlaylistClick()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Huge Vinyl Record Disk (Rotates clockwise slowly as the song plays)
                VinylRecordDisk(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Title, Artist and Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = track.title,
                            fontSize = 20.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = track.artist,
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = RogTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Feature Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Download
                        if (isDownloading) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onDownloadClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadProgressFraction },
                                    modifier = Modifier.size(20.dp),
                                    color = RogGoldPrimary,
                                    strokeWidth = 2.dp,
                                    trackColor = RogSurfaceDark
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isDownloaded) R.drawable.ic_check else R.drawable.ic_download
                                    ),
                                    contentDescription = "Download",
                                    tint = if (isDownloaded) RogGoldPrimary else RogTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Like
                        IconButton(
                            onClick = onLikeClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (track.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                                ),
                                contentDescription = "Like",
                                tint = if (track.isLiked) RogTextGold else RogTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 8D / 16D Minimal Pill (No border)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isEightDActive) RogSurfaceHighlight else Color.Transparent)
                                .combinedClickable(
                                    onClick = onEightDClick,
                                    onLongClick = onEightDLongClick
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = eightDLabel,
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isEightDActive) RogTextGold else RogTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Slider (Minimal Slim)
                Slider(
                    value = displayPos.coerceIn(0f, durationSeconds),
                    onValueChange = {
                        isUserDragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        isUserDragging = false
                        onSeek(sliderValue)
                    },
                    valueRange = 0f..durationSeconds,
                    colors = SliderDefaults.colors(
                        thumbColor = RogTextGold,
                        activeTrackColor = RogTextGold,
                        inactiveTrackColor = RogSurfaceHighlight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Timestamp Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = elapsedString,
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )
                    Text(
                        text = remainingString,
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffleClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shuffle),
                            contentDescription = "Shuffle",
                            tint = if (isShuffleActive) RogTextGold else RogTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipPrevClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_back),
                            contentDescription = "Previous",
                            tint = RogTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Material 3 Expressive Circular Hero Play/Pause (64dp)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(RogGoldPrimary)
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = RogObsidian,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = RogObsidian,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSkipNextClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            tint = RogTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onRepeatClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_repeat),
                            contentDescription = "Repeat",
                            tint = if (isRepeatActive) RogTextGold else RogTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Material 3 Expressive Lyrics Preview Card
                val lyricsLines = if (track.syncedLyrics.isNotEmpty()) {
                    track.syncedLyrics.map { it.text }
                } else {
                    track.lyrics
                }

                if (lyricsLines.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(RogSurfaceDark)
                            .clickable { showFullLyrics = true }
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (track.syncedLyrics.isNotEmpty() || track.lyrics.isNotEmpty()) "Lyrics (Live)" else "Lyrics",
                                    fontSize = 13.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextPrimary
                                )
                                Text(
                                    text = "Expand",
                                    fontSize = 11.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = RogTextGold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val previewStart = if (activeLyricIndex >= 0) {
                                (activeLyricIndex - 1).coerceAtLeast(0)
                            } else {
                                0
                            }
                            val previewSlice = lyricsLines.drop(previewStart).take(3)

                            previewSlice.forEachIndexed { i, line ->
                                val lineIndex = previewStart + i
                                val isHighlighted = track.syncedLyrics.isNotEmpty() &&
                                    activeLyricIndex >= 0 &&
                                    lineIndex == activeLyricIndex
                                Text(
                                    text = line,
                                    fontSize = if (isHighlighted) 16.sp else 14.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHighlighted) RogTextGold else RogTextMuted,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else if (isPlaying || isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(RogSurfaceDark)
                            .clickable { showFullLyrics = true }
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lyrics",
                                fontSize = 13.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = if (isFindingLyrics) "Finding lyrics..." else "No lyrics available",
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                color = if (isFindingLyrics) RogTextGold else RogTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun VinylRecordDisk(
    thumbnailUrl: String?,
    title: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val current = rotationAngle.value
                rotationAngle.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(
                        durationMillis = 20000, // 20s per rotation (slow, smooth, realistic vinyl spin)
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Vinyl Disc body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotationAngle.value % 360f
                }
                .clip(CircleShape)
                .background(Color(0xFF0C0C0E)),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl Record Grooves, Rim & Opposing Specular Sheen
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                val maxRadius = size.minDimension / 2f

                // Outer edge rim
                drawCircle(
                    color = Color(0xFF26262D),
                    radius = maxRadius - 2f,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Run-in lead groove at vinyl edge
                drawCircle(
                    color = Color(0x35FFFFFF),
                    radius = maxRadius * 0.96f,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // 26 Micro audio track grooves
                val grooveSteps = 26
                val startRatio = 0.50f
                val endRatio = 0.93f
                val step = (endRatio - startRatio) / grooveSteps

                for (i in 0..grooveSteps) {
                    val r = maxRadius * (startRatio + i * step)
                    val alpha = if (i % 4 == 0) 0.18f else if (i % 2 == 0) 0.09f else 0.04f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = r,
                        style = Stroke(width = if (i % 4 == 0) 1.2.dp.toPx() else 0.8.dp.toPx())
                    )
                }

                // Run-out groove before label
                drawCircle(
                    color = Color(0x35FFFFFF),
                    radius = maxRadius * 0.485f,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Specular light sheen highlights (Classic butterfly / opposing cone reflection)
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x24FFFFFF), Color.Transparent),
                        center = center,
                        radius = maxRadius
                    ),
                    startAngle = 35f,
                    sweepAngle = 45f,
                    useCenter = true
                )
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x24FFFFFF), Color.Transparent),
                        center = center,
                        radius = maxRadius
                    ),
                    startAngle = 215f,
                    sweepAngle = 45f,
                    useCenter = true
                )
            }

            // Center Circular Thumbnail (The Vinyl Record Label) - 44% of disc diameter (Initial Design)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.44f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(RogSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_soundwave),
                        contentDescription = null,
                        tint = RogGoldPrimary,
                        modifier = Modifier.size(54.dp)
                    )
                }

                // Inner label perimeter ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, Color(0x40000000), CircleShape)
                )

                // Center Spindle Hole (metallic outer ring + dark hole)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E2E35)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(RogObsidian)
                    )
                }
            }
        }
    }
}
