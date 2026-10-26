package com.example.isolation.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.IsoVaultApp
import com.example.isolation.data.entity.AppIsolationConfig

/**
 * Isolation & System-Wide Stealth rule storage and path helpers.
 *
 * Rules are published to hooked apps, SystemUI, and Android System Framework through two channels
 * that only the manager can write:
 * the [com.example.isolation.provider.IsoVaultConfigProvider] (live, caller-authenticated) and a
 * world-readable SharedPreferences file read via XSharedPreferences.
 */
object IsolationRuleManager {

    const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    const val PREFS_NAME = "isovault_rules"

    data class AppRule(
        val packageName: String,
        val isIsolated: Boolean,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>,
        val pickerPolicy: PickerPolicy = PickerPolicy.ALLOW
    )

    /**
     * System-wide stealth rule read by Android System Framework (`android`), `com.android.systemui`,
     * and any existing or newly installed launcher.
     */
    data class StealthRule(
        val packageName: String,
        val visibilityMode: LauncherVisibilityMode,
        val customLabel: String,
        val iconPreset: DisguiseIconPreset,
        val hideInRecents: Boolean = true
    )

    const val KEY_PICKER_POLICY = "global_picker_policy"
    const val KEY_STEALTH_TABLE = "global_stealth_table_v1"
    const val KEY_SYSTEMUI_TRIGGER = "systemui_trigger_enabled"

    fun publishPickerPolicy(policy: PickerPolicy) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit().putString(KEY_PICKER_POLICY, policy.name).commit()
        } catch (_: Throwable) {
        }
    }

    fun publishSystemUiTrigger(enabled: Boolean) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit().putBoolean(KEY_SYSTEMUI_TRIGGER, enabled).commit()
        } catch (_: Throwable) {
        }
    }

    /**
     * Serializes all active app stealth rules + Hollow's own stealth settings into a compact
     * newline-delimited table in world-readable SharedPreferences so `android`, `com.android.systemui`,
     * and any launcher process can read the full stealth map in a single fast lookup.
     */
    fun publishStealthTable(
        configs: List<AppIsolationConfig>,
        settings: AppSettings
    ) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        val entries = mutableListOf<String>()

        // 1. Hollow itself if disguised or hidden
        if (settings.hollowVisibilityMode != LauncherVisibilityMode.VISIBLE) {
            val safeLabel = settings.hollowCustomName.replace("|", " ").replace("\n", " ").trim()
            entries.add(
                listOf(
                    MODULE_PKG,
                    settings.hollowVisibilityMode.name,
                    settings.hollowIconPreset.name,
                    "1",
                    safeLabel
                ).joinToString("|")
            )
        }

        // 2. All apps enrolled in Hide & Disguise Apps
        for (cfg in configs) {
            if (cfg.isHiddenOrDisguised && cfg.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE) {
                val safeLabel = cfg.customLauncherLabel.replace("|", " ").replace("\n", " ").trim()
                entries.add(
                    listOf(
                        cfg.packageName,
                        cfg.launcherVisibilityMode.name,
                        cfg.disguiseIconPreset.name,
                        if (cfg.hideInSystemUiRecents) "1" else "0",
                        safeLabel
                    ).joinToString("|")
                )
            }
        }

        val serialized = entries.joinToString("\n")
        try {
            getReadableSharedPrefs(app).edit()
                .putString(KEY_STEALTH_TABLE, serialized)
                .putBoolean(KEY_SYSTEMUI_TRIGGER, settings.systemUiTriggerEnabled)
                .commit()
        } catch (_: Throwable) {
        }
    }

    fun parseStealthTable(raw: String?): Map<String, StealthRule> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, StealthRule>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("|", limit = 5)
            if (parts.size < 4) continue
            val pkg = parts[0]
            val visMode = try {
                LauncherVisibilityMode.valueOf(parts[1])
            } catch (_: Exception) {
                LauncherVisibilityMode.VISIBLE
            }
            if (visMode == LauncherVisibilityMode.VISIBLE) continue
            val preset = try {
                DisguiseIconPreset.valueOf(parts[2])
            } catch (_: Exception) {
                DisguiseIconPreset.CALCULATOR
            }
            val hideRecents = parts[3] == "1"
            val customLabel = if (parts.size >= 5) parts[4] else ""
            out[pkg] = StealthRule(
                packageName = pkg,
                visibilityMode = visMode,
                customLabel = customLabel,
                iconPreset = preset,
                hideInRecents = hideRecents
            )
        }
        return out
    }

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
