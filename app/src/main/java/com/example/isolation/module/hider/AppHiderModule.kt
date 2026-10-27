package com.example.isolation.module.hider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule

object AppHiderModule : HollowModule {
    const val ID = "module_hider"

    override val id: String = ID
    override val displayName: String = "App Hider & SystemUI Stealth"
    override val summary: String =
        "System-wide launcher hiding, Calculator/Notes icon disguise, and SystemUI Recents suppression."
    override val version: Int = 3
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.VisibilityOff
    override val primaryRoute: String = "APP_HIDER"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 412
}
