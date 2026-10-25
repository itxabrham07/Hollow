package com.example

import android.app.Application
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AuditAction
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

        // Ensure internal vault storage structure exists (in private filesDir)
        val vaultsDir = File(filesDir, "vaults")
        val sharedDir = File(vaultsDir, "shared")
        val recycleBinDir = File(filesDir, "recycle_bin")
        vaultsDir.mkdirs()
        sharedDir.mkdirs()
        recycleBinDir.mkdirs()

        // Connect hook interception engine to database audit logger
        IsolationRuleManager.onAccessIntercepted = { pkg, path, action, details ->
            applicationScope.launch {
                try {
                    val config = database.isolationDao().getConfigForPackage(pkg)
                    val appName = config?.appName ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    database.isolationDao().insertAuditLog(
                        AccessAuditLog(
                            packageName = pkg,
                            appName = appName,
                            requestedPath = path,
                            targetResolvedPath = when (action) {
                                AuditAction.ENCRYPTED -> "/files/vaults/$pkg/storage/"
                                AuditAction.REDIRECTED -> "/files/vaults/$pkg/storage/"
                                AuditAction.BLOCKED -> "/dev/null"
                                AuditAction.SPOOFED_EMPTY -> "/dev/null"
                                AuditAction.PERMITTED -> path
                            },
                            action = action,
                            operation = when (action) {
                                AuditAction.ENCRYPTED -> "ENCRYPT_WRITE"
                                AuditAction.BLOCKED -> "ACCESS_BLOCKED"
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

        // Synchronize active isolation rules from Room database
        applicationScope.launch {
            syncSavedRules()
        }
    }

    private suspend fun syncSavedRules() {
        val dao = database.isolationDao()
        val existingConfigs = dao.getAllConfigs().first()

        for (cfg in existingConfigs) {
            if (cfg.isIsolated) {
                IsolationRuleManager.updateRule(
                    packageName = cfg.packageName,
                    mode = cfg.isolationMode,
                    blockRootPollution = cfg.blockRootPollution,
                    whitelistedFolders = cfg.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = cfg.useSharedSpace
                )
            } else {
                IsolationRuleManager.removeRule(cfg.packageName)
            }
        }
    }

    companion object {
        lateinit var instance: IsoVaultApp
            private set

        fun instanceOrNull(): IsoVaultApp? = if (::instance.isInitialized) instance else null
    }
}
