package com.example.isolation.module.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.screens.DeviceSpooferScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object DevicePrivacyModule : HollowModule {
    const val ID = "module_privacy"

    override val id: String = ID
    override val displayName: String = "Hardware Spoofer & Privacy Profiles"
    override val cardTitle: String = "Privacy & ID"
    override val summary: String =
        "Per-app IMEI/Android ID/DRM spoofing, Virtual GPS teleport, sensor scrubbing, and Root Deadman Switch."
    override val version: Int = 5
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Fingerprint
    override val primaryRoute: String = "DEVICE_SPOOFER"
    override val contributesBottomNavTab: Boolean = true
    override val sizeKb: Int = 790
    override val accentColorHex: Long = 0xFF8B5CF6L
    override val cardTestTag: String = "card_device_identity_spoofer"

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        val identitySpoofedCount by viewModel.identitySpoofedCount.collectAsState()
        return "$identitySpoofedCount"
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        val identitySpoofedCount by viewModel.identitySpoofedCount.collectAsState()
        return if (identitySpoofedCount == 0) {
            "IMEI, Build, GPS & Shields"
        } else {
            "$identitySpoofedCount app${if (identitySpoofedCount == 1) "" else "s"} cloaked"
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        DeviceSpooferScreen(
            viewModel = viewModel,
            onBack = onBack,
            onDetailOpenChanged = onDetailOpenChanged
        )
    }
}

