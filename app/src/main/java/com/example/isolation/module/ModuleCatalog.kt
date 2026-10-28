package com.example.isolation.module

import com.example.isolation.core.module.HollowModule

/**
 * Core APK Module Catalog (`ModuleCatalog`).
 *
 * The release APK ships ONLY the Core (onboarding, licensing, and `HollowDexModuleLoader`)
 * with zero feature-module code compiled into `classes.dex`.
 *
 * All feature modules (`module_isolation`, `module_privacy`, `module_hider`, `module_network`,
 * `module_sensor_firewall`, etc.) are built independently from `/modules-src/<module_id>/` into
 * signed `.dex` binaries (`module.dex.bin`), verified with SHA-256 + Ed25519 signature, and loaded
 * dynamically at runtime via `HollowDexModuleLoader`.
 */
object ModuleCatalog {
    val registeredModules: List<HollowModule> = emptyList()

    val defaultCoreModuleIds: Set<String> = setOf(
        "module_isolation",
        "module_privacy",
        "module_hider",
        "module_network"
    )
}
