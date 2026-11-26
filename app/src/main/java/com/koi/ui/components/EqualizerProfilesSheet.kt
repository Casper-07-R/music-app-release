package com.koi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.audio.AudioDspConfig
import com.koi.backend.audio.AudioProfile
import com.koi.backend.audio.BassBoostFrequency
import com.koi.backend.audio.EqPresets
import com.koi.ui.theme.*
import kotlin.math.abs
import kotlin.math.round

private val AquaCutAccent = Color(0xFF48CAE4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerProfilesSheet(
    dspConfig: AudioDspConfig,
    selectedPreset: String,
    spectrumBands: FloatArray = FloatArray(10) { 0.05f },
    customPresets: Map<String, AudioDspConfig> = emptyMap(),
    showResetButton: Boolean = true,
    onSelectPreset: (String) -> Unit,
    onProfileSelect: (AudioProfile) -> Unit = {},
    onSaveCustomPreset: (String) -> Boolean = { false },
    onDeleteCustomPreset: (String) -> Unit = {},
    onResetEqualizer: () -> Unit = { onSelectPreset("Flat") },
    onConfigChange: (AudioDspConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var customNameInput by remember { mutableStateOf("") }

    val activeBands = remember(dspConfig.eqBands, selectedPreset) {
        if (dspConfig.eqBands.size == 10) dspConfig.eqBands else List(10) { 0f }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = RogSurfaceDark,
        scrimColor = RogObsidian.copy(alpha = 0.78f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RogSurfaceHighlight)
            )
        },
        modifier = Modifier.fillMaxHeight(0.90f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Minimal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = if (dspConfig.eqEnabled && !dspConfig.abBypass) RogTextGold else RogTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Equalizer",
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dspConfig.abBypass) Color(0xFFC62828) else RogSurfaceMedium)
                            .clickable { onConfigChange(dspConfig.copy(abBypass = !dspConfig.abBypass)) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (dspConfig.abBypass) "RAW" else "A/B",
                            fontSize = 10.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (dspConfig.abBypass) Color.White else RogTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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
                        IconButton(
                            onClick = onResetEqualizer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = RogTextGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Curve & Live Spectrum Canvas (zero text clutter)
            FrequencyResponseCard(
                dspConfig = dspConfig,
                spectrumBands = spectrumBands
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RogObsidian)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Bands", "Stage").forEachIndexed { idx, title ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) RogSurfaceHighlight else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RogTextGold else RogTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                GraphicEqTabContent(
                    dspConfig = dspConfig,
                    activeBands = activeBands,
                    selectedPreset = selectedPreset,
                    customPresets = customPresets,
                    onSelectPreset = onSelectPreset,
                    onOpenSaveDialog = {
                        customNameInput = ""
                        showSaveDialog = true
                    },
                    onDeleteCustomPreset = onDeleteCustomPreset,
                    onConfigChange = onConfigChange
                )
            } else {
                StageTabContent(
                    dspConfig = dspConfig,
                    onProfileSelect = onProfileSelect,
                    onConfigChange = onConfigChange
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = RogSurfaceDark,
            title = {
                Text(
                    text = "Save Preset",
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = { if (it.length <= 24) customNameInput = it },
                    singleLine = true,
                    placeholder = {
                        Text("Preset name", color = RogTextMuted, fontSize = 13.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RogTextGold,
                        unfocusedBorderColor = RogSurfaceHighlight,
                        focusedTextColor = RogTextPrimary,
                        unfocusedTextColor = RogTextPrimary,
                        cursorColor = RogTextGold
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onSaveCustomPreset(customNameInput)) {
                            showSaveDialog = false
                        }
                    },
                    enabled = customNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RogGoldPrimary,
                        contentColor = RogObsidian
                    )
                ) {
                    Text("Save", fontFamily = CircularFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", fontFamily = CircularFontFamily, color = RogTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun FrequencyResponseCard(
    dspConfig: AudioDspConfig,
    spectrumBands: FloatArray
) {
    val eqCurvePoints = remember(
        dspConfig.eqEnabled,
        dspConfig.eqBands,
        dspConfig.bassBoostEnabled,
        dspConfig.bassBoostGainDb,
        dspConfig.bassBoostFreq,
        dspConfig.trebleClarityDb,
        dspConfig.trebleFreqHz,
        dspConfig.vocalPresenceDb,
        dspConfig.microDetailLevel,
        dspConfig.safeMode
    ) {
        EqPresets.computeFrequencyResponseCurve(dspConfig, pointCount = 56, includeToneStage = false)
    }

    val totalCurvePoints = remember(
        dspConfig.eqEnabled,
        dspConfig.eqBands,
        dspConfig.bassBoostEnabled,
        dspConfig.bassBoostGainDb,
        dspConfig.bassBoostFreq,
        dspConfig.trebleClarityDb,
        dspConfig.trebleFreqHz,
        dspConfig.vocalPresenceDb,
        dspConfig.microDetailLevel,
        dspConfig.safeMode
    ) {
        EqPresets.computeFrequencyResponseCurve(dspConfig, pointCount = 56, includeToneStage = true)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RogObsidian)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val midY = h * 0.5f
            val maxDbRange = 13f

            drawLine(
                color = RogTextMuted.copy(alpha = 0.35f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            val isSignalActive = dspConfig.masterEnabled && !dspConfig.abBypass
            val barCount = 10
            val colWidth = w / barCount
            val barW = colWidth * 0.54f
            for (i in 0 until barCount) {
                val energy = if (isSignalActive && i < spectrumBands.size) {
                    spectrumBands[i].coerceIn(0.04f, 1.0f)
                } else {
                    0.04f
                }
                val barH = (h * energy * 0.85f).coerceAtLeast(2.dp.toPx())
                val x = i * colWidth + (colWidth - barW) * 0.5f
                drawRoundRect(
                    color = if (isSignalActive) RogGoldPrimary.copy(alpha = 0.18f) else RogSurfaceHighlight.copy(alpha = 0.2f),
                    topLeft = Offset(x, h - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            fun buildCurvePath(points: FloatArray): Path {
                val path = Path()
                val count = points.size
                fun yForDb(db: Float): Float {
                    val clamped = db.coerceIn(-maxDbRange, maxDbRange)
                    return midY - (clamped / maxDbRange) * (midY - 4.dp.toPx())
                }
                val stepX = w / (count - 1).coerceAtLeast(1)
                path.moveTo(0f, yForDb(points[0]))
                for (i in 1 until count) {
                    val prevX = (i - 1) * stepX
                    val prevY = yForDb(points[i - 1])
                    val curX = i * stepX
                    val curY = yForDb(points[i])
                    val ctrlX = (prevX + curX) * 0.5f
                    path.cubicTo(ctrlX, prevY, ctrlX, curY, curX, curY)
                }
                return path
            }

            val totalPath = buildCurvePath(totalCurvePoints)
            drawPath(
                path = totalPath,
                color = AquaCutAccent.copy(alpha = if (dspConfig.abBypass) 0.15f else 0.45f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )

            val eqPath = buildCurvePath(eqCurvePoints)
            val fillPath = Path().apply {
                addPath(eqPath)
                lineTo(w, midY)
                lineTo(0f, midY)
                close()
            }
            val eqColor = when {
                dspConfig.abBypass -> RogTextMuted
                dspConfig.eqEnabled -> RogGoldPrimary
                else -> RogTextSecondary.copy(alpha = 0.45f)
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        eqColor.copy(alpha = 0.24f),
                        eqColor.copy(alpha = 0.04f)
                    )
                )
            )
            drawPath(
                path = eqPath,
                color = eqColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun GraphicEqTabContent(
    dspConfig: AudioDspConfig,
    activeBands: List<Float>,
    selectedPreset: String,
    customPresets: Map<String, AudioDspConfig>,
    onSelectPreset: (String) -> Unit,
    onOpenSaveDialog: () -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onConfigChange: (AudioDspConfig) -> Unit
) {
    val bandLabels = EqPresets.FREQUENCY_LABELS

    var currentBands by remember(activeBands, selectedPreset) {
        mutableStateOf(activeBands.toFloatArray())
    }

    val latestConfig by rememberUpdatedState(dspConfig)
    val latestBands by rememberUpdatedState(currentBands)

    Column {
        // Horizontal Presets + Save Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(RogGoldDim)
                    .clickable { onOpenSaveDialog() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Save Preset",
                    tint = RogTextGold,
                    modifier = Modifier.size(16.dp)
                )
            }

            EqPresets.SPEC_MAP["Flat"]?.let { flatSpec ->
                PresetPillChip(
                    name = flatSpec.name,
                    isSelected = selectedPreset == flatSpec.name,
                    isCustom = false,
                    onClick = {
                        onSelectPreset(flatSpec.name)
                        currentBands = flatSpec.bands.clone()
                    }
                )
            }

            customPresets.forEach { (name, cfg) ->
                PresetPillChip(
                    name = name,
                    isSelected = selectedPreset == name,
                    isCustom = true,
                    onClick = {
                        onSelectPreset(name)
                        currentBands = cfg.eqBands.toFloatArray()
                    },
                    onDelete = { onDeleteCustomPreset(name) }
                )
            }

            EqPresets.SPECS.filter { it.name != "Flat" }.forEach { spec ->
                PresetPillChip(
                    name = spec.name,
                    isSelected = selectedPreset == spec.name,
                    isCustom = false,
                    onClick = {
                        onSelectPreset(spec.name)
                        currentBands = spec.bands.clone()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 10-Band Visual Faders
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RogObsidian)
                .padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 10) {
                    val gain = currentBands.getOrElse(i) { 0f }
                    val formattedGain = formatDbCompact(gain)
                    var lastTapTimeMs by remember(i) { mutableLongStateOf(0L) }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) {
                        Text(
                            text = formattedGain,
                            fontSize = 8.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                gain > 0.05f -> RogTextGold
                                gain < -0.05f -> AquaCutAccent
                                else -> RogTextMuted
                            }
                        )

                        BoxWithConstraints(
                            modifier = Modifier
                                .width(28.dp)
                                .weight(1f)
                                .padding(vertical = 5.dp)
                                .pointerInput(i) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val now = System.currentTimeMillis()
                                        val h = size.height.toFloat()
                                        var moved = false
                                        val startY = down.position.y

                                        fun applyFraction(yPos: Float) {
                                            if (h <= 0f) return
                                            val fraction = (1f - (yPos / h)).coerceIn(0f, 1f)
                                            val db = (round((fraction * 24f - 12f) * 2f) / 2f).coerceIn(-12f, 12f)
                                            if (abs(latestBands.getOrElse(i) { 0f } - db) >= 0.05f) {
                                                val newBands = latestBands.clone()
                                                newBands[i] = db
                                                currentBands = newBands
                                                onConfigChange(
                                                    latestConfig.copy(
                                                        eqEnabled = true,
                                                        eqPreset = "Custom",
                                                        eqBands = newBands.toList()
                                                    )
                                                )
                                            }
                                        }

                                        val isDoubleTap = (now - lastTapTimeMs) in 40..280
                                        lastTapTimeMs = now
                                        if (!isDoubleTap) applyFraction(down.position.y)
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) break
                                            if (abs(change.position.y - startY) > 6f) moved = true
                                            applyFraction(change.position.y)
                                            change.consume()
                                        }

                                        if (isDoubleTap && !moved) {
                                            val newBands = latestBands.clone()
                                            newBands[i] = 0f
                                            currentBands = newBands
                                            onConfigChange(
                                                latestConfig.copy(
                                                    eqEnabled = true,
                                                    eqPreset = "Custom",
                                                    eqBands = newBands.toList()
                                                )
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(RogSurfaceHighlight)
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(10.dp)
                                    .height(1.dp)
                                    .background(RogTextMuted.copy(alpha = 0.6f))
                            )

                            val fraction = ((gain + 12f) / 24f).coerceIn(0f, 1f)
                            val trackH = maxHeight
                            val fillColor = when {
                                !dspConfig.eqEnabled || dspConfig.abBypass -> RogTextMuted
                                gain >= 0f -> RogTextGold
                                else -> AquaCutAccent
                            }

                            if (gain > 0.05f) {
                                val boostFrac = (gain / 12f).coerceIn(0f, 1f) * 0.5f
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = trackH * 0.5f)
                                        .fillMaxHeight(boostFrac)
                                        .width(3.dp)
                                        .background(fillColor)
                                )
                            } else if (gain < -0.05f) {
                                val cutFrac = (-gain / 12f).coerceIn(0f, 1f) * 0.5f
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = trackH * 0.5f)
                                        .fillMaxHeight(cutFrac)
                                        .width(3.dp)
                                        .background(fillColor)
                                )
                            }

                            val thumbSize = 12.dp
                            val bottomOffset = (fraction * (trackH.value - thumbSize.value)).dp.coerceAtLeast(0.dp)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = bottomOffset)
                                    .size(thumbSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (dspConfig.eqEnabled && !dspConfig.abBypass) {
                                            if (gain < -0.05f) AquaCutAccent else RogTextGold
                                        } else {
                                            RogSurfaceHighlight
                                        }
                                    )
                            )
                        }

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

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Pre-Amp & Auto-Headroom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RogSurfaceSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            StageSliderRow(
                label = "Pre-Amp",
                valueText = "${if (dspConfig.eqPreampDb > 0.05f) "+" else ""}${String.format("%.1f", dspConfig.eqPreampDb)} dB",
                value = dspConfig.eqPreampDb,
                range = -12f..6f,
                onValueChange = { raw ->
                    val snapped = round(raw * 2f) / 2f
                    onConfigChange(dspConfig.copy(eqPreampDb = snapped.coerceIn(-12f, 6f)))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Headroom Guard",
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary
                )
                Switch(
                    checked = dspConfig.autoHeadroomEnabled,
                    onCheckedChange = { onConfigChange(dspConfig.copy(autoHeadroomEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RogObsidian,
                        checkedTrackColor = RogTextGold,
                        uncheckedThumbColor = RogTextMuted,
                        uncheckedTrackColor = RogSurfaceHighlight
                    )
                )
            }
        }
    }
}

@Composable
private fun StageTabContent(
    dspConfig: AudioDspConfig,
    onProfileSelect: (AudioProfile) -> Unit,
    onConfigChange: (AudioDspConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Compact Visual Profile Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioProfile.values().forEach { profile ->
                val isSelected = dspConfig.activeProfile == profile
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) RogSurfaceHighlight else RogSurfaceSubtle)
                        .clickable { onProfileSelect(profile) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = profileIcon(profile),
                        contentDescription = null,
                        tint = if (isSelected) RogTextGold else RogTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = profile.label,
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) RogTextGold else RogTextPrimary
                    )
                }
            }
        }

        // Clean Sliders (zero explanatory paragraphs)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RogSurfaceSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            StageSliderRow(
                label = "Bass",
                valueText = "+${String.format("%.1f", dspConfig.bassBoostGainDb)} dB",
                value = dspConfig.bassBoostGainDb,
                range = 0f..10f,
                onValueChange = {
                    onConfigChange(dspConfig.copy(bassBoostGainDb = it, bassBoostEnabled = it > 0.1f))
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BassBoostFrequency.values().forEach { freq ->
                    val isSelected = dspConfig.bassBoostFreq == freq
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) RogSurfaceHighlight else RogSurfaceMedium)
                            .clickable { onConfigChange(dspConfig.copy(bassBoostFreq = freq)) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = freq.shortLabel,
                            fontSize = 10.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RogTextGold else RogTextSecondary
                        )
                    }
                }
            }

            StageSliderRow(
                label = "Vocals",
                valueText = "${if (dspConfig.vocalPresenceDb > 0.05f) "+" else ""}${String.format("%.1f", dspConfig.vocalPresenceDb)} dB",
                value = dspConfig.vocalPresenceDb,
                range = -4f..6f,
                onValueChange = { onConfigChange(dspConfig.copy(vocalPresenceDb = it)) }
            )

            StageSliderRow(
                label = "Treble",
                valueText = "+${String.format("%.1f", dspConfig.trebleClarityDb)} dB",
                value = dspConfig.trebleClarityDb,
                range = 0f..8f,
                onValueChange = { onConfigChange(dspConfig.copy(trebleClarityDb = it)) }
            )

            StageSliderRow(
                label = "Detail",
                valueText = "${(dspConfig.microDetailLevel * 100).toInt()}%",
                value = dspConfig.microDetailLevel,
                range = 0f..1f,
                onValueChange = { onConfigChange(dspConfig.copy(microDetailLevel = it)) }
            )

            StageSliderRow(
                label = "Width",
                valueText = "${String.format("%.2f", dspConfig.stereoWidth)}x",
                value = dspConfig.stereoWidth,
                range = 0.7f..1.6f,
                onValueChange = { onConfigChange(dspConfig.copy(stereoWidth = it)) }
            )

            StageSliderRow(
                label = "Crossfeed",
                valueText = "${(dspConfig.crossfeedAmount * 100).toInt()}%",
                value = dspConfig.crossfeedAmount,
                range = 0f..1f,
                onValueChange = {
                    onConfigChange(dspConfig.copy(crossfeedAmount = it, crossfeed = it > 0.02f))
                }
            )

            StageSliderRow(
                label = "Reverb",
                valueText = "${(dspConfig.reverbLevel * 100).toInt()}%",
                value = dspConfig.reverbLevel,
                range = 0f..0.7f,
                onValueChange = { onConfigChange(dspConfig.copy(reverbLevel = it)) }
            )

            StageSliderRow(
                label = "Room",
                valueText = "${(dspConfig.roomSize * 100).toInt()}%",
                value = dspConfig.roomSize,
                range = 0.1f..1f,
                onValueChange = { onConfigChange(dspConfig.copy(roomSize = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Limiter",
                    fontSize = 12.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary
                )
                Switch(
                    checked = dspConfig.safeMode && dspConfig.limiterEnabled,
                    onCheckedChange = { enabled ->
                        onConfigChange(dspConfig.copy(safeMode = enabled, limiterEnabled = enabled))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RogObsidian,
                        checkedTrackColor = RogTextGold,
                        uncheckedThumbColor = RogTextMuted,
                        uncheckedTrackColor = RogSurfaceHighlight
                    )
                )
            }
        }
    }
}

@Composable
private fun PresetPillChip(
    name: String,
    isSelected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> RogSurfaceHighlight
                    isCustom -> RogGoldDim
                    else -> RogSurfaceMedium
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            fontFamily = CircularFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) RogTextGold else if (isCustom) RogGoldChampagne else RogTextSecondary
        )
        if (isCustom && isSelected && onDelete != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete $name",
                tint = RogTextSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
private fun StageSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontFamily = CircularFontFamily,
            color = RogTextSecondary,
            modifier = Modifier.width(74.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
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
            fontWeight = FontWeight.Bold,
            color = RogTextGold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
}

private fun formatDbCompact(gain: Float): String {
    val snapped = (round(gain * 2f) / 2f)
    if (abs(snapped) < 0.05f) return "0"
    val isWhole = abs(snapped - snapped.toInt().toFloat()) < 0.05f
    val prefix = if (snapped > 0f) "+" else ""
    return if (isWhole) "$prefix${snapped.toInt()}" else "$prefix${String.format("%.1f", snapped)}"
}
