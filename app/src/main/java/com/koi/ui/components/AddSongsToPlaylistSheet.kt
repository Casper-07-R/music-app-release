package com.koi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistSheet(
    playlist: Playlist,
    searchQuery: String,
    searchResults: List<Track>,
    isSearching: Boolean,
    suggestedTracks: List<Track> = emptyList(),
    onQueryChange: (String) -> Unit,
    onAddTrack: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentTrackIds = playlist.tracks.map { it.id }.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = RogSurfaceDark,
        scrimColor = RogObsidian.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RogSurfaceHighlight)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADD SONGS TO",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.title,
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search songs, artists...",
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Clear",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = RogTextPrimary,
                    unfocusedTextColor = RogTextPrimary,
                    focusedContainerColor = RogSurfaceMedium,
                    unfocusedContainerColor = RogSurfaceMedium,
                    cursorColor = RogGoldPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results / Suggestions List
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = RogGoldPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs found for \"$searchQuery\"",
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(searchResults, key = { it.id }) { track ->
                            val isInPlaylist = currentTrackIds.contains(track.id)
                            AddSongRow(
                                track = track,
                                isInPlaylist = isInPlaylist,
                                onAddClick = {
                                    onAddTrack(track)
                                    Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                                },
                                onRemoveClick = {
                                    onRemoveTrack(track)
                                    Toast.makeText(context, "Removed from ${playlist.title}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            } else {
                // Empty search query: show suggested tracks if available
                val deduplicatedSuggestions = suggestedTracks.distinctBy { it.id }
                if (deduplicatedSuggestions.isNotEmpty()) {
                    Text(
                        text = "Suggested songs",
                        fontSize = 13.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = RogTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(deduplicatedSuggestions, key = { it.id }) { track ->
                            val isInPlaylist = currentTrackIds.contains(track.id)
                            AddSongRow(
                                track = track,
                                isInPlaylist = isInPlaylist,
                                onAddClick = {
                                    onAddTrack(track)
                                    Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                                },
                                onRemoveClick = {
                                    onRemoveTrack(track)
                                    Toast.makeText(context, "Removed from ${playlist.title}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                tint = RogTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Type above to search songs to add",
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSongRow(
    track: Track,
    isInPlaylist: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RogSurfaceDark)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!track.thumbnailUrl.isNullOrEmpty()) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RogSurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_soundwave),
                    contentDescription = null,
                    tint = RogTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Medium,
                color = RogTextPrimary,
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

        Spacer(modifier = Modifier.width(8.dp))

        // Add / Remove action button
        IconButton(
            onClick = {
                if (isInPlaylist) {
                    onRemoveClick()
                } else {
                    onAddClick()
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            if (isInPlaylist) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RogGoldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Added",
                        tint = RogObsidian,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = "Add",
                        tint = RogTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
