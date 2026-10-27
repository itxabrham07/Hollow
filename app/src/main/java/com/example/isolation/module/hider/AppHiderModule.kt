package com.example.isolation.module.hider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.screens.AppHiderScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object AppHiderModule : HollowModule {
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

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        val hiddenOrDisguisedCount by viewModel.hiddenOrDisguisedCount.collectAsState()
        return "$hiddenOrDisguisedCount"
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        val hiddenOrDisguisedCount by viewModel.hiddenOrDisguisedCount.collectAsState()
        return if (hiddenOrDisguisedCount == 0) {
            "Launcher & SystemUI stealth"
        } else {
            "$hiddenOrDisguisedCount app${if (hiddenOrDisguisedCount == 1) "" else "s"} disguised"
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        AppHiderScreen(viewModel = viewModel, onBack = onBack)
    }
}

