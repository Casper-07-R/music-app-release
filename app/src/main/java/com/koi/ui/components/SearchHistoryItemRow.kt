package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.SearchHistoryEntry
import com.koi.ui.model.SearchHistoryType
import com.koi.ui.theme.*

@Composable
fun SearchHistoryItemRow(
    entry: SearchHistoryEntry,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (entry.type) {
            SearchHistoryType.TRACK -> {
                val track = entry.track
                // Track Artwork (Spotify-style 4dp rounded cover)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RogSurfaceMedium),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track?.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = track?.thumbnailUrl,
                            contentDescription = track?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_headphones),
                            contentDescription = null,
                            tint = RogTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(RogObsidian.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_soundwave),
                                contentDescription = "Playing",
                                tint = RogTextGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Subtitle: "Song • Artist"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = track?.title ?: "",
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (isPlaying) RogTextGold else RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Song • ${track?.artist ?: ""}",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SearchHistoryType.QUERY -> {
                // Circular search icon badge
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(RogSurfaceMedium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Query text & "Search" subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = entry.query ?: "",
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Search",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SearchHistoryType.ARTIST -> {
                val artist = entry.artist
                // Artist circular avatar (Spotify/YT Music style circular avatar)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceMedium),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artist?.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = artist?.thumbnailUrl,
                            contentDescription = artist?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile),
                            contentDescription = null,
                            tint = RogTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & "Artist" subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = artist?.name ?: "",
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val cleanAudience = artist?.subscribersOrAudience?.replace(Regex("""^Artist\s*•\s*""", RegexOption.IGNORE_CASE), "")?.trim()
                    Text(
                        text = if (!cleanAudience.isNullOrBlank()) "Artist • $cleanAudience" else "Artist",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SearchHistoryType.PLAYLIST -> {
                val playlist = entry.playlist
                // Playlist square cover
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RogSurfaceMedium),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playlist?.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = playlist?.thumbnailUrl,
                            contentDescription = playlist?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_library),
                            contentDescription = null,
                            tint = RogTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & "Playlist" subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = playlist?.title ?: "",
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = (if (playlist?.isAlbum == true) "Album" else "Playlist") + if (!playlist?.author.isNullOrBlank()) " • ${playlist?.author}" else "",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Close / Remove item icon button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove from history",
                tint = RogTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
