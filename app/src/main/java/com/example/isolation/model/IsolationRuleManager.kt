package com.example.isolation.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.IsoVaultApp
import java.io.File

/**
 * Pure Android Isolation Rule & Path Resolution Manager.
 * Synchronizes rules via Vector/LSPosed XSharedPreferences (MODE_WORLD_READABLE),
 * ContentProvider, and shared storage config so hooked apps always receive live rules.
 */
object IsolationRuleManager {

    const val HOOK_VERSION = "2.6.0-vector-lsposed"
    const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    const val PREFS_NAME = "isovault_rules"

    @Volatile
    var isSimulatedHookActive: Boolean = false

    var onAccessIntercepted: ((packageName: String, path: String, action: AuditAction, details: String) -> Unit)? = null

    val localRules = mutableMapOf<String, AppRule>()

    data class AppRule(
        val packageName: String,
        val isIsolated: Boolean,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>
    )

    init {
        localRules["com.snapchat.android"] = AppRule(
            packageName = "com.snapchat.android",
            isIsolated = true,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = setOf("Download")
        )
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
        } catch (_: SecurityException) {
            false
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
        whitelistedFolders: Set<String> = setOf("Download")
    ) {
        val cleanWhitelist = whitelistedFolders.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        localRules[packageName] = AppRule(
            packageName = packageName,
            isIsolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelistedFolders = cleanWhitelist
        )
        syncRuleToSharedPrefsAndDisk(
            pkg = packageName,
            isolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = cleanWhitelist.joinToString(",")
        )
    }

    fun removeRule(packageName: String) {
        val existing = localRules[packageName]
        localRules[packageName] = AppRule(
            packageName = packageName,
            isIsolated = false,
            mode = existing?.mode ?: IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = existing?.blockRootPollution ?: true,
            whitelistedFolders = existing?.whitelistedFolders ?: setOf("Download")
        )
        syncRuleToSharedPrefsAndDisk(
            pkg = packageName,
            isolated = false,
            mode = existing?.mode ?: IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = existing?.blockRootPollution ?: true,
            whitelist = (existing?.whitelistedFolders ?: setOf("Download")).joinToString(",")
        )
    }

    private fun syncRuleToSharedPrefsAndDisk(
        pkg: String,
        isolated: Boolean,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelist: String
    ) {
        try {
            val app = IsoVaultApp.instanceOrNull()
            if (app != null) {
                val prefs = getReadableSharedPrefs(app)
                prefs.edit()
                    .putBoolean("configured_init", true)
                    .putBoolean("${pkg}_configured", true)
                    .putBoolean("${pkg}_isolated", isolated)
                    .putBoolean("${pkg}_explicitly_disabled", !isolated)
                    .putString("${pkg}_mode", mode.name)
                    .putBoolean("${pkg}_pollution", blockRootPollution)
                    .putString("${pkg}_whitelist", whitelist)
                    .commit() // Synchronous commit so XSharedPreferences sees changes immediately
            }
        } catch (_: Throwable) {}

        // Also mirror to shared external .IsoVault config directory if accessible
        try {
            val sharedDir = File("/storage/emulated/0/.IsoVault/config")
            if (sharedDir.exists() || sharedDir.mkdirs()) {
                val nomedia = File("/storage/emulated/0/.IsoVault/.nomedia")
                if (!nomedia.exists()) nomedia.createNewFile()
                val ruleFile = File(sharedDir, "$pkg.rule")
                ruleFile.writeText("$isolated|${mode.name}|$blockRootPollution|$whitelist")
            }
        } catch (_: Throwable) {}
    }

    fun isPackageIsolated(packageName: String): Boolean {
        val rule = localRules[packageName]
        if (rule != null) return rule.isIsolated
        return readExternalRule(packageName)?.isIsolated ?: false
    }

