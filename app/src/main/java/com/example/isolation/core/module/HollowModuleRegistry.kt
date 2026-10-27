package com.example.isolation.core.module

import android.content.Context
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.module.hider.AppHiderModule
import com.example.isolation.module.isolation.StorageIsolationModule
import com.example.isolation.module.network.VpnProxyModule
import com.example.isolation.module.privacy.DevicePrivacyModule

/**
 * Central Coordinator (`HollowModuleRegistry`) for Core vs Dynamic Modules.
 *
 * - In Developer Full Build (`devFullBuild = true`), all registered modules are mounted and ready.
 * - In Release Shell Mode (`devFullBuild = false`), modules are only mounted when:
 *   1. The account status in `/data/adb/.hollow_vault` is `APPROVED`.
 *   2. The module ID is present in `allowedModules` (granted by Admin).
 *   3. The module payload has been downloaded and encrypted inside `/data/adb/.hollow_vault/modules/`.
 */
object HollowModuleRegistry {

    val allCatalogModules: List<HollowModule> = listOf(
        StorageIsolationModule,
        AppHiderModule,
        DevicePrivacyModule,
        VpnProxyModule
    )

    val allModuleIds: Set<String> = allCatalogModules.map { it.id }.toSet()

    fun resolveModuleStates(
        context: Context,
        devFullBuild: Boolean
    ): List<DynamicModuleState> {
        if (devFullBuild) {
            return allCatalogModules.map { mod ->
                DynamicModuleState(
                    module = mod,
                    isAuthorizedByAdmin = true,
                    isDownloadedInRootVault = true,
                    isMountedInMemory = true
                )
            }
        }

        val vaultRecord = RootLicenseVaultManager.readVaultRecord(context)
        val clockCheck = RootLicenseVaultManager.verifyVaultClockIntegrity(context)
        val isLicenseValid = vaultRecord.status == LicenseAccountStatus.APPROVED && clockCheck.isValid

        return allCatalogModules.map { mod ->
            val authorized = isLicenseValid && vaultRecord.allowedModules.contains(mod.id)
            val downloaded = vaultRecord.downloadedModules.contains(mod.id)
            DynamicModuleState(
                module = mod,
                isAuthorizedByAdmin = authorized,
                isDownloadedInRootVault = downloaded,
                isMountedInMemory = authorized && downloaded
            )
        }
    }

    fun isModuleActive(
        moduleId: String,
        states: List<DynamicModuleState>,
        devFullBuild: Boolean
    ): Boolean {
        if (devFullBuild) return true
        return states.firstOrNull { it.module.id == moduleId }?.isReadyToUse == true
    }
}
