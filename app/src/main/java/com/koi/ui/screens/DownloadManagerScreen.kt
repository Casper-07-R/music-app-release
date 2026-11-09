package com.koi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.ActiveDownloadState
import java.util.Locale

@Composable
fun DownloadManagerScreen(
    downloadedSongs: List<Track>,
    downloadedPlaylists: List<Playlist>,
    activeDownload: ActiveDownloadState,
    totalSizeBytes: Long,
    currentTrackId: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeleteSong: (Track) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onCancelActiveDownload: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onToggleLike: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var songToDelete by remember { mutableStateOf<Track?>(null) }

    val hasAnyDownloads = downloadedSongs.isNotEmpty() || downloadedPlaylists.isNotEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // --- 1. HEADER BAR ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        Column {
                            Text(
                                text = "Download Manager",
                                fontSize = 20.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Offline Storage & Playback",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = RogTextSecondary
                            )
                        }
                    }

                    if (hasAnyDownloads) {
                        TextButton(
                            onClick = { showDeleteAllDialog = true }
                        ) {
                            Text(
                                text = "Clear All",
                                fontSize = 13.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 2. STORAGE & OFFLINE STATUS CARD ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RogSurfaceDark)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(RogSurfaceHighlight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_download),
                                        contentDescription = null,
                                        tint = RogGoldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "${downloadedSongs.size} Songs • ${downloadedPlaylists.size} Playlists",
                                        fontSize = 15.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatSize(totalSizeBytes)} storage used",
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary
                                    )
                                }
                            }

                            // Offline Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(RogSurfaceHighlight)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Offline ready",
                                        tint = RogGoldPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Offline Ready",
                                        fontSize = 11.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RogGoldPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Downloaded songs stream directly from device storage without an internet connection.",
                            fontSize = 11.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }

                // --- 3. ACTIVE DOWNLOAD PROGRESS CARD ---
                AnimatedVisibility(visible = activeDownload.isDownloading) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(RogSurfaceMedium)
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (!activeDownload.playlistTitle.isNullOrEmpty()) {
                                                "Downloading Playlist: ${activeDownload.playlistTitle}"
                                            } else {
                                                "Downloading: ${activeDownload.currentItemTitle}"
                                            },
                                            fontSize = 13.5.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogGoldPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val subtext = if (activeDownload.totalCount > 0) {
                                            "${activeDownload.completedCount} of ${activeDownload.totalCount} tracks (${(activeDownload.progressFraction * 100).toInt()}%)"
                                        } else {
                                            "Preparing stream..."
                                        }
                                        Text(
                                            text = subtext,
                                            fontSize = 11.5.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextSecondary
                                        )
                                    }

                                    TextButton(
                                        onClick = onCancelActiveDownload,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 12.sp,
                                            fontFamily = CircularFontFamily,
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { activeDownload.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = RogGoldPrimary,
                                    trackColor = RogSurfaceDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 4. TAB SELECTOR ---
                val tabs = listOf(
                    "Songs (${downloadedSongs.size})",
                    "Playlists (${downloadedPlaylists.size})"
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = RogGoldPrimary,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = RogGoldPrimary,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = RogDivider)
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTabIndex == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) RogTextGold else RogTextSecondary
                                )
                            }
                        )
                    }
                }
            }
        }

        // --- 5. TAB CONTENT ---
        if (selectedTabIndex == 0) {
            // SONGS TAB
            if (downloadedSongs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = null,
                                tint = RogTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No downloaded songs yet",
                            fontSize = 16.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Download songs from the player or playlists to listen offline anywhere without an internet connection.",
                            fontSize = 12.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                // Play all button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (downloadedSongs.isNotEmpty()) {
                                    onTrackClick(downloadedSongs.first(), downloadedSongs)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RogGoldPrimary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play),
                                contentDescription = null,
                                tint = RogObsidian,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All Offline",
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogObsidian,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "${downloadedSongs.size} tracks",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted
                        )
                    }
                }

                itemsIndexed(downloadedSongs) { index, track ->
                    val isPlayingThis = track.id == currentTrackId && isPlaying
                    DownloadedTrackItemRow(
                        track = track,
                        index = index,
                        isPlayingCurrentTrack = isPlayingThis,
                        onTrackClick = { onTrackClick(track, downloadedSongs) },
                        onDeleteClick = { songToDelete = track },
                        onLikeClick = { onToggleLike(track) }
                    )
                }
            }
        } else {
            // PLAYLISTS TAB
            if (downloadedPlaylists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_library),
                                contentDescription = null,
                                tint = RogTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No downloaded playlists yet",
                            fontSize = 16.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Open any playlist and tap the download button to save the entire playlist for offline listening.",
                            fontSize = 12.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                itemsIndexed(downloadedPlaylists) { _, playlist ->
                    DownloadedPlaylistItemCard(
                        playlist = playlist,
                        onCardClick = { onPlaylistClick(playlist) },
                        onPlayClick = { onPlayPlaylist(playlist) },
                        onDeleteClick = { playlistToDelete = playlist }
                    )
                }
            }
        }
    }

    // Dialog: Delete single song
    songToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = {
                Text(
                    text = "Remove download?",
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${track.title}\" from downloaded songs? The file will be removed from device storage.",
                    fontFamily = CircularFontFamily,
                    fontSize = 13.5.sp,
                    color = RogTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSong(track)
                        songToDelete = null
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Cancel", color = RogTextSecondary)
                }
            },
            containerColor = RogSurfaceDark
        )
    }

    // Dialog: Delete single playlist
    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = {
                Text(
                    text = "Remove playlist download?",
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${pl.title}\" from downloaded playlists? Audio files that are not in other playlists will also be freed from storage.",
                    fontFamily = CircularFontFamily,
                    fontSize = 13.5.sp,
                    color = RogTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(pl)
                        playlistToDelete = null
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = RogTextSecondary)
                }
            },
            containerColor = RogSurfaceDark
        )
    }

    // Dialog: Clear all downloads
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = "Clear all downloads?",
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will remove all downloaded songs and playlists from offline storage. You will need internet access to stream them again.",
                    fontFamily = CircularFontFamily,
                    fontSize = 13.5.sp,
                    color = RogTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllDownloads()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Clear All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel", color = RogTextSecondary)
                }
            },
            containerColor = RogSurfaceDark
        )
    }
}

