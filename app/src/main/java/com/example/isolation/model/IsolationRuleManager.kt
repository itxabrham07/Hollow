package com.example.isolation.model

import android.content.Context
import android.content.SharedPreferences
import com.example.IsoVaultApp
import java.io.File

/**
 * Pure Android Isolation Rule & Path Resolution Manager.
 * Operates purely with secure private storage and ContentProvider communication.
 * No MODE_WORLD_READABLE and no external unencrypted config files.
 */
object IsolationRuleManager {

    const val HOOK_VERSION = "3.0.0-hollow"
    const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    const val PREFS_NAME = "hollow_rules_private"

    @Volatile
    var isSimulatedHookActive: Boolean = false

    var onAccessIntercepted: ((packageName: String, path: String, action: AuditAction, details: String) -> Unit)? = null

    val localRules = mutableMapOf<String, AppRule>()

    data class AppRule(
        val packageName: String,
        val isIsolated: Boolean,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>,
        val useSharedSpace: Boolean = false
    )

    fun getPrivateSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun updateRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean = true,
        whitelistedFolders: Set<String> = emptySet(),
        useSharedSpace: Boolean = false
    ) {
        val cleanWhitelist = whitelistedFolders.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        localRules[packageName] = AppRule(
            packageName = packageName,
            isIsolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelistedFolders = cleanWhitelist,
            useSharedSpace = useSharedSpace
        )
        syncRuleToPrefs(
            pkg = packageName,
            isolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = cleanWhitelist.joinToString(","),
            useSharedSpace = useSharedSpace
        )
    }

    fun removeRule(packageName: String) {
        val existing = localRules[packageName]
        localRules[packageName] = AppRule(
            packageName = packageName,
            isIsolated = false,
            mode = existing?.mode ?: IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = existing?.blockRootPollution ?: true,
            whitelistedFolders = existing?.whitelistedFolders ?: emptySet(),
            useSharedSpace = existing?.useSharedSpace ?: false
        )
        syncRuleToPrefs(
            pkg = packageName,
            isolated = false,
            mode = existing?.mode ?: IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = existing?.blockRootPollution ?: true,
            whitelist = (existing?.whitelistedFolders ?: emptySet()).joinToString(","),
            useSharedSpace = existing?.useSharedSpace ?: false
        )
    }

    private fun syncRuleToPrefs(
        pkg: String,
        isolated: Boolean,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelist: String,
        useSharedSpace: Boolean
    ) {
        try {
            val app = IsoVaultApp.instanceOrNull()
            if (app != null) {
                val prefs = getPrivateSharedPrefs(app)
                prefs.edit()
                    .putBoolean("${pkg}_configured", true)
                    .putBoolean("${pkg}_isolated", isolated)
                    .putString("${pkg}_mode", mode.name)
                    .putBoolean("${pkg}_pollution", blockRootPollution)
                    .putString("${pkg}_whitelist", whitelist)
                    .putBoolean("${pkg}_shared_space", useSharedSpace)
                    .apply()
            }
        } catch (_: Throwable) {}
    }

    fun isPackageIsolated(packageName: String): Boolean {
        val rule = localRules[packageName]
        if (rule != null) return rule.isIsolated
        val app = IsoVaultApp.instanceOrNull() ?: return false
        val prefs = getPrivateSharedPrefs(app)
        return prefs.getBoolean("${packageName}_isolated", false)
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

        val relative = extractRelative(normalized)
        val targetBase = baseAppVaultDir?.absolutePath ?: "/data/user/0/$MODULE_PKG/files/vaults/$packageName"

        return when (rule.mode) {
            IsolationMode.ENCRYPTED_VAULT -> {
                val encPath = "$targetBase/$relative.hllw"
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.ENCRYPTED, "Encrypted vault redirection")
                PathResolutionResult(requestedPath, encPath, AuditAction.ENCRYPTED, "AES-256 chunked AEAD container")
            }
            IsolationMode.SANDBOX_REDIRECT -> {
                val sandPath = "$targetBase/$relative.hllw"
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.REDIRECTED, "Sandbox redirection")
                PathResolutionResult(requestedPath, sandPath, AuditAction.REDIRECTED, "Isolated sandbox container")
            }
            IsolationMode.FAKE_EMPTY -> {
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.SPOOFED_EMPTY, "Empty storage spoofed")
                PathResolutionResult(requestedPath, "/dev/null", AuditAction.SPOOFED_EMPTY, "Empty spoof")
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
