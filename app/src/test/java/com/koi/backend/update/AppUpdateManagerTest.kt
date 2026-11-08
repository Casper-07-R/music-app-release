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
}
