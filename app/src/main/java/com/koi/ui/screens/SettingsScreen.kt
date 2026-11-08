package com.koi.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.koi.backend.player.BatteryOptimizationHelper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.audio.AudioDspConfig
import com.koi.backend.audio.AudioProfile
import com.koi.backend.audio.SpatialMode
import com.koi.backend.update.GitHubAsset
import com.koi.backend.update.GitHubRelease
import com.koi.backend.update.UpdateUiState
import com.koi.ui.R
import com.koi.ui.theme.*
import com.koi.ui.viewmodel.UsageMetrics
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(
    updateState: UpdateUiState,
    dspConfig: AudioDspConfig,
    usageMetrics: UsageMetrics,
    sleepTimerRemainingSeconds: Int,
    onBackClick: () -> Unit,
    onSelectSpatialMode: (SpatialMode) -> Unit,
    onToggleMasterDsp: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSurroundStudio: () -> Unit,
    onOpenProfileSelector: () -> Unit,
    onResetProfile: (AudioProfile) -> Unit = {},
    onSetSleepTimer: (Int) -> Unit,
    onClearCacheClick: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (GitHubRelease, GitHubAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBatteryOpt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var gaplessPlaybackEnabled by remember { mutableStateOf(true) }
    var autoCacheEnabled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RogObsidian)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
    ) {
        // --- 1. TOP APP BAR ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceHighlight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RogTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary
                    )
                    Text(
                        text = "Preferences & Audio Engine",
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- 2. AUDIO & DSP ENGINE ---
        item {
            SettingsSectionHeader(title = "AUDIO & DSP ENGINE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Spatial Audio Mode Selector
                    Text(
                        text = "Spatial Audio Mode",
                        fontSize = 14.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = RogTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            SpatialMode.OFF to "Stereo (Off)",
                            SpatialMode.MODE_8D to "8D Spatial",
                            SpatialMode.MODE_16D to "16D Dual Ring"
                        )
                        modes.forEach { (mode, label) ->
                            val isSelected = dspConfig.spatialMode == mode
                            Button(
                                onClick = { onSelectSpatialMode(mode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) RogTextGold else RogSurfaceHighlight,
                                    contentColor = if (isSelected) Color.Black else RogTextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Sound Profile Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acoustic Profile",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "${dspConfig.activeProfile.label} • ${dspConfig.activeProfile.shortDescription}",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    onResetProfile(dspConfig.activeProfile)
                                    Toast.makeText(context, "${dspConfig.activeProfile.label} reset to default", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset ${dspConfig.activeProfile.label}",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Reset", fontSize = 11.5.sp, fontFamily = CircularFontFamily, color = RogTextGold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = onOpenProfileSelector,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RogSurfaceHighlight,
                                    contentColor = RogTextGold
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Change", fontSize = 11.5.sp, fontFamily = CircularFontFamily)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Surround Studio Quick Access
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "8D / 16D Surround Studio",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Fine-tune orbit speed, reverb, distance & width",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        IconButton(
                            onClick = onOpenSurroundStudio,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceHighlight)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Surround Studio",
                                tint = RogTextGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Equalizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "10-Band Equalizer",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Pre-amp gains & studio frequency curve",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        IconButton(
                            onClick = onOpenEqualizer,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RogSurfaceHighlight)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = RogTextGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Master DSP Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master DSP Processing",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = if (dspConfig.masterEnabled) "Effects active (Spatial, Equalizer & Reverb)" else "Bypassed (Bit-perfect direct audio)",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Switch(
                            checked = dspConfig.masterEnabled,
                            onCheckedChange = { onToggleMasterDsp() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = RogTextGold,
                                uncheckedThumbColor = RogTextSecondary,
                                uncheckedTrackColor = RogSurfaceHighlight
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- 3. PLAYBACK & SYSTEM ---
        item {
            SettingsSectionHeader(title = "PLAYBACK & SYSTEM")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Unrestricted Background Access / Battery Optimization
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isIgnoringBatteryOpt) RogSurfaceHighlight.copy(alpha = 0.5f) else Color(0xFF332005))
                            .clickable {
                                if (!isIgnoringBatteryOpt) {
                                    Toast.makeText(
                                        context,
                                        "Allow unrestricted background access for continuous playback",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                } else {
                                    BatteryOptimizationHelper.openAppDetailsSettings(context)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isIgnoringBatteryOpt) RogTextGold.copy(alpha = 0.15f) else Color(0xFFFFA000).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isIgnoringBatteryOpt) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryAlert,
                                        contentDescription = null,
                                        tint = if (isIgnoringBatteryOpt) RogTextGold else Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Unrestricted Background Access",
                                        fontSize = 14.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RogTextPrimary
                                    )
                                    Text(
                                        text = if (isIgnoringBatteryOpt) {
                                            "Active • Never off during long sessions"
                                        } else {
                                            "Restricted • May stop after 10–15 mins"
                                        },
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        color = if (isIgnoringBatteryOpt) RogTextSecondary else Color(0xFFFFD54F)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isIgnoringBatteryOpt) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RogSurfaceHighlight)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = RogTextGold,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Allowed",
                                            fontSize = 11.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextGold,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Allow unrestricted background access for continuous playback",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RogTextGold,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Enable",
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!isIgnoringBatteryOpt) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Android battery saver stops background playback after 10–15 minutes when minimized. Tap Enable to allow unrestricted background activity.",
                                fontSize = 11.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { BatteryOptimizationHelper.openAppDetailsSettings(context) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manage in Device Battery Settings",
                            fontSize = 11.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Settings",
                            tint = RogTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Streaming Quality
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Streaming Quality",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Lossless Opus / High-bitrate audio stream",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RogSurfaceHighlight)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "High (256 kbps)",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextGold,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sleep Timer",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = if (sleepTimerRemainingSeconds > 0) "${(sleepTimerRemainingSeconds + 59) / 60} minutes remaining" else "Inactive",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = if (sleepTimerRemainingSeconds > 0) RogTextGold else RogTextSecondary
                            )
                        }

                        if (sleepTimerRemainingSeconds > 0) {
                            TextButton(
                                onClick = { onSetSleepTimer(0) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel", color = Color(0xFFEF5350), fontSize = 12.sp)
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RogSurfaceHighlight,
                                    contentColor = RogTextPrimary
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Gapless Playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gapless Playback",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Seamless transitions without silence between songs",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Switch(
                            checked = gaplessPlaybackEnabled,
                            onCheckedChange = { gaplessPlaybackEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = RogTextGold,
                                uncheckedThumbColor = RogTextSecondary,
                                uncheckedTrackColor = RogSurfaceHighlight
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto Cache
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Cache Streams",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "Save played tracks for quick instant re-play",
                                fontSize = 11.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }

                        Switch(
                            checked = autoCacheEnabled,
                            onCheckedChange = { autoCacheEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = RogTextGold,
                                uncheckedThumbColor = RogTextSecondary,
                                uncheckedTrackColor = RogSurfaceHighlight
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- 4. STORAGE & CACHE ---
        item {
            SettingsSectionHeader(title = "STORAGE & CACHE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", usageMetrics.estimatedCacheSizeMb)} MB Cached",
                                fontSize = 14.sp,
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RogTextPrimary
                            )
                            Text(
                                text = "${usageMetrics.cachedStreams} cached tracks & cover images",
                                fontSize = 11.5.sp,
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
                                fontFamily = CircularFontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- 5. SOFTWARE UPDATE (MIGRATED UPDATE CHECKER) ---
        item {
            SettingsSectionHeader(title = "SOFTWARE UPDATE & RELEASES")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RogSurfaceHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_download),
                                    contentDescription = "Updates",
                                    tint = RogTextGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Koi Updater",
                                    fontSize = 14.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RogTextPrimary
                                )
                                Text(
                                    text = "Version ${updateState.currentVersion} • music-app-release",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary
                                )
                            }
                        }

                        if (updateState is UpdateUiState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = RogTextGold,
                                strokeWidth = 2.dp
                            )
                        } else if (updateState !is UpdateUiState.Downloading) {
                            Button(
                                onClick = onCheckForUpdate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RogSurfaceHighlight,
                                    contentColor = RogTextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Check",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (updateState) {
                        is UpdateUiState.Idle -> {
                            Text(
                                text = "Check for new releases and updates directly from Casper-07-R/music-app-release.",
                                fontSize = 12.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary,
                                lineHeight = 16.sp
                            )
                        }

                        is UpdateUiState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Checking for new releases on GitHub...",
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary
                                )
                            }
                        }

                        is UpdateUiState.UpToDate -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Up to date",
                                        tint = RogTextGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Koi is up to date (v${updateState.currentVersion})",
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RogTextPrimary
                                    )
                                }
                                Text(
                                    text = "No newer releases found on Casper-07-R/music-app-release.",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary
                                )

                                val existingApk = updateState.latestRelease?.assets?.firstOrNull {
                                    it.name.endsWith(".apk", ignoreCase = true) ||
                                    it.contentType == "application/vnd.android.package-archive"
                                }
                                if (existingApk != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { onDownloadUpdate(updateState.latestRelease, existingApk) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "Re-download latest package (${existingApk.name})",
                                            fontSize = 11.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextGold
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateUiState.UpdateAvailable -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(RogTextGold)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "NEW RELEASE",
                                                fontSize = 10.sp,
                                                fontFamily = CircularFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = updateState.release.name ?: updateState.release.tagName,
                                            fontSize = 14.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RogTextPrimary
                                        )
                                    }

                                    Text(
                                        text = formatFileSize(updateState.apkAsset.size),
                                        fontSize = 12.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary
                                    )
                                }

                                if (!updateState.release.body.isNullOrBlank()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = RogSurfaceHighlight)
                                    ) {
                                        Text(
                                            text = updateState.release.body.trim(),
                                            fontSize = 11.5.sp,
                                            fontFamily = CircularFontFamily,
                                            color = RogTextSecondary,
                                            modifier = Modifier.padding(10.dp),
                                            maxLines = 5
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onDownloadUpdate(updateState.release, updateState.apkAsset) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RogTextGold,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_download),
                                        contentDescription = "Download",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download & Install Update",
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is UpdateUiState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading ${updateState.release.tagName}...",
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = RogTextPrimary
                                    )

                                    val progressPct = if (updateState.progress >= 0f) "${(updateState.progress * 100).toInt()}%" else "..."
                                    Text(
                                        text = "$progressPct (${formatFileSize(updateState.downloadedBytes)} / ${formatFileSize(updateState.totalBytes)})",
                                        fontSize = 11.5.sp,
                                        fontFamily = CircularFontFamily,
                                        color = RogTextSecondary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { if (updateState.progress >= 0f) updateState.progress.coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = RogTextGold,
                                    trackColor = RogSurfaceHighlight
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = onCancelDownload,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 12.sp,
                                            fontFamily = CircularFontFamily,
                                            color = Color(0xFFEF5350)
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateUiState.ReadyToInstall -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Downloaded",
                                        tint = RogTextGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Update downloaded: ${updateState.release.tagName}",
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RogTextPrimary
                                    )
                                }

                                Text(
                                    text = "Ready to install (${formatFileSize(updateState.apkFile.length())}). If prompted, allow 'Install unknown apps' in Android Settings.",
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary,
                                    lineHeight = 16.sp
                                )

                                Button(
                                    onClick = { onInstallUpdate(updateState.apkFile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RogTextGold,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Text(
                                        text = "Install Update",
                                        fontSize = 13.sp,
                                        fontFamily = CircularFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is UpdateUiState.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Update Check Failed",
                                    fontSize = 13.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF5350)
                                )
                                Text(
                                    text = updateState.message,
                                    fontSize = 11.5.sp,
                                    fontFamily = CircularFontFamily,
                                    color = RogTextSecondary,
                                    lineHeight = 15.sp
                                )

                                if (updateState.canRetry) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Button(
                                        onClick = onCheckForUpdate,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RogSurfaceHighlight,
                                            contentColor = RogTextPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            fontSize = 11.5.sp,
                                            fontFamily = CircularFontFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- 6. ABOUT ---
        item {
            SettingsSectionHeader(title = "ABOUT")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RogSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Koi Music",
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary
                        )
                        Text(
                            text = "v${updateState.currentVersion}",
                            fontSize = 12.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "High-Fidelity 8D / 16D Spatial Audio Player • Free & Ad-Free",
                        fontSize = 11.5.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = RogDivider)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "GitHub Release Repository",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = RogTextPrimary
                    )
                    Text(
                        text = "https://github.com/Casper-07-R/music-app-release",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.5.sp,
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        color = RogTextSecondary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
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
