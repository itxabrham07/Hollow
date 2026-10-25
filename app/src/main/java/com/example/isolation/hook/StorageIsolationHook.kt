package com.example.isolation.hook

import android.app.Activity
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.Instrumentation
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
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
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * IsoVault Official LSPosed & JingMatrix/Vector Hook Implementation.
 * Loaded by Vector/LSPosed via assets/xposed_init.
 *
 * Comprehensive 5-Layer Isolation Engine:
 * 1. MediaStore, ContentResolver, ContentProviderClient & IContentProvider Proxy Hook
 *    (blocks Camera Roll queries, thumbnails & URI opens in Snapchat, WhatsApp, Instagram, etc.)
 * 2. Snapchat & Local Gallery SQLiteSession CursorWindow Guard
 *    (hides previously cached camera_roll rows in SQLite while preserving exact table schema & user login)
 * 3. System Photo Picker & SAF Document Picker Interceptor
 *    (prevents ACTION_PICK / PICK_IMAGES / GET_CONTENT / OPEN_DOCUMENT from launching the system picker & leaking phone data)
 * 4. Java File & Directory Listing Redirection (File constructors, list(), listFiles())
 * 5. Low-level libcore.io (IoBridge / BlockGuardOs) Path Redirection (stat, access, open, lstat, opendir)
 */
class StorageIsolationHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "IsoVaultHook"
        const val MODULE_PKG = "com.aistudio.isovault.xrvf"
        const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"

        // Re-entrancy guard to prevent infinite recursion inside File/ContentResolver/OS hooks
        private val isHandlingHook = object : ThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }

        // Background single-thread executor for non-blocking audit IPC calls to IsoVault
        private val auditExecutor = Executors.newSingleThreadExecutor()
        private val recentAuditCache = ConcurrentHashMap<String, Long>()

        // Fast in-memory rule cache (refreshed every 2.5 seconds) so hooks never stall app startup
        @Volatile
        private var cachedRule: IsolationRuleManager.AppRule? = null
        @Volatile
        private var lastRuleFetchMs: Long = 0L

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
     * Crash-proof empty MatrixCursor for MediaStore and Camera Roll queries.
     * Some apps (including Snapchat's Memories Camera Roll scanner) call
     * cursor.getColumnIndexOrThrow(...) BEFORE checking cursor.moveToFirst().
     * This cursor guarantees 0 rows while never throwing IllegalArgumentException on any column name.
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

        // Ignore core system framework packages
        if (pkg == "android" || pkg.startsWith("com.android.systemui")) {
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
        // In JingMatrix/Vector and LSPosed, handleLoadPackage is ONLY called for apps
        // that the user enabled in the Module Scope! So we isolate by default unless explicitly disabled.
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
        // 1. Try live IPC call to IsoVaultConfigProvider if Application context is ready
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

        // 2. Try XSharedPreferences (Vector/LSPosed native bridge)
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

        // 4. Default: Since Vector/LSPosed only invokes handleLoadPackage on scoped apps, isolate with ENCRYPTED_VAULT!
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

    /**
     * LAYER 1: Hooks ContentResolver, ContentProviderClient, AND ContentProviderProxy queries,
     * thumbnail loaders, image decoders, and file descriptor / stream openers.
     * Fixes Snapchat's in-built Camera Roll (which uses ContentProviderClient.query and
     * ContentResolver.openTypedAssetFileDescriptor / loadThumbnail) as well as WhatsApp/Instagram/Telegram.
     */
    private fun attachMediaStoreAndProviderHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        val mediaQueryHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return

                // Find the Uri argument index (index 0 for ContentResolver/ContentProviderClient, 1 or 2 for IContentProvider)
                val uriIndex = param.args.indexOfFirst { it is Uri }
                if (uriIndex < 0) return
                val uri = param.args[uriIndex] as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                    if (isGalleryWhitelisted(rule)) return

                    // In all query overloads, projection is immediately after Uri (uriIndex + 1)
                    val requestedProjection = param.args.getOrNull(uriIndex + 1) as? Array<*>
                    val mergedColumns = LinkedHashSet<String>()
                    requestedProjection?.forEach { col ->
                        if (col is String && col.isNotBlank()) mergedColumns.add(col)
                    }
                    DEFAULT_MEDIA_COLUMNS.forEach { mergedColumns.add(it) }

                    param.result = SafeEmptyMatrixCursor(mergedColumns.toTypedArray())

                    val action = if (rule.mode == IsolationMode.ENCRYPTED_VAULT) AuditAction.ENCRYPTED else AuditAction.SPOOFED_EMPTY
                    XposedBridge.log("[IsoVault] Isolated MediaStore/Provider query in $pkg: $uri")
                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uri.toString(),
                        resolvedPath = "IsoVault://empty_camera_roll",
                        action = action,
                        operation = "CAMERA_ROLL_QUERY",
                        details = "Hidden camera roll & media scan (${rule.mode.badge})"
                    )
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        // Hook ALL overloads of ContentResolver.query AND ContentProviderClient.query
        try {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "query", mediaQueryHook)
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.query hookAllMethods: ${t.message}")
        }

        try {
            XposedBridge.hookAllMethods(ContentProviderClient::class.java, "query", mediaQueryHook)
        } catch (t: Throwable) {
            Log.w(TAG, "ContentProviderClient.query hookAllMethods: ${t.message}")
        }

        // Also hook low-level android.content.ContentProviderProxy.query if present
        try {
            val proxyClass = XposedHelpers.findClass("android.content.ContentProviderProxy", null)
            XposedBridge.hookAllMethods(proxyClass, "query", mediaQueryHook)
        } catch (_: Throwable) {}

        // Block opening media URIs or cached thumbnails (openInputStream, openFileDescriptor,
        // openAssetFileDescriptor, openTypedAssetFileDescriptor, loadThumbnail)
        val mediaOpenBlockerHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val uri = param.args.firstOrNull { it is Uri } as? Uri ?: return
                if (!isMediaOrExternalDocumentUri(uri)) return

                // Allow URIs that explicitly point inside our own IsoVault_Sandbox
                val uriStr = uri.toString()
                if (uriStr.contains("IsoVault_Sandbox") || uriStr.contains(MODULE_PKG)) return

                isHandlingHook.set(true)
                try {
                    val rule = resolveRuleForPackage(pkg)
                    if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                    if (isGalleryWhitelisted(rule)) return

                    reportAuditToManager(
                        pkg = pkg,
                        requestedPath = uriStr,
                        resolvedPath = "IsoVault://blocked_media_uri",
                        action = AuditAction.BLOCKED,
                        operation = "MEDIA_URI_OPEN",
                        details = "Blocked direct media/thumbnail read (${param.method.name})"
                    )
                    param.throwable = FileNotFoundException("Protected by IsoVault: media URI isolated")
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        val resolverOpenMethods = listOf(
            "openInputStream",
            "openFileDescriptor",
            "openAssetFileDescriptor",
            "openTypedAssetFileDescriptor",
            "loadThumbnail"
        )
        for (methodName in resolverOpenMethods) {
            try {
                XposedBridge.hookAllMethods(ContentResolver::class.java, methodName, mediaOpenBlockerHook)
            } catch (_: Throwable) {}
        }

        val clientOpenMethods = listOf(
            "openFile",
            "openAssetFile",
            "openTypedAssetFile"
        )
        for (methodName in clientOpenMethods) {
            try {
                XposedBridge.hookAllMethods(ContentProviderClient::class.java, methodName, mediaOpenBlockerHook)
            } catch (_: Throwable) {}
        }

        // Hook ImageDecoder.createSource(ContentResolver, Uri) & MediaMetadataRetriever.setDataSource(Context, Uri)
        try {
            XposedBridge.hookAllMethods(ImageDecoder::class.java, "createSource", mediaOpenBlockerHook)
        } catch (_: Throwable) {}

        try {
            XposedBridge.hookAllMethods(MediaMetadataRetriever::class.java, "setDataSource", mediaOpenBlockerHook)
        } catch (_: Throwable) {}

        // Legacy MediaStore.Images.Thumbnails & Video.Thumbnails
        try {
            XposedBridge.hookAllMethods(
                MediaStore.Images.Thumbnails::class.java,
                "getThumbnail",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isGalleryWhitelisted(rule)) {
                            param.result = null as Bitmap?
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                MediaStore.Video.Thumbnails::class.java,
                "getThumbnail",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isGalleryWhitelisted(rule)) {
                            param.result = null as Bitmap?
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    /**
     * LAYER 2: Snapchat & Local Gallery SQLiteSession CursorWindow Guard.
     * Snapchat caches the user's Camera Roll rows in its own internal SQLite DB (tables named
     * `camera_roll*` / `memories_camera_roll*`) before IsoVault is enabled.
     * By hooking SQLiteSession.executeForCursorWindow when a SELECT query targets `camera_roll`
     * or `/storage/emulated/0/dcim`, we clear the CursorWindow and return 0 rows while preserving
     * the exact SQLite table column schema and keeping the user's Snapchat login intact!
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
     * LAYER 3: System Photo Picker & Document Picker Interceptor.
     * When the user clicks the external Gallery / Document Picker inside WhatsApp, Snapchat,
     * Instagram, or Telegram, the app fires Intent.ACTION_PICK, MediaStore.ACTION_PICK_IMAGES,
     * Intent.ACTION_GET_CONTENT, or Intent.ACTION_OPEN_DOCUMENT, which would normally launch
     * the un-isolated system PhotoPicker / DocumentsUI process and expose the phone's real data.
     *
     * This hook intercepts those picker intents right inside the target app and shows an
     * isolated IsoVault Private Locker dialog instead, returning RESULT_CANCELED to the app
     * so real phone photos and files are never exposed!
     */
    private fun attachPickerInterceptorHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        val execStartActivityHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return

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
                        action = AuditAction.BLOCKED,
                        operation = "SYSTEM_PICKER_BLOCKED",
                        details = "Prevented system Photo/Document Picker from exposing phone files"
                    )

                    // Suppress launching the external system PhotoPicker / DocumentsUI process
                    param.result = null

                    // Show isolated vault feedback & deliver clean RESULT_CANCELED to calling Activity
                    showIsolatedPickerFeedback(whoContext, pkg, requestCode)
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(Instrumentation::class.java, "execStartActivity", execStartActivityHook)
        } catch (t: Throwable) {
            Log.w(TAG, "Instrumentation.execStartActivity hook error: ${t.message}")
        }

        // Also hook Activity.startActivityForResult directly as a secondary safeguard
        try {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "startActivityForResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isHandlingHook.get() == true) return
                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                        if (!isExternalPickerIntent(intent)) return

                        isHandlingHook.set(true)
                        try {
                            val rule = resolveRuleForPackage(pkg)
                            if (!rule.isIsolated || rule.mode == IsolationMode.READ_ONLY) return
                            if (isGalleryWhitelisted(rule)) return

                            val activity = param.thisObject as? Activity
                            val requestCode = (param.args.filterIsInstance<Int>().firstOrNull()) ?: -1

                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = "SystemPicker://${intent.action ?: "PICK"}",
                                resolvedPath = "IsoVault://isolated_picker_sandbox",
                                action = AuditAction.BLOCKED,
                                operation = "SYSTEM_PICKER_BLOCKED",
                                details = "Prevented system Photo/Document Picker from exposing phone files"
                            )

                            param.result = null
                            showIsolatedPickerFeedback(activity, pkg, requestCode)
                        } finally {
                            isHandlingHook.set(false)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun isExternalPickerIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: ""

        // Unwrap Intent.ACTION_CHOOSER if wrapped
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
                    .take(20)
                    .toList()

                val activity = whoContext as? Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    if (sandboxFiles.isEmpty()) {
                        AlertDialog.Builder(activity)
                            .setTitle("Protected by IsoVault")
                            .setMessage(
                                "This app is isolated in its own private space.\n\n" +
                                    "IsoVault blocked the system file & photo picker so this app cannot view your phone's real gallery or documents.\n\n" +
                                    "(This app's private locker currently has 0 files.)"
                            )
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setOnDismissListener {
                                dispatchCanceledResult(activity, requestCode)
                            }
                            .show()
                    } else {
                        val fileNames = sandboxFiles.map { it.name }.toTypedArray()
                        AlertDialog.Builder(activity)
                            .setTitle("IsoVault Private Locker (${sandboxFiles.size} files)")
                            .setItems(fileNames) { dialog, which ->
                                val chosen = sandboxFiles[which]
                                val resultIntent = Intent().apply {
                                    data = Uri.fromFile(chosen)
                                }
                                dispatchActivityResult(activity, requestCode, Activity.RESULT_OK, resultIntent)
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dispatchCanceledResult(activity, requestCode)
                                dialog.dismiss()
                            }
                            .setOnCancelListener {
                                dispatchCanceledResult(activity, requestCode)
                            }
                            .show()
                    }
                } else {
                    val appCtx = whoContext ?: AndroidAppHelper.currentApplication()
                    if (appCtx != null) {
                        Toast.makeText(
                            appCtx,
                            "IsoVault: System gallery & file picker blocked to protect your private data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Throwable) {
                try {
                    if (whoContext is Activity && requestCode >= 0) {
                        dispatchCanceledResult(whoContext, requestCode)
                    }
                } catch (_: Throwable) {}
            }
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
     * LAYER 4: Hooks java.io.File constructors, BitmapFactory.decodeFile, and directory listing methods (list AND listFiles).
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

        // 4a. File.list() and File.list(FilenameFilter) protection against directory scanning
        val emptyListStringsHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isHandlingHook.get() == true) return
                val file = param.thisObject as? File ?: return
                isHandlingHook.set(true)
                try {
                    val path = file.path
                    if (shouldInterceptPath(pkg, path)) {
                        val rule = resolveRuleForPackage(pkg)
                        if (rule.isIsolated && !isWhitelisted(path, rule.whitelistedFolders)) {
                            param.result = emptyArray<String>()
                            reportAuditToManager(
                                pkg = pkg,
                                requestedPath = path,
                                resolvedPath = "IsoVault://empty_dir_listing",
                                action = AuditAction.SPOOFED_EMPTY,
                                operation = "LIST_DIR",
                                details = "Blocked folder scan on $path"
                            )
                        }
                    }
                } finally {
                    isHandlingHook.set(false)
                }
            }
        }

        // 4b. File.listFiles() protection against directory scanning
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
            XposedHelpers.findAndHookMethod(File::class.java, "list", emptyListStringsHook)
            XposedHelpers.findAndHookMethod(File::class.java, "list", FilenameFilter::class.java, emptyListStringsHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", emptyListFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FilenameFilter::class.java, emptyListFilesHook)
            XposedHelpers.findAndHookMethod(File::class.java, "listFiles", FileFilter::class.java, emptyListFilesHook)
        } catch (_: Throwable) {}

        // 5. BitmapFactory.decodeFile protection
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
     * LAYER 5: Low-Level OS / libcore.io Bridge Hooks (IoBridge.open & BlockGuardOs).
     * Catches direct path opens, stat(), lstat(), access(), and opendir() calls that bypass
     * File(String) constructors (e.g. NIO Files, File(URI), or cached path strings).
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
            !norm.startsWith("/mnt/sdcard") &&
            !norm.startsWith("/storage/self/primary")
        ) {
            return false
        }
        // Do not re-intercept paths already inside our sandbox or app-private Android/data
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
            } catch (_: Throwable) {}
        }
    }
}
