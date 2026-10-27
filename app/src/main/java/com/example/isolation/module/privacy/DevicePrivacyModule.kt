package com.example.isolation.module.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule

object DevicePrivacyModule : HollowModule {
    const val ID = "module_privacy"

    override val id: String = ID
    override val displayName: String = "Hardware Spoofer & Privacy Profiles"
    override val summary: String =
        "Per-app IMEI/Android ID/DRM spoofing, Virtual GPS teleport, sensor scrubbing, and Root Deadman Switch."
    override val version: Int = 5
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Fingerprint
    override val primaryRoute: String = "DEVICE_SPOOFER"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 790
}
