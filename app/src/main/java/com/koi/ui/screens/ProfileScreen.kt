package com.koi.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.components.EditUsernameDialog
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.UsageMetrics
import java.util.Locale

private val AquaAccent = Color(0xFF48CAE4)
private val EmeraldAccent = Color(0xFF4ADE80)
private val VioletAccent = Color(0xFFA78BFA)

@Composable
fun ProfileScreen(
    userName: String = "Casper",
    usageMetrics: UsageMetrics,
    sleepTimerRemainingSeconds: Int,
    isPlaying: Boolean = false,
    onClearCacheClick: () -> Unit,
    onRefreshMetricsClick: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onUpdateUsername: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditUsernameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshMetricsClick()
    }

    if (showEditUsernameDialog) {
        EditUsernameDialog(
            currentUsername = userName,
            onDismiss = { showEditUsernameDialog = false },
            onSave = { newName ->
                onUpdateUsername(newName)
                showEditUsernameDialog = false
                Toast.makeText(context, "Username updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp)
    ) {
        // 1. User Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showEditUsernameDialog = true }
                        .padding(4.dp)
                ) {
                    val initial = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "K"
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(RogSurfaceHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 24.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userName,
                                fontSize = 20.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Edit username",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) EmeraldAccent else RogTextMuted)
                            )
                            Text(
                                text = "Total Listened: ${formatLifetimeTime(usageMetrics.lifetimeListeningSeconds)}",
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextGold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        onRefreshMetricsClick()
                        Toast.makeText(context, "Stats synced", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceDark)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_repeat),
                        contentDescription = "Refresh Stats",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Lifetime Highlights 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "Total Listened",
                        value = formatLifetimeTime(usageMetrics.lifetimeListeningSeconds),
                        iconRes = R.drawable.ic_soundwave,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Lifetime Plays",
                        value = "${usageMetrics.totalPlays}",
                        iconRes = R.drawable.ic_history,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "Top Genre",
                        value = usageMetrics.topGenres.firstOrNull()?.label ?: "Exploring",
                        iconRes = R.drawable.ic_heart_filled,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Session Time",
                        value = formatLifetimeTime(usageMetrics.listeningTimeSeconds),
                        iconRes = R.drawable.ic_library,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. 7-Day Listening Activity Bar Chart
        item {
            val days = usageMetrics.dailyPlays7Days
            if (days.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7-DAY ACTIVITY",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextGold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "${days.sumOf { it.playCount }} plays",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val maxCount = days.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                        ) {
                            val count = days.size
                            val gap = 10.dp.toPx()
                            val barW = (size.width - gap * (count - 1)) / count
                            days.forEachIndexed { idx, day ->
                                val frac = (day.playCount.toFloat() / maxCount.toFloat()).coerceIn(0.06f, 1f)
                                val barH = (size.height * frac).coerceAtLeast(4.dp.toPx())
                                val isToday = idx == count - 1
                                drawRoundRect(
                                    color = if (day.playCount == 0) RogSurfaceHighlight else if (isToday) RogGoldPrimary else RogGoldChampagne.copy(alpha = 0.72f),
                                    topLeft = Offset(idx * (barW + gap), size.height - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            days.forEachIndexed { idx, day ->
                                Text(
                                    text = day.dayLabel,
                                    fontSize = 9.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (idx == days.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (idx == days.lastIndex) RogTextGold else RogTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 4. Favorite Genres Graph
        if (usageMetrics.topGenres.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "FAVORITE GENRES",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val totalGenreHits = usageMetrics.topGenres.sumOf { it.count }.coerceAtLeast(1)
                        val palette = listOf(RogGoldPrimary, AquaAccent, EmeraldAccent, RogGoldChampagne, VioletAccent)

                        usageMetrics.topGenres.forEachIndexed { idx, genre ->
                            val frac = genre.count.toFloat() / totalGenreHits.toFloat()
                            val pct = (frac * 100).toInt()
                            val barColor = palette[idx % palette.size]

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = genre.label,
                                        fontSize = 12.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextPrimary
                                    )
                                    Text(
                                        text = "$pct%",
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(RogObsidian)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac.coerceIn(0.05f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 5. Most Played Songs
        if (usageMetrics.topSongs.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "MOST PLAYED SONGS",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val maxSongPlays = usageMetrics.topSongs.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
                        usageMetrics.topSongs.forEachIndexed { idx, entry ->
                            val frac = (entry.playCount.toFloat() / maxSongPlays.toFloat()).coerceIn(0.1f, 1f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) RogTextGold else RogTextMuted,
                                    modifier = Modifier.width(18.dp)
                                )
                                AsyncImage(
                                    model = entry.song.thumbnailUrl,
                                    contentDescription = entry.song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogSurfaceHighlight)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.song.title,
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RogTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.song.artists,
                                        fontSize = 11.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(RogGoldPrimary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogGoldDim)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${entry.playCount}x",
                                        fontSize = 10.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextGold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 6. Most Played Playlists & Top Artists
        if (usageMetrics.topPlaylists.isNotEmpty() || usageMetrics.topArtists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (usageMetrics.topPlaylists.isNotEmpty()) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "TOP PLAYLISTS",
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextGold,
                                    letterSpacing = 0.7.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                usageMetrics.topPlaylists.forEach { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pl.label,
                                            fontSize = 11.5.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${pl.count}",
                                            fontSize = 11.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogTextGold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (usageMetrics.topArtists.isNotEmpty()) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "TOP ARTISTS",
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = AquaAccent,
                                    letterSpacing = 0.7.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                usageMetrics.topArtists.take(4).forEach { artist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = artist.label,
                                            fontSize = 11.5.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${artist.count}",
                                            fontSize = 11.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = AquaAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 7. Library & Storage Breakdown + Cache & Memory
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", usageMetrics.estimatedCacheSizeMb)} MB Cached",
                                fontSize = 15.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "${usageMetrics.cachedStreams} streams • ${usageMetrics.downloadedTracksCount} offline (${String.format(Locale.US, "%.1f", usageMetrics.downloadsSizeMb)} MB)",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                onClearCacheClick()
                                Toast.makeText(context, "Storage cache cleared!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RogSurfaceHighlight,
                                contentColor = RogTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Clear",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val liked = usageMetrics.totalLiked.coerceAtLeast(0)
                    val offline = usageMetrics.downloadedTracksCount.coerceAtLeast(0)
                    val streams = usageMetrics.cachedStreams.coerceAtLeast(0)
                    val playlists = usageMetrics.totalPlaylists.coerceAtLeast(0)
                    val totalItems = (liked + offline + streams + playlists).coerceAtLeast(1).toFloat()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(RogObsidian)
                    ) {
                        if (liked > 0) Box(modifier = Modifier.weight(liked / totalItems).fillMaxHeight().background(RogGoldPrimary))
                        if (offline > 0) Box(modifier = Modifier.weight(offline / totalItems).fillMaxHeight().background(EmeraldAccent))
                        if (streams > 0) Box(modifier = Modifier.weight(streams / totalItems).fillMaxHeight().background(AquaAccent))
                        if (playlists > 0) Box(modifier = Modifier.weight(playlists / totalItems).fillMaxHeight().background(RogGoldChampagne))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(color = RogGoldPrimary, label = "Liked $liked")
                        LegendDot(color = EmeraldAccent, label = "Offline $offline")
                        LegendDot(color = AquaAccent, label = "Cached $streams")
                        LegendDot(color = RogGoldChampagne, label = "Lists $playlists")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "App Memory",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary
                        )
                        Text(
                            text = "${usageMetrics.memoryUsedMb} / ${usageMetrics.memoryMaxMb} MB",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextGold
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    val memoryFraction = if (usageMetrics.memoryMaxMb > 0) {
                        (usageMetrics.memoryUsedMb.toFloat() / usageMetrics.memoryMaxMb.toFloat()).coerceIn(0.05f, 1f)
                    } else 0.2f

                    LinearProgressIndicator(
                        progress = { memoryFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = RogTextGold,
                        trackColor = RogSurfaceHighlight
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 8. Live Sleep Timer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sleep Timer",
                                fontSize = 15.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            if (sleepTimerRemainingSeconds > 0) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatCountdown(sleepTimerRemainingSeconds),
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextGold
                                )
                            }
                        }
                        if (sleepTimerRemainingSeconds > 0) {
                            TextButton(onClick = { onSetSleepTimer(0) }) {
                                Text("Cancel", color = Color(0xFFEF5350))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val timers = listOf(15, 30, 45, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timers.forEach { mins ->
                            Button(
                                onClick = { onSetSleepTimer(mins) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RogSurfaceHighlight
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    color = RogTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary
        )
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    value: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary
                )
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = RogTextGold,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 17.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatLifetimeTime(seconds: Long): String {
    val totalMins = seconds / 60
    val hours = totalMins / 60
    return when {
        hours > 0 -> "${hours}h ${totalMins % 60}m"
        totalMins > 0 -> "${totalMins}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun formatCountdown(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
