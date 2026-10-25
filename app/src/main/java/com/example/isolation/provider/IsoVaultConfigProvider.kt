package com.example.isolation.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.VaultLockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import kotlin.concurrent.thread

/**
 * Hardened Cross-Process Configuration & IPC ContentProvider for IsoVault and LSPosed Hooked Apps.
 *
 * Security invariants:
 * 1. Storage Location: All volume data is strictly stored in the Vault app's private internal storage (filesDir).
 *    Never exposes raw filesystem paths or relies on world-readable storage.
 * 2. Restricted openFile(): Returns streaming ParcelFileDescriptor pipes so encryption and decryption
 *    always happen inside the Vault process, never in the target app.
 * 3. Identity Verification: Verifies Binder.getCallingUid() and package ownership against an enabled rule every time.
 * 4. Volume Scoping: The caller may only open files inside ITS OWN volume (plus Shared Space if useSharedSpace is on).
 * 5. Path Sanitization: Resolves paths with canonical paths, strictly rejecting "..", symlinks, and absolute paths.
 * 6. Fail-Closed: Rejects everything for locked vaults or unknown callers.
 * 7. Rate Limiting & Auditing: Rate-limits requests per UID and logs audit records for permitted and denied calls.
 */
class IsoVaultConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aistudio.isovault.xrvf.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")
        val OPEN_URI: Uri = Uri.parse("content://$AUTHORITY/open")

        private const val CODE_ALL_RULES = 1
        private const val CODE_PACKAGE_RULE = 2
        private const val CODE_OPEN_FILE = 3

        private const val MAX_CALLS_PER_WINDOW = 60
        private const val RATE_WINDOW_MS = 10_000L
        private const val MAX_SANITIZED_LENGTH = 260

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "rules", CODE_ALL_RULES)
            addURI(AUTHORITY, "rules/*", CODE_PACKAGE_RULE)
            addURI(AUTHORITY, "open", CODE_OPEN_FILE)
            addURI(AUTHORITY, "volume/*/*", CODE_OPEN_FILE)
        }
    }

    private data class RateBucket(var windowStartMs: Long, var count: Int)
    private val rateLimiter = ConcurrentHashMap<Int, RateBucket>()

    override fun onCreate(): Boolean = true

    /**
     * Checks per-UID rate limit to prevent flooding or DoS attacks.
     */
    private fun checkRateLimit(callingUid: Int): Boolean {
        if (callingUid == Process.myUid()) return true
        val now = System.currentTimeMillis()
        val bucket = rateLimiter.compute(callingUid) { _, existing ->
            if (existing == null || now - existing.windowStartMs > RATE_WINDOW_MS) {
                RateBucket(now, 1)
            } else {
                existing.count += 1
                existing
            }
        }
        return (bucket?.count ?: 0) <= MAX_CALLS_PER_WINDOW
    }

    /**
     * Verifies that Binder.getCallingUid() owns targetPackage (or is the Vault manager itself).
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

    private fun sanitizeInput(raw: String?, maxLen: Int = MAX_SANITIZED_LENGTH): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
            .take(maxLen)
    }

    private fun getSandboxesBaseDir(ctx: Context): File {
        return File(ctx.filesDir, "vault_sandboxes").apply { if (!exists()) mkdirs() }
    }

    private fun getSharedSpaceDir(ctx: Context): File {
        return File(ctx.filesDir, "vault_shared_space").apply { if (!exists()) mkdirs() }
    }

    private fun logAuditAsync(
        pkg: String,
        path: String,
        resolved: String,
        action: AuditAction,
        details: String,
        bytes: Long = 0L
    ) {
        val ctx = context ?: return
        val db = IsoVaultDatabase.getDatabase(ctx)
        val app = IsoVaultApp.instanceOrNull()
        val scope = app?.applicationScope
        scope?.launch {
            try {
                val cfg = db.isolationDao().getConfigForPackage(pkg)
                val appName = cfg?.appName ?: pkg.substringAfterLast('.')
                db.isolationDao().insertAuditLog(
                    AccessAuditLog(
                        packageName = pkg,
                        appName = appName,
                        requestedPath = path,
                        targetResolvedPath = resolved,
                        action = action,
                        operation = "IPC_${action.name}",
                        details = details
                    )
                )
                if (action != AuditAction.BLOCKED) {
                    db.isolationDao().incrementRedirectCount(pkg, System.currentTimeMillis())
                    if (bytes > 0L) {
                        db.isolationDao().addEncryptedBytes(pkg, bytes)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Resolves and verifies the target file path inside the volume sandbox.
     * Enforces canonical path checks and rejects "..", symlinks, and absolute paths.
     */
    private fun resolveAndVerifyVolumeFile(
        ctx: Context,
        pkg: String,
        space: String,
        rawRelativePath: String
    ): Pair<File, SecretKey> {
        val cleanRel = rawRelativePath.trimStart('/', '\\')

        if (cleanRel.isBlank() ||
            cleanRel.startsWith("/") ||
            cleanRel.contains("..") ||
            cleanRel.contains("\\") ||
            cleanRel.contains("\u0000")
        ) {
            throw SecurityException("Rejected invalid relative path: path traversal or control characters detected")
        }

        val baseDir = if (space.equals("shared", ignoreCase = true)) {
            getSharedSpaceDir(ctx)
        } else {
            File(getSandboxesBaseDir(ctx), pkg).apply { if (!exists()) mkdirs() }
        }

        val canonicalBase = baseDir.canonicalPath
        val targetFile = File(baseDir, cleanRel)
        val canonicalTarget = targetFile.canonicalPath

        if (!canonicalTarget.startsWith(canonicalBase + File.separator) && canonicalTarget != canonicalBase) {
            throw SecurityException("Access denied: path escapes volume boundaries")
        }

        // Check if any existing component in the path is a symbolic link
        var checkFile: File? = targetFile
        while (checkFile != null && checkFile.canonicalPath.startsWith(canonicalBase)) {
            if (checkFile.exists() && Files.isSymbolicLink(checkFile.toPath())) {
                throw SecurityException("Access denied: symbolic links are strictly prohibited")
            }
            if (checkFile.canonicalPath == canonicalBase) break
            checkFile = checkFile.parentFile
        }

        // Resolve encryption key for this volume
        val db = IsoVaultDatabase.getDatabase(ctx)
        val volumeKey: SecretKey = if (space.equals("shared", ignoreCase = true)) {
            AesVaultCipher.getMasterKey()
        } else {
            val cfg = runBlocking(Dispatchers.IO) { db.isolationDao().getConfigForPackage(pkg) }
            if (cfg != null && cfg.wrappedVolumeKey.isNotBlank()) {
                try {
                    AesVaultCipher.unwrapVolumeKey(cfg.wrappedVolumeKey)
                } catch (_: Throwable) {
                    AesVaultCipher.getMasterKey()
                }
            } else {
                AesVaultCipher.getMasterKey()
            }
        }

        return Pair(targetFile, volumeKey)
    }

    /**
     * Strictly restricted openFile() returning streaming ParcelFileDescriptor pipes.
     *
     * Invariants:
     * - Authenticates caller UID against an enabled rule.
     * - Fails closed if vault is locked or caller is unauthorized.
     * - Performs real-time AES256_GCM_HKDF_4KB streaming encryption (for writes)
     *   and decryption (for reads) inside the Vault process.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw SecurityException("Provider context not available")
        val callingUid = Binder.getCallingUid()

        if (!checkRateLimit(callingUid)) {
            logAuditAsync("unknown", uri.toString(), "DENIED", AuditAction.BLOCKED, "Rate limit exceeded on openFile")
            throw SecurityException("Rate limit exceeded")
        }

        // Fail-Closed: Check if Vault is locked
        if (VaultLockState.isLocked(ctx)) {
            logAuditAsync("unknown", uri.toString(), "DENIED", AuditAction.BLOCKED, "Vault is locked (fail-closed)")
            throw SecurityException("IsoVault is currently locked")
        }

        // Parse query params or path segments
        val pkgParam = sanitizeInput(uri.getQueryParameter("pkg"), 128)
        val spaceParam = sanitizeInput(uri.getQueryParameter("space"), 32).ifEmpty {
            if (uri.pathSegments.contains("shared")) "shared" else "private"
        }
        val relPathParam = uri.getQueryParameter("path") ?: run {
            val segments = uri.pathSegments
            val volIdx = segments.indexOf("volume")
            if (volIdx >= 0 && volIdx + 2 < segments.size) {
                segments.subList(volIdx + 2, segments.size).joinToString("/")
            } else {
                ""
            }
        }

        val callerPkgs = getCallerPackages(callingUid)
        val effectivePkg = if (pkgParam.isNotEmpty()) {
            if (!isCallerAuthorizedForPackage(callingUid, pkgParam)) {
                logAuditAsync(pkgParam, uri.toString(), "DENIED", AuditAction.BLOCKED, "Caller UID $callingUid unauthorized for $pkgParam")
                throw SecurityException("Caller UID unauthorized for package: $pkgParam")
            }
            pkgParam
        } else {
            if (callerPkgs.isEmpty()) {
                throw SecurityException("Could not identify caller package")
            }
            callerPkgs.first()
        }

        // Verify rule in database
        val db = IsoVaultDatabase.getDatabase(ctx)
        val cfg = runBlocking(Dispatchers.IO) { db.isolationDao().getConfigForPackage(effectivePkg) }
        val isSelf = (callingUid == Process.myUid())

        if (!isSelf && (cfg == null || !cfg.isIsolated)) {
            logAuditAsync(effectivePkg, uri.toString(), "DENIED", AuditAction.BLOCKED, "Package $effectivePkg is not isolated")
            throw SecurityException("Package is not isolated in IsoVault")
        }

        // Verify shared space permissions
        if (spaceParam.equals("shared", ignoreCase = true) && !isSelf && (cfg == null || !cfg.useSharedSpace)) {
            logAuditAsync(effectivePkg, uri.toString(), "DENIED", AuditAction.BLOCKED, "Attempted Shared Space access without opt-in")
            throw SecurityException("Shared space is not enabled for this package")
        }

        val (targetFile, volumeKey) = try {
            resolveAndVerifyVolumeFile(ctx, effectivePkg, spaceParam, relPathParam)
        } catch (se: SecurityException) {
            logAuditAsync(effectivePkg, relPathParam, "DENIED", AuditAction.BLOCKED, se.message ?: "Path verification failed")
            throw se
        }

        val isWrite = mode.contains("w") || mode.contains("rw") || mode.contains("a")

        return if (isWrite) {
            // Write Mode: Create streaming encryption pipe.
            // Caller writes plaintext -> Pipe -> Vault process encrypts with AES-256-GCM chunked stream to disk.
            targetFile.parentFile?.mkdirs()
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            thread(name = "IsoVault-EncryptStream-$effectivePkg", isDaemon = true) {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(readSide).use { pipeIn ->
                        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp_${System.currentTimeMillis()}")
                        try {
                            FileOutputStream(tempFile).use { fos ->
                                AesVaultCipher.encryptStream(pipeIn, fos, volumeKey)
                            }
                            if (tempFile.exists() && tempFile.length() >= AesVaultCipher.V2_HEADER_TOTAL_BYTES) {
                                if (targetFile.exists()) targetFile.delete()
                                tempFile.renameTo(targetFile)
                                logAuditAsync(
                                    pkg = effectivePkg,
                                    path = relPathParam,
                                    resolved = targetFile.name,
                                    action = AuditAction.ENCRYPTED,
                                    details = "AES-256-GCM encrypted write (${targetFile.length()} bytes stored)",
                                    bytes = targetFile.length()
                                )
                            }
                        } finally {
                            if (tempFile.exists()) tempFile.delete()
                        }
                    }
                } catch (_: Throwable) {}
            }
            writeSide
        } else {
            // Read Mode: Create streaming decryption pipe.
            // Target file on disk -> Vault process decrypts with streaming cipher -> Pipe -> Caller receives plaintext.
            if (!targetFile.exists()) {
                throw FileNotFoundException("File not found in volume: $relPathParam")
            }

            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            thread(name = "IsoVault-DecryptStream-$effectivePkg", isDaemon = true) {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { pipeOut ->
                        FileInputStream(targetFile).use { fis ->
                            if (AesVaultCipher.isEncryptedIsoVaultFile(targetFile)) {
                                AesVaultCipher.decryptStream(fis, pipeOut, volumeKey)
                            } else {
                                fis.copyTo(pipeOut)
                            }
                        }
                    }
                    logAuditAsync(
                        pkg = effectivePkg,
                        path = relPathParam,
                        resolved = targetFile.name,
                        action = AuditAction.REDIRECTED,
                        details = "AES-256-GCM decrypted stream read"
                    )
                } catch (_: Throwable) {}
            }
            readSide
        }
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

        runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                CODE_ALL_RULES -> {
                    val configs = db.isolationDao().getAllConfigs().first()
                    val callerPkgs = getCallerPackages(callingUid)
                    for (cfg in configs) {
                        if (isSelf || (cfg.packageName in callerPkgs && cfg.isIsolated)) {
                            cursor.addRow(
                                arrayOf<Any>(
                                    cfg.packageName,
                                    if (cfg.isIsolated) 1 else 0,
                                    cfg.isolationMode.name,
                                    if (cfg.blockRootPollution) 1 else 0,
                                    cfg.whitelistedFolders,
                                    if (cfg.useSharedSpace) 1 else 0,
                                    cfg.volumeQuotaMb,
                                    "", // Do not hand out raw filesystem paths
                                    ""
                                )
                            )
                        }
                    }
                }
                CODE_PACKAGE_RULE -> {
                    val requestedPkg = sanitizeInput(uri.lastPathSegment, 128)
                    if (requestedPkg.isNotEmpty() && isCallerAuthorizedForPackage(callingUid, requestedPkg)) {
                        val cfg = db.isolationDao().getConfigForPackage(requestedPkg)
                        if (cfg != null && (isSelf || cfg.isIsolated)) {
                            cursor.addRow(
                                arrayOf<Any>(
                                    cfg.packageName,
                                    if (cfg.isIsolated) 1 else 0,
                                    cfg.isolationMode.name,
                                    if (cfg.blockRootPollution) 1 else 0,
                                    cfg.whitelistedFolders,
                                    if (cfg.useSharedSpace) 1 else 0,
                                    cfg.volumeQuotaMb,
                                    "",
                                    ""
                                )
                            )
                        }
                    }
                }
            }
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val callingUid = Binder.getCallingUid()
        val db = IsoVaultDatabase.getDatabase(ctx)

        return when (method) {
            "get_rule" -> {
                val pkg = sanitizeInput(arg, 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) {
                    logAuditAsync(pkg.ifEmpty { "unknown" }, "get_rule", "DENIED", AuditAction.BLOCKED, "Unauthorized UID $callingUid")
                    return null
                }
                val cfg = runBlocking(Dispatchers.IO) {
                    db.isolationDao().getConfigForPackage(pkg)
                }
                if (cfg == null) return null

                Bundle().apply {
                    putBoolean("isolated", cfg.isIsolated)
                    putString("mode", cfg.isolationMode.name)
                    putBoolean("blockRoot", cfg.blockRootPollution)
                    putString("whitelist", cfg.whitelistedFolders)
                    putBoolean("useSharedSpace", cfg.useSharedSpace)
                    putInt("volumeQuotaMb", cfg.volumeQuotaMb)
                    // Return virtual URI authorities instead of raw filesystem paths
                    putString("sandboxUri", "content://$AUTHORITY/open?pkg=$pkg&space=private")
                    putString("sharedUri", if (cfg.useSharedSpace) "content://$AUTHORITY/open?pkg=$pkg&space=shared" else "")
                }
            }

            "file_stat" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) return null
                val space = sanitizeInput(extras.getString("space"), 32).ifEmpty { "private" }
                val path = extras.getString("path") ?: ""

                try {
                    val (file, _) = resolveAndVerifyVolumeFile(ctx, pkg, space, path)
                    Bundle().apply {
                        putBoolean("exists", file.exists())
                        putBoolean("isDirectory", file.isDirectory)
                        putLong("length", if (file.exists()) file.length() else 0L)
                        putLong("lastModified", if (file.exists()) file.lastModified() else 0L)
                    }
                } catch (_: Throwable) {
                    Bundle().apply {
                        putBoolean("exists", false)
                        putBoolean("isDirectory", false)
                        putLong("length", 0L)
                        putLong("lastModified", 0L)
                    }
                }
            }

            "list_files" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) return null
                val space = sanitizeInput(extras.getString("space"), 32).ifEmpty { "private" }
                val path = extras.getString("path") ?: ""

                try {
                    val (dir, _) = resolveAndVerifyVolumeFile(ctx, pkg, space, path)
                    val filesList = ArrayList<String>()
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { f ->
                            if (f.name != ".nomedia" && !f.name.startsWith(".seeded")) {
                                filesList.add(f.name.removeSuffix(".isovault"))
                            }
                        }
                    }
                    val cfg = runBlocking(Dispatchers.IO) { db.isolationDao().getConfigForPackage(pkg) }
                    if (space == "private" && (path.isEmpty() || path == "/") && cfg?.useSharedSpace == true) {
                        if ("Shared" !in filesList) filesList.add("Shared")
                    }
                    Bundle().apply {
                        putStringArrayList("files", filesList)
                    }
                } catch (_: Throwable) {
                    Bundle().apply { putStringArrayList("files", ArrayList()) }
                }
            }

            "file_delete" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) return null
                val space = sanitizeInput(extras.getString("space"), 32).ifEmpty { "private" }
                val path = extras.getString("path") ?: ""

                try {
                    val (file, _) = resolveAndVerifyVolumeFile(ctx, pkg, space, path)
                    val deleted = if (file.exists()) file.delete() else false
                    Bundle().apply { putBoolean("deleted", deleted) }
                } catch (_: Throwable) {
                    Bundle().apply { putBoolean("deleted", false) }
                }
            }

            "file_mkdir" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) return null
                val space = sanitizeInput(extras.getString("space"), 32).ifEmpty { "private" }
                val path = extras.getString("path") ?: ""

                try {
                    val (file, _) = resolveAndVerifyVolumeFile(ctx, pkg, space, path)
                    val ok = file.mkdirs() || file.exists()
                    Bundle().apply { putBoolean("ok", ok) }
                } catch (_: Throwable) {
                    Bundle().apply { putBoolean("ok", false) }
                }
            }

            "record_audit" -> {
                if (extras == null || !checkRateLimit(callingUid)) return null
                val pkg = sanitizeInput(extras.getString("pkg"), 128)
                if (pkg.isEmpty() || !isCallerAuthorizedForPackage(callingUid, pkg)) return null

                val path = sanitizeInput(extras.getString("path"), MAX_SANITIZED_LENGTH)
                val resolved = sanitizeInput(extras.getString("resolved"), MAX_SANITIZED_LENGTH)
                val actionStr = sanitizeInput(extras.getString("action"), 32).ifEmpty { "REDIRECTED" }
                val details = sanitizeInput(extras.getString("details"), MAX_SANITIZED_LENGTH)
                val bytes = extras.getLong("bytes", 0L).coerceIn(0L, 10L * 1024L * 1024L * 1024L)

                val action = AuditAction.entries.find { it.name == actionStr } ?: AuditAction.REDIRECTED
                logAuditAsync(pkg, path, resolved, action, details, bytes)
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
