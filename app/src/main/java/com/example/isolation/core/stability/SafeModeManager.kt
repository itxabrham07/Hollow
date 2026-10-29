package com.example.isolation.core.stability

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Safe Mode Manager (`SafeModeManager`) for Hollow Core Engine.
 *
 * Implements crash loop protection:
 * If the app crashes twice during startup within the detection window,
 * Safe Mode is automatically engaged on the subsequent launch.
 *
 * In Safe Mode:
 * - Zero dynamic DEX modules are mounted.
 * - Core APK runs strictly in minimal shell mode.
 * - Recovery UI affordances (Reset Safe Mode, Purge Modules, Export Diagnostics) are displayed.
 */
object SafeModeManager {

    private const val TAG = "SafeModeManager"
    private const val PREFS_NAME = "hollow_safe_mode_prefs"
    private const val KEY_STARTUP_CRASH_COUNT = "startup_crash_count"
    private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"
    private const val KEY_SAFE_MODE_TIMESTAMP = "safe_mode_timestamp"

    private const val MAX_CRASH_THRESHOLD = 2
    private const val STABILITY_WINDOW_MS = 6000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Called in [com.example.IsoVaultApp.onCreate] before any modules or hooks are initialized.
     * Increments crash counter and schedules a timer to clear it if startup stabilizes.
     */
    fun onAppStartupBegin(context: Context) {
        val prefs = getPrefs(context)
        val currentCrashes = prefs.getInt(KEY_STARTUP_CRASH_COUNT, 0) + 1
        Log.w(TAG, "Startup sequence initiated. Consecutive crash count: $currentCrashes")

        val editor = prefs.edit().putInt(KEY_STARTUP_CRASH_COUNT, currentCrashes)

        if (currentCrashes >= MAX_CRASH_THRESHOLD) {
            Log.e(TAG, "CRITICAL: Detected $currentCrashes startup crashes. ENGAGING SAFE MODE.")
            editor.putBoolean(KEY_SAFE_MODE_ENABLED, true)
            editor.putLong(KEY_SAFE_MODE_TIMESTAMP, System.currentTimeMillis())
        }
        editor.apply()

        // If the process survives the stability window without an uncaught exception, reset counter
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isSafeModeEngaged(context)) {
                Log.i(TAG, "Startup stabilization successful. Resetting crash counter.")
                resetStartupCrashCounter(context)
            }
        }, STABILITY_WINDOW_MS)
    }

    /**
     * Checks if Safe Mode is currently active.
     */
    fun isSafeModeEngaged(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SAFE_MODE_ENABLED, false)
    }

    /**
     * Resets Safe Mode back to normal operation.
     */
    fun exitSafeMode(context: Context) {
        Log.i(TAG, "Manual exit from Safe Mode requested.")
        getPrefs(context).edit()
            .putBoolean(KEY_SAFE_MODE_ENABLED, false)
            .putInt(KEY_STARTUP_CRASH_COUNT, 0)
            .putLong(KEY_SAFE_MODE_TIMESTAMP, 0L)
            .apply()
    }

    fun resetStartupCrashCounter(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_STARTUP_CRASH_COUNT, 0)
            .apply()
    }
}
