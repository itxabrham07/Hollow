package com.example.isolation.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.IsoVaultApp

/**
 * Isolation rule storage and path helpers.
 *
 * Rules are published to hooked apps through two channels that only the manager can write:
 * the [com.example.isolation.provider.IsoVaultConfigProvider] (live, caller-authenticated) and a
 * world-readable SharedPreferences file read via XSharedPreferences. Rules are deliberately NOT
 * mirrored to shared storage: a hooked app can write there and could switch its own isolation off.
 */
object IsolationRuleManager {

    const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    const val PREFS_NAME = "isovault_rules"

    data class AppRule(
        val packageName: String,
        val isIsolated: Boolean,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>
    )

    /**
     * Checks if Vector / LSPosed has hooked Context.getSharedPreferences(MODE_WORLD_READABLE).
     * When a module with xposedsharedprefs="true" is enabled in Vector/LSPosed,
     * MODE_WORLD_READABLE (1) succeeds without throwing SecurityException.
     */
    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    fun isXSharedPrefsHooked(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    fun getReadableSharedPrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun updateRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean = true,
        whitelistedFolders: Set<String> = emptySet()
    ) {
        val cleanWhitelist = whitelistedFolders.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        publish(
            pkg = packageName,
            isolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = cleanWhitelist.joinToString(",")
        )
    }

    /** Marks [packageName] as explicitly not isolated, keeping its other settings. */
    fun disableRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelistedFolders: Set<String>
    ) {
        publish(
            pkg = packageName,
            isolated = false,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = whitelistedFolders.joinToString(",")
        )
    }

    private fun publish(
        pkg: String,
        isolated: Boolean,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelist: String
    ) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit()
                .putBoolean("${pkg}_configured", true)
                .putBoolean("${pkg}_isolated", isolated)
                .putBoolean("${pkg}_explicitly_disabled", !isolated)
                .putString("${pkg}_mode", mode.name)
                .putBoolean("${pkg}_pollution", blockRootPollution)
                .putString("${pkg}_whitelist", whitelist)
                .commit() // Synchronous so XSharedPreferences readers see the change immediately
        } catch (_: Throwable) {
        }
    }

    fun isRootPollution(path: String): Boolean {
        val patterns = listOf("/.tencent", "/.baidu", "/.pdd", "/.snssdk", "/.amap", "/.alipay", "/tencent/", "/baidu/")
        return patterns.any { path.contains(it, ignoreCase = true) }
    }

    fun extractRelative(path: String): String {
        val prefixes = listOf(
            "/storage/emulated/0/",
            "/storage/self/primary/",
            "/sdcard/",
            "/mnt/sdcard/"
        )
        for (p in prefixes) {
            if (path.startsWith(p)) return sanitizeRelative(path.removePrefix(p))
        }
        // Bare roots ("/sdcard", "/storage/emulated/0") map to the sandbox root
        val roots = listOf("/storage/emulated/0", "/storage/self/primary", "/sdcard", "/mnt/sdcard")
        if (roots.any { path == it }) return ""
        return sanitizeRelative(path.trimStart('/'))
    }

    /**
     * Drops "." and ".." segments so a redirected path can never escape the sandbox root
     * (e.g. "/sdcard/../../data/data/other.app/databases").
     */
    fun sanitizeRelative(relative: String): String {
        val out = ArrayList<String>()
        for (segment in relative.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(segment)
            }
        }
        return out.joinToString("/")
    }
}
