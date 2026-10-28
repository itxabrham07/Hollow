package com.example.isolation.module.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.screens.VpnProxyScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object VpnProxyModule : HollowModule {
    const val ID = "module_network"

    override val id: String = ID
    override val displayName: String = "Proxy & V2Ray Suite"
    override val cardTitle: String = "Proxy & V2Ray"
    override val summary: String =
        "Built-in VLESS/VMess/Trojan/Shadowsocks & SOCKS5/HTTP routing with per-app exit servers and kill switch."
    override val version: Int = 2
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Public
    override val primaryRoute: String = "VPN_PROXY"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 540
    override val accentColorHex: Long = 0xFF0EA5E9L
    override val cardTestTag: String = "dashboard_vpn_card"

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        val settings by viewModel.settings.collectAsState()
        return settings.activeServer.protocol.badge
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        val settings by viewModel.settings.collectAsState()
        val allApps by viewModel.allInstalledApps.collectAsState()
        val routedAppsCount = allApps.count { it.vpnProxyEnabled }
        val activeServer = settings.activeServer
        val isVpnOn = settings.vpnTunnelEnabled
        return when {
            isVpnOn && routedAppsCount > 0 -> "[${activeServer.countryCode}] ${activeServer.exitIp} • $routedAppsCount apps"
            isVpnOn -> "[${activeServer.countryCode}] ${activeServer.exitIp} • Ready"
            else -> "Per-app & global routing"
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        VpnProxyScreen(viewModel = viewModel, onBack = onBack)
    }
}

