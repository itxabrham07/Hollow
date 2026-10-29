package com.example.isolation.module.hider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.sdk.hook.HollowHookEntry

object AppHiderModule : HollowModule, HollowHookEntry {
    const val ID = "module_hider"

    override val id: String = ID
    override val displayName: String = "App Hider & SystemUI Stealth"
    override val cardTitle: String = "Hide App"
    override val summary: String =
        "System-wide launcher hiding, Calculator/Notes icon disguise, and SystemUI Recents suppression."
    override val version: Int = 3
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.VisibilityOff
    override val primaryRoute: String = "APP_HIDER"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 412
    override val accentColorHex: Long = 0xFF06B6D4L
    override val cardTestTag: String = "card_hide_disguise_apps"

    override val hookTargets: List<String> = listOf(
        "android.app.ApplicationPackageManager",
        "android.content.pm.LauncherApps",
        "com.android.systemui"
    )

    override fun onMount(context: HollowModuleContext) {
        context.logger.i("AppHider", "Mounted AppHiderModule v$version")
    }

    override fun onUnmount() {}

    override fun initZygote(startupParam: Any?) {}

    override fun handleLoadPackage(loadPackageParam: Any) {
        // Hook execution when target launcher, PM or SystemUI loads
    }

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        val count = context.preferences.getInt("hidden_disguised_count", 0)
        return "$count"
    }

    @Composable
    override fun resolveCardSubtitle(context: HollowModuleContext): String {
        val count = context.preferences.getInt("hidden_disguised_count", 0)
        return if (count == 0) {
            "Launcher & SystemUI stealth"
        } else {
            "$count app${if (count == 1) "" else "s"} disguised"
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        AppHiderScreen(context = context, onBack = onBack)
    }
}
