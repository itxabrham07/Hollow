package com.example.isolation.module

import com.example.isolation.core.module.HollowModule

/**
 * Core APK Module Catalog (`ModuleCatalog`).
 *
 * The Core APK contains ZERO feature module bytecode or hardcoded module IDs.
 * Every feature module is dynamically loaded and verified via HollowDexModuleLoader.
 */
object ModuleCatalog {
    val registeredModules: List<HollowModule> = emptyList()
    val defaultCoreModuleIds: Set<String> = emptySet()
}
