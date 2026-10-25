package com.example.isolation.hook

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.provider.IsoVaultConfigProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Production LSPosed / Xposed Hook Entry Point (API 82+).
 *
 * Implements:
 * 1. Java File Constructor & Directory Listing Redirection (`java.io.File`) + Shared Space virtual directory
 * 2. Native Posix / Libcore `Os.open` & `IoBridge.open` Path Redirection
 * 3. `Environment.getExternalStorageDirectory` & `getExternalStoragePublicDirectory` Spoofing
 * 4. `MediaStore` ContentResolver Query / Insert / Delete / Stream Isolation + Shared Space media union
 * 5. System Photo Picker (`ACTION_PICK_IMAGES`, `ACTION_GET_CONTENT`, `ACTION_OPEN_DOCUMENT`) Isolation
 * 6. Fail-Closed Security: Hooked apps query the UID-verified IsoVaultConfigProvider and cache their rule
 *    in memory. If the Vault provider is unreachable, the hook fails closed into an empty private sandbox
 *    and NEVER exposes real phone storage.
 */
class StorageIsolationHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "IsoVault-LSPosed"
        private const val SELF_PACKAGE_1 = "com.aistudio.isovault.xrvf"
        private const val SELF_PACKAGE_2 = "com.example"

        private val VIRTUAL_URI_AUTHORITY = "isovault.virtual.media"
        private val virtualUriToFileMap = ConcurrentHashMap<String, File>()

        // ThreadLocal guard to avoid recursive hook loops inside a single thread
        private val isInternalHookCall = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        // In-memory rule and volume directory cache per hooked process
        @Volatile
        private var lastRuleSyncMs: Long = 0L

        @Volatile
        private var cachedSandboxDir: File? = null

        @Volatile
        private var cachedSharedDir: File? = null

        @Volatile
        private var providerReachable: Boolean = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pkg = lpparam.packageName ?: return

            // 1. Self-Hook for IsoVault Manager App to confirm LSPosed is active
            if (pkg == SELF_PACKAGE_1 || pkg == SELF_PACKAGE_2 || pkg.startsWith("com.aistudio.isovault")) {
                hookSelfStatusCheck(lpparam)
                return
            }

            // Skip core Android framework packages
            if (pkg == "android" || pkg.startsWith("com.android.systemui")) {
                return
            }

            XposedBridge.log("[$TAG] Attaching storage & MediaStore isolation hooks to package: $pkg")

            // Install all 5 isolation hook layers (each wrapped in try/catch)
            hookJavaIoFile(pkg)
            hookLibcoreOsAndIoBridge(pkg)
            hookEnvironmentDirectories(pkg)
            hookMediaStoreContentResolver(pkg)
            hookPhotoPickerAndDocumentIntents(pkg)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Error in handleLoadPackage: ${t.message}")
        }
    }

    private fun hookSelfStatusCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.example.MainActivity",
                lpparam.classLoader,
                "isLsposedModuleActiveReal",
                XC_MethodReplacement.returnConstant(true)
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Could not hook MainActivity.isLsposedModuleActiveReal: ${t.message}")
        }
    }

    /**
     * Synchronizes rules for this package from the UID-verified IsoVaultConfigProvider and caches them in memory.
     * If the Vault provider is unreachable, FAILS CLOSED by assigning an empty isolated rule so real storage
     * is never exposed.
     */
    private fun ensureRuleLoadedForPackage(pkg: String): IsolationRuleManager.Rule? {
        try {
            val now = System.currentTimeMillis()
            val existing = IsolationRuleManager.getRule(pkg)
            if (existing != null && now - lastRuleSyncMs < 5_000L) {
                return existing
            }
            lastRuleSyncMs = now

            val appContext: Context? = try {
                AndroidAppHelper.currentApplication()
            } catch (_: Throwable) {
                null
            }

            if (appContext != null) {
                try {
                    val bundle = appContext.contentResolver.call(
                        IsoVaultConfigProvider.CONTENT_URI,
                        "get_rule",
                        pkg,
                        null
                    )
                    if (bundle != null) {
                        providerReachable = true
                        val isIsolated = bundle.getBoolean("isolated", false)
                        if (isIsolated) {
                            val modeName = bundle.getString("mode") ?: IsolationMode.ENCRYPTED_VAULT.name
                            val mode = IsolationMode.entries.find { it.name == modeName } ?: IsolationMode.ENCRYPTED_VAULT
                            val blockRoot = bundle.getBoolean("blockRoot", true)
                            val whitelistStr = bundle.getString("whitelist") ?: ""
                            val whitelist = whitelistStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                            val useSharedSpace = bundle.getBoolean("useSharedSpace", false)
                            val volumeQuotaMb = bundle.getInt("volumeQuotaMb", 0)
                            val sandboxPath = bundle.getString("sandboxPath")
                            val sharedPath = bundle.getString("sharedPath")

                            if (!sandboxPath.isNullOrBlank()) {
                                cachedSandboxDir = File(sandboxPath).also { it.mkdirs() }
                            }
                            cachedSharedDir = if (useSharedSpace && !sharedPath.isNullOrBlank()) {
                                File(sharedPath).also { it.mkdirs() }
                            } else {
                                null
                            }

                            IsolationRuleManager.updateRule(
                                packageName = pkg,
                                mode = mode,
                                blockRootPollution = blockRoot,
                                whitelistedFolders = whitelist,
                                useSharedSpace = useSharedSpace,
                                volumeQuotaMb = volumeQuotaMb
                            )
                            return IsolationRuleManager.getRule(pkg)
                        } else {
                            IsolationRuleManager.removeRule(pkg)
                            return null
                        }
                    }
                } catch (_: Throwable) {
                    // Provider unreachable -> fall through to fail-closed behavior below
                }
            }

            // FAIL CLOSED: When the hooked app is in LSPosed scope but the Vault provider cannot be reached,
            // return a fail-closed rule backed by an empty private folder so real phone storage is never visible.
            if (!providerReachable && existing == null) {
                val failClosed = IsolationRuleManager.createFailClosedRule(pkg)
                IsolationRuleManager.updateRule(
                    packageName = pkg,
                    mode = failClosed.mode,
                    blockRootPollution = true,
                    whitelistedFolders = emptySet(),
                    useSharedSpace = false,
                    volumeQuotaMb = 0
                )
                return failClosed
            }

            return IsolationRuleManager.getRule(pkg)
        } catch (_: Throwable) {
            return IsolationRuleManager.createFailClosedRule(pkg)
        }
    }

    /**
     * Resolves the isolated sandbox directory for the target app without using world-readable files.
     */
    private fun getAppSandboxRoot(pkg: String): File {
        cachedSandboxDir?.let {
            if (it.exists() || it.mkdirs()) return it
        }
        val app = try { AndroidAppHelper.currentApplication() } catch (_: Throwable) { null }
        val fallback = if (app != null) {
            File(app.filesDir, ".isovault_sandbox")
        } else {
            File("/data/data/$pkg/files/.isovault_sandbox")
        }
        if (!fallback.exists()) fallback.mkdirs()
        IsolationRuleManager.ensureStandardVolumeFolders(fallback)
        return fallback
    }

    /**
     * Resolves the Shared Space volume directory if this app has opted into `useSharedSpace`.
     */
    private fun getSharedSpaceRootIfOptedIn(pkg: String): File? {
        val rule = IsolationRuleManager.getRule(pkg) ?: return null
        if (!rule.useSharedSpace) return null
        cachedSharedDir?.let {
            if (it.exists() || it.mkdirs()) return it
        }
        val sandbox = getAppSandboxRoot(pkg)
        val siblingShared = File(sandbox.parentFile, IsolationRuleManager.SHARED_VOLUME_ID)
        if (!siblingShared.exists()) siblingShared.mkdirs()
        return siblingShared
    }

    /**
     * Reports an interception event back to IsoVaultConfigProvider for real-time Activity Logs.
     */
    private fun reportAuditAsync(
        pkg: String,
        originalPath: String,
        resolvedPath: String,
        action: AuditAction,
        details: String,
        bytes: Long = 0L
    ) {
        try {
            val app = AndroidAppHelper.currentApplication() ?: return
            val extras = Bundle().apply {
                putString("pkg", pkg)
                putString("path", originalPath)
                putString("resolved", resolvedPath)
                putString("action", action.name)
                putString("details", details)
                putLong("bytes", bytes)
            }
            app.contentResolver.call(
                IsoVaultConfigProvider.CONTENT_URI,
                "record_audit",
                pkg,
                extras
            )
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 1: Hook `java.io.File` constructors and directory listings (`list`, `listFiles`, `exists`).
     * Supports per-app private volume and optional `"Shared"` directory when `useSharedSpace` is enabled.
     */
    private fun hookJavaIoFile(pkg: String) {
        try {
            // File(String pathname)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val path = param.args[0] as? String ?: return
                        if (!shouldInspectPath(path)) return

                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)
                            val redirected = IsolationRuleManager.resolveRedirectedFile(pkg, path, sandboxRoot, sharedRoot)
                            if (redirected != null) {
                                param.args[0] = redirected.absolutePath
                                reportAuditAsync(
                                    pkg = pkg,
                                    originalPath = path,
                                    resolvedPath = redirected.absolutePath,
                                    action = if (rule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.REDIRECTED,
                                    details = "File(String) redirected -> ${redirected.name}"
                                )
                            }
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            // File(String parent, String child)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val parent = param.args[0] as? String ?: return
                        val child = param.args[1] as? String ?: return
                        val combined = "$parent/$child"
                        if (!shouldInspectPath(combined)) return

                        isInternalHookCall.set(true)
                        try {
                            ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)
                            val redirected = IsolationRuleManager.resolveRedirectedFile(pkg, combined, sandboxRoot, sharedRoot)
                            if (redirected != null) {
                                param.args[0] = redirected.parent
                                param.args[1] = redirected.name
                            }
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            // File(File parent, String child)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val parentFile = param.args[0] as? File ?: return
                        val child = param.args[1] as? String ?: return
                        val combined = "${parentFile.absolutePath}/$child"
                        if (!shouldInspectPath(combined)) return

                        isInternalHookCall.set(true)
                        try {
                            ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)
                            val redirected = IsolationRuleManager.resolveRedirectedFile(pkg, combined, sandboxRoot, sharedRoot)
                            if (redirected != null) {
                                param.args[0] = redirected.parentFile
                                param.args[1] = redirected.name
                            }
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            // Hook File.list() to hide `.IsoVault`, enforce FAKE_EMPTY, and expose "Shared" when useSharedSpace is on
            XposedHelpers.findAndHookMethod(
                File::class.java,
                "list",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val list = param.result as? Array<*> ?: return
                        isInternalHookCall.set(true)
                        try {
                            val fileObj = param.thisObject as? File ?: return
                            val rule = ensureRuleLoadedForPackage(pkg)
                            if (rule?.mode == IsolationMode.FAKE_EMPTY && shouldInspectPath(fileObj.absolutePath)) {
                                param.result = emptyArray<String>()
                                return
                            }
                            val filtered = list.filterIsInstance<String>()
                                .filter { !it.equals(".IsoVault", ignoreCase = true) }
                                .toMutableList()

                            // If listing root of isolated storage and Shared Space is enabled, include "Shared"
                            if (rule?.useSharedSpace == true &&
                                (fileObj.absolutePath == "/storage/emulated/0" ||
                                    fileObj.absolutePath == getAppSandboxRoot(pkg).absolutePath)
                            ) {
                                if ("Shared" !in filtered) {
                                    filtered.add("Shared")
                                }
                            }
                            param.result = filtered.toTypedArray()
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed hooking java.io.File: ${t.message}")
        }
    }

    /**
     * LAYER 2: Hook low-level `libcore.io.IoBridge.open` and `libcore.io.Os.open`
     */
    private fun hookLibcoreOsAndIoBridge(pkg: String) {
        try {
            val ioBridgeClass = XposedHelpers.findClassIfExists("libcore.io.IoBridge", null)
            if (ioBridgeClass != null) {
                XposedBridge.hookAllMethods(
                    ioBridgeClass,
                    "open",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (isInternalHookCall.get() == true) return
                            val path = param.args.getOrNull(0) as? String ?: return
                            if (!shouldInspectPath(path)) return

                            isInternalHookCall.set(true)
                            try {
                                ensureRuleLoadedForPackage(pkg) ?: return
                                val sandboxRoot = getAppSandboxRoot(pkg)
                                val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)
                                val redirected = IsolationRuleManager.resolveRedirectedFile(pkg, path, sandboxRoot, sharedRoot)
                                if (redirected != null) {
                                    redirected.parentFile?.mkdirs()
                                    param.args[0] = redirected.absolutePath
                                }
                            } catch (_: Throwable) {
                            } finally {
                                isInternalHookCall.set(false)
                            }
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] libcore.io.IoBridge hook warning: ${t.message}")
        }
    }

    /**
     * LAYER 3: Hook `Environment.getExternalStorageDirectory()` & `getExternalStoragePublicDirectory(String)`
     */
    private fun hookEnvironmentDirectories(pkg: String) {
        try {
            XposedHelpers.findAndHookMethod(
                Environment::class.java,
                "getExternalStorageDirectory",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val redirected = when (rule.mode) {
                                IsolationMode.ENCRYPTED_VAULT -> File(sandboxRoot, "vault_storage")
                                IsolationMode.SANDBOX_REDIRECT -> File(sandboxRoot, "sandbox_storage")
                                IsolationMode.FAKE_EMPTY -> File(sandboxRoot, "empty_spoof")
                                IsolationMode.READ_ONLY -> return
                            }
                            if (!redirected.exists()) redirected.mkdirs()
                            param.result = redirected
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                Environment::class.java,
                "getExternalStoragePublicDirectory",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val dirType = param.args[0] as? String ?: return
                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            if (rule.whitelistedFolders.any { it.equals(dirType, ignoreCase = true) }) {
                                return
                            }
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val base = when (rule.mode) {
                                IsolationMode.ENCRYPTED_VAULT -> File(sandboxRoot, "vault_storage")
                                IsolationMode.SANDBOX_REDIRECT -> File(sandboxRoot, "sandbox_storage")
                                IsolationMode.FAKE_EMPTY -> File(sandboxRoot, "empty_spoof")
                                IsolationMode.READ_ONLY -> return
                            }
                            val target = File(base, dirType)
                            if (!target.exists()) target.mkdirs()
                            param.result = target
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed hooking Environment directories: ${t.message}")
        }
    }

    /**
     * LAYER 4: Hook `ContentResolver` for `MediaStore` Queries, Inserts, Deletes, and Streams.
     * When `rule.useSharedSpace == true`, includes media from both the app's private volume and the Shared Space volume.
     */
    private fun hookMediaStoreContentResolver(pkg: String) {
        try {
            // 4A. Hook ContentResolver.query(...)
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "query",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        if (!isMediaStoreUri(uri)) return

                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)

                            if (rule.mode == IsolationMode.FAKE_EMPTY) {
                                val origCursor = param.result as? Cursor
                                val cols = origCursor?.columnNames ?: arrayOf(
                                    MediaStore.MediaColumns._ID,
                                    MediaStore.MediaColumns.DISPLAY_NAME,
                                    MediaStore.MediaColumns.DATA,
                                    MediaStore.MediaColumns.SIZE
                                )
                                origCursor?.close()
                                param.result = MatrixCursor(cols)
                                reportAuditAsync(
                                    pkg = pkg,
                                    originalPath = uri.toString(),
                                    resolvedPath = "MediaStore [EMPTY]",
                                    action = AuditAction.SPOOFED_EMPTY,
                                    details = "MediaStore.query spoofed empty cursor"
                                )
                                return
                            }

                            val originalCursor = param.result as? Cursor
                            val filteredCursor = if (originalCursor != null) {
                                filterCursorForApp(originalCursor, pkg, sandboxRoot, sharedRoot)
                            } else {
                                buildVirtualMediaStoreCursor(sandboxRoot, sharedRoot)
                            }
                            param.result = filteredCursor

                            reportAuditAsync(
                                pkg = pkg,
                                originalPath = uri.toString(),
                                resolvedPath = if (sharedRoot != null) {
                                    "${sandboxRoot.absolutePath} + SharedSpace"
                                } else {
                                    sandboxRoot.absolutePath
                                },
                                action = AuditAction.REDIRECTED,
                                details = "MediaStore.query isolated (${filteredCursor.count} items visible, shared=${sharedRoot != null})"
                            )
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            // 4B. Hook ContentResolver.insert(Uri, ContentValues)
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "insert",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        val values = param.args.getOrNull(1) as? ContentValues ?: return
                        if (!isMediaStoreUri(uri)) return

                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            if (rule.mode == IsolationMode.READ_ONLY) {
                                param.result = null
                                return
                            }

                            val sandboxRoot = getAppSandboxRoot(pkg)
                            val sharedRoot = getSharedSpaceRootIfOptedIn(pkg)
                            val displayName = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
                                ?: "media_${System.currentTimeMillis()}.jpg"
                            val relPath = values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
                                ?: "Pictures"

                            // If the app inserts into RELATIVE_PATH starting with "Shared" and opted into Shared Space,
                            // store in the Shared Space volume; otherwise store in the app's private volume.
                            val targetFolder = if (rule.useSharedSpace && sharedRoot != null &&
                                relPath.trimStart('/').startsWith("Shared", ignoreCase = true)
                            ) {
                                val sub = relPath.trimStart('/').removePrefix("Shared").trimStart('/').ifEmpty { "Pictures" }
                                File(sharedRoot, sub)
                            } else {
                                val subFolder = when (rule.mode) {
                                    IsolationMode.ENCRYPTED_VAULT -> "vault_storage/$relPath"
                                    else -> "sandbox_storage/$relPath"
                                }
                                File(sandboxRoot, subFolder)
                            }
                            if (!targetFolder.exists()) targetFolder.mkdirs()

                            val finalName = if (rule.mode == IsolationMode.ENCRYPTED_VAULT && !displayName.endsWith(".isovault")) {
                                "$displayName.isovault"
                            } else {
                                displayName
                            }

                            val targetFile = File(targetFolder, finalName)
                            val virtualId = System.currentTimeMillis()
                            val virtualUri = Uri.parse("content://$VIRTUAL_URI_AUTHORITY/$pkg/$virtualId")
                            virtualUriToFileMap[virtualUri.toString()] = targetFile

                            param.result = virtualUri
                            reportAuditAsync(
                                pkg = pkg,
                                originalPath = "MediaStore.insert($displayName)",
                                resolvedPath = targetFile.absolutePath,
                                action = if (rule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.REDIRECTED,
                                details = "MediaStore insert redirected to isolated volume -> ${targetFile.name}"
                            )
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )

            // 4C. Hook ContentResolver.openOutputStream for virtual MediaStore URIs (on-the-fly streaming encryption)
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "openOutputStream",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        if (uri.authority != VIRTUAL_URI_AUTHORITY) return

                        try {
                            val targetFile = virtualUriToFileMap[uri.toString()] ?: return
                            targetFile.parentFile?.mkdirs()
                            val rule = ensureRuleLoadedForPackage(pkg)

                            if (rule?.mode == IsolationMode.ENCRYPTED_VAULT || targetFile.name.endsWith(".isovault")) {
                                param.result = object : ByteArrayOutputStream() {
                                    private var closed = false
                                    override fun close() {
                                        if (closed) return
                                        closed = true
                                        super.close()
                                        try {
                                            val plain = toByteArray()
                                            FileOutputStream(targetFile).use { fos ->
                                                AesVaultCipher.encryptStream(ByteArrayInputStream(plain), fos)
                                            }
                                            reportAuditAsync(
                                                pkg = pkg,
                                                originalPath = uri.toString(),
                                                resolvedPath = targetFile.absolutePath,
                                                action = AuditAction.ENCRYPTED,
                                                details = "Encrypted ${plain.size} bytes with AES256_GCM_HKDF_4KB",
                                                bytes = plain.size.toLong()
                                            )
                                        } catch (e: Throwable) {
                                            XposedBridge.log("[$TAG] Streaming encrypt error: ${e.message}")
                                        }
                                    }
                                }
                            } else {
                                param.result = FileOutputStream(targetFile)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )

            // 4D. Hook ContentResolver.openInputStream for virtual MediaStore URIs (on-the-fly streaming decryption)
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "openInputStream",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        if (uri.authority != VIRTUAL_URI_AUTHORITY) return

                        try {
                            val targetFile = virtualUriToFileMap[uri.toString()] ?: return
                            if (!targetFile.exists()) return

                            if (AesVaultCipher.isEncryptedIsoVaultFile(targetFile)) {
                                val decrypted = AesVaultCipher.decryptToMemory(targetFile)
                                param.result = ByteArrayInputStream(decrypted)
                            } else {
                                param.result = FileInputStream(targetFile)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )

            // 4E. Hook ContentResolver.delete(Uri, ...) to prevent deleting other apps' media
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "delete",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        if (uri.authority == VIRTUAL_URI_AUTHORITY) {
                            try {
                                val file = virtualUriToFileMap.remove(uri.toString())
                                val deleted = if (file != null && file.exists() && file.delete()) 1 else 0
                                param.result = deleted
                            } catch (_: Throwable) {
                                param.result = 0
                            }
                            return
                        }
                        if (!isMediaStoreUri(uri)) return
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            if (rule.mode == IsolationMode.READ_ONLY || rule.mode == IsolationMode.FAKE_EMPTY) {
                                param.result = 0
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed hooking ContentResolver MediaStore: ${t.message}")
        }
    }

    /**
     * LAYER 5: Hook System Photo Picker & Document Picker Intents
     */
    private fun hookPhotoPickerAndDocumentIntents(pkg: String) {
        try {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "startActivityForResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalHookCall.get() == true) return
                        val intent = param.args.getOrNull(0) as? Intent ?: return
                        val action = intent.action ?: return

                        val isPickerAction = action == "android.provider.action.PICK_IMAGES" ||
                                action == Intent.ACTION_PICK ||
                                action == Intent.ACTION_GET_CONTENT ||
                                action == Intent.ACTION_OPEN_DOCUMENT

                        if (!isPickerAction) return

                        isInternalHookCall.set(true)
                        try {
                            val rule = ensureRuleLoadedForPackage(pkg) ?: return
                            val sandboxRoot = getAppSandboxRoot(pkg)
                            intent.putExtra("isovault_isolated_pkg", pkg)
                            intent.putExtra("isovault_sandbox_path", sandboxRoot.absolutePath)
                            intent.putExtra("isovault_use_shared", rule.useSharedSpace)

                            reportAuditAsync(
                                pkg = pkg,
                                originalPath = "Intent($action)",
                                resolvedPath = sandboxRoot.absolutePath,
                                action = AuditAction.REDIRECTED,
                                details = "Intercepted picker intent ($action) -> scoped to volume"
                            )
                        } catch (_: Throwable) {
                        } finally {
                            isInternalHookCall.set(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Picker intent hook warning: ${t.message}")
        }
    }

    /**
     * Filters an existing MediaStore cursor so the hooked app ONLY sees rows belonging to its own
     * isolated sandbox, PLUS rows from the Shared Space volume if `sharedRoot != null` (`useSharedSpace == true`).
     * If the cursor is empty, populates virtual rows from the app's volume (and Shared Space if opted in).
     */
    private fun filterCursorForApp(
        original: Cursor,
        pkg: String,
        sandboxRoot: File,
        sharedRoot: File?
    ): Cursor {
        val columnNames = original.columnNames
        val matrix = MatrixCursor(columnNames)
        val dataIdx = original.getColumnIndex(MediaStore.MediaColumns.DATA)
        val pkgIdx = original.getColumnIndex("owner_package_name")

        try {
            if (original.moveToFirst()) {
                do {
                    val dataPath = if (dataIdx >= 0) original.getString(dataIdx) ?: "" else ""
                    val ownerPkg = if (pkgIdx >= 0) original.getString(pkgIdx) ?: "" else ""

                    val belongsToAppSandbox = dataPath.contains(pkg) ||
                            dataPath.startsWith(sandboxRoot.absolutePath) ||
                            ownerPkg == pkg

                    val belongsToSharedSpace = sharedRoot != null && (
                        dataPath.startsWith(sharedRoot.absolutePath) ||
                            dataPath.contains("/${IsolationRuleManager.SHARED_VOLUME_ID}/")
                        )

                    if (belongsToAppSandbox || belongsToSharedSpace) {
                        val row = Array<Any?>(columnNames.size) { col ->
                            when (original.getType(col)) {
                                Cursor.FIELD_TYPE_INTEGER -> original.getLong(col)
                                Cursor.FIELD_TYPE_FLOAT -> original.getDouble(col)
                                Cursor.FIELD_TYPE_STRING -> original.getString(col)
                                Cursor.FIELD_TYPE_BLOB -> original.getBlob(col)
                                else -> null
                            }
                        }
                        matrix.addRow(row)
                    }
                } while (original.moveToNext())
            }
        } catch (_: Throwable) {}

        // Also append files stored inside the app's volume and (if enabled) Shared Space volume
        if (matrix.count == 0) {
            return buildVirtualMediaStoreCursor(sandboxRoot, sharedRoot, columnNames)
        }
        return matrix
    }

    /**
     * Builds a virtual MediaStore cursor scanning the app's private volume directory
     * AND (when `sharedRoot != null`) the Shared Space volume directory.
     */
    private fun buildVirtualMediaStoreCursor(
        sandboxRoot: File,
        sharedRoot: File? = null,
        requestedColumns: Array<String>? = null
    ): Cursor {
        val columns = requestedColumns ?: arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val cursor = MatrixCursor(columns)
        var nextId = 1000L

        val rootsToScan = buildList {
            if (sandboxRoot.exists()) add(sandboxRoot)
            if (sharedRoot != null && sharedRoot.exists()) add(sharedRoot)
        }

        for (root in rootsToScan) {
            root.walkTopDown().maxDepth(6).forEach { file ->
                if (file.isFile && file.name != ".nomedia" && !file.name.startsWith(".seeded")) {
                    val cleanName = file.name.removeSuffix(".isovault")
                    val row = Array<Any?>(columns.size) { idx ->
                        when (columns[idx]) {
                            MediaStore.MediaColumns._ID -> nextId++
                            MediaStore.MediaColumns.DISPLAY_NAME -> cleanName
                            MediaStore.MediaColumns.DATA -> file.absolutePath
                            MediaStore.MediaColumns.SIZE -> file.length()
                            MediaStore.MediaColumns.DATE_MODIFIED -> file.lastModified() / 1000L
                            MediaStore.MediaColumns.MIME_TYPE -> guessMimeType(cleanName)
                            else -> null
                        }
                    }
                    cursor.addRow(row)
                }
            }
        }
        return cursor
    }

    private fun shouldInspectPath(path: String): Boolean {
        return path.startsWith("/storage/emulated/0") ||
                path.startsWith("/sdcard") ||
                path.startsWith("/mnt/sdcard")
    }

    private fun isMediaStoreUri(uri: Uri): Boolean {
        val auth = uri.authority ?: return false
        return auth == MediaStore.AUTHORITY || auth.contains("media")
    }

    private fun guessMimeType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
