package com.example.isolation.hook

import android.app.Activity
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.CursorWindow
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * IsoVault Official LSPosed & JingMatrix/Vector Storage Isolation Engine.
 * Loaded by Vector/LSPosed via assets/xposed_init.
 *
 * Implements true virtual storage isolation:
 * - A dedicated isolated sandbox directory (/data/user/0/<pkg>/files/IsoVault_Sandbox)
 *   is mounted as the app's entire external storage (/storage/emulated/0).
 * - Apps can freely create folders, files, and save media without errors ("Failed to save" fixed!).
 * - MediaStore insertions (ContentResolver.insert) save directly into the sandbox and return virtual URIs.
 * - MediaStore queries return only the files saved within the app's isolated sandbox (0 real phone photos leaked).
 * - Media reads and thumbnail decoders stream sandbox media directly while blocking access to real phone media.
 * - System Photo/Document Pickers are intercepted and replaced with an in-app IsoVault Isolated Picker.
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "IsoVaultHook"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"
        const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"

        // Re-entrancy guard to prevent recursion inside File/ContentResolver/OS hooks
        private val isHandlingHook = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        // Temporary bypass guard for user-initiated safe import actions
        private val isTemporarilyBypassingPicker = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        // Background executor for non-blocking audit IPC calls to IsoVault Manager
        private val auditExecutor = Executors.newSingleThreadExecutor()
        private val recentAuditCache = ConcurrentHashMap<String, Long>()

        // Fast in-memory rule cache
        @Volatile
        private var cachedRule: IsolationRuleManager.AppRule? = null
        @Volatile
        private var lastRuleFetchMs: Long = 0L

        // Fast thread-safe cache mapping virtual media ID -> actual File in isolated sandbox
        private val virtualMediaMap = ConcurrentHashMap<Long, File>()
        // Fast thread-safe cache mapping content URI string -> actual File in isolated sandbox
        private val virtualUriMap = ConcurrentHashMap<String, File>()
        private val nextVirtualId = AtomicLong(100_000L)

        // Maps pending safe import request codes to original request codes
        private val pendingImportMap = ConcurrentHashMap<Int, Int>()
        private const val SAFE_IMPORT_REQUEST_BASE = 8990

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
            "duration",
            "media_type",
            "is_pending",
            "is_trashed",
            "is_favorite",
            "generation_added",
            "generation_modified",
            "volume_name",
            "owner_package_name",
            "title",
            "album",
            "artist",
            "resolution",
            "is_drm",
            "parent",
            "format",
            "storage_id"
        )
    }

    /**
     * Crash-proof empty MatrixCursor for when the sandbox has 0 media files.
     * Prevents IllegalArgumentException in apps (e.g. Snapchat) that call getColumnIndexOrThrow before moveToFirst().
     */
    private class SafeEmptyMatrixCursor(projection: Array<String>) : MatrixCursor(projection, 0) {
        override fun getCount(): Int = 0

        override fun getColumnIndex(columnName: String?): Int {
            if (columnName == null) return 0
            val idx = super.getColumnIndex(columnName)
            return if (idx >= 0) idx else 0
        }

        override fun getColumnIndexOrThrow(columnName: String?): Int {
            if (columnName == null) return 0
            val idx = super.getColumnIndex(columnName)
            return if (idx >= 0) idx else 0
        }

        override fun getExtras(): Bundle = Bundle.EMPTY
    }

    /**
     * Crash-proof MatrixCursor populated with the isolated sandbox's media files.
     */
    private class SafeSandboxMatrixCursor(projection: Array<String>) : MatrixCursor(projection) {
        override fun getColumnIndex(columnName: String?): Int {
            if (columnName == null) return 0
            val idx = super.getColumnIndex(columnName)
            return if (idx >= 0) idx else 0
        }

        override fun getColumnIndexOrThrow(columnName: String?): Int {
            if (columnName == null) return 0
            val idx = super.getColumnIndex(columnName)
            return if (idx >= 0) idx else 0
        }

        override fun getExtras(): Bundle = Bundle.EMPTY
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

        // Ignore core system packages
        if (pkg == "android" || pkg.startsWith("com.android.systemui")) {
            return
        }

        // Self-hook for IsoVault Manager App to confirm module status
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

        val rule = resolveRuleForPackage(pkg)
        if (!rule.isIsolated) {
            XposedBridge.log("[IsoVault] Skipping $pkg (explicitly disabled in IsoVault settings)")
            return
        }

        XposedBridge.log("[IsoVault] >>> ACTIVATING FULL ISOLATION FOR: $pkg (Mode: ${rule.mode}) <<<")
        Log.i(TAG, "[IsoVault] Activating full isolation for $pkg (Mode: ${rule.mode})")

        try {
            attachMediaStoreAndProviderHooks(lpparam, pkg)
            attachPickerInterceptorHooks(lpparam, pkg)
            attachSnapchatAndGallerySqliteHooks(lpparam, pkg)
            attachFileHooks(lpparam, pkg, rule)
            attachLowLevelOsHooks(lpparam, pkg, rule)
            attachEnvironmentHooks(lpparam, pkg, rule)
        } catch (t: Throwable) {
            XposedBridge.log("[IsoVault] Error attaching hooks to $pkg: ${t.message}")
            Log.e(TAG, "Error attaching hooks to $pkg", t)
        }
    }

    private fun registerSandboxMedia(file: File): Long {
        for ((id, f) in virtualMediaMap) {
            if (f.absolutePath == file.absolutePath) {
                return id
            }
        }
        val id = nextVirtualId.incrementAndGet()
        virtualMediaMap[id] = file
        val uriStr = "content://media/external/images/media/$id"
        virtualUriMap[uriStr] = file
        virtualUriMap["content://media/external/video/media/$id"] = file
        virtualUriMap["content://media/external/audio/media/$id"] = file
        virtualUriMap["content://media/external/downloads/$id"] = file
        virtualUriMap["content://media/external/file/$id"] = file
        return id
    }

    private fun resolveRuleForPackage(pkg: String): IsolationRuleManager.AppRule {
        val now = System.currentTimeMillis()
        val existing = cachedRule
        if (existing != null && existing.packageName == pkg && (now - lastRuleFetchMs) < 2500L) {
            return existing
        }

        val resolved = fetchRuleInternal(pkg)
        cachedRule = resolved
        lastRuleFetchMs = now
        return resolved
    }

    private fun fetchRuleInternal(pkg: String): IsolationRuleManager.AppRule {
        // 1. Try live IPC call to IsoVaultConfigProvider
        try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) {
                val bundle = app.contentResolver.call(
                    Uri.parse(PROVIDER_URI),
                    "check_isolated",
                    pkg,
                    null
                )
                if (bundle != null && bundle.getBoolean("isConfigured", false)) {
                    val explicitlyDisabled = bundle.getBoolean("explicitlyDisabled", false)
                    val isolated = bundle.getBoolean("isIsolated", true) && !explicitlyDisabled
                    val modeStr = bundle.getString("mode", "ENCRYPTED_VAULT") ?: "ENCRYPTED_VAULT"
                    val mode = try { IsolationMode.valueOf(modeStr) } catch (_: Exception) { IsolationMode.ENCRYPTED_VAULT }
                    val pollution = bundle.getBoolean("blockPollution", true)
                    val whitelistStr = bundle.getString("whitelistedFolders", "") ?: ""
                    val whitelist = whitelistStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    return IsolationRuleManager.AppRule(pkg, isolated, mode, pollution, whitelist)
                }
            }
        } catch (_: Throwable) {}

        // 2. Try XSharedPreferences
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
                val whitelistStr = prefs.getString("${pkg}_whitelist", "") ?: ""
                val whitelist = whitelistStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                return IsolationRuleManager.AppRule(pkg, isolated, mode, pollution, whitelist)
            }
        } catch (_: Throwable) {}

        // 3. Try external .IsoVault rule file
        IsolationRuleManager.readExternalRule(pkg)?.let { return it }

        // 4. Default: isolate with ENCRYPTED_VAULT
        return IsolationRuleManager.AppRule(
            packageName = pkg,
            isIsolated = true,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = emptySet()
        )
    }

    private fun isGalleryWhitelisted(rule: IsolationRuleManager.AppRule): Boolean {
        return rule.whitelistedFolders.any {
            it.equals("DCIM", ignoreCase = true) ||
                it.equals("Pictures", ignoreCase = true) ||
                it.equals("Camera", ignoreCase = true) ||
                it.equals("All", ignoreCase = true)
        }
    }

    private fun isMediaOrExternalDocumentUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme == "file") {
            val path = uri.path ?: return false
            return shouldInterceptPath("", path)
        }
        if (scheme != "content") return false
        val auth = uri.authority?.lowercase() ?: return false
        return auth == "media" ||
            auth.contains("media") ||
            auth.contains("photopicker") ||
            auth.contains("externalstorage") ||
            auth.contains("downloads") ||
            auth.contains("gallery") ||
            auth.contains("photos")
    }

    private fun isMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic",
            "mp4", "mkv", "3gp", "webm", "avi", "mov",
            "mp3", "m4a", "wav", "ogg", "aac", "flac"
        )
    }

    private fun getMimeTypeFromExtension(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/x-wav"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun guessExtension(uri: Uri): String {
        val path = uri.path ?: ""
        val last = path.substringAfterLast('/')
        if (last.contains('.')) {
            val ext = last.substringAfterLast('.').lowercase()
            if (ext.length in 2..4) return ext
        }
        val uriStr = uri.toString().lowercase()
        return when {
            uriStr.contains("video") -> "mp4"
            uriStr.contains("audio") -> "mp3"
            else -> "jpg"
        }
    }

    /**
     * LAYER 1: Full Virtual MediaStore & ContentProvider Engine.
     * 1. MediaStore.insert / openOutputStream:
     *    Creates files in the app's isolated sandbox and provides writable descriptors.
     *    Apps can save photos, videos, and downloads seamlessly without "Failed to save".
     * 2. MediaStore.query:
     *    Returns files present in the app's isolated sandbox.
     *    Camera roll in WhatsApp/Snapchat shows ONLY the app's isolated media (0 real phone photos).
     * 3. Media reads / thumbnails:
     *    Streams sandbox media to the app while blocking access to real phone media URIs.
     */
    private fun attachMediaStoreAndProviderHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        // A. MediaStore & ContentProvider QUERIES (Camera Roll)
        val mediaQueryHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return

                val uriIndex = param.args.indexOfFirst { it is Uri }
                if (uriIndex < 0) return
                val uri = param.args[uriIndex] as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                    if (isGalleryWhitelisted(rule)) return

                    val requestedProjection = param.args.getOrNull(uriIndex + 1) as? Array<*>
                    val mergedColumns = LinkedHashSet<String>()
                    requestedProjection?.forEach { col ->
                        if (col is String && col.isNotBlank()) mergedColumns.add(col)
                    }
                    DEFAULT_MEDIA_COLUMNS.forEach { mergedColumns.add(it) }

                    val sandboxRoot = getOrCreateSandboxRoot(pkg)
                    val mediaFiles = mutableListOf<File>()

                    // Scan sandbox directories for files saved by this isolated app
                    try {
                        val searchDirs = listOf(
                            File(sandboxRoot, "DCIM"),
                            File(sandboxRoot, "Pictures"),
                            File(sandboxRoot, "Movies"),
                            File(sandboxRoot, "Download"),
                            sandboxRoot
                        )
                        for (dir in searchDirs) {
                            if (dir.exists()) {
                                dir.walkTopDown()
                                    .maxDepth(3)
                                    .filter { it.isFile && isMediaFile(it) && !it.name.startsWith(".") }
                                    .take(150)
                                    .forEach { mediaFiles.add(it) }
                            }
                        }
                    } catch (_: Throwable) {}

                    if (mediaFiles.isEmpty()) {
                        param.result = SafeEmptyMatrixCursor(mergedColumns.toTypedArray())
                    } else {
                        val cursor = SafeSandboxMatrixCursor(mergedColumns.toTypedArray())
                        for (file in mediaFiles.distinctBy { it.absolutePath }) {
                            val virtualId = registerSandboxMedia(file)
                            val ext = file.extension.lowercase()
                            val mime = getMimeTypeFromExtension(ext)
                            val isVideo = mime.startsWith("video/")
                            val relDir = try {
                                file.parentFile?.relativeToOrSelf(sandboxRoot)?.path ?: "Pictures"
                            } catch (_: Throwable) { "Pictures" }

                            val row = arrayOfNulls<Any>(mergedColumns.size)
                            mergedColumns.forEachIndexed { index, col ->
                                row[index] = when (col) {
                                    "_id", "id" -> virtualId
                                    "_data", "data" -> file.absolutePath
                                    "_display_name", "display_name" -> file.name
                                    "_size", "size" -> file.length()
                                    "mime_type" -> mime
                                    "date_added" -> file.lastModified() / 1000
                                    "date_modified" -> file.lastModified() / 1000
                                    "datetaken" -> file.lastModified()
                                    "width" -> 1080
                                    "height" -> 1920
                                    "orientation" -> 0
                                    "bucket_id" -> relDir.hashCode()
                                    "bucket_display_name" -> file.parentFile?.name ?: "Pictures"
                                    "relative_path" -> if (relDir.endsWith("/")) relDir else "$relDir/"
                                    "duration" -> if (isVideo) 5000L else 0L
                                    "media_type" -> if (isVideo) 3 else 1
                                    "is_pending" -> 0
                                    "is_trashed" -> 0
                                    "is_favorite" -> 0
                                    "volume_name" -> "external_primary"
                                    "owner_package_name" -> pkg
                                    "title" -> file.nameWithoutExtension
                                    else -> null
                                }
                            }
                            cursor.addRow(row)
                        }
                        param.result = cursor
                    }

                    XposedBridge.log("[IsoVault] Media query in $pkg (${mediaFiles.size} sandbox files returned)")
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uri.toString(),
                        resolvedPath = "${sandboxRoot.absolutePath} (${mediaFiles.size} media)",
                        action = AuditAction.REDIRECTED,
                        operation = "CAMERA_ROLL_QUERY",
                        details = "Served ${mediaFiles.size} media items from isolated sandbox"
                    )
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "query", mediaQueryHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "query", mediaQueryHook)
            val proxyClass = XposedHelpers.findClass("android.content.ContentProviderProxy", null)
            XposedBridge.hookAllMethods(proxyClass, "query", mediaQueryHook)
        } catch (_: Throwable) {}

        // B. MediaStore & ContentResolver INSERT (Saving photos/videos/files)
        val mediaInsertHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull { it is Uri } as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return

                    val values = param.args.firstOrNull { it is ContentValues } as? ContentValues
                    val sandboxRoot = getOrCreateSandboxRoot(pkg)

                    val relPath = values?.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)?.trim('/')
                        ?: values?.getAsString("relative_path")?.trim('/')
                        ?: "Pictures"
                    val displayName = values?.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
                        ?: values?.getAsString("_display_name")
                        ?: "media_${System.currentTimeMillis()}.jpg"

                    val targetDir = File(sandboxRoot, relPath)
                    targetDir.mkdirs()
                    val targetFile = File(targetDir, displayName)
                    try { targetFile.createNewFile() } catch (_: Throwable) {}

                    val virtualId = registerSandboxMedia(targetFile)
                    val resultUri = Uri.parse("content://media/external/images/media/$virtualId")
                    virtualUriMap[resultUri.toString()] = targetFile

                    param.result = resultUri
                    XposedBridge.log("[IsoVault] Intercepted insert in $pkg -> sandbox file: ${targetFile.absolutePath}")
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uri.toString(),
                        resolvedPath = targetFile.absolutePath,
                        action = AuditAction.REDIRECTED,
                        operation = "MEDIA_INSERT",
                        details = "Created media entry inside isolated sandbox ($displayName)"
                    )
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "insert", mediaInsertHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "insert", mediaInsertHook)
        } catch (_: Throwable) {}

        // C. Media Streams & Descriptors (openInputStream, openOutputStream, openFileDescriptor, openAssetFileDescriptor, etc.)
        val mediaStreamAndDescriptorHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull { it is Uri } as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                val methodName = param.method.name
                val uriStr = uri.toString()
                val mode = param.args.firstOrNull { it is String && it.length <= 4 } as? String ?: ""
                val isWrite = methodName.contains("OutputStream", ignoreCase = true) ||
                    mode.contains("w") || mode.contains("rw") || mode.contains("wt") || mode.contains("wa")

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                    if (isGalleryWhitelisted(rule)) return

                    val sandboxRoot = getOrCreateSandboxRoot(pkg)

                    // Find or resolve target sandbox file
                    var targetFile = virtualUriMap[uriStr]
                    if (targetFile == null) {
                        val idLong = uri.lastPathSegment?.toLongOrNull()
                        if (idLong != null) {
                            targetFile = virtualMediaMap[idLong]
                        }
                    }
                    if (targetFile == null && uri.scheme == "file") {
                        val p = uri.path
                        if (p != null && shouldInterceptPath(pkg, p)) {
                            val rel = IsolationRuleManager.extractRelative(p)
                            targetFile = File(sandboxRoot, rel)
                        }
                    }

                    if (isWrite) {
                        // SAVING MEDIA: Ensure file exists in sandbox and return write descriptor
                        if (targetFile == null) {
                            val ext = guessExtension(uri)
                            val dir = File(sandboxRoot, "Pictures")
                            dir.mkdirs()
                            targetFile = File(dir, "saved_media_${System.currentTimeMillis()}.$ext")
                            try { targetFile.createNewFile() } catch (_: Throwable) {}
                            registerSandboxMedia(targetFile)
                            virtualUriMap[uriStr] = targetFile
                        }
                        targetFile.parentFile?.mkdirs()
                        try { targetFile.createNewFile() } catch (_: Throwable) {}

                        val pfd = ParcelFileDescriptor.open(
                            targetFile,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                        )

                        when {
                            methodName == "openOutputStream" -> {
                                param.result = FileOutputStream(targetFile)
                            }
                            methodName == "openFileDescriptor" || methodName == "openFile" -> {
                                param.result = pfd
                            }
                            else -> {
                                param.result = AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                            }
                        }

                        reportAuditToManager(
                            pkg = pkg,
                            requestedPath = uriStr,
                            resolvedPath = targetFile.absolutePath,
                            action = AuditAction.REDIRECTED,
                            operation = "MEDIA_WRITE",
                            details = "Saved media file into isolated sandbox (${targetFile.name})"
                        )
                        return
                    }

                    // READING MEDIA: If the file is in our sandbox, return real descriptor
                    if (targetFile != null && targetFile.exists()) {
                        val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        when {
                            methodName == "openInputStream" -> {
                                param.result = FileInputStream(targetFile)
                            }
                            methodName == "loadThumbnail" -> {
                                param.result = BitmapFactory.decodeFile(targetFile.absolutePath)
                            }
                            methodName == "openFileDescriptor" || methodName == "openFile" -> {
                                param.result = pfd
                            }
                            else -> {
                                param.result = AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                            }
                        }
                        return
                    }

                    // URI does NOT belong to the sandbox: Block read access to protect phone's real gallery
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uriStr,
                        resolvedPath = "IsoVault://blocked_media_read",
                        action = AuditAction.BLOCKED,
                        operation = "MEDIA_URI_READ",
                        details = "Blocked access to real phone media URI ($methodName)"
                    )
                    param.throwable = FileNotFoundException("Protected by IsoVault: media URI isolated")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        val resolverOpenMethods = listOf(
            "openInputStream",
            "openOutputStream",
            "openFileDescriptor",
            "openAssetFileDescriptor",
            "openTypedAssetFileDescriptor",
            "loadThumbnail"
        )
        for (m in resolverOpenMethods) {
            try { XposedBridge.hookAllMethods(ContentResolver::class.java, m, mediaStreamAndDescriptorHook) } catch (_: Throwable) {}
        }

        val clientOpenMethods = listOf("openFile", "openAssetFile", "openTypedAssetFile")
        for (m in clientOpenMethods) {
            try { XposedBridge.hookAllMethods(ContentProviderClient::class.java, m, mediaStreamAndDescriptorHook) } catch (_: Throwable) {}
        }

        try {
            XposedBridge.hookAllMethods(ImageDecoder::class.java, "createSource", mediaStreamAndDescriptorHook)
            XposedBridge.hookAllMethods(MediaMetadataRetriever::class.java, "setDataSource", mediaStreamAndDescriptorHook)
        } catch (_: Throwable) {}

        // D. Delete and Update
        val mediaDeleteHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val uri = param.args.firstOrNull { it is Uri } as? Uri ?: return
                val targetFile = virtualUriMap[uri.toString()]
                if (targetFile != null) {
                    try { targetFile.delete() } catch (_: Throwable) {}
                    virtualUriMap.remove(uri.toString())
                    param.result = 1
                }
            }
        }
        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "delete", mediaDeleteHook)
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "delete", mediaDeleteHook)
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 2: Snapchat & Local Gallery SQLiteSession CursorWindow Guard.
     * Clears CursorWindow when Snapchat queries its local SQLite cache for real phone photos.
     */
    private fun attachSnapchatAndGallerySqliteHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        try {
            val sqliteSessionClass = XposedHelpers.findClass("android.database.sqlite.SQLiteSession", null)
            XposedBridge.hookAllMethods(
                sqliteSessionClass,
                "executeForCursorWindow",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val sql = param.args.firstOrNull() as? String ?: return
                        val lower = sql.trimStart().lowercase()
                        if (!lower.startsWith("select")) return

                        if (lower.contains("camera_roll") ||
                            lower.contains("cameraroll") ||
                            lower.contains("local_media_reference") ||
                            lower.contains("/storage/emulated/0/dcim") ||
                            lower.contains("/storage/emulated/0/pictures")
                        ) {
                            isHandlingHook.set(true)
                            try {
                                val rule = resolveRuleForPackage(pkg)
                                if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                                if (isGalleryWhitelisted(rule)) return

                                val window = param.args.firstOrNull { it is CursorWindow } as? CursorWindow
                                window?.clear()
                                param.result = 0
                                XposedBridge.log("[IsoVault] Cleared cached Camera Roll SQLite CursorWindow in $pkg")
                            } finally {
                                isHandlingHook.set(false)
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 3: Isolated Photo & Document Picker Interceptor.
     * Replaces system pickers with an in-app IsoVault Isolated Picker so apps cannot access
     * un-isolated phone photos, but CAN pick files from their own isolated sandbox.
     */
    private fun attachPickerInterceptorHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        val execStartActivityHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true || isTemporarilyBypassingPicker.get() == true) return

                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                if (!isExternalPickerIntent(intent)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                    if (isGalleryWhitelisted(rule)) return

                    val whoContext = param.args.firstOrNull { it is Context } as? Context
                    val requestCode = (param.args.filterIsInstance<Int>().firstOrNull()) ?: -1

                    XposedBridge.log("[IsoVault] Intercepted external system picker in $pkg: ${intent.action}")
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = "SystemPicker://${intent.action ?: "PICK"}",
                        resolvedPath = "IsoVault://isolated_picker_sandbox",
                        action = AuditAction.REDIRECTED,
                        operation = "SYSTEM_PICKER_ISOLATED",
                        details = "Opened IsoVault Isolated Storage Picker instead of system picker"
                    )

                    param.result = null
                    showIsolatedPickerFeedback(whoContext, pkg, requestCode)
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(Instrumentation::class.java, "execStartActivity", execStartActivityHook)
        } catch (_: Throwable) {}

        try {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "startActivityForResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true || isTemporarilyBypassingPicker.get() == true) return
                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                        if (!isExternalPickerIntent(intent)) return

                        isHandlingHook.set(true)
                        try {
                            val rule = resolveRuleForPackage(pkg)
                            if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                            if (isGalleryWhitelisted(rule)) return

                            val activity = param.thisObject as? Activity
                            val requestCode = (param.args.filterIsInstance<Int>().firstOrNull()) ?: -1

                            param.result = null
                            showIsolatedPickerFeedback(activity, pkg, requestCode)
                        } finally {
                            isHandlingHook.set(false)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook onActivityResult to handle safe import callbacks
        try {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onActivityResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val requestCode = param.args.getOrNull(0) as? Int ?: return
                        val resultCode = param.args.getOrNull(1) as? Int ?: return
                        val data = param.args.getOrNull(2) as? Intent

                        val originalReq = pendingImportMap.remove(requestCode) ?: return
                        val activity = param.thisObject as? Activity ?: return

                        if (resultCode == Activity.RESULT_OK && data?.data != null) {
                            val sourceUri = data.data ?: return
                            try {
                                val sandboxRoot = getOrCreateSandboxRoot(pkg)
                                val ext = guessExtension(sourceUri)
                                val importedFile = File(File(sandboxRoot, "Pictures"), "imported_${System.currentTimeMillis()}.$ext")
                                importedFile.parentFile?.mkdirs()

                                activity.contentResolver.openInputStream(sourceUri)?.use { input ->
                                    FileOutputStream(importedFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                val virtualId = registerSandboxMedia(importedFile)
                                val mediaUri = Uri.parse("content://media/external/images/media/$virtualId")
                                virtualUriMap[mediaUri.toString()] = importedFile

                                val resultIntent = Intent().apply {
                                    setDataAndType(mediaUri, getMimeTypeFromExtension(ext))
                                    clipData = ClipData.newUri(activity.contentResolver, importedFile.name, mediaUri)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }

                                dispatchActivityResult(activity, originalReq, Activity.RESULT_OK, resultIntent)
                                Toast.makeText(activity, "Imported into IsoVault Sandbox", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {
                                dispatchCanceledResult(activity, originalReq)
                            }
                        } else {
                            dispatchCanceledResult(activity, originalReq)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun isExternalPickerIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: ""

        if (action == Intent.ACTION_CHOOSER) {
            @Suppress("DEPRECATION")
            val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (isExternalPickerIntent(target)) return true
        }

        val pickerActions = setOf(
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE,
            "android.provider.action.PICK_IMAGES",
            "com.google.android.gms.provider.action.PICK_IMAGES",
            "androidx.activity.result.contract.action.PICK_IMAGES"
        )
        if (action in pickerActions) return true

        val type = intent.type?.lowercase() ?: ""
        if ((action.contains("PICK", ignoreCase = true) || action.contains("CONTENT", ignoreCase = true)) &&
            (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") || type == "*/*")
        ) {
            return true
        }

        val targetPkg = intent.`package`?.lowercase() ?: intent.component?.packageName?.lowercase() ?: ""
        if (targetPkg.contains("photopicker") ||
            targetPkg.contains("documentsui") ||
            targetPkg.contains("providers.media") ||
            targetPkg.contains("gallery")
        ) {
            return true
        }

        val dataUri = intent.data
        if (dataUri != null && isMediaOrExternalDocumentUri(dataUri)) {
            return true
        }

        return false
    }

    private fun showIsolatedPickerFeedback(whoContext: Context?, pkg: String, requestCode: Int) {
        Handler(Looper.getMainLooper()).post {
            try {
                val sandboxRoot = getOrCreateSandboxRoot(pkg)
                val sandboxFiles = sandboxRoot.walkTopDown()
                    .filter { it.isFile && !it.name.startsWith(".") }
                    .take(30)
                    .toList()

                val activity = whoContext as? Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    val items = mutableListOf<String>()
                    val filesList = mutableListOf<File>()

                    for (file in sandboxFiles) {
                        val rel = file.relativeToOrSelf(sandboxRoot).path
                        val sizeKb = file.length() / 1024
                        items.add("📄 $rel (${sizeKb} KB)")
                        filesList.add(file)
                    }

                    items.add("📥 Import Photo from Phone (Safe Copy to Sandbox)")

                    val builder = AlertDialog.Builder(activity)
                        .setTitle("IsoVault Isolated Storage")

                    if (sandboxFiles.isEmpty()) {
                        builder.setMessage(
                            "This app is running in an isolated sandbox.\n\n" +
                                "Its private storage currently has no photos or documents.\n\n" +
                                "You can import a photo from your phone safely into this app's sandbox, or cancel."
                        )
                            .setPositiveButton("Import from Phone") { dialog, _ ->
                                launchSafeImportPicker(activity, pkg, requestCode)
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dispatchCanceledResult(activity, requestCode)
                                dialog.dismiss()
                            }
                            .setOnCancelListener {
                                dispatchCanceledResult(activity, requestCode)
                            }
                    } else {
                        builder.setItems(items.toTypedArray()) { dialog, which ->
                            if (which < filesList.size) {
                                val chosen = filesList[which]
                                val virtualId = registerSandboxMedia(chosen)
                                val mediaUri = Uri.parse("content://media/external/images/media/$virtualId")
                                virtualUriMap[mediaUri.toString()] = chosen
                                val resultIntent = Intent().apply {
                                    setDataAndType(mediaUri, getMimeTypeFromExtension(chosen.extension))
                                    clipData = ClipData.newUri(activity.contentResolver, chosen.name, mediaUri)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                dispatchActivityResult(activity, requestCode, Activity.RESULT_OK, resultIntent)
                            } else {
                                launchSafeImportPicker(activity, pkg, requestCode)
                            }
                            dialog.dismiss()
                        }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dispatchCanceledResult(activity, requestCode)
                                dialog.dismiss()
                            }
                            .setOnCancelListener {
                                dispatchCanceledResult(activity, requestCode)
                            }
                    }
                    builder.show()
                } else {
                    val appCtx = whoContext ?: AndroidAppHelper.currentApplication()
                    if (appCtx != null) {
                        Toast.makeText(appCtx, "IsoVault: Isolated storage active for $pkg", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Throwable) {
                if (whoContext is Activity && requestCode >= 0) {
                    dispatchCanceledResult(whoContext, requestCode)
                }
            }
        }
    }

    private fun launchSafeImportPicker(activity: Activity, pkg: String, originalRequestCode: Int) {
        val importReq = SAFE_IMPORT_REQUEST_BASE + (originalRequestCode % 500)
        pendingImportMap[importReq] = originalRequestCode
        try {
            isTemporarilyBypassingPicker.set(true)
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            activity.startActivityForResult(intent, importReq)
        } catch (_: Throwable) {
            dispatchCanceledResult(activity, originalRequestCode)
        } finally {
            isTemporarilyBypassingPicker.set(false)
        }
    }

    private fun dispatchCanceledResult(activity: Activity, requestCode: Int) {
        dispatchActivityResult(activity, requestCode, Activity.RESULT_CANCELED, null)
    }

    private fun dispatchActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode < 0) return
        try {
            XposedHelpers.callMethod(
                activity,
                "onActivityResult",
                requestCode,
                resultCode,
                data
            )
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 4: Java File Redirection & Virtual Directory Listing.
     * Creates and lists files in the app's isolated sandbox so the app can create folders,
     * read/write files, and see its own directory structure seamlessly.
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
        } catch (_: Throwable) {}

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
        } catch (_: Throwable) {}

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
        } catch (_: Throwable) {}

        // 4. File.list() & File.list(FilenameFilter) -> lists isolated sandbox directory
        val listStringsHook = object : XC_MethodHook() {
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
                            val sandboxDir = File(getOrCreateSandboxRoot(pkg), relative)
                            if (sandboxDir.exists() && sandboxDir.isDirectory) {
                                param.result = sandboxDir.list() ?: emptyArray<String>()
                            } else {
                                param.result = emptyArray<String>()
                            }
                        }
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        // 5. File.listFiles() -> lists isolated sandbox directory
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
                            val relative = IsolationRuleManager.extractRelative(path)
                            val sandboxDir = File(getOrCreateSandboxRoot(pkg), relative)
                            if (sandboxDir.exists() && sandboxDir.isDirectory) {
                                param.result = sandboxDir.listFiles() ?: emptyArray<File>()
                            } else {
                                param.result = emptyArray<File>()
                            }
                        }
                    } finally {
                        isHandlingHook.set(false)
                    }
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(File::class.java, "list", listStringsHook)
            XposedHelpers.findAndHookMethod(File::class.java, "list", FilenameFilter::class.java, listStringsHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FilenameFilter::class.java, listFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FileFilter::class.java, listFilesHook)
        } catch (_: Throwable) {}

        // 6. File status & existence queries (exists, isDirectory, isFile, length, canRead, canWrite)
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
                            val sandboxFile = File(getOrCreateSandboxRoot(pkg), relative)
                            when (param.method.name) {
                                "exists" -> param.result = sandboxFile.exists()
                                "isDirectory" -> param.result = sandboxFile.isDirectory
                                "isFile" -> param.result = sandboxFile.isFile
                                "length" -> param.result = if (sandboxFile.exists()) sandboxFile.length() else 0L
                                "canRead" -> param.result = sandboxFile.canRead()
                                "canWrite" -> param.result = true
                            }
                        }
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

        // 7. BitmapFactory.decodeFile
        try {
            XposedBridge.hookAllMethods(
                BitmapFactory::class.java,
                "decodeFile",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val path = param.args.firstOrNull() as? String ?: return
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
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 5: Low-Level OS & libcore.io Bridge Hooks.
     */
    private fun attachLowLevelOsHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String,
        initialRule: IsolationRuleManager.AppRule
    ) {
        val osPathRedirectHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val path = param.args.firstOrNull() as? String ?: return
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

        try {
            val ioBridgeClass = XposedHelpers.findClass("libcore.io.IoBridge", null)
            XposedBridge.hookAllMethods(ioBridgeClass, "open", osPathRedirectHook)
        } catch (_: Throwable) {}

        try {
            val blockGuardOsClass = XposedHelpers.findClass("libcore.io.BlockGuardOs", null)
            for (method in listOf("open", "stat", "lstat", "access", "opendir")) {
                XposedBridge.hookAllMethods(blockGuardOsClass, method, osPathRedirectHook)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Environment external storage getters redirection.
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
        } catch (_: Throwable) {}

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
        } catch (_: Throwable) {}
    }

    private fun shouldInterceptPath(pkg: String, path: String): Boolean {
        val norm = path.replace("\\", "/")
        if (!norm.startsWith("/storage/emulated/0") &&
            !norm.startsWith("/sdcard") &&
            !norm.startsWith("/mnt/sdcard") &&
            !norm.startsWith("/storage/self/primary")
        ) {
            return false
        }
        if (norm.contains("/IsoVault_Sandbox") ||
            norm.contains("/.IsoVault/") ||
            (pkg.isNotBlank() && norm.contains("/Android/data/$pkg"))
        ) {
            return false
        }
        return true
    }

    private fun isWhitelisted(path: String, whitelistedFolders: Set<String>): Boolean {
        for (wf in whitelistedFolders) {
            if (wf.isNotBlank() && path.contains("/$wf", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun getOrCreateSandboxRoot(pkg: String): File {
        val baseDir = try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) File(app.filesDir, "IsoVault_Sandbox") else null
        } catch (_: Throwable) { null }

        val sandbox = baseDir ?: File("/data/user/0/$pkg/files/IsoVault_Sandbox")
        if (!sandbox.exists()) {
            sandbox.mkdirs()
        }

        // Pre-create standard storage hierarchy so the app finds a real Android filesystem layout
        val subDirs = listOf(
            "DCIM",
            "DCIM/Camera",
            "Pictures",
            "Pictures/WhatsApp",
            "Pictures/Snapchat",
            "Movies",
            "Download",
            "Documents",
            "Music",
            "Android/media/$pkg"
        )
        for (sub in subDirs) {
            try { File(sandbox, sub).mkdirs() } catch (_: Throwable) {}
        }
        return sandbox
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
