package com.example

import android.app.Application
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.AppPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IsoVaultApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { IsoVaultDatabase.getDatabase(this) }
    val packageRepository by lazy { AppPackageRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize hardware-backed Android Keystore Master Key (no hardcoded passphrase)
        applicationScope.launch {
            try {
                AesVaultCipher.initializeMasterKey(this@IsoVaultApp)
            } catch (_: Throwable) {}
        }

        // Connect hook interception engine to database audit logger
        IsolationRuleManager.onAccessIntercepted = { pkg, path, action, details ->
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

        // Initialize default rules and volume keys if needed
        applicationScope.launch {
            try {
                AesVaultCipher.initializeMasterKey(this@IsoVaultApp)
                seedInitialDataIfNeeded()
            } catch (_: Throwable) {}
        }
    }

    private suspend fun seedInitialDataIfNeeded() {
        val dao = database.isolationDao()
        val existingConfigs = dao.getAllConfigs().first()

        if (existingConfigs.isEmpty()) {
            val initial = listOf(
                AppIsolationConfig(
                    packageName = "com.snapchat.android",
                    appName = "Snapchat",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 24,
                    totalBytesEncrypted = 3840000L,
                    lastAccessedTimestamp = System.currentTimeMillis(),
                    useSharedSpace = false,
                    volumeQuotaMb = 500,
                    wrappedVolumeKey = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey()),
                    activeHooksMask = 0x1F
                ),
                AppIsolationConfig(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "Download",
                    totalFilesRedirected = 56,
                    totalBytesEncrypted = 8200000L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 3600000,
                    useSharedSpace = true,
                    volumeQuotaMb = 1024,
                    wrappedVolumeKey = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey()),
                    activeHooksMask = 0x1F
                ),
                AppIsolationConfig(
                    packageName = "org.telegram.messenger",
                    appName = "Telegram",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 18,
                    totalBytesEncrypted = 1920000L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 7200000,
                    useSharedSpace = false,
                    volumeQuotaMb = 0,
                    wrappedVolumeKey = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey()),
                    activeHooksMask = 0x1F
                ),
                AppIsolationConfig(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    isIsolated = false,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 0,
                    totalBytesEncrypted = 0L,
                    lastAccessedTimestamp = 0L,
                    useSharedSpace = false,
                    volumeQuotaMb = 0,
                    wrappedVolumeKey = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey()),
                    activeHooksMask = 0x1F
                )
            )
            dao.insertConfigs(initial)

            for (cfg in initial) {
                if (cfg.isIsolated) {
                    IsolationRuleManager.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                        useSharedSpace = cfg.useSharedSpace,
                        volumeQuotaMb = cfg.volumeQuotaMb
                    )
                }
            }
        } else {
            for (cfg in existingConfigs) {
                // Ensure each existing config has a wrapped per-volume key
                val finalKey = if (cfg.wrappedVolumeKey.isBlank()) {
                    val generated = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey())
                    dao.setWrappedVolumeKey(cfg.packageName, generated)
                    generated
                } else {
                    cfg.wrappedVolumeKey
                }
                if (cfg.isIsolated) {
                    IsolationRuleManager.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                        useSharedSpace = cfg.useSharedSpace,
                        volumeQuotaMb = cfg.volumeQuotaMb
                    )
                } else {
                    IsolationRuleManager.removeRule(cfg.packageName)
                }
            }
        }
    }

    companion object {
        lateinit var instance: IsoVaultApp
            private set

        fun instanceOrNull(): IsoVaultApp? = if (::instance.isInitialized) instance else null
    }
}
