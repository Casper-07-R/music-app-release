package com.koi.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onEditPlaylistClick: () -> Unit = {},
    onChangeCoverClick: (String) -> Unit = {},
    onRemoveTracks: (Set<String>) -> Unit = {},
    isPlaylistDownloaded: Boolean = false,
    isPlaylistDownloading: Boolean = false,
    downloadProgressFraction: Float = 0f,
    downloadedTrackIds: Set<String> = emptySet(),
    onDownloadPlaylistClick: () -> Unit = {},
    isAddedAsPlaylist: Boolean = false,
    onAddAsPlaylistClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isManageMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<String>() }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    val activeManageMode = isManageMode && !playlist.isAlbum

    BackHandler(enabled = activeManageMode) {
        isManageMode = false
        selectedTrackIds.clear()
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onChangeCoverClick(uri.toString())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
    ) {
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = (if (activeManageMode) 230.dp else 145.dp) + navBarPadding)
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

                    // Square Cover Artwork (Clean, no borders, with Camera change badge if not Liked Songs and not Album)
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RogSurfaceDark)
                            .align(Alignment.CenterHorizontally)
                            .clickable(enabled = !playlist.isLikedSongs && !playlist.isAlbum) {
                                coverPickerLauncher.launch("image/*")
                            },
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

                        if (!playlist.isLikedSongs && !playlist.isAlbum) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(RogSurfaceDark.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_camera),
                                    contentDescription = "Change Cover",
                                    tint = RogGoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Playlist/Album Title & Rename/Edit Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = playlist.title,
                            fontSize = 22.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!playlist.isLikedSongs && !playlist.isAlbum) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onEditPlaylistClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "Edit Playlist",
                                    tint = RogGoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Description
                    Text(
                        text = playlist.description.ifEmpty {
                            if (playlist.isAlbum) "Album by ${playlist.creator}" else "Collection by ${playlist.creator}"
                        },
                        fontSize = 12.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Creator and Track count
                    Text(
                        text = if (playlist.isAlbum) {
                            val parts = mutableListOf<String>()
                            if (playlist.creator.isNotBlank() && playlist.creator != "Koi") parts.add(playlist.creator)
                            if (playlist.trackCount > 0) parts.add("${playlist.trackCount} songs")
                            parts.joinToString(" • ").ifEmpty { "${playlist.trackCount} songs" }
                        } else {
                            "${playlist.creator} • ${playlist.trackCount} songs"
                        },
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
                            if (isPlaylistDownloading) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { onDownloadPlaylistClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgressFraction },
                                        modifier = Modifier.size(24.dp),
                                        color = RogGoldPrimary,
                                        strokeWidth = 2.5.dp,
                                        trackColor = Color.Transparent
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close),
                                        contentDescription = "Cancel Download",
                                        tint = RogGoldPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onDownloadPlaylistClick,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isPlaylistDownloaded) R.drawable.ic_check else R.drawable.ic_download
                                        ),
                                        contentDescription = if (isPlaylistDownloaded) "Downloaded" else "Download Playlist",
                                        tint = if (isPlaylistDownloaded) RogGoldPrimary else RogTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (playlist.isAlbum) {
                                // Add as Playlist Button (Only option for album)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isAddedAsPlaylist) RogSurfaceDark else RogSurfaceHighlight)
                                        .clickable { onAddAsPlaylistClick() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isAddedAsPlaylist) R.drawable.ic_check else R.drawable.ic_plus
                                            ),
                                            contentDescription = if (isAddedAsPlaylist) "Added to Playlists" else "Add as playlist",
                                            tint = if (isAddedAsPlaylist) RogGoldPrimary else RogTextGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAddedAsPlaylist) "Added to Playlists" else "Add as playlist",
                                            fontSize = 13.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAddedAsPlaylist) RogGoldPrimary else RogTextPrimary
                                        )
                                    }
                                }
                            } else {
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

                                // Manage / Select Mode Button (Only when tracks exist)
                                if (playlist.tracks.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (activeManageMode) RogGoldPrimary else RogSurfaceHighlight)
                                            .clickable {
                                                isManageMode = !isManageMode
                                                selectedTrackIds.clear()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = if (activeManageMode) R.drawable.ic_close else R.drawable.ic_checklist),
                                                contentDescription = if (activeManageMode) "Done" else "Manage songs",
                                                tint = if (activeManageMode) RogObsidian else RogTextGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (activeManageMode) "Done" else "Manage",
                                                fontSize = 13.sp,
                                                fontFamily = CircularFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (activeManageMode) RogObsidian else RogTextPrimary
                                            )
                                        }
                                    }
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

            // In Manage Mode: Select All / Deselect All Action Bar (Only for non-albums)
            if (activeManageMode && playlist.tracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RogSurfaceDark)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTrackIds.isEmpty()) "Select songs to remove" else "${selectedTrackIds.size} of ${playlist.tracks.size} selected",
                            fontSize = 13.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedTrackIds.isNotEmpty()) RogGoldPrimary else RogTextSecondary
                        )

                        Text(
                            text = if (selectedTrackIds.size == playlist.tracks.size) "Deselect All" else "Select All",
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogGoldPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (selectedTrackIds.size == playlist.tracks.size) {
                                        selectedTrackIds.clear()
                                    } else {
                                        selectedTrackIds.clear()
                                        selectedTrackIds.addAll(playlist.tracks.map { it.id })
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Empty State
            if (playlist.tracks.isEmpty()) {
                item {
                    if (playlist.isAlbum) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = RogGoldPrimary,
                                strokeWidth = 3.dp
                            )
                        }
                    } else {
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
            }

            // Track List
            itemsIndexed(
                items = playlist.tracks,
                key = { index, track -> "${playlist.id}_${track.id}_$index" }
            ) { index, track ->
                val isDownloaded = downloadedTrackIds.contains(track.id) || track.isDownloaded
                val thumb = track.thumbnailUrl?.ifBlank { null }
                    ?: playlist.thumbnailUrl?.ifBlank { null }
                    ?: "https://i.ytimg.com/vi/${track.id}/hqdefault.jpg"
                val trackWithThumb = track.copy(
                    thumbnailUrl = thumb,
                    isDownloaded = isDownloaded
                )
                TrackListItem(
                    track = trackWithThumb,
                    index = index,
                    isPlayingCurrentTrack = track.id == currentTrackId,
                    onTrackClick = { onTrackClick(trackWithThumb, playlist.tracks) },
                    onLikeClick = { onLikeTrackClick(trackWithThumb) },
                    onMoreClick = { onMoreTrackClick(trackWithThumb) },
                    isSelectable = activeManageMode,
                    isSelected = selectedTrackIds.contains(track.id),
                    onSelectToggle = if (activeManageMode) {
                        {
                            if (selectedTrackIds.contains(track.id)) {
                                selectedTrackIds.remove(track.id)
                            } else {
                                selectedTrackIds.add(track.id)
                            }
                        }
                    } else null,
                    onLongClick = if (!playlist.isAlbum) {
                        {
                            if (!isManageMode) {
                                isManageMode = true
                                selectedTrackIds.clear()
                                selectedTrackIds.add(track.id)
                            }
                        }
                    } else null
                )
            }

            // Add more songs footer button when tracks exist and not in manage mode (Only for non-albums)
            if (!playlist.isAlbum && playlist.tracks.isNotEmpty() && !activeManageMode) {
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

        // Floating Bottom Removal Bar in Manage Mode (Properly elevated above NowPlayingMiniBar and KoiBottomNavBar)
        AnimatedVisibility(
            visible = activeManageMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 148.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = RogSurfaceDark,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isManageMode = false
                                selectedTrackIds.clear()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Cancel manage mode",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${selectedTrackIds.size} selected",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = if (selectedTrackIds.isEmpty()) "Tap songs to select" else "Ready to remove",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedTrackIds.isNotEmpty()) {
                                showConfirmDeleteDialog = true
                            }
                        },
                        enabled = selectedTrackIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            disabledContainerColor = RogSurfaceHighlight
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trash),
                            contentDescription = "Remove",
                            tint = if (selectedTrackIds.isNotEmpty()) Color.White else RogTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedTrackIds.isEmpty()) "Remove" else "Remove (${selectedTrackIds.size})",
                            fontSize = 13.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTrackIds.isNotEmpty()) Color.White else RogTextMuted
                        )
                    }
                }
            }
        }

        // Confirmation Dialog
        if (showConfirmDeleteDialog && activeManageMode) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteDialog = false },
                title = {
                    Text(
                        text = "Remove from playlist?",
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                },
                text = {
                    val count = selectedTrackIds.size
                    Text(
                        text = if (count == 1) {
                            val trackTitle = playlist.tracks.find { it.id == selectedTrackIds.first() }?.title ?: "this song"
                            "Are you sure you want to remove \"$trackTitle\" from this playlist?"
                        } else {
                            "Are you sure you want to remove $count songs from this playlist?"
                        },
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toRemove = selectedTrackIds.toSet()
                            onRemoveTracks(toRemove)
                            selectedTrackIds.clear()
                            isManageMode = false
                            showConfirmDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Remove",
                            color = Color.White,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmDeleteDialog = false }
                    ) {
                        Text(
                            text = "Cancel",
                            color = RogTextSecondary,
                            fontFamily = CircularFontFamily
                        )
                    }
                },
                containerColor = RogSurfaceDark,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

