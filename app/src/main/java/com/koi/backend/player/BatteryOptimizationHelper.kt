package com.koi.backend.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimization"

    /**
     * Checks if the app currently has unrestricted background execution
     * (i.e. is ignoring battery optimizations).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check battery optimization status", e)
            false
        }
    }

    /**
     * Prompts the user with the official system dialog to grant unrestricted background access
     * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
     *
     * Falls back to the Battery Optimization list or App Info details screen
     * if the direct prompt is not supported by the OEM firmware.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Direct request not supported, falling back to optimization settings", e)
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * Opens system battery optimization settings or App Info details so the user
     * can manually select "Unrestricted" on OEMs like Samsung, Xiaomi, Infinix, Oppo, etc.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, opening App Info", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens the app's system details (App Info) screen where battery usage can be configured.
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open App Info settings", e)
        }
    }
}
