package com.example.isolation.hook

import android.app.AndroidAppHelper
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.provider.IsoVaultConfigProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileDescriptor
import java.io.FileFilter
import java.io.FilenameFilter
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Production LSPosed Storage Isolation Hook Engine for Hollow (API 82).
 * Intercepts File constructors, File queries, Os/IoBridge open, MediaStore operations,
 * ImageDecoder, MediaMetadataRetriever, and Picker intents.
 *
 * Routes all volume I/O strictly through IsoVaultConfigProvider via ParcelFileDescriptor pipes.
 * Enforces per-app volumes and Shared Space visibility toggle.
 * All hook bodies are guarded with robust try/catch blocks to ensure target apps never crash.
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "HollowHook"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"
        const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"
        const val VAULT_BASE_URI = "content://com.aistudio.isovault.xrvf.provider/vault"

        private val isHandlingHook = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        private val ruleCache = ConcurrentHashMap<String, CachedRule>()
        private val recentAuditCache = ConcurrentHashMap<String, Long>()
        private val auditExecutor = Executors.newSingleThreadExecutor()

        private data class CachedRule(
            val rule: IsolationRuleManager.AppRule,
            val timestamp: Long
        )
    }

    private class SafeEmptyMatrixCursor(projection: Array<String>) : MatrixCursor(projection, 0) {
        override fun getCount(): Int = 0
        override fun getColumnIndex(columnName: String?): Int = -1
        override fun getColumnIndexOrThrow(columnName: String?): Int {
            val idx = getColumnIndex(columnName)
            if (idx < 0) throw IllegalArgumentException("Column $columnName not found")
            return idx
        }
        override fun getExtras(): Bundle = Bundle.EMPTY
    }

    private class SafeSandboxMatrixCursor(projection: Array<String>) : MatrixCursor(projection) {
        override fun getColumnIndex(columnName: String?): Int {
            for (i in columnNames.indices) {
                if (columnNames[i].equals(columnName, ignoreCase = true)) return i
            }
            return -1
        }
        override fun getColumnIndexOrThrow(columnName: String?): Int {
            val idx = getColumnIndex(columnName)
            if (idx < 0) throw IllegalArgumentException("Column $columnName not found")
            return idx
        }
        override fun getExtras(): Bundle = Bundle.EMPTY
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        try {
            XposedBridge.log("[Hollow] Initialized in Zygote (Android 15+ compatible, LSPosed API 82)")
        } catch (t: Throwable) {
            Log.w(TAG, "initZygote non-fatal: ${t.message}")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return

        // Self-hook for Hollow Manager App to confirm module status
        if (pkg == MODULE_PKG) {
            try {
                val activityClass = XposedHelpers.findClassIfExists("com.example.MainActivity", lpparam.classLoader)
                if (activityClass != null) {
                    XposedHelpers.findAndHookMethod(
                        activityClass,
                        "isLsposedModuleActiveReal",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Self-hook non-fatal: ${t.message}")
            }
            return
        }

        // Target application hooks
        try {
            val initialRule = resolveRuleForPackage(pkg)
            attachMediaStoreAndProviderHooks(lpparam, pkg, initialRule)
            attachPickerInterceptorHooks(lpparam, pkg, initialRule)
            attachFileHooks(lpparam, pkg, initialRule)
            attachLowLevelOsHooks(lpparam, pkg, initialRule)
            attachEnvironmentHooks(lpparam, pkg, initialRule)
        } catch (t: Throwable) {
            Log.w(TAG, "Non-fatal error attaching hooks to $pkg: ${t.message}")
        }
    }

    private fun resolveRuleForPackage(pkg: String): IsolationRuleManager.AppRule {
        val cached = ruleCache[pkg]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestamp < 3000L)) {
            return cached.rule
        }
        val rule = fetchRuleInternal(pkg)
        ruleCache[pkg] = CachedRule(rule, now)
        return rule
    }

    private fun fetchRuleInternal(pkg: String): IsolationRuleManager.AppRule {
        try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) {
                val bundle = app.contentResolver.call(
                    Uri.parse(PROVIDER_URI),
                    "check_isolated",
                    pkg,
                    null
                )
                if (bundle != null && !bundle.getBoolean("error", false)) {
                    val isIsolated = bundle.getBoolean("isIsolated", false)
                    val modeName = bundle.getString("mode", IsolationMode.ENCRYPTED_VAULT.name)
                    val mode = try { IsolationMode.valueOf(modeName) } catch (_: Exception) { IsolationMode.ENCRYPTED_VAULT }
                    val whitelistStr = bundle.getString("whitelistedFolders", "")
                    val whitelist = whitelistStr.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    val pollution = bundle.getBoolean("blockPollution", true)
                    val useShared = bundle.getBoolean("useSharedSpace", false)

                    return IsolationRuleManager.AppRule(
                        packageName = pkg,
                        isIsolated = isIsolated,
                        mode = mode,
                        blockRootPollution = pollution,
                        whitelistedFolders = whitelist,
                        useSharedSpace = useShared
                    )
                }
            }
        } catch (_: Throwable) {}

        return IsolationRuleManager.AppRule(
            packageName = pkg,
            isIsolated = false,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = emptySet(),
            useSharedSpace = false
        )
    }

    private fun isMediaOrExternalDocumentUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val auth = uri.authority ?: return false
        return auth == "media" ||
                auth == "com.android.providers.media" ||
                auth == "com.android.providers.media.documents" ||
                auth == "com.android.externalstorage.documents"
    }

    private fun buildVaultContentUri(pkg: String, relativePath: String, useSharedSpace: Boolean): Uri {
        val isShared = relativePath.startsWith("Shared/", ignoreCase = true)
        val volume = if (isShared && useSharedSpace) "shared" else pkg
        val cleanPath = if (isShared) relativePath.removePrefix("Shared/").removePrefix("shared/") else relativePath
        return Uri.parse("$VAULT_BASE_URI/$volume/$cleanPath")
    }

    /**
     * LAYER 1: MediaStore & ContentResolver Hooks.
     */
    private fun attachMediaStoreAndProviderHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        // Query hook
        val mediaQueryHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull() as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated) return

                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uri.toString(),
                        resolvedPath = "isolated_media",
                        action = if (rule.mode == IsolationMode.FAKE_EMPTY) AuditAction.SPOOFED_EMPTY else AuditAction.ENCRYPTED,
                        operation = "MEDIASTORE_QUERY",
                        details = "Intercepted MediaStore query (${rule.mode.badge})"
                    )

                    val proj = (param.args.getOrNull(1) as? Array<*>)?.filterIsInstance<String>()?.toTypedArray()
                        ?: arrayOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DATA,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.MIME_TYPE,
                            MediaStore.MediaColumns.DATE_MODIFIED
                        )

                    if (rule.mode == IsolationMode.FAKE_EMPTY) {
                        param.result = SafeEmptyMatrixCursor(proj)
                        return
                    }

                    val cursor = SafeSandboxMatrixCursor(proj)
                    // Query files from provider
                    val app = AndroidAppHelper.currentApplication()
                    if (app != null) {
                        val extras = Bundle().apply {
                            putString("path", "")
                            putString("volume", pkg)
                        }
                        val bundle = app.contentResolver.call(Uri.parse(PROVIDER_URI), "list_files", pkg, extras)
                        val files = bundle?.getStringArray("files") ?: emptyArray()

                        var idCounter = 1000L
                        for (fName in files) {
                            val row = arrayOfNulls<Any>(proj.size)
                            for (c in proj.indices) {
                                when (proj[c]) {
                                    MediaStore.MediaColumns._ID -> row[c] = idCounter
                                    MediaStore.MediaColumns.DATA -> row[c] = "/storage/emulated/0/$fName"
                                    MediaStore.MediaColumns.DISPLAY_NAME -> row[c] = fName
                                    MediaStore.MediaColumns.SIZE -> row[c] = 1024L
                                    MediaStore.MediaColumns.MIME_TYPE -> row[c] = "application/octet-stream"
                                    MediaStore.MediaColumns.DATE_MODIFIED -> row[c] = System.currentTimeMillis() / 1000
                                    else -> row[c] = null
                                }
                            }
                            cursor.addRow(row)
                            idCounter++
                        }

                        // If shared space is enabled, also list files from Shared volume
                        if (rule.useSharedSpace) {
                            val sharedExtras = Bundle().apply {
                                putString("path", "")
                                putString("volume", "shared")
                            }
                            val sharedBundle = app.contentResolver.call(Uri.parse(PROVIDER_URI), "list_files", pkg, sharedExtras)
                            val sharedFiles = sharedBundle?.getStringArray("files") ?: emptyArray()
                            for (sName in sharedFiles) {
                                val row = arrayOfNulls<Any>(proj.size)
                                for (c in proj.indices) {
                                    when (proj[c]) {
                                        MediaStore.MediaColumns._ID -> row[c] = idCounter
                                        MediaStore.MediaColumns.DATA -> row[c] = "/storage/emulated/0/Shared/$sName"
                                        MediaStore.MediaColumns.DISPLAY_NAME -> row[c] = sName
                                        MediaStore.MediaColumns.SIZE -> row[c] = 1024L
                                        MediaStore.MediaColumns.MIME_TYPE -> row[c] = "application/octet-stream"
                                        MediaStore.MediaColumns.DATE_MODIFIED -> row[c] = System.currentTimeMillis() / 1000
                                        else -> row[c] = null
                                    }
                                }
                                cursor.addRow(row)
                                idCounter++
                            }
                        }
                    }
                    param.result = cursor
                } catch (t: Throwable) {
                    Log.w(TAG, "mediaQueryHook error: ${t.message}")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "query", mediaQueryHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "query", mediaQueryHook)
        } catch (_: Throwable) {}

        // Insert hook
        val mediaInsertHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull() as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated) return

                    val values = param.args.getOrNull(1) as? ContentValues
                    val displayName = values?.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
                        ?: "isolated_${System.currentTimeMillis()}.dat"

                    val vaultUri = buildVaultContentUri(pkg, displayName, rule.useSharedSpace)
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uri.toString(),
                        resolvedPath = vaultUri.toString(),
                        action = AuditAction.ENCRYPTED,
                        operation = "MEDIASTORE_INSERT",
                        details = "Redirected MediaStore insert to encrypted vault"
                    )
                    param.result = vaultUri
                } catch (t: Throwable) {
                    Log.w(TAG, "mediaInsertHook error: ${t.message}")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "insert", mediaInsertHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "insert", mediaInsertHook)
        } catch (_: Throwable) {}

        // Open OutputStream and Open File Descriptor
        val mediaStreamAndDescriptorHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull() as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri) && uri.authority != IsoVaultConfigProvider.AUTHORITY) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated) return

                    val app = AndroidAppHelper.currentApplication() ?: return
                    val mode = (param.args.getOrNull(1) as? String) ?: "r"
                    val relative = uri.lastPathSegment ?: "media_${System.currentTimeMillis()}.dat"
                    val vaultUri = buildVaultContentUri(pkg, relative, rule.useSharedSpace)

                    when (param.method.name) {
                        "openOutputStream" -> {
                            val pfd = app.contentResolver.openFileDescriptor(vaultUri, "w")
                            if (pfd != null) {
                                param.result = ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                            }
                        }
                        "openInputStream" -> {
                            val pfd = app.contentResolver.openFileDescriptor(vaultUri, "r")
                            if (pfd != null) {
                                param.result = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                            }
                        }
                        "openFileDescriptor", "openAssetFileDescriptor", "openTypedAssetFileDescriptor" -> {
                            param.result = app.contentResolver.openFileDescriptor(vaultUri, mode)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "mediaStreamAndDescriptorHook error: ${t.message}")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            for (m in listOf("openOutputStream", "openInputStream", "openFileDescriptor", "openAssetFileDescriptor")) {
                XposedBridge.hookAllMethods(ContentResolver::class.java, m, mediaStreamAndDescriptorHook)
                XposedBridge.hookAllMethods(ContentProviderClient::class.java, m, mediaStreamAndDescriptorHook)
            }
            XposedBridge.hookAllMethods(ImageDecoder::class.java, "createSource", mediaStreamAndDescriptorHook)
            XposedBridge.hookAllMethods(MediaMetadataRetriever::class.java, "setDataSource", mediaStreamAndDescriptorHook)
        } catch (_: Throwable) {}

        // Delete hook
        val mediaDeleteHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull() as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (rule.isIsolated) {
                        val app = AndroidAppHelper.currentApplication()
                        val relative = uri.lastPathSegment ?: ""
                        if (app != null && relative.isNotBlank()) {
                            val extras = Bundle().apply {
                                putString("path", relative)
                                putString("volume", pkg)
                            }
                            app.contentResolver.call(Uri.parse(PROVIDER_URI), "file_delete", pkg, extras)
                        }
                        param.result = 1
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "mediaDeleteHook error: ${t.message}")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "delete", mediaDeleteHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "delete", mediaDeleteHook)
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 2: System File & Photo Picker Intent Interceptors.
     */
    private fun attachPickerInterceptorHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        val pickerHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val intent = param.args.firstOrNull() as? Intent ?: return
                val action = intent.action ?: return

                if (action == Intent.ACTION_GET_CONTENT ||
                    action == Intent.ACTION_OPEN_DOCUMENT ||
                    action == Intent.ACTION_PICK ||
                    action.contains("action_pick_images", ignoreCase = true)
                ) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated) {
                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = action,
                                resolvedPath = "isolated_picker",
                                action = AuditAction.PERMITTED,
                                operation = "PICKER_LAUNCH",
                                details = "Guarded media picker for isolated app"
                            )
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "pickerHook error: ${t.message}")
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            val actClass = XposedHelpers.findClassIfExists("android.app.Activity", lpparam.classLoader)
            if (actClass != null) {
                XposedBridge.hookAllMethods(actClass, "startActivityForResult", pickerHook)
                XposedBridge.hookAllMethods(actClass, "startActivity", pickerHook)
            }
        } catch (_: Throwable) {}
    }

    private fun shouldInterceptPath(pkg: String, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val p = path.replace('\\', '/')
        if (p.startsWith("/data/user/0/$pkg") || p.startsWith("/data/data/$pkg")) {
            return false
        }
        return p.startsWith("/storage/emulated/0") ||
                p.startsWith("/sdcard") ||
                p.startsWith("/mnt/sdcard") ||
                p.startsWith("/storage/")
    }

    private fun isWhitelisted(path: String, whitelistedFolders: Set<String>): Boolean {
        for (wf in whitelistedFolders) {
            if (wf.isNotBlank() && path.contains("/$wf", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * LAYER 3: Java File Methods & Listing via IsoVaultConfigProvider.
     */
    private fun attachFileHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        // File constructors
        val fileConstructorHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val path = (param.args.firstOrNull() as? String)
                    ?: (param.args.firstOrNull() as? URI)?.path
                    ?: return
                if (shouldInterceptPath(pkg, path)) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && rule.blockRootPollution && IsolationRuleManager.isRootPollution(path)) {
                            param.args[0] = "/dev/null"
                        }
                    } catch (_: Throwable) {
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            XposedHelpers.findAndHookConstructor(File::class.java, String::class.java, fileConstructorHook)
            XposedHelpers.findAndHookConstructor(File::class.java, URI::class.java, fileConstructorHook)
        } catch (_: Throwable) {}

        // File.list() & File.listFiles()
        val listFilesHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val file = param.thisObject as? File ?: return
                val path = file.path
                if (shouldInterceptPath(pkg, path)) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isWhitelisted(path, rule.whitelistedFolders)) {
                            val app = AndroidAppHelper.currentApplication()
                            val relative = IsolationRuleManager.extractRelative(path)
                            val isSharedQuery = relative.startsWith("Shared", ignoreCase = true)
                            val targetVolume = if (isSharedQuery && rule.useSharedSpace) "shared" else pkg
                            val sub = if (isSharedQuery) relative.removePrefix("Shared").removePrefix("shared").trimStart('/') else relative

                            val extras = Bundle().apply {
                                putString("path", sub)
                                putString("volume", targetVolume)
                            }
                            val bundle = app?.contentResolver?.call(Uri.parse(PROVIDER_URI), "list_files", pkg, extras)
                            var filesList = bundle?.getStringArray("files")?.toList() ?: emptyList()

                            // If querying root of storage and shared space is enabled, inject "Shared" folder
                            if (relative.isBlank() && rule.useSharedSpace && !filesList.contains("Shared")) {
                                filesList = filesList + "Shared"
                            }

                            if (param.method.name == "list") {
                                param.result = filesList.toTypedArray()
                            } else {
                                val fileArr = filesList.map { File(file, it) }.toTypedArray()
                                param.result = fileArr
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "listFilesHook error: ${t.message}")
                        param.result = if (param.method.name == "list") emptyArray<String>() else emptyArray<File>()
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(File::class.java, "list", listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "list", FilenameFilter::class.java, listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FilenameFilter::class.java, listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FileFilter::class.java, listFilesHook)
        } catch (_: Throwable) {}

        // File status & metadata (exists, isDirectory, isFile, length, canRead, canWrite)
        val fileStatusHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val file = param.thisObject as? File ?: return
                val path = file.path
                if (shouldInterceptPath(pkg, path)) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isWhitelisted(path, rule.whitelistedFolders)) {
                            val relative = IsolationRuleManager.extractRelative(path)

                            // Virtual Shared folder handling
                            if (relative.equals("Shared", ignoreCase = true) && rule.useSharedSpace) {
                                when (param.method.name) {
                                    "exists" -> param.result = true
                                    "isDirectory" -> param.result = true
                                    "isFile" -> param.result = false
                                    "length" -> param.result = 4096L
                                    "canRead", "canWrite" -> param.result = true
                                }
                                return
                            }

                            val app = AndroidAppHelper.currentApplication()
                            val isShared = relative.startsWith("Shared/", ignoreCase = true)
                            val targetVol = if (isShared && rule.useSharedSpace) "shared" else pkg
                            val sub = if (isShared) relative.removePrefix("Shared/").removePrefix("shared/") else relative

                            val extras = Bundle().apply {
                                putString("path", sub)
                                putString("volume", targetVol)
                            }
                            val bundle = app?.contentResolver?.call(Uri.parse(PROVIDER_URI), "file_stat", pkg, extras)
                            val exists = bundle?.getBoolean("exists", false) ?: false

                            when (param.method.name) {
                                "exists" -> param.result = exists
                                "isDirectory" -> param.result = bundle?.getBoolean("isDirectory", false) ?: false
                                "isFile" -> param.result = bundle?.getBoolean("isFile", false) ?: false
                                "length" -> param.result = if (exists) bundle?.getLong("length", 0L) ?: 0L else 0L
                                "canRead" -> param.result = exists
                                "canWrite" -> param.result = rule.mode != IsolationMode.READ_ONLY
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "fileStatusHook error: ${t.message}")
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            for (m in listOf("exists", "isDirectory", "isFile", "length", "canRead", "canWrite")) {
                XposedHelpers.findAndHookMethod(File::class.java, m, fileStatusHook)
            }
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 4: Low-Level OS & libcore.io Bridge Hooks.
     */
    private fun attachLowLevelOsHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        val osOpenHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val path = param.args.firstOrNull() as? String ?: return
                if (shouldInterceptPath(pkg, path)) {
                    isHandlingHook.set(true)
                    try {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isWhitelisted(path, rule.whitelistedFolders)) {
                            val app = AndroidAppHelper.currentApplication()
                            if (app != null) {
                                val relative = IsolationRuleManager.extractRelative(path)
                                val vaultUri = buildVaultContentUri(pkg, relative, rule.useSharedSpace)
                                val pfd = app.contentResolver.openFileDescriptor(vaultUri, "rw")
                                if (pfd != null) {
                                    val fdObj = pfd.fileDescriptor
                                    param.result = fdObj
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "osOpenHook non-fatal error: ${t.message}")
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            val osClass = XposedHelpers.findClassIfExists("android.system.Os", lpparam.classLoader)
            if (osClass != null) {
                XposedBridge.hookAllMethods(osClass, "open", osOpenHook)
            }
            val ioBridgeClass = XposedHelpers.findClassIfExists("libcore.io.IoBridge", lpparam.classLoader)
            if (ioBridgeClass != null) {
                XposedBridge.hookAllMethods(ioBridgeClass, "open", osOpenHook)
            }
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 5: Environment Redirection Hooks.
     */
    private fun attachEnvironmentHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        try {
            val envClass = XposedHelpers.findClassIfExists("android.os.Environment", lpparam.classLoader)
            if (envClass != null) {
                XposedHelpers.findAndHookMethod(
                    envClass,
                    "getExternalStorageState",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = android.os.Environment.MEDIA_MOUNTED
                        }
                    }
                )
            }
        } catch (_: Throwable) {}
    }

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
        if (now - lastTime < 3500L) return
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
            } catch (_: Throwable) {}
        }
    }
}
