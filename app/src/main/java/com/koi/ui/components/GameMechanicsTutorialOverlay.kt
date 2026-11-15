package com.koi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koi.ui.R
import com.koi.ui.theme.*

enum class TutorialTarget {
    SPATIAL_DECK,
    SOUND_STATIONS,
    VAULT,
    SETTINGS,
    SLEEP_TIMER
}

data class TutorialMission(
    val stepIndex: Int,
    val target: TutorialTarget,
    val questBadge: String,
    val title: String,
    val loreDescription: String,
    val objectivePrompt: String,
    val actionButtonText: String,
    val iconVector: Any // ImageVector or Int drawable resource
)

/**
 * AAA Game-style interactive walkthrough tutorial overlay.
 * Darkens the entire screen with a cinematic scrim, cuts out and spotlights
 * the active feature with a pulsing glowing aperture halo, and displays a
 * HUD quest dialogue box teaching the game mechanics as the user interacts.
 */
@Composable
fun GameMechanicsTutorialOverlay(
    currentStep: Int,
    username: String,
    targetBoundsMap: Map<TutorialTarget, Rect>,
    onNextStep: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onActionClick: (TutorialTarget) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val missions = remember {
        listOf(
            TutorialMission(
                stepIndex = 0,
                target = TutorialTarget.SPATIAL_DECK,
                questBadge = "MISSION 1 OF 5 • AUDIO ENGINE & EQ",
                title = "360° Spatializer & 10-Band EQ",
                loreDescription = "Ordinary music is flat stereo. In Koi, your audio engine features real-time 360° orbital spatialization (8D / 16D) harmonized with a 10-band studio equalizer. Music revolves around your head for a live concert acoustic space.",
                objectivePrompt = "👉 Interactive Mechanic: Tap [ 8D ] or [ 16D ] on the player deck above right now to feel the sound rotate around you!",
                actionButtonText = "Next Mission →",
                iconVector = Icons.Default.GraphicEq
            ),
            TutorialMission(
                stepIndex = 1,
                target = TutorialTarget.SOUND_STATIONS,
                questBadge = "MISSION 2 OF 5 • SOUNDSCAPES",
                title = "Curated Sound Stations",
                loreDescription = "Need deep focus, chill beats, or sleep? Jump straight into handcrafted acoustic spaces tuned specifically for 8D spatial acoustics and ambient binaural beats.",
                objectivePrompt = "👉 Interactive Mechanic: Tap any station card above to switch tracks and hear continuous soundscapes!",
                actionButtonText = "Next Mission →",
                iconVector = Icons.Default.AutoAwesome
            ),
            TutorialMission(
                stepIndex = 2,
                target = TutorialTarget.VAULT,
                questBadge = "MISSION 3 OF 5 • VAULT",
                title = "The Private Music Vault",
                loreDescription = "Every track you heart or download is stored in your private vault with zero-latency playback. Enjoy seamless listening even with zero mobile data or in airplane mode.",
                objectivePrompt = "👉 Your Offline Vault: All your downloaded songs and playlists stay right here for instant offline playback.",
                actionButtonText = "Next Mission →",
                iconVector = Icons.Default.CheckCircle
            ),
            TutorialMission(
                stepIndex = 3,
                target = TutorialTarget.SETTINGS,
                questBadge = "MISSION 4 OF 5 • ZERO-BUFFER STREAMING",
                title = "Adaptive Quality & Fallbacks",
                loreDescription = "Weak cellular coverage or traveling? Koi dynamically monitors network bandwidth and automatically falls back through 4 audio tiers (High, Med, Low, Data Saver). Playback never tears, stutters, or buffers even on low network coverage!",
                objectivePrompt = "👉 Interactive Mechanic: Tap the Settings gear icon above to explore Streaming Quality & Coverage settings, or switch between Auto and Data Saver mode!",
                actionButtonText = "Next Mission →",
                iconVector = Icons.Default.Speed
            ),
            TutorialMission(
                stepIndex = 4,
                target = TutorialTarget.SLEEP_TIMER,
                questBadge = "MISSION 5 OF 5 • BEDTIME & GESTURES",
                title = "Fade-Out Sleep Timer & Swipe Controls",
                loreDescription = "Falling asleep to music? Koi's smart timer never cuts off abruptly — it gently decrescendos volume to zero over the final 3 minutes. Plus, swipe the floating mini-player left or right to dismiss it cleanly!",
                objectivePrompt = "👉 Interactive Mechanic: Tap the bedtime moon icon above to set your sleep timer, and swipe the mini-player sideways when you want it out of view!",
                actionButtonText = "Finish Academy & Listen 🎧",
                iconVector = Icons.Default.Bedtime
            )
        )
    }

    val isGraduation = currentStep >= missions.size

    // Pulsing halo animation for spotlight aperture
    val infiniteTransition = rememberInfiniteTransition(label = "spotlightPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val arrowBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(alpha = 0.99f) // Required for BlendMode.Clear cutout
    ) {
        if (!isGraduation) {
            val mission = missions[currentStep]
            val recordedBounds = targetBoundsMap[mission.target]

            // Calculate active spotlight cutout bounds with safe fallback
            val targetRect = recordedBounds ?: when (mission.target) {
                TutorialTarget.SPATIAL_DECK -> Rect(
                    left = screenWidthPx * 0.04f,
                    top = screenHeightPx * 0.08f,
                    right = screenWidthPx * 0.96f,
                    bottom = screenHeightPx * 0.28f
                )
                TutorialTarget.SOUND_STATIONS -> Rect(
                    left = screenWidthPx * 0.04f,
                    top = screenHeightPx * 0.32f,
                    right = screenWidthPx * 0.96f,
                    bottom = screenHeightPx * 0.48f
                )
                TutorialTarget.VAULT -> Rect(
                    left = screenWidthPx * 0.04f,
                    top = screenHeightPx * 0.52f,
                    right = screenWidthPx * 0.96f,
                    bottom = screenHeightPx * 0.64f
                )
                TutorialTarget.SETTINGS -> Rect(
                    left = screenWidthPx - with(density) { 56.dp.toPx() },
                    top = with(density) { 38.dp.toPx() },
                    right = screenWidthPx - with(density) { 8.dp.toPx() },
                    bottom = with(density) { 88.dp.toPx() }
                )
                TutorialTarget.SLEEP_TIMER -> Rect(
                    left = screenWidthPx - with(density) { 145.dp.toPx() },
                    top = with(density) { 38.dp.toPx() },
                    right = screenWidthPx - with(density) { 85.dp.toPx() },
                    bottom = with(density) { 88.dp.toPx() }
                )
            }

            val paddingPx = with(density) {
                if (mission.target == TutorialTarget.SLEEP_TIMER || mission.target == TutorialTarget.SETTINGS) 10.dp.toPx() else 8.dp.toPx()
            }
            val cutoutLeft = (targetRect.left - paddingPx).coerceAtLeast(0f)
            val cutoutTop = (targetRect.top - paddingPx).coerceAtLeast(0f)
            val cutoutRight = (targetRect.right + paddingPx).coerceAtMost(screenWidthPx)
            val cutoutBottom = (targetRect.bottom + paddingPx).coerceAtMost(screenHeightPx)
            val cutoutWidth = (cutoutRight - cutoutLeft).coerceAtLeast(10f)
            val cutoutHeight = (cutoutBottom - cutoutTop).coerceAtLeast(10f)

            // 1. Cinematic Darkening Scrim with Aperture Cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background dark scrim
                drawRect(color = RogObsidian.copy(alpha = 0.88f))

                // Cut out the targeted feature using BlendMode.Clear so the real feature shines through
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cutoutLeft, cutoutTop),
                    size = Size(cutoutWidth, cutoutHeight),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    blendMode = BlendMode.Clear
                )

                // Glowing pulsing border around the spotlighted feature
                drawRoundRect(
                    color = RogGoldPrimary.copy(alpha = pulseAlpha),
                    topLeft = Offset(cutoutLeft, cutoutTop),
                    size = Size(cutoutWidth, cutoutHeight),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Outer accent aura
                drawRoundRect(
                    color = RogGoldChampagne.copy(alpha = pulseAlpha * 0.35f),
                    topLeft = Offset(cutoutLeft - 4.dp.toPx(), cutoutTop - 4.dp.toPx()),
                    size = Size(cutoutWidth + 8.dp.toPx(), cutoutHeight + 8.dp.toPx()),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Click Interceptors ONLY for Dark Areas Outside the Cutout
            // Taps directly inside [cutoutLeft, cutoutTop, cutoutRight, cutoutBottom] fall through
            // directly to the underlying real UI so the user can interact freely without skipping!

            // Top dark strip (from y=0 to cutoutTop)
            if (cutoutTop > 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { cutoutTop.toDp() })
                        .align(Alignment.TopCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks in top dark zone */ }
                )
            }

            // Bottom dark strip (from cutoutBottom to screen bottom)
            val bottomHeightPx = screenHeightPx - cutoutBottom
            if (bottomHeightPx > 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { bottomHeightPx.toDp() })
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks in bottom dark zone */ }
                )
            }

            // Left dark strip (between cutoutTop and cutoutBottom, from x=0 to cutoutLeft)
            if (cutoutLeft > 1f) {
                Box(
                    modifier = Modifier
                        .width(with(density) { cutoutLeft.toDp() })
                        .height(with(density) { cutoutHeight.toDp() })
                        .offset(
                            x = 0.dp,
                            y = with(density) { cutoutTop.toDp() }
                        )
                        .align(Alignment.TopStart)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks in left dark zone */ }
                )
            }

            // Right dark strip (between cutoutTop and cutoutBottom, from cutoutRight to screen width)
            val rightWidthPx = screenWidthPx - cutoutRight
            if (rightWidthPx > 1f) {
                Box(
                    modifier = Modifier
                        .width(with(density) { rightWidthPx.toDp() })
                        .height(with(density) { cutoutHeight.toDp() })
                        .offset(
                            x = with(density) { cutoutRight.toDp() },
                            y = with(density) { cutoutTop.toDp() }
                        )
                        .align(Alignment.TopStart)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks in right dark zone */ }
                )
            }

            // 3. HUD Dialogue Box (Positioned strategically above or below the cutout)
            val isCutoutInTopHalf = (cutoutTop + cutoutHeight / 2) < screenHeightPx * 0.5f
            val targetCenterX = cutoutLeft + cutoutWidth / 2f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .padding(
                        top = if (isCutoutInTopHalf) with(density) { (cutoutBottom + 16.dp.toPx()).toDp() } else 40.dp,
                        bottom = if (!isCutoutInTopHalf) with(density) { (screenHeightPx - cutoutTop + 16.dp.toPx()).toDp() } else 40.dp
                    ),
                contentAlignment = if (isCutoutInTopHalf) Alignment.TopCenter else Alignment.BottomCenter
            ) {
                GameHudQuestCard(
                    mission = mission,
                    totalMissions = missions.size,
                    targetCenterX = targetCenterX,
                    screenWidthPx = screenWidthPx,
                    arrowBounce = arrowBounce,
                    isPointingUp = isCutoutInTopHalf,
                    onNext = onNextStep,
                    onSkip = onSkip
                )
            }
        } else {
            // 4. Victory / Tutorial Completed Screen
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = RogObsidian.copy(alpha = 0.94f))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TutorialGraduationCard(
                    username = username,
                    onEnterApp = onComplete
                )
            }
        }
    }
}

