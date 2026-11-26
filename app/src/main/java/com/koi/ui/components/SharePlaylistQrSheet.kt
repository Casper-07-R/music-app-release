package com.koi.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.backend.share.KoiQrTheme
import com.koi.backend.share.QrCodeHelper
import com.koi.backend.share.QrCodeHelper.QrGenerationResult
import com.koi.ui.R
import com.koi.ui.model.Playlist
import com.koi.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePlaylistQrSheet(
    playlist: Playlist,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTheme by remember { mutableStateOf(KoiQrTheme.KOI_POND) }
    var generationResult by remember(playlist.id, playlist.tracks, selectedTheme) {
        mutableStateOf<QrGenerationResult?>(null)
    }
    var isSharing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(playlist.id, playlist.tracks, selectedTheme) {
        generationResult = null
        generationResult = QrCodeHelper.generatePlaylistQr(
            playlistTitle = playlist.title,
            playlistDescription = playlist.description,
            coverUrl = playlist.thumbnailUrl,
            songs = playlist.tracks.map { it.toSongItem() },
            theme = selectedTheme
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = RogSurfaceDark,
        scrimColor = RogObsidian.copy(alpha = 0.72f),
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
                .padding(bottom = 26.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHARE PLAYLIST QR",
                        fontSize = 11.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.title,
                        fontSize = 19.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = RogTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (val result = generationResult) {
                null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1440f / 1600f)
                            .widthIn(max = 330.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(RogSurfaceMedium),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = RogGoldPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Crafting Koi QR v40...",
                                fontSize = 12.5.sp,
                                fontFamily = CircularFontFamily,
                                color = RogTextSecondary
                            )
                        }
                    }
                }

                is QrGenerationResult.TooLarge -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RogSurfaceMedium)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = RogGoldChampagne,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Playlist exceeds QR Version 40 capacity",
                            fontSize = 15.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All ${result.songCount} songs compressed to ${result.encodedChars} chars, while a single Version 40 QR holds up to ${result.maxChars} chars. Try splitting this playlist into smaller collections.",
                            fontSize = 12.5.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is QrGenerationResult.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RogSurfaceMedium)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = result.message,
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is QrGenerationResult.Success -> {
                    // Full Koi Pond Styled QR Share Card Preview
                    val glowBrush = Brush.linearGradient(
                        listOf(
                            Color(result.theme.accentPrimary),
                            Color(result.theme.accentSecondary),
                            Color(result.theme.accentPrimary)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .widthIn(max = 336.dp)
                            .fillMaxWidth()
                            .aspectRatio(1440f / 1600f)
                            .shadow(16.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.5.dp, glowBrush, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = result.shareCardBitmap.asImageBitmap(),
                            contentDescription = "Koi Playlist QR Card Version 40",
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Koi QR Theme Switcher Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        KoiQrTheme.entries.forEach { theme ->
                            val isSelected = theme == selectedTheme
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) Color(theme.accentPrimary).copy(alpha = 0.18f)
                                        else RogSurfaceMedium
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(theme.accentPrimary) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedTheme = theme }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(theme.accentPrimary), Color(theme.accentSecondary))
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = theme.label,
                                    fontSize = 12.sp,
                                    fontFamily = CircularFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) RogTextPrimary else RogTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compact Compression & Version 40 Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RogSurfaceSubtle)
                            .border(1.dp, RogDivider, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = Color(result.theme.accentPrimary),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR v40 (EC-${result.errorCorrectionLevel.name}) • ${result.payload.compressionMethod}: ${result.payload.jsonBytesCount} B → ${result.payload.compressedBytesCount} B (${result.payload.savedPercent}% saved)",
                            fontSize = 11.5.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = RogTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isSaving) return@OutlinedButton
                                isSaving = true
                                scope.launch {
                                    val saveResult = QrCodeHelper.saveQrToGallery(
                                        context = context,
                                        shareCardBitmap = result.shareCardBitmap,
                                        playlistTitle = playlist.title
                                    )
                                    isSaving = false
                                    saveResult.onSuccess { fileName ->
                                        Toast.makeText(context, "Saved $fileName to Pictures/Koi", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Failed to save image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RogTextPrimary),
                            border = BorderStroke(1.dp, RogSurfaceHighlight),
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = RogGoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Saving..." else "Save Image",
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = RogTextPrimary
                            )
                        }

                        Button(
                            onClick = {
                                if (isSharing) return@Button
                                isSharing = true
                                scope.launch {
                                    val shareRes = QrCodeHelper.shareQrImage(
                                        context = context,
                                        shareCardBitmap = result.shareCardBitmap,
                                        playlistTitle = playlist.title
                                    )
                                    isSharing = false
                                    shareRes.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Could not open share sheet", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSharing,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RogGoldPrimary),
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier.weight(1.25f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share),
                                contentDescription = null,
                                tint = RogObsidian,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSharing) "Preparing..." else "Share QR",
                                fontFamily = CircularFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RogObsidian
                            )
                        }
                    }
                }
            }
        }
    }
}
