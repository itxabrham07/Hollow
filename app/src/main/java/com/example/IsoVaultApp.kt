package com.example

import android.app.Application
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
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
                    packageName = "com.snapchat.android",
                    appName = "Snapchat",
                    isIsolated = true,
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 24,
                    totalBytesEncrypted = 3840000L,
                    lastAccessedTimestamp = System.currentTimeMillis()
                ),
                AppIsolationConfig(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    isIsolated = true,
                    isolationMode = IsolationMode.SANDBOX_REDIRECT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "Download",
                    totalFilesRedirected = 56,
                    totalBytesEncrypted = 8200000L,
                    lastAccessedTimestamp = System.currentTimeMillis() - 3600000
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
                    lastAccessedTimestamp = System.currentTimeMillis() - 7200000
                ),
                AppIsolationConfig(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    isIsolated = false, // Shared storage by default, toggleable!
                    isolationMode = IsolationMode.ENCRYPTED_VAULT,
                    encryptMediaOnly = false,
                    blockRootPollution = true,
                    whitelistedFolders = "",
                    totalFilesRedirected = 0,
                    totalBytesEncrypted = 0L,
                    lastAccessedTimestamp = 0L
                )
            )
            dao.insertConfigs(initial)

            // Register rule in rule manager
            for (cfg in initial) {
                if (cfg.isIsolated) {
                    IsolationRuleManager.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.toSet()
                    )
                }
            }
        } else {
            // Re-sync active and disabled rules to rule manager
            for (cfg in existingConfigs) {
                if (cfg.isIsolated) {
                    IsolationRuleManager.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.toSet()
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
