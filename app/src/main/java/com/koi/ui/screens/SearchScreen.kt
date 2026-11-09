package com.koi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Minimal Search Bar (No border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
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
                        text = "Songs, artists, podcasts...",
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

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area: Results list or Browse Categories Grid with Recent Searches
        if (searchQuery.trim().isNotEmpty()) {
            if (searchResults.isEmpty() && !isSearching) {
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
                            text = "No results for \"$searchQuery\"",
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

