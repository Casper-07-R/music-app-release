package com.tsuki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsuki.backend.audio.AudioDspConfig
import com.tsuki.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerProfilesSheet(
    dspConfig: AudioDspConfig,
    selectedPreset: String,
    onSelectPreset: (String) -> Unit,
    onConfigChange: (AudioDspConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Reducer" to floatArrayOf(-6f, -5f, -4f, -2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal Clarity" to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, 0f),
        "Treble Boost" to floatArrayOf(0f, 0f, 0f, 0f, 1f, 2f, 3f, 5f, 6f, 6f),
        "Electronic" to floatArrayOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 4f),
        "Rock" to floatArrayOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 3f, 4f, 5f),
        "Hip-Hop" to floatArrayOf(7f, 6f, 5f, 2f, 0f, 0f, 1f, 2f, 2f, 1f),
        "Acoustic" to floatArrayOf(3f, 2f, 1f, 1f, 2f, 2f, 3f, 3f, 2f, 1f),
        "Jazz" to floatArrayOf(4f, 3f, 2f, 1f, 1f, 1f, 2f, 2f, 1f, 0f)
    )

    val bandLabels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    // Local state for the 10-band slider faders (initialized from selected preset)
    var currentBands by remember(selectedPreset) {
        val found = presets.find { it.first == selectedPreset }?.second
        mutableStateOf(found?.clone() ?: FloatArray(10) { 0f })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = RogSurfaceDark,
        scrimColor = RogObsidian.copy(alpha = 0.75f),
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
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = RogTextGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EQUALIZER PROFILES",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "10-Band Graphic Acoustic Tuning",
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = RogTextSecondary
                        )
                    }
                }

                TextButton(
                    onClick = {
                        onSelectPreset("Flat")
                        currentBands = FloatArray(10) { 0f }
                    }
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Scrollable Pill Row
            Text(
                text = "PRESETS",
                fontSize = 10.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (name, presetBands) ->
                    val isSelected = selectedPreset == name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RogSurfaceHighlight else RogSurfaceMedium)
                            .clickable {
                                onSelectPreset(name)
                                currentBands = presetBands.clone()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RogTextGold else RogTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 10-Band Faders Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RogObsidian)
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+12 dB",
                            fontSize = 10.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted
                        )
                        Text(
                            text = "FREQUENCY BANDS (Hz)",
                            fontSize = 9.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "-12 dB",
                            fontSize = 10.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 10-Band Sliders Column Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 10) {
                            val gain = currentBands.getOrElse(i) { 0f }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                            ) {
                                // dB value indicator
                                Text(
                                    text = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}",
                                    fontSize = 9.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = if (gain != 0f) RogTextGold else RogTextMuted
                                )

                                // Vertical Bar representation
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .weight(1f)
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Track line
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(RogSurfaceHighlight)
                                    )

                                    // Thumb fader positioned by gain (-12 to +12)
                                    val fraction = ((gain + 12f) / 24f).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxHeight(fraction)
                                            .width(4.dp)
                                            .background(RogTextGold)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(RogTextGold)
                                    )
                                }

                                // Frequency label
                                Text(
                                    text = bandLabels[i],
                                    fontSize = 9.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-bass & Treble Dial Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bass Boost
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RogSurfaceSubtle)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bass Boost",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary
                        )
                        Text(
                            text = "+${String.format("%.1f", dspConfig.bassBoostGainDb)} dB",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold
                        )
                    }
                    Slider(
                        value = dspConfig.bassBoostGainDb,
                        onValueChange = { onConfigChange(dspConfig.copy(bassBoostGainDb = it, bassBoostEnabled = it > 0.1f)) },
                        valueRange = 0f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = RogTextGold,
                            activeTrackColor = RogTextGold,
                            inactiveTrackColor = RogSurfaceHighlight
                        )
                    )
                }

                // Treble Clarity
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RogSurfaceSubtle)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Treble Air",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary
                        )
                        Text(
                            text = "+${String.format("%.1f", dspConfig.trebleClarityDb)} dB",
                            fontSize = 11.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold
                        )
                    }
                    Slider(
                        value = dspConfig.trebleClarityDb,
                        onValueChange = { onConfigChange(dspConfig.copy(trebleClarityDb = it)) },
                        valueRange = 0f..8f,
                        colors = SliderDefaults.colors(
                            thumbColor = RogTextGold,
                            activeTrackColor = RogTextGold,
                            inactiveTrackColor = RogSurfaceHighlight
                        )
                    )
                }
            }
        }
    }
}
