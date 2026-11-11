package com.koi.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.backend.innertube.ArtistAlbumItem
import com.koi.backend.innertube.ArtistItem
import com.koi.backend.innertube.ArtistProfile
import com.koi.backend.innertube.PlaylistItem
import com.koi.backend.innertube.SongItem
import com.koi.ui.R
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@Composable
fun ArtistProfileScreen(
    profile: ArtistProfile?,
    isLoading: Boolean,
    isFollowed: Boolean,
    currentTrackId: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayAllClick: (List<SongItem>) -> Unit,
    onShuffleClick: (List<SongItem>) -> Unit = {},
    onSongClick: (SongItem, List<SongItem>) -> Unit,
    onAlbumClick: (ArtistAlbumItem) -> Unit,
    onSingleClick: (ArtistAlbumItem) -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onSimilarArtistClick: (ArtistItem) -> Unit,
    onToggleFollowClick: () -> Unit,
    onLikeSongClick: (Track) -> Unit = {},
    onMoreSongClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading && profile == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(RogObsidian)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = RogTextGold,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading artist profile...",
                    fontSize = 14.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary
                )
            }
        }
        return
    }

    if (profile == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(RogObsidian)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_profile),
                    contentDescription = null,
                    tint = RogTextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Artist profile unavailable",
                    fontSize = 16.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unable to load artist information at this time.",
                    fontSize = 13.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(RogSurfaceHighlight)
                        .clickable { onBackClick() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Go Back",
                        fontSize = 13.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = RogTextPrimary
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    var isBioExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Hero Header Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Artist Banner / Hero Image
                    val heroImage = profile.headerThumbnailUrl ?: profile.avatarThumbnailUrl
                    if (!heroImage.isNullOrEmpty()) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = profile.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(RogSurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_profile),
                                contentDescription = null,
                                tint = RogTextMuted,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    // Scrim gradient overlay (fade into obsidian background)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        RogObsidian.copy(alpha = 0.35f),
                                        RogObsidian.copy(alpha = 0.5f),
                                        RogObsidian.copy(alpha = 0.95f),
                                        RogObsidian
                                    )
                                )
                            )
                    )

                    // Hero Content (Name, Audience, Action Buttons)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Verified / Stats subtitle
                        val audienceText = profile.subscriberCount
                            ?: profile.monthlyAudience
                            ?: "Verified Artist"
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Verified",
                                tint = RogTextGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verified Artist • $audienceText",
                                fontSize = 12.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = RogTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Artist Name
                        Text(
                            text = profile.name,
                            fontSize = 32.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Follow, Play All, Shuffle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Follow Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isFollowed) RogTextGold else RogSurfaceHighlight,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .background(if (isFollowed) RogTextGold.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { onToggleFollowClick() }
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isFollowed) "Following" else "Follow",
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFollowed) RogTextGold else RogTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Shuffle Icon Button
                            if (profile.topSongs.isNotEmpty()) {
                                IconButton(
                                    onClick = { onShuffleClick(profile.topSongs) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(RogSurfaceMedium)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shuffle),
                                        contentDescription = "Shuffle",
                                        tint = RogTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Big Round Play Button
                            if (profile.topSongs.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(RogTextGold)
                                        .clickable { onPlayAllClick(profile.topSongs) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "Play Top Songs",
                                        tint = RogObsidian,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Popular / Top Songs Section
            if (profile.topSongs.isNotEmpty()) {
                item {
                    Text(
                        text = "Popular",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
                    )
                }

                itemsIndexed(profile.topSongs.take(10)) { index, song ->
                    val isCurrent = song.videoId == currentTrackId
                    val track = Track(
                        id = song.videoId,
                        title = song.title,
                        artist = song.artists,
                        album = song.album ?: "",
                        durationSeconds = song.durationSeconds,
                        thumbnailUrl = song.thumbnailUrl
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, profile.topSongs) }
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Index or Soundwave
                        Box(
                            modifier = Modifier.width(28.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isCurrent && isPlaying) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_soundwave),
                                    contentDescription = "Playing",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 14.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCurrent) RogTextGold else RogTextSecondary
                                )
                            }
                        }

                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RogSurfaceMedium)
                        ) {
                            if (!song.thumbnailUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Subtitle
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = song.title,
                                fontSize = 14.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = if (isCurrent) RogTextGold else RogTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artists,
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Duration or Options
                        if (!song.duration.isNullOrEmpty()) {
                            Text(
                                text = song.duration,
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        IconButton(
                            onClick = { onLikeSongClick(track) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_heart),
                                contentDescription = "Like",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { onMoreSongClick(track) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more),
                                contentDescription = "More",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Albums Discography Section
            if (profile.albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(profile.albums) { album ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onAlbumClick(album) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogSurfaceMedium)
                                ) {
                                    if (!album.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = album.thumbnailUrl,
                                            contentDescription = album.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.title,
                                    fontSize = 13.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!album.year.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = album.year,
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Singles & EPs Section
            if (profile.singles.isNotEmpty()) {
                item {
                    Text(
                        text = "Singles & EPs",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(profile.singles) { single ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onSingleClick(single) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogSurfaceMedium)
                                ) {
                                    if (!single.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = single.thumbnailUrl,
                                            contentDescription = single.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = single.title,
                                    fontSize = 13.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!single.year.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = single.year,
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Featured In / Playlists Section
            if (profile.featuredIn.isNotEmpty()) {
                item {
                    Text(
                        text = "Featured On",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(profile.featuredIn) { pl ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onPlaylistClick(pl) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogSurfaceMedium)
                                ) {
                                    if (!pl.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = pl.thumbnailUrl,
                                            contentDescription = pl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pl.title,
                                    fontSize = 13.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!pl.author.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pl.author,
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fans Also Like / Similar Artists Section
            if (profile.similarArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Fans Also Like",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(profile.similarArtists) { similar ->
                            Column(
                                modifier = Modifier
                                    .width(104.dp)
                                    .clickable { onSimilarArtistClick(similar) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Circular Artist Avatar
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(CircleShape)
                                        .background(RogSurfaceMedium)
                                ) {
                                    if (!similar.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = similar.thumbnailUrl,
                                            contentDescription = similar.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_profile),
                                            contentDescription = null,
                                            tint = RogTextMuted,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = similar.name,
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Artist",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // About / Bio Section
            val bioText = profile.bio ?: profile.description
            if (!bioText.isNullOrBlank()) {
                item {
                    Text(
                        text = "About",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RogSurfaceMedium)
                            .clickable { isBioExpanded = !isBioExpanded }
                            .padding(18.dp)
                    ) {
                        Column {
                            if (!profile.subscriberCount.isNullOrEmpty()) {
                                Text(
                                    text = "${profile.subscriberCount} Subscribers",
                                    fontSize = 18.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextGold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Text(
                                text = bioText,
                                fontSize = 13.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = RogTextPrimary.copy(alpha = 0.9f),
                                lineHeight = 20.sp,
                                maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (bioText.length > 200) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isBioExpanded) "Show less" else "Read more",
                                    fontSize = 12.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextGold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pinned Top Floating Action Bar with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(RogObsidian.copy(alpha = 0.65f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = RogTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
