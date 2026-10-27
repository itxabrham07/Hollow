package com.example.isolation.core.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

/**
 * Standard Zero-Touch Plugin Contract for all Hollow Feature Modules.
 *
 * Core (`MainActivity`, `DashboardScreen`, `OnboardingAuthScreen`) interacts with modules
 * strictly through this interface so new modules can be added in `/modules/<module_id>/`
 * or `com.example.isolation.module.<name>` and appear automatically as a Dashboard Bento Card
 * and interactive screen without modifying a single line of Core APK code.
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

    @Composable
    fun resolveCardBadge(viewModel: IsoVaultViewModel): String = "v$version"

    @Composable
    fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String = summary

    @Composable
    fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit = {}
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

