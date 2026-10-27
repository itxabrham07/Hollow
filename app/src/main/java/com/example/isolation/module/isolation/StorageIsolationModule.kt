package com.example.isolation.module.isolation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object StorageIsolationModule : HollowModule {
    const val ID = "module_isolation"

    override val id: String = ID
    override val displayName: String = "Storage Shield & Virtual Vault"
    override val cardTitle: String = "Storage Isolate"
    override val summary: String =
        "FUSE root volume mounting, per-app sandboxed virtual drives, and AES-256 encrypted media vault."
    override val version: Int = 4
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Apps
    override val primaryRoute: String = "APPS"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 684
    override val accentColorHex: Long = 0xFF10B981L
    override val cardTestTag: String = "stat_isolated_apps"

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        val isolatedCount by viewModel.isolatedCount.collectAsState()
        return "$isolatedCount"
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        val isolatedCount by viewModel.isolatedCount.collectAsState()
        return if (isolatedCount == 0) {
            "Sandbox app files & media"
        } else {
            "$isolatedCount app${if (isolatedCount == 1) "" else "s"} isolated"
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        AppsScreen(viewModel = viewModel, onBack = onBack)
    }
}

