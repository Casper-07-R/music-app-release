package com.koi.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    index: Int,
    isPlayingCurrentTrack: Boolean,
    onTrackClick: () -> Unit,
    onLikeClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    isSelectable: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) RogSurfaceHighlight.copy(alpha = 0.85f)
                else if (isPlayingCurrentTrack) RogSurfaceHighlight
                else Color.Transparent
            )
            .combinedClickable(
                onClick = {
                    if (isSelectable) {
                        onSelectToggle?.invoke()
                    } else {
                        onTrackClick()
                    }
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox Circle
        if (isSelectable) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) RogGoldPrimary else RogSurfaceDark)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else RogTextSecondary.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onSelectToggle?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Selected",
                        tint = RogObsidian,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Track artwork thumbnail (Rounded shape)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
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
                Text(
                    text = String.format("%02d", index + 1),
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlayingCurrentTrack) RogTextGold else RogTextMuted
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
                        tint = RogTextGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (track.isDownloaded) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Downloaded",
                        tint = RogGoldPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = track.artist,
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = RogTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isSelectable) {
            // Like button
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (track.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                    ),
                    contentDescription = "Like",
                    tint = if (track.isLiked) RogTextGold else RogTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // More options
            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more),
                        contentDescription = "More",
                        tint = RogTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
