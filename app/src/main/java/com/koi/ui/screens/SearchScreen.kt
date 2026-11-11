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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.backend.innertube.ArtistItem
import com.koi.backend.innertube.ComprehensiveSearchResult
import com.koi.backend.innertube.PlaylistItem
import com.koi.backend.innertube.SearchFilterType
import com.koi.backend.innertube.SongItem
import com.koi.ui.R
import com.koi.ui.components.GenreBrowseCard
import com.koi.ui.components.SearchHistoryItemRow
import com.koi.ui.components.TrackListItem
import com.koi.ui.model.GenreCategory
import com.koi.ui.model.SearchHistoryEntry
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@Composable
fun SearchScreen(
    categories: List<GenreCategory>,
    searchQuery: String,
    searchResults: List<Track>,
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    isSearching: Boolean,
    currentTrackId: String,
    searchFilter: SearchFilterType = SearchFilterType.ALL,
    comprehensiveResult: ComprehensiveSearchResult? = null,
    artistResults: List<ArtistItem> = emptyList(),
    playlistResults: List<PlaylistItem> = emptyList(),
    onFilterSelected: (SearchFilterType) -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (ArtistItem) -> Unit = {},
    onPlaylistClick: (PlaylistItem) -> Unit = {},
    onHistoryItemClick: (SearchHistoryEntry) -> Unit = {},
    onRemoveHistoryItem: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onLikeTrackClick: (Track) -> Unit,
    onMoreTrackClick: (Track) -> Unit,
    onCategoryClick: (GenreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
            .statusBarsPadding()
    ) {
        // Minimal Search Header Title
        Text(
            text = "Search",
            fontSize = 24.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Minimal Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RogSurfaceMedium)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = "Search",
                tint = RogTextSecondary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Songs, artists, playlists...",
                        fontSize = 13.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextMuted
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    textStyle = TextStyle(
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = RogTextPrimary
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(RogTextGold),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearchSubmit(searchQuery)
                            }
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isSearching) {
                CircularProgressIndicator(
                    color = RogTextGold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Filter Chips Row (Spotify / YT Music Style)
        if (searchQuery.trim().isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilterType.values()) { filter ->
                    val isSelected = filter == searchFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) RogTextGold else RogSurfaceMedium)
                            .clickable { onFilterSelected(filter) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 12.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RogObsidian else RogTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Content Area: Results list or Browse Categories Grid with Recent Searches
        if (searchQuery.trim().isNotEmpty()) {
            when (searchFilter) {
                SearchFilterType.ALL -> {
                    // Comprehensive mixed search results: Top result, Songs, Artists, Playlists
                    val comp = comprehensiveResult
                    val hasAnyResult = (comp != null && (comp.topResult != null || comp.songs.isNotEmpty() || comp.artists.isNotEmpty() || comp.playlists.isNotEmpty())) || searchResults.isNotEmpty()

                    if (!hasAnyResult && !isSearching) {
                        EmptySearchState(query = searchQuery)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 140.dp)
                        ) {
                            // 1. Top Result Card
                            comp?.topResult?.let { top ->
                                item {
                                    Text(
                                        text = "Top result",
                                        fontSize = 18.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextPrimary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(RogSurfaceMedium)
                                            .clickable {
                                                when (top.type) {
                                                    "ARTIST" -> {
                                                        val artist = top.artistItem ?: ArtistItem(
                                                            id = top.id,
                                                            name = top.title,
                                                            subscribersOrAudience = top.subtitle,
                                                            thumbnailUrl = top.thumbnailUrl
                                                        )
                                                        onArtistClick(artist)
                                                    }
                                                    "SONG" -> {
                                                        val track = Track(
                                                            id = top.id,
                                                            title = top.title,
                                                            artist = top.subtitle ?: "",
                                                            thumbnailUrl = top.thumbnailUrl
                                                        )
                                                        onTrackClick(track, searchResults)
                                                    }
                                                    "PLAYLIST", "ALBUM" -> {
                                                        val pl = top.playlistItem ?: PlaylistItem(
                                                            id = top.id,
                                                            title = top.title,
                                                            author = top.subtitle,
                                                            thumbnailUrl = top.thumbnailUrl,
                                                            isAlbum = top.type == "ALBUM"
                                                        )
                                                        onPlaylistClick(pl)
                                                    }
                                                }
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Image: Circular if artist, square otherwise
                                            val isArtist = top.type == "ARTIST"
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                                                    .background(RogSurfaceDark)
                                            ) {
                                                if (!top.thumbnailUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = top.thumbnailUrl,
                                                        contentDescription = top.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(id = if (isArtist) R.drawable.ic_profile else R.drawable.ic_headphones),
                                                        contentDescription = null,
                                                        tint = RogTextMuted,
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .align(Alignment.Center)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = top.title,
                                                    fontSize = 17.sp,
                                                    fontFamily = CircularFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RogTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Type pill badge
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(RogTextGold.copy(alpha = 0.2f))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = when (top.type) {
                                                                "ARTIST" -> "Artist"
                                                                "ALBUM" -> "Album"
                                                                "PLAYLIST" -> "Playlist"
                                                                else -> "Song"
                                                            },
                                                            fontSize = 10.5.sp,
                                                            fontFamily = CircularFontFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            color = RogTextGold
                                                        )
                                                    }
                                                    if (!top.subtitle.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "• ${top.subtitle}",
                                                            fontSize = 12.5.sp,
                                                            fontFamily = CircularFontFamily,
                                                            color = RogTextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // Trailing Action: Play button for songs, Chevron for artist/playlist
                                            if (top.type == "SONG") {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(RogTextGold),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_play),
                                                        contentDescription = "Play",
                                                        tint = RogObsidian,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Songs Section (top 4-5)
                            val songList = if (comp != null && comp.songs.isNotEmpty()) {
                                comp.songs.map { song ->
                                    Track(
                                        id = song.videoId,
                                        title = song.title,
                                        artist = song.artists,
                                        album = song.album ?: "",
                                        durationSeconds = song.durationSeconds,
                                        thumbnailUrl = song.thumbnailUrl
                                    )
                                }
                            } else {
                                searchResults
                            }

                            if (songList.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Songs",
                                            fontSize = 18.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogTextPrimary
                                        )
                                        Text(
                                            text = "See all",
                                            fontSize = 12.5.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RogTextGold,
                                            modifier = Modifier.clickable { onFilterSelected(SearchFilterType.SONGS) }
                                        )
                                    }
                                }

                                itemsIndexed(songList.take(4)) { index, track ->
                                    TrackListItem(
                                        track = track,
                                        index = index,
                                        isPlayingCurrentTrack = track.id == currentTrackId,
                                        onTrackClick = { onTrackClick(track, songList) },
                                        onLikeClick = { onLikeTrackClick(track) },
                                        onMoreClick = { onMoreTrackClick(track) }
                                    )
                                }
                            }

                            // 3. Artists Section (Horizontal Shelf)
                            val artistList = comp?.artists ?: emptyList()
                            if (artistList.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Artists",
                                            fontSize = 18.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogTextPrimary
                                        )
                                        Text(
                                            text = "See all",
                                            fontSize = 12.5.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RogTextGold,
                                            modifier = Modifier.clickable { onFilterSelected(SearchFilterType.ARTISTS) }
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(artistList) { artist ->
                                            Column(
                                                modifier = Modifier
                                                    .width(104.dp)
                                                    .clickable { onArtistClick(artist) },
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(96.dp)
                                                        .clip(CircleShape)
                                                        .background(RogSurfaceMedium)
                                                ) {
                                                    if (!artist.thumbnailUrl.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = artist.thumbnailUrl,
                                                            contentDescription = artist.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_profile),
                                                            contentDescription = null,
                                                            tint = RogTextMuted,
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .align(Alignment.Center)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = artist.name,
                                                    fontSize = 13.sp,
                                                    fontFamily = CircularFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = RogTextPrimary,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
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

                            // 4. Playlists & Albums Section (Horizontal Shelf)
                            val playlistList = comp?.playlists ?: emptyList()
                            if (playlistList.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Playlists & Albums",
                                            fontSize = 18.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogTextPrimary
                                        )
                                        Text(
                                            text = "See all",
                                            fontSize = 12.5.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RogTextGold,
                                            modifier = Modifier.clickable { onFilterSelected(SearchFilterType.PLAYLISTS) }
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(playlistList) { playlist ->
                                            Column(
                                                modifier = Modifier
                                                    .width(132.dp)
                                                    .clickable { onPlaylistClick(playlist) }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(132.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(RogSurfaceMedium)
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
                                                            painter = painterResource(id = R.drawable.ic_library),
                                                            contentDescription = null,
                                                            tint = RogTextMuted,
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .align(Alignment.Center)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = playlist.title,
                                                    fontSize = 13.5.sp,
                                                    fontFamily = CircularFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = RogTextPrimary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!playlist.author.isNullOrEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = (if (playlist.isAlbum) "Album" else "Playlist") + " • ${playlist.author}",
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
                        }
                    }
                }
                SearchFilterType.SONGS -> {
                    if (searchResults.isEmpty() && !isSearching) {
                        EmptySearchState(query = searchQuery)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 140.dp)
                        ) {
                            itemsIndexed(searchResults) { index, track ->
                                TrackListItem(
                                    track = track,
                                    index = index,
                                    isPlayingCurrentTrack = track.id == currentTrackId,
                                    onTrackClick = { onTrackClick(track, searchResults) },
                                    onLikeClick = { onLikeTrackClick(track) },
                                    onMoreClick = { onMoreTrackClick(track) }
                                )
                            }
                        }
                    }
                }
                SearchFilterType.ARTISTS -> {
                    val artists = if (artistResults.isNotEmpty()) artistResults else (comprehensiveResult?.artists ?: emptyList())
                    if (artists.isEmpty() && !isSearching) {
                        EmptySearchState(query = searchQuery)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            items(artists) { artist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onArtistClick(artist) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(RogSurfaceMedium)
                                    ) {
                                        if (!artist.thumbnailUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = artist.thumbnailUrl,
                                                contentDescription = artist.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_profile),
                                                contentDescription = null,
                                                tint = RogTextMuted,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .align(Alignment.Center)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.name,
                                            fontSize = 15.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RogTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Artist" + if (!artist.subscribersOrAudience.isNullOrBlank()) " • ${artist.subscribersOrAudience}" else "",
                                            fontSize = 12.5.sp,
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
                SearchFilterType.PLAYLISTS -> {
                    val playlists = if (playlistResults.isNotEmpty()) playlistResults else (comprehensiveResult?.playlists ?: emptyList())
                    if (playlists.isEmpty() && !isSearching) {
                        EmptySearchState(query = searchQuery)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            items(playlists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPlaylistClick(pl) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RogSurfaceMedium)
                                    ) {
                                        if (!pl.thumbnailUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = pl.thumbnailUrl,
                                                contentDescription = pl.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_library),
                                                contentDescription = null,
                                                tint = RogTextMuted,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .align(Alignment.Center)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            fontSize = 15.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RogTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = (if (pl.isAlbum) "Album" else "Playlist") + if (!pl.author.isNullOrBlank()) " • ${pl.author}" else "",
                                            fontSize = 12.5.sp,
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
            }
        } else {
            // Browse Categories Grid with Spotify-like Recent Searches
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spotify-style "Recent searches" section
                if (searchHistory.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "Recent searches",
                            fontSize = 18.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = searchHistory,
                        key = { it.id },
                        span = { GridItemSpan(2) }
                    ) { entry ->
                        SearchHistoryItemRow(
                            entry = entry,
                            isPlaying = entry.track?.id == currentTrackId,
                            onClick = { onHistoryItemClick(entry) },
                            onRemove = { onRemoveHistoryItem(entry.id) }
                        )
                    }

                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, RogSurfaceHighlight, RoundedCornerShape(20.dp))
                                    .clickable { onClearSearchHistory() }
                                    .padding(horizontal = 18.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Clear recent searches",
                                    fontSize = 12.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RogTextPrimary
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Browse all",
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        modifier = Modifier.padding(top = if (searchHistory.isNotEmpty()) 6.dp else 4.dp, bottom = 4.dp)
                    )
                }

                items(categories, key = { it.id }) { category ->
                    GenreBrowseCard(
                        category = category,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                tint = RogTextMuted,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No results for \"$query\"",
                fontSize = 14.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Medium,
                color = RogTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Check spelling or try a different search",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary
            )
        }
    }
}