/**
 * High-tech game HUD Quest dialogue box.
 */
@Composable
private fun GameHudQuestCard(
    mission: TutorialMission,
    totalMissions: Int,
    targetCenterX: Float,
    screenWidthPx: Float,
    arrowBounce: Float,
    isPointingUp: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current
    val arrowHorizontalOffset = with(density) {
        val rawX = targetCenterX - 18.dp.toPx()
        rawX.coerceIn(24.dp.toPx(), screenWidthPx - 72.dp.toPx()).toDp()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isPointingUp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = arrowHorizontalOffset)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Target above",
                    tint = RogGoldPrimary,
                    modifier = Modifier
                        .offset(y = (-arrowBounce).dp)
                        .size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = RogSurfaceDark),
            border = BorderStroke(
                1.2.dp,
                Brush.linearGradient(
                    listOf(
                        RogGoldPrimary,
                        RogGoldChampagne.copy(alpha = 0.6f),
                        RogDivider
                    )
                )
            )
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Quest Header Pill & Skip link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RogGoldDim)
                        .border(
                            BorderStroke(0.8.dp, RogGoldPrimary.copy(alpha = 0.6f)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RogGoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = mission.questBadge,
                        fontSize = 10.5.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogGoldChampagne,
                        letterSpacing = 0.6.sp
                    )
                }

                Text(
                    text = "Skip Tutorial",
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextMuted,
                    modifier = Modifier.clickable { onSkip() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quest Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(RogGoldPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mission.iconVector is androidx.compose.ui.graphics.vector.ImageVector) {
                        Icon(
                            imageVector = mission.iconVector,
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    } else if (mission.iconVector is Int) {
                        Icon(
                            painter = painterResource(id = mission.iconVector),
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Text(
                    text = mission.title,
                    fontSize = 18.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lore / Mechanic Description
            Text(
                text = mission.loreDescription,
                fontSize = 12.5.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Objective Prompt Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RogSurfaceMedium.copy(alpha = 0.7f))
                    .border(BorderStroke(0.6.dp, RogGoldPrimary.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = mission.objectivePrompt,
                    fontSize = 11.5.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RogGoldChampagne,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action / Next Mission Button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogGoldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = mission.actionButtonText,
                    fontSize = 14.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mission Step Progress Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalMissions) {
                    val isActive = i == mission.stepIndex
                    val isPast = i < mission.stepIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isActive) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> RogGoldPrimary
                                    isPast -> RogGoldChampagne.copy(alpha = 0.7f)
                                    else -> RogSurfaceHighlight
                                }
                            )
                    )
                }
            }
        }
    }

    if (!isPointingUp) {
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = arrowHorizontalOffset)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Target below",
                tint = RogGoldPrimary,
                modifier = Modifier
                    .offset(y = arrowBounce.dp)
                    .size(24.dp)
            )
        }
    }
}
}

/**
 * Victory / Graduation Card shown when the tutorial is completed.
 */
@Composable
private fun TutorialGraduationCard(
    username: String,
    onEnterApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = RogSurfaceDark),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(
                    RogGoldPrimary,
                    RogGoldChampagne,
                    RogDivider
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(RogGoldPrimary.copy(alpha = 0.2f))
                    .border(BorderStroke(1.2.dp, RogGoldPrimary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Victory",
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RogGoldDim)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "🏆 TUTORIAL COMPLETE • AUDIO MASTER",
                    fontSize = 11.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogGoldChampagne,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Welcome to Koi, $username!",
                fontSize = 22.sp,
                fontFamily = CircularFontFamily,
                fontWeight = FontWeight.Bold,
                color = RogTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You've mastered all core mechanics of the Koi Audio Engine — from 360° spatial sound and 10-band EQ to adaptive zero-buffering streaming and gentle bedtime timers. Put on your headphones and immerse yourself in pure sound.",
                fontSize = 13.sp,
                fontFamily = CircularFontFamily,
                color = RogTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onEnterApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RogGoldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_headphones),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Begin Listening in 360°",
                    fontSize = 15.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