    fun readExternalRule(pkg: String): AppRule? {
        return try {
            val ruleFile = File("/storage/emulated/0/.IsoVault/config/$pkg.rule")
            if (ruleFile.exists() && ruleFile.canRead()) {
                val parts = ruleFile.readText().trim().split("|")
                if (parts.size >= 4) {
                    val isolated = parts[0].toBoolean()
                    val mode = try { IsolationMode.valueOf(parts[1]) } catch (_: Exception) { IsolationMode.ENCRYPTED_VAULT }
                    val pollution = parts[2].toBoolean()
                    val whitelist = parts[3].split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    AppRule(pkg, isolated, mode, pollution, whitelist)
                } else null
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun resolvePath(
        packageName: String,
        requestedPath: String,
        isWriteOperation: Boolean = false,
        baseAppVaultDir: File? = null
    ): PathResolutionResult {
        val rule = localRules[packageName]
        if (rule == null || !rule.isIsolated) {
            return PathResolutionResult(requestedPath, requestedPath, AuditAction.PERMITTED, "Not isolated")
        }

        val normalized = requestedPath.replace("\\", "/")

        if (rule.blockRootPollution && isRootPollution(normalized)) {
            onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.BLOCKED, "Blocked root pollution folder")
            return PathResolutionResult(requestedPath, "/dev/null", AuditAction.BLOCKED, "Root pollution blocked")
        }

        for (wf in rule.whitelistedFolders) {
            if (wf.isNotBlank() && normalized.contains("/$wf", ignoreCase = true)) {
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.PERMITTED, "Whitelisted folder")
                return PathResolutionResult(requestedPath, requestedPath, AuditAction.PERMITTED, "Whitelisted folder")
            }
        }

        val targetBase = baseAppVaultDir?.absolutePath ?: "/storage/emulated/0/Android/media/$packageName/IsoVault_Sandbox"
        val relative = extractRelative(normalized)

        return when (rule.mode) {
            IsolationMode.ENCRYPTED_VAULT -> {
                val encPath = "$targetBase/$relative.isovault"
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.ENCRYPTED, "Encrypted vault redirection")
                PathResolutionResult(requestedPath, encPath, AuditAction.ENCRYPTED, "AES-256 vault container")
            }
            IsolationMode.SANDBOX_REDIRECT -> {
                val sandPath = "$targetBase/$relative"
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.REDIRECTED, "Sandbox redirection")
                PathResolutionResult(requestedPath, sandPath, AuditAction.REDIRECTED, "Isolated sandbox container")
            }
            IsolationMode.FAKE_EMPTY -> {
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.SPOOFED_EMPTY, "Empty storage spoofed")
                PathResolutionResult(requestedPath, "$targetBase/.empty_spoof", AuditAction.SPOOFED_EMPTY, "Empty spoof")
            }
            IsolationMode.READ_ONLY -> {
                if (isWriteOperation) {
                    onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.BLOCKED, "Write blocked in Read-Only mode")
                    PathResolutionResult(requestedPath, "/dev/null", AuditAction.BLOCKED, "Read-only blocked")
                } else {
                    PathResolutionResult(requestedPath, requestedPath, AuditAction.PERMITTED, "Read allowed")
                }
            }
        }
    }

    fun isRootPollution(path: String): Boolean {
        val patterns = listOf("/.tencent", "/.baidu", "/.pdd", "/.snssdk", "/.amap", "/.alipay", "/tencent/", "/baidu/")
        return patterns.any { path.contains(it, ignoreCase = true) }
    }

    fun extractRelative(path: String): String {
        val p1 = "/storage/emulated/0/"
        val p2 = "/sdcard/"
        val p3 = "/mnt/sdcard/"
        return when {
            path.startsWith(p1) -> path.removePrefix(p1)
            path.startsWith(p2) -> path.removePrefix(p2)
            path.startsWith(p3) -> path.removePrefix(p3)
            else -> path.trimStart('/')
        }
    }

    data class PathResolutionResult(
        val originalPath: String,
        val resolvedPath: String,
        val action: AuditAction,
        val details: String
    )
}
