package com.example.isolation.core.data.repository

import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.InstalledAppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface IsolationRepository {
    fun getAllConfigs(): Flow<List<AppIsolationConfig>>
    suspend fun getAllConfigsSync(): List<AppIsolationConfig>
    suspend fun getConfigForPackage(pkg: String): AppIsolationConfig?
    fun getIsolatedConfigs(): Flow<List<AppIsolationConfig>>
    fun getHiddenOrDisguisedConfigs(): Flow<List<AppIsolationConfig>>
    fun getIsolatedAppsCount(): Flow<Int>
    fun getHiddenOrDisguisedAppsCount(): Flow<Int>
    fun getIdentitySpoofedAppsCount(): Flow<Int>

    suspend fun insertOrUpdateConfig(config: AppIsolationConfig): HollowResult<Unit>
    suspend fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean): HollowResult<Unit>
    suspend fun updateAppIsolationMode(packageName: String, newMode: IsolationMode): HollowResult<Unit>
    suspend fun updateRootPollution(packageName: String, block: Boolean): HollowResult<Unit>
    suspend fun updateWhitelistedFolders(packageName: String, folders: String): HollowResult<Unit>
    suspend fun saveStealthConfig(config: AppIsolationConfig): HollowResult<Unit>
    suspend fun removeAppFromStealth(packageName: String): HollowResult<Unit>
    suspend fun saveIdentitySpoofConfig(config: AppIsolationConfig): HollowResult<Unit>
    suspend fun removeAppIdentitySpoof(packageName: String): HollowResult<Unit>
}

class DefaultIsolationRepository(
    private val dao: IsolationDao
) : IsolationRepository {

    override fun getAllConfigs(): Flow<List<AppIsolationConfig>> = dao.getAllConfigs()

    override suspend fun getAllConfigsSync(): List<AppIsolationConfig> = withContext(Dispatchers.IO) {
        dao.getAllConfigsSync()
    }

    override suspend fun getConfigForPackage(pkg: String): AppIsolationConfig? = withContext(Dispatchers.IO) {
        dao.getConfigForPackage(pkg)
    }

    override fun getIsolatedConfigs(): Flow<List<AppIsolationConfig>> = dao.getIsolatedConfigs()

    override fun getHiddenOrDisguisedConfigs(): Flow<List<AppIsolationConfig>> = dao.getHiddenOrDisguisedConfigs()

    override fun getIsolatedAppsCount(): Flow<Int> = dao.getIsolatedAppsCount()

    override fun getHiddenOrDisguisedAppsCount(): Flow<Int> = dao.getHiddenOrDisguisedAppsCount()

    override fun getIdentitySpoofedAppsCount(): Flow<Int> = dao.getIdentitySpoofedAppsCount()

    override suspend fun insertOrUpdateConfig(config: AppIsolationConfig): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to update isolation config") {
            withContext(Dispatchers.IO) {
                dao.insertOrUpdateConfig(config)
                publishRuleState(config)
            }
        }

    override suspend fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to toggle isolation for ${appInfo.packageName}") {
            withContext(Dispatchers.IO) {
                val existing = dao.getConfigForPackage(appInfo.packageName)
                val updated = existing?.copy(isIsolated = enable)
                    ?: AppIsolationConfig(
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        isIsolated = enable,
                        isolationMode = IsolationMode.SHARED_WORKSPACE
                    )
                dao.insertOrUpdateConfig(updated)
                if (enable) {
                    publishRuleState(updated)
                } else {
                    IsolationRuleManager.disableRule(
                        packageName = updated.packageName,
                        mode = updated.isolationMode,
                        blockRootPollution = updated.blockRootPollution,
                        whitelistedFolders = updated.whitelistedFolders.parseWhitelist()
                    )
                }
            }
        }

    override suspend fun updateAppIsolationMode(packageName: String, newMode: IsolationMode): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to update isolation mode for $packageName") {
            withContext(Dispatchers.IO) {
                val cfg = dao.getConfigForPackage(packageName)
                if (cfg != null) {
                    val updated = cfg.copy(isolationMode = newMode)
                    dao.insertOrUpdateConfig(updated)
                    if (updated.isIsolated) publishRuleState(updated)
                }
            }
        }

    override suspend fun updateRootPollution(packageName: String, block: Boolean): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to update root pollution for $packageName") {
            withContext(Dispatchers.IO) {
                val cfg = dao.getConfigForPackage(packageName)
                if (cfg != null) {
                    val updated = cfg.copy(blockRootPollution = block)
                    dao.insertOrUpdateConfig(updated)
                    if (updated.isIsolated) publishRuleState(updated)
                }
            }
        }

    override suspend fun updateWhitelistedFolders(packageName: String, folders: String): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to update whitelist folders for $packageName") {
            withContext(Dispatchers.IO) {
                val cfg = dao.getConfigForPackage(packageName)
                if (cfg != null) {
                    val updated = cfg.copy(whitelistedFolders = folders)
                    dao.insertOrUpdateConfig(updated)
                    if (updated.isIsolated) publishRuleState(updated)
                }
            }
        }

    override suspend fun saveStealthConfig(config: AppIsolationConfig): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to save stealth config for ${config.packageName}") {
            withContext(Dispatchers.IO) {
                dao.insertOrUpdateConfig(config)
            }
        }

    override suspend fun removeAppFromStealth(packageName: String): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to remove $packageName from stealth") {
            withContext(Dispatchers.IO) {
                val existing = dao.getConfigForPackage(packageName) ?: return@withContext
                val updated = existing.copy(
                    isHiddenOrDisguised = false,
                    launcherVisibilityMode = com.example.isolation.model.LauncherVisibilityMode.VISIBLE,
                    customLauncherLabel = "",
                    hideInSystemUiRecents = false,
                    hideFromPlayStore = false,
                    spoofInSettings = false,
                    customIconBase64 = "",
                    customIconDonorPkg = ""
                )
                dao.insertOrUpdateConfig(updated)
            }
        }

    override suspend fun saveIdentitySpoofConfig(config: AppIsolationConfig): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to save identity spoof for ${config.packageName}") {
            withContext(Dispatchers.IO) {
                dao.insertOrUpdateConfig(config)
            }
        }

    override suspend fun removeAppIdentitySpoof(packageName: String): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "IsolationRepo", errorMessage = "Failed to remove identity spoof for $packageName") {
            withContext(Dispatchers.IO) {
                val existing = dao.getConfigForPackage(packageName) ?: return@withContext
                val updated = existing.copy(
                    isIdentitySpoofed = false,
                    deviceProfilePreset = com.example.isolation.model.DeviceProfilePreset.REAL_HARDWARE,
                    spoofBrand = "",
                    spoofModel = "",
                    spoofDevice = "",
                    spoofAndroidId = "",
                    spoofImei = "",
                    spoofAdvertisingId = "",
                    spoofMacAddress = "",
                    spoofCarrierName = "",
                    spoofSimOperator = "",
                    spoofSerial = ""
                )
                dao.insertOrUpdateConfig(updated)
            }
        }

    private fun publishRuleState(cfg: AppIsolationConfig) {
        if (cfg.isIsolated) {
            IsolationRuleManager.updateRule(
                packageName = cfg.packageName,
                mode = cfg.isolationMode,
                blockRootPollution = cfg.blockRootPollution,
                whitelistedFolders = cfg.whitelistedFolders.parseWhitelist()
            )
        }
    }

    private fun String.parseWhitelist(): Set<String> =
        split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
}
