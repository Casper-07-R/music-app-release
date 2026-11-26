package com.koi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.components.FilterChipRow
import com.koi.ui.components.TutorialTarget
import com.koi.ui.model.Playlist
import com.koi.ui.theme.*

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    userName: String = "Listener",
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onEditPlaylistClick: (Playlist) -> Unit = {},
    onSharePlaylistClick: (Playlist) -> Unit = {},
    onScanQrClick: () -> Unit = {},
    onImportQrImageUri: (Uri) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onTargetPositioned: (TutorialTarget, Rect) -> Unit = { _, _ -> },
    tutorialStep: Int? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(tutorialStep) {
        if (tutorialStep != null) {
            listState.animateScrollToItem(0)
        }
    }
    val qrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportQrImageUri(uri)
        }
    }
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredPlaylists = remember(playlists, selectedFilter) {
        when (selectedFilter) {
            "Playlists" -> playlists.filter { !it.isLikedSongs }
            "Favorites" -> playlists.filter { it.isLikedSongs }
            else -> playlists
        }
    }
    val initial = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Minimal Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceHighlight)
                                .clickable { onProfileClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextGold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Your Library",
                            fontSize = 22.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onScanQrClick) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = "Scan Playlist QR",
                                tint = RogGoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onCreatePlaylistClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_plus),
                                contentDescription = "Create Playlist",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilterChipRow(
                    chips = listOf("All", "Playlists", "Favorites"),
                    selectedChip = selectedFilter,
                    onChipSelected = { selectedFilter = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick QR Import Actions in Playlist Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coords -> onTargetPositioned(TutorialTarget.QR_PLAYLIST_SHARING, coords.boundsInRoot()) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(RogSurfaceDark)
                            .clickable { onScanQrClick() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan QR",
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(RogSurfaceDark)
                            .clickable { qrImagePickerLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Image",
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Playlist Items
        items(filteredPlaylists) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Artwork thumbnail (Rounded container)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RogSurfaceDark),
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Playlist • ${playlist.trackCount} songs",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (playlist.trackCount > 0 || !playlist.isLikedSongs) {
                    IconButton(
                        onClick = { onSharePlaylistClick(playlist) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Share Playlist QR",
                            tint = if (playlist.trackCount > 0) RogTextSecondary else RogTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!playlist.isLikedSongs) {
                    IconButton(
                        onClick = { onEditPlaylistClick(playlist) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit",
                            tint = RogTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeletePlaylistClick(playlist) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Delete",
                            tint = RogTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
