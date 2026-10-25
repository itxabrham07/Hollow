package com.example.isolation.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationRuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
)

class IsoVaultRepository(
    private val dao: IsolationDao,
    private val context: Context
) {
    val allConfigs: Flow<List<AppIsolationConfig>> = dao.getAllConfigs()
    val isolatedConfigs: Flow<List<AppIsolationConfig>> = dao.getIsolatedConfigs()
    val recentAuditLogs: Flow<List<AccessAuditLog>> = dao.getRecentAuditLogs(100)
    val allVaultFiles: Flow<List<VaultFileItem>> = dao.getAllVaultFiles()
    val isolatedAppsCount: Flow<Int> = dao.getIsolatedAppsCount()
    val totalAuditCount: Flow<Int> = dao.getTotalAuditCount()
    val totalEncryptedBytes: Flow<Long> = dao.getTotalEncryptedBytes()

    private val vaultStorageDir: File by lazy {
        File(context.filesDir, "encrypted_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getConfigForPackage(packageName: String): AppIsolationConfig? {
        return dao.getConfigForPackage(packageName)
    }

    suspend fun saveConfig(config: AppIsolationConfig) {
        dao.insertOrUpdateConfig(config)
        syncInMemoryRules()
    }

    suspend fun toggleIsolation(packageName: String, isIsolated: Boolean) {
        dao.setIsolationStatus(packageName, isIsolated)
        syncInMemoryRules()
    }

    suspend fun toggleSharedSpace(packageName: String, useSharedSpace: Boolean) {
        dao.setSharedSpaceStatus(packageName, useSharedSpace)
        syncInMemoryRules()
    }

    suspend fun setVolumeQuota(packageName: String, quotaMb: Int) {
        // Reserved for future volume quota limits
        syncInMemoryRules()
    }

    suspend fun deleteVolumeConfig(packageName: String) {
        dao.deleteConfigForPackage(packageName)
        dao.deleteVaultFilesForPackage(packageName)
        IsolationRuleManager.removeRule(packageName)
        syncInMemoryRules()
    }

    suspend fun recordAudit(
        packageName: String,
        appName: String,
        originalPath: String,
        redirectedPath: String,
        action: AuditAction,
        operationType: String
    ) {
        val now = System.currentTimeMillis()
        dao.insertAuditLog(
            AccessAuditLog(
                packageName = packageName,
                appName = appName,
                requestedPath = originalPath,
                targetResolvedPath = redirectedPath,
                action = action,
                operation = operationType,
                timestamp = now
            )
        )
        dao.incrementRedirectCount(packageName, now)
    }

    suspend fun clearAuditLogs() {
        dao.clearAuditLogs()
    }

    suspend fun decryptVaultFilePreview(item: VaultFileItem): String = withContext(Dispatchers.IO) {
        try {
            val file = File(item.physicalPath)
            if (!file.exists()) return@withContext "[Vault file missing on disk]"
            val volumeKey = AesVaultCipher.getVolumeKey(item.packageName, context)
            val decryptedBytes = AesVaultCipher.decryptToMemory(file, volumeKey)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption error: ${e.message}]"
        }
    }

    suspend fun deleteVaultFile(item: VaultFileItem) = withContext(Dispatchers.IO) {
        File(item.physicalPath).delete()
        dao.deleteVaultFile(item.id)
    }

    suspend fun syncInMemoryRules() {
        val configs = dao.getAllConfigs().first()
        for (cfg in configs) {
            if (cfg.isIsolated) {
                IsolationRuleManager.updateRule(
                    packageName = cfg.packageName,
                    mode = cfg.isolationMode,
                    blockRootPollution = cfg.blockRootPollution,
                    whitelistedFolders = cfg.whitelistedFolders
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
                    useSharedSpace = cfg.useSharedSpace
                )
            } else {
                IsolationRuleManager.removeRule(cfg.packageName)
            }
        }
    }

    /**
     * One-time cleanup that removes any legacy seeded dummy apps, fake byte counts,
     * fake audit logs, and fake `.seeded_v2` placeholder files from previous versions,
     * ensuring 100% real data on disk and in Room.
     */
    suspend fun purgeLegacyDummySeedDataIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("isovault_security_prefs", Context.MODE_PRIVATE)
        val alreadyPurged = prefs.getBoolean("purged_legacy_dummy_v3", false)

        val sandboxBase = File(context.filesDir, "vault_sandboxes")
        val sharedBase = File(context.filesDir, "vault_shared_space")

        if (!alreadyPurged) {
            val fakeSeedFileNames = setOf(
                "IMG_20250218_194022.jpg.isovault",
                "Chat_Export_Notes.txt.isovault",
                "Snap_Memory_01.jpg.isovault",
                "Telegram_Backup_Key.pdf.isovault",
                "Vault_Welcome_Guide.txt.isovault",
                ".seeded_v2"
            )
            listOf(sandboxBase, sharedBase, vaultStorageDir).forEach { root ->
                if (root.exists()) {
                    root.walkTopDown().forEach { f ->
                        if (f.isFile && (f.name in fakeSeedFileNames || f.name.startsWith(".seeded"))) {
                            f.delete()
                        }
                    }
                }
            }

            val legacyFakeByteSignatures = setOf(
                148_250_000L,
                92_410_000L,
                64_120_000L,
                18_400_000L
            )
            val legacySeededPackages = setOf(
                "com.whatsapp",
                "com.snapchat.android",
                "org.telegram.messenger",
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            )

            val pm = context.packageManager
            val existingConfigs = dao.getAllConfigs().first()
            for (cfg in existingConfigs) {
                val isPackageInstalled = try {
                    pm.getApplicationInfo(cfg.packageName, 0)
                    true
                } catch (_: Exception) {
                    false
                }

                val pkgSandbox = File(sandboxBase, cfg.packageName)
                val realDiskBytes = if (pkgSandbox.exists()) {
                    pkgSandbox.walkTopDown()
                        .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                        .sumOf { it.length() }
                } else {
                    0L
                }

                if (cfg.packageName in legacySeededPackages &&
                    !isPackageInstalled &&
                    realDiskBytes == 0L &&
                    (cfg.totalBytesEncrypted in legacyFakeByteSignatures || cfg.totalFilesRedirected in setOf<Long>(0L, 42L, 31L, 19L, 12L))
                ) {
                    dao.deleteConfigForPackage(cfg.packageName)
                    pkgSandbox.deleteRecursively()
                } else if (cfg.totalBytesEncrypted != realDiskBytes) {
                    dao.insertOrUpdateConfig(cfg.copy(totalBytesEncrypted = realDiskBytes))
                }
            }

            val existingLogs = dao.getRecentAuditLogs(100).first()
            val fakePaths = setOf(
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Images/IMG-20250218-WA0014.jpg",
                "/storage/emulated/0/DCIM/Camera",
                "/storage/emulated/0/Telegram/Telegram Video/vid_8891.mp4",
                "/storage/emulated/0/Pictures/Instagram/IG_story_draft.mp4"
            )
            if (existingLogs.any { it.requestedPath in fakePaths }) {
                dao.clearAuditLogs()
            }

            prefs.edit().putBoolean("purged_legacy_dummy_v3", true).apply()
        }

        val currentConfigs = dao.getAllConfigs().first()
        for (cfg in currentConfigs) {
            val pkgSandbox = File(sandboxBase, cfg.packageName)
            val realBytes = if (pkgSandbox.exists()) {
                pkgSandbox.walkTopDown()
                    .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                    .sumOf { it.length() }
            } else {
                0L
            }
            if (cfg.totalBytesEncrypted != realBytes) {
                dao.insertOrUpdateConfig(cfg.copy(totalBytesEncrypted = realBytes))
            }
        }
        syncInMemoryRules()
    }

    /**
     * Queries PackageManager for real installed applications on the device,
     * returning both user-installed apps and launchable system apps with their real Drawable icons.
     */
    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchablePackages = try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }

        apps.asSequence()
            .filter { info ->
                if (info.packageName == context.packageName) return@filter false
                val isUserApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                isUserApp || info.packageName in launchablePackages
            }
            .map { info ->
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val label = try {
                    pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: info.packageName
                } catch (_: Exception) {
                    info.packageName
                }
                val iconDrawable = try {
                    pm.getApplicationIcon(info)
                } catch (_: Exception) {
                    null
                }
                InstalledAppInfo(
                    packageName = info.packageName,
                    appName = label,
                    icon = iconDrawable,
                    isSystemApp = isSystem
                )
            }
            .sortedWith(
                compareBy<InstalledAppInfo> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }
}
