package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.ui.R
import com.koi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingSeconds: Int,
    onSetTimer: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SLEEP TIMER",
                        fontSize = 12.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (remainingSeconds > 0) {
                            "Fading in ${remainingSeconds / 60}m ${remainingSeconds % 60}s"
                        } else {
                            "Fade out & pause playback"
                        },
                        fontSize = 14.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = RogTextSecondary
                    )
                }

                if (remainingSeconds > 0) {
                    TextButton(
                        onClick = {
                            onSetTimer(0)
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "Turn Off",
                            color = RogTextGold,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val options = listOf(
                15 to "15 minutes",
                30 to "30 minutes",
                45 to "45 minutes",
                60 to "1 hour",
                90 to "1.5 hours"
            )

            options.forEach { (mins, label) ->
                val isActive = remainingSeconds > 0 && (remainingSeconds + 59) / 60 == mins
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) RogSurfaceHighlight else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            onSetTimer(mins)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) RogTextGold else RogTextPrimary
                    )

                    if (isActive) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "Active",
                            tint = RogTextGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
