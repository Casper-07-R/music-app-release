package com.tsuki.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tsuki.ui.R

// Spotify Circular Typeface Family
val CircularFontFamily = FontFamily(
    Font(R.font.circular_sp_book, FontWeight.Normal),
    Font(R.font.circular_sp_bold, FontWeight.Bold),
    Font(R.font.circular_sp_title_bold, FontWeight.ExtraBold),
    Font(R.font.circular_sp_title_black, FontWeight.Black)
)

val SpotifyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        color = SpotifyTextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        color = SpotifyTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = SpotifyTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = SpotifyTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = SpotifyTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = SpotifyTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SpotifyTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = SpotifyTextMuted
    ),
    labelMedium = TextStyle(
        fontFamily = CircularFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = SpotifyTextPrimary
    )
)
