package com.example.isolation.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.core.data.repository.AuditLogRepository
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.core.data.repository.VaultRepository
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AppSettings
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.service.DeadmanIncident
import com.example.isolation.service.RootDeadmanWatchdog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Focused ViewModel for Hollow's Dashboard / Home overview.
 * Handles isolation metrics, security posture, recent audit events, and system badges.
 */
class DashboardViewModel(
    application: Application,
    private val isolationRepository: IsolationRepository,
    private val vaultRepository: VaultRepository,
    private val auditLogRepository: AuditLogRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val app = application as IsoVaultApp

    val isolatedCount: StateFlow<Int> = isolationRepository.getIsolatedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hiddenOrDisguisedCount: StateFlow<Int> = isolationRepository.getHiddenOrDisguisedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val identitySpoofedCount: StateFlow<Int> = isolationRepository.getIdentitySpoofedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalEncryptedBytes: StateFlow<Long> = vaultRepository.getTotalEncryptedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val recentAuditLogs: StateFlow<List<AccessAuditLog>> = auditLogRepository.getRecentAuditLogs(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _isLsposedActive = MutableStateFlow(
        com.example.MainActivity.isLsposedModuleActiveReal() ||
                IsolationRuleManager.isXSharedPrefsHooked(app)
    )
    val isLsposedActive: StateFlow<Boolean> = _isLsposedActive.asStateFlow()

    val deadmanBlockedIncidents: StateFlow<List<DeadmanIncident>> = RootDeadmanWatchdog.blockedUidsFlow

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(enabled)
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLsposedActive.value = com.example.MainActivity.isLsposedModuleActiveReal() ||
                    IsolationRuleManager.isXSharedPrefsHooked(app)
        }
    }
}
