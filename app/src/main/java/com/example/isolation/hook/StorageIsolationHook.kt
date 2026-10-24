package com.example.isolation.hook

import android.util.Log
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * IsoVault Official LSPosed Module Hook Implementation
 * Excluded from APK (compileOnly) to prevent classloader collisions.
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "IsoVaultHook"
        const val HOOK_VERSION = "2.5.3-android15"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"

        // In-memory runtime tracking for in-app tests
        @Volatile
        var isModuleHookActive: Boolean = false

        // Listener for in-app live ticker
        var onAccessIntercepted: ((packageName: String, path: String, action: AuditAction, details: String) -> Unit)? = null

        // In-memory map of isolated apps
        val localRules = mutableMapOf<String, AppRule>()

        data class AppRule(
            val packageName: String,
            val isIsolated: Boolean,
            val mode: IsolationMode,
            val blockRootPollution: Boolean,
            val whitelistedFolders: Set<String>
        )

        init {
            // Default target rule for Snapchat so it hooks immediately out of the box
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

        private fun extractRelative(path: String): String {
            val p1 = "/storage/emulated/0/"
            val p2 = "/sdcard/"
            return when {
                path.startsWith(p1) -> path.removePrefix(p1)
                path.startsWith(p2) -> path.removePrefix(p2)
                else -> path.trimStart('/')
            }
        }
    }

    data class PathResolutionResult(
        val originalPath: String,
        val resolvedPath: String,
        val action: AuditAction,
        val details: String
    )

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        isModuleHookActive = true
        Log.i(TAG, "[IsoVault] Zygote initialized successfully on Android 15 runtime.")
        XposedBridge.log("[IsoVault] Zygote initialized. Storage Isolation Module active.")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return

        // 1. Hook IsoVault itself so UI knows LSPosed is active
        if (pkg == MODULE_PKG) {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.example.MainActivity",
                    lpparam.classLoader,
                    "isLsposedModuleActiveReal",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
                XposedBridge.log("[IsoVault] Module self-hook ACTIVE")
            } catch (t: Throwable) {
                Log.w(TAG, "Self-hook error: ${t.message}")
            }
            return
        }

        // 2. Check if this package should be isolated (Snapchat is enabled by default)
        val shouldIsolate = isPackageIsolated(pkg) || pkg == "com.snapchat.android"
        if (!shouldIsolate) return

        XposedBridge.log("[IsoVault] Attaching storage isolation to: $pkg")
        Log.i(TAG, "[IsoVault] Attaching storage isolation to: $pkg")

        try {
            attachStorageHooks(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("[IsoVault] Error hooking $pkg: ${t.message}")
            Log.e(TAG, "Error hooking $pkg", t)
        }
    }

    private fun attachStorageHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // Hook File(String)
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args[0] as? String ?: return
                        if (shouldRedirect(path)) {
                            val targetPath = getRedirectedPath(pkg, path)
                            param.args[0] = targetPath
                            XposedBridge.log("[IsoVault] Redirected File($path) -> $targetPath")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "File(String) hook error: ${t.message}")
        }

        // Hook File(String, String)
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val parent = param.args[0] as? String ?: return
                        val child = param.args[1] as? String ?: return
                        val full = "$parent/$child"
                        if (shouldRedirect(full)) {
                            param.args[0] = getRedirectedBase(pkg)
                            param.args[1] = extractRelative(full)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "File(String, String) hook error: ${t.message}")
        }

        // Hook Environment.getExternalStorageDirectory()
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Environment",
                lpparam.classLoader,
                "getExternalStorageDirectory",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val isolatedDir = File("/data/data/$pkg/isolated_storage")
                        isolatedDir.mkdirs()
                        param.result = isolatedDir
                        XposedBridge.log("[IsoVault] Redirected getExternalStorageDirectory() for $pkg")
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "getExternalStorageDirectory hook error: ${t.message}")
        }

        // Hook Environment.getExternalStoragePublicDirectory(String)
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Environment",
                lpparam.classLoader,
                "getExternalStoragePublicDirectory",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val type = param.args[0] as? String ?: "data"
                        val isolatedDir = File("/data/data/$pkg/isolated_storage/$type")
                        isolatedDir.mkdirs()
                        param.result = isolatedDir
                        XposedBridge.log("[IsoVault] Redirected getExternalStoragePublicDirectory($type) for $pkg")
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "getExternalStoragePublicDirectory hook error: ${t.message}")
        }
    }

    private fun shouldRedirect(path: String): Boolean {
        return path.startsWith("/storage/emulated/0") ||
                path.startsWith("/sdcard") ||
                path.startsWith("/mnt/sdcard")
    }

    private fun getRedirectedBase(pkg: String): String {
        val dir = File("/data/data/$pkg/isolated_storage")
        try { dir.mkdirs() } catch (_: Exception) {}
        return dir.absolutePath
    }

    private fun getRedirectedPath(pkg: String, originalPath: String): String {
        val rel = extractRelative(originalPath)
        val isolatedDir = File("/data/data/$pkg/isolated_storage")
        try { isolatedDir.mkdirs() } catch (_: Exception) {}
        return File(isolatedDir, rel).absolutePath
    }
}
