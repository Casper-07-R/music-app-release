package com.koi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koi.backend.update.GitHubAsset
import com.koi.backend.update.GitHubRelease
import com.koi.backend.update.UpdateUiState
import com.koi.ui.R
import com.koi.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UpdateAvailableDialog(
    updateState: UpdateUiState,
    onUpdate: (GitHubRelease, GitHubAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onLater: () -> Unit
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = RogObsidian,
            tonalElevation = 12.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        RogGoldPrimary.copy(alpha = 0.45f),
                        RogDivider,
                        RogSurfaceHighlight.copy(alpha = 0.3f)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .heightIn(max = 680.dp)
        ) {
            when (updateState) {
                is UpdateUiState.UpdateAvailable -> {
                    InteractiveUpdateWalkthrough(
                        currentVersion = updateState.currentVersion,
                        release = updateState.release,
                        apkAsset = updateState.apkAsset,
                        onUpdate = { onUpdate(updateState.release, updateState.apkAsset) },
                        onLater = onLater
                    )
                }

                is UpdateUiState.Downloading -> {
                    DownloadingUpdateContent(
                        release = updateState.release,
                        progress = updateState.progress,
                        downloadedBytes = updateState.downloadedBytes,
                        totalBytes = updateState.totalBytes,
                        onCancel = onCancelDownload,
                        onLater = onLater
                    )
                }

                is UpdateUiState.ReadyToInstall -> {
                    ReadyToInstallContent(
                        release = updateState.release,
                        apkFile = updateState.apkFile,
                        onInstall = { onInstallUpdate(updateState.apkFile) },
                        onLater = onLater
                    )
                }

                else -> {
                    // Other states do not render inside the dialog
                }
            }
        }
    }
}

/**
 * Highly interactive multi-page walkthrough for new update features.
 * Displays a separate page for each update detail with a "Next" button,
 * and on the final page reveals the "Update Now" and "Later" buttons.
 */
