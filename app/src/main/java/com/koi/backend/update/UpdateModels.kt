package com.koi.backend.update

import java.io.File

data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val name: String?,
    val body: String?,
    val publishedAt: String?,
    val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String?,
    val browserDownloadUrl: String
)

sealed interface UpdateUiState {
    val currentVersion: String

    data class Idle(
        override val currentVersion: String
    ) : UpdateUiState

    data class Checking(
        override val currentVersion: String
    ) : UpdateUiState

    data class UpToDate(
        override val currentVersion: String,
        val latestRelease: GitHubRelease? = null,
        val checkedTimestamp: Long = System.currentTimeMillis()
    ) : UpdateUiState

    data class UpdateAvailable(
        override val currentVersion: String,
        val release: GitHubRelease,
        val apkAsset: GitHubAsset
    ) : UpdateUiState

    data class Downloading(
        override val currentVersion: String,
        val release: GitHubRelease,
        val apkAsset: GitHubAsset,
        val progress: Float, // 0.0 to 1.0 (-1f if unknown)
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState

    data class ReadyToInstall(
        override val currentVersion: String,
        val release: GitHubRelease,
        val apkAsset: GitHubAsset,
        val apkFile: File
    ) : UpdateUiState

    data class Error(
        override val currentVersion: String,
        val message: String,
        val canRetry: Boolean = true
    ) : UpdateUiState
}
