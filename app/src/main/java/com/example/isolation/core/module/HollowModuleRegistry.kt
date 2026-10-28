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
        devFullBuild: Boolean
    ): List<DynamicModuleState> {
        if (devFullBuild && BuildConfig.DEBUG) {
            val debugDexModules = HollowDexModuleLoader.loadDebugBundledModules(context)
            val byId = LinkedHashMap<String, HollowModule>()
            allCatalogModules.forEach { byId[it.id] = it }
            debugDexModules.forEach { byId[it.id] = it }
            return byId.values.map { mod ->
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

        val baseIds = allCatalogModules.map { it.id }.toSet()
        val candidateIds = (ModuleCatalog.defaultCoreModuleIds + vaultRecord.allowedModules + vaultRecord.downloadedModules)
            .filter { it.isNotBlank() && it !in baseIds }
            .sorted()

        val resolvedStates = mutableListOf<DynamicModuleState>()

        // 1. Any statically registered catalog modules (empty in release builds)
        for (mod in allCatalogModules) {
            val authorized = isLicenseValid && vaultRecord.allowedModules.contains(mod.id)
            val downloaded = vaultRecord.downloadedModules.contains(mod.id)
            resolvedStates.add(
                DynamicModuleState(
                    module = mod,
                    isAuthorizedByAdmin = authorized,
                    isDownloadedInRootVault = downloaded,
                    isMountedInMemory = authorized && downloaded
                )
            )
        }

        // 2. Dynamically loaded DEX modules via HollowDexModuleLoader
        for (moduleId in candidateIds) {
            val authorized = isLicenseValid && vaultRecord.allowedModules.contains(moduleId)
            val hadVaultFile = vaultRecord.downloadedModules.contains(moduleId)

            val loadedDexModule: HollowModule? = if (hadVaultFile) {
                // Verifies SHA-256 + Ed25519 signature and loads entryClass via DexClassLoader.
                // If tampered or invalid signature, HollowDexModuleLoader deletes the vault payload
                // and returns null.
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
                        isMountedInMemory = authorized
                    )
                )
            } else if (authorized || moduleId in ModuleCatalog.defaultCoreModuleIds) {
                // Un-downloaded (or purged after tamper detection) descriptor so OnboardingAuthScreen
                // can display the "Download" action for authorized modules.
                resolvedStates.add(
                    DynamicModuleState(
                        module = UnmountedModuleDescriptor(moduleId),
                        isAuthorizedByAdmin = authorized,
                        isDownloadedInRootVault = false,
                        isMountedInMemory = false
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
        if (devFullBuild && BuildConfig.DEBUG) return true
        return states.firstOrNull { it.module.id == moduleId }?.isReadyToUse == true
    }
}

/**
 * Lightweight metadata descriptor for an authorized module that has NOT yet been downloaded
 * into `/data/adb/.hollow_vault/modules/<id>.dex.enc`. Contains zero feature-module logic.
 */
private class UnmountedModuleDescriptor(
    override val id: String
) : HollowModule {
    override val displayName: String = when (id) {
        "module_isolation" -> "Storage Shield & Virtual Vault"
        "module_privacy" -> "Hardware Spoofer & Privacy Profiles"
        "module_hider" -> "App Hider & SystemUI Stealth"
        "module_network" -> "Proxy & V2Ray Suite"
        "module_sensor_firewall" -> "Sensor & Mic Hardware Firewall"
        else -> id.removePrefix("module_").split("_").joinToString(" ") {
            it.replaceFirstChar { c -> c.uppercase() }
        }
    }
    override val cardTitle: String = when (id) {
        "module_isolation" -> "Storage Isolate"
        "module_privacy" -> "Privacy & ID"
        "module_hider" -> "Hide App"
        "module_network" -> "Proxy & V2Ray"
        "module_sensor_firewall" -> "Sensor Firewall"
        else -> displayName.take(18)
    }
    override val summary: String = when (id) {
        "module_isolation" -> "Redirects file and media queries into encrypted per-app workspaces so personal photos and files stay protected."
        "module_privacy" -> "Provides isolated per-application hardware profiles for Android ID, serial numbers, and sensor streams."
        "module_hider" -> "Hides sensitive apps from system package queries, app launchers, and recent tasks."
        "module_network" -> "Routes selected application traffic through dedicated encrypted proxies with kill switch protection."
        "module_sensor_firewall" -> "Monitors and intercepts unauthorized microphone, camera, and sensor access."
        else -> "System privacy module for app sandboxing and device protection."
    }
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = when (id) {
        "module_isolation" -> Icons.Default.Apps
        "module_privacy" -> Icons.Default.Fingerprint
        "module_hider" -> Icons.Default.VisibilityOff
        "module_network" -> Icons.Default.Public
        "module_sensor_firewall" -> Icons.Default.Sensors
        else -> Icons.Default.Extension
    }
    override val primaryRoute: String = when (id) {
        "module_isolation" -> "APPS"
        "module_privacy" -> "DEVICE_SPOOFER"
        "module_hider" -> "APP_HIDER"
        "module_network" -> "VPN_PROXY"
        else -> "DYNAMIC_${id.uppercase()}"
    }
    override val contributesBottomNavTab: Boolean = id == "module_isolation" || id == "module_privacy"
    override val sizeKb: Int = 512
    override val cardTestTag: String = when (id) {
        "module_isolation" -> "stat_isolated_apps"
        "module_privacy" -> "card_device_identity_spoofer"
        "module_hider" -> "card_hide_disguise_apps"
        "module_network" -> "dashboard_vpn_card"
        else -> "module_card_$id"
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        // Never invoked while unmounted (isReadyToUse == false)
    }
}
