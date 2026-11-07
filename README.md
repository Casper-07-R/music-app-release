# Tsuki - High-Fidelity Music UI with Jetpack Compose

**Tsuki** is a modern, production-grade Android music player interface built entirely from scratch with **Kotlin Jetpack Compose** and Material 3, incorporating reverse-engineered design patterns, typography, and iconography extracted from modern streaming architectures using the **REA MCP** suite.

---

## Project Architecture

```
Tsuki/                                        # Root Project Directory
├── app/                                        # Main Android Application Module
│   ├── build.gradle.kts                        # Gradle config (Compose BOM, M3, compileSdk 35)
│   └── src/main/
│       ├── AndroidManifest.xml                 # App manifest with launcher activity
│       ├── java/com/tsuki/ui/
│       │   ├── MainActivity.kt                 # Entry point & root view orchestration
│       │   ├── components/                     # Reusable UI components
│       │   │   ├── FilterChipRow.kt            # Pill filter chips ("All", "Music", "Podcasts")
│       │   │   ├── GenreBrowseCard.kt          # Angled 25° colorful search genre tiles
│       │   │   ├── HorizontalMediaShelf.kt     # Horizontal scrollable album/mix shelves
│       │   │   ├── NowPlayingFullScreenSheet.kt# Full-screen player with lyrics & scrubber
│       │   │   ├── NowPlayingMiniBar.kt        # Floating bottom player bar with 2dp progress
│       │   │   ├── QuickAccessGrid.kt          # 2x3 compact grid (Liked Songs, Daily Mix)
│       │   │   ├── SpotifyBottomNavBar.kt      # Persistent 4-tab bottom navigation (TsukiBottomNavBar)
│       │   │   └── TrackListItem.kt            # Playlist track row with metadata & like
│       │   ├── model/
│       │   │   └── Models.kt                   # Track, Album, Playlist, Category models
│       │   ├── screens/
│       │   │   ├── HomeScreen.kt               # Home feed with greeting & shelves
│       │   │   ├── LibraryScreen.kt            # Your Library with Liked Songs & playlists
│       │   │   ├── PlaylistDetailScreen.kt     # Playlist hero header, track list & FAB
│       │   │   └── SearchScreen.kt             # Search bar & 2-column genre grid
│       │   ├── theme/
│       │   │   ├── Color.kt                    # Encore design tokens (#1ED760, #121212)
│       │   │   ├── Theme.kt                    # TsukiTheme Material3 dark theme
│       │   │   └── Type.kt                     # Circular typeface typography definitions
│       │   └── viewmodel/
│       │       └── PlaybackViewModel.kt        # Reactive playback state management
│       └── res/
│           ├── drawable/                       # Vector drawables (icons, glyphs)
│           │   ├── ic_spotify_logo.xml
│           │   ├── ic_home.xml
│           │   ├── ic_search.xml
│           │   ├── ic_library.xml
│           │   ├── ic_play.xml
│           │   ├── ic_pause.xml
│           │   ├── ic_skip_next.xml
│           │   ├── ic_skip_back.xml
│           │   ├── ic_shuffle.xml
│           │   ├── ic_repeat.xml
│           │   ├── ic_heart.xml
│           │   ├── ic_heart_filled.xml
│           │   ├── ic_connect.xml
│           │   └── ...
│           ├── font/                           # Authentic Circular typefaces
│           │   ├── circular_sp_bold.otf
│           │   ├── circular_sp_book.otf
│           │   ├── circular_sp_title_black.otf
│           │   └── circular_sp_title_bold.otf
│           └── values/
│               ├── colors.xml                  # Color definitions
│               └── strings.xml                 # App name: Tsuki
│
├── stripped-ui/                                # Curated stripped reference UI assets
│   ├── UI_SPECIFICATION.md                     # Deep design spec & token documentation
│   ├── tokens/                                 # Extracted colors, dimens & styles XMLs
│   ├── fonts/                                  # Full Circular OTF/TTF font collection
│   ├── icons/                                  # 1,130+ Encore vector drawables
│   ├── layouts/                                # 200+ decompiled layout XML references
│   └── manifest/                               # Original AndroidManifest.xml
│
├── build.gradle.kts                            # Root Gradle build configuration
├── settings.gradle.kts                         # rootProject.name = "Tsuki"
└── gradle.properties
```

---

## Design Tokens

- **Brand Accent**: `#1ED760` (`SpotifyGreen`)
- **Background**: `#121212` (`SpotifyBlack`)
- **Bottom Scrim / Navigation Bar**: `#000000` (`SpotifyPureBlack`)
- **Surface / Floating Cards**: `#282828` (`SpotifySurface`)
- **Surface Highlight**: `#3E3E3E`
- **Primary Text**: `#FFFFFF` (`SpotifyTextPrimary`)
- **Secondary Text**: `#B3B3B3` (`SpotifyTextSecondary`)
- **Muted Text**: `#7F7F7F` (`SpotifyTextMuted`)

---

## Features

1. **Floating Mini-Player ("Now Playing Bar")**:
   - Floats above the bottom navigation with rounded corners (`8dp`).
   - Track thumbnail, title, artist, connect icon, like button, and play/pause button.
   - Continuous `2dp` progress indicator line.
   - Tap anywhere to expand into the full-screen player sheet.

2. **Full-Screen Interactive Player**:
   - Dynamic gradient background matching the current track artwork.
   - Chevron down button to collapse.
   - 300dp album cover with drop shadow.
   - Timeline slider with elapsed and remaining timestamps.
   - Playback controls: Shuffle, Previous, 64dp Circular Play/Pause, Next, Repeat.
   - Device routing bar ("Casper's Audio Device"), Share, and Queue buttons.
   - Interactive live lyrics drawer preview.

3. **Home Screen**:
   - User profile avatar chip, contextual greeting ("Good morning", "Good afternoon", "Good evening").
   - Filter chips ("All", "Music", "Podcasts").
   - Quick-Access `2×3` Grid (Liked Songs, Daily Mix 1, Today's Top Hits, Starboy, Discover Weekly, Deep Focus).
   - Horizontal shelves for "Recently played", "Made for Casper", and "Your top mixes".

4. **Search Screen**:
   - Search input box with placeholder and icons.
   - 2-column grid of angled `25°` colorful genre tiles (Pop, Hip-Hop, Rock, Indie, Dance, Chill, etc.).

5. **Your Library Screen**:
   - Filter pills ("Playlists", "Podcasts", "Albums", "Artists").
   - Recents sort bar.
   - Pinned "Liked Songs" item with iconic purple-to-lavender gradient and heart.
   - List of user and followed playlists.

6. **Playlist Detail Screen**:
   - Hero header with artwork, title, creator metadata, and song count.
   - Iconic Spotify Green circular Play FAB (`#1ED760`).
   - Interactive track list with track numbers, titles, artists, and instant tap-to-play.

---

## Build & Run

Open `/home/casper/Projects/Tsuki` in **Android Studio**, or build via Gradle:

```bash
cd ~/Projects/Tsuki
gradle assembleDebug
```
