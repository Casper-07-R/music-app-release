package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.innertube.AudioQualityTier
import com.koi.backend.innertube.StreamQuality
import com.koi.backend.player.NetworkCoverageLevel
import com.koi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingQualitySheet(
    currentQuality: StreamQuality,
    currentNetworkCoverage: NetworkCoverageLevel = NetworkCoverageLevel.GOOD,
    activeTier: AudioQualityTier = AudioQualityTier.HIGH,
    onDismiss: () -> Unit,
    onConfirmQuality: (StreamQuality) -> Unit
) {
    var selectedQuality by remember { mutableStateOf(currentQuality) }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STREAMING QUALITY & FALLBACK",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogGoldPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Audio Streaming Fidelity",
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Network Coverage Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RogSurfaceSubtle)
                    .border(1.dp, RogDivider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (currentNetworkCoverage) {
                                        NetworkCoverageLevel.EXCELLENT, NetworkCoverageLevel.GOOD -> RogGoldPrimary.copy(alpha = 0.15f)
                                        NetworkCoverageLevel.MODERATE -> Color(0xFFFFA726).copy(alpha = 0.15f)
                                        NetworkCoverageLevel.LOW -> Color(0xFFEF5350).copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentNetworkCoverage == NetworkCoverageLevel.EXCELLENT) Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = when (currentNetworkCoverage) {
                                    NetworkCoverageLevel.EXCELLENT, NetworkCoverageLevel.GOOD -> RogGoldPrimary
                                    NetworkCoverageLevel.MODERATE -> Color(0xFFFFA726)
                                    NetworkCoverageLevel.LOW -> Color(0xFFEF5350)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Current Network Coverage",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextMuted
                            )
                            Text(
                                text = currentNetworkCoverage.displayName,
                                fontSize = 12.5.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RogSurfaceHighlight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeTier.label,
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Select your preferred streaming fidelity. In Auto mode, Koi streams in studio high definition during good coverage and automatically falls back to lightweight streams during low network coverage to eliminate tearing and buffering completely.",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Options List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamQuality.values().forEach { q ->
                    val isSelected = selectedQuality == q

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) RogSurfaceHighlight else RogSurfaceMedium)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) RogGoldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedQuality = q }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Radio indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
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
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) RogTextPrimary else RogTextSecondary
                                )

                                if (q.badge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(RogGoldPrimary.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = q.badge,
                                            fontSize = 9.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogGoldPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = q.description,
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    onConfirmQuality(selectedQuality)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogGoldPrimary,
                    contentColor = RogObsidian
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Streaming Preference",
                        fontSize = 14.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
