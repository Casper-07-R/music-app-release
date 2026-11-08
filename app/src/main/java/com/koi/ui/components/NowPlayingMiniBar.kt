package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.koi.ui.model.Track
import com.koi.ui.theme.*

/**
 * Material 3 Expressive Floating Now Playing Card.
 */
@Composable
fun NowPlayingMiniBar(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progressFraction: Float,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(RogSurfaceDark)
            .clickable { onBarClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expressive Rounded Thumbnail
                Box(
                    modifier = Modifier
                        .size(46.dp)
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
                        Icon(
                            painter = painterResource(id = R.drawable.ic_soundwave),
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = track.title,
                        fontSize = 13.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Heart / Like button
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (track.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                        ),
                        contentDescription = "Like",
                        tint = if (track.isLiked) RogTextGold else RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Play / Pause button (Minimal clean icon)
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = RogGoldPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Rounded Progress Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(RogSurfaceHighlight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(RogGoldPrimary)
                )
            }
        }
    }
}
