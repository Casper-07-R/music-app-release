package com.koi.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =========================================================================
// KOI POND THEME TOKENS
// Background & Surfaces: Serene, Deep Aquatic Pond Water
// UI Elements & Accents: Vibrant Koi Fish Colors (Kohaku Orange & Golden Amber)
// =========================================================================

// Deep Aquatic Pond Surfaces (Tranquil, Clean & Pure Organic Depth)
val RogObsidian = Color(0xFF041014)          // Deepest abyss pond black (status/nav bars)
val RogBlack = Color(0xFF07181E)             // Viewport main pond surface
val RogSurfaceDark = Color(0xFF0D252E)       // Deep pond basin (cards, list items, mini-player)
val RogSurfaceMedium = Color(0xFF13323D)     // Elevated items & search bar
val RogSurfaceHighlight = Color(0xFF1B4250)  // Active selection & pressed state
val RogSurfaceSubtle = Color(0xFF091F26)     // Recessed wells & subtle card depths
val RogDivider = Color(0x1F48CAE4)           // Aquatic water shimmer 12% divider

// Surface aliases
val RogCard = RogSurfaceDark
val RogCardSecondary = RogSurfaceSubtle
val RogBorderSubtle = Color.Transparent

// Vibrant Koi Fish Accent Tokens (Matching the Kohaku & Golden Showa Koi)
val RogGoldBright = Color(0xFFFF7438)        // Vibrant radiant koi orange
val RogGoldPrimary = Color(0xFFFF6B35)       // Signature Kohaku Koi Orange (FAB, active toggles)
val RogGoldChampagne = Color(0xFFFFB703)     // Shimmering Golden Koi amber
val RogGoldDark = Color(0xFFE65100)          // Deep vermilion orange (pressed / gradient)
val RogGoldDim = Color(0xFF2E170E)           // Subdued warm koi surface glow
val RogGoldGlow = Color(0xFFFF6B35)
val RogGoldBorder = Color.Transparent        // Borderless minimal design
val RogGoldBorderBright = Color.Transparent  // Borderless minimal design
val RogGoldMuted = RogGoldDim

// Aquatic & Koi Typography
val RogTextPrimary = Color(0xFFF0FDFD)       // Crystal clean water white
val RogTextSecondary = Color(0xFF88A6B0)     // Soft misty pond slate
val RogTextMuted = Color(0xFF4C6B75)         // Submerged pond depth gray-teal
val RogTextGold = Color(0xFFFF7A45)          // Luminous Koi Orange accent text

// Serene Pond & Japanese Garden Inspired Genre Channels
val GenreCyberGold = Color(0xFF13323D)
val GenreTitanium = Color(0xFF102A33)
val GenreCrimsonEdge = Color(0xFF381A16)
val GenreNeonViolet = Color(0xFF2A1C36)
val GenreAmberCore = Color(0xFF382914)
val GenreStealthCyan = Color(0xFF0F303B)

val GenrePop = Color(0xFF183B37)             // Lotus Pad Teal
val GenreHipHop = Color(0xFF3B2014)          // Kohaku Ember
val GenreRock = Color(0xFF3E1815)            // Hi-Utsuri Crimson
val GenreIndie = Color(0xFF12353E)           // Pond Current
val GenreDance = Color(0xFF3B3012)           // Yamabuki Gold
val GenreWorkout = Color(0xFF113842)         // Waterfall Blue
val GenreChill = Color(0xFF0D2932)           // Still Water
val GenreCountry = Color(0xFF332614)         // Wooden Bridge
val GenreRAndB = Color(0xFF2B1936)           // Twilight Lily
val GenreJazz = Color(0xFF102E38)            // Midnight Pool
val GenreKPop = Color(0xFF361A2B)            // Cherry Blossom
val GenreAnime = Color(0xFF381B18)           // Scarlet Carp

// Aquatic & Koi Gradient Brushes
val RogGoldGradient = Brush.horizontalGradient(listOf(RogGoldBright, RogGoldDark))
val RogGoldVerticalGradient = Brush.verticalGradient(listOf(RogGoldBright, RogGoldDark))
val RogDarkDeckGradient = Brush.verticalGradient(listOf(Color(0xFF0E2831), Color(0xFF041014)))
val RogCardGloss = Brush.linearGradient(listOf(Color(0xFF143642), Color(0xFF0A2027)))
val RogGoldBorderBrush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
val RogHaloGlow = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))

// Backward-Compatibility Aliases
val SpotifyGreen = RogGoldPrimary
val SpotifyGreenPressed = RogGoldDark
val SpotifyBlack = RogBlack
val SpotifyPureBlack = RogObsidian
val SpotifySurface = RogSurfaceDark
val SpotifySurfaceHighlight = RogSurfaceHighlight
val SpotifySurfaceSubtle = RogSurfaceSubtle
val SpotifyDivider = RogDivider

val SpotifyTextPrimary = RogTextPrimary
val SpotifyTextSecondary = RogTextSecondary
val SpotifyTextMuted = RogTextMuted

val LikedSongsGradientStart = Color(0xFFFF6B35) // Radiant Koi Orange
val LikedSongsGradientEnd = Color(0xFF07181E)   // Deep pond water
val DailyMixGradient1 = Color(0xFF0077B6)       // Shimmering Blue Koi
val DailyMixGradient2 = Color(0xFF07181E)       // Deep pond water
