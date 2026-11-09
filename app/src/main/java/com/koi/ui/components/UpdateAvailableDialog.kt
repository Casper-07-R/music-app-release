package com.koi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.io.File
import java.util.Locale

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
            color = RogSurfaceDark,
            tonalElevation = 10.dp,
            border = BorderStroke(1.dp, RogDivider),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateState) {
                    is UpdateUiState.UpdateAvailable -> {
                        UpdateAvailableContent(
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
                        // Other states do not render content inside the dialog
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    currentVersion: String,
    release: GitHubRelease,
    apkAsset: GitHubAsset,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    // Top Icon Badge
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(RogGoldPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = "Update Available",
            tint = RogGoldPrimary,
            modifier = Modifier.size(28.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Badge Pill
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RogGoldPrimary.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "NEW UPDATE AVAILABLE",
            fontSize = 10.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogGoldChampagne,
            letterSpacing = 0.8.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Title
    Text(
        text = "Update Available",
        fontSize = 20.sp,
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        color = RogTextPrimary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Release Tag / Version comparison
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = "v$currentVersion",
            fontSize = 12.5.sp,
            fontFamily = CircularFontFamily,
            color = RogTextMuted
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = RogTextMuted,
            modifier = Modifier.size(13.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = release.tagName,
            fontSize = 13.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextGold
        )

        if (apkAsset.size > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "•  ${formatFileSize(apkAsset.size)}",
                fontSize = 12.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Update Details / Changes Card
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "WHAT'S NEW & BUG FIXES",
            fontSize = 11.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogGoldChampagne,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 220.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RogSurfaceMedium)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                UpdateChangelogView(body = release.body)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Action Buttons: Update and Later
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Later" Button
        OutlinedButton(
            onClick = onLater,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = RogTextSecondary
            ),
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

        // "Update" Button
        Button(
            onClick = onUpdate,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RogGoldPrimary,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Update",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Update",
                fontSize = 14.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
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
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(RogGoldPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Downloading",
            tint = RogGoldPrimary,
            modifier = Modifier.size(28.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Downloading Update...",
        fontSize = 19.sp,
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

    Spacer(modifier = Modifier.height(20.dp))

    // Progress Bar
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

    // Progress text & bytes
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pctText = if (progress >= 0f) "${(progress * 100).toInt()}%" else "Downloading..."
        Text(
            text = pctText,
            fontSize = 12.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.SemiBold,
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

    Spacer(modifier = Modifier.height(22.dp))

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

@Composable
private fun ReadyToInstallContent(
    release: GitHubRelease,
    apkFile: File,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(RogGoldPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Ready to Install",
            tint = RogGoldPrimary,
            modifier = Modifier.size(30.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

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

@Composable
fun UpdateChangelogView(
    body: String?,
    modifier: Modifier = Modifier
) {
    val cleanBody = body?.replace("\r\n", "\n")?.trim()
    val lines = remember(cleanBody) {
        cleanBody?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    if (lines.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChangelogSectionHeader(title = "Added Features")
            ChangelogBulletItem(text = "Enhanced audio processing and playback quality")
            ChangelogBulletItem(text = "UI responsiveness and navigation improvements")

            Spacer(modifier = Modifier.height(4.dp))

            ChangelogSectionHeader(title = "Bug Fixes")
            ChangelogBulletItem(text = "Resolved playback issues and streaming stability")
            ChangelogBulletItem(text = "Background service lifecycle and battery optimizations")
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (line in lines) {
                when {
                    line.startsWith("###") -> {
                        val header = line.removePrefix("###").trim()
                        ChangelogSectionHeader(title = header)
                    }
                    line.startsWith("##") -> {
                        val header = line.removePrefix("##").trim()
                        ChangelogSectionHeader(title = header)
                    }
                    line.startsWith("#") -> {
                        val header = line.removePrefix("#").trim()
                        ChangelogSectionHeader(title = header)
                    }
                    line.startsWith("* ") || line.startsWith("- ") || line.startsWith("+ ") -> {
                        val bullet = line.substring(2).trim()
                        ChangelogBulletItem(text = bullet)
                    }
                    line.startsWith("• ") -> {
                        val bullet = line.removePrefix("• ").trim()
                        ChangelogBulletItem(text = bullet)
                    }
                    else -> {
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
        }
    }
}

@Composable
private fun ChangelogSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.5.sp,
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        color = RogGoldChampagne,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
    )
}

@Composable
private fun ChangelogBulletItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp, end = 8.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(RogGoldPrimary)
        )
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontFamily = CircularFontFamily,
            color = RogTextPrimary,
            lineHeight = 17.sp
        )
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
