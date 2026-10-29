package com.example.isolation.module.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext

object VpnProxyModule : HollowModule {
    const val ID = "module_network"

    override val id: String = ID
    override val displayName: String = "Proxy & V2Ray Suite"
    override val cardTitle: String = "Proxy & V2Ray"
    override val summary: String =
        "Per-app V2Ray/Shadowsocks proxy routing, XTLS Reality, and Exit IP Geolocation synchronization."
    override val version: Int = 2
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Public
    override val primaryRoute: String = "VPN_PROXY"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 450
    override val accentColorHex: Long = 0xFF3B82F6L
    override val cardTestTag: String = "dashboard_vpn_card"

    override fun onMount(context: HollowModuleContext) {
        context.logger.i("VpnProxy", "Mounted VpnProxyModule v$version")
    }

    override fun onUnmount() {}

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        val active = context.preferences.getBoolean("vpn_tunnel_active", false)
        return if (active) "Active" else "Standby"
    }

    @Composable
    override fun resolveCardSubtitle(context: HollowModuleContext): String {
        val active = context.preferences.getBoolean("vpn_tunnel_active", false)
        val serverId = context.preferences.getString("selected_server_id", "us_ny_vless")
        val s = VpnProxyPresets.defaultServers.firstOrNull { it.id == serverId } ?: VpnProxyPresets.defaultServers.first()
        return if (active) "Connected: ${s.city}" else "Proxy & Kill Switch"
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        VpnProxyScreen(context = context, onBack = onBack)
    }
}
