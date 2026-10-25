package com.example.isolation.hook

import android.app.AndroidAppHelper
import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * IsoVault Official LSPosed & JingMatrix/Vector Hook Implementation.
 * Loaded by Vector/LSPosed via assets/xposed_init.
 * Hooks MediaStore (ContentResolver), java.io.File, Environment, and ContextImpl.
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "IsoVaultHook"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"
        const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"

        // Re-entrancy guard to prevent infinite recursion inside File/ContentResolver hooks
        private val isHandlingHook = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        // Background single-thread executor for non-blocking audit IPC calls to IsoVault
        private val auditExecutor = Executors.newSingleThreadExecutor()
        private val recentAuditCache = ConcurrentHashMap<String, Long>()

        private val DEFAULT_MEDIA_COLUMNS = arrayOf(
            "_id",
            "_data",
            "_display_name",
            "_size",
            "mime_type",
            "date_added",
            "date_modified",
            "datetaken",
            "width",
            "height",
            "orientation",
            "bucket_id",
            "bucket_display_name",
            "relative_path",
            "duration"
        )
    }

    private var xSharedPrefs: XSharedPreferences? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        try {
            xSharedPrefs = XSharedPreferences(MODULE_PKG, IsolationRuleManager.PREFS_NAME).apply {
                makeWorldReadable()
            }
        } catch (_: Throwable) {}
        Log.i(TAG, "[IsoVault] Zygote initialized on Vector / LSPosed.")
        XposedBridge.log("[IsoVault] Zygote initialized on Vector / LSPosed.")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return

        // Ignore core system packages unless specifically targeted
        if (pkg == "android" || pkg.startsWith("com.android.systemui") || pkg.startsWith("com.android.providers.")) {
            return
        }

        // 1. Self-hook for IsoVault Manager App
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
                XposedBridge.log("[IsoVault] Manager self-check hooked: ACTIVE")
            } catch (t: Throwable) {
                Log.w(TAG, "Self-hook error: ${t.message}")
            }
            return
        }

        // 2. Load isolation rule for this target package.
        // Note: In JingMatrix/Vector and LSPosed, handleLoadPackage is ONLY called for apps
        // that the user enabled in the Module Scope! So we isolate by default unless explicitly disabled.
        val rule = resolveRuleForPackage(pkg)
        if (!rule.isIsolated) {
            XposedBridge.log("[IsoVault] Skipping $pkg (explicitly disabled in IsoVault settings)")
            return
        }

        XposedBridge.log("[IsoVault] >>> ACTIVATING STORAGE & MEDIASTORE ISOLATION FOR: $pkg (Mode: ${rule.mode}) <<<")
        Log.i(TAG, "[IsoVault] Activating isolation for $pkg (Mode: ${rule.mode})")

        try {
            attachMediaStoreHooks(lpparam, pkg, rule)
            attachFileHooks(lpparam, pkg, rule)
            attachEnvironmentHooks(lpparam, pkg, rule)
        } catch (t: Throwable) {
            XposedBridge.log("[IsoVault] Error attaching hooks to $pkg: ${t.message}")
            Log.e(TAG, "Error attaching hooks to $pkg", t)
        }
    }

    private fun resolveRuleForPackage(pkg: String): IsolationRuleManager.AppRule {
        // 1. Try XSharedPreferences (Vector/LSPosed native bridge)
        try {
            val prefs = xSharedPrefs ?: XSharedPreferences(MODULE_PKG, IsolationRuleManager.PREFS_NAME).also {
                xSharedPrefs = it
            }
            prefs.reload()
            if (prefs.getBoolean("${pkg}_configured", false)) {
                val explicitlyDisabled = prefs.getBoolean("${pkg}_explicitly_disabled", false)
                val isolated = prefs.getBoolean("${pkg}_isolated", true) && !explicitlyDisabled
                val modeStr = prefs.getString("${pkg}_mode", "ENCRYPTED_VAULT") ?: "ENCRYPTED_VAULT"
                val mode = try { IsolationMode.valueOf(modeStr) } catch (_: Exception) { IsolationMode.ENCRYPTED_VAULT }
                val pollution = prefs.getBoolean("${pkg}_pollution", true)
                val whitelistStr = prefs.getString("${pkg}_whitelist", "Download") ?: "Download"
                val whitelist = whitelistStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                return IsolationRuleManager.AppRule(pkg, isolated, mode, pollution, whitelist)
            }
        } catch (_: Throwable) {}

        // 2. Try external .IsoVault rule file
        IsolationRuleManager.readExternalRule(pkg)?.let { return it }

        // 3. Default: Since Vector/LSPosed only invokes handleLoadPackage on scoped apps, isolate with ENCRYPTED_VAULT!
        return IsolationRuleManager.AppRule(
            packageName = pkg,
            isIsolated = true,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = emptySet()
        )
    }

    /**
     * Hooks ContentResolver.query so MediaStore scans (used by Snapchat Camera Roll, WhatsApp Gallery,
     * Instagram, TikTok, etc. on Android 10-16) cannot see the user's private photos & videos.
     */
    private fun attachMediaStoreHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        val mediaQueryHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull() as? Uri ?: return
                val authority = uri.authority ?: return

                if (authority == "media" || authority.contains("media", ignoreCase = true)) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return

                        // Check if user explicitly whitelisted DCIM or Pictures or Camera
                        val allowsGallery = rule.whitelistedFolders.any {
                            it.equals("DCIM", ignoreCase = true) ||
                                    it.equals("Pictures", ignoreCase = true) ||
                                    it.equals("Camera", ignoreCase = true) ||
                                    it.equals("All", ignoreCase = true)
                        }
                        if (allowsGallery) return

                        val projection = (param.args.getOrNull(1) as? Array<*>)
                            ?.filterIsInstance<String>()
                            ?.toTypedArray()
                            ?.takeIf { it.isNotEmpty() }
                            ?: DEFAULT_MEDIA_COLUMNS

                        // Return a clean empty MatrixCursor so the app's Camera Roll / Gallery is isolated
                        val emptyCursor: Cursor = MatrixCursor(projection)
                        param.result = emptyCursor

                        val action = if (rule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.SPOOFED_EMPTY
                        XposedBridge.log("[IsoVault] Isolated MediaStore scan in $pkg: $uri")
                        reportAuditToManager(
                            pkg = pkg,
                            requestedPath = uri.toString(),
                            resolvedPath = "IsoVault://empty_mediastore_cursor",
                            action = action,
                            operation = "MEDIASTORE_QUERY",
                            details = "Isolated MediaStore gallery scan (${rule.mode.badge})"
                        )
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        // Overload 1: query(Uri, String[], String, String[], String)
        try {
            XposedHelpers.findAndHookMethod(
                ContentResolver::class.java,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                mediaQueryHook
            )
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.query(5) hook: ${t.message}")
        }

        // Overload 2: query(Uri, String[], String, String[], String, CancellationSignal)
        try {
            XposedHelpers.findAndHookMethod(
                ContentResolver::class.java,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                CancellationSignal::class.java,
                mediaQueryHook
            )
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.query(6) hook: ${t.message}")
        }

        // Overload 3 (Android 10-16 primary API): query(Uri, String[], Bundle, CancellationSignal)
        try {
            XposedHelpers.findAndHookMethod(
                ContentResolver::class.java,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                Bundle::class.java,
                CancellationSignal::class.java,
                mediaQueryHook
            )
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.query(4) hook: ${t.message}")
        }
    }

    /**
     * Hooks java.io.File constructors and directory listing methods.
     */
    private fun attachFileHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        // 1. File(String pathname)
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val path = param.args[0] as? String ?: return
                        if (shouldInterceptPath(pkg, path)) {
                            isHandlingHook.set(true)
                            try {
                                val redirected = redirectPathForApp(pkg, path, initialRule)
                                if (redirected != path) {
                                    param.args[0] = redirected
                                }
                            } finally {
                                isHandlingHook.set(false)
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "File(String) hook error: ${t.message}")
        }

        // 2. File(String parent, String child)
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val parent = param.args[0] as? String ?: return
                        val child = param.args[1] as? String ?: return
                        val full = "${parent.trimEnd('/')}/${child.trimStart('/')}"
                        if (shouldInterceptPath(pkg, full)) {
                            isHandlingHook.set(true)
                            try {
                                val redirected = redirectPathForApp(pkg, full, initialRule)
                                if (redirected != full) {
                                    param.args[0] = redirected.substringBeforeLast('/')
                                    param.args[1] = redirected.substringAfterLast('/')
                                }
                            } finally {
                                isHandlingHook.set(false)
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "File(String, String) hook error: ${t.message}")
        }

        // 3. File(File parent, String child)
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val parentFile = param.args[0] as? File ?: return
                        val child = param.args[1] as? String ?: return
                        isHandlingHook.set(true)
                        try {
                            val full = "${parentFile.path.trimEnd('/')}/${child.trimStart('/')}"
                            if (shouldInterceptPath(pkg, full)) {
                                val redirected = redirectPathForApp(pkg, full, initialRule)
                                if (redirected != full) {
                                    param.args[0] = File(redirected.substringBeforeLast('/'))
                                    param.args[1] = redirected.substringAfterLast('/')
                                }
                            }
                        } finally {
                            isHandlingHook.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "File(File, String) hook error: ${t.message}")
        }

        // 4. File.listFiles() and File.list() protection against directory scanning
        val emptyListFilesHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val file = param.thisObject as? File ?: return
                isHandlingHook.set(true)
                try {
                    val path = file.path
                    if (shouldInterceptPath(pkg, path)) {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isWhitelisted(path, rule.whitelistedFolders)) {
                            param.result = emptyArray<File>()
                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = path,
                                resolvedPath = "IsoVault://empty_dir_listing",
                                action = AuditAction.SPOOFED_EMPTY,
                                operation = "LIST_FILES",
                                details = "Blocked folder scan on $path"
                            )
                        }
                    }
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", emptyListFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FilenameFilter::class.java, emptyListFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FileFilter::class.java, emptyListFilesHook)
        } catch (_: Throwable) {}
    }

    /**
     * Hooks Environment external storage directory getters.
     */
    private fun attachEnvironmentHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Environment",
                lpparam.classLoader,
                "getExternalStorageDirectory",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        isHandlingHook.set(true)
                        try {
                            val sandboxRoot = getOrCreateSandboxRoot(pkg)
                            param.result = sandboxRoot
                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = "/storage/emulated/0",
                                resolvedPath = sandboxRoot.absolutePath,
                                action = if (initialRule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.REDIRECTED,
                                operation = "GET_EXT_STORAGE",
                                details = "Redirected root external storage to isolated sandbox"
                            )
                        } finally {
                            isHandlingHook.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "getExternalStorageDirectory hook: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Environment",
                lpparam.classLoader,
                "getExternalStoragePublicDirectory",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val type = param.args[0] as? String ?: "Files"
                        isHandlingHook.set(true)
                        try {
                            val rule = resolveRuleForPackage(pkg)
                            if (isWhitelisted(type, rule.whitelistedFolders)) return
                            val sandboxSubDir = File(getOrCreateSandboxRoot(pkg), type)
                            sandboxSubDir.mkdirs()
                            param.result = sandboxSubDir
                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = "/storage/emulated/0/$type",
                                resolvedPath = sandboxSubDir.absolutePath,
                                action = if (rule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.REDIRECTED,
                                operation = "GET_PUBLIC_DIR",
                                details = "Redirected public directory ($type) to isolated vault"
                            )
                        } finally {
                            isHandlingHook.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "getExternalStoragePublicDirectory hook: ${t.message}")
        }
    }

    private fun shouldInterceptPath(pkg: String, path: String): Boolean {
        val norm = path.replace("\\", "/")
        if (!norm.startsWith("/storage/emulated/0") &&
            !norm.startsWith("/sdcard") &&
            !norm.startsWith("/mnt/sdcard")
        ) {
            return false
        }
        // Do not re-intercept paths already inside our sandbox or app-private Android/data
        if (norm.contains("/IsoVault_Sandbox") ||
            norm.contains("/.IsoVault/") ||
            norm.contains("/Android/data/$pkg")
        ) {
            return false
        }
        return true
    }

    private fun isWhitelisted(path: String, whitelistedFolders: Set<String>): Boolean {
        for (wf in whitelistedFolders) {
            if (wf.isNotBlank() && path.contains(wf, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun getOrCreateSandboxRoot(pkg: String): File {
        // Try app-specific external files dir first (always writable by the target app without permissions)
        try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) {
                val extSandbox = File(app.filesDir, "IsoVault_Sandbox")
                if (extSandbox.exists() || extSandbox.mkdirs()) {
                    return extSandbox
                }
            }
        } catch (_: Throwable) {}

        val fallback = File("/data/user/0/$pkg/files/IsoVault_Sandbox")
        try { fallback.mkdirs() } catch (_: Throwable) {}
        return fallback
    }

    private fun redirectPathForApp(
        pkg: String,
        originalPath: String,
        fallbackRule: IsolationRuleManager.AppRule
    ): String {
        val rule = resolveRuleForPackage(pkg)
        if (!rule.isIsolated) return originalPath

        val norm = originalPath.replace("\\", "/")
        if (isWhitelisted(norm, rule.whitelistedFolders)) {
            return originalPath
        }

        // Block root pollution folders
        if (rule.blockRootPollution && IsolationRuleManager.isRootPollution(norm)) {
            reportAuditToManager(
                pkg = pkg,
                requestedPath = originalPath,
                resolvedPath = "/dev/null",
                action = AuditAction.BLOCKED,
                operation = "BLOCK_POLLUTION",
                details = "Blocked root storage pollution folder"
            )
            return "/dev/null"
        }

        val relative = IsolationRuleManager.extractRelative(norm)
        val sandboxRoot = getOrCreateSandboxRoot(pkg)
        val targetFile = File(sandboxRoot, relative)
        try { targetFile.parentFile?.mkdirs() } catch (_: Throwable) {}

        val action = when (rule.mode) {
            IsolationMode.ENCRYPTED_VAULT -> AuditAction.ENCRYPTED
            IsolationMode.SANDBOX_REDIRECT -> AuditAction.REDIRECTED
            IsolationMode.FAKE_EMPTY -> AuditAction.SPOOFED_EMPTY
            IsolationMode.READ_ONLY -> AuditAction.BLOCKED
        }

        XposedBridge.log("[IsoVault] Redirected ($pkg): $originalPath -> ${targetFile.absolutePath}")
        reportAuditToManager(
            pkg = pkg,
            requestedPath = originalPath,
            resolvedPath = targetFile.absolutePath,
            action = action,
            operation = "FILE_REDIRECT",
            details = "Redirected to isolated container (${rule.mode.badge})"
        )

        return targetFile.absolutePath
    }

    /**
     * Asynchronously sends real-time interception audit logs from the hooked target process
     * back to IsoVaultConfigProvider so the user sees live interceptions in the IsoVault UI.
     */
    private fun reportAuditToManager(
        pkg: String,
        requestedPath: String,
        resolvedPath: String,
        action: AuditAction,
        operation: String,
        details: String
    ) {
        val key = "$pkg|$operation|$requestedPath"
        val now = System.currentTimeMillis()
        val lastTime = recentAuditCache[key] ?: 0L
        // Throttle identical path logs to once every 4 seconds per path
        if (now - lastTime < 4000L) return
        recentAuditCache[key] = now

        auditExecutor.execute {
            try {
                val app = AndroidAppHelper.currentApplication() ?: return@execute
                val extras = Bundle().apply {
                    putString("requestedPath", requestedPath)
                    putString("resolvedPath", resolvedPath)
                    putString("action", action.name)
                    putString("operation", operation)
                    putString("details", details)
                }
                app.contentResolver.call(
                    Uri.parse(PROVIDER_URI),
                    "record_audit",
                    pkg,
                    extras
                )
            } catch (_: Throwable) {
                // Ignore if provider not reachable yet during early process startup
            }
        }
    }
}
