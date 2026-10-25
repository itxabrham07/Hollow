package com.example.isolation.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.example.IsoVaultApp
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationRuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hardened Cross-Process Configuration & Audit ContentProvider for LSPosed Hooked Apps.
 *
 * Security invariants:
 * - Provides CONFIGURATION ONLY — `openFile()` is completely disabled/removed.
 * - Verifies caller identity via `Binder.getCallingUid()` and `packageManager.getPackagesForUid(callingUid)`.
 * - Only responds to callers that match the requested package AND have an active rule configured in IsoVault
 *   (or the IsoVault manager process itself).
 * - Sanitizes and rate-limits `record_audit` calls per UID to prevent log flooding or injection.
 */
class IsoVaultConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aistudio.isovault.xrvf.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")

        private const val CODE_ALL_RULES = 1
        private const val CODE_PACKAGE_RULE = 2

        private const val MAX_AUDIT_CALLS_PER_WINDOW = 20
        private const val AUDIT_RATE_WINDOW_MS = 10_000L
        private const val MAX_SANITIZED_LENGTH = 240

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "rules", CODE_ALL_RULES)
            addURI(AUTHORITY, "rules/*", CODE_PACKAGE_RULE)
        }
    }

    private data class RateBucket(var windowStartMs: Long, var count: Int)
    private val auditRateLimiter = ConcurrentHashMap<Int, RateBucket>()

    override fun onCreate(): Boolean = true

    /**
     * Verifies that `Binder.getCallingUid()` owns `targetPackage` (or is the Vault manager process itself).
     */
    private fun isCallerAuthorizedForPackage(callingUid: Int, targetPackage: String): Boolean {
        if (callingUid == Process.myUid() || callingUid == Process.SYSTEM_UID || callingUid == 0) {
            return true
        }
        val ctx = context ?: return false
        val packagesForUid = try {
            ctx.packageManager.getPackagesForUid(callingUid)?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
        return targetPackage in packagesForUid
    }

    private fun getCallerPackages(callingUid: Int): Set<String> {
        val ctx = context ?: return emptySet()
        if (callingUid == Process.myUid()) return setOf(ctx.packageName)
        return try {
            ctx.packageManager.getPackagesForUid(callingUid)?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun checkRateLimit(callingUid: Int): Boolean {
        val now = System.currentTimeMillis()
        val bucket = auditRateLimiter.compute(callingUid) { _, existing ->
            if (existing == null || now - existing.windowStartMs > AUDIT_RATE_WINDOW_MS) {
                RateBucket(now, 1)
            } else {
                existing.count += 1
                existing
            }
        }
        return (bucket?.count ?: 0) <= MAX_AUDIT_CALLS_PER_WINDOW
    }

    private fun sanitizeInput(raw: String?, maxLen: Int = MAX_SANITIZED_LENGTH): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
            .take(maxLen)
    }

    private fun resolveVaultRoot(ctx: android.content.Context): File {
        val ext = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val root = File(ext, "IsoVault")
        if (!root.exists()) root.mkdirs()
        return root
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                "packageName",
                "isIsolated",
                "isolationMode",
                "blockRootPollution",
                "whitelistedFolders",
                "useSharedSpace",
                "volumeQuotaMb",
                "sandboxPath",
                "sharedPath"
            )
        )
        val ctx = context ?: return cursor
        val callingUid = Binder.getCallingUid()
        val isSelf = (callingUid == Process.myUid())
        val db = IsoVaultDatabase.getDatabase(ctx)
        val vaultRoot = resolveVaultRoot(ctx)
        val sharedDir = File(vaultRoot, "sandboxes/${IsolationRuleManager.SHARED_VOLUME_ID}")

        runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                CODE_ALL_RULES -> {
                    // Only the Vault app itself can list all rules; hooked apps only receive their own rule
                    val configs = db.isolationDao().getAllConfigs().first()
                    val callerPkgs = getCallerPackages(callingUid)
                    for (cfg in configs) {
                        if (isSelf || (cfg.packageName in callerPkgs && cfg.isIsolated)) {
                            val sandboxDir = File(vaultRoot, "sandboxes/${cfg.packageName}")
                            cursor.addRow(
                                arrayOf<Any>(
                                    cfg.packageName,
                                    if (cfg.isIsolated) 1 else 0,
                                    cfg.isolationMode.name,
                                    if (cfg.blockRootPollution) 1 else 0,
                                    cfg.whitelistedFolders,
                                    if (cfg.useSharedSpace) 1 else 0,
                                    cfg.volumeQuotaMb,
                                    sandboxDir.absolutePath,
                                    if (cfg.useSharedSpace) sharedDir.absolutePath else ""
                                )
                            )
                        }
                    }
                }
                CODE_PACKAGE_RULE -> {
                    val requestedPkg = sanitizeInput(uri.lastPathSegment, 128)
                    if (requestedPkg.isNotEmpty() && isCallerAuthorizedForPackage(callingUid, requestedPkg)) {
                        val cfg = db.isolationDao().getConfigForPackage(requestedPkg)
                        // Only answer apps that have an active rule configured
                        if (cfg != null && (isSelf || cfg.isIsolated)) {
                            val sandboxDir = File(vaultRoot, "sandboxes/${cfg.packageName}")
                            cursor.addRow(
                                arrayOf<Any>(
                                    cfg.packageName,
                                    if (cfg.isIsolated) 1 else 0,
                                    cfg.isolationMode.name,
                                    if (cfg.blockRootPollution) 1 else 0,
                                    cfg.whitelistedFolders,
                                    if (cfg.useSharedSpace) 1 else 0,
                                    cfg.volumeQuotaMb,
                                    sandboxDir.absolutePath,
                                    if (cfg.useSharedSpace) sharedDir.absolutePath else ""
                                )
                            )
                        }
                    }
                }
            }
        }
        return cursor
    }

    /**
     * Handles cross-process RPC calls from hooked apps with strict UID verification and rate limiting.
     * Provides configuration only; never provides raw file access.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val callingUid = Binder.getCallingUid()
        val db = IsoVaultDatabase.getDatabase(ctx)
        val vaultRoot = resolveVaultRoot(ctx)
        val sharedDir = File(vaultRoot, "sandboxes/${IsolationRuleManager.SHARED_VOLUME_ID}")

        return when (method) {
            "get_rule" -> {
                val pkg = sanitizeInput(arg, 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) {
                    return null
                }
                val cfg = runBlocking(Dispatchers.IO) {
                    db.isolationDao().getConfigForPackage(pkg)
                }
                // Only answer apps that have a rule configured
                if (cfg == null) return null

                val sandboxDir = File(vaultRoot, "sandboxes/${cfg.packageName}")
                IsolationRuleManager.ensureStandardVolumeFolders(sandboxDir)
                if (cfg.useSharedSpace) {
                    IsolationRuleManager.ensureStandardVolumeFolders(sharedDir)
                }

                Bundle().apply {
                    putBoolean("isolated", cfg.isIsolated)
                    putString("mode", cfg.isolationMode.name)
                    putBoolean("blockRoot", cfg.blockRootPollution)
                    putString("whitelist", cfg.whitelistedFolders)
                    putBoolean("useSharedSpace", cfg.useSharedSpace)
                    putInt("volumeQuotaMb", cfg.volumeQuotaMb)
                    putString("sandboxPath", sandboxDir.absolutePath)
                    putString("sharedPath", if (cfg.useSharedSpace) sharedDir.absolutePath else "")
                }
            }

            "record_audit" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) {
                    return null
                }

                val path = sanitizeInput(extras.getString("path"), MAX_SANITIZED_LENGTH)
                val resolved = sanitizeInput(extras.getString("resolved"), MAX_SANITIZED_LENGTH)
                val actionStr = sanitizeInput(extras.getString("action"), 32).ifEmpty { "REDIRECTED" }
                val details = sanitizeInput(extras.getString("details"), MAX_SANITIZED_LENGTH)
                val bytes = extras.getLong("bytes", 0L).coerceIn(0L, 10L * 1024L * 1024L * 1024L)

                val app = IsoVaultApp.instanceOrNull()
                app?.applicationScope?.launch {
                    try {
                        val cfg = db.isolationDao().getConfigForPackage(pkg)
                        // Only record audits for configured apps
                        if (cfg == null) return@launch
                        val appName = cfg.appName
                        val action = AuditAction.entries.find { it.name == actionStr } ?: AuditAction.REDIRECTED
                        db.isolationDao().insertAuditLog(
                            AccessAuditLog(
                                packageName = pkg,
                                appName = appName,
                                requestedPath = path,
                                targetResolvedPath = resolved,
                                action = action,
                                operation = "LSP_HOOK_${action.name}",
                                details = details
                            )
                        )
                        db.isolationDao().incrementRedirectCount(pkg, System.currentTimeMillis())
                        if (bytes > 0L) {
                            db.isolationDao().addEncryptedBytes(pkg, bytes)
                        }
                    } catch (_: Exception) {}
                }
                Bundle().apply { putBoolean("ok", true) }
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
