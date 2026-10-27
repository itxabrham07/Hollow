package com.example.isolation.core.module

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Standard Plugin Contract for all Hollow Feature Modules.
 * Core interacts with modules strictly through this contract so new modules can be
 * published on the server and loaded dynamically without touching Core.
 */
interface HollowModule {
    val id: String
    val displayName: String
    val summary: String
    val version: Int
    val requiredMinCoreVersion: Int
    val icon: ImageVector
    val primaryRoute: String
    val contributesBottomNavTab: Boolean
    val sizeKb: Int
}

data class DynamicModuleState(
    val module: HollowModule,
    val isAuthorizedByAdmin: Boolean,
    val isDownloadedInRootVault: Boolean,
    val isMountedInMemory: Boolean
) {
    val isReadyToUse: Boolean
        get() = isAuthorizedByAdmin && isDownloadedInRootVault && isMountedInMemory
}
