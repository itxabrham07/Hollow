package com.example.isolation.module.isolation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.sdk.hook.HollowHookEntry

object StorageIsolationModule : HollowModule, HollowHookEntry {
    const val ID = "module_isolation"

    override val id: String = ID
    override val displayName: String = "Storage Shield & Virtual Vault"
    override val cardTitle: String = "Storage Isolate"
    override val summary: String =
        "Redirects file and media queries into encrypted per-app workspaces so personal photos and files stay protected."
    override val version: Int = 5
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Apps
    override val primaryRoute: String = "APPS"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 480
    override val accentColorHex: Long = 0xFF10B981L
    override val cardTestTag: String = "stat_isolated_apps"

    override val hookTargets: List<String> = listOf("android", "*")

    override fun onMount(context: HollowModuleContext) {
        context.logger.i("StorageIsolation", "Mounted StorageIsolationModule v$version")
    }

    override fun onUnmount() {}

    override fun initZygote(startupParam: Any?) {}

    override fun handleLoadPackage(loadPackageParam: Any) {
        // Intercept filesystem queries: redirect /sdcard to private app sandboxes
    }

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        val count = context.preferences.getInt("isolated_apps_count", 0)
        return "$count"
    }

    @Composable
    override fun resolveCardSubtitle(context: HollowModuleContext): String {
        val count = context.preferences.getInt("isolated_apps_count", 0)
        return if (count == 0) "Virtual volume sandboxing" else "$count app${if (count == 1) "" else "s"} isolated"
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        AppsScreen(context = context, onBack = onBack)
    }
}
