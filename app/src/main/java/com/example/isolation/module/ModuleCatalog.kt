package com.example.isolation.module

import com.example.isolation.core.module.HollowModule
import com.example.isolation.module.hider.AppHiderModule
import com.example.isolation.module.isolation.StorageIsolationModule
import com.example.isolation.module.network.VpnProxyModule
import com.example.isolation.module.privacy.DevicePrivacyModule

/**
 * Single registration list for local Kotlin `HollowModule` implementations inside `module/`.
 *
 * Note: Modules shipped purely from the server `/modules/<module_id>/manifest.json` directory
 * into `/data/adb/.hollow_vault/modules/<module_id>.dex.enc` do NOT even need to be added here;
 * `HollowModuleRegistry` automatically discovers and mounts them via `DynamicSchemaModule`.
 */
object ModuleCatalog {
    val registeredModules: List<HollowModule> = listOf(
        StorageIsolationModule,
        DevicePrivacyModule,
        AppHiderModule,
        VpnProxyModule
    )
}
