package com.example.isolation.hook

import android.util.Log
import com.example.isolation.model.IsolationRuleManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * IsoVault Official LSPosed Module Hook Implementation.
 * Loaded ONLY by LSPosed runtime via assets/xposed_init.
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "IsoVaultHook"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
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
        val shouldIsolate = IsolationRuleManager.isPackageIsolated(pkg) || pkg == "com.snapchat.android"
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
                            param.args[1] = IsolationRuleManager.extractRelative(full)
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
        val rel = IsolationRuleManager.extractRelative(originalPath)
        val isolatedDir = File("/data/data/$pkg/isolated_storage")
        try { isolatedDir.mkdirs() } catch (_: Exception) {}
        return File(isolatedDir, rel).absolutePath
    }
}
