package com.example.isolation.model

import java.io.File

/**
 * Pure Android Isolation Rule & Path Resolution Manager.
 * Contains no Xposed dependencies so it can be safely called by the Manager App UI
 * without triggering NoClassDefFoundError in normal userspace.
 */
object IsolationRuleManager {

    const val HOOK_VERSION = "2.5.3-android15"
    const val MODULE_PKG = "com.aistudio.isovault.xrvf"

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
        // Pre-configure Snapchat by default
        updateRule("com.snapchat.android", IsolationMode.ENCRYPTED_VAULT, true, setOf("Download"))
    }

    fun updateRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean = true,
        whitelistedFolders: Set<String> = setOf("Download")
    ) {
        localRules[packageName] = AppRule(
            packageName = packageName,
            isIsolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelistedFolders = whitelistedFolders
        )
        writeRuleToFile(packageName, true, mode.name)
    }

    fun removeRule(packageName: String) {
        localRules.remove(packageName)
        writeRuleToFile(packageName, false, "NONE")
    }

    fun isPackageIsolated(packageName: String): Boolean {
        val rule = localRules[packageName]
        if (rule != null) return rule.isIsolated
        return checkRuleFile(packageName)
    }

    private fun writeRuleToFile(pkg: String, isolated: Boolean, mode: String) {
        try {
            val dir = File("/data/local/tmp")
            if (dir.exists() && dir.canWrite()) {
                val file = File(dir, "isovault_$pkg.rule")
                file.writeText("$isolated|$mode")
            }
        } catch (_: Exception) {}
    }

    private fun checkRuleFile(pkg: String): Boolean {
        return try {
            val file = File("/data/local/tmp/isovault_$pkg.rule")
            if (file.exists() && file.canRead()) {
                file.readText().trim().startsWith("true")
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun resolvePath(
        packageName: String,
        requestedPath: String,
        isWriteOperation: Boolean = false,
        baseAppVaultDir: File? = null
    ): PathResolutionResult {
        val rule = localRules[packageName]
            ?: return PathResolutionResult(requestedPath, requestedPath, AuditAction.PERMITTED, "Not isolated")

        val normalized = requestedPath.replace("\\", "/")

        if (rule.blockRootPollution && isRootPollution(normalized)) {
            onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.BLOCKED, "Blocked root pollution folder")
            return PathResolutionResult(requestedPath, "/dev/null", AuditAction.BLOCKED, "Root pollution blocked")
        }

        for (wf in rule.whitelistedFolders) {
            if (normalized.contains("/$wf", ignoreCase = true)) {
                onAccessIntercepted?.invoke(packageName, requestedPath, AuditAction.PERMITTED, "Whitelisted folder")
                return PathResolutionResult(requestedPath, requestedPath, AuditAction.PERMITTED, "Whitelisted folder")
            }
        }

        val targetBase = baseAppVaultDir?.absolutePath ?: "/data/data/$packageName/isolated_storage"
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
                PathResolutionResult(requestedPath, "/data/local/tmp/isovault_empty_dummy", AuditAction.SPOOFED_EMPTY, "Empty spoof")
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

    private fun isRootPollution(path: String): Boolean {
        val patterns = listOf("/.tencent", "/.baidu", "/.pdd", "/.snssdk", "/.amap", "/.alipay")
        return patterns.any { path.contains(it, ignoreCase = true) }
    }

    fun extractRelative(path: String): String {
        val p1 = "/storage/emulated/0/"
        val p2 = "/sdcard/"
        return when {
            path.startsWith(p1) -> path.removePrefix(p1)
            path.startsWith(p2) -> path.removePrefix(p2)
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