@Composable
private fun InteractiveUpdateWalkthrough(
    currentVersion: String,
    release: GitHubRelease,
    apkAsset: GitHubAsset,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val features = remember(release) {
        UpdateFeatureParser.parse(release)
    }
    val pageCount = features.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val isLastPage = pagerState.currentPage == pageCount - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Top Header Bar: Version Tag Pill & Close Button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill with pulsing dot
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotPulse"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RogGoldDim)
                    .border(
                        BorderStroke(0.8.dp, RogGoldPrimary.copy(alpha = 0.5f)),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RogGoldPrimary.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KOI ${release.tagName.uppercase()} • WHAT'S NEW",
                        fontSize = 10.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogGoldChampagne,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Dismiss / Close Icon
            IconButton(
                onClick = onLater,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = RogTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Segmented Progress Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (i in 0 until pageCount) {
                val isCompleted = i <= pagerState.currentPage
                val segmentColor = if (isCompleted) RogGoldPrimary else RogSurfaceHighlight
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(segmentColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step Label & Quick Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FEATURE ${pagerState.currentPage + 1} OF $pageCount",
                fontSize = 11.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextGold,
                letterSpacing = 0.6.sp
            )

            if (!isLastPage) {
                Text(
                    text = "Swipe or tap Next",
                    fontSize = 11.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextMuted
                )
            } else {
                Text(
                    text = "Ready to install",
                    fontSize = 11.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RogGoldChampagne
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 3. Horizontal Pager (Each Update Detail on Its Own Page) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val feature = features[pageIndex]
                UpdateFeaturePage(
                    feature = feature,
                    currentVersion = currentVersion,
                    release = release
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 4. Bottom Action Bar: Next on earlier pages, Update Now / Later on final page ---
        if (!isLastPage) {
            // Earlier Pages: [Back (if page > 0)] and [Next →]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .width(52.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RogDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextSecondary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Feature",
                            tint = RogTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RogGoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Next",
                        fontSize = 14.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Secondary option row: "Remind Me Later" & "Skip to Update"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onLater,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Remind Me Later",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextMuted
                    )
                }

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pageCount - 1)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Skip to Update →",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = RogGoldChampagne
                    )
                }
            }
        } else {
            // Final Page: Prominent "Update Now" and "Later" buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Action Button: UPDATE NOW
                Button(
                    onClick = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RogGoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Update Now",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val sizeLabel = if (apkAsset.size > 0) " • ${formatFileSize(apkAsset.size)}" else ""
                    Text(
                        text = "Update Now$sizeLabel",
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                // Secondary Navigation / Dismiss Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pageCount > 1) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .weight(0.4f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RogDivider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextSecondary)
                        ) {
                            Text(
                                text = "← Back",
                                fontSize = 13.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLater,
                        modifier = Modifier
                            .weight(if (pageCount > 1) 0.6f else 1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RogDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextSecondary)
                    ) {
                        Text(
                            text = "Later",
                            fontSize = 13.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = RogTextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * A dedicated page for a single update detail with rich interactive elements.
 */
@Composable
private fun UpdateFeaturePage(
    feature: UpdateFeatureItem,
    currentVersion: String,
    release: GitHubRelease,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Category Tag & High-impact Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(feature.badgeColor.copy(alpha = 0.18f))
                    .border(
                        BorderStroke(0.7.dp, feature.badgeColor.copy(alpha = 0.5f)),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = feature.categoryLabel,
                    fontSize = 10.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = feature.badgeColor,
                    letterSpacing = 0.6.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RogSurfaceHighlight)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = feature.tag,
                    fontSize = 9.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextGold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v$currentVersion → ${release.tagName}",
                fontSize = 10.5.sp,
                fontFamily = CircularFontFamily,
                color = RogTextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Feature Title
        Text(
            text = feature.title,
            fontSize = 19.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Feature Subtitle
        Text(
            text = feature.subtitle,
            fontSize = 12.5.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary,
            lineHeight = 16.sp
        )

        // --- INTERACTIVE FEATURE DEMO WIDGET ---
        // Tailored interactive element allowing user to try and experience supported features.
        when (feature.type) {
            UpdateFeatureType.AUDIO_8D -> {
                InteractiveAudio8DDemo(accentColor = feature.badgeColor)
                Spacer(modifier = Modifier.height(12.dp))
            }

            UpdateFeatureType.SLEEP_TIMER -> {
                InteractiveSleepTimerDemo(accentColor = feature.badgeColor)
                Spacer(modifier = Modifier.height(12.dp))
            }

            else -> Unit
        }

        // Feature Explanation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            colors = CardDefaults.cardColors(containerColor = RogSurfaceMedium.copy(alpha = 0.6f)),
            border = BorderStroke(0.6.dp, RogDivider.copy(alpha = 0.15f))
        ) {
            Text(
                text = feature.description,
                fontSize = 12.5.sp,
                fontFamily = CircularFontFamily,
                color = RogTextPrimary.copy(alpha = 0.95f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pro Tip & Usage Hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RogSurfaceSubtle)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature.tip,
                fontSize = 11.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Interactive 360° Binaural Audio Orbit & Soundwave Visualizer.
 * Lets users toggle and watch simulated 8D audio revolve around their head!
 */
@Composable
private fun InteractiveAudio8DDemo(accentColor: Color) {
    var isSimulating by remember { mutableStateOf(true) }
    var orbitSpeed by remember { mutableFloatStateOf(1f) } // 1f, 2f, 0.5f

    val infiniteTransition = rememberInfiniteTransition(label = "8DAudioDemo")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (5000 / orbitSpeed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    // Animated sound equalizer wave heights
    val bar1 by infiniteTransition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(290, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(0.3f, 0.85f, infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b3")
    val bar4 by infiniteTransition.animateFloat(0.5f, 0.95f, infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b4")
    val bar5 by infiniteTransition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(410, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b5")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RogSurfaceDark),
        border = BorderStroke(1.dp, RogDivider.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Radar stage & revolving audio orb
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dashed circular orbit track
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2.3f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        color = RogDivider.copy(alpha = 0.35f),
                        radius = radius,
                        center = center,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )

                    // Draw moving sound orb if simulating
                    val currentRad = Math.toRadians((if (isSimulating) orbitAngle else 45f).toDouble())
                    val orbX = center.x + radius * cos(currentRad).toFloat()
                    val orbY = center.y + radius * sin(currentRad).toFloat()

                    // Glow halo
                    drawCircle(
                        color = accentColor.copy(alpha = 0.35f),
                        radius = 12.dp.toPx(),
                        center = Offset(orbX, orbY)
                    )
                    // Core orb
                    drawCircle(
                        color = accentColor,
                        radius = 6.dp.toPx(),
                        center = Offset(orbX, orbY)
                    )
                }

                // Center Listener Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_headphones),
                        contentDescription = "Listener",
                        tint = RogTextGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Sound position feedback
            val positionText = when {
                !isSimulating -> "Simulation Paused"
                orbitAngle in 315f..360f || orbitAngle in 0f..45f -> "🔊 Front (Direct Center)"
                orbitAngle in 45f..135f -> "🎧 Right Ear (Binaural Panning)"
                orbitAngle in 135f..225f -> "🔊 Behind You (Rear Dimension)"
                else -> "🎧 Left Ear (Binaural Panning)"
            }

            Text(
                text = positionText,
                fontSize = 11.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogGoldChampagne
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Equalizer Visualizer Bars
            Row(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val heights = if (isSimulating) {
                    listOf(bar1, bar2, bar3, bar4, bar5)
                } else {
                    listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
                }

                heights.forEach { frac ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight(frac)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Controls: Orbit Speed chips & Play/Pause toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed selection chips
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1f to "1x", 2f to "2x", 0.5f to "Slow").forEach { (speedVal, label) ->
                        val isSelected = orbitSpeed == speedVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) RogGoldPrimary else RogSurfaceHighlight)
                                .clickable { orbitSpeed = speedVal }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else RogTextSecondary
                            )
                        }
                    }
                }

                // Simulation Toggle
                Button(
                    onClick = { isSimulating = !isSimulating },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulating) RogSurfaceHighlight else RogGoldPrimary,
                        contentColor = if (isSimulating) RogTextPrimary else Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (isSimulating) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Simulation",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSimulating) "Pause" else "Simulate",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Interactive Sleep Timer Demo Widget.
 * Lets users tap duration chips and preview the smooth gradual fade-out curve.
 */
@Composable
private fun InteractiveSleepTimerDemo(accentColor: Color) {
    var selectedMinutes by remember { mutableIntStateOf(30) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RogSurfaceDark),
        border = BorderStroke(1.dp, RogDivider.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Try Bedtime Duration:",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                }

                Text(
                    text = "$selectedMinutes mins selected",
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Duration Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    val isSelected = selectedMinutes == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor else RogSurfaceMedium)
                            .border(
                                BorderStroke(
                                    0.8.dp,
                                    if (isSelected) accentColor else RogDivider.copy(alpha = 0.2f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMinutes = mins }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else RogTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gradual Volume Fade-out Curve Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RogSurfaceSubtle)
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Volume Fade Curve",
                            fontSize = 10.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogGoldChampagne
                        )
                        Text(
                            text = "100% ➔ 0% smoothly",
                            fontSize = 10.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary
                        )
                    }

                    // Tapered gradient bar simulating gentle fade-out
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        RogGoldPrimary,
                                        RogGoldChampagne,
                                        RogSurfaceHighlight.copy(alpha = 0.3f)
                                    )
                                )
                            )
                    )

                    Text(
                        text = "💤 Music gently tapers down over the final 3 minutes without abrupt cutoffs.",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingUpdateContent(
    release: GitHubRelease,
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Box(
            modifier = Modifier
                .scale(pulseScale)
                .size(62.dp)
                .clip(CircleShape)
                .background(RogGoldPrimary.copy(alpha = 0.18f))
                .border(BorderStroke(1.dp, RogGoldPrimary.copy(alpha = 0.4f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Downloading",
                tint = RogGoldPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Downloading Update...",
            fontSize = 20.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = release.name ?: release.tagName,
            fontSize = 13.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Progress Bar with Gradient
        val progressFraction = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = RogGoldPrimary,
            trackColor = RogSurfaceHighlight
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Progress percentage & bytes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pctText = if (progress >= 0f) "${(progress * 100).toInt()}%" else "Connecting..."
            Text(
                text = pctText,
                fontSize = 12.5.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogGoldChampagne
            )

            if (totalBytes > 0) {
                Text(
                    text = "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}",
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextSecondary),
                border = BorderStroke(1.dp, RogDivider)
            ) {
                Text(
                    text = "Later",
                    fontSize = 13.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = RogTextSecondary
                )
            }

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogSurfaceHighlight,
                    contentColor = RogTextPrimary
                )
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 13.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RogTextPrimary
                )
            }
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    release: GitHubRelease,
    apkFile: File,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(RogGoldPrimary.copy(alpha = 0.18f))
                .border(BorderStroke(1.dp, RogGoldPrimary.copy(alpha = 0.4f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Ready to Install",
                tint = RogGoldPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Update Downloaded",
            fontSize = 20.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${release.name ?: release.tagName} is ready to install (${formatFileSize(apkFile.length())}).",
            fontSize = 13.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = RogSurfaceMedium)
        ) {
            Text(
                text = "Tap 'Install' to launch the Android package installer. If prompted, please allow 'Install unknown apps' for Koi.",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextSecondary),
                border = BorderStroke(1.dp, RogDivider)
            ) {
                Text(
                    text = "Later",
                    fontSize = 14.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RogTextSecondary
                )
            }

            Button(
                onClick = onInstall,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogGoldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Install",
                    fontSize = 14.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Backward compatibility helper for raw changelog rendering if needed.
 */
@Composable
fun UpdateChangelogView(
    body: String?,
    modifier: Modifier = Modifier
) {
    val cleanBody = body?.replace("\r\n", "\n")?.trim()
    val lines = remember(cleanBody) {
        cleanBody?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (line in lines) {
            Text(
                text = line,
                fontSize = 12.5.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes.toDouble() / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}
