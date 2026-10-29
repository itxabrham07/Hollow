package com.example.isolation.core.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.sdk.HollowModuleContext

/**
 * Standard Zero-Touch Plugin Contract for all Hollow Feature Modules.
 *
 * Core interacts with modules strictly through this interface.
 * Modules compile against `:hollow-sdk` (compileOnly) and Core provides it at runtime.
 */
interface HollowModule {
    val id: String
    val displayName: String
    val cardTitle: String
        get() = displayName
    val summary: String
    val version: Int
    val requiredMinCoreVersion: Int
    val icon: ImageVector
    val primaryRoute: String
    val contributesBottomNavTab: Boolean
    val sizeKb: Int
    val accentColorHex: Long
        get() = 0xFF10B981L
    val cardTestTag: String
        get() = "module_card_$id"

    // Lifecycle
    fun onMount(context: HollowModuleContext) {}
    fun onUnmount() {}
    fun onHookInit(bridge: Any) {}

    @Composable
    fun resolveCardBadge(context: HollowModuleContext): String = "v$version"

    @Composable
    fun resolveCardSubtitle(context: HollowModuleContext): String = summary

    @Composable
    fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    )
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
