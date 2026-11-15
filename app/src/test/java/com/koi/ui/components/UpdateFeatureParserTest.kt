package com.koi.ui.components

import com.koi.backend.update.GitHubAsset
import com.koi.backend.update.GitHubRelease
import org.junit.Assert.*
import org.junit.Test

class UpdateFeatureParserTest {

    @Test
    fun testParse_withStructuredChangelog_producesCorrectPages() {
        val release = GitHubRelease(
            id = 101,
            tagName = "v1.2.0",
            name = "Koi v1.2.0 - 8D Audio & Bug Fixes",
            body = """
                ### Added Features
                * 8D Surround sound
                * Sleep timer
                
                ### Bug Fixes
                * Fixed crash on background resume
                * Fixed audio focus on incoming call
            """.trimIndent(),
            publishedAt = "2026-11-09T08:00:00Z",
            htmlUrl = "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.2.0",
            assets = listOf(GitHubAsset(1, "Koi-v1.2.0.apk", 15_000_000L, "application/vnd.android.package-archive", "https://url"))
        )

        val pages = UpdateFeatureParser.parse(release)
        assertEquals(4, pages.size)

        // Page 1: 8D Audio
        assertEquals(UpdateFeatureType.AUDIO_8D, pages[0].type)
        assertTrue(pages[0].title.contains("8D", ignoreCase = true))

        // Page 2: Sleep Timer
        assertEquals(UpdateFeatureType.SLEEP_TIMER, pages[1].type)
        assertTrue(pages[1].title.contains("Sleep Timer", ignoreCase = true))

        // Page 3: Bug fix
        assertEquals(UpdateFeatureType.BUG_FIX, pages[2].type)

        // Page 4: Audio focus fix under Bug Fixes
        assertEquals(UpdateFeatureType.BUG_FIX, pages[3].type)
    }

    @Test
    fun testParse_withEmptyBody_fallsBackToShowcaseFeatures() {
        val release = GitHubRelease(
            id = 102,
            tagName = "v1.2.0",
            name = "Koi v1.2.0",
            body = null,
            publishedAt = "2026-11-09T08:00:00Z",
            htmlUrl = "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.2.0"
        )

        val pages = UpdateFeatureParser.parse(release)
        assertTrue(pages.size >= 2)
        assertEquals(4, pages.size)
        assertEquals(UpdateFeatureType.AUDIO_8D, pages[0].type)
        assertEquals(UpdateFeatureType.SLEEP_TIMER, pages[1].type)
    }

    @Test
    fun testParse_withSingleItem_supplementsToMultiPage() {
        val release = GitHubRelease(
            id = 103,
            tagName = "v1.2.0",
            name = "Koi v1.2.0",
            body = "* Added sleep timer support with fade out",
            publishedAt = "2026-11-09T08:00:00Z",
            htmlUrl = "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.2.0"
        )

        val pages = UpdateFeatureParser.parse(release)
        assertTrue(pages.size >= 2)
        assertEquals(UpdateFeatureType.SLEEP_TIMER, pages[0].type)
    }

    @Test
    fun testParse_withColonSeparatedTitleAndDetail() {
        val release = GitHubRelease(
            id = 104,
            tagName = "v1.3.0",
            name = "Koi v1.3.0",
            body = """
                * **Turbo Buffer**: Instant track prefetching with zero audio stutter
                * **Playlist Covers**: Choose custom art from your device photo library
                * **Stability**: Fixed memory leak during continuous playback
            """.trimIndent(),
            publishedAt = "2026-11-14T08:00:00Z",
            htmlUrl = "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.3.0"
        )

        val pages = UpdateFeatureParser.parse(release)
        assertEquals(3, pages.size)

        assertEquals(UpdateFeatureType.PERFORMANCE, pages[0].type)
        assertTrue(pages[0].title.contains("Turbo Buffer"))
        assertEquals("Instant track prefetching with zero audio stutter", pages[0].description)

        assertEquals(UpdateFeatureType.PLAYLIST_CUSTOMIZATION, pages[1].type)
        assertTrue(pages[1].title.contains("Playlist Covers"))

        assertEquals(UpdateFeatureType.BUG_FIX, pages[2].type)
    }

    @Test
    fun testParse_withManyItems_capsToMaxFive() {
        val release = GitHubRelease(
            id = 105,
            tagName = "v1.4.0",
            name = "Koi v1.4.0",
            body = """
                * Feature 1: 8D audio expansion
                * Feature 2: Sleep timer improvements
                * Feature 3: Speed optimization
                * Feature 4: Playlist reordering
                * Feature 5: Fixed equalizer reset bug
                * Feature 6: Additional genre channels
                * Feature 7: Battery optimization helper
            """.trimIndent(),
            publishedAt = "2026-11-14T08:00:00Z",
            htmlUrl = "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.4.0"
        )

        val pages = UpdateFeatureParser.parse(release)
        assertEquals(5, pages.size)
    }
}
