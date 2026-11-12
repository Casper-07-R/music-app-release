package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.audio.AudioDspConfig
import com.koi.backend.audio.EqPresets
import com.koi.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerProfilesSheet(
    dspConfig: AudioDspConfig,
    selectedPreset: String,
    showResetButton: Boolean = true,
    onSelectPreset: (String) -> Unit,
    onConfigChange: (AudioDspConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = EqPresets.PRESETS.toList()
    val bandLabels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state for the 10-band slider faders
    var currentBands by remember(dspConfig.eqBands, selectedPreset) {
        val initial = if (dspConfig.eqBands.size == 10) {
            dspConfig.eqBands.toFloatArray()
        } else {
            EqPresets.PRESETS[selectedPreset]?.clone() ?: FloatArray(10) { 0f }
        }
        mutableStateOf(initial)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            // Header with EQ status and Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = if (dspConfig.eqEnabled) RogTextGold else RogTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EQUALIZER",
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (dspConfig.eqEnabled) RogTextGold else RogTextSecondary,
                                letterSpacing = 1.sp
                            )
                            if (!dspConfig.eqEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(Bypassed)",
                                    fontSize = 11.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextMuted
                                )
                            }
                        }
                        Text(
                            text = "10-Band Graphic Acoustic Tuning",
                            fontSize = 13.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = RogTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dspConfig.eqEnabled,
                        onCheckedChange = { isChecked ->
                            onConfigChange(dspConfig.copy(eqEnabled = isChecked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RogObsidian,
                            checkedTrackColor = RogTextGold,
                            uncheckedThumbColor = RogTextMuted,
                            uncheckedTrackColor = RogSurfaceSubtle
                        )
                    )

                    if (showResetButton) {
                        Spacer(modifier = Modifier.width(6.dp))

                        TextButton(
                            onClick = {
                                onSelectPreset("Flat")
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

            // 10-Band Interactive Faders Box
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
                            text = "FREQUENCY BANDS (Hz) • LIVE GAIN",
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

                    // 10-Band Interactive Sliders Row Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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

                                // Interactive Vertical Fader Column
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                        .pointerInput(i, dspConfig) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown()
                                                val h = size.height.toFloat()
                                                if (h > 0f) {
                                                    val fraction = (1f - (down.position.y / h)).coerceIn(0f, 1f)
                                                    val db = (fraction * 24f - 12f).roundToInt().toFloat()
                                                    val newBands = currentBands.clone()
                                                    newBands[i] = db
                                                    currentBands = newBands
                                                    onSelectPreset("Custom")
                                                    onConfigChange(
                                                        dspConfig.copy(
                                                            eqEnabled = true,
                                                            eqPreset = "Custom",
                                                            eqBands = newBands.toList()
                                                        )
                                                    )
                                                }
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull() ?: break
                                                    if (!change.pressed) break
                                                    val fraction = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                                                    val db = (fraction * 24f - 12f).roundToInt().toFloat()
                                                    if (currentBands.getOrElse(i) { 0f } != db) {
                                                        val newBands = currentBands.clone()
                                                        newBands[i] = db
                                                        currentBands = newBands
                                                        onSelectPreset("Custom")
                                                        onConfigChange(
                                                            dspConfig.copy(
                                                                eqEnabled = true,
                                                                eqPreset = "Custom",
                                                                eqBands = newBands.toList()
                                                            )
                                                        )
                                                    }
                                                    change.consume()
                                                }
                                            }
                                        },
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

                                    // Center (0 dB) tick indicator
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .width(8.dp)
                                            .height(1.dp)
                                            .background(RogTextMuted.copy(alpha = 0.5f))
                                    )

                                    // Thumb fader positioned by gain (-12 to +12)
                                    val fraction = ((gain + 12f) / 24f).coerceIn(0f, 1f)

                                    // Active colored fill from center or thumb
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxHeight(fraction)
                                            .width(3.dp)
                                            .background(if (dspConfig.eqEnabled) RogTextGold else RogTextMuted)
                                    )

                                    // Thumb knob
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = (fraction * (maxHeight.value - 12.dp.value)).dp.coerceAtLeast(0.dp))
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (dspConfig.eqEnabled) RogTextGold else RogSurfaceHighlight)
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
