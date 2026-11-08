package com.koi.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =========================================================================
// MINIMAL BLACK & GOLD THEME TOKENS (Frameless, Clean & Understated)
// =========================================================================

// Deep Minimal Carbon Surfaces (No harsh borders, pure minimal depth)
val RogObsidian = Color(0xFF09090B)          // Deep obsidian black
val RogBlack = Color(0xFF0F0F12)             // Viewport surface
val RogSurfaceDark = Color(0xFF141417)       // Card & component background
val RogSurfaceMedium = Color(0xFF1A1A1E)     // Elevated items & search bar
val RogSurfaceHighlight = Color(0xFF232328)  // Active selection
val RogSurfaceSubtle = Color(0xFF111114)     // Recessed wells
val RogDivider = Color(0x14FFFFFF)           // Subdued 8% divider

// Surface aliases
val RogCard = RogSurfaceDark
val RogCardSecondary = RogSurfaceSubtle
val RogBorderSubtle = Color.Transparent

// Original Green Accent Tokens (Refined, Clean & Understated)
val RogGoldBright = Color(0xFF1ED760)        // Vibrant emerald green
val RogGoldPrimary = Color(0xFF1DB954)       // Original Spotify Green #1DB954
val RogGoldChampagne = Color(0xFF1ED760)     // Bright accent green
val RogGoldDark = Color(0xFF1AA34A)          // Darker green for press/gradients
val RogGoldDim = Color(0xFF0D2818)           // Subdued dark green surface
val RogGoldGlow = Color(0xFF1DB954)
val RogGoldBorder = Color.Transparent        // Borderless minimal design
val RogGoldBorderBright = Color.Transparent  // Borderless minimal design
val RogGoldMuted = RogGoldDim

// Minimalist Typography & Text
val RogTextPrimary = Color(0xFFF4F4F5)       // Crisp white (Zinc-100)
val RogTextSecondary = Color(0xFFA1A1AA)     // Neutral gray (Zinc-400)
val RogTextMuted = Color(0xFF52525B)         // Subtle gray (Zinc-600)
val RogTextGold = Color(0xFF1DB954)          // Original Green accent text

// Minimal Dark Genre Channels
val GenreCyberGold = Color(0xFF161512)
val GenreTitanium = Color(0xFF151618)
val GenreCrimsonEdge = Color(0xFF181415)
val GenreNeonViolet = Color(0xFF171418)
val GenreAmberCore = Color(0xFF181512)
val GenreStealthCyan = Color(0xFF121718)

val GenrePop = Color(0xFF161514)
val GenreHipHop = Color(0xFF171512)
val GenreRock = Color(0xFF181415)
val GenreIndie = Color(0xFF141614)
val GenreDance = Color(0xFF171512)
val GenreWorkout = Color(0xFF131615)
val GenreChill = Color(0xFF131517)
val GenreCountry = Color(0xFF171513)
val GenreRAndB = Color(0xFF171316)
val GenreJazz = Color(0xFF141519)
val GenreKPop = Color(0xFF131518)
val GenreAnime = Color(0xFF181415)

// Minimal Gradient Brushes
val RogGoldGradient = Brush.horizontalGradient(listOf(RogGoldBright, RogGoldDark))
val RogGoldVerticalGradient = Brush.verticalGradient(listOf(RogGoldBright, RogGoldDark))
val RogDarkDeckGradient = Brush.verticalGradient(listOf(Color(0xFF141418), Color(0xFF09090C)))
val RogCardGloss = Brush.linearGradient(listOf(Color(0xFF16161A), Color(0xFF121215)))
val RogGoldBorderBrush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
val RogHaloGlow = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))

// Backward-Compatibility Aliases
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenPressed = Color(0xFF1AA34A)
val SpotifyBlack = RogBlack
val SpotifyPureBlack = RogObsidian
val SpotifySurface = RogSurfaceDark
val SpotifySurfaceHighlight = RogSurfaceHighlight
val SpotifySurfaceSubtle = RogSurfaceSubtle
val SpotifyDivider = RogDivider

val SpotifyTextPrimary = RogTextPrimary
val SpotifyTextSecondary = RogTextSecondary
val SpotifyTextMuted = RogTextMuted

val LikedSongsGradientStart = Color(0xFF0D3B1E)
val LikedSongsGradientEnd = Color(0xFF0A140F)
val DailyMixGradient1 = Color(0xFF0E2E1B)
val DailyMixGradient2 = Color(0xFF0F0F12)
