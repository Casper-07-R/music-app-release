package com.koi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.audio.*
import com.koi.ui.R
import com.koi.ui.components.EditUsernameDialog
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.UsageMetrics
import kotlin.math.*

@Composable
fun ProfileScreen(
    userName: String = "Casper",
    usageMetrics: UsageMetrics,
    sleepTimerRemainingSeconds: Int,
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showEditUsernameDialog = true }
                        .padding(4.dp)
                ) {
                    val initial = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
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
                                color = RogTextPrimary
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Edit username",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Koi Listener • Free & Ad-Free",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RogSurfaceDark)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(RogTextGold)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "DSP Active",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = RogTextGold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        onRefreshMetricsClick()
                        Toast.makeText(context, "Stats updated", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 2. Metrics Section
        item {
            Text(
                text = "Listening stats",
                fontSize = 15.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "Tracks Played",
                        value = "${usageMetrics.totalPlays}",
                        iconRes = R.drawable.ic_history,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Listening Time",
                        value = formatListeningTime(usageMetrics.listeningTimeSeconds),
                        iconRes = R.drawable.ic_soundwave,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "Liked Songs",
                        value = "${usageMetrics.totalLiked}",
                        iconRes = R.drawable.ic_heart_filled,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Playlists",
                        value = "${usageMetrics.totalPlaylists}",
                        iconRes = R.drawable.ic_library,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Storage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
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
                                text = "${String.format("%.1f", usageMetrics.estimatedCacheSizeMb)} MB Cached",
                                fontSize = 15.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "${usageMetrics.cachedStreams} cached streams",
                                fontSize = 12.sp,
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
                                text = "Clear Cache",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sleep Timer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sleep Timer",
                            fontSize = 15.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary
                        )
                        if (sleepTimerRemainingSeconds > 0) {
                            TextButton(onClick = { onSetSleepTimer(0) }) {
                                Text("Cancel", color = Color(0xFFEF5350))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Footer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Koi Music",
                    fontSize = 13.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = RogTextSecondary
                )
            }
        }
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
                fontSize = 18.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary
            )
        }
    }
}

private fun formatListeningTime(seconds: Long): String {
    val mins = seconds / 60
    val hours = mins / 60
    return when {
        hours > 0 -> "${hours}h ${mins % 60}m"
        mins > 0 -> "${mins}m"
        else -> "${seconds}s"
    }
}
