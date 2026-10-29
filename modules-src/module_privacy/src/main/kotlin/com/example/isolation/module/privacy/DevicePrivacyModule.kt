package com.example.isolation.module.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.sdk.hook.HollowHookEntry

object DevicePrivacyModule : HollowModule, HollowHookEntry {
    const val ID = "module_privacy"

    override val id: String = ID
    override val displayName: String = "Hardware Spoofer & Privacy Profiles"
    override val cardTitle: String = "Privacy & ID"
    override val summary: String =
        "Provides isolated per-application hardware profiles for Android ID, serial numbers, and sensor streams."
    override val version: Int = 4
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Fingerprint
    override val primaryRoute: String = "DEVICE_SPOOFER"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 465
    override val accentColorHex: Long = 0xFF8B5CF6L
    override val cardTestTag: String = "card_device_identity_spoofer"

    override val hookTargets: List<String> = listOf("android", "com.google.android.gms", "*")

    override fun onMount(context: HollowModuleContext) {
        context.logger.i("DevicePrivacy", "Mounted DevicePrivacyModule v$version")
    }

    override fun onUnmount() {}

    override fun initZygote(startupParam: Any?) {}

    override fun handleLoadPackage(loadPackageParam: Any) {
        // Hook execution when target app loads: spoof Build fields, Android ID, GL vendor
    }

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        val count = context.preferences.getInt("spoofed_apps_count", 0)
        return "$count"
    }

    @Composable
    override fun resolveCardSubtitle(context: HollowModuleContext): String {
        val count = context.preferences.getInt("spoofed_apps_count", 0)
        return if (count == 0) "Hardware & Sensor cloaking" else "$count app${if (count == 1) "" else "s"} spoofed"
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        DeviceSpooferScreen(context = context, onBack = onBack)
    }
}
