package com.example.isolation.core.module

import android.content.Context
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.module.ModuleCatalog

/**
 * Central Coordinator (`HollowModuleRegistry`) for Core vs Dynamic Modules.
 *
 * - Zero-Touch Architecture:
 *   1. Local Kotlin modules are read from `ModuleCatalog.registeredModules`.
 *   2. Any additional server-shipped modules in `/modules/<module_id>/manifest.json` downloaded into
 *      `/data/adb/.hollow_vault/modules/<module_id>.dex.enc` (or granted in `allowedModules`) are
 *      automatically instantiated as `DynamicSchemaModule` without touching a single line of Core code!
 */
object HollowModuleRegistry {

    val allCatalogModules: List<HollowModule>
        get() = ModuleCatalog.registeredModules

    val allModuleIds: Set<String>
        get() = allCatalogModules.map { it.id }.toSet()

    fun findModuleById(moduleId: String, activeStates: List<DynamicModuleState> = emptyList()): HollowModule? =
        activeStates.firstOrNull { it.module.id == moduleId }?.module
            ?: allCatalogModules.firstOrNull { it.id == moduleId }

    fun findModuleByRoute(route: String, activeStates: List<DynamicModuleState> = emptyList()): HollowModule? =
        activeStates.firstOrNull { it.module.primaryRoute == route }?.module
            ?: allCatalogModules.firstOrNull { it.primaryRoute == route }

    fun resolveModuleStates(
        context: Context,
        devFullBuild: Boolean
    ): List<DynamicModuleState> {
        val vaultRecord = RootLicenseVaultManager.readVaultRecord(context)
        val clockCheck = RootLicenseVaultManager.verifyVaultClockIntegrity(context)
        val isLicenseValid = vaultRecord.status == LicenseAccountStatus.APPROVED && clockCheck.isValid

        // Discover any extra server-shipped modules in /data/adb/.hollow_vault/modules/
        // that are not part of the base Kotlin ModuleCatalog
        val baseIds = allModuleIds
        val dynamicIds = (vaultRecord.allowedModules + vaultRecord.downloadedModules)
            .filter { it.isNotBlank() && it !in baseIds }
            .sorted()

        val dynamicModules: List<HollowModule> = dynamicIds.map { dynamicId ->
            val rawManifest = RootLicenseVaultManager.readDownloadedModulePayload(context, dynamicId)
            DynamicSchemaModule.fromDecryptedManifestJson(dynamicId, rawManifest)
        }

        val combinedModules = allCatalogModules + dynamicModules

        if (devFullBuild) {
            return combinedModules.map { mod ->
                DynamicModuleState(
                    module = mod,
                    isAuthorizedByAdmin = true,
                    isDownloadedInRootVault = true,
                    isMountedInMemory = true
                )
            }
        }

        return combinedModules.map { mod ->
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

