package com.example.isolation.core.module

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.BuildConfig
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.module.ModuleCatalog
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

/**
 * Central Coordinator (`HollowModuleRegistry`) for Core vs Dynamic DEX Modules.
 *
 * - Release APK (`ModuleCatalog.registeredModules == emptyList()`):
 *   Zero feature-module code is compiled into the Core APK. Every feature module is verified
 *   (SHA-256 + Ed25519 signature) and loaded dynamically from `/data/adb/.hollow_vault/modules/<id>.dex.enc`
 *   via `HollowDexModuleLoader` (`DexClassLoader`).
 * - Developer Full Build (`BuildConfig.DEBUG && devFullBuild`):
 *   Loads the standalone compiled & signed `.dex` modules from debug assets via `HollowDexModuleLoader`.
 */
object HollowModuleRegistry {

    val allCatalogModules: List<HollowModule>
        get() = ModuleCatalog.registeredModules

    val allModuleIds: Set<String>
        get() = (allCatalogModules.map { it.id } + ModuleCatalog.defaultCoreModuleIds).toSet()

    fun findModuleById(moduleId: String, activeStates: List<DynamicModuleState> = emptyList()): HollowModule? =
        activeStates.firstOrNull { it.module.id == moduleId }?.module
            ?: allCatalogModules.firstOrNull { it.id == moduleId }

    fun findModuleByRoute(route: String, activeStates: List<DynamicModuleState> = emptyList()): HollowModule? =
        activeStates.firstOrNull { it.module.primaryRoute == route }?.module
            ?: allCatalogModules.firstOrNull { it.primaryRoute == route }

    fun resolveModuleStates(
        context: Context,
        devFullBuild: Boolean,
        disabledModules: Set<String> = com.example.isolation.model.SettingsStore.load(context).disabledModules
    ): List<DynamicModuleState> {
        // Phase 7: Safe Mode bypasses all dynamic module mounting if startup crash threshold hit
        if (com.example.isolation.core.stability.SafeModeManager.isSafeModeEngaged(context)) {
            android.util.Log.e("HollowModuleRegistry", "SAFE MODE ACTIVE: 0 dynamic modules will be mounted.")
            return emptyList()
        }

        if (devFullBuild && BuildConfig.DEBUG) {
            val debugDexModules = HollowDexModuleLoader.loadDebugBundledModules(context)
            val byId = LinkedHashMap<String, HollowModule>()
            allCatalogModules.forEach { byId[it.id] = it }
            debugDexModules.forEach { byId[it.id] = it }
            return byId.values.map { mod ->
                val isDisabled = mod.id in disabledModules
                DynamicModuleState(
                    module = mod,
                    isAuthorizedByAdmin = true,
                    isDownloadedInRootVault = true,
                    isMountedInMemory = !isDisabled,
                    isDisabledByUser = isDisabled
                )
            }
        }

        val vaultRecord = RootLicenseVaultManager.readVaultRecord(context)
        val clockCheck = RootLicenseVaultManager.verifyVaultClockIntegrity(context)
        val isLicenseValid = vaultRecord.status == LicenseAccountStatus.APPROVED && clockCheck.isValid

        val baseIds = allCatalogModules.map { it.id }.toSet()
        val candidateIds = (ModuleCatalog.defaultCoreModuleIds + vaultRecord.allowedModules + vaultRecord.downloadedModules)
            .filter { it.isNotBlank() && it !in baseIds }
            .sorted()

        val resolvedStates = mutableListOf<DynamicModuleState>()

        // 1. Any statically registered catalog modules (empty in release builds)
        for (mod in allCatalogModules) {
            val authorized = isLicenseValid && vaultRecord.allowedModules.contains(mod.id)
            val downloaded = vaultRecord.downloadedModules.contains(mod.id)
            val isDisabled = mod.id in disabledModules
            resolvedStates.add(
                DynamicModuleState(
                    module = mod,
                    isAuthorizedByAdmin = authorized,
                    isDownloadedInRootVault = downloaded,
                    isMountedInMemory = authorized && downloaded && !isDisabled,
                    isDisabledByUser = isDisabled
                )
            )
        }

        // 2. Dynamically loaded DEX modules via HollowDexModuleLoader
        for (moduleId in candidateIds) {
            val isQuarantined = com.example.isolation.core.stability.ModuleQuarantineManager.isQuarantined(context, moduleId)
            val authorized = isLicenseValid && vaultRecord.allowedModules.contains(moduleId)
            val hadVaultFile = vaultRecord.downloadedModules.contains(moduleId)
            val isDisabled = moduleId in disabledModules

            val loadedDexModule: HollowModule? = if (hadVaultFile && !isQuarantined) {
                HollowDexModuleLoader.loadModuleFromVault(context, moduleId)
            } else {
                null
            }

            if (loadedDexModule != null) {
                resolvedStates.add(
                    DynamicModuleState(
                        module = loadedDexModule,
                        isAuthorizedByAdmin = authorized,
                        isDownloadedInRootVault = true,
                        isMountedInMemory = authorized && !isDisabled,
                        isDisabledByUser = isDisabled
                    )
                )
            } else if (authorized || moduleId in ModuleCatalog.defaultCoreModuleIds) {
                resolvedStates.add(
                    DynamicModuleState(
                        module = UnmountedModuleDescriptor(moduleId),
                        isAuthorizedByAdmin = authorized,
                        isDownloadedInRootVault = false,
                        isMountedInMemory = false,
                        isDisabledByUser = isDisabled
                    )
                )
            }
        }

        return resolvedStates
    }

    fun isModuleActive(
        moduleId: String,
        states: List<DynamicModuleState>,
        devFullBuild: Boolean
    ): Boolean {
        val state = states.firstOrNull { it.module.id == moduleId }
        if (state?.isDisabledByUser == true) return false
        if (devFullBuild && BuildConfig.DEBUG) return true
        return state?.isReadyToUse == true
    }
}

/**
 * Lightweight metadata descriptor for an authorized module that has NOT yet been downloaded
 * into `/data/adb/.hollow_vault/modules/<id>.dex.enc`. Contains zero feature-module logic.
 */
private class UnmountedModuleDescriptor(
    override val id: String
) : HollowModule {
    override val displayName: String = id.split("_")
        .filter { it.isNotBlank() && it.lowercase() != "module" }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { id }

    override val cardTitle: String = displayName.take(18)
    override val summary: String = "Dynamic security and privacy extension module for Hollow platform."
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Extension
    override val primaryRoute: String = "DYNAMIC_${id.uppercase()}"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 512
    override val cardTestTag: String = "module_card_$id"

    @Composable
    override fun RenderPrimaryScreen(
        context: com.example.isolation.sdk.HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        // Never invoked while unmounted (isReadyToUse == false)
    }
}

