package com.example

import android.app.Application
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.hook.StorageIsolationHook
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.AppPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class IsoVaultApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { IsoVaultDatabase.getDatabase(this) }
    val packageRepository by lazy { AppPackageRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Connect hook interception engine to database audit logger
        StorageIsolationHook.onAccessIntercepted = { pkg, path, action, details ->
            applicationScope.launch {
                try {
                    val config = database.isolationDao().getConfigForPackage(pkg)
                    val appName = config?.appName ?: pkg.substringAfterLast('.')
                    database.isolationDao().insertAuditLog(
                        AccessAuditLog(
                            packageName = pkg,
                            appName = appName,
                            requestedPath = path,
                            targetResolvedPath = when (action) {
                                AuditAction.ENCRYPTED -> "/vaults/$pkg/storage/encrypted.isovault"
                                AuditAction.REDIRECTED -> "/vaults/$pkg/storage/"
                                AuditAction.BLOCKED -> "/dev/null"
                                AuditAction.SPOOFED_EMPTY -> "/isovault_empty_dummy"
                                AuditAction.PERMITTED -> path
                            },
                            action = action,
                            operation = when (action) {
                                AuditAction.ENCRYPTED -> "ENCRYPT_WRITE"
                                AuditAction.BLOCKED -> "MKDIR_DENIED"
                                AuditAction.REDIRECTED -> "REDIRECT_OPEN"
                                else -> "READ"
                            },
                            details = details
                        )
                    )
                    database.isolationDao().incrementRedirectCount(pkg, System.currentTimeMillis())
                } catch (_: Exception) {}
            }
        }

        // Initialize default rules and sample files if needed
        applicationScope.launch {
            seedInitialDataIfNeeded()
        }
    }

    private suspend fun seedInitialDataIfNeeded() {
        val dao = database.isolationDao()
        val existingConfigs = dao.getAllConfigs().first()

        if (existingConfigs.isEmpty()) {
            val initial = listOf(
                AppIsolationConfig(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "Download",
                    totalFilesRedirected = 28,
                    totalBytesEncrypted = 1024 * 1024 * 14L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 1000 * 60 * 12
                ),
                AppIsolationConfig(
                    packageName = "com.tencent.mm",
                    appName = "WeChat",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "Pictures,Download",
                    totalFilesRedirected = 84,
                    totalBytesEncrypted = 1024 * 1024 * 45L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 1000 * 60 * 3
                ),
                AppIsolationConfig(
                    packageName = "com.autonavi.minimap",
                    appName = "Amap Navigation",
                    isIsolated = true,
                    isolationMode = IsolationMode.READ_ONLY,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 15,
                    totalBytesEncrypted = 0L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 1000 * 60 * 45
                ),
                AppIsolationConfig(
                    packageName = "org.telegram.messenger",
                    appName = "Telegram",
                    isIsolated = false,
                    isolationMode = IsolationMode.SANDBOX_REDIRECT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "Telegram,Download",
                    totalFilesRedirected = 0,
                    totalBytesEncrypted = 0L
                ),
                AppIsolationConfig(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    isIsolated = false,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "WhatsApp Images",
                    totalFilesRedirected = 0,
                    totalBytesEncrypted = 0L
                )
            )
            dao.insertConfigs(initial)

            // Register in hook
            for (cfg in initial) {
                if (cfg.isIsolated) {
                    StorageIsolationHook.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.toSet()
                    )
                }
            }

            // Seed sample audit logs
            dao.insertAuditLog(
                AccessAuditLog(
                    packageName = "com.tencent.mm",
                    appName = "WeChat",
                    requestedPath = "/storage/emulated/0/tencent/MicroMsg/cache.db",
                    targetResolvedPath = "/data/user/0/com.aistudio.isovault.xrvf/files/vaults/com.tencent.mm/storage/cache.db.isovault",
                    action = AuditAction.ENCRYPTED,
                    operation = "ENCRYPT_WRITE",
                    details = "Redirected to AES-256 encrypted vault. Root pollution prevented."
                )
            )
            dao.insertAuditLog(
                AccessAuditLog(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    requestedPath = "/sdcard/.snssdk_cache/device_fingerprint.dat",
                    targetResolvedPath = "/dev/null",
                    action = AuditAction.BLOCKED,
                    operation = "MKDIR_DENIED",
                    details = "Root pollution hidden directory blocked from public storage."
                )
            )
            dao.insertAuditLog(
                AccessAuditLog(
                    packageName = "com.autonavi.minimap",
                    appName = "Amap Navigation",
                    requestedPath = "/storage/emulated/0/autonavi/temp_ads.json",
                    targetResolvedPath = "/dev/null",
                    action = AuditAction.BLOCKED,
                    operation = "WRITE_BLOCKED",
                    details = "Read-Only policy enforced. Writing blocked."
                )
            )

            // Create initial sample encrypted file in app sandbox storage
            try {
                val sampleVaultDir = File(getExternalFilesDir(null), "vaults/com.tencent.mm/storage")
                sampleVaultDir.mkdirs()
                val plainTextSample = "IsoVault Isolated Storage Content:\nCreated by WeChat session.\nEncrypted with AES-256-GCM authenticated cipher.\nPrivacy preserved against unauthorized storage scanning."
                val sampleEncryptedFile = File(sampleVaultDir, "private_chat_cache.dat.isovault")
                val encBytes = AesVaultCipher.encrypt(plainTextSample.toByteArray())
                sampleEncryptedFile.writeBytes(encBytes)

                dao.insertVaultFile(
                    VaultFileItem(
                        packageName = "com.tencent.mm",
                        relativePath = "storage/private_chat_cache.dat",
                        physicalPath = sampleEncryptedFile.absolutePath,
                        isEncrypted = true,
                        originalSizeBytes = plainTextSample.length.toLong(),
                        encryptedSizeBytes = encBytes.size.toLong(),
                        mimeType = "text/plain",
                        lastModified = System.currentTimeMillis()
                    )
                )

                // TikTok sample
                val tikTokVaultDir = File(getExternalFilesDir(null), "vaults/com.zhiliaoapp.musically/storage")
                tikTokVaultDir.mkdirs()
                val tikTokSample = "TikTok Video Cache Index (Isolated)\nIdentifier: tk_v_98231\nProtected against cross-app scanning."
                val tikTokEncFile = File(tikTokVaultDir, "video_cache.idx.isovault")
                val tikTokEncBytes = AesVaultCipher.encrypt(tikTokSample.toByteArray())
                tikTokEncFile.writeBytes(tikTokEncBytes)

                dao.insertVaultFile(
                    VaultFileItem(
                        packageName = "com.zhiliaoapp.musically",
                        relativePath = "storage/video_cache.idx",
                        physicalPath = tikTokEncFile.absolutePath,
                        isEncrypted = true,
                        originalSizeBytes = tikTokSample.length.toLong(),
                        encryptedSizeBytes = tikTokEncBytes.size.toLong(),
                        mimeType = "application/octet-stream",
                        lastModified = System.currentTimeMillis() - 1000 * 60 * 10
                    )
                )
            } catch (_: Exception) {}
        } else {
            // Re-sync active rules to hook
            for (cfg in existingConfigs) {
                if (cfg.isIsolated) {
                    StorageIsolationHook.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.toSet()
                    )
                }
            }
        }
    }

    companion object {
        lateinit var instance: IsoVaultApp
            private set
    }
}
