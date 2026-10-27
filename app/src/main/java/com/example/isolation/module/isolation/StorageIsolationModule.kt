package com.example.isolation.module.isolation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule

object StorageIsolationModule : HollowModule {
    const val ID = "module_isolation"

    override val id: String = ID
    override val displayName: String = "Storage Shield & Virtual Vault"
    override val summary: String =
        "FUSE root volume mounting, per-app sandboxed virtual drives, and AES-256 encrypted media vault."
    override val version: Int = 4
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Folder
    override val primaryRoute: String = "APPS"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 684
}
