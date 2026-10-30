package com.example.isolation.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.isolation.core.data.repository.AuditLogRepository
import com.example.isolation.core.data.repository.MountRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.core.data.repository.VpnProxyRepository
import com.example.isolation.core.result.onError
import com.example.isolation.core.result.onSuccess
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AppSettings
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.PickerPolicy
import com.example.isolation.model.VpnProxyServer
import com.example.isolation.mount.MountEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Focused ViewModel for Settings, Diagnostics, Root Mounts, V2Ray/Proxy, and Audit Logs.
 */
class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val mountRepository: MountRepository,
    private val vpnProxyRepository: VpnProxyRepository,
    private val auditLogRepository: AuditLogRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _systemMounts = MutableStateFlow<List<MountEntry>>(emptyList())
    val systemMounts: StateFlow<List<MountEntry>> = _systemMounts.asStateFlow()

    private val _selinuxStatus = MutableStateFlow("Unknown")
    val selinuxStatus: StateFlow<String> = _selinuxStatus.asStateFlow()

    private val _rootStatus = MutableStateFlow("Not checked")
    val rootStatus: StateFlow<String> = _rootStatus.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    val recentAuditLogs: StateFlow<List<AccessAuditLog>> = auditLogRepository.getRecentAuditLogs(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun appendTerminalLog(log: String) {
        val current = _terminalLogs.value.toMutableList()
        current.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $log")
        if (current.size > 200) current.removeAt(0)
        _terminalLogs.value = current
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            mountRepository.readSystemMounts()
                .onSuccess { _systemMounts.value = it }
            mountRepository.getSelinuxStatus()
                .onSuccess { _selinuxStatus.value = it }
        }
    }

    fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            mountRepository.isRootAvailable()
                .onSuccess { hasRoot ->
                    _rootStatus.value = if (hasRoot) "Granted (su detected)" else "Not available / denied"
                    appendTerminalLog("Root check: ${_rootStatus.value}")
                }
                .onError { _, message ->
                    _rootStatus.value = "Error: $message"
                    appendTerminalLog("Root check failed: $message")
                }
        }
    }

    fun setRootVolumesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRootVolumesEnabled(enabled) }
    }

    fun setEncryptVolumesAtRest(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEncryptVolumesAtRest(enabled) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setHollowIconTheme(theme: HollowIconTheme) {
        viewModelScope.launch {
            settingsRepository.setHollowIconTheme(theme)
            _statusMessage.value = "Selected launcher theme: ${theme.label}."
        }
    }

    fun setPickerPolicy(policy: PickerPolicy) {
        viewModelScope.launch {
            settingsRepository.setPickerPolicy(policy)
            _statusMessage.value = "Picker policy updated: ${policy.title}."
        }
    }

    fun attachVolume(packageName: String) {
        viewModelScope.launch {
            mountRepository.attachVolume(packageName)
                .onSuccess { result ->
                    appendTerminalLog("Attach volume $packageName: exit=${result.exitCode}")
                    _statusMessage.value = if (result.isSuccess) "Volume attached for $packageName" else "Volume attach failed."
                }
                .onError { _, message ->
                    _statusMessage.value = "Volume error: $message"
                }
        }
    }

    fun detachVolume(packageName: String) {
        viewModelScope.launch {
            mountRepository.detachVolume(packageName)
                .onSuccess { result ->
                    appendTerminalLog("Detach volume $packageName: exit=${result.exitCode}")
                    _statusMessage.value = if (result.isSuccess) "Volume detached for $packageName" else "Volume detach failed."
                }
                .onError { _, message ->
                    _statusMessage.value = "Volume error: $message"
                }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            auditLogRepository.clearAuditLogs()
                .onSuccess {
                    _statusMessage.value = "Access audit logs cleared."
                }
                .onError { _, message ->
                    _statusMessage.value = "Could not clear logs: $message"
                }
        }
    }

    fun generateBootScript(): String {
        return kotlinx.coroutines.runBlocking {
            mountRepository.generateBootScript()
        }
    }

    // Proxy & V2Ray
    fun setVpnTunnelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            vpnProxyRepository.setVpnTunnelEnabled(enabled)
            val server = settings.value.activeServer
            _statusMessage.value = if (enabled) {
                "V2Ray / Proxy ACTIVE -> ${server.name} (${server.exitIp})"
            } else {
                "V2Ray / Proxy paused."
            }
        }
    }

    fun selectActiveProxyServer(serverId: String) {
        viewModelScope.launch {
            vpnProxyRepository.selectActiveProxyServer(serverId)
            _statusMessage.value = "Switched Exit Node to $serverId."
        }
    }

    fun importProxyServerFromUri(uriOrConfig: String): Boolean {
        var success = false
        viewModelScope.launch {
            vpnProxyRepository.importProxyServer(uriOrConfig)
                .onSuccess { node ->
                    success = true
                    _statusMessage.value = "Imported ${node.protocol.badge} node '${node.name}'."
                }
                .onError { _, message ->
                    _statusMessage.value = "Import failed: $message"
                }
        }
        return true
    }

    fun addCustomProxyServer(server: VpnProxyServer) {
        viewModelScope.launch {
            vpnProxyRepository.addCustomProxyServer(server)
            _statusMessage.value = "Added custom proxy node ${server.name}."
        }
    }

    fun deleteCustomProxyServer(serverId: String) {
        viewModelScope.launch {
            vpnProxyRepository.deleteCustomProxyServer(serverId)
            _statusMessage.value = "Deleted custom proxy node."
        }
    }

    fun updateProxyingSuiteOptions(
        socksProxy: Boolean = settings.value.socksProxyEnabled,
        httpProxy: Boolean = settings.value.httpProxyEnabled,
        webViewProxy: Boolean = settings.value.webViewProxyEnabled,
        trustAllCerts: Boolean = settings.value.trustAllCertificates,
        hideProxyConn: Boolean = settings.value.hideProxyConnection,
        hideVpnConn: Boolean = settings.value.hideVpnConnection,
        vpnKillSwitch: Boolean = settings.value.vpnKillSwitchEnabled,
        matchTzWithIp: Boolean = settings.value.matchTimezoneWithIpGlobal,
        showIpInfo: Boolean = settings.value.showIpInfoInToolbar
    ) {
        viewModelScope.launch {
            vpnProxyRepository.updateSuiteOptions(
                socksProxy, httpProxy, webViewProxy, trustAllCerts,
                hideProxyConn, hideVpnConn, vpnKillSwitch, matchTzWithIp, showIpInfo
            )
            _statusMessage.value = "Proxying options updated."
        }
    }

    fun toggleVpnGlobalKillSwitch() {
        viewModelScope.launch {
            val current = settings.value.vpnKillSwitchEnabled
            settingsRepository.updateSettings { it.copy(vpnKillSwitchEnabled = !current) }
            _statusMessage.value = if (!current) "Global VPN Kill-Switch enabled." else "Global VPN Kill-Switch disabled."
        }
    }
}
