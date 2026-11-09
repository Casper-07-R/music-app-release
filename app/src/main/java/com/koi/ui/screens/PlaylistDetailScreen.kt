package com.koi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.components.TrackListItem
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentTrackId: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayAllClick: () -> Unit,
    onLikeTrackClick: (Track) -> Unit,
    onMoreTrackClick: (Track) -> Unit,
    onAddSongsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Minimal Hero Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Back button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_back),
                        contentDescription = "Back",
                        tint = RogTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Square Cover Artwork (Clean, no borders)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RogSurfaceDark)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playlist.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = playlist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (playlist.isLikedSongs) R.drawable.ic_heart_filled else R.drawable.ic_soundwave
                            ),
                            contentDescription = null,
                            tint = RogTextGold,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Playlist Title
                Text(
                    text = playlist.title,
                    fontSize = 22.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Description
                Text(
                    text = playlist.description.ifEmpty { "Collection by ${playlist.creator}" },
                    fontSize = 12.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = RogTextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Creator and Track count
                Text(
                    text = "${playlist.creator} • ${playlist.trackCount} songs",
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = RogTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row & Minimal Play FAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { /* Download */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Download",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Add Songs Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(RogSurfaceHighlight)
                                .clickable { onAddSongsClick() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_plus),
                                    contentDescription = "Add songs",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add songs",
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary
                                )
                            }
                        }
                    }

                    // Minimal Circular Play FAB
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(RogTextGold)
                            .clickable { onPlayAllClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = RogObsidian,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Empty Playlist State
        if (playlist.tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(RogSurfaceHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = null,
                            tint = RogTextGold,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "This playlist is empty",
                        fontSize = 17.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Find and add songs to start listening",
                        fontSize = 13.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onAddSongsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = RogGoldPrimary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = null,
                            tint = RogObsidian,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add songs",
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogObsidian,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }

        // Track List
        itemsIndexed(playlist.tracks) { index, track ->
            TrackListItem(
                track = track,
                index = index,
                isPlayingCurrentTrack = track.id == currentTrackId,
                onTrackClick = { onTrackClick(track, playlist.tracks) },
                onLikeClick = { onLikeTrackClick(track) },
                onMoreClick = { onMoreTrackClick(track) }
            )
        }

        // Add more songs footer button when tracks exist
        if (playlist.tracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RogSurfaceDark)
                        .clickable { onAddSongsClick() }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = null,
                        tint = RogTextGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to this playlist",
                        fontSize = 14.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                }
            }
        }
    }
}
