package com.koi.backend.update

import org.junit.Assert.*
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testVersionComparator_isNewer() {
        // Newer versions
        assertTrue(VersionComparator.isNewer("1.1", "1.0"))
        assertTrue(VersionComparator.isNewer("v1.1", "1.0"))
        assertTrue(VersionComparator.isNewer("V1.0.1", "1.0"))
        assertTrue(VersionComparator.isNewer("2.0.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("v1.0.0.1", "1.0.0.0"))
        assertTrue(VersionComparator.isNewer("v1.2-alpha", "1.1"))

        // Equal versions
        assertFalse(VersionComparator.isNewer("1.0", "1.0"))
        assertFalse(VersionComparator.isNewer("v1.0", "1.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))

        // Older versions
        assertFalse(VersionComparator.isNewer("0.9", "1.0"))
        assertFalse(VersionComparator.isNewer("v0.9.9", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun testParseReleases_validJsonWithApk() {
        val sampleJson = """
            [
              {
                "id": 123456,
                "tag_name": "v1.1.0",
                "name": "Koi v1.1.0 - Bug Fixes & Improvements",
                "body": "### Changes\n* Added sleep timer\n* In-app updater support",
                "published_at": "2026-11-07T05:00:00Z",
                "html_url": "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.1.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "id": 987654,
                    "name": "koi-v1.1.0.apk",
                    "size": 15728640,
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://github.com/Casper-07-R/music-app-release/releases/download/v1.1.0/koi-v1.1.0.apk"
                  },
                  {
                    "id": 987655,
                    "name": "checksums.txt",
                    "size": 128,
                    "content_type": "text/plain",
                    "browser_download_url": "https://github.com/Casper-07-R/music-app-release/releases/download/v1.1.0/checksums.txt"
                  }
                ]
              }
            ]
        """.trimIndent()

        val releases = AppUpdateManager.parseReleases(sampleJson)
        assertEquals(1, releases.size)

        val release = releases[0]
        assertEquals("v1.1.0", release.tagName)
        assertEquals("Koi v1.1.0 - Bug Fixes & Improvements", release.name)
        assertEquals(2, release.assets.size)

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
        assertNotNull(apkAsset)
        assertEquals("koi-v1.1.0.apk", apkAsset?.name)
        assertEquals(15728640L, apkAsset?.size)
        assertEquals(
            "https://github.com/Casper-07-R/music-app-release/releases/download/v1.1.0/koi-v1.1.0.apk",
            apkAsset?.browserDownloadUrl
        )
    }

    @Test
    fun testParseReleases_emptyJson() {
        val releases = AppUpdateManager.parseReleases("[]")
        assertTrue(releases.isEmpty())
    }

    @Test
    fun testParseReleases_draftExcluded() {
        val draftJson = """
            [
              {
                "id": 1,
                "tag_name": "v2.0.0-draft",
                "name": "Unreleased Draft",
                "draft": true,
                "assets": []
              }
            ]
        """.trimIndent()

        val releases = AppUpdateManager.parseReleases(draftJson)
        assertTrue(releases.isEmpty())
    }

    @Test
    fun testReleaseBodyContainsFeaturesAndBugFixes() {
        val json = """
            [
              {
                "id": 200,
                "tag_name": "v1.2.0",
                "name": "Koi v1.2.0 - 8D Audio & Bug Fixes",
                "body": "### Added Features\n* 8D Surround sound\n* Sleep timer\n\n### Bug Fixes\n* Fixed crash on background resume\n* Fixed audio focus on incoming call",
                "published_at": "2026-11-09T08:00:00Z",
                "html_url": "https://github.com/Casper-07-R/music-app-release/releases/tag/v1.2.0",
                "assets": [
                  {
                    "id": 999,
                    "name": "Koi-v1.2.0.apk",
                    "size": 25000000,
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://github.com/Casper-07-R/music-app-release/releases/download/v1.2.0/Koi-v1.2.0.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val releases = AppUpdateManager.parseReleases(json)
        assertEquals(1, releases.size)
        val release = releases[0]
        assertNotNull(release.body)
        assertTrue(release.body!!.contains("Added Features"))
        assertTrue(release.body!!.contains("Bug Fixes"))
        assertTrue(VersionComparator.isNewer(release.tagName, "1.0"))
    }

    @Test
    fun testUpdateAvailablePopupCondition_onlyWhenNewUpdateAvailable() {
        val release = GitHubRelease(
            id = 10,
            tagName = "v1.2.0",
            name = "Koi 1.2",
            body = "Bug fixes and features",
            publishedAt = "2026-11-09T00:00:00Z",
            htmlUrl = "https://example.com",
            assets = listOf(
                GitHubAsset(1, "koi.apk", 1000L, "application/vnd.android.package-archive", "https://example.com/koi.apk")
            )
        )
        val apk = release.assets.first()

        val idleState = UpdateUiState.Idle("1.0")
        val checkingState = UpdateUiState.Checking("1.0")
        val upToDateState = UpdateUiState.UpToDate("1.0", release)
        val errorState = UpdateUiState.Error("1.0", "Network failure")
        val availableState = UpdateUiState.UpdateAvailable("1.0", release, apk)

        fun shouldShowPopup(state: UpdateUiState, isDismissed: Boolean): Boolean {
            val isUpdateActive = state is UpdateUiState.UpdateAvailable ||
                    state is UpdateUiState.Downloading ||
                    state is UpdateUiState.ReadyToInstall
            return isUpdateActive && !isDismissed
        }

        // When state is Idle, Checking, UpToDate, or Error -> DO NOT show popup
        assertFalse(shouldShowPopup(idleState, isDismissed = false))
        assertFalse(shouldShowPopup(checkingState, isDismissed = false))
        assertFalse(shouldShowPopup(upToDateState, isDismissed = false))
        assertFalse(shouldShowPopup(errorState, isDismissed = false))

        // When state is UpdateAvailable -> SHOW popup
        assertTrue(shouldShowPopup(availableState, isDismissed = false))

        // If user tapped "Later" -> DO NOT show popup even if UpdateAvailable
        assertFalse(shouldShowPopup(availableState, isDismissed = true))
    }
}
