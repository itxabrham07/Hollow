package com.example.isolation.core.data.repository

import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.model.AppSettings
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.model.VpnProxyServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface VpnProxyRepository {
    val activeServer: Flow<VpnProxyServer>
    val allServers: Flow<List<VpnProxyServer>>

    suspend fun setVpnTunnelEnabled(enabled: Boolean): HollowResult<Unit>
    suspend fun selectActiveProxyServer(serverId: String): HollowResult<Unit>
    suspend fun importProxyServer(uriOrConfig: String): HollowResult<VpnProxyServer>
    suspend fun addCustomProxyServer(server: VpnProxyServer): HollowResult<Unit>
    suspend fun deleteCustomProxyServer(serverId: String): HollowResult<Unit>
    suspend fun updateSuiteOptions(
        socksProxy: Boolean,
        httpProxy: Boolean,
        webViewProxy: Boolean,
        trustAllCerts: Boolean,
        hideProxyConn: Boolean,
        hideVpnConn: Boolean,
        vpnKillSwitch: Boolean,
        matchTzWithIp: Boolean,
        showIpInfo: Boolean
    ): HollowResult<Unit>
}

class DefaultVpnProxyRepository(
    private val settingsRepository: SettingsRepository
) : VpnProxyRepository {

    override val activeServer: Flow<VpnProxyServer> = settingsRepository.settings.map { it.activeServer }
    override val allServers: Flow<List<VpnProxyServer>> = settingsRepository.settings.map { it.allProxyServers }

    override suspend fun setVpnTunnelEnabled(enabled: Boolean): HollowResult<Unit> =
        settingsRepository.updateSettings { it.copy(vpnTunnelEnabled = enabled) }.mapToUnit()

    override suspend fun selectActiveProxyServer(serverId: String): HollowResult<Unit> =
        settingsRepository.updateSettings { it.copy(activeProxyServerId = serverId, vpnTunnelEnabled = true) }.mapToUnit()

    override suspend fun importProxyServer(uriOrConfig: String): HollowResult<VpnProxyServer> =
        runSuspendCatchingHollow(tag = "VpnProxyRepo", errorMessage = "Failed to parse proxy URI") {
            val parsed = VpnProxyPresets.parseUri(uriOrConfig)
                ?: throw IllegalArgumentException("Invalid proxy configuration or protocol URI")
            settingsRepository.updateSettings { current ->
                current.copy(
                    customProxyServers = listOf(parsed) + current.customProxyServers,
                    activeProxyServerId = parsed.id,
                    vpnTunnelEnabled = true
                )
            }
            parsed
        }

    override suspend fun addCustomProxyServer(server: VpnProxyServer): HollowResult<Unit> =
        settingsRepository.updateSettings { current ->
            current.copy(
                customProxyServers = listOf(server) + current.customProxyServers,
                activeProxyServerId = server.id,
                vpnTunnelEnabled = true
            )
        }.mapToUnit()

    override suspend fun deleteCustomProxyServer(serverId: String): HollowResult<Unit> =
        settingsRepository.updateSettings { current ->
            val remaining = current.customProxyServers.filterNot { it.id == serverId }
            val nextActive = if (current.activeProxyServerId == serverId) "us_ny_vless" else current.activeProxyServerId
            current.copy(customProxyServers = remaining, activeProxyServerId = nextActive)
        }.mapToUnit()

    override suspend fun updateSuiteOptions(
        socksProxy: Boolean,
        httpProxy: Boolean,
        webViewProxy: Boolean,
        trustAllCerts: Boolean,
        hideProxyConn: Boolean,
        hideVpnConn: Boolean,
        vpnKillSwitch: Boolean,
        matchTzWithIp: Boolean,
        showIpInfo: Boolean
    ): HollowResult<Unit> =
        settingsRepository.updateSettings {
            it.copy(
                socksProxyEnabled = socksProxy,
                httpProxyEnabled = httpProxy,
                webViewProxyEnabled = webViewProxy,
                trustAllCertificates = trustAllCerts,
                hideProxyConnection = hideProxyConn,
                hideVpnConnection = hideVpnConn,
                vpnKillSwitchEnabled = vpnKillSwitch,
                matchTimezoneWithIpGlobal = matchTzWithIp,
                showIpInfoInToolbar = showIpInfo
            )
        }.mapToUnit()

    private fun HollowResult<AppSettings>.mapToUnit(): HollowResult<Unit> = when (this) {
        is HollowResult.Success -> HollowResult.Success(Unit)
        is HollowResult.Error -> this
        is HollowResult.Loading -> HollowResult.Loading
    }
}
