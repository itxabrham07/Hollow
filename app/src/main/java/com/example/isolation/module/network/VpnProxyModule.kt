package com.example.isolation.module.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule

object VpnProxyModule : HollowModule {
    const val ID = "module_network"

    override val id: String = ID
    override val displayName: String = "Proxy & V2Ray Suite"
    override val summary: String =
        "Built-in VLESS/VMess/Trojan/Shadowsocks & SOCKS5/HTTP routing with per-app exit servers and kill switch."
    override val version: Int = 2
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Public
    override val primaryRoute: String = "VPN_PROXY"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 540
}
