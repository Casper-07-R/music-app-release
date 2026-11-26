package com.koi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onSelectSpatialMode: (SpatialMode) -> Unit = {},
    onProfileSelect: (AudioProfile) -> Unit,
    onResetProfile: (AudioProfile) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = RogObsidian,
        scrimColor = RogObsidian.copy(alpha = 0.78f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = RogSurfaceHighlight)
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profiles",
                    fontSize = 18.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )

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

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Spatial Mode Segmented Switcher (Stereo / 8D / 16D)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RogSurfaceDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    SpatialMode.OFF to "Stereo",
                    SpatialMode.MODE_8D to "8D",
                    SpatialMode.MODE_16D to "16D"
                )
                modes.forEach { (mode, label) ->
                    val isModeSelected = spatialMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isModeSelected) RogSurfaceHighlight else Color.Transparent)
                            .clickable { onSelectSpatialMode(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isModeSelected) RogTextGold else RogTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Visual Profile Tiles
            AudioProfile.values().forEach { profile ->
                val isSelected = profile == activeProfile
                val icon = profileIcon(profile)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) RogSurfaceHighlight else RogSurfaceDark)
                        .clickable { onProfileSelect(profile) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RogGoldDim else RogSurfaceMedium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = profile.label,
                            tint = if (isSelected) RogTextGold else RogTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = profile.label,
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) RogTextGold else RogTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // Visual 3-bar EQ/Stage signature meter (Bass, Stage, Clarity)
                    ProfileSignatureBars(
                        bass = profile.bassMeter,
                        stage = profile.stageMeter,
                        clarity = profile.clarityMeter,
                        isSelected = isSelected
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            onResetProfile(profile)
                            Toast.makeText(context, "${profile.label} reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = RogTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) RogTextGold else Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onClose()
                    onOpenEqualizer()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogSurfaceMedium,
                    contentColor = RogTextGold
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = RogTextGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Equalizer",
                    fontSize = 13.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextGold
                )
            }
        }
    }
}

@Composable
private fun ProfileSignatureBars(
    bass: Float,
    stage: Float,
    clarity: Float,
    isSelected: Boolean
) {
    val activeColor = if (isSelected) RogTextGold else RogTextSecondary
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        listOf(bass, stage, clarity).forEach { level ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(RogObsidian.copy(alpha = 0.55f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0.2f, 1f))
                        .background(activeColor)
                )
            }
        }
    }
}

internal fun profileIcon(profile: AudioProfile): ImageVector = when (profile) {
    AudioProfile.BALANCED -> Icons.Default.Tune
    AudioProfile.IMMERSIVE_STAGE -> Icons.Default.SurroundSound
    AudioProfile.SPATIAL_360 -> Icons.Default.TrackChanges
    AudioProfile.ABSOLUTE_HEARING -> Icons.Default.Hearing
    AudioProfile.NIGHT_CONCERT -> Icons.Default.NightsStay
}
