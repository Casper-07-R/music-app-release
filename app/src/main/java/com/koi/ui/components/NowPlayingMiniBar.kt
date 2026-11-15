package com.koi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.Track
import com.koi.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Floating Now Playing Card.
 * Can be slid to the sides (left or right) to dismiss.
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
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(track.id) {
        dragOffset = 0f
        animOffset.snapTo(0f)
        isAnimating = false
    }

    val currentOffset = if (isAnimating) animOffset.value else dragOffset
    val effectiveWidth = if (cardWidthPx > 0f) cardWidthPx else screenWidthPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> cardWidthPx = size.width.toFloat() }
            .then(
                if (onDismiss != null) {
                    Modifier
                        .offset { IntOffset(currentOffset.roundToInt(), 0) }
                        .graphicsLayer {
                            val progress = (abs(currentOffset) / (effectiveWidth * 0.75f)).coerceIn(0f, 1f)
                            alpha = (1f - progress).coerceIn(0f, 1f)
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (!isAnimating) {
                                    dragOffset += delta
                                }
                            },
                            onDragStarted = {
                                isAnimating = false
                            },
                            onDragStopped = { velocity ->
                                val dismissThreshold = effectiveWidth * 0.28f
                                val velocityThreshold = 800f

                                val shouldDismissRight = (dragOffset > dismissThreshold && velocity > -400f) || velocity > velocityThreshold
                                val shouldDismissLeft = (dragOffset < -dismissThreshold && velocity < 400f) || velocity < -velocityThreshold

                                coroutineScope.launch {
                                    isAnimating = true
                                    animOffset.snapTo(dragOffset)
                                    if (shouldDismissRight) {
                                        animOffset.animateTo(
                                            targetValue = effectiveWidth * 1.25f,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                        onDismiss()
                                    } else if (shouldDismissLeft) {
                                        animOffset.animateTo(
                                            targetValue = -effectiveWidth * 1.25f,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                        onDismiss()
                                    } else {
                                        animOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        dragOffset = 0f
                                        isAnimating = false
                                    }
                                }
                            }
                        )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onBarClick() }
    ) {
        // Pond Background Artwork
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.koi_notification_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.Alignment.BottomStart,
            modifier = Modifier.matchParentSize()
        )

        // Aquatic Scrim for optimal contrast and deep water vibrance
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xBD05141C),
                            androidx.compose.ui.graphics.Color(0x6005141C),
                            androidx.compose.ui.graphics.Color(0xAA05141C)
                        )
                    )
                )
        )

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
