package com.tsuki.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val RogDarkColorScheme = darkColorScheme(
    primary = RogGoldPrimary,
    onPrimary = RogObsidian,
    primaryContainer = RogSurfaceMedium,
    onPrimaryContainer = RogGoldBright,
    secondary = RogGoldChampagne,
    onSecondary = RogObsidian,
    secondaryContainer = RogSurfaceDark,
    onSecondaryContainer = RogTextPrimary,
    tertiary = RogGoldBright,
    onTertiary = RogObsidian,
    background = RogBlack,
    onBackground = RogTextPrimary,
    surface = RogSurfaceDark,
    onSurface = RogTextPrimary,
    surfaceVariant = RogSurfaceMedium,
    onSurfaceVariant = RogTextSecondary,
    outline = RogGoldBorder,
    outlineVariant = RogDivider
)

@Composable
fun TsukiTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = RogDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = RogObsidian.toArgb()
                window.navigationBarColor = RogObsidian.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpotifyTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}

@Composable
fun SpotifyTheme(
    content: @Composable () -> Unit
) = TsukiTheme(content = content)
