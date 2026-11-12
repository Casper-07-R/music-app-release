package com.koi.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.koi.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EightDAudioStudioSheet(
    dspConfig: AudioDspConfig,
    rotationAngleDegrees: Float,
    spectrumBands: FloatArray,
    sleepTimerRemainingSeconds: Int,
    onConfigChange: (AudioDspConfig) -> Unit,
    onSelectSpatialMode: (SpatialMode) -> Unit = {},
    onProfileSelect: (AudioProfile) -> Unit = {},
    onResetProfile: (AudioProfile) -> Unit = {},
    onResetToFlat: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
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
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = RogSurfaceHighlight)
        },
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio & DSP Studio",
                            fontSize = 18.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary
                        )
                        Text(
                            text = "360° Binaural Spatializer & Studio DSP",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (dspConfig.spatialMode != SpatialMode.OFF) {
                                    onResetProfile(dspConfig.activeProfile)
                                    Toast.makeText(context, "${dspConfig.activeProfile.label} reset to default", Toast.LENGTH_SHORT).show()
                                } else {
                                    onResetToFlat()
                                    Toast.makeText(context, "Reset to Flat", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = RogTextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Reset",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { onConfigChange(dspConfig.copy(abBypass = !dspConfig.abBypass)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dspConfig.abBypass) Color(0xFFC62828) else RogSurfaceHighlight,
                                contentColor = RogTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (dspConfig.abBypass) "Bypass On" else "A/B Test",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

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
                }
            }

            // Master Engine & Visualizers (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Master Engine",
                                    fontSize = 14.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextPrimary
                                )
                                Text(
                                    text = if (dspConfig.masterEnabled) "Studio Processing Active" else "Bypassed",
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    color = if (dspConfig.masterEnabled) RogTextGold else RogTextSecondary
                                )
                            }

                            Switch(
                                checked = dspConfig.masterEnabled,
                                onCheckedChange = { onConfigChange(dspConfig.copy(masterEnabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RogObsidian,
                                    checkedTrackColor = RogTextGold,
                                    uncheckedThumbColor = RogTextMuted,
                                    uncheckedTrackColor = RogSurfaceHighlight
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visualizers Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val orbitLabel = when (dspConfig.spatialMode) {
                                    SpatialMode.MODE_16D -> "16D Dual Ring"
                                    SpatialMode.MODE_8D -> "8D 360° Orbit"
                                    SpatialMode.OFF -> "Orbit"
                                }
                                Text(
                                    text = orbitLabel,
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SoundOrbitRadar(
                                    angleDegrees = rotationAngleDegrees,
                                    isActive = dspConfig.masterEnabled && dspConfig.is8dEnabled && !dspConfig.abBypass,
                                    is16D = dspConfig.spatialMode == SpatialMode.MODE_16D,
                                    modifier = Modifier.size(80.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text(
                                    text = "Spectrum",
                                    fontSize = 10.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SpectrumVisualizer(
                                    bands = spectrumBands,
                                    isActive = dspConfig.masterEnabled && !dspConfig.abBypass,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Spatial Mode & Profiles (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Spatial mode",
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpatialMode.values().forEach { mode ->
                                val isSelected = dspConfig.spatialMode == mode
                                val label = when (mode) {
                                    SpatialMode.OFF -> "Off"
                                    SpatialMode.MODE_8D -> "8D Audio"
                                    SpatialMode.MODE_16D -> "16D Audio"
                                }
                                Button(
                                    onClick = {
                                        onSelectSpatialMode(mode)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) RogSurfaceHighlight else RogSurfaceSubtle
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) RogTextGold else RogTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio profiles",
                                fontSize = 13.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )

                            TextButton(
                                onClick = {
                                    onResetProfile(dspConfig.activeProfile)
                                    Toast.makeText(context, "${dspConfig.activeProfile.label} reset to default", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset ${dspConfig.activeProfile.label}",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Reset Profile",
                                    fontSize = 11.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = RogTextGold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioProfile.values().forEach { profile ->
                                val isSelected = dspConfig.activeProfile == profile
                                Button(
                                    onClick = {
                                        onProfileSelect(profile)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) RogSurfaceHighlight else RogSurfaceSubtle
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = profile.label,
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) RogTextGold else RogTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dspConfig.activeProfile.fullDescription,
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Spatializer Section (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val spatializerTitle = when (dspConfig.spatialMode) {
                                SpatialMode.MODE_16D -> "16D Spatializer"
                                SpatialMode.MODE_8D -> "8D Spatializer"
                                SpatialMode.OFF -> "Spatializer"
                            }
                            Text(
                                text = spatializerTitle,
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Switch(
                                checked = dspConfig.is8dEnabled,
                                onCheckedChange = { isChecked ->
                                    val updatedMode = if (!isChecked) {
                                        dspConfig.spatialMode
                                    } else {
                                        if (dspConfig.spatialMode == SpatialMode.OFF) SpatialMode.MODE_8D else dspConfig.spatialMode
                                    }
                                    onConfigChange(dspConfig.copy(is8dEnabled = isChecked, spatialMode = updatedMode))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RogObsidian,
                                    checkedTrackColor = RogTextGold,
                                    uncheckedThumbColor = RogTextMuted,
                                    uncheckedTrackColor = RogSurfaceHighlight
                                )
                            )
                        }

                        AnimatedVisibility(visible = dspConfig.is8dEnabled) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                DspSliderRow(
                                    label = "Speed",
                                    valueText = "${String.format("%.1f", dspConfig.rotationSpeedSeconds)}s",
                                    value = dspConfig.rotationSpeedSeconds,
                                    range = 2f..30f,
                                    onValueChange = { onConfigChange(dspConfig.copy(rotationSpeedSeconds = it)) }
                                )

                                DspSliderRow(
                                    label = "Pan Depth",
                                    valueText = "${(dspConfig.panDepth * 100).toInt()}%",
                                    value = dspConfig.panDepth,
                                    range = 0.2f..1.0f,
                                    onValueChange = { onConfigChange(dspConfig.copy(panDepth = it)) }
                                )

                                DspSliderRow(
                                    label = "Room Size",
                                    valueText = "${(dspConfig.roomSize * 100).toInt()}%",
                                    value = dspConfig.roomSize,
                                    range = 0.0f..1.0f,
                                    onValueChange = { onConfigChange(dspConfig.copy(roomSize = it)) }
                                )

                                DspSliderRow(
                                    label = "Reverb",
                                    valueText = "${(dspConfig.reverbLevel * 100).toInt()}%",
                                    value = dspConfig.reverbLevel,
                                    range = 0.0f..1.0f,
                                    onValueChange = { onConfigChange(dspConfig.copy(reverbLevel = it)) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Clarity & Bass Boost (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Acoustic Clarity & Bass",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Switch(
                                checked = dspConfig.bassBoostEnabled,
                                onCheckedChange = { onConfigChange(dspConfig.copy(bassBoostEnabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RogObsidian,
                                    checkedTrackColor = RogTextGold,
                                    uncheckedThumbColor = RogTextMuted,
                                    uncheckedTrackColor = RogSurfaceHighlight
                                )
                            )
                        }

                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            DspSliderRow(
                                label = "Bass Gain",
                                valueText = "+${String.format("%.1f", dspConfig.bassBoostGainDb)} dB",
                                value = dspConfig.bassBoostGainDb,
                                range = 0f..10f,
                                onValueChange = { onConfigChange(dspConfig.copy(bassBoostGainDb = it, bassBoostEnabled = it > 0.1f)) }
                            )

                            DspSliderRow(
                                label = "Treble Air",
                                valueText = "+${String.format("%.1f", dspConfig.trebleClarityDb)} dB",
                                value = dspConfig.trebleClarityDb,
                                range = 0f..8f,
                                onValueChange = { onConfigChange(dspConfig.copy(trebleClarityDb = it)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Speed & Pitch (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Speed & Pitch",
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DspSliderRow(
                            label = "Speed",
                            valueText = "${String.format("%.2f", dspConfig.playbackSpeed)}x",
                            value = dspConfig.playbackSpeed,
                            range = 0.5f..2.0f,
                            onValueChange = { onConfigChange(dspConfig.copy(playbackSpeed = it)) }
                        )

                        DspSliderRow(
                            label = "Pitch",
                            valueText = "${if (dspConfig.pitchSemitones > 0) "+" else ""}${String.format("%.1f", dspConfig.pitchSemitones)} st",
                            value = dspConfig.pitchSemitones,
                            range = -12f..12f,
                            onValueChange = { onConfigChange(dspConfig.copy(pitchSemitones = it)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Sleep Timer (No border)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sleep Timer",
                                fontSize = 14.sp,
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

                        Spacer(modifier = Modifier.height(8.dp))

                        val timers = listOf(15, 30, 45, 60)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            timers.forEach { mins ->
                                Button(
                                    onClick = { onSetSleepTimer(mins) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RogSurfaceHighlight),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text("${mins}m", fontSize = 12.sp, color = RogTextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOrbitRadar(
    angleDegrees: Float,
    isActive: Boolean,
    is16D: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width / 2f) * 0.78f

        drawCircle(
            color = if (isActive) RogTextGold.copy(alpha = 0.25f) else RogSurfaceHighlight,
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        if (is16D) {
            val innerRadius = radius * 0.65f
            drawCircle(
                color = if (isActive) RogTextGold.copy(alpha = 0.18f) else RogSurfaceHighlight,
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        drawCircle(
            color = if (isActive) RogTextGold else RogTextMuted,
            radius = 6.dp.toPx(),
            center = center
        )

        if (isActive) {
            val rad = Math.toRadians(angleDegrees.toDouble() - 90.0)
            val satX = center.x + radius * cos(rad).toFloat()
            val satY = center.y + radius * sin(rad).toFloat()

            drawCircle(
                color = RogTextGold,
                radius = 4.dp.toPx(),
                center = Offset(satX, satY)
            )

            if (is16D) {
                val innerRadius = radius * 0.65f
                val rad2 = Math.toRadians(-angleDegrees.toDouble() * 1.5 - 90.0)
                val satX2 = center.x + innerRadius * cos(rad2).toFloat()
                val satY2 = center.y + innerRadius * sin(rad2).toFloat()

                drawCircle(
                    color = RogTextGold.copy(alpha = 0.85f),
                    radius = 3.dp.toPx(),
                    center = Offset(satX2, satY2)
                )
            }
        }
    }
}

@Composable
private fun SpectrumVisualizer(
    bands: FloatArray,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val count = 10
        val totalWidth = size.width
        val barWidth = (totalWidth / count) * 0.65f
        val gap = (totalWidth - (barWidth * count)) / (count - 1).coerceAtLeast(1)

        for (i in 0 until count) {
            val fraction = if (isActive && i < bands.size) bands[i].coerceIn(0.05f, 1.0f) else 0.05f
            val barHeight = (size.height * fraction).coerceAtLeast(2.dp.toPx())
            val left = i * (barWidth + gap)
            val top = (size.height - barHeight).coerceAtLeast(0f)

            drawRoundRect(
                color = if (isActive) RogTextGold else RogSurfaceHighlight,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
private fun DspSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary,
            modifier = Modifier.width(100.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = RogTextGold,
                activeTrackColor = RogTextGold,
                inactiveTrackColor = RogSurfaceHighlight
            )
        )

        Text(
            text = valueText,
            fontSize = 11.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Medium,
            color = RogTextGold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(50.dp)
        )
    }
}