@Composable
private fun DownloadedTrackItemRow(
    track: Track,
    index: Int,
    isPlayingCurrentTrack: Boolean,
    onTrackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlayingCurrentTrack) RogSurfaceHighlight else Color.Transparent)
            .clickable { onTrackClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded corners
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RogSurfaceMedium),
            contentAlignment = Alignment.Center
        ) {
            if (!track.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_soundwave),
                    contentDescription = null,
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isPlayingCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RogObsidian.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_soundwave),
                        contentDescription = "Playing",
                        tint = RogGoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 13.5.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Medium,
                color = if (isPlayingCurrentTrack) RogTextGold else RogTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Offline ready",
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = track.artist,
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Like button
        IconButton(
            onClick = onLikeClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                painter = painterResource(
                    id = if (track.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                ),
                contentDescription = "Like",
                tint = if (track.isLiked) RogGoldPrimary else RogTextMuted,
                modifier = Modifier.size(17.dp)
            )
        }

        // Delete button
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete download",
                tint = RogTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DownloadedPlaylistItemCard(
    playlist: Playlist,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RogSurfaceDark)
            .clickable { onCardClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist cover
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RogSurfaceMedium),
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
                            id = if (playlist.isLikedSongs) R.drawable.ic_heart_filled else R.drawable.ic_library
                        ),
                        contentDescription = null,
                        tint = RogGoldPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Playlist info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.title,
                    fontSize = 15.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = RogGoldPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${playlist.trackCount} songs • ${playlist.creator}",
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play offline button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(RogSurfaceHighlight)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play),
                    contentDescription = "Play offline",
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete downloaded playlist",
                    tint = RogTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.2f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
