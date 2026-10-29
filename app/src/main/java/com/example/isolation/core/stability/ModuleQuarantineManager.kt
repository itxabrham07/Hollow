package com.example.isolation.core.stability

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.isolation.core.security.HollowSecureClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Module Quarantine Manager (`ModuleQuarantineManager`).
 *
 * Enforces stability guardrails:
 * - Tracks runtime failures (uncaught exceptions in Composable screens, onMount, onUnmount).
 * - After 3 consecutive failures within 24 hours, the module is automatically quarantined:
 *   1. Auto-disabled from mounting so Core APK never crashes.
 *   2. Displays a quarantined failure state on the Dashboard with Retry & Remove options.
 *   3. Dispatches a security / stability incident to the Hollow server.
 */
object ModuleQuarantineManager {

    private const val TAG = "ModuleQuarantineManager"
    private const val PREFS_NAME = "hollow_quarantine_prefs"
    private const val FAILURE_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val MAX_CONSECUTIVE_FAILURES = 3

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    data class QuarantineRecord(
        val moduleId: String,
        val failureCount: Int,
        val lastFailureMessage: String,
        val lastFailureTimestampMs: Long,
        val isQuarantined: Boolean
    )

    /**
     * Records a failure for a dynamic module. Returns true if the module just crossed the threshold into quarantine.
     */
    fun recordModuleFailure(
        context: Context,
        moduleId: String,
        throwable: Throwable,
        serverBaseUrl: String = ""
    ): Boolean {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val errorMsg = throwable.message ?: throwable.javaClass.simpleName

        val rawHistory = prefs.getString("history_$moduleId", "[]") ?: "[]"
        val historyArr = try { JSONArray(rawHistory) } catch (_: Throwable) { JSONArray() }

        // Filter failures within last 24h
        val recentTimestamps = mutableListOf<Long>()
        for (i in 0 until historyArr.length()) {
            val ts = historyArr.optLong(i, 0L)
            if (now - ts < FAILURE_WINDOW_MS) {
                recentTimestamps.add(ts)
            }
        }
        recentTimestamps.add(now)

        val updatedArr = JSONArray()
        recentTimestamps.forEach { updatedArr.put(it) }

        val failureCount = recentTimestamps.size
        val shouldQuarantine = failureCount >= MAX_CONSECUTIVE_FAILURES

        Log.e(TAG, "Module $moduleId failure recorded (#$failureCount/3 in 24h): $errorMsg")

        prefs.edit()
            .putString("history_$moduleId", updatedArr.toString())
            .putString("last_error_$moduleId", errorMsg)
            .putLong("last_error_ts_$moduleId", now)
            .putBoolean("quarantined_$moduleId", shouldQuarantine)
            .apply()

        if (shouldQuarantine) {
            Log.e(TAG, "QUARANTINING MODULE '$moduleId' due to $failureCount failures within 24h.")
            HollowSecureClient.reportSecurityIncidentAsync(
                context = context,
                serverBaseUrl = serverBaseUrl,
                moduleId = moduleId,
                triggerVector = "MODULE_QUARANTINE_CONSECUTIVE_FAILURES",
                details = "Module failed $failureCount times within 24h. Last error: $errorMsg"
            )
        }

        return shouldQuarantine
    }

    /**
     * Checks if a module is currently quarantined.
     */
    fun isQuarantined(context: Context, moduleId: String): Boolean {
        return getPrefs(context).getBoolean("quarantined_$moduleId", false)
    }

    /**
     * Retrieves quarantine status details for UI rendering.
     */
    fun getQuarantineRecord(context: Context, moduleId: String): QuarantineRecord {
        val prefs = getPrefs(context)
        val isQuarantined = prefs.getBoolean("quarantined_$moduleId", false)
        val lastError = prefs.getString("last_error_$moduleId", "") ?: ""
        val lastTs = prefs.getLong("last_error_ts_$moduleId", 0L)

        val rawHistory = prefs.getString("history_$moduleId", "[]") ?: "[]"
        val count = try { JSONArray(rawHistory).length() } catch (_: Throwable) { 0 }

        return QuarantineRecord(
            moduleId = moduleId,
            failureCount = count,
            lastFailureMessage = lastError,
            lastFailureTimestampMs = lastTs,
            isQuarantined = isQuarantined
        )
    }

    /**
     * Resets failures for a module (e.g. user tapped "Retry").
     */
    fun liftQuarantine(context: Context, moduleId: String) {
        Log.i(TAG, "Lifting quarantine for module: $moduleId")
        getPrefs(context).edit()
            .remove("history_$moduleId")
            .remove("last_error_$moduleId")
            .remove("last_error_ts_$moduleId")
            .putBoolean("quarantined_$moduleId", false)
            .apply()
    }

    /**
     * Clears all quarantines.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
