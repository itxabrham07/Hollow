package com.example.isolation.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.crypto.VaultLockManager
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * World-accessible ContentProvider enabling hooked target apps to query isolation rules,
 * check file stats, and securely stream encrypted file I/O through pipes.
 *
 * Enforces strict Binder.getCallingUid() authentication, per-volume isolation,
 * canonical path traversal prevention, and zero-leakage fail-closed security.
 */
class IsoVaultConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aistudio.isovault.xrvf.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")
        val VAULT_URI: Uri = Uri.parse("content://$AUTHORITY/vault")
        private const val TAG = "HollowProvider"

        private val deniedAuditCache = ConcurrentHashMap<String, Long>()
        private val ioExecutor = Executors.newCachedThreadPool()
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun notifyRuleChanged(context: Context, packageName: String) {
            try {
                val uri = Uri.withAppendedPath(CONTENT_URI, packageName)
                context.contentResolver.notifyChange(uri, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify provider change: ${e.message}")
            }
        }
    }

    override fun onCreate(): Boolean = true

    /**
     * Validates caller UID, verifies package mapping, enforces vault unlock state,
     * and returns the active AppIsolationConfig. Fails closed.
     */
    private fun authenticateCaller(requestedPkgHint: String? = null): Pair<String, AppIsolationConfig> {
        val callingUid = Binder.getCallingUid()
        val myUid = Process.myUid()
        val ctx = context ?: throw SecurityException("Provider context null")

        // Hollow Manager process itself
        if (callingUid == myUid) {
            val pkg = requestedPkgHint ?: ctx.packageName
            val config = getAppConfig(pkg) ?: AppIsolationConfig(
                packageName = pkg,
                appName = "Hollow",
                isIsolated = true,
                isolationMode = IsolationMode.ENCRYPTED_VAULT,
                useSharedSpace = true
            )
            return Pair(pkg, config)
        }

        // Map callingUid to verified package names
        val packages = ctx.packageManager.getPackagesForUid(callingUid)
        if (packages.isNullOrEmpty()) {
            recordDeniedAudit("uid:$callingUid", "unknown", "Unmapped caller UID $callingUid")
            throw SecurityException("Caller UID $callingUid cannot be mapped to an installed package.")
        }

        val callingPkg = if (requestedPkgHint != null && packages.contains(requestedPkgHint)) {
            requestedPkgHint
        } else {
            packages[0]
        }

        // Check if vault is locked
        if (VaultLockManager.isLocked.value) {
            recordDeniedAudit(callingPkg, "vault", "Vault locked by user")
            throw SecurityException("Hollow vault is locked.")
        }

        // Check isolation config
        val config = getAppConfig(callingPkg)
        if (config == null || !config.isIsolated) {
            recordDeniedAudit(callingPkg, "rules", "No active isolation rule configured for $callingPkg")
            throw SecurityException("Package $callingPkg is not authorized for vault isolation.")
        }

        return Pair(callingPkg, config)
    }

    private fun getAppConfig(pkg: String): AppIsolationConfig? {
        val app = context?.applicationContext as? IsoVaultApp
        return if (app != null) {
            try {
                runBlocking { app.database.isolationDao().getConfigForPackage(pkg) }
            } catch (_: Throwable) { null }
        } else null
    }

    private fun recordDeniedAudit(pkg: String, path: String, reason: String) {
        val key = "$pkg|$path"
        val now = System.currentTimeMillis()
        val last = deniedAuditCache[key] ?: 0L
        if (now - last < 3500L) return
        deniedAuditCache[key] = now

        val app = context?.applicationContext as? IsoVaultApp ?: return
        providerScope.launch {
            try {
                val dao = app.database.isolationDao()
                dao.insertAuditLog(
                    AccessAuditLog(
                        packageName = pkg,
                        appName = pkg.substringAfterLast('.'),
                        requestedPath = path,
                        targetResolvedPath = "/dev/null",
                        action = AuditAction.BLOCKED,
                        operation = "DENIED",
                        details = reason
                    )
                )
            } catch (_: Throwable) {}
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "packageName",
            "isIsolated",
            "mode",
            "whitelistedFolders",
            "blockPollution",
            "useSharedSpace",
            "explicitlyDisabled"
        ))

        val pkgHint = uri.lastPathSegment ?: selectionArgs?.firstOrNull() ?: ""
        val (pkg, config) = try {
            authenticateCaller(pkgHint.ifBlank { null })
        } catch (_: SecurityException) {
            // Return fail-closed empty rule
            cursor.addRow(arrayOf<Any>(pkgHint, 0, IsolationMode.ENCRYPTED_VAULT.name, "", 1, 0, 1))
            return cursor
        }

        cursor.addRow(arrayOf<Any>(
            config.packageName,
            if (config.isIsolated) 1 else 0,
            config.isolationMode.name,
            config.whitelistedFolders,
            if (config.blockRootPollution) 1 else 0,
            if (config.useSharedSpace) 1 else 0,
            if (!config.isIsolated) 1 else 0
        ))
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        val pkgHint = arg ?: ""

        val (callingPkg, config) = try {
            authenticateCaller(pkgHint.ifBlank { null })
        } catch (se: SecurityException) {
            bundle.putBoolean("error", true)
            bundle.putString("message", se.message)
            return bundle
        }

        when (method) {
            "check_isolated" -> {
                bundle.putBoolean("isConfigured", true)
                bundle.putBoolean("isIsolated", config.isIsolated)
                bundle.putString("mode", config.isolationMode.name)
                bundle.putString("whitelistedFolders", config.whitelistedFolders)
                bundle.putBoolean("blockPollution", config.blockRootPollution)
                bundle.putBoolean("useSharedSpace", config.useSharedSpace)
                return bundle
            }

            "record_audit" -> {
                if (extras != null) {
                    val requestedPath = extras.getString("requestedPath") ?: ""
                    val resolvedPath = extras.getString("resolvedPath") ?: ""
                    val actionStr = extras.getString("action") ?: "REDIRECTED"
                    val operation = extras.getString("operation") ?: "REDIRECT"
                    val details = extras.getString("details") ?: ""
                    val action = try { AuditAction.valueOf(actionStr) } catch (_: Exception) { AuditAction.REDIRECTED }

                    val app = context?.applicationContext as? IsoVaultApp
                    if (app != null) {
                        providerScope.launch {
                            try {
                                val dao = app.database.isolationDao()
                                dao.incrementRedirectCount(callingPkg, System.currentTimeMillis())
                                dao.insertAuditLog(
                                    AccessAuditLog(
                                        packageName = callingPkg,
                                        appName = config.appName,
                                        requestedPath = requestedPath,
                                        targetResolvedPath = resolvedPath,
                                        action = action,
                                        operation = operation,
                                        details = details
                                    )
                                )
                            } catch (_: Throwable) {}
                        }
                    }
                    bundle.putBoolean("recorded", true)
                }
                return bundle
            }

            "file_stat" -> {
                val subpath = extras?.getString("path") ?: ""
                val volume = extras?.getString("volume") ?: callingPkg
                val file = resolveSecureFile(callingPkg, config, volume, subpath)
                if (file != null && file.exists()) {
                    bundle.putBoolean("exists", true)
                    bundle.putBoolean("isDirectory", file.isDirectory)
                    bundle.putBoolean("isFile", file.isFile)
                    bundle.putLong("length", file.length())
                    bundle.putLong("lastModified", file.lastModified())
                } else {
                    bundle.putBoolean("exists", false)
                }
                return bundle
            }

            "list_files" -> {
                val subpath = extras?.getString("path") ?: ""
                val volume = extras?.getString("volume") ?: callingPkg
                val dir = resolveSecureFile(callingPkg, config, volume, subpath)
                if (dir != null && dir.exists() && dir.isDirectory) {
                    val list = dir.list() ?: emptyArray()
                    val cleaned = list.map { it.removeSuffix(".hllw") }.toTypedArray()
                    bundle.putStringArray("files", cleaned)
                } else {
                    bundle.putStringArray("files", emptyArray())
                }
                return bundle
            }

            "file_delete" -> {
                val subpath = extras?.getString("path") ?: ""
                val volume = extras?.getString("volume") ?: callingPkg
                val file = resolveSecureFile(callingPkg, config, volume, subpath)
                if (file != null && file.exists()) {
                    val success = file.delete()
                    bundle.putBoolean("deleted", success)
                } else {
                    bundle.putBoolean("deleted", false)
                }
                return bundle
            }

            "file_mkdir" -> {
                val subpath = extras?.getString("path") ?: ""
                val volume = extras?.getString("volume") ?: callingPkg
                val dir = resolveSecureFile(callingPkg, config, volume, subpath)
                if (dir != null) {
                    val success = dir.mkdirs()
                    bundle.putBoolean("created", success)
                } else {
                    bundle.putBoolean("created", false)
                }
                return bundle
            }
        }

        return bundle
    }

    /**
     * Resolves a canonical file strictly inside the allowed volume root in filesDir.
     * Rejects path traversal (".."), symlinks outside root, and absolute paths.
     */
    private fun resolveSecureFile(
        callingPkg: String,
        config: AppIsolationConfig,
        volume: String,
        subpath: String
    ): File? {
        val ctx = context ?: return null

        // Volume check: must be caller's own volume, or "shared" if useSharedSpace is enabled
        if (volume != callingPkg) {
            if (volume == "shared") {
                if (!config.useSharedSpace) {
                    recordDeniedAudit(callingPkg, subpath, "Shared space disabled for $callingPkg")
                    return null
                }
            } else {
                recordDeniedAudit(callingPkg, subpath, "Unauthorized volume '$volume'")
                return null
            }
        }

        // Canonical path validation
        val cleanSub = subpath.replace('\\', '/').trimStart('/')
        if (cleanSub.contains("..") || cleanSub.contains("\u0000")) {
            recordDeniedAudit(callingPkg, subpath, "Path traversal attempt detected")
            return null
        }

        val volumeRoot = File(ctx.filesDir, "vaults/$volume")
        volumeRoot.mkdirs()
        val canonicalRoot = volumeRoot.canonicalPath

        val target = File(volumeRoot, cleanSub)
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            recordDeniedAudit(callingPkg, subpath, "Canonical path traversal rejected")
            return null
        }

        return target
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.isEmpty() || segments[0] != "vault") {
            throw SecurityException("Invalid URI structure. Access denied.")
        }

        val targetVolume = segments.getOrNull(1) ?: throw SecurityException("Missing volume")
        val subpath = segments.drop(2).joinToString("/")
        if (subpath.isBlank()) throw SecurityException("Missing path")

        val (callingPkg, config) = authenticateCaller(null)
        val ctx = context ?: throw SecurityException("Context unavailable")

        // Volume access check
        if (targetVolume != callingPkg) {
            if (targetVolume == "shared") {
                if (!config.useSharedSpace) {
                    recordDeniedAudit(callingPkg, subpath, "Shared space is disabled for $callingPkg")
                    throw SecurityException("Shared space is not enabled for $callingPkg")
                }
            } else {
                recordDeniedAudit(callingPkg, subpath, "Denied access to foreign volume '$targetVolume'")
                throw SecurityException("Cross-volume access to $targetVolume denied.")
            }
        }

        val volumeRoot = File(ctx.filesDir, "vaults/$targetVolume")
        volumeRoot.mkdirs()
        val canonicalRoot = volumeRoot.canonicalPath

        val targetEncryptedFile = File(volumeRoot, "$subpath.hllw")
        val canonicalTarget = targetEncryptedFile.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot) || subpath.contains("..")) {
            recordDeniedAudit(callingPkg, subpath, "Path traversal blocked")
            throw SecurityException("Path traversal attempt detected.")
        }

        val volumeKey = AesVaultCipher.getVolumeKey(targetVolume, ctx)
        val isWrite = mode.contains("w") || mode.contains("a")

        if (isWrite) {
            targetEncryptedFile.parentFile?.mkdirs()
            val pipe = ParcelFileDescriptor.createPipe()
            val readPipe = pipe[0]
            val writePipe = pipe[1]

            ioExecutor.execute {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(readPipe).use { inputStream ->
                        FileOutputStream(targetEncryptedFile).use { fos ->
                            // Stream through AES-256 chunked cipher in Hollow process
                            AesVaultCipher.encryptStream(inputStream, fos, -1L, volumeKey)
                        }
                    }

                    // Record metadata in Room
                    val app = ctx.applicationContext as? IsoVaultApp
                    if (app != null) {
                        providerScope.launch {
                            try {
                                val dao = app.database.isolationDao()
                                dao.insertVaultFile(
                                    VaultFileItem(
                                        packageName = targetVolume,
                                        relativePath = subpath,
                                        physicalPath = targetEncryptedFile.absolutePath,
                                        isEncrypted = true,
                                        originalSizeBytes = targetEncryptedFile.length(),
                                        encryptedSizeBytes = targetEncryptedFile.length(),
                                        mimeType = "application/octet-stream",
                                        lastModified = System.currentTimeMillis()
                                    )
                                )
                                dao.addEncryptedBytes(callingPkg, targetEncryptedFile.length())
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Write pipe encryption failed: ${e.message}")
                }
            }
            return writePipe
        } else {
            // Read mode
            if (!targetEncryptedFile.exists()) {
                return null
            }

            val pipe = ParcelFileDescriptor.createPipe()
            val readPipe = pipe[0]
            val writePipe = pipe[1]

            ioExecutor.execute {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writePipe).use { outputStream ->
                        FileInputStream(targetEncryptedFile).use { fis ->
                            AesVaultCipher.decryptStream(fis, outputStream, volumeKey)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Read pipe decryption failed: ${e.message}")
                }
            }
            return readPipe
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.vault"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
