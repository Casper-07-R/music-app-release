package com.koi.backend.profile

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object UserProfileManager {

    private const val TAG = "UserProfileManager"
    const val PREFS_NAME = "koi_user_profile"
    const val KEY_USERNAME = "profile_username"
    const val KEY_HAS_PROMPTED = "has_prompted_initial_username"
    const val DEFAULT_USERNAME = "Casper"

    /**
     * Determines whether the app should prompt for username on first open.
     * This returns true ONLY on a fresh initial install on its very first launch,
     * and strictly returns false during any app update or subsequent launches.
     */
    fun shouldPromptInitialUsername(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_PROMPTED, false)) {
            return false
        }

        // If this launch is an app update, do NOT prompt; mark as completed so it never prompts.
        if (isAppUpdate(context)) {
            Log.d(TAG, "App launch detected as update. Skipping initial username prompt.")
            markPromptCompleted(context)
            return false
        }

        return true
    }

    /**
     * Checks if this running app instance is an update over a previous installation.
     */
    fun isAppUpdate(context: Context): Boolean {
        var firstInstallTime = 0L
        var lastUpdateTime = 0L

        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            firstInstallTime = packageInfo.firstInstallTime
            lastUpdateTime = packageInfo.lastUpdateTime
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect package times", e)
        }

        val hasPriorUserData = checkHasPriorUserData(context)
        return isUpdate(firstInstallTime, lastUpdateTime, hasPriorUserData)
    }

    /**
     * Pure testable logic to detect if an installation is an update vs clean install.
     */
    fun isUpdate(
        firstInstallTime: Long,
        lastUpdateTime: Long,
        hasPriorUserData: Boolean = false
    ): Boolean {
        if (hasPriorUserData) {
            return true
        }

        // In Android PMS:
        // Fresh install: firstInstallTime == lastUpdateTime
        // Update: lastUpdateTime is updated to current time, while firstInstallTime remains the original timestamp.
        if (firstInstallTime > 0L && lastUpdateTime > 0L) {
            // Delta greater than 1 second confirms an update occurred
            if (lastUpdateTime - firstInstallTime > 1000L) {
                return true
            }
        }

        return false
    }

    /**
     * Pure testable logic for whether to prompt username.
     */
    fun shouldPromptForUsername(
        hasPromptedBefore: Boolean,
        firstInstallTime: Long,
        lastUpdateTime: Long,
        hasPriorUserData: Boolean = false
    ): Boolean {
        if (hasPromptedBefore) return false
        if (isUpdate(firstInstallTime, lastUpdateTime, hasPriorUserData)) return false
        return true
    }

    private fun checkHasPriorUserData(context: Context): Boolean {
        return try {
            // Check if existing preference files from earlier versions exist
            val dspPrefs = context.getSharedPreferences("koi_dsp_prefs", Context.MODE_PRIVATE)
            if (dspPrefs.all.isNotEmpty()) return true

            val playbackPrefs = context.getSharedPreferences("koi_playback_state", Context.MODE_PRIVATE)
            if (playbackPrefs.all.isNotEmpty()) return true

            val notifPrefs = context.getSharedPreferences("koi_notif_prefs", Context.MODE_PRIVATE)
            if (notifPrefs.all.isNotEmpty()) return true

            false
        } catch (e: Exception) {
            false
        }
    }

    fun getUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
    }

    fun saveUsername(context: Context, username: String) {
        val cleanName = username.trim()
        val finalName = if (cleanName.isNotBlank()) cleanName else DEFAULT_USERNAME
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USERNAME, finalName)
            .putBoolean(KEY_HAS_PROMPTED, true)
            .apply()
    }

    fun markPromptCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HAS_PROMPTED, true)
            .apply()
    }
}
