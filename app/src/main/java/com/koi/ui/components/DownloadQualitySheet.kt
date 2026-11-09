package com.koi.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.backend.download.AudioFormat
import com.koi.backend.download.AudioQuality
import com.koi.backend.download.calculateEstimatedDownloadBytes
import com.koi.backend.download.formatDownloadSize
import com.koi.ui.R
import com.koi.ui.model.Playlist
import com.koi.ui.model.Track
import com.koi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySheet(
    track: Track? = null,
    playlist: Playlist? = null,
    initialFormat: AudioFormat = AudioFormat.M4A,
    initialQuality: AudioQuality = AudioQuality.HIGH,
    onDismiss: () -> Unit,
    onConfirmDownload: (format: AudioFormat, quality: AudioQuality) -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedQuality by remember { mutableStateOf(initialQuality) }

    // Calculate duration and estimated size
    val durationSeconds = track?.durationSeconds ?: playlist?.tracks?.sumOf { if (it.durationSeconds > 0) it.durationSeconds else 210 } ?: 210
    val trackCount = if (track != null) 1 else playlist?.trackCount?.coerceAtLeast(1) ?: 1

    val estimatedBytes = if (track != null) {
        calculateEstimatedDownloadBytes(durationSeconds, selectedFormat, selectedQuality)
    } else {
        val tracksList = playlist?.tracks.orEmpty()
        if (tracksList.isNotEmpty()) {
            tracksList.sumOf { calculateEstimatedDownloadBytes(it.durationSeconds, selectedFormat, selectedQuality) }
        } else {
            calculateEstimatedDownloadBytes(210, selectedFormat, selectedQuality) * trackCount
        }
    }
    val estimatedSizeText = formatDownloadSize(estimatedBytes)

    // Device storage query
    val availableStorageBytes = remember { getAvailableStorageBytes(context) }
    val availableStorageText = formatStorageSize(availableStorageBytes)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = RogSurfaceDark,
        scrimColor = RogObsidian.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RogSurfaceHighlight)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Item Info & Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOWNLOAD OPTIONS",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track?.title ?: playlist?.title ?: "Download",
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (track != null) {
                            "${track.artist} • ${formatDuration(track.durationSeconds)}"
                        } else {
                            "Playlist • $trackCount ${if (trackCount == 1) "song" else "songs"} • ~${durationSeconds / 60} mins"
                        },
                        fontSize = 12.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: AUDIO FORMAT
            Text(
                text = "AUDIO FORMAT",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioFormat.values().forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) RogSurfaceHighlight else RogSurfaceMedium)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) RogGoldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedFormat = fmt }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = fmt.displayName,
                                fontSize = 13.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RogGoldPrimary else RogTextPrimary
                            )
                            if (fmt == AudioFormat.M4A) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Best",
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RogGoldPrimary else RogTextGold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedFormat.description,
                fontSize = 11.5.sp,
                fontFamily = CircularFontFamily,
                color = RogTextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: AUDIO QUALITY
            Text(
                text = "AUDIO QUALITY",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedFormat == AudioFormat.FLAC) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RogSurfaceHighlight.copy(alpha = 0.5f))
                        .border(1.dp, RogGoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RogGoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_headphones),
                                contentDescription = null,
                                tint = RogGoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lossless Studio Fidelity",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "16-bit / 44.1kHz • ~850 kbps uncompressed clarity",
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudioQuality.values().forEach { q ->
                        val isSelected = selectedQuality == q
                        val qBytes = if (track != null) {
                            calculateEstimatedDownloadBytes(durationSeconds, selectedFormat, q)
                        } else {
                            calculateEstimatedDownloadBytes(210, selectedFormat, q) * trackCount
                        }
                        val qSizeText = formatDownloadSize(qBytes)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) RogSurfaceHighlight else RogSurfaceMedium)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) RogGoldPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedQuality = q }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Radio indicator
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) RogGoldPrimary else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 0.dp else 1.5.dp,
                                        color = if (isSelected) Color.Transparent else RogTextSecondary.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(RogObsidian)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = q.displayName,
                                        fontSize = 14.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) RogTextPrimary else RogTextSecondary
                                    )
                                    if (q.badge.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (q == AudioQuality.HIGH) RogGoldPrimary.copy(alpha = 0.2f)
                                                    else RogSurfaceDark
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = q.badge,
                                                fontSize = 9.sp,
                                                fontFamily = CircularFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (q == AudioQuality.HIGH) RogGoldPrimary else RogTextSecondary
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = q.description,
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextMuted
                                )
                            }

                            Text(
                                text = "~$qSizeText",
                                fontSize = 13.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) RogGoldPrimary else RogTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 3: LIVE ESTIMATED SIZE & STORAGE BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RogSurfaceHighlight.copy(alpha = 0.6f))
                    .border(1.dp, RogDivider, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RogGoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Estimated download size: ",
                                fontSize = 12.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                            Text(
                                text = "~$estimatedSizeText",
                                fontSize = 14.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogGoldPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Free space: $availableStorageText • ${selectedFormat.displayName}",
                            fontSize = 11.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Section 4: Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = RogTextSecondary,
                        fontFamily = CircularFontFamily,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = {
                        onConfirmDownload(selectedFormat, selectedQuality)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RogGoldPrimary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                        tint = RogObsidian,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (track != null) "Download (~$estimatedSizeText)" else "Download Playlist (~$estimatedSizeText)",
                        color = RogObsidian,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "3:30"
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

private fun getAvailableStorageBytes(context: Context): Long {
    return try {
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: Exception) {
        0L
    }
}

private fun formatStorageSize(bytes: Long): String {
    val gb = bytes.toFloat() / (1024f * 1024f * 1024f)
    return if (gb >= 1.0f) {
        String.format("%.1f GB", gb)
    } else {
        val mb = bytes / (1024 * 1024)
        "$mb MB"
    }
}
