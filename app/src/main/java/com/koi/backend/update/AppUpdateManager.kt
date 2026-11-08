package com.koi.backend.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AppUpdateManager(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    overrideCurrentVersion: String? = null
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        const val REPO_OWNER = "Casper-07-R"
        const val REPO_NAME = "music-app-release"
        const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"

        fun parseReleases(json: String): List<GitHubRelease> {
            if (json.isBlank()) return emptyList()
            val releases = mutableListOf<GitHubRelease>()
            try {
                val parsed = JsonParser.parseString(json)
                if (parsed.isJsonArray) {
                    for (elem in parsed.asJsonArray) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        val draft = obj.get("draft")?.asBoolean ?: false
                        if (draft) continue

                        val id = obj.get("id")?.asLong ?: 0L
                        val tagName = obj.get("tag_name")?.asString ?: ""
                        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                        val body = obj.get("body")?.takeIf { !it.isJsonNull }?.asString
                        val publishedAt = obj.get("published_at")?.takeIf { !it.isJsonNull }?.asString
                        val htmlUrl = obj.get("html_url")?.asString ?: ""
                        val prerelease = obj.get("prerelease")?.asBoolean ?: false

                        val assetsList = mutableListOf<GitHubAsset>()
                        val assetsArray = obj.getAsJsonArray("assets")
                        if (assetsArray != null) {
                            for (aElem in assetsArray) {
                                if (!aElem.isJsonObject) continue
                                val aObj = aElem.asJsonObject
                                val aId = aObj.get("id")?.asLong ?: 0L
                                val aName = aObj.get("name")?.asString ?: ""
                                val aSize = aObj.get("size")?.asLong ?: 0L
                                val aContentType = aObj.get("content_type")?.takeIf { !it.isJsonNull }?.asString
                                val aDownloadUrl = aObj.get("browser_download_url")?.asString ?: ""
                                assetsList.add(GitHubAsset(aId, aName, aSize, aContentType, aDownloadUrl))
                            }
                        }

                        releases.add(GitHubRelease(id, tagName, name, body, publishedAt, htmlUrl, draft, prerelease, assetsList))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse GitHub releases JSON", e)
            }
            return releases
        }
    }

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    val currentVersion: String = overrideCurrentVersion ?: resolveCurrentVersionName(appContext)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle(currentVersion))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate() {
        val currentState = _state.value
        if (currentState is UpdateUiState.Checking || currentState is UpdateUiState.Downloading) {
            return
        }

        updateScope.launch {
            _state.value = UpdateUiState.Checking(currentVersion)

            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Koi-App")
                .get()
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.code == 404) {
                        // Repository has no releases yet
                        _state.value = UpdateUiState.UpToDate(
                            currentVersion = currentVersion,
                            latestRelease = null
                        )
                        return@use
                    }

                    if (resp.code == 403) {
                        val remaining = resp.header("X-RateLimit-Remaining")
                        if (remaining == "0") {
                            _state.value = UpdateUiState.Error(
                                currentVersion = currentVersion,
                                message = "GitHub API rate limit exceeded. Please try again later."
                            )
                            return@use
                        }
                    }

                    if (!resp.isSuccessful) {
                        _state.value = UpdateUiState.Error(
                            currentVersion = currentVersion,
                            message = "GitHub returned HTTP ${resp.code}: ${resp.message}"
                        )
                        return@use
                    }

                    val bodyStr = resp.body?.string() ?: ""
                    val releases = parseReleases(bodyStr)

                    if (releases.isEmpty()) {
                        _state.value = UpdateUiState.UpToDate(
                            currentVersion = currentVersion,
                            latestRelease = null
                        )
                        return@use
                    }

                    val latest = releases.first()
                    val apkAsset = latest.assets.firstOrNull { asset ->
                        asset.name.endsWith(".apk", ignoreCase = true) ||
                        asset.contentType == "application/vnd.android.package-archive"
                    }

                    val isNewer = VersionComparator.isNewer(latest.tagName, currentVersion)

                    if (isNewer) {
                        if (apkAsset != null) {
                            _state.value = UpdateUiState.UpdateAvailable(
                                currentVersion = currentVersion,
                                release = latest,
                                apkAsset = apkAsset
                            )
                        } else {
                            _state.value = UpdateUiState.Error(
                                currentVersion = currentVersion,
                                message = "New release ${latest.tagName} found, but no .apk file is attached."
                            )
                        }
                    } else {
                        _state.value = UpdateUiState.UpToDate(
                            currentVersion = currentVersion,
                            latestRelease = latest
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                _state.value = UpdateUiState.Error(
                    currentVersion = currentVersion,
                    message = e.localizedMessage ?: "Failed to connect to GitHub"
                )
            }
        }
    }

    fun downloadUpdate(release: GitHubRelease, apkAsset: GitHubAsset, context: Context = appContext) {
        downloadJob?.cancel()

        downloadJob = updateScope.launch {
            val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
            val cleanTag = release.tagName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val apkFile = File(updatesDir, "Koi-$cleanTag.apk")

            if (apkFile.exists()) {
                apkFile.delete()
            }

            _state.value = UpdateUiState.Downloading(
                currentVersion = currentVersion,
                release = release,
                apkAsset = apkAsset,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = apkAsset.size
            )

            val request = Request.Builder()
                .url(apkAsset.browserDownloadUrl)
                .header("User-Agent", "Koi-App")
                .get()
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _state.value = UpdateUiState.Error(
                        currentVersion = currentVersion,
                        message = "Download failed: HTTP ${response.code}"
                    )
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    _state.value = UpdateUiState.Error(
                        currentVersion = currentVersion,
                        message = "Download failed: Empty server response"
                    )
                    return@launch
                }

                val contentLength = if (body.contentLength() > 0) body.contentLength() else apkAsset.size

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                apkFile.delete()
                                return@launch
                            }

                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 100 || totalRead == contentLength) {
                                lastUpdate = now
                                val progressFraction = if (contentLength > 0) {
                                    (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                }
                                _state.value = UpdateUiState.Downloading(
                                    currentVersion = currentVersion,
                                    release = release,
                                    apkAsset = apkAsset,
                                    progress = progressFraction,
                                    downloadedBytes = totalRead,
                                    totalBytes = contentLength
                                )
                            }
                        }
                        output.flush()
                    }
                }

                _state.value = UpdateUiState.ReadyToInstall(
                    currentVersion = currentVersion,
                    release = release,
                    apkAsset = apkAsset,
                    apkFile = apkFile
                )

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    apkFile.delete()
                    _state.value = UpdateUiState.UpdateAvailable(
                        currentVersion = currentVersion,
                        release = release,
                        apkAsset = apkAsset
                    )
                } else {
                    Log.e(TAG, "Failed to download update APK", e)
                    apkFile.delete()
                    _state.value = UpdateUiState.Error(
                        currentVersion = currentVersion,
                        message = "Download error: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            _state.value = UpdateUiState.UpdateAvailable(
                currentVersion = currentVersion,
                release = current.release,
                apkAsset = current.apkAsset
            )
        } else {
            _state.value = UpdateUiState.Idle(currentVersion)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Update package not found. Please download again.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context,
                    "Please enable 'Allow from this source' for Koi to install the update.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open unknown app sources settings", e)
                }
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent", e)
            Toast.makeText(context, "Failed to launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun parseReleases(json: String): List<GitHubRelease> = Companion.parseReleases(json)

    private fun resolveCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
}
