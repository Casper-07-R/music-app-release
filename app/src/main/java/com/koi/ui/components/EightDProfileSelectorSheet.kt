package com.koi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.audio.AudioProfile
import com.koi.backend.audio.SpatialMode
import com.koi.ui.R
import com.koi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EightDProfileSelectorSheet(
    activeProfile: AudioProfile,
    spatialMode: SpatialMode,
    onProfileSelect: (AudioProfile) -> Unit,
    onResetProfile: (AudioProfile) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = RogObsidian,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = RogSurfaceHighlight)
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Audio profiles",
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                    val modeLabel = when (spatialMode) {
                        SpatialMode.MODE_16D -> "16D Mode"
                        SpatialMode.MODE_8D -> "8D Mode"
                        SpatialMode.OFF -> "Spatial Off"
                    }
                    Text(
                        text = "Common for 8D & 16D • $modeLabel",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profile Cards (No border lines)
            AudioProfile.values().forEach { profile ->
                val isSelected = profile == activeProfile
                val tagText = when (profile) {
                    AudioProfile.BALANCED -> "Clarity"
                    AudioProfile.SPATIAL_360 -> "360° Surround"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onProfileSelect(profile) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RogSurfaceHighlight else RogSurfaceDark
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = profile.label,
                                    fontSize = 15.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RogTextGold else RogTextPrimary
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RogSurfaceSubtle)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tagText,
                                        fontSize = 10.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) RogTextGold else RogTextMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = profile.fullDescription,
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Reset button for each profile
                        TextButton(
                            onClick = {
                                onResetProfile(profile)
                                Toast.makeText(context, "${profile.label} reset to default", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset ${profile.label}",
                                tint = RogTextGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Reset",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextGold
                            )
                        }

                        // Radio checkmark indicator (Minimal)
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(RogTextGold)
                            )
                        }
                    }
                }
            }
        }
    }
}
